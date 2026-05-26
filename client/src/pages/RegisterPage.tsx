import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

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
      <form className="auth-card" onSubmit={submit}>
        <h1>Регистрация в WaveLink</h1>
        <label>
          Email
          <input type="email" required value={email} onChange={e => setEmail(e.target.value)} autoFocus />
        </label>
        <label>
          Пароль
          <input type="password" required value={password} onChange={e => setPassword(e.target.value)} />
        </label>
        <label>
          Подтвердите пароль
          <input type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} />
        </label>
        {err && <div className="error">{err}</div>}
        <button type="submit" disabled={busy} className="btn-primary">
          {busy ? "Создание…" : "Создать аккаунт"}
        </button>
        <p className="muted">
          Уже есть аккаунт? <Link to="/login">Войти</Link>
        </p>
      </form>
    </div>
  );
}
