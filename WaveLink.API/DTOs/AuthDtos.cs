using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record RegisterRequest(
    [Required, RegularExpression(UsernameRules.Pattern, ErrorMessage = UsernameRules.Message)]
    string Username,
    [Required, MinLength(8)] string Password);

public record LoginRequest(
    [Required] string Username,
    [Required] string Password);

public record RefreshRequest([Required] string RefreshToken);

public record LogoutRequest([Required] string RefreshToken);

public record TokenPairResponse(string AccessToken, string RefreshToken, DateTime AccessTokenExpiresAt);

public static class UsernameRules
{
    public const int MinLength = 3;
    public const int MaxLength = 32;

    /// <summary>Латиница, цифры, точка, дефис и подчёркивание; 3–32 символа.</summary>
    public const string Pattern = @"^[A-Za-z0-9._-]{3,32}$";

    public const string Message =
        "Никнейм должен содержать от 3 до 32 символов: латиница, цифры, точка, дефис или подчёркивание";
}
