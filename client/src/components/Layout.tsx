import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PlayerProvider } from "../player/PlayerContext";
import { TelegramProvider } from "../telegram/TelegramContext";
import { AppShellProvider, useAppShell } from "../app/AppShellContext";
import { NowPlaying } from "./NowPlaying";
import { UploadModal } from "./UploadModal";
import { Backdrop } from "./Backdrop";
import { Wordmark } from "./Wordmark";

function AccountMenu() {
  const { username, logout } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const handleLogout = async () => {
    await logout();
    nav("/login");
  };

  return (
    <div className="menu-wrap" ref={wrap}>
      <button
        className="avatar"
        onClick={() => setOpen(v => !v)}
        title={username ?? "Аккаунт"}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        {(username?.[0] ?? "?").toUpperCase()}
      </button>
      {open && (
        <div className="popover account-popover" role="menu">
          <div className="account-name">{username ?? "Аккаунт"}</div>
          <hr className="hr" style={{ margin: "4px 0" }} />
          <button className="popover-item" onClick={handleLogout}>Выйти</button>
        </div>
      )}
    </div>
  );
}

export function Layout() {
  const [uploadOpen, setUploadOpen] = useState(false);
  const openUpload = useCallback(() => setUploadOpen(true), []);

  return (
    <TelegramProvider>
      <PlayerProvider>
        <AppShellProvider openUpload={openUpload}>
          <ShellBody uploadOpen={uploadOpen} onCloseUpload={() => setUploadOpen(false)} />
        </AppShellProvider>
      </PlayerProvider>
    </TelegramProvider>
  );
}

/** Inside the provider so the header and the dialog can use the shell state. */
function ShellBody({
  uploadOpen, onCloseUpload
}: { uploadOpen: boolean; onCloseUpload: () => void }) {
  return (
    <div className="app-shell">
      <Backdrop />

      <header className="topbar">
        <Wordmark to="/" />
        <nav className="topnav">
          <NavLink to="/" end>Библиотека</NavLink>
          <NavLink to="/public">Общий банк</NavLink>
          <NavLink to="/collections">Коллекции</NavLink>
          <NavLink to="/stats">Статистика</NavLink>
          <NavLink to="/telegram">Telegram</NavLink>
        </nav>
        <div className="topbar-right">
          <UploadButton />
          <AccountMenu />
        </div>
      </header>

      <div className="shell-body">
        <main className="content">
          <Outlet />
        </main>
        <NowPlaying />
      </div>

      {uploadOpen && <UploadDialog onClose={onCloseUpload} />}
    </div>
  );
}

function UploadButton() {
  const { openUpload } = useAppShell();
  return <button className="btn btn-primary" onClick={openUpload}>Загрузить</button>;
}

function UploadDialog({ onClose }: { onClose: () => void }) {
  const { bumpTracks, refreshCollections } = useAppShell();
  return (
    <UploadModal
      onClose={onClose}
      onUploaded={() => { bumpTracks(); void refreshCollections(); }}
    />
  );
}
