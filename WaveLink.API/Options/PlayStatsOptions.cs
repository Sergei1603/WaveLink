namespace WaveLink.API.Options;

public class PlayStatsOptions
{
    public const string SectionName = "PlayStats";

    /// <summary>Fraction of the track that counts as a significant listen (0..1).</summary>
    public double SignificantCompletion { get; set; } = 0.60;

    /// <summary>Absolute listened seconds that count as significant regardless of completion.</summary>
    public int SignificantSeconds { get; set; } = 120;

    /// <summary>Fraction above which the track counts as "listened to the end".</summary>
    public double CompletedCompletion { get; set; } = 0.95;

    /// <summary>Events shorter than this are dropped as noise (accidental clicks).</summary>
    public int MinReportedSeconds { get; set; } = 5;

    /// <summary>Offline queues older than this are dropped instead of being backdated.</summary>
    public int MaxEventAgeDays { get; set; } = 30;

    /// <summary>Tolerance for client clocks running ahead of the server.</summary>
    public int MaxFutureSkewMinutes { get; set; } = 10;

    public int MaxBatchSize { get; set; } = 200;

    /// <summary>Exponent α in the discover-shuffle weight w = 1 / (myPlays + 1)^α.</summary>
    public double DiscoverExponent { get; set; } = 0.7;

    public int MaxShuffleLimit { get; set; } = 200;
}
