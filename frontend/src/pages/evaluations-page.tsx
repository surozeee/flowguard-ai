import { FormEvent, useState } from "react";
import { PageCard } from "@/components/common/page-card";
import { evaluationService } from "@/shared/services/evaluation.service";

export function EvaluationsPage() {
  const [arm, setArm] = useState<"META" | "RANDOM">("META");
  const [evaluationId, setEvaluationId] = useState<string | null>(null);
  const [result, setResult] = useState<string>("");

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setResult("Starting…");
    const started = await evaluationService.start({
      arm,
      probeBudget: 3,
      objective: "Benchmark duplicate credit race",
    });
    setEvaluationId(started.evaluationId);
    const poll = window.setInterval(async () => {
      const summary = await evaluationService.getById(started.evaluationId);
      if (summary.status === "COMPLETED" || summary.status === "FAILED") {
        window.clearInterval(poll);
        const first = summary.firstViolationProbe;
        const status =
          summary.status === "COMPLETED"
            ? first
              ? `Finished. First break showed up on probe ${first}.`
              : "Finished. No break showed up in the budget."
            : "Stopped before it could finish.";
        setResult(`${status} Used ${summary.probesUsed} probe(s) on the ${summary.arm} arm.`);
      }
    }, 2000);
  }

  return (
    <PageCard
      title="Evaluations"
      description="Compare Meta-guided probes with a random baseline. Both arms still get scored by the same independent ledger checks."
    >
      <div className="toolbar">
        <form onSubmit={onSubmit}>
          <label>
            Arm
            <select value={arm} onChange={(e) => setArm(e.target.value as "META" | "RANDOM")}>
              <option value="META">Meta-guided</option>
              <option value="RANDOM">Random baseline</option>
            </select>
          </label>
          <button type="submit">Run evaluation</button>
        </form>
      </div>
      {evaluationId && <p className="muted">Run {evaluationId.slice(0, 8)}</p>}
      {result && <p>{result}</p>}
    </PageCard>
  );
}
