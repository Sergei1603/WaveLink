import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { getStoredTokens, setStoredTokens } from "../api/client";
import * as authApi from "../api/auth";
import type { AuthTokens } from "../types";

interface AuthState {
  tokens: AuthTokens | null;
  username: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

function decodeUsername(accessToken: string): string | null {
  try {
    const payload = JSON.parse(atob(accessToken.split(".")[1]));
    return payload.username ?? payload.unique_name ?? null;
  } catch { return null; }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokens] = useState<AuthTokens | null>(() => getStoredTokens());

  useEffect(() => {
    setStoredTokens(tokens);
  }, [tokens]);

  const value = useMemo<AuthState>(() => ({
    tokens,
    username: tokens ? decodeUsername(tokens.accessToken) : null,
    isAuthenticated: !!tokens,
    login: async (u, p) => { setTokens(await authApi.login(u, p)); },
    register: async (u, p) => { setTokens(await authApi.register(u, p)); },
    logout: async () => {
      const rt = tokens?.refreshToken;
      if (rt) await authApi.logout(rt);
      setTokens(null);
    }
  }), [tokens]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth must be used inside AuthProvider");
  return v;
}
