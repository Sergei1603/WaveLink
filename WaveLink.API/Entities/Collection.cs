namespace WaveLink.API.Entities;

public class Collection
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Name { get; set; } = null!;
    public DateTime CreatedAt { get; set; }

    public User User { get; set; } = null!;
    public ICollection<CollectionTrack> CollectionTracks { get; set; } = new List<CollectionTrack>();
}
