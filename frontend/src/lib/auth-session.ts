const TOKEN_KEY = "flowguard.access_token";

export function getAccessToken(): string | null {
  const token = sessionStorage.getItem(TOKEN_KEY);
  return token && token.trim().length > 0 ? token.trim() : null;
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

export function isLoggedIn(): boolean {
  return getAccessToken() != null;
}
