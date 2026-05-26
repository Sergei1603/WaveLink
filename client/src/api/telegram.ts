import { api } from "./client";
import type { TelegramLinkToken } from "../types";

export const generateTelegramToken = () =>
  api<TelegramLinkToken>("/api/telegram/generate-link-token");
