using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Telegram.Bot;
using Telegram.Bot.Polling;
using Telegram.Bot.Types;
using Telegram.Bot.Types.Enums;
using WaveLink.API.Data;
using WaveLink.API.Entities;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

public class TelegramBotService : BackgroundService
{
    private readonly TelegramOptions _options;
    private readonly IServiceProvider _services;
    private readonly ILogger<TelegramBotService> _logger;

    public TelegramBotService(
        IOptions<TelegramOptions> options,
        IServiceProvider services,
        ILogger<TelegramBotService> logger)
    {
        _options = options.Value;
        _services = services;
        _logger = logger;
    }

    protected override Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!_options.Enabled || string.IsNullOrWhiteSpace(_options.BotToken))
        {
            _logger.LogInformation("Telegram bot disabled (set Telegram:Enabled=true and Telegram:BotToken to activate)");
            return Task.CompletedTask;
        }

        var bot = new TelegramBotClient(_options.BotToken);
        var receiverOptions = new ReceiverOptions
        {
            AllowedUpdates = new[] { UpdateType.Message }
        };

        bot.StartReceiving(
            updateHandler: HandleUpdateAsync,
            errorHandler: HandleErrorAsync,
            receiverOptions: receiverOptions,
            cancellationToken: stoppingToken);

        _logger.LogInformation("Telegram bot started");
        return Task.CompletedTask;
    }

    private async Task HandleUpdateAsync(ITelegramBotClient bot, Update update, CancellationToken ct)
    {
        if (update.Message is not { } message) return;

        try
        {
            if (message.Audio != null || message.Document?.MimeType?.StartsWith("audio/") == true)
            {
                await HandleAudioAsync(bot, message, ct);
                return;
            }

            var text = message.Text?.Trim() ?? "";
            if (text.StartsWith("/start")) await SendStartAsync(bot, message, ct);
            else if (text.StartsWith("/help")) await SendHelpAsync(bot, message, ct);
            else if (text.StartsWith("/link ")) await HandleLinkAsync(bot, message, text[6..].Trim(), ct);
            else if (text.StartsWith("/list")) await HandleListAsync(bot, message, ct);
            else if (text.StartsWith("/upload")) await bot.SendMessage(message.Chat.Id,
                "Send me an audio file as a message and I'll add it to your library.", cancellationToken: ct);
            else if (text.StartsWith("/get ")) await HandleGetAsync(bot, message, text[5..].Trim(), ct);
            else await bot.SendMessage(message.Chat.Id, "Unknown command. Try /help.", cancellationToken: ct);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling Telegram update");
            try { await bot.SendMessage(message.Chat.Id, $"Error: {ex.Message}", cancellationToken: ct); }
            catch { /* ignore */ }
        }
    }

    private Task HandleErrorAsync(ITelegramBotClient bot, Exception ex, CancellationToken ct)
    {
        _logger.LogError(ex, "Telegram polling error");
        return Task.CompletedTask;
    }

    // ----- handlers -----

    private static Task SendStartAsync(ITelegramBotClient bot, Message m, CancellationToken ct) =>
        bot.SendMessage(m.Chat.Id,
            "Welcome to WaveLink!\n" +
            "1. Open the web app and generate a link token.\n" +
            "2. Send /link <token> here to connect this chat to your account.\n" +
            "Use /help to see all commands.", cancellationToken: ct);

    private static Task SendHelpAsync(ITelegramBotClient bot, Message m, CancellationToken ct) =>
        bot.SendMessage(m.Chat.Id,
            "Commands:\n" +
            "/link <token> - link this chat to your WaveLink account\n" +
            "/list - list your tracks\n" +
            "/upload - upload a track (then send an audio file)\n" +
            "/get <title> - search your library and receive the audio file\n" +
            "/help - show this message", cancellationToken: ct);

    private async Task HandleLinkAsync(ITelegramBotClient bot, Message m, string token, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var linker = scope.ServiceProvider.GetRequiredService<ITelegramLinkTokenService>();
        var user = await linker.ConsumeAsync(token, m.Chat.Id, ct);
        await bot.SendMessage(m.Chat.Id, $"Linked to {user.Email}", cancellationToken: ct);
    }

    private async Task<WaveLink.API.Entities.User?> ResolveUserAsync(AppDbContext db, long chatId, CancellationToken ct) =>
        await db.Users.FirstOrDefaultAsync(u => u.TelegramChatId == chatId, ct);

    private async Task HandleListAsync(ITelegramBotClient bot, Message m, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "This chat is not linked. Use /link <token>.", cancellationToken: ct);
            return;
        }

        var tracks = await db.Tracks
            .Where(t => t.UserId == user.Id)
            .OrderByDescending(t => t.UploadedAt)
            .Take(20)
            .ToListAsync(ct);

        if (tracks.Count == 0)
        {
            await bot.SendMessage(m.Chat.Id, "Your library is empty.", cancellationToken: ct);
            return;
        }

        var text = string.Join("\n", tracks.Select((t, i) => $"{i + 1}. {t.Artist} - {t.Title}"));
        await bot.SendMessage(m.Chat.Id, text, cancellationToken: ct);
    }

    private async Task HandleGetAsync(ITelegramBotClient bot, Message m, string title, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();

        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "This chat is not linked. Use /link <token>.", cancellationToken: ct);
            return;
        }

        var needle = $"%{title}%";
        var track = await db.Tracks
            .Where(t => t.UserId == user.Id && EF.Functions.ILike(t.Title, needle))
            .FirstOrDefaultAsync(ct);

        if (track == null)
        {
            await bot.SendMessage(m.Chat.Id, $"No track matching \"{title}\"", cancellationToken: ct);
            return;
        }

        await using var data = await storage.OpenReadAsync(track.FileKey, ct);
        await bot.SendAudio(m.Chat.Id,
            InputFile.FromStream(data, $"{track.Title}.mp3"),
            title: track.Title,
            performer: track.Artist,
            duration: track.Duration,
            cancellationToken: ct);
    }

    private async Task HandleAudioAsync(ITelegramBotClient bot, Message m, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();

        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "This chat is not linked. Use /link <token>.", cancellationToken: ct);
            return;
        }

        string fileId, fileName, mime;
        int duration;
        long size;
        string title, artist;

        if (m.Audio is { } audio)
        {
            fileId = audio.FileId;
            fileName = audio.FileName ?? $"{audio.FileId}.mp3";
            mime = audio.MimeType ?? "audio/mpeg";
            duration = audio.Duration;
            size = audio.FileSize ?? 0;
            title = audio.Title ?? Path.GetFileNameWithoutExtension(fileName);
            artist = audio.Performer ?? "Unknown";
        }
        else
        {
            var doc = m.Document!;
            fileId = doc.FileId;
            fileName = doc.FileName ?? $"{doc.FileId}.bin";
            mime = doc.MimeType ?? "application/octet-stream";
            duration = 0;
            size = doc.FileSize ?? 0;
            title = Path.GetFileNameWithoutExtension(fileName);
            artist = "Unknown";
        }

        var trackId = Guid.NewGuid();
        var key = $"{user.Id}/{trackId}/{fileName}";

        using var ms = new MemoryStream();
        await bot.GetInfoAndDownloadFile(fileId, ms, ct);
        ms.Position = 0;

        await storage.UploadAsync(key, ms, ms.Length, mime, ct);

        db.Tracks.Add(new Track
        {
            Id = trackId,
            UserId = user.Id,
            Title = title,
            Artist = artist,
            Duration = duration,
            FileKey = key,
            FileSize = size > 0 ? size : ms.Length,
            MimeType = mime,
            UploadedAt = DateTime.UtcNow
        });
        await db.SaveChangesAsync(ct);

        await bot.SendMessage(m.Chat.Id, $"Added: {artist} - {title}", cancellationToken: ct);
    }
}
