using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record TrackResponse(
    Guid Id,
    string Title,
    string Artist,
    int Duration,
    long FileSize,
    string MimeType,
    DateTime UploadedAt,
    bool IsPublic,
    bool IsOwned);

public record PagedResponse<T>(IReadOnlyList<T> Items, int Page, int Limit, int Total);

public class UploadTrackForm
{
    [Required] public IFormFile File { get; set; } = default!;
    [Required, StringLength(512)] public string Title { get; set; } = "";
    [Required, StringLength(512)] public string Artist { get; set; } = "";
    public int? Duration { get; set; }
    public bool IsPublic { get; set; }
}

public record UpdateTrackRequest(
    [StringLength(512)] string? Title,
    [StringLength(512)] string? Artist,
    bool? IsPublic);
