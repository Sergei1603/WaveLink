namespace WaveLink.API.Entities;

public class SavedTrack
{
    public Guid UserId { get; set; }
    public Guid TrackId { get; set; }
    public DateTime SavedAt { get; set; }

    public User User { get; set; } = null!;
    public Track Track { get; set; } = null!;
}
