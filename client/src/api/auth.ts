import { api, setStoredTokens } from "./client";
import type { AuthTokens } from "../types";

export async function register(username: string, password: string) {
  const tokens = await api<AuthTokens>("/api/auth/register", {
    body: { username, password }, skipAuth: true
  });
  setStoredTokens(tokens);
  return tokens;
}

export async function login(username: string, password: string) {
  const tokens = await api<AuthTokens>("/api/auth/login", {
    body: { username, password }, skipAuth: true
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
