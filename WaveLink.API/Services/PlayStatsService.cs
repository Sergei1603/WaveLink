using System.Text;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Npgsql;
using NpgsqlTypes;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public interface IPlayStatsService
{
    Task<ReportPlaysResponse> ReportAsync(Guid userId, ReportPlaysRequest request, CancellationToken ct);
    Task<TrackStatsResponse> GetTrackStatsAsync(Guid userId, Guid trackId, CancellationToken ct);
    Task<MyStatsResponse> GetMyStatsAsync(Guid userId, DateTime? from, DateTime? to, int limit, CancellationToken ct);
    Task<ArtistStatsResponse> GetArtistStatsAsync(Guid userId, string artist, CancellationToken ct);
}

public class PlayStatsService : IPlayStatsService
{
    private const string StatusAccepted = "accepted";
    private const string StatusDuplicate = "duplicate";
    private const string StatusRejected = "rejected";

    private readonly AppDbContext _db;
    private readonly PlayStatsOptions _options;
    private readonly ILogger<PlayStatsService> _logger;

    public PlayStatsService(AppDbContext db, IOptions<PlayStatsOptions> options, ILogger<PlayStatsService> logger)
    {
        _db = db;
        _options = options.Value;
        _logger = logger;
    }

    // ---------------------------------------------------------------- reporting

    /// <summary>
    /// Accepts a batch of listening sessions. A batch arrives from an offline queue, so a single
    /// bad item must never fail the whole request — everything except an empty/oversized batch is
    /// reported as a per-item status. Replays are safe: <c>ClientEventId</c> is unique per user.
    /// </summary>
    public async Task<ReportPlaysResponse> ReportAsync(Guid userId, ReportPlaysRequest request, CancellationToken ct)
    {
        var incoming = request.Events ?? [];
        if (incoming.Count == 0)
            throw new AppException("No play events supplied");
        if (incoming.Count > _options.MaxBatchSize)
            throw new AppException($"Batch exceeds {_options.MaxBatchSize} events");

        // Duplicates inside the batch itself: keep the first occurrence.
        var items = incoming
            .GroupBy(e => e.ClientEventId)
            .Select(g => g.First())
            .ToList();

        var results = new Dictionary<Guid, ReportPlayResult>();

        var trackIds = items.Select(i => i.TrackId).Distinct().ToList();
        var tracks = await _db.Tracks
            .Where(t => trackIds.Contains(t.Id))
            .ToDictionaryAsync(t => t.Id, ct);
        var saved = await _db.SavedTracks
            .Where(s => s.UserId == userId && trackIds.Contains(s.TrackId))
            .Select(s => s.TrackId)
            .ToListAsync(ct);
        var savedSet = saved.ToHashSet();

        var now = DateTime.UtcNow;
        var oldestAllowed = now.AddDays(-_options.MaxEventAgeDays);
        var newestAllowed = now.AddMinutes(_options.MaxFutureSkewMinutes);

        var pending = new List<NormalizedPlay>();

        foreach (var item in items)
        {
            if (!tracks.TryGetValue(item.TrackId, out var track))
            {
                results[item.ClientEventId] = Rejected(item, "track_not_found");
                continue;
            }

            // Same rule as ITrackService.GetAccessibleAsync, evaluated in memory to avoid N queries.
            var accessible = track.UserId == userId
                             || (track.IsPublic && !track.IsDeletedByOwner)
                             || savedSet.Contains(track.Id);
            if (!accessible)
            {
                results[item.ClientEventId] = Rejected(item, "forbidden");
                continue;
            }

            var startedAt = ToUtc(item.StartedAt);
            if (startedAt < oldestAllowed)
            {
                results[item.ClientEventId] = Rejected(item, "too_old");
                continue;
            }
            if (startedAt > newestAllowed)
            {
                results[item.ClientEventId] = Rejected(item, "clock_skew");
                continue;
            }

            // Self-healing metadata: bot uploads and failed browser metadata reads leave Duration = 0.
            if (track.Duration <= 0 && item.TrackDuration is > 0 and <= 86400)
                track.Duration = item.TrackDuration.Value;

            var duration = track.Duration > 0
                ? track.Duration
                : (item.TrackDuration is > 0 and <= 86400 ? item.TrackDuration.Value : 0);

            // Never trust the client's seconds: cap them at slightly over the real duration.
            var ceiling = duration > 0 ? duration * 1.05 + 5 : 86400;
            var listened = (int)Math.Round(Math.Clamp(item.ListenedSeconds, 0, ceiling));

            if (listened < _options.MinReportedSeconds)
            {
                results[item.ClientEventId] = Rejected(item, "too_short");
                continue;
            }

            var completion = duration > 0 ? Math.Min((double)listened / duration, 1.0) : 0;
            var significant = (duration > 0 && completion >= _options.SignificantCompletion)
                              || listened >= _options.SignificantSeconds;
            var completed = duration > 0 && completion >= _options.CompletedCompletion;

            pending.Add(new NormalizedPlay(
                item.ClientEventId, track.Id, startedAt, listened, duration,
                completion, significant, completed, ParseSource(item.Source),
                track.Title, track.Artist));
        }

        await PersistAsync(userId, pending, results, now, ct);

        var ordered = items.Select(i => results[i.ClientEventId]).ToList();
        return new ReportPlaysResponse(
            ordered,
            ordered.Count(r => r.Status == StatusAccepted),
            ordered.Count(r => r.Status == StatusDuplicate),
            ordered.Count(r => r.Status == StatusRejected));
    }

