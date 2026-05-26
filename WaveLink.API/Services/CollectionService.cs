using Microsoft.EntityFrameworkCore;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

public interface ICollectionService
{
    Task<IReadOnlyList<CollectionResponse>> ListAsync(Guid userId, CancellationToken ct);
    Task<CollectionDetailResponse> GetAsync(Guid userId, Guid collectionId, CancellationToken ct);
    Task<CollectionResponse> CreateAsync(Guid userId, CreateCollectionRequest request, CancellationToken ct);
    Task AddTrackAsync(Guid userId, Guid collectionId, Guid trackId, CancellationToken ct);
    Task RemoveTrackAsync(Guid userId, Guid collectionId, Guid trackId, CancellationToken ct);
    Task DeleteAsync(Guid userId, Guid collectionId, CancellationToken ct);
}

public class CollectionService : ICollectionService
{
    private readonly AppDbContext _db;

    public CollectionService(AppDbContext db) { _db = db; }

    public async Task<IReadOnlyList<CollectionResponse>> ListAsync(Guid userId, CancellationToken ct)
    {
        return await _db.Collections
            .Where(c => c.UserId == userId)
            .OrderBy(c => c.CreatedAt)
            .Select(c => new CollectionResponse(c.Id, c.Name, c.CreatedAt, c.CollectionTracks.Count))
            .ToListAsync(ct);
    }

    public async Task<CollectionDetailResponse> GetAsync(Guid userId, Guid collectionId, CancellationToken ct)
    {
        var collection = await GetOwnedAsync(userId, collectionId, ct);
        var tracks = await _db.CollectionTracks
            .Where(ct2 => ct2.CollectionId == collectionId)
            .OrderByDescending(ct2 => ct2.AddedAt)
            .Select(ct2 => new TrackResponse(
                ct2.Track.Id,
                ct2.Track.Title,
                ct2.Track.Artist,
                ct2.Track.Duration,
                ct2.Track.FileSize,
                ct2.Track.MimeType,
                ct2.Track.UploadedAt))
            .ToListAsync(ct);
        return new CollectionDetailResponse(collection.Id, collection.Name, collection.CreatedAt, tracks);
    }

    public async Task<CollectionResponse> CreateAsync(Guid userId, CreateCollectionRequest request, CancellationToken ct)
    {
        var entity = new Collection
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Name = request.Name.Trim(),
            CreatedAt = DateTime.UtcNow
        };
        _db.Collections.Add(entity);
        await _db.SaveChangesAsync(ct);
        return new CollectionResponse(entity.Id, entity.Name, entity.CreatedAt, 0);
    }

    public async Task AddTrackAsync(Guid userId, Guid collectionId, Guid trackId, CancellationToken ct)
    {
        var collection = await GetOwnedAsync(userId, collectionId, ct);
        var track = await _db.Tracks.FirstOrDefaultAsync(t => t.Id == trackId, ct)
                    ?? throw AppException.NotFound("Track");
        if (track.UserId != userId)
            throw AppException.Forbidden("Track does not belong to user");

        var exists = await _db.CollectionTracks
            .AnyAsync(ct => ct.CollectionId == collectionId && ct.TrackId == trackId, ct);
        if (exists)
            throw AppException.Conflict("Track is already in this collection");

        _db.CollectionTracks.Add(new CollectionTrack
        {
            CollectionId = collection.Id,
            TrackId = track.Id,
            AddedAt = DateTime.UtcNow
        });
        await _db.SaveChangesAsync(ct);
    }

    public async Task RemoveTrackAsync(Guid userId, Guid collectionId, Guid trackId, CancellationToken ct)
    {
        var collection = await GetOwnedAsync(userId, collectionId, ct);
        var link = await _db.CollectionTracks
            .FirstOrDefaultAsync(x => x.CollectionId == collection.Id && x.TrackId == trackId, ct)
            ?? throw AppException.NotFound("Track in collection");
        _db.CollectionTracks.Remove(link);
        await _db.SaveChangesAsync(ct);
    }

    public async Task DeleteAsync(Guid userId, Guid collectionId, CancellationToken ct)
    {
        var collection = await GetOwnedAsync(userId, collectionId, ct);
        _db.Collections.Remove(collection);
        await _db.SaveChangesAsync(ct);
    }

    private async Task<Collection> GetOwnedAsync(Guid userId, Guid collectionId, CancellationToken ct)
    {
        var c = await _db.Collections.FirstOrDefaultAsync(x => x.Id == collectionId, ct)
                ?? throw AppException.NotFound("Collection");
        if (c.UserId != userId)
            throw AppException.Forbidden();
        return c;
    }
}
