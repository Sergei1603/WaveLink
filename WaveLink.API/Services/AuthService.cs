using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public interface IAuthService
{
    Task<TokenPairResponse> RegisterAsync(RegisterRequest request, CancellationToken ct);
    Task<TokenPairResponse> LoginAsync(LoginRequest request, CancellationToken ct);
    Task<TokenPairResponse> RefreshAsync(string refreshToken, CancellationToken ct);
    Task LogoutAsync(string refreshToken, CancellationToken ct);
}

public class AuthService : IAuthService
{
    private readonly AppDbContext _db;
    private readonly JwtOptions _jwt;
    private readonly ILogger<AuthService> _logger;

    public AuthService(AppDbContext db, IOptions<JwtOptions> jwt, ILogger<AuthService> logger)
    {
        _db = db;
        _jwt = jwt.Value;
        _logger = logger;
    }

    public async Task<TokenPairResponse> RegisterAsync(RegisterRequest request, CancellationToken ct)
    {
        var username = request.Username.Trim();
        var normalized = username.ToLowerInvariant();

        if (await _db.Users.AnyAsync(u => u.Username.ToLower() == normalized, ct))
            throw AppException.Conflict("Никнейм уже занят");

        var user = new User
        {
            Id = Guid.NewGuid(),
            Username = username,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password),
            CreatedAt = DateTime.UtcNow
        };
        _db.Users.Add(user);
        await _db.SaveChangesAsync(ct);

        _logger.LogInformation("Registered user {UserId} ({Username})", user.Id, user.Username);
        return await IssueTokensAsync(user, ct);
    }

    public async Task<TokenPairResponse> LoginAsync(LoginRequest request, CancellationToken ct)
    {
        var normalized = request.Username.Trim().ToLowerInvariant();
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Username.ToLower() == normalized, ct)
                   ?? throw AppException.Unauthorized("Invalid credentials");

        if (!BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash))
            throw AppException.Unauthorized("Invalid credentials");

        return await IssueTokensAsync(user, ct);
    }

    public async Task<TokenPairResponse> RefreshAsync(string refreshToken, CancellationToken ct)
    {
        var hash = HashToken(refreshToken);
        var stored = await _db.RefreshTokens
            .Include(rt => rt.User)
            .FirstOrDefaultAsync(rt => rt.TokenHash == hash, ct)
            ?? throw AppException.Unauthorized("Invalid refresh token");

        if (!stored.IsActive)
            throw AppException.Unauthorized("Refresh token expired or revoked");

        // rotate
        stored.RevokedAt = DateTime.UtcNow;
        var pair = await IssueTokensAsync(stored.User, ct);
        await _db.SaveChangesAsync(ct);
        return pair;
    }

    public async Task LogoutAsync(string refreshToken, CancellationToken ct)
    {
        var hash = HashToken(refreshToken);
        var stored = await _db.RefreshTokens.FirstOrDefaultAsync(rt => rt.TokenHash == hash, ct);
        if (stored != null && stored.RevokedAt == null)
        {
            stored.RevokedAt = DateTime.UtcNow;
            await _db.SaveChangesAsync(ct);
        }
    }

    private async Task<TokenPairResponse> IssueTokensAsync(User user, CancellationToken ct)
    {
        var accessExpires = DateTime.UtcNow.AddMinutes(_jwt.AccessTokenMinutes);
        var access = CreateAccessToken(user, accessExpires);
        var refresh = GenerateRefreshToken();

        _db.RefreshTokens.Add(new RefreshToken
        {
            Id = Guid.NewGuid(),
            UserId = user.Id,
            TokenHash = HashToken(refresh),
            ExpiresAt = DateTime.UtcNow.AddDays(_jwt.RefreshTokenDays),
            CreatedAt = DateTime.UtcNow
        });
        await _db.SaveChangesAsync(ct);
        return new TokenPairResponse(access, refresh, accessExpires);
    }

    private string CreateAccessToken(User user, DateTime expires)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_jwt.Secret));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        var claims = new[]
        {
            new Claim(CurrentUser.UserIdClaim, user.Id.ToString()),
            new Claim(CurrentUser.UsernameClaim, user.Username),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };
        var token = new JwtSecurityToken(
            issuer: _jwt.Issuer,
            audience: _jwt.Audience,
            claims: claims,
            expires: expires,
            signingCredentials: creds);
        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    private static string GenerateRefreshToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(64);
        return Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    private static string HashToken(string token)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(token));
        return Convert.ToHexString(bytes);
    }
}