    /// <summary>
    /// Inserts the events and folds them into the rollup. Retried once: two devices flushing the
    /// same offline queue at the same moment can slip past the pre-check and hit the unique index.
    /// </summary>
    private async Task PersistAsync(
        Guid userId,
        List<NormalizedPlay> pending,
        Dictionary<Guid, ReportPlayResult> results,
        DateTime now,
        CancellationToken ct)
    {
        for (var attempt = 1; pending.Count > 0; attempt++)
        {
            var ids = pending.Select(p => p.ClientEventId).ToList();
            var known = await _db.PlayEvents
                .Where(e => e.UserId == userId && ids.Contains(e.ClientEventId))
                .Select(e => e.ClientEventId)
                .ToListAsync(ct);

            if (known.Count > 0)
            {
                var knownSet = known.ToHashSet();
                foreach (var id in knownSet)
                    results[id] = new ReportPlayResult(id, StatusDuplicate, null);
                pending.RemoveAll(p => knownSet.Contains(p.ClientEventId));
                if (pending.Count == 0) break;
            }

            var entities = pending.Select(p => new PlayEvent
            {
                UserId = userId,
                TrackId = p.TrackId,
                ClientEventId = p.ClientEventId,
                StartedAt = p.StartedAt,
                ReportedAt = now,
                ListenedSeconds = p.ListenedSeconds,
                TrackDuration = p.Duration,
                CompletionPercent = p.Completion,
                IsSignificant = p.IsSignificant,
                IsCompleted = p.IsCompleted,
                Source = p.Source,
                TitleSnapshot = p.Title,
                ArtistSnapshot = p.Artist
            }).ToList();

            await using var tx = await _db.Database.BeginTransactionAsync(ct);
            try
            {
                _db.PlayEvents.AddRange(entities);
                await _db.SaveChangesAsync(ct);
                await UpsertRollupAsync(userId, pending, ct);
                await tx.CommitAsync(ct);
            }
            catch (DbUpdateException ex) when (attempt < 2 && IsUniqueViolation(ex))
            {
                await tx.RollbackAsync(ct);
                foreach (var e in entities) _db.Entry(e).State = EntityState.Detached;
                _logger.LogInformation("Concurrent replay of a play-event batch for user {UserId}; retrying", userId);
                continue;
            }

            foreach (var p in pending)
                results[p.ClientEventId] = new ReportPlayResult(p.ClientEventId, StatusAccepted, null);
            break;
        }
    }

