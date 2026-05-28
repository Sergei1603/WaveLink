using Microsoft.EntityFrameworkCore;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

public enum TrackSort { Recent, Artist, Title }

public interface ITrackService
{
    Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, TrackSort sort, CancellationToken ct);
    Task<PagedResponse<TrackResponse>> ListPublicAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct);
    Task<TrackResponse> UploadAsync(Guid userId, UploadTrackForm form, CancellationToken ct);
    Task<TrackResponse> UpdateAsync(Guid userId, Guid trackId, UpdateTrackRequest request, CancellationToken ct);
    Task SaveAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task UnsaveAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task DeleteAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<Track> GetOwnedAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<Track> GetAccessibleAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<TrackResponse?> FindByTitleAsync(Guid userId, string title, CancellationToken ct);
}

public class TrackService : ITrackService
{
    private static readonly HashSet<string> AllowedMimeTypes = new(StringComparer.OrdinalIgnoreCase)
    {
        "audio/mpeg", "audio/flac", "audio/wav", "audio/x-wav", "audio/ogg", "audio/vorbis"
    };
    private const long MaxSizeBytes = 50L * 1024 * 1024;

    private readonly AppDbContext _db;
    private readonly IMinioStorageService _storage;
    private readonly ILogger<TrackService> _logger;

    public TrackService(AppDbContext db, IMinioStorageService storage, ILogger<TrackService> logger)
    {
        _db = db;
        _storage = storage;
        _logger = logger;
    }

    private static IQueryable<Track> ApplySort(IQueryable<Track> q, TrackSort sort) => sort switch
    {
        TrackSort.Artist => q.OrderBy(t => t.Artist).ThenBy(t => t.Title),
        TrackSort.Title  => q.OrderBy(t => t.Title),
        _                => q.OrderByDescending(t => t.UploadedAt)
    };

    private static TrackResponse ToDto(Track t, Guid currentUserId) =>
        new(t.Id, t.Title, t.Artist, t.Duration, t.FileSize, t.MimeType, t.UploadedAt,
            t.IsPublic, t.UserId == currentUserId);

