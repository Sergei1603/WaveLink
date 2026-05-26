import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null); setBusy(true);
    try { await login(email, password); nav("/"); }
    catch (e: any) { setErr(e?.message ?? "Ошибка входа"); }
    finally { setBusy(false); }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>Вход в WaveLink</h1>
        <label>
          Email
          <input type="email" required value={email} onChange={e => setEmail(e.target.value)} autoFocus />
        </label>
        <label>
          Пароль
          <input type="password" required value={password} onChange={e => setPassword(e.target.value)} />
        </label>
        {err && <div className="error">{err}</div>}
        <button type="submit" disabled={busy} className="btn-primary">
          {busy ? "Вход…" : "Войти"}
        </button>
        <p className="muted">
          Нет аккаунта? <Link to="/register">Создать</Link>
        </p>
      </form>
    </div>
  );
}