    private async Task UpsertRollupAsync(Guid userId, List<NormalizedPlay> plays, CancellationToken ct)
    {
        // Postgres refuses to let one ON CONFLICT statement touch the same row twice, and an
        // offline queue routinely holds several plays of one track — so fold per track first.
        var byTrack = plays
            .GroupBy(p => p.TrackId)
            .Select(g => new
            {
                TrackId = g.Key,
                Plays = g.Count(p => p.IsSignificant),
                Starts = g.Count(),
                Completed = g.Count(p => p.IsCompleted),
                Seconds = g.Sum(p => (long)p.ListenedSeconds),
                First = g.Min(p => p.StartedAt),
                Last = g.Max(p => p.StartedAt)
            })
            .ToList();

        var sql = new StringBuilder(
            """
            INSERT INTO "UserTrackStats"
                ("UserId","TrackId","PlayCount","StartCount","CompletedCount",
                 "TotalListenedSeconds","FirstPlayedAt","LastPlayedAt")
            VALUES
            """);

        var ps = new List<object>();
        for (var i = 0; i < byTrack.Count; i++)
        {
            var row = byTrack[i];
            var b = ps.Count;
            sql.Append(i > 0 ? "," : " ")
               .Append($"(@p{b},@p{b + 1},@p{b + 2},@p{b + 3},@p{b + 4},@p{b + 5},@p{b + 6},@p{b + 7})");

            ps.Add(new NpgsqlParameter($"p{b}", NpgsqlDbType.Uuid) { Value = userId });
            ps.Add(new NpgsqlParameter($"p{b + 1}", NpgsqlDbType.Uuid) { Value = row.TrackId });
            ps.Add(new NpgsqlParameter($"p{b + 2}", NpgsqlDbType.Integer) { Value = row.Plays });
            ps.Add(new NpgsqlParameter($"p{b + 3}", NpgsqlDbType.Integer) { Value = row.Starts });
            ps.Add(new NpgsqlParameter($"p{b + 4}", NpgsqlDbType.Integer) { Value = row.Completed });
            ps.Add(new NpgsqlParameter($"p{b + 5}", NpgsqlDbType.Bigint) { Value = row.Seconds });
            ps.Add(new NpgsqlParameter($"p{b + 6}", NpgsqlDbType.TimestampTz) { Value = row.First });
            ps.Add(new NpgsqlParameter($"p{b + 7}", NpgsqlDbType.TimestampTz) { Value = row.Last });
        }

        // ON CONFLICT keeps concurrent batches (phone + browser) from losing an increment.
        sql.Append(
            """

            ON CONFLICT ("UserId","TrackId") DO UPDATE SET
                "PlayCount"            = "UserTrackStats"."PlayCount"            + EXCLUDED."PlayCount",
                "StartCount"           = "UserTrackStats"."StartCount"           + EXCLUDED."StartCount",
                "CompletedCount"       = "UserTrackStats"."CompletedCount"       + EXCLUDED."CompletedCount",
                "TotalListenedSeconds" = "UserTrackStats"."TotalListenedSeconds" + EXCLUDED."TotalListenedSeconds",
                "FirstPlayedAt"        = LEAST("UserTrackStats"."FirstPlayedAt", EXCLUDED."FirstPlayedAt"),
                "LastPlayedAt"         = GREATEST("UserTrackStats"."LastPlayedAt", EXCLUDED."LastPlayedAt")
            """);

        await _db.Database.ExecuteSqlRawAsync(sql.ToString(), ps, ct);
    }

    // ---------------------------------------------------------------- reading

    public async Task<TrackStatsResponse> GetTrackStatsAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var global = await _db.UserTrackStats
            .Where(s => s.TrackId == trackId && s.PlayCount > 0)
            .GroupBy(_ => 1)
            .Select(g => new { Total = g.Sum(s => s.PlayCount), Listeners = g.Count() })
            .FirstOrDefaultAsync(ct);

        var mine = await _db.UserTrackStats
            .Where(s => s.UserId == userId && s.TrackId == trackId)
            .Select(s => new { s.PlayCount, s.LastPlayedAt, s.CompletedCount, s.TotalListenedSeconds })
            .FirstOrDefaultAsync(ct);

