namespace WaveLink.API.Entities;

public class CollectionTrack
{
    public Guid CollectionId { get; set; }
    public Guid TrackId { get; set; }
    public DateTime AddedAt { get; set; }

    public Collection Collection { get; set; } = null!;
    public Track Track { get; set; } = null!;
}
