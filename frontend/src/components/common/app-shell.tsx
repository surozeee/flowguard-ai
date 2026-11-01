import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { clearAccessToken, isLoggedIn } from "@/lib/auth-session";

const workspace = [
  { to: "/", label: "Overview", hint: "System health", end: true, icon: "home" },
  { to: "/present", label: "Present", hint: "Enterprise briefing", icon: "present" },
  { to: "/investigate", label: "Investigate", hint: "Start a run", icon: "search" },
  { to: "/transactions/new", label: "Create credit", hint: "Idempotent payment", icon: "card" },
  { to: "/transactions/saga", label: "Create transfer", hint: "Saga choreography", icon: "flow" },
  { to: "/evaluations", label: "Evaluations", hint: "Benchmark arms", icon: "chart" },
];

const operations = [
  { to: "/dashboard", label: "Dashboard", hint: "Processing KPIs", icon: "grid" },
  { to: "/ops", label: "Operations", hint: "Inbox & DLQ", icon: "pulse" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const signedIn = isLoggedIn();
  const authPage = location.pathname === "/login";
  const presentPage = location.pathname === "/present";

  return (
    <div className={presentPage ? "app-shell present" : authPage ? "app-shell auth" : "app-shell"}>
      <aside className="sidebar" aria-label="Primary" hidden={presentPage} aria-hidden={presentPage}>
        <NavLink to="/" className="brand" end>
          <span className="brand-mark" aria-hidden>
            FG
          </span>
          <span>
            <h1>FlowGuard</h1>
            <p>LedgerGuard</p>
          </span>
        </NavLink>

        <nav className="menu" key={location.pathname + String(signedIn)}>
          <p className="menu-label">Workspace</p>
          {workspace.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="menu-item">
              <MenuIcon name={item.icon} />
              <span>
                <strong>{item.label}</strong>
                <em>{item.hint}</em>
              </span>
            </NavLink>
          ))}
          <p className="menu-label">Control plane</p>
          {operations.map((item) => (
            <NavLink key={item.to} to={item.to} className="menu-item">
              <MenuIcon name={item.icon} />
              <span>
                <strong>{item.label}</strong>
                <em>{item.hint}</em>
              </span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-foot">
          {signedIn ? (
            <button
              type="button"
              className="session-btn"
              onClick={() => {
                clearAccessToken();
                navigate("/login");
              }}
            >
              Sign out
            </button>
          ) : (
            <NavLink to="/login" className="session-btn">
              Sign in
            </NavLink>
          )}
        </div>
      </aside>

      <div className="workspace">
        <header className="context-bar">
          <div>
            <p className="eyebrow">FlowGuard AI — LedgerGuard</p>
            <p className="context-copy">Independent Java and SQL checks decide. Models only hypothesize.</p>
          </div>
          <span className={signedIn ? "session-chip on" : "session-chip"}>
            <span className="dot" />
            {signedIn ? "Authenticated session" : "Public session"}
          </span>
        </header>
        <main className="workspace-main">{children}</main>
      </div>
    </div>
  );
}

function MenuIcon({ name }: { name: string }) {
  const common = {
    width: 18,
    height: 18,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };
  switch (name) {
    case "search":
      return (
        <svg {...common}>
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.2-3.2" />
        </svg>
      );
    case "chart":
      return (
        <svg {...common}>
          <path d="M4 19V5" />
          <path d="M4 19h16" />
          <path d="M8 15v-4" />
          <path d="M12 15V8" />
          <path d="M16 15v-7" />
        </svg>
      );
    case "grid":
      return (
        <svg {...common}>
          <rect x="4" y="4" width="7" height="7" rx="1.5" />
          <rect x="13" y="4" width="7" height="7" rx="1.5" />
          <rect x="4" y="13" width="7" height="7" rx="1.5" />
          <rect x="13" y="13" width="7" height="7" rx="1.5" />
        </svg>
      );
    case "card":
      return (
        <svg {...common}>
          <rect x="3" y="6" width="18" height="13" rx="2" />
          <path d="M3 10h18" />
        </svg>
      );
    case "flow":
      return (
        <svg {...common}>
          <circle cx="6" cy="12" r="2.5" />
          <circle cx="18" cy="6" r="2.5" />
          <circle cx="18" cy="18" r="2.5" />
          <path d="M8.2 11.2 15.5 7.2" />
          <path d="M8.2 12.8 15.5 16.8" />
        </svg>
      );
    case "present":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="12" rx="2" />
          <path d="M8 21h8" />
          <path d="M12 17v4" />
        </svg>
      );
    case "pulse":
      return (
        <svg {...common}>
          <path d="M3 12h4l2-6 4 12 2-6h6" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z" />
        </svg>
      );
  }
}
