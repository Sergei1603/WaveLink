using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public enum TrackSort { Recent, Artist, Title }

/// <summary>
/// <see cref="Random"/> plays every accessible track with equal probability;
/// <see cref="Discover"/> biases towards what the caller has played least.
/// </summary>
public enum ShuffleMode { Random, Discover }

public interface ITrackService
{
    Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct);
    Task<PagedResponse<TrackResponse>> ListPublicAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct);
    Task<TrackResponse> UploadAsync(Guid userId, UploadTrackForm form, CancellationToken ct);
    Task<TrackResponse> UpdateAsync(Guid userId, Guid trackId, UpdateTrackRequest request, CancellationToken ct);
    Task SaveAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task UnsaveAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task DeleteAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<Track> GetOwnedAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<Track> GetAccessibleAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<TrackResponse?> FindByTitleAsync(Guid userId, string title, CancellationToken ct);
    Task<TrackDetailResponse> GetDetailAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<ShufflePageResponse> ShuffleAsync(Guid userId, ShuffleMode mode, int limit, Guid? collectionId, int? seed, int cursor, CancellationToken ct);
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
    private readonly IPlayStatsService _stats;
    private readonly PlayStatsOptions _statsOptions;
    private readonly ILogger<TrackService> _logger;

    public TrackService(
        AppDbContext db,
        IMinioStorageService storage,
        IPlayStatsService stats,
        IOptions<PlayStatsOptions> statsOptions,
        ILogger<TrackService> logger)
    {
        _db = db;
        _storage = storage;
        _stats = stats;
        _statsOptions = statsOptions.Value;
        _logger = logger;
    }

    // Id is a stable tiebreaker: without it Recent/Artist paging can drop or repeat rows.
    private static IQueryable<Track> ApplySort(IQueryable<Track> q, TrackSort sort) => sort switch
    {
        TrackSort.Artist => q.OrderBy(t => t.Artist).ThenBy(t => t.Title).ThenBy(t => t.Id),
        TrackSort.Title  => q.OrderBy(t => t.Title).ThenBy(t => t.Id),
        _                => q.OrderByDescending(t => t.UploadedAt).ThenBy(t => t.Id)
    };

    /// <summary>Own (not soft-deleted) tracks plus everything saved from the public bank.</summary>
    private IQueryable<Track> LibraryQuery(Guid userId) => _db.Tracks.Where(t =>
        (t.UserId == userId && !t.IsDeletedByOwner) ||
        _db.SavedTracks.Any(s => s.UserId == userId && s.TrackId == t.Id));

    // Search runs in SQL on purpose: the clients page through the list, so filtering the
    // already-loaded page would only ever search the part that happens to be in memory.
    private static IQueryable<Track> ApplySearch(IQueryable<Track> q, string? search)
    {
        if (string.IsNullOrWhiteSpace(search)) return q;

        var needle = $"%{search.Trim()}%";
        return q.Where(t =>
            EF.Functions.ILike(t.Title, needle) || EF.Functions.ILike(t.Artist, needle));
    }

    public async Task<PagedResponse<TrackResponse>> ListAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct)
    {
        page = Math.Max(page, 1);
        limit = Math.Clamp(limit, 1, 200);

        var query = ApplySearch(LibraryQuery(userId), search);
        var total = await query.CountAsync(ct);

        var items = await ApplySort(query, sort)
            .Skip((page - 1) * limit)
            .Take(limit)
            .Select(TrackProjections.ToDto(userId))
            .ToListAsync(ct);

        return new PagedResponse<TrackResponse>(items, page, limit, total);
    }

    public async Task<PagedResponse<TrackResponse>> ListPublicAsync(Guid userId, int page, int limit, string? search, TrackSort sort, CancellationToken ct)
    {
        page = Math.Max(page, 1);
        limit = Math.Clamp(limit, 1, 200);

        var query = ApplySearch(_db.Tracks.Where(t => t.IsPublic && !t.IsDeletedByOwner), search);
        var total = await query.CountAsync(ct);

        var items = await ApplySort(query, sort)
            .Skip((page - 1) * limit)
            .Take(limit)
            .Select(TrackProjections.ToDto(userId))
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

        return await GetDtoAsync(userId, track.Id, ct);
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
        return await GetDtoAsync(userId, track.Id, ct);
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
        // Cascades away the track's PlayEvents and UserTrackStats along with it.
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
        return await LibraryQuery(userId)
            .Where(t => EF.Functions.ILike(t.Title, needle))
            .OrderByDescending(t => t.UploadedAt)
            .Select(TrackProjections.ToDto(userId))
            .FirstOrDefaultAsync(ct);
    }

    public async Task<TrackDetailResponse> GetDetailAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        await GetAccessibleAsync(userId, trackId, ct);   // authorization gate, before any stats work
        var dto = await GetDtoAsync(userId, trackId, ct);
        var stats = await _stats.GetTrackStatsAsync(userId, trackId, ct);
        return new TrackDetailResponse(dto, stats);
    }

    public async Task<ShufflePageResponse> ShuffleAsync(
        Guid userId, ShuffleMode mode, int limit, Guid? collectionId, int? seed, int cursor, CancellationToken ct)
    {
        limit = Math.Clamp(limit, 1, _statsOptions.MaxShuffleLimit);
        cursor = Math.Max(cursor, 0);
        // The first page carries no seed: the server picks one and the client hands it back for
        // every later page, which is what makes one shuffled cycle pageable.
        var cycleSeed = seed ?? System.Random.Shared.Next();

        var scope = LibraryQuery(userId);
        if (collectionId.HasValue)
        {
            var owns = await _db.Collections.AnyAsync(c => c.Id == collectionId.Value && c.UserId == userId, ct);
            if (!owns) throw AppException.NotFound("Collection");
            scope = _db.CollectionTracks
                .Where(x => x.CollectionId == collectionId.Value && !x.Track.IsDeletedByOwner)
                .Select(x => x.Track);
        }

        var candidates = await scope
            .Select(t => new
            {
                t.Id,
                MyPlays = t.PlayStats.Where(s => s.UserId == userId).Select(s => s.PlayCount).FirstOrDefault()
            })
            .ToListAsync(ct);

        if (candidates.Count == 0) return new ShufflePageResponse([], cycleSeed, 0, false, 0);

        var order = WeightedOrder(
            candidates.Select(c => (c.Id, c.MyPlays)).ToList(),
            mode, _statsOptions.DiscoverExponent, cycleSeed);

        var page = order.Skip(cursor).Take(limit).ToList();

        // Postgres does not preserve the order of `WHERE id = ANY(...)`, so restore it here.
        var byId = await _db.Tracks
            .Where(t => page.Contains(t.Id))
            .Select(TrackProjections.ToDto(userId))
            .ToDictionaryAsync(t => t.Id, ct);

        var items = page.Where(byId.ContainsKey).Select(id => byId[id]).ToList();
        var nextCursor = Math.Min(cursor + page.Count, order.Count);

        return new ShufflePageResponse(items, cycleSeed, nextCursor, nextCursor < order.Count, order.Count);
    }

    /// <summary>
    /// Ordered weighted sampling without replacement (Efraimidis–Spirakis), in log form for
    /// numeric stability: key = ln(U)/w with U ~ Uniform(0,1). ln(U) is negative, so a larger
    /// weight pulls the key towards zero — sort descending and take the first N.
    /// Discover weights are w = 1/(myPlays+1)^α, so a never-played track (w = 1) always has the
    /// strongest pull and a much-played one fades out smoothly.
    ///
    /// The whole order is returned, and the same <paramref name="seed"/> reproduces it, which is
    /// what lets a client page through one cycle. Reproducibility holds only for an unchanged
    /// candidate set: an upload, a save, or a play count that grew in the meantime shifts the
    /// keys, so a page can repeat or skip a track. Clients dedupe their queue tail instead of the
    /// server keeping per-cycle state.
    /// </summary>
    internal static List<Guid> WeightedOrder(
        IReadOnlyList<(Guid Id, int MyPlays)> candidates, ShuffleMode mode, double alpha, int seed)
    {
        // Sorted first, so the RNG draw a given track gets depends on the seed alone.
        var ordered = candidates.OrderBy(c => c.Id).ToList();
        var rng = new Random(seed);

        var keyed = new List<(Guid Id, double Key)>(ordered.Count);
        foreach (var (id, myPlays) in ordered)
        {
            var weight = mode == ShuffleMode.Discover
                ? 1.0 / Math.Pow(Math.Max(myPlays, 0) + 1, alpha)
                : 1.0;

            var u = rng.NextDouble();
            if (u <= 0) u = double.Epsilon;   // ln(0) would be -inf
            keyed.Add((id, Math.Log(u) / weight));
        }

        return keyed
            .OrderByDescending(x => x.Key)
            .Select(x => x.Id)
            .ToList();
    }

    private async Task<TrackResponse> GetDtoAsync(Guid userId, Guid trackId, CancellationToken ct) =>
        await _db.Tracks
            .Where(t => t.Id == trackId)
            .Select(TrackProjections.ToDto(userId))
            .FirstOrDefaultAsync(ct)
        ?? throw AppException.NotFound("Track");

    private async Task<bool> HasDuplicateInLibraryAsync(Guid userId, string title, string artist, Guid? excludeTrackId, CancellationToken ct)
    {
        var t = title.Trim().ToLower();
        var a = artist.Trim().ToLower();
        return await LibraryQuery(userId).AnyAsync(track =>
            track.Id != excludeTrackId
            && track.Title.ToLower() == t
            && track.Artist.ToLower() == a, ct);
    }
}
