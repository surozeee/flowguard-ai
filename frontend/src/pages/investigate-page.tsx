import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PageCard } from "@/components/common/page-card";
import { investigationService } from "@/shared/services/investigation.service";

export function InvestigatePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const start = async () => {
    setLoading(true);
    try {
      const created = await investigationService.start({
        projectId: "sample-wallet",
        objective: "Investigate at-most-once processing and duplicate credit risk",
        maxProbes: 10,
      });
      navigate(`/investigations/${created.id}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageCard
      title="New investigation"
      description="Meta will rephrase the question, pick a safe probe, and explain each result. Independent ledger checks — not the model — decide whether something actually broke."
    >
      <p className="muted">Demo wallet · up to 10 probes · looking for duplicate credit or at-most-once slips.</p>
      <button disabled={loading} onClick={start}>
        {loading ? "Starting…" : "Start investigation"}
      </button>
    </PageCard>
  );
}