    public async Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, TrackSort sort, CancellationToken ct)
    {
        page = Math.Max(page, 1);
        limit = Math.Clamp(limit, 1, 200);

        // own (not soft-deleted) + saved
        var query = _db.Tracks.Where(t =>
            (t.UserId == userId && !t.IsDeletedByOwner) ||
            _db.SavedTracks.Any(s => s.UserId == userId && s.TrackId == t.Id));

        var total = await query.CountAsync(ct);

        var items = await ApplySort(query, sort)
            .Skip((page - 1) * limit)
            .Take(limit)
            .ToListAsync(ct);

        return new PagedResponse<TrackResponse>(
            items.Select(t => ToDto(t, userId)).ToList(),
            page, limit, total);
    }

    public async Task<PagedResponse<TrackResponse>> ListPublicAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct)
    {
        page = Math.Max(page, 1);
        limit = Math.Clamp(limit, 1, 200);

        var query = _db.Tracks.Where(t => t.IsPublic && !t.IsDeletedByOwner);

        if (!string.IsNullOrWhiteSpace(search))
        {
            var needle = $"%{search.Trim()}%";
            query = query.Where(t =>
                EF.Functions.ILike(t.Title, needle) || EF.Functions.ILike(t.Artist, needle));
        }

        var total = await query.CountAsync(ct);

        var items = await ApplySort(query, sort)
            .Skip((page - 1) * limit)
            .Take(limit)
            .ToListAsync(ct);

        return new PagedResponse<TrackResponse>(
            items.Select(t => ToDto(t, userId)).ToList(),
            page, limit, total);
    }

    public async Task<TrackResponse> UploadAsync(Guid userId, UploadTrackForm form, CancellationToken ct)
    {
        if (form.File.Length == 0)
            throw new AppException("Uploaded file is empty");
        if (form.File.Length > MaxSizeBytes)
            throw new AppException($"File exceeds {MaxSizeBytes / 1024 / 1024}MB limit");
        if (!AllowedMimeTypes.Contains(form.File.ContentType))
            throw new AppException($"Unsupported audio MIME type: {form.File.ContentType}");

        var title = form.Title.Trim();
        var artist = form.Artist.Trim();

        if (await HasDuplicateInLibraryAsync(userId, title, artist, excludeTrackId: null, ct))
            throw AppException.Conflict($"You already have a track \"{artist} – {title}\" in your library");

        var trackId = Guid.NewGuid();
        var safeName = Path.GetFileName(form.File.FileName);
        var fileKey = $"{userId}/{trackId}/{safeName}";

        await using (var s = form.File.OpenReadStream())
        {
            await _storage.UploadAsync(fileKey, s, form.File.Length, form.File.ContentType, ct);
        }

        var track = new Track
        {
            Id = trackId,
            UserId = userId,
            Title = title,
            Artist = artist,
            Duration = form.Duration ?? 0,
            FileKey = fileKey,
            FileSize = form.File.Length,
            MimeType = form.File.ContentType,
            UploadedAt = DateTime.UtcNow,
            IsPublic = form.IsPublic,
            IsDeletedByOwner = false
        };
        _db.Tracks.Add(track);
        await _db.SaveChangesAsync(ct);
        _logger.LogInformation("Uploaded track {TrackId} for user {UserId} ({Size} bytes, public={Public})",
            track.Id, userId, track.FileSize, track.IsPublic);

        return ToDto(track, userId);
    }

    public async Task<TrackResponse> UpdateAsync(Guid userId, Guid trackId, UpdateTrackRequest request, CancellationToken ct)
    {
        var track = await GetOwnedAsync(userId, trackId, ct);

        var newTitle = string.IsNullOrWhiteSpace(request.Title) ? track.Title : request.Title.Trim();
        var newArtist = string.IsNullOrWhiteSpace(request.Artist) ? track.Artist : request.Artist.Trim();

        if (!string.Equals(newTitle, track.Title, StringComparison.OrdinalIgnoreCase) ||
            !string.Equals(newArtist, track.Artist, StringComparison.OrdinalIgnoreCase))
        {
            if (await HasDuplicateInLibraryAsync(userId, newTitle, newArtist, excludeTrackId: trackId, ct))
                throw AppException.Conflict($"You already have a track \"{newArtist} – {newTitle}\"");
        }

        track.Title = newTitle;
        track.Artist = newArtist;
        if (request.IsPublic.HasValue) track.IsPublic = request.IsPublic.Value;

        await _db.SaveChangesAsync(ct);
        return ToDto(track, userId);
    }

    public async Task SaveAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var track = await _db.Tracks.FirstOrDefaultAsync(t => t.Id == trackId, ct)
                    ?? throw AppException.NotFound("Track");

        if (track.UserId == userId)
            throw AppException.Conflict("You already own this track");
        if (!track.IsPublic || track.IsDeletedByOwner)
            throw AppException.Forbidden("Track is not available in the public bank");

        var alreadySaved = await _db.SavedTracks.AnyAsync(s => s.UserId == userId && s.TrackId == trackId, ct);
        if (alreadySaved)
            throw AppException.Conflict("Track is already in your library");

        if (await HasDuplicateInLibraryAsync(userId, track.Title, track.Artist, excludeTrackId: null, ct))
            throw AppException.Conflict($"You already have a track \"{track.Artist} – {track.Title}\" in your library");

        _db.SavedTracks.Add(new SavedTrack
        {
            UserId = userId,
            TrackId = trackId,
            SavedAt = DateTime.UtcNow
        });
        await _db.SaveChangesAsync(ct);
    }

    public async Task UnsaveAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var saved = await _db.SavedTracks.FirstOrDefaultAsync(s => s.UserId == userId && s.TrackId == trackId, ct)
                    ?? throw AppException.NotFound("Saved track");
        _db.SavedTracks.Remove(saved);
        await _db.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var track = await GetOwnedAsync(userId, trackId, ct);

        var hasSaves = await _db.SavedTracks.AnyAsync(s => s.TrackId == trackId, ct);

        if (hasSaves)
        {
            // Soft-delete: hide from owner and public bank; saved users keep access.
            track.IsDeletedByOwner = true;
            await _db.SaveChangesAsync(ct);
            _logger.LogInformation("Soft-deleted track {TrackId} (has saves)", track.Id);
            return;
        }

        try
        {
            await _storage.DeleteAsync(track.FileKey, ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to remove file {Key} from storage; deleting DB record anyway", track.FileKey);
        }
        _db.Tracks.Remove(track);
        await _db.SaveChangesAsync(ct);
    }

    public async Task<Track> GetOwnedAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var track = await _db.Tracks.FirstOrDefaultAsync(t => t.Id == trackId, ct)
                    ?? throw AppException.NotFound("Track");
        if (track.UserId != userId)
            throw AppException.Forbidden();
        return track;
    }

    public async Task<Track> GetAccessibleAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var track = await _db.Tracks.FirstOrDefaultAsync(t => t.Id == trackId, ct)
                    ?? throw AppException.NotFound("Track");

        if (track.UserId == userId) return track;
        if (track.IsPublic && !track.IsDeletedByOwner) return track;

        var saved = await _db.SavedTracks.AnyAsync(s => s.UserId == userId && s.TrackId == trackId, ct);
        if (saved) return track;

        throw AppException.Forbidden();
    }

    public async Task<TrackResponse?> FindByTitleAsync(Guid userId, string title, CancellationToken ct)
    {
        var needle = $"%{title.Trim()}%";
        var t = await _db.Tracks
            .Where(t =>
                ((t.UserId == userId && !t.IsDeletedByOwner) ||
                 _db.SavedTracks.Any(s => s.UserId == userId && s.TrackId == t.Id))
                && EF.Functions.ILike(t.Title, needle))
            .OrderByDescending(t => t.UploadedAt)
            .FirstOrDefaultAsync(ct);
        return t == null ? null : ToDto(t, userId);
    }

    private async Task<bool> HasDuplicateInLibraryAsync(Guid userId, string title, string artist, Guid? excludeTrackId, CancellationToken ct)
    {
        var t = title.Trim().ToLower();
        var a = artist.Trim().ToLower();
        return await _db.Tracks.AnyAsync(track =>
            track.Id != excludeTrackId &&
            ((track.UserId == userId && !track.IsDeletedByOwner) ||
             _db.SavedTracks.Any(s => s.UserId == userId && s.TrackId == track.Id))
            && track.Title.ToLower() == t
            && track.Artist.ToLower() == a, ct);
    }
}
