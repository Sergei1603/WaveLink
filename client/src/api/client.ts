import type { AuthTokens } from "../types";

const TOKEN_KEY = "wavelink.tokens";

export const API_BASE = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");
export const apiUrl = (path: string) =>
  path.startsWith("http") ? path : `${API_BASE}${path}`;

export function getStoredTokens(): AuthTokens | null {
  const raw = localStorage.getItem(TOKEN_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as AuthTokens; }
  catch { return null; }
}

export function setStoredTokens(t: AuthTokens | null) {
  if (t) localStorage.setItem(TOKEN_KEY, JSON.stringify(t));
  else localStorage.removeItem(TOKEN_KEY);
}

let refreshPromise: Promise<AuthTokens | null> | null = null;

async function refreshTokens(): Promise<AuthTokens | null> {
  const current = getStoredTokens();
  if (!current) return null;
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(apiUrl("/api/auth/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: current.refreshToken })
      });
      if (!res.ok) { setStoredTokens(null); return null; }
      const tokens = await res.json() as AuthTokens;
      setStoredTokens(tokens);
      return tokens;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  formData?: FormData;
  skipAuth?: boolean;
  signal?: AbortSignal;
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (opts.body && !opts.formData) headers["Content-Type"] = "application/json";

    if (!opts.skipAuth) {
      const tokens = getStoredTokens();
      if (tokens) headers["Authorization"] = `Bearer ${tokens.accessToken}`;
    }

    return fetch(apiUrl(path), {
      method: opts.method ?? (opts.body || opts.formData ? "POST" : "GET"),
      headers,
      body: opts.formData
        ? opts.formData
        : opts.body
          ? JSON.stringify(opts.body)
          : undefined,
      signal: opts.signal
    });
  };

  let res = await send();

  if (res.status === 401 && !opts.skipAuth) {
    const refreshed = await refreshTokens();
    if (refreshed) res = await send();
  }

  if (!res.ok) {
    let msg = res.statusText;
    try {
      const body = await res.json();
      if (body?.error) msg = body.error;
    } catch { /* ignore */ }
    throw new ApiError(msg, res.status);
  }

  if (res.status === 204) return undefined as T;

  const ct = res.headers.get("Content-Type") ?? "";
  if (ct.includes("application/json")) return res.json() as Promise<T>;
  return undefined as T;
}

export function authHeader(): Record<string, string> {
  const t = getStoredTokens();
  return t ? { Authorization: `Bearer ${t.accessToken}` } : {};
}
