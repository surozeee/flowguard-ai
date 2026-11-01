import { Route, Routes } from "react-router-dom";
import { HomePage } from "@/pages/home-page";
import { InvestigatePage } from "@/pages/investigate-page";
import { InvestigationDetailPage } from "@/pages/investigation-detail-page";
import { EvaluationsPage } from "@/pages/evaluations-page";
import { OpsPage } from "@/pages/ops-page";
import { LoginPage } from "@/pages/login-page";
import { EnterpriseDashboardPage } from "@/pages/enterprise-dashboard-page";
import { TransactionCreatePage } from "@/pages/transaction-create-page";
import { TransactionSagaPage } from "@/pages/transaction-saga-page";
import { RequireAccess } from "@/components/auth/require-access";
import { PresentationPage } from "@/pages/presentation-page";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<HomePage />} />
      <Route path="/present" element={<PresentationPage />} />
      <Route path="/investigate" element={<InvestigatePage />} />
      <Route path="/investigations/:id" element={<InvestigationDetailPage />} />
      <Route path="/evaluations" element={<EvaluationsPage />} />
      <Route
        path="/transactions/new"
        element={
          <RequireAccess>
            <TransactionCreatePage />
          </RequireAccess>
        }
      />
      <Route
        path="/transactions/saga"
        element={
          <RequireAccess>
            <TransactionSagaPage />
          </RequireAccess>
        }
      />
      <Route
        path="/ops"
        element={
          <RequireAccess>
            <OpsPage />
          </RequireAccess>
        }
      />
      <Route
        path="/dashboard"
        element={
          <RequireAccess>
            <EnterpriseDashboardPage />
          </RequireAccess>
        }
      />
    </Routes>
  );
}
