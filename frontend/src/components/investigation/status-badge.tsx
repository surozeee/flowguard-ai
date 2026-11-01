import { verdictLabel } from "@/lib/investigation-copy";

export function StatusBadge({ verdict }: { verdict: string }) {
  if (!verdict) return null;
  const kind = verdict === "VIOLATION_REPRODUCED" ? "violation" : "ok";
  return (
    <p>
      <span className={`badge ${kind}`}>{verdictLabel(verdict)}</span>
    </p>
  );
}
