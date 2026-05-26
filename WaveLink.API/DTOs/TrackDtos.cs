using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record TrackResponse(
    Guid Id,
    string Title,
    string Artist,
    int Duration,
    long FileSize,
    string MimeType,
    DateTime UploadedAt);

public record PagedResponse<T>(IReadOnlyList<T> Items, int Page, int Limit, int Total);

public record UploadTrackForm(
    [Required] IFormFile File,
    [Required, StringLength(512)] string Title,
    [Required, StringLength(512)] string Artist,
    int? Duration);
