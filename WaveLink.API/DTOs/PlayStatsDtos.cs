using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

// ---- reporting ----

public record ReportPlayItem(
    [Required] Guid ClientEventId,
    [Required] Guid TrackId,
    [Required] DateTime StartedAt,
    [Range(0, 86400)] double ListenedSeconds,
    int? TrackDuration,
    string? Source);

public record ReportPlaysRequest([Required] IReadOnlyList<ReportPlayItem> Events);

/// <summary>Status is one of "accepted", "duplicate", "rejected".</summary>
public record ReportPlayResult(Guid ClientEventId, string Status, string? Reason);

public record ReportPlaysResponse(
    IReadOnlyList<ReportPlayResult> Results,
    int Accepted,
    int Duplicates,
    int Rejected);

// ---- reading ----

public record TrackStatsResponse(
    Guid TrackId,
    int TotalPlays,
    int DistinctListeners,
    int MyPlays,
    DateTime? MyLastPlayedAt,
    bool MyCompleted,
    long MyListenedSeconds);

public record TopTrackItem(
    Guid TrackId, string Title, string Artist,
    int Plays, long ListenedSeconds, DateTime LastPlayedAt);

public record TopArtistItem(string Artist, int Plays, int TrackCount, long ListenedSeconds);

public record MyStatsResponse(
    DateTime? From,
    DateTime? To,
    int TotalPlays,
    int DistinctTracks,
    long TotalListenedSeconds,
    int CompletedPlays,
    IReadOnlyList<TopTrackItem> TopTracks,
    IReadOnlyList<TopArtistItem> TopArtists);

public record ArtistStatsResponse(
    string Artist,
    int TotalPlays,
    int DistinctListeners,
    int TrackCount,
    int MyPlays,
    long MyListenedSeconds);
