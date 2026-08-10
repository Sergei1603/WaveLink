using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using WaveLink.API.Common;
using WaveLink.API.DTOs;
using WaveLink.API.Services;

namespace WaveLink.API.Controllers;

[ApiController]
[Authorize]
[Route("api/plays")]
public class PlaysController : ControllerBase
{
    private readonly IPlayStatsService _stats;

    public PlaysController(IPlayStatsService stats)
    {
        _stats = stats;
    }

    /// <summary>
    /// Reports one or more finished listening sessions. Idempotent per <c>clientEventId</c>,
    /// so a client may safely replay its offline queue.
    /// </summary>
    [HttpPost]
    public async Task<ActionResult<ReportPlaysResponse>> Report(ReportPlaysRequest request, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _stats.ReportAsync(userId, request, ct));
    }
}
