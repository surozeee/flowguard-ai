import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageCard } from "@/components/common/page-card";
import { EvidencePanel } from "@/components/investigation/evidence-panel";
import { StatusBadge } from "@/components/investigation/status-badge";
import { useInvestigationPoll } from "@/hooks/use-investigation-poll";
import { useInvestigationSse } from "@/hooks/use-investigation-sse";
import { InvestigationTimeline } from "@/components/investigation/investigation-timeline";
import { SagaTracePanel } from "@/components/investigation/saga-trace-panel";
import { investigationService } from "@/shared/services/investigation.service";
import { stateLabel } from "@/lib/investigation-copy";

export function InvestigationDetailPage() {
  const { id = "" } = useParams();
  const { investigation, experiments } = useInvestigationPoll(id);
  const { events, connected } = useInvestigationSse(id);
  const [patchNote, setPatchNote] = useState("");
  const summaryEvent = [...events].reverse().find((e) => e.eventType === "model.summary.received");
  const explainEvent = [...events].reverse().find((e) => e.eventType === "model.explain.received");
  const refineEvent = [...events].reverse().find((e) => e.eventType === "model.objective.refined");
  const latest = experiments[experiments.length - 1];
  const idempotencyKey = (() => {
    for (let i = events.length - 1; i >= 0; i--) {
      const e = events[i];
      if (e.eventType === "saga.trace.verified" || e.eventType === "saga.trace.failed") {
        const key = e.payload?.idempotencyKey ?? e.payload?.idempotency_key;
        if (typeof key === "string" && key.length > 0) return key;
      }
      if (e.eventType === "experiment.finished") {
        const measurement = e.payload?.measurement as Record<string, unknown> | undefined;
        const key = measurement?.idempotencyKey;
        if (typeof key === "string" && key.length > 0) return key;
      }
    }
    return undefined;
  })();

  return (
    <PageCard title={`Investigation ${id.slice(0, 8)}`} description="A short walk through what Meta asked, what the wallet did, and what the ledger checks decided.">
      {investigation && (
        <p className="muted">
          {stateLabel(investigation.state)}
          {investigation.errorCode ? ` · ${investigation.errorCode}` : ""} · Probes{" "}
          {investigation.probesUsed ?? 0}/{investigation.maxProbes ?? 10}
        </p>
      )}
      <StatusBadge verdict={latest?.oracleVerdict ?? ""} />
      {latest?.id && (
        <p>
          <Link to="#evidence">Evidence for experiment {latest.id.slice(0, 8)}</Link>
        </p>
      )}
      {(summaryEvent || explainEvent || refineEvent) && (
        <section className="timeline">
          <h3>What Meta said</h3>
          {typeof refineEvent?.payload?.refinedObjective === "string" && (
            <p className="muted">Sharpened question: {refineEvent.payload.refinedObjective}</p>
          )}
          {typeof explainEvent?.payload?.headline === "string" && <p>{explainEvent.payload.headline}</p>}
          {typeof explainEvent?.payload?.explanation === "string" && (
            <p className="muted">{explainEvent.payload.explanation}</p>
          )}
          {typeof summaryEvent?.payload?.summary === "string" && <p>{summaryEvent.payload.summary}</p>}
          {typeof summaryEvent?.payload?.recommendedFollowUp === "string" && (
            <p className="muted">{summaryEvent.payload.recommendedFollowUp}</p>
          )}
        </section>
      )}
      <p>
        <button
          type="button"
          onClick={async () => {
            setPatchNote("Asking Meta for a candidate fix…");
            try {
              const result = await investigationService.proposePatch(id);
              const from = result.source === "LIVE" ? "live Meta" : result.source === "STUB" ? "the Meta stub" : "a fallback draft";
              setPatchNote(`Draft patch ${result.patchId.slice(0, 8)} from ${from}.`);
            } catch (error) {
              setPatchNote(error instanceof Error ? error.message : "Could not ask Meta for a patch.");
            }
          }}
        >
          Ask Meta for a draft fix
        </button>
      </p>
      {patchNote && <p className="muted">{patchNote}</p>}
      <InvestigationTimeline events={events} connected={connected} />
      <SagaTracePanel idempotencyKey={idempotencyKey} />
      <EvidencePanel experimentId={latest?.id} />
    </PageCard>
  );
}
