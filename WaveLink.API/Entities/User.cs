namespace WaveLink.API.Entities;

public class User
{
    public Guid Id { get; set; }
    public string Email { get; set; } = null!;
    public string PasswordHash { get; set; } = null!;
    public long? TelegramChatId { get; set; }
    public DateTime CreatedAt { get; set; }

    public ICollection<Track> Tracks { get; set; } = new List<Track>();
    public ICollection<Collection> Collections { get; set; } = new List<Collection>();
    public ICollection<RefreshToken> RefreshTokens { get; set; } = new List<RefreshToken>();
    public ICollection<SavedTrack> SavedTracks { get; set; } = new List<SavedTrack>();
}
