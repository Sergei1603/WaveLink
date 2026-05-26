import { api, setStoredTokens } from "./client";
import type { AuthTokens } from "../types";

export async function register(email: string, password: string) {
  const tokens = await api<AuthTokens>("/api/auth/register", {
    body: { email, password }, skipAuth: true
  });
  setStoredTokens(tokens);
  return tokens;
}

export async function login(email: string, password: string) {
  const tokens = await api<AuthTokens>("/api/auth/login", {
    body: { email, password }, skipAuth: true
  });
  setStoredTokens(tokens);
  return tokens;
}

export async function logout(refreshToken: string) {
  try {
    await api<void>("/api/auth/logout", { body: { refreshToken } });
  } finally {
    setStoredTokens(null);
  }
}
