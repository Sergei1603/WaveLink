using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record RegisterRequest(
    [Required, EmailAddress] string Email,
    [Required, MinLength(8)] string Password);

public record LoginRequest(
    [Required, EmailAddress] string Email,
    [Required] string Password);

public record RefreshRequest([Required] string RefreshToken);

public record LogoutRequest([Required] string RefreshToken);

public record TokenPairResponse(string AccessToken, string RefreshToken, DateTime AccessTokenExpiresAt);
