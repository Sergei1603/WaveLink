import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Backdrop } from "../components/Backdrop";
import { Wordmark } from "../components/Wordmark";

export function RegisterPage() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirm) { setErr("Пароли не совпадают"); return; }
    if (password.length < 8) { setErr("Пароль должен содержать не менее 8 символов"); return; }
    setErr(null); setBusy(true);
    try { await register(email, password); nav("/"); }
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
            <label htmlFor="reg-email">Email</label>
            <input id="reg-email" className="input" type="email" required autoFocus
                   value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-password">Пароль</label>
            <input id="reg-password" className="input" type="password" required
                   value={password} onChange={e => setPassword(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-confirm">Подтвердите пароль</label>
            <input id="reg-confirm" className="input" type="password" required
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
