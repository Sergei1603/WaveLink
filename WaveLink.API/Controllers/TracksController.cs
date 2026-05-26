using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using WaveLink.API.Common;
using WaveLink.API.DTOs;
using WaveLink.API.Services;

namespace WaveLink.API.Controllers;

[ApiController]
[Authorize]
[Route("api/tracks")]
public class TracksController : ControllerBase
{
    private readonly ITrackService _tracks;
    private readonly IMinioStorageService _storage;

    public TracksController(ITrackService tracks, IMinioStorageService storage)
    {
        _tracks = tracks;
        _storage = storage;
    }

    [HttpGet]
    public async Task<ActionResult<PagedResponse<TrackResponse>>> List(
        [FromQuery] int page = 1,
        [FromQuery] int limit = 20,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.ListAsync(userId, page, limit, ct));
    }

    [HttpPost("upload")]
    [RequestSizeLimit(52_428_800)] // 50MB
    public async Task<ActionResult<TrackResponse>> Upload([FromForm] UploadTrackForm form, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var track = await _tracks.UploadAsync(userId, form, ct);
        return CreatedAtAction(nameof(List), new { }, track);
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _tracks.DeleteAsync(userId, id, ct);
        return NoContent();
    }

    [HttpGet("{id:guid}/stream")]
    public async Task<IActionResult> Stream(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var track = await _tracks.GetOwnedAsync(userId, id, ct);

        var totalSize = track.FileSize > 0
            ? track.FileSize
            : await _storage.GetSizeAsync(track.FileKey, ct);

        var rangeHeader = Request.Headers.Range.ToString();
        if (string.IsNullOrEmpty(rangeHeader))
        {
            var full = await _storage.OpenReadAsync(track.FileKey, ct);
            Response.Headers.AcceptRanges = "bytes";
            return File(full, track.MimeType, enableRangeProcessing: false);
        }

        // Parse "bytes=start-end"
        var (start, end) = ParseRange(rangeHeader, totalSize);
        var length = end - start + 1;

        var partial = await _storage.OpenRangeAsync(track.FileKey, start, length, ct);
        Response.StatusCode = StatusCodes.Status206PartialContent;
        Response.Headers.AcceptRanges = "bytes";
        Response.Headers.ContentRange = $"bytes {start}-{end}/{totalSize}";
        Response.ContentLength = length;
        return File(partial, track.MimeType, enableRangeProcessing: false);
    }

    private static (long Start, long End) ParseRange(string rangeHeader, long total)
    {
        // bytes=start-end (end optional)
        var spec = rangeHeader.Replace("bytes=", "", StringComparison.OrdinalIgnoreCase).Trim();
        var parts = spec.Split('-', 2);
        if (parts.Length != 2) throw new AppException("Invalid Range header");
        if (!long.TryParse(parts[0], out var start)) start = 0;
        long end;
        if (string.IsNullOrEmpty(parts[1]) || !long.TryParse(parts[1], out end))
            end = total - 1;
        if (start < 0 || end >= total || start > end) throw new AppException("Invalid Range header");
        return (start, end);
    }
}
