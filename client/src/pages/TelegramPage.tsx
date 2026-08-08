import { useState } from "react";
import { generateTelegramToken } from "../api/telegram";
import { useTelegram } from "../telegram/TelegramContext";

const STEPS = [
  "Откройте бота WaveLink в Telegram и нажмите /start.",
  "Сгенерируйте одноразовый токен — он действует 10 минут.",
  "Отправьте боту команду /link с этим токеном."
];

const COMMANDS = [
  { cmd: "/list", desc: "Библиотека кнопками — нажатие отдаёт файл" },
  { cmd: "/find запрос", desc: "Поиск по общему банку" },
  { cmd: "/get название", desc: "Точный поиск в библиотеке" },
  { cmd: "аудиофайл", desc: "Трек попадает в библиотеку и общий банк" }
];

export function TelegramPage() {
  const { linked, botEnabled, refresh } = useTelegram();
  const [token, setToken] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const gen = async () => {
    setErr(null); setBusy(true);
    try {
      const t = await generateTelegramToken();
      setToken(t.token); setExpiresAt(t.expiresAt); setCopied(false);
    } catch (e: any) { setErr(e.message); }
    finally { setBusy(false); }
  };

  const copy = async () => {
    if (!token) return;
    await navigator.clipboard.writeText(`/link ${token}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const statusTag = !botEnabled
    ? <span className="tag tag-neutral">Бот отключён на сервере</span>
    : linked
      ? <span className="tag tag-accent">Чат привязан</span>
      : <span className="tag tag-outline">Чат не привязан</span>;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Telegram</h1>
          <div className="page-sub">
            Привяжите чат — и библиотека станет доступна прямо в мессенджере.
          </div>
        </div>
      </div>

      <div className="tg-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <div>{statusTag}</div>

          <div className="tg-steps">
            {STEPS.map((text, i) => (
              <div key={i} className="tg-step">
                <span className="n">{i + 1}</span>
                <span className="text">{text}</span>
              </div>
            ))}
          </div>

          <div className="page-actions">
            <button className="btn btn-primary" onClick={gen} disabled={busy || !botEnabled}>
              {busy ? "Генерация…" : "Сгенерировать токен"}
            </button>
            <button className="btn btn-secondary" onClick={() => void refresh()}>
              Проверить привязку
            </button>
          </div>

          {err && <div className="error">{err}</div>}

          {token && (
            <div className="tg-token">
              <div className="muted small">Отправьте боту</div>
              <div className="tg-token-row">
                <code>/link {token}</code>
                <button className="btn btn-ghost btn-sm" onClick={copy}>
                  {copied ? "Скопировано" : "Скопировать"}
                </button>
              </div>
              {expiresAt && (
                <div className="muted" style={{ fontSize: 11 }}>
                  Истекает в {new Date(expiresAt).toLocaleTimeString("ru-RU")}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="card">
          <h6 className="section-label">Команды бота</h6>
          <div className="tg-commands">
            {COMMANDS.map(c => (
              <div key={c.cmd} className="tg-command">
                <code>{c.cmd}</code>
                <span>{c.desc}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
