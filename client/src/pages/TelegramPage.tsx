import { useState } from "react";
import { generateTelegramToken } from "../api/telegram";
import { useTelegram } from "../telegram/TelegramContext";

export function TelegramPage() {
  const { linked, botEnabled, refresh } = useTelegram();
  const [token, setToken] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const gen = async () => {
    setErr(null); setBusy(true);
    try {
      const t = await generateTelegramToken();
      setToken(t.token); setExpiresAt(t.expiresAt);
    } catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };

  const copy = () => {
    if (token) navigator.clipboard.writeText(`/link ${token}`);
  };

  return (
    <div className="page">
      <div className="page-header"><h1>Telegram</h1></div>

      <div className="card-stack">
        <div className="card">
          <h3>Привязать Telegram-аккаунт</h3>

          <p className="muted">
            {!botEnabled
              ? "Бот сейчас отключён на сервере."
              : linked
                ? "Чат привязан — в списках треков доступна кнопка ✈ для отправки трека в бот."
                : "Чат ещё не привязан."}
          </p>

          <ol className="steps">
            <li>Откройте бота WaveLink в Telegram и нажмите <code>/start</code>.</li>
            <li>Нажмите кнопку ниже, чтобы сгенерировать одноразовый токен (действует 10 минут).</li>
            <li>Отправьте боту команду <code>/link &lt;токен&gt;</code>.</li>
            <li>После привязки доступны команды:
              <ul>
                <li><code>/list</code> — ваша библиотека в виде кнопок (нажатие = получение файла).</li>
                <li><code>/find &lt;запрос&gt;</code> — поиск по общему банку (кнопки для скачивания).</li>
                <li><code>/get &lt;название&gt;</code> — точный поиск в библиотеке.</li>
                <li>Отправка аудиофайла — трек попадает в библиотеку и сразу публикуется в общем банке.</li>
                <li>Кнопка ✈ у трека в веб-интерфейсе — отправка файла в чат с ботом.</li>
              </ul>
            </li>
          </ol>

          <div className="token-actions">
            <button className="btn-primary" onClick={gen} disabled={busy}>
              {busy ? "Генерация…" : "Сгенерировать токен"}
            </button>
            <button className="btn-ghost" onClick={() => void refresh()}>
              Проверить привязку
            </button>
          </div>

          {err && <div className="error">{err}</div>}

          {token && (
            <div className="token-box">
              <div className="muted">Отправьте боту:</div>
              <code className="token-value">/link {token}</code>
              <div className="token-actions">
                <button className="btn-ghost" onClick={copy}>Скопировать</button>
                {expiresAt && (
                  <span className="muted">
                    истекает в {new Date(expiresAt).toLocaleTimeString("ru-RU")}
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
