import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { getStoredTokens, setStoredTokens } from "../api/client";
import * as authApi from "../api/auth";
import type { AuthTokens } from "../types";

interface AuthState {
  tokens: AuthTokens | null;
  email: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

function decodeEmail(accessToken: string): string | null {
  try {
    const payload = JSON.parse(atob(accessToken.split(".")[1]));
    return payload.email ?? payload.unique_name ?? null;
  } catch { return null; }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setTokens] = useState<AuthTokens | null>(() => getStoredTokens());

  useEffect(() => {
    setStoredTokens(tokens);
  }, [tokens]);

  const value = useMemo<AuthState>(() => ({
    tokens,
    email: tokens ? decodeEmail(tokens.accessToken) : null,
    isAuthenticated: !!tokens,
    login: async (e, p) => { setTokens(await authApi.login(e, p)); },
    register: async (e, p) => { setTokens(await authApi.register(e, p)); },
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
