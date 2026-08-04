using Microsoft.EntityFrameworkCore;
using Telegram.Bot;
using Telegram.Bot.Exceptions;
using Telegram.Bot.Types;
using WaveLink.API.Common;
using WaveLink.API.Data;
using WaveLink.API.DTOs;
using WaveLink.API.Entities;

namespace WaveLink.API.Services;

public interface ITelegramDeliveryService
{
    Task<TelegramStatusResponse> GetStatusAsync(Guid userId, CancellationToken ct);
    Task SendTrackAsync(Guid userId, Guid trackId, CancellationToken ct);
}

public class TelegramDeliveryService : ITelegramDeliveryService
{
    private readonly AppDbContext _db;
    private readonly ITrackService _tracks;
    private readonly IMinioStorageService _storage;
    private readonly ITelegramClientProvider _telegram;
    private readonly ILogger<TelegramDeliveryService> _logger;

    public TelegramDeliveryService(
        AppDbContext db,
        ITrackService tracks,
        IMinioStorageService storage,
        ITelegramClientProvider telegram,
        ILogger<TelegramDeliveryService> logger)
    {
        _db = db;
        _tracks = tracks;
        _storage = storage;
        _telegram = telegram;
        _logger = logger;
    }

    public async Task<TelegramStatusResponse> GetStatusAsync(Guid userId, CancellationToken ct)
    {
        var chatId = await GetChatIdAsync(userId, ct);
        return new TelegramStatusResponse(_telegram.Enabled, chatId != null);
    }

    public async Task SendTrackAsync(Guid userId, Guid trackId, CancellationToken ct)
    {
        var bot = _telegram.Client
                  ?? throw new AppException("Telegram-бот отключён на сервере", 503);

        var chatId = await GetChatIdAsync(userId, ct)
                     ?? throw new AppException("Telegram не привязан. Привяжите чат командой /link.");

        var track = await _tracks.GetAccessibleAsync(userId, trackId, ct);

        try
        {
            await SendTrackAudioAsync(bot, chatId, track, _storage, ct);
        }
        catch (ApiRequestException ex)
        {
            _logger.LogWarning(ex, "Telegram rejected sending track {TrackId} to chat {ChatId}", trackId, chatId);
            throw new AppException($"Telegram отклонил отправку: {ex.Message}", 502);
        }

        _logger.LogInformation("Sent track {TrackId} to Telegram chat {ChatId}", trackId, chatId);
    }

    private Task<long?> GetChatIdAsync(Guid userId, CancellationToken ct) =>
        _db.Users
            .Where(u => u.Id == userId)
            .Select(u => u.TelegramChatId)
            .FirstOrDefaultAsync(ct);

    /// <summary>
    /// Общая для бота и API-контроллера отправка аудиофайла в чат.
    /// </summary>
    public static async Task SendTrackAudioAsync(
        ITelegramBotClient bot, long chatId, Track track,
        IMinioStorageService storage, CancellationToken ct)
    {
        await using var data = await storage.OpenReadAsync(track.FileKey, ct);
        var filename = Path.GetFileName(track.FileKey);
        if (string.IsNullOrWhiteSpace(filename)) filename = $"{track.Title}.mp3";

        await bot.SendAudio(chatId,
            InputFile.FromStream(data, filename),
            title: track.Title,
            performer: track.Artist,
            duration: track.Duration,
            cancellationToken: ct);
    }
}
