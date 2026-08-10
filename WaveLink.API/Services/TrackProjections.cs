using System.Linq.Expressions;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

/// <summary>
/// The single place a <see cref="Track"/> becomes a <see cref="TrackResponse"/>. Used by both
/// <see cref="TrackService"/> and <see cref="CollectionService"/> — keep it that way, or the two
/// projections drift apart the next time the DTO grows.
/// </summary>
public static class TrackProjections
{
    public static Expression<Func<Track, TrackResponse>> ToDto(Guid currentUserId) => t => new TrackResponse(
        t.Id,
        t.Title,
        t.Artist,
        t.Duration,
        t.FileSize,
        t.MimeType,
        t.UploadedAt,
        t.IsPublic,
        t.UserId == currentUserId,
        t.UserId,
        t.User.Username,
        t.PlayStats.Where(s => s.UserId == currentUserId).Select(s => s.PlayCount).FirstOrDefault(),
        t.PlayStats.Where(s => s.UserId == currentUserId).Select(s => (DateTime?)s.LastPlayedAt).FirstOrDefault(),
        t.PlayStats.Any(s => s.UserId == currentUserId && s.CompletedCount > 0));
}
