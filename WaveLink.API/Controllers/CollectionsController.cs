using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using WaveLink.API.Common;
using WaveLink.API.DTOs;
using WaveLink.API.Services;

namespace WaveLink.API.Controllers;

[ApiController]
[Authorize]
[Route("api/collections")]
public class CollectionsController : ControllerBase
{
    private readonly ICollectionService _collections;

    public CollectionsController(ICollectionService collections) { _collections = collections; }

    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<CollectionResponse>>> List(CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _collections.ListAsync(userId, ct));
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<CollectionDetailResponse>> Get(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        return Ok(await _collections.GetAsync(userId, id, ct));
    }

    [HttpPost]
    public async Task<ActionResult<CollectionResponse>> Create(CreateCollectionRequest request, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        var created = await _collections.CreateAsync(userId, request, ct);
        return CreatedAtAction(nameof(List), new { }, created);
    }

    [HttpPost("{id:guid}/tracks")]
    public async Task<IActionResult> AddTrack(Guid id, AddTrackToCollectionRequest request, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _collections.AddTrackAsync(userId, id, request.TrackId, ct);
        return NoContent();
    }

    [HttpDelete("{id:guid}/tracks/{trackId:guid}")]
    public async Task<IActionResult> RemoveTrack(Guid id, Guid trackId, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _collections.RemoveTrackAsync(userId, id, trackId, ct);
        return NoContent();
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var userId = CurrentUser.GetId(User);
        await _collections.DeleteAsync(userId, id, ct);
        return NoContent();
    }
}
