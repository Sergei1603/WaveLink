import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Backdrop } from "../components/Backdrop";
import { Wordmark } from "../components/Wordmark";

export function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null); setBusy(true);
    try { await login(username, password); nav("/"); }
    catch (e: any) { setErr(e?.message ?? "Ошибка входа"); }
    finally { setBusy(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-panel">
        <Backdrop name="backdrop-auth" className="auth-bg" />

        <Wordmark />

        <div>
          <h1>С возвращением</h1>
          <div className="auth-lede">Ваша музыка ждёт там же, где вы её оставили.</div>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="login-username">Никнейм</label>
            <input id="login-username" className="input" type="text" required autoFocus
                   autoComplete="username" autoCapitalize="off" spellCheck={false}
                   value={username} onChange={e => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="login-password">Пароль</label>
            <input id="login-password" className="input" type="password" required
                   autoComplete="current-password"
                   value={password} onChange={e => setPassword(e.target.value)} />
          </div>

          {err && <div className="error">{err}</div>}

          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? "Вход…" : "Войти"}
          </button>

          <div className="muted small">
            Нет аккаунта? <Link to="/register">Создать</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
