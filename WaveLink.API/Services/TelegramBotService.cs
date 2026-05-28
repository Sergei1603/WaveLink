using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Telegram.Bot;
using Telegram.Bot.Polling;
using Telegram.Bot.Types;
using Telegram.Bot.Types.Enums;
using Telegram.Bot.Types.ReplyMarkups;
using WaveLink.API.Common;
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

    private static readonly BotCommand[] BotCommands =
    {
        new() { Command = "list",   Description = "Моя библиотека" },
        new() { Command = "find",   Description = "Поиск в общем банке" },
        new() { Command = "upload", Description = "Как загрузить трек" },
        new() { Command = "get",    Description = "Скачать трек по названию" },
        new() { Command = "link",   Description = "Привязать аккаунт" },
        new() { Command = "help",   Description = "Список команд" }
    };

    private static ReplyKeyboardMarkup MainKeyboard { get; } = new(new[]
    {
        new KeyboardButton[] { "/list", "/find" },
        new KeyboardButton[] { "/upload", "/help" }
    })
    {
        ResizeKeyboard = true,
        IsPersistent = true
    };

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!_options.Enabled || string.IsNullOrWhiteSpace(_options.BotToken))
        {
            _logger.LogInformation("Telegram bot disabled (set Telegram:Enabled=true and Telegram:BotToken to activate)");
            return;
        }

        var bot = new TelegramBotClient(_options.BotToken);

        try { await bot.SetMyCommands(BotCommands, cancellationToken: stoppingToken); }
        catch (Exception ex) { _logger.LogWarning(ex, "Failed to register bot commands"); }

        var receiverOptions = new ReceiverOptions
        {
            AllowedUpdates = new[] { UpdateType.Message, UpdateType.CallbackQuery }
        };

        bot.StartReceiving(
            updateHandler: HandleUpdateAsync,
            errorHandler: HandleErrorAsync,
            receiverOptions: receiverOptions,
            cancellationToken: stoppingToken);

        _logger.LogInformation("Telegram bot started");
    }

    private async Task HandleUpdateAsync(ITelegramBotClient bot, Update update, CancellationToken ct)
    {
        if (update.CallbackQuery is { } cb)
        {
            await SafeHandleCallbackAsync(bot, cb, ct);
            return;
        }

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
            else if (text == "/list" || text.StartsWith("/list ")) await HandleListAsync(bot, message, ct);
            else if (text.StartsWith("/upload")) await bot.SendMessage(message.Chat.Id,
                "Отправьте мне аудиофайл, и я добавлю его в вашу библиотеку.", cancellationToken: ct);
            else if (text.StartsWith("/get ")) await HandleGetAsync(bot, message, text[5..].Trim(), ct);
            else if (text == "/find" || text.StartsWith("/find "))
                await HandleFindAsync(bot, message, text.Length > 5 ? text[6..].Trim() : "", ct);
            else await bot.SendMessage(message.Chat.Id, "Неизвестная команда. Попробуйте /help.", cancellationToken: ct);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling Telegram update");
            try { await bot.SendMessage(message.Chat.Id, $"Ошибка: {ex.Message}", cancellationToken: ct); }
            catch { /* ignore */ }
        }
    }

    private Task HandleErrorAsync(ITelegramBotClient bot, Exception ex, HandleErrorSource source, CancellationToken ct)
    {
        _logger.LogError(ex, "Telegram polling error ({Source})", source);
        return Task.CompletedTask;
    }

    // ----- handlers -----

    private static Task SendStartAsync(ITelegramBotClient bot, Message m, CancellationToken ct) =>
        bot.SendMessage(m.Chat.Id,
            "Добро пожаловать в WaveLink!\n" +
            "1. Откройте веб-приложение и сгенерируйте токен привязки.\n" +
            "2. Отправьте сюда /link <токен>, чтобы связать этот чат с аккаунтом.\n" +
            "Команда /help покажет все доступные команды.",
            replyMarkup: MainKeyboard, cancellationToken: ct);

    private static Task SendHelpAsync(ITelegramBotClient bot, Message m, CancellationToken ct) =>
        bot.SendMessage(m.Chat.Id,
            "Команды:\n" +
            "/link <токен> — привязать чат к аккаунту WaveLink\n" +
            "/list — ваша библиотека (кнопки для скачивания)\n" +
            "/upload — загрузить трек (затем отправьте аудиофайл)\n" +
            "/get <название> — точный поиск в библиотеке и получение файла\n" +
            "/find <запрос> — поиск по общему банку (кнопки для скачивания)\n" +
            "/help — это сообщение",
            replyMarkup: MainKeyboard, cancellationToken: ct);

    private async Task HandleLinkAsync(ITelegramBotClient bot, Message m, string token, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var linker = scope.ServiceProvider.GetRequiredService<ITelegramLinkTokenService>();
        var user = await linker.ConsumeAsync(token, m.Chat.Id, ct);
        await bot.SendMessage(m.Chat.Id, $"Привязан к {user.Email}",
            replyMarkup: MainKeyboard, cancellationToken: ct);
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
            await bot.SendMessage(m.Chat.Id, "Чат не привязан. Используйте /link <токен>.", cancellationToken: ct);
            return;
        }

        var tracks = await db.Tracks
            .Where(t =>
                (t.UserId == user.Id && !t.IsDeletedByOwner) ||
                db.SavedTracks.Any(s => s.UserId == user.Id && s.TrackId == t.Id))
            .OrderByDescending(t => t.UploadedAt)
            .Take(20)
            .ToListAsync(ct);

        if (tracks.Count == 0)
        {
            await bot.SendMessage(m.Chat.Id, "Ваша библиотека пуста.", cancellationToken: ct);
            return;
        }

        var keyboard = BuildTrackKeyboard(tracks);
        await bot.SendMessage(m.Chat.Id, "Ваша библиотека (выберите трек):",
            replyMarkup: keyboard, cancellationToken: ct);
    }

    private async Task HandleFindAsync(ITelegramBotClient bot, Message m, string query, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(query))
        {
            await bot.SendMessage(m.Chat.Id,
                "Использование: /find <запрос> — поиск по общему банку.", cancellationToken: ct);
            return;
        }

        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "Чат не привязан. Используйте /link <токен>.", cancellationToken: ct);
            return;
        }

        var needle = $"%{query}%";
        var tracks = await db.Tracks
            .Where(t => !t.IsDeletedByOwner)
            .Where(t => EF.Functions.ILike(t.Title, needle) || EF.Functions.ILike(t.Artist, needle))
            .OrderByDescending(t => t.UploadedAt)
            .Take(20)
            .ToListAsync(ct);

        if (tracks.Count == 0)
        {
            await bot.SendMessage(m.Chat.Id, $"По запросу \"{query}\" ничего не найдено.", cancellationToken: ct);
            return;
        }

        var keyboard = BuildTrackKeyboard(tracks);
        await bot.SendMessage(m.Chat.Id, $"Результаты поиска \"{query}\":",
            replyMarkup: keyboard, cancellationToken: ct);
    }

    private async Task HandleGetAsync(ITelegramBotClient bot, Message m, string title, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();

        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "Чат не привязан. Используйте /link <токен>.", cancellationToken: ct);
            return;
        }

        var needle = $"%{title}%";
        var track = await db.Tracks
            .Where(t =>
                ((t.UserId == user.Id && !t.IsDeletedByOwner) ||
                 db.SavedTracks.Any(s => s.UserId == user.Id && s.TrackId == t.Id))
                && EF.Functions.ILike(t.Title, needle))
            .FirstOrDefaultAsync(ct);

        if (track == null)
        {
            await bot.SendMessage(m.Chat.Id, $"Трек по запросу \"{title}\" не найден.", cancellationToken: ct);
            return;
        }

        await SendTrackAudioAsync(bot, m.Chat.Id, track, storage, ct);
    }

    private async Task SafeHandleCallbackAsync(ITelegramBotClient bot, CallbackQuery cb, CancellationToken ct)
    {
        try
        {
            await HandleCallbackAsync(bot, cb, ct);
        }
        catch (AppException ax)
        {
            try { await bot.AnswerCallbackQuery(cb.Id, ax.Message, showAlert: true, cancellationToken: ct); }
            catch { /* ignore */ }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling Telegram callback");
            try { await bot.AnswerCallbackQuery(cb.Id, "Ошибка обработки запроса", showAlert: true, cancellationToken: ct); }
            catch { /* ignore */ }
        }
    }

    private async Task HandleCallbackAsync(ITelegramBotClient bot, CallbackQuery cb, CancellationToken ct)
    {
        var data = cb.Data ?? "";
        var chatId = cb.Message?.Chat.Id ?? cb.From.Id;

        if (!data.StartsWith("p:") || !Guid.TryParseExact(data[2..], "N", out var trackId))
        {
            await bot.AnswerCallbackQuery(cb.Id, "Неизвестное действие", showAlert: true, cancellationToken: ct);
            return;
        }

        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();
        var tracks = scope.ServiceProvider.GetRequiredService<ITrackService>();

        var user = await ResolveUserAsync(db, chatId, ct);
        if (user == null)
        {
            await bot.AnswerCallbackQuery(cb.Id, "Чат не привязан. Используйте /link <токен>.", showAlert: true, cancellationToken: ct);
            return;
        }

        var track = await tracks.GetAccessibleAsync(user.Id, trackId, ct);

        await bot.AnswerCallbackQuery(cb.Id, cancellationToken: ct);
        await SendTrackAudioAsync(bot, chatId, track, storage, ct);
    }

    private static InlineKeyboardMarkup BuildTrackKeyboard(IEnumerable<Track> tracks)
    {
        var rows = tracks
            .Select(t => new[]
            {
                InlineKeyboardButton.WithCallbackData(
                    $"{t.Artist} — {t.Title}",
                    $"p:{t.Id:N}")
            })
            .ToArray();
        return new InlineKeyboardMarkup(rows);
    }

    private static async Task SendTrackAudioAsync(
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

    private async Task HandleAudioAsync(ITelegramBotClient bot, Message m, CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var storage = scope.ServiceProvider.GetRequiredService<IMinioStorageService>();

        var user = await ResolveUserAsync(db, m.Chat.Id, ct);
        if (user == null)
        {
            await bot.SendMessage(m.Chat.Id, "Чат не привязан. Используйте /link <токен>.", cancellationToken: ct);
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

        var titleLower = title.Trim().ToLower();
        var artistLower = artist.Trim().ToLower();
        var hasDup = await db.Tracks.AnyAsync(t =>
            ((t.UserId == user.Id && !t.IsDeletedByOwner) ||
             db.SavedTracks.Any(s => s.UserId == user.Id && s.TrackId == t.Id))
            && t.Title.ToLower() == titleLower
            && t.Artist.ToLower() == artistLower, ct);
        if (hasDup)
        {
            await bot.SendMessage(m.Chat.Id, $"Уже в библиотеке: {artist} — {title}", cancellationToken: ct);
            return;
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
            UploadedAt = DateTime.UtcNow,
            IsPublic = false,
            IsDeletedByOwner = false
        });
        await db.SaveChangesAsync(ct);

        await bot.SendMessage(m.Chat.Id, $"Добавлено: {artist} — {title}", cancellationToken: ct);
    }
}
