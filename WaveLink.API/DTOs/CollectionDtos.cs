using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record CollectionResponse(
    Guid Id,
    string Name,
    DateTime CreatedAt,
    int TrackCount);

public record CollectionDetailResponse(
    Guid Id,
    string Name,
    DateTime CreatedAt,
    IReadOnlyList<TrackResponse> Tracks);

public record CreateCollectionRequest([Required, StringLength(256)] string Name);

public record AddTrackToCollectionRequest([Required] Guid TrackId);
