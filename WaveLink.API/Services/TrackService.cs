using Microsoft.EntityFrameworkCore;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

public interface ITrackService
{
    Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, CancellationToken ct);
    Task<TrackResponse> UploadAsync(Guid userId, UploadTrackForm form, CancellationToken ct);
    Task DeleteAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<Track> GetOwnedAsync(Guid userId, Guid trackId, CancellationToken ct);
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

    public async Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, CancellationToken ct)
    {
        page = Math.Max(page, 1);
        limit = Math.Clamp(limit, 1, 100);

        var query = _db.Tracks.Where(t => t.UserId == userId);
        var total = await query.CountAsync(ct);

        var items = await query
            .OrderByDescending(t => t.UploadedAt)
            .Skip((page - 1) * limit)
            .Take(limit)
            .Select(t => new TrackResponse(t.Id, t.Title, t.Artist, t.Duration, t.FileSize, t.MimeType, t.UploadedAt))
            .ToListAsync(ct);

        return new PagedResponse<TrackResponse>(items, page, limit, total);
    }

    public async Task<TrackResponse> UploadAsync(Guid userId, UploadTrackForm form, CancellationToken ct)
    {
        if (form.File.Length == 0)
            throw new AppException("Uploaded file is empty");
        if (form.File.Length > MaxSizeBytes)
            throw new AppException($"File exceeds {MaxSizeBytes / 1024 / 1024}MB limit");
        if (!AllowedMimeTypes.Contains(form.File.ContentType))
            throw new AppException($"Unsupported audio MIME type: {form.File.ContentType}");

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
            Title = form.Title.Trim(),
            Artist = form.Artist.Trim(),
            Duration = form.Duration ?? 0,
            FileKey = fileKey,
            FileSize = form.File.Length,
            MimeType = form.File.ContentType,
            UploadedAt = DateTime.UtcNow
        };
        _db.Tracks.Add(track);
        await _db.SaveChangesAsync(ct);
        _logger.LogInformation("Uploaded track {TrackId} for user {UserId} ({Size} bytes)", track.Id, userId, track.FileSize);

        return new TrackResponse(track.Id, track.Title, track.Artist, track.Duration, track.FileSize, track.MimeType, track.UploadedAt);
    }

    public async Task DeleteAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var track = await GetOwnedAsync(userId, trackId, ct);
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

    public async Task<TrackResponse?> FindByTitleAsync(Guid userId, string title, CancellationToken ct)
    {
        var needle = $"%{title.Trim()}%";
        var t = await _db.Tracks
            .Where(t => t.UserId == userId && EF.Functions.ILike(t.Title, needle))
            .OrderByDescending(t => t.UploadedAt)
            .FirstOrDefaultAsync(ct);
        return t == null
            ? null
            : new TrackResponse(t.Id, t.Title, t.Artist, t.Duration, t.FileSize, t.MimeType, t.UploadedAt);
    }
}
