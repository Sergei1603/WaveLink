namespace WaveLink.API.Entities;

public enum PlaySource
{
    Web = 0,
    Android = 1
}

/// <summary>
/// One listening session, as reported by a client. Raw append-only log: period-filtered
/// statistics read from here, everything else reads the <see cref="UserTrackStat"/> rollup.
/// </summary>
public class PlayEvent
{
    public long Id { get; set; }
    public Guid UserId { get; set; }
    public Guid TrackId { get; set; }

    /// <summary>Client-generated idempotency key — an offline queue may be replayed any number of times.</summary>
    public Guid ClientEventId { get; set; }

    /// <summary>When the session started, per the client clock (validated against server time).</summary>
    public DateTime StartedAt { get; set; }

    /// <summary>When the server accepted the event. Differs from StartedAt for offline syncs.</summary>
    public DateTime ReportedAt { get; set; }

    /// <summary>Seconds of the timeline actually heard — seek-forward does not count.</summary>
    public int ListenedSeconds { get; set; }

    /// <summary>Track duration used to compute <see cref="CompletionPercent"/>; 0 when unknown.</summary>
    public int TrackDuration { get; set; }

    /// <summary>0..1. Always 0 when the duration is unknown.</summary>
    public double CompletionPercent { get; set; }

    public bool IsSignificant { get; set; }
    public bool IsCompleted { get; set; }
    public PlaySource Source { get; set; }

    /// <summary>Title/artist as they were at report time. Audit only — statistics group by the
    /// current <see cref="Track.Artist"/> so that renaming fixes typos retroactively.</summary>
    public string TitleSnapshot { get; set; } = null!;
    public string ArtistSnapshot { get; set; } = null!;

    public User User { get; set; } = null!;
    public Track Track { get; set; } = null!;
}
