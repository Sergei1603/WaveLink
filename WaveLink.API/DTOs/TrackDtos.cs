using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record TrackResponse(
    Guid Id,
    string Title,
    string Artist,
    int Duration,
    long FileSize,
    string MimeType,
    DateTime UploadedAt,
    bool IsPublic,
    bool IsOwned,
    // Saved from the public bank into the caller's library. Own tracks are not "saved" —
    // the public bank rows the client marks as already-owned are `IsOwned || IsSaved`.
    bool IsSaved,
    Guid UploaderId,
    string UploaderUsername,
    // Per-caller counters. Cheap enough for list rows (one correlated lookup on the
    // UserTrackStats PK); cross-user totals live on TrackDetailResponse instead.
    int MyPlays,
    DateTime? MyLastPlayedAt,
    bool MyCompleted);

public record TrackDetailResponse(TrackResponse Track, TrackStatsResponse Stats);

public record PagedResponse<T>(IReadOnlyList<T> Items, int Page, int Limit, int Total);

/// <summary>
/// One page of a shuffled cycle. <c>Seed</c> pins the order, <c>NextCursor</c> is where the next
/// page starts, and <c>Total</c> is the size of the cycle. When <c>HasMore</c> is false the cycle
/// is exhausted; a client that wants to keep playing asks for a new one without a seed.
/// </summary>
public record ShufflePageResponse(
    IReadOnlyList<TrackResponse> Items,
    int Seed,
    int NextCursor,
    bool HasMore,
    int Total);

public class UploadTrackForm
{
    [Required] public IFormFile File { get; set; } = default!;
    [Required, StringLength(512)] public string Title { get; set; } = "";
    [Required, StringLength(512)] public string Artist { get; set; } = "";
    public int? Duration { get; set; }
    public bool IsPublic { get; set; }
}

public record UpdateTrackRequest(
    [StringLength(512)] string? Title,
    [StringLength(512)] string? Artist,
    bool? IsPublic);
