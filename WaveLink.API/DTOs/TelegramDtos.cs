using System.ComponentModel.DataAnnotations;

namespace WaveLink.API.DTOs;

public record GenerateLinkTokenResponse(string Token, DateTime ExpiresAt);

public record LinkTelegramRequest([Required] string Token);

public record ErrorResponse(string Error, int StatusCode);
