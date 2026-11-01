import { getAccessToken } from "@/lib/auth-session";

export function resolveApiKeyFromEnv(): string | null {
  const key = import.meta.env.VITE_FLOWGUARD_API_KEY;
  if (typeof key !== "string") {
    return null;
  }
  const trimmed = key.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function hasAuthenticatedSession(): boolean {
  return getAccessToken() != null || resolveApiKeyFromEnv() != null;
}
