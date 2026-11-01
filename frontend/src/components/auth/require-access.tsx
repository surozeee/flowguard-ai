import { Navigate, useLocation } from "react-router-dom";
import { hasAuthenticatedSession } from "@/lib/api-credentials";

export function RequireAccess({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  if (hasAuthenticatedSession()) {
    return <>{children}</>;
  }
  const returnUrl = encodeURIComponent(location.pathname + location.search);
  return <Navigate to={`/login?returnUrl=${returnUrl}`} replace />;
}
