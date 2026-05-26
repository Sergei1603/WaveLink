namespace WaveLink.API.Entities;

public class Track
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Title { get; set; } = null!;
    public string Artist { get; set; } = null!;
    public int Duration { get; set; }      // seconds
    public string FileKey { get; set; } = null!;
    public long FileSize { get; set; }
    public string MimeType { get; set; } = null!;
    public DateTime UploadedAt { get; set; }

    public User User { get; set; } = null!;
    public ICollection<CollectionTrack> CollectionTracks { get; set; } = new List<CollectionTrack>();
}
