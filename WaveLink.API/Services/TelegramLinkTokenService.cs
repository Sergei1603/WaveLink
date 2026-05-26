using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

public interface ITelegramLinkTokenService
{
    Task<(string Token, DateTime ExpiresAt)> GenerateAsync(Guid userId, CancellationToken ct);
    Task<User> ConsumeAsync(string token, long telegramChatId, CancellationToken ct);
}

public class TelegramLinkTokenService : ITelegramLinkTokenService
{
    private static readonly TimeSpan Lifetime = TimeSpan.FromMinutes(10);

    private readonly AppDbContext _db;

    public TelegramLinkTokenService(AppDbContext db) { _db = db; }

    public async Task<(string Token, DateTime ExpiresAt)> GenerateAsync(Guid userId, CancellationToken ct)
    {
        var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(16));
        var expires = DateTime.UtcNow.Add(Lifetime);
        _db.TelegramLinkTokens.Add(new TelegramLinkToken
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Token = token,
            ExpiresAt = expires
        });
        await _db.SaveChangesAsync(ct);
        return (token, expires);
    }

    public async Task<User> ConsumeAsync(string token, long telegramChatId, CancellationToken ct)
    {
        var entry = await _db.TelegramLinkTokens
            .Include(t => t.User)
            .FirstOrDefaultAsync(t => t.Token == token, ct)
            ?? throw AppException.NotFound("Link token");

        if (entry.UsedAt != null) throw new AppException("Token already used");
        if (entry.ExpiresAt < DateTime.UtcNow) throw new AppException("Token expired");

        entry.UsedAt = DateTime.UtcNow;
        entry.User.TelegramChatId = telegramChatId;
        await _db.SaveChangesAsync(ct);
        return entry.User;
    }
}
