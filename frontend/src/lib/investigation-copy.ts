const EVENT_LABELS: Record<string, string> = {
  "investigation.created": "Investigation started",
  "investigation.state": "Status updated",
  "investigation.completed": "Investigation finished",
  "investigation.failed": "Investigation stopped",
  "hypothesis.recorded": "Hypothesis recorded",
  "experiment.started": "Probe started",
  "experiment.finished": "Probe finished",
  "saga.trace.verified": "Saga trace checked",
  "saga.trace.failed": "Saga trace found a gap",
  "model.objective.refined": "Meta sharpened the question",
  "model.plan.started": "Asking Meta for the next probe",
  "model.plan.received": "Meta proposed a probe",
  "model.plan.fallback": "Using a safe fallback probe",
  "model.plan.failed": "Meta could not propose a probe",
  "model.explain.received": "Meta explained the last probe",
  "model.summary.received": "Meta wrote a wrap-up",
  "model.patch.received": "Meta suggested a patch",
};

const STATE_LABELS: Record<string, string> = {
  CREATED: "Queued",
  RUNNING: "Running",
  MODEL_PLANNING: "Asking Meta",
  COMPLETED: "Finished — no break found",
  VIOLATION_FOUND: "Finished — break reproduced",
  FAILED: "Stopped",
};

const VERDICT_LABELS: Record<string, string> = {
  VIOLATION_REPRODUCED: "Ledger checks found a break",
  NO_VIOLATION_OBSERVED: "Ledger checks looked clean",
  INCONCLUSIVE: "Ledger checks were inconclusive",
};

const EXPERIMENT_LABELS: Record<string, string> = {
  CONCURRENT_DUPLICATE: "overlapping duplicate payments",
  CONCURRENT_DEBIT: "overlapping debit transfers",
  SAGA_CHOREOGRAPHY_VERIFY: "saga choreography",
};

export function eventLabel(eventType: string): string {
  return EVENT_LABELS[eventType] ?? eventType.replaceAll(".", " ");
}

export function stateLabel(state: string | undefined): string {
  if (!state) return "Unknown";
  return STATE_LABELS[state] ?? state;
}

export function verdictLabel(verdict: string): string {
  return VERDICT_LABELS[verdict] ?? verdict.replaceAll("_", " ").toLowerCase();
}

export function experimentLabel(type: unknown): string | null {
  if (typeof type !== "string" || type.length === 0) return null;
  return EXPERIMENT_LABELS[type] ?? type.replaceAll("_", " ").toLowerCase();
}
