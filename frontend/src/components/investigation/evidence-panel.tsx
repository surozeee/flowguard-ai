import { useEffect, useState } from "react";
import { experimentService } from "@/shared/services/experiment.service";

export function EvidencePanel({ experimentId }: { experimentId?: string }) {
  const [evidence, setEvidence] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (!experimentId) return;
    experimentService.getEvidence(experimentId).then(setEvidence);
  }, [experimentId]);

  if (!evidence) return null;

  return (
    <>
      <h3>Evidence</h3>
      <pre id="evidence">{JSON.stringify(evidence, null, 2)}</pre>
    </>
  );
}
