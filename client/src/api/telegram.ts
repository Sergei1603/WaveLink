import { api } from "./client";
import type { TelegramLinkToken, TelegramStatus } from "../types";

export const generateTelegramToken = () =>
  api<TelegramLinkToken>("/api/telegram/generate-link-token");

export const getTelegramStatus = () =>
  api<TelegramStatus>("/api/telegram/status");

export const sendTrackToTelegram = (trackId: string) =>
  api<void>("/api/telegram/send", { method: "POST", body: { trackId } });
