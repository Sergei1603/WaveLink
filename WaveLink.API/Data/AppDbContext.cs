using Microsoft.EntityFrameworkCore;
using WaveLink.API.Entities;

namespace WaveLink.API.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Track> Tracks => Set<Track>();
    public DbSet<Collection> Collections => Set<Collection>();
    public DbSet<CollectionTrack> CollectionTracks => Set<CollectionTrack>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<TelegramLinkToken> TelegramLinkTokens => Set<TelegramLinkToken>();
    public DbSet<SavedTrack> SavedTracks => Set<SavedTrack>();
    public DbSet<PlayEvent> PlayEvents => Set<PlayEvent>();
    public DbSet<UserTrackStat> UserTrackStats => Set<UserTrackStat>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.Username).IsRequired().HasMaxLength(32);
            e.HasIndex(x => x.Username).IsUnique();
            e.Property(x => x.PasswordHash).IsRequired();
            e.HasIndex(x => x.TelegramChatId).IsUnique().HasFilter("\"TelegramChatId\" IS NOT NULL");
        });

        b.Entity<Track>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.Title).IsRequired().HasMaxLength(512);
            e.Property(x => x.Artist).IsRequired().HasMaxLength(512);
            e.Property(x => x.FileKey).IsRequired().HasMaxLength(1024);
            e.Property(x => x.MimeType).IsRequired().HasMaxLength(128);
            e.Property(x => x.IsPublic).HasDefaultValue(false);
            e.Property(x => x.IsDeletedByOwner).HasDefaultValue(false);
            e.HasOne(x => x.User)
                .WithMany(u => u.Tracks)
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasIndex(x => x.UserId);
            e.HasIndex(x => new { x.IsPublic, x.IsDeletedByOwner });
        });

        b.Entity<SavedTrack>(e =>
        {
            e.HasKey(x => new { x.UserId, x.TrackId });
            e.HasOne(x => x.User)
                .WithMany(u => u.SavedTracks)
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.Track)
                .WithMany(t => t.SavedBy)
                .HasForeignKey(x => x.TrackId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasIndex(x => x.UserId);
        });

        b.Entity<PlayEvent>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.TitleSnapshot).IsRequired().HasMaxLength(512);
            e.Property(x => x.ArtistSnapshot).IsRequired().HasMaxLength(512);
            e.Property(x => x.Source).HasConversion<int>();
            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.Track)
                .WithMany()
                .HasForeignKey(x => x.TrackId)
                .OnDelete(DeleteBehavior.Cascade);
            // Idempotency gate: an offline queue may be replayed, possibly from two devices at once.
            e.HasIndex(x => new { x.UserId, x.ClientEventId }).IsUnique();
            e.HasIndex(x => new { x.UserId, x.StartedAt });
            e.HasIndex(x => new { x.TrackId, x.StartedAt });
        });

        b.Entity<UserTrackStat>(e =>
        {
            e.HasKey(x => new { x.UserId, x.TrackId });
            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.Track)
                .WithMany(t => t.PlayStats)
                .HasForeignKey(x => x.TrackId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasIndex(x => x.TrackId);                     // global per-track counters
            e.HasIndex(x => new { x.UserId, x.PlayCount }); // top tracks without a sort
        });

        b.Entity<Collection>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.Name).IsRequired().HasMaxLength(256);
            e.HasOne(x => x.User)
                .WithMany(u => u.Collections)
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasIndex(x => x.UserId);
        });

        b.Entity<CollectionTrack>(e =>
        {
            e.HasKey(x => new { x.CollectionId, x.TrackId });
            e.HasOne(x => x.Collection)
                .WithMany(c => c.CollectionTracks)
                .HasForeignKey(x => x.CollectionId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.Track)
                .WithMany(t => t.CollectionTracks)
                .HasForeignKey(x => x.TrackId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<RefreshToken>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.TokenHash).IsRequired();
            e.HasIndex(x => x.TokenHash).IsUnique();
            e.HasOne(x => x.User)
                .WithMany(u => u.RefreshTokens)
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<TelegramLinkToken>(e =>
        {
            e.HasKey(x => x.Id);
            e.Property(x => x.Token).IsRequired().HasMaxLength(128);
            e.HasIndex(x => x.Token).IsUnique();
            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}
