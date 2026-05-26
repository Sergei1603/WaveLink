namespace WaveLink.API.Options;

public class TelegramOptions
{
    public const string SectionName = "Telegram";

    public string BotToken { get; set; } = "";
    public bool Enabled { get; set; } = false;
}
