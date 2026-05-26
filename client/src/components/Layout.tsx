import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PlayerProvider } from "../player/PlayerContext";
import { PlayerBar } from "./PlayerBar";

export function Layout() {
  const { email, logout } = useAuth();
  const nav = useNavigate();

  const handleLogout = async () => {
    await logout();
    nav("/login");
  };

  return (
    <PlayerProvider>
      <div className="app-shell">
        <header className="topbar">
          <Link to="/" className="brand">WaveLink</Link>
          <nav className="nav">
            <NavLink to="/" end>Library</NavLink>
            <NavLink to="/collections">Collections</NavLink>
            <NavLink to="/telegram">Telegram</NavLink>
          </nav>
          <div className="topbar-right">
            <span className="user-email">{email ?? "Account"}</span>
            <button className="btn-ghost" onClick={handleLogout}>Logout</button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>

        <PlayerBar />
      </div>
    </PlayerProvider>
  );
}
