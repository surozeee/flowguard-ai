import { useEffect, useState } from "react";
import { investigationService } from "@/shared/services/investigation.service";
import type { ExperimentSummary, InvestigationSummary } from "@/shared/types/investigation.types";

export function useInvestigationPoll(investigationId: string, intervalMs = 2000) {
  const [investigation, setInvestigation] = useState<InvestigationSummary | null>(null);
  const [experiments, setExperiments] = useState<ExperimentSummary[]>([]);

  useEffect(() => {
    if (!investigationId) return;

    const refresh = () => {
      investigationService.getById(investigationId).then(setInvestigation);
      investigationService.listExperiments(investigationId).then(setExperiments);
    };

    refresh();
    const handle = window.setInterval(refresh, intervalMs);
    return () => window.clearInterval(handle);
  }, [investigationId, intervalMs]);

  return { investigation, experiments };
}