        return new TrackStatsResponse(
            trackId,
            global?.Total ?? 0,
            global?.Listeners ?? 0,
            mine?.PlayCount ?? 0,
            mine?.LastPlayedAt,
            mine is { CompletedCount: > 0 },
            mine?.TotalListenedSeconds ?? 0);
    }

    public async Task<MyStatsResponse> GetMyStatsAsync(Guid userId, DateTime? from, DateTime? to, int limit, CancellationToken ct)
    {
        limit = Math.Clamp(limit, 1, 100);
        var fromUtc = from.HasValue ? ToUtc(from.Value) : (DateTime?)null;
        var toUtc = to.HasValue ? ToUtc(to.Value) : (DateTime?)null;

        return fromUtc.HasValue || toUtc.HasValue
            ? await GetMyStatsForPeriodAsync(userId, fromUtc, toUtc, limit, ct)
            : await GetMyStatsLifetimeAsync(userId, limit, ct);
    }

    private async Task<MyStatsResponse> GetMyStatsLifetimeAsync(Guid userId, int limit, CancellationToken ct)
    {
        var stats = _db.UserTrackStats.Where(s => s.UserId == userId && s.PlayCount > 0);

        var totals = await stats
            .GroupBy(_ => 1)
            .Select(g => new
            {
                Plays = g.Sum(s => s.PlayCount),
                Tracks = g.Count(),
                Seconds = g.Sum(s => s.TotalListenedSeconds),
                Completed = g.Sum(s => s.CompletedCount)
            })
            .FirstOrDefaultAsync(ct);

        var aggregates = await stats
            .Select(s => new { s.TrackId, Plays = s.PlayCount, Seconds = s.TotalListenedSeconds, Last = s.LastPlayedAt })
            .ToListAsync(ct);

        var (topTracks, topArtists) = await BuildTopsAsync(
            aggregates.Select(a => new TrackAggregate(a.TrackId, a.Plays, a.Seconds, a.Last)).ToList(),
            limit, ct);

        return new MyStatsResponse(
            null, null,
            totals?.Plays ?? 0, totals?.Tracks ?? 0, totals?.Seconds ?? 0, totals?.Completed ?? 0,
            topTracks, topArtists);
    }

    private async Task<MyStatsResponse> GetMyStatsForPeriodAsync(
        Guid userId, DateTime? from, DateTime? to, int limit, CancellationToken ct)
    {
        var events = _db.PlayEvents.Where(e => e.UserId == userId);
        if (from.HasValue) events = events.Where(e => e.StartedAt >= from.Value);
        if (to.HasValue) events = events.Where(e => e.StartedAt < to.Value);

        var totals = await events
            .GroupBy(_ => 1)
            .Select(g => new
            {
                Plays = g.Count(e => e.IsSignificant),
                Seconds = g.Sum(e => (long)e.ListenedSeconds),
                Completed = g.Count(e => e.IsCompleted)
            })
            .FirstOrDefaultAsync(ct);

        // Grouped on PlayEvents' own columns only — see BuildTopsAsync for why. Aggregated over
        // *all* events, not just significant ones, so the numbers mean exactly what the lifetime
        // rollup means: plays count significant listens, seconds count everything heard.
        var aggregates = await events
            .GroupBy(e => e.TrackId)
            .Select(g => new
            {
                TrackId = g.Key,
                Plays = g.Count(e => e.IsSignificant),
                Seconds = g.Sum(e => (long)e.ListenedSeconds),
                Last = g.Max(e => e.StartedAt)
            })
            .ToListAsync(ct);

        var played = aggregates
            .Where(a => a.Plays > 0)
            .Select(a => new TrackAggregate(a.TrackId, a.Plays, a.Seconds, a.Last))
            .ToList();

        var (topTracks, topArtists) = await BuildTopsAsync(played, limit, ct);

        return new MyStatsResponse(
            from, to,
            totals?.Plays ?? 0, played.Count, totals?.Seconds ?? 0, totals?.Completed ?? 0,
            topTracks, topArtists);
    }

    private readonly record struct TrackAggregate(Guid TrackId, int Plays, long Seconds, DateTime Last);

    /// <summary>
    /// Turns per-track aggregates into the top-tracks and top-artists lists.
    /// <para>
    /// Titles and artists are resolved in a second query and folded in memory rather than joined
    /// into the GROUP BY: EF pushes any intermediate projection back into the grouping, and a join
    /// inside a group has no SQL translation. The row count here is bounded by the number of tracks
    /// the user has ever played, so this stays cheap for a personal service.
    /// </para>
    /// Artists are grouped by the track's *current* name, so renaming an artist fixes history
    /// everywhere; the snapshots on PlayEvent are audit-only.
    /// </summary>
    private async Task<(List<TopTrackItem> Tracks, List<TopArtistItem> Artists)> BuildTopsAsync(
        List<TrackAggregate> aggregates, int limit, CancellationToken ct)
    {
        if (aggregates.Count == 0) return ([], []);

        var ids = aggregates.Select(a => a.TrackId).ToList();
        var meta = await _db.Tracks
            .Where(t => ids.Contains(t.Id))
            .Select(t => new { t.Id, t.Title, t.Artist })
            .ToDictionaryAsync(t => t.Id, ct);

        var known = aggregates.Where(a => meta.ContainsKey(a.TrackId)).ToList();

        var tracks = known
            .OrderByDescending(a => a.Plays)
            .ThenByDescending(a => a.Last)
            .Take(limit)
            .Select(a => new TopTrackItem(
                a.TrackId, meta[a.TrackId].Title, meta[a.TrackId].Artist,
                a.Plays, a.Seconds, a.Last))
            .ToList();

        var artists = known
            .GroupBy(a => meta[a.TrackId].Artist.ToLowerInvariant())
            .Select(g => new TopArtistItem(
                meta[g.First().TrackId].Artist,
                g.Sum(a => a.Plays),
                g.Select(a => a.TrackId).Distinct().Count(),
                g.Sum(a => a.Seconds)))
            .OrderByDescending(a => a.Plays)
            .Take(limit)
            .ToList();

        return (tracks, artists);
    }

    public async Task<ArtistStatsResponse> GetArtistStatsAsync(Guid userId, string artist, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(artist)) throw new AppException("Artist name is required");
        var key = artist.Trim().ToLowerInvariant();

        // Cross-user counters, so gate on visibility: without this the stats API would let anyone
        // enumerate the popularity of the whole catalogue.
        var visible = await _db.Tracks.AnyAsync(t =>
            t.Artist.ToLower() == key &&
            (t.UserId == userId
             || (t.IsPublic && !t.IsDeletedByOwner)
             || _db.SavedTracks.Any(s => s.UserId == userId && s.TrackId == t.Id)), ct);
        if (!visible) throw AppException.NotFound("Artist");

        var stats = _db.UserTrackStats.Where(s => s.Track.Artist.ToLower() == key && s.PlayCount > 0);

        var global = await stats
            .GroupBy(_ => 1)
            .Select(g => new
            {
                Total = g.Sum(s => s.PlayCount),
                Listeners = g.Select(s => s.UserId).Distinct().Count(),
                Tracks = g.Select(s => s.TrackId).Distinct().Count()
            })
            .FirstOrDefaultAsync(ct);

        var mine = await stats
            .Where(s => s.UserId == userId)
            .GroupBy(_ => 1)
            .Select(g => new { Plays = g.Sum(s => s.PlayCount), Seconds = g.Sum(s => s.TotalListenedSeconds) })
            .FirstOrDefaultAsync(ct);

        var display = await _db.Tracks
            .Where(t => t.Artist.ToLower() == key)
            .Select(t => t.Artist)
            .FirstOrDefaultAsync(ct) ?? artist.Trim();

        return new ArtistStatsResponse(
            display,
            global?.Total ?? 0,
            global?.Listeners ?? 0,
            global?.Tracks ?? 0,
            mine?.Plays ?? 0,
            mine?.Seconds ?? 0);
    }

    // ---------------------------------------------------------------- helpers

    private static ReportPlayResult Rejected(ReportPlayItem item, string reason) =>
        new(item.ClientEventId, StatusRejected, reason);

    private static PlaySource ParseSource(string? source) => source?.ToLowerInvariant() switch
    {
        "android" => PlaySource.Android,
        _ => PlaySource.Web
    };

    /// <summary>Clients are expected to send UTC; a value without an offset is taken as UTC.</summary>
    private static DateTime ToUtc(DateTime value) => value.Kind switch
    {
        DateTimeKind.Utc => value,
        DateTimeKind.Unspecified => DateTime.SpecifyKind(value, DateTimeKind.Utc),
        _ => value.ToUniversalTime()
    };

    private static bool IsUniqueViolation(DbUpdateException ex) =>
        ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation };

    private readonly record struct NormalizedPlay(
        Guid ClientEventId, Guid TrackId, DateTime StartedAt, int ListenedSeconds, int Duration,
        double Completion, bool IsSignificant, bool IsCompleted, PlaySource Source,
        string Title, string Artist);
}
