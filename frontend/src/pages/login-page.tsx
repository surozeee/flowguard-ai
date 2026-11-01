import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { isLoggedIn } from "@/lib/auth-session";
import { login } from "@/shared/services/auth.service";

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnUrl = searchParams.get("returnUrl");
  const [username, setUsername] = useState("investigator");
  const [password, setPassword] = useState("investigator123");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (isLoggedIn()) {
    const dest = returnUrl && returnUrl.startsWith("/") ? returnUrl : "/";
    return <Navigate to={dest} replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
      navigate(returnUrl && returnUrl.startsWith("/") ? returnUrl : "/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="card">
      <div className="page-header">
        <h2>Sign in</h2>
        <p>Investigator or ops credentials are issued for this demo environment only.</p>
      </div>
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        {error ? <p className="error">{error}</p> : null}
        <button type="submit" disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="muted" style={{ marginTop: "1rem" }}>
        investigator / investigator123 · ops / ops123
      </p>
      <p>
        <Link to="/">Back to home</Link>
      </p>
    </section>
  );
}
