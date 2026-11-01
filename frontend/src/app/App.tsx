import { BrowserRouter } from "react-router-dom";
import { AppShell } from "@/components/common/app-shell";
import { AppRoutes } from "@/app/routes";

export default function App() {
  return (
    <BrowserRouter>
      <AppShell>
        <AppRoutes />
      </AppShell>
    </BrowserRouter>
  );
}
