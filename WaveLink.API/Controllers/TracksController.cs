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
        [FromQuery] int limit = 50,
        [FromQuery] string? search = null,
        [FromQuery] string? sort = null,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.ListAsync(userId, page, limit, search, ParseSort(sort), ct));
    }

    [HttpGet("public")]
    public async Task<ActionResult<PagedResponse<TrackResponse>>> ListPublic(
        [FromQuery] int page = 1,
        [FromQuery] int limit = 50,
        [FromQuery] string? search = null,
        [FromQuery] string? sort = null,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.ListPublicAsync(userId, page, limit, search, ParseSort(sort), ct));
    }

    /// <summary>Track card: the track itself plus global and per-caller listening counters.</summary>
    [HttpGet("{id:guid}")]
    public async Task<ActionResult<TrackDetailResponse>> Detail(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.GetDetailAsync(userId, id, ct));
    }

    /// <summary>
    /// A shuffled queue. <c>mode=discover</c> biases towards tracks the caller has played least;
    /// <c>mode=random</c> is a plain uniform shuffle.
    /// </summary>
    [HttpGet("shuffle")]
    public async Task<ActionResult<IReadOnlyList<TrackResponse>>> Shuffle(
        [FromQuery] string? mode = null,
        [FromQuery] int limit = 50,
        [FromQuery] Guid? collectionId = null,
        CancellationToken ct = default)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.ShuffleAsync(userId, ParseShuffleMode(mode), limit, collectionId, ct));
    }

    [HttpPost("upload")]
    [RequestSizeLimit(52_428_800)] // 50MB
    public async Task<ActionResult<TrackResponse>> Upload([FromForm] UploadTrackForm form, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var track = await _tracks.UploadAsync(userId, form, ct);
        return CreatedAtAction(nameof(List), new { }, track);
    }

    [HttpPatch("{id:guid}")]
    public async Task<ActionResult<TrackResponse>> Update(Guid id, UpdateTrackRequest request, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _tracks.UpdateAsync(userId, id, request, ct));
    }

    [HttpPost("{id:guid}/save")]
    public async Task<IActionResult> Save(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _tracks.SaveAsync(userId, id, ct);
        return NoContent();
    }

    [HttpDelete("{id:guid}/save")]
    public async Task<IActionResult> Unsave(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _tracks.UnsaveAsync(userId, id, ct);
        return NoContent();
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
        var track = await _tracks.GetAccessibleAsync(userId, id, ct);

        var totalSize = track.FileSize > 0
            ? track.FileSize
            : await _storage.GetSizeAsync(track.FileKey, ct);

        // The object is piped straight from MinIO into the response body. Never buffer it:
        // players (ExoPlayer in particular) hold a single open-ended range request for the whole
        // session, so buffering would pin the entire file in server memory per listener.
        Response.Headers.AcceptRanges = "bytes";
        Response.ContentType = track.MimeType;

        var rangeHeader = Request.Headers.Range.ToString();
        if (string.IsNullOrEmpty(rangeHeader))
        {
            Response.ContentLength = totalSize;
            await _storage.CopyToAsync(track.FileKey, null, null, Response.Body, ct);
            return new EmptyResult();
        }

        var (start, end) = ParseRange(rangeHeader, totalSize);
        var length = end - start + 1;

        Response.StatusCode = StatusCodes.Status206PartialContent;
        Response.Headers.ContentRange = $"bytes {start}-{end}/{totalSize}";
        Response.ContentLength = length;
        await _storage.CopyToAsync(track.FileKey, start, length, Response.Body, ct);
        return new EmptyResult();
    }

    private static TrackSort ParseSort(string? sort) => sort?.ToLowerInvariant() switch
    {
        "artist" => TrackSort.Artist,
        "title"  => TrackSort.Title,
        _        => TrackSort.Recent
    };

    private static ShuffleMode ParseShuffleMode(string? mode) => mode?.ToLowerInvariant() switch
    {
        "discover" => ShuffleMode.Discover,
        _ => ShuffleMode.Random
    };

    private static (long Start, long End) ParseRange(string rangeHeader, long total)
    {
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
