using Microsoft.Extensions.Options;
using Telegram.Bot;
using WaveLink.API.Options;

namespace WaveLink.API.Services;

/// <summary>
/// Единая точка доступа к клиенту бота: и polling-сервис, и HTTP-контроллеры
/// работают через один и тот же экземпляр. <see cref="Client"/> равен null,
/// когда бот выключен в конфигурации.
/// </summary>
public interface ITelegramClientProvider
{
    ITelegramBotClient? Client { get; }
    bool Enabled { get; }
}

public class TelegramClientProvider : ITelegramClientProvider
{
    public TelegramClientProvider(IOptions<TelegramOptions> options)
    {
        var o = options.Value;
        Enabled = o.Enabled && !string.IsNullOrWhiteSpace(o.BotToken);
        Client = Enabled ? new TelegramBotClient(o.BotToken) : null;
    }

    public ITelegramBotClient? Client { get; }
    public bool Enabled { get; }
}
