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

    public TelegramController(ITelegramLinkTokenService linker) { _linker = linker; }

    [HttpGet("generate-link-token")]
    public async Task<ActionResult<GenerateLinkTokenResponse>> Generate(CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var (token, expires) = await _linker.GenerateAsync(userId, ct);
        return Ok(new GenerateLinkTokenResponse(token, expires));
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
