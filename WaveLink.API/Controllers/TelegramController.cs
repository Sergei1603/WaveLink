using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using WaveLink.API.Common;
using WaveLink.API.DTOs;
using WaveLink.API.Services;

namespace WaveLink.API.Controllers;

[ApiController]
[Authorize]
[Route("api/telegram")]
public class TelegramController : ControllerBase
{
    private readonly ITelegramLinkTokenService _linker;
    private readonly ITelegramDeliveryService _delivery;

    public TelegramController(ITelegramLinkTokenService linker, ITelegramDeliveryService delivery)
    {
        _linker = linker;
        _delivery = delivery;
    }

    [HttpGet("generate-link-token")]
    public async Task<ActionResult<GenerateLinkTokenResponse>> Generate(CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var (token, expires) = await _linker.GenerateAsync(userId, ct);
        return Ok(new GenerateLinkTokenResponse(token, expires));
    }

    [HttpGet("status")]
    public async Task<ActionResult<TelegramStatusResponse>> Status(CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _delivery.GetStatusAsync(userId, ct));
    }

    [HttpPost("send")]
    public async Task<IActionResult> Send(SendTrackToTelegramRequest request, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _delivery.SendTrackAsync(userId, request.TrackId, ct);
        return NoContent();
    }

    // Web-side completion of linking is performed by the Telegram bot itself
    // when the user runs /link <token>. This endpoint is included for symmetry
    // with the spec; it intentionally returns 410 to direct users to the bot.
    [HttpPost("link")]
    public IActionResult Link([FromBody] LinkTelegramRequest _) =>
        StatusCode(StatusCodes.Status410Gone, new ErrorResponse(
            "Send the token to the Telegram bot via '/link <token>' to complete linking.",
            StatusCodes.Status410Gone));
}
