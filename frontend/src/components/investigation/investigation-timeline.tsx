import type { InvestigationEvent } from "@/hooks/use-investigation-sse";
import { eventLabel, experimentLabel } from "@/lib/investigation-copy";

function formatWhen(value: string | undefined) {
  if (!value) return "";
  const numeric = Number(value);
  const date = Number.isFinite(numeric)
    ? new Date(numeric > 1e12 ? numeric : numeric * 1000)
    : new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function planNote(event: InvestigationEvent) {
  const source = event.payload?.source;
  const experimentType = experimentLabel(event.payload?.experimentType ?? event.payload?.nextSuggestedExperimentType);
  const headline =
    event.payload?.headline ??
    event.payload?.summary ??
    event.payload?.refinedObjective ??
    event.payload?.message;
  const parts = [
    source === "LIVE" ? "Meta answered live" : source === "STUB" ? "Meta stub" : source === "FALLBACK" ? "safe fallback" : null,
    experimentType,
    typeof headline === "string" ? headline : null,
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return <div className={event.eventType === "model.plan.failed" || event.eventType === "investigation.failed" ? "error" : "muted"}>{parts.join(" · ")}</div>;
}

export function InvestigationTimeline({
  events,
  connected,
}: {
  events: InvestigationEvent[];
  connected: boolean;
}) {
  return (
    <section className="timeline">
      <h3>Live timeline {connected ? "(live)" : "(catching up from the log)"}</h3>
      {events.length === 0 ? (
        <p className="empty">Waiting for the first update…</p>
      ) : (
        <ol>
          {events.map((event) => (
            <li key={event.id}>
              <strong>{eventLabel(event.eventType)}</strong>
              <div className="muted">{formatWhen(event.timestamp)}</div>
              {planNote(event)}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
