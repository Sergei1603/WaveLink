import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Backdrop } from "../components/Backdrop";
import { Wordmark } from "../components/Wordmark";

const USERNAME_RE = /^[A-Za-z0-9._-]{3,32}$/;

export function RegisterPage() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!USERNAME_RE.test(username)) {
      setErr("Никнейм: 3–32 символа, латиница, цифры, точка, дефис или подчёркивание");
      return;
    }
    if (password !== confirm) { setErr("Пароли не совпадают"); return; }
    if (password.length < 8) { setErr("Пароль должен содержать не менее 8 символов"); return; }
    setErr(null); setBusy(true);
    try { await register(username, password); nav("/"); }
    catch (e: any) { setErr(e?.message ?? "Ошибка регистрации"); }
    finally { setBusy(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-panel">
        <Backdrop name="backdrop-auth" className="auth-bg" />

        <Wordmark />

        <div>
          <h1>Своя фонотека</h1>
          <div className="auth-lede">
            Загружайте треки, собирайте коллекции, слушайте в браузере и в Telegram.
          </div>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="reg-username">Никнейм</label>
            <input id="reg-username" className="input" type="text" required autoFocus
                   autoComplete="username" autoCapitalize="off" spellCheck={false}
                   minLength={3} maxLength={32}
                   value={username} onChange={e => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-password">Пароль</label>
            <input id="reg-password" className="input" type="password" required
                   autoComplete="new-password"
                   value={password} onChange={e => setPassword(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-confirm">Подтвердите пароль</label>
            <input id="reg-confirm" className="input" type="password" required
                   autoComplete="new-password"
                   value={confirm} onChange={e => setConfirm(e.target.value)} />
          </div>

          {err && <div className="error">{err}</div>}

          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? "Создание…" : "Создать аккаунт"}
          </button>

          <div className="muted small">
            Уже есть аккаунт? <Link to="/login">Войти</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
