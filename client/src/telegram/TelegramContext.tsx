import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { getTelegramStatus } from "../api/telegram";
import type { TelegramStatus } from "../types";

interface TelegramState extends TelegramStatus {
  /** Перечитать статус — например, после привязки чата. */
  refresh: () => Promise<void>;
}

const Ctx = createContext<TelegramState | null>(null);

export function TelegramProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<TelegramStatus>({ botEnabled: false, linked: false });

  const refresh = useCallback(async () => {
    try { setStatus(await getTelegramStatus()); }
    catch { setStatus({ botEnabled: false, linked: false }); }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const value = useMemo<TelegramState>(() => ({ ...status, refresh }), [status, refresh]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTelegram(): TelegramState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useTelegram must be used inside TelegramProvider");
  return v;
}
