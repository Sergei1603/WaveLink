using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using WaveLink.API.Common;
using WaveLink.API.DTOs;
using WaveLink.API.Services;

namespace WaveLink.API.Controllers;

[ApiController]
[Authorize]
[Route("api/stats")]
public class StatsController : ControllerBase
{
    private readonly IPlayStatsService _stats;

    public StatsController(IPlayStatsService stats)
    {
        _stats = stats;
    }

    /// <summary>
    /// The caller's own listening statistics. Without <c>from</c>/<c>to</c> this reads the
    /// lifetime rollup; with a period it falls back to the raw event log.
    /// </summary>
    [HttpGet("me")]
    public async Task<ActionResult<MyStatsResponse>> Me(
        [FromQuery] DateTime? from = null,
        [FromQuery] DateTime? to = null,
        [FromQuery] int limit = 10,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _stats.GetMyStatsAsync(userId, from, to, limit, ct));
    }

    /// <summary>
    /// Cross-user counters for one artist — aggregates only, never who listened. Requires the
    /// caller to have access to at least one track by that artist.
    /// </summary>
    [HttpGet("artist")]
    public async Task<ActionResult<ArtistStatsResponse>> Artist(
        [FromQuery] string name,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _stats.GetArtistStatsAsync(userId, name, ct));
    }
}
