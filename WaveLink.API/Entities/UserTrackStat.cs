namespace WaveLink.API.Entities;

/// <summary>
/// Denormalized per-(user, track) rollup, upserted on every accepted <see cref="PlayEvent"/>.
/// Exists so that "my plays" in list responses and discover-shuffle weights are a single
/// indexed lookup instead of a scan over the raw log.
/// </summary>
public class UserTrackStat
{
    public Guid UserId { get; set; }
    public Guid TrackId { get; set; }

    /// <summary>Significant listens only — this is what "прослушиваний" means everywhere in the UI.</summary>
    public int PlayCount { get; set; }

    /// <summary>Every accepted event, including skipped-after-a-few-seconds ones.</summary>
    public int StartCount { get; set; }

    public int CompletedCount { get; set; }
    public long TotalListenedSeconds { get; set; }
    public DateTime FirstPlayedAt { get; set; }
    public DateTime LastPlayedAt { get; set; }

    public User User { get; set; } = null!;
    public Track Track { get; set; } = null!;
}
