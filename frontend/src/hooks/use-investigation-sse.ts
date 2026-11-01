import { useEffect, useState } from "react";
import { API_V1, apiFetch } from "@/lib/api-fetch";

export type InvestigationEvent = {
  id: string;
  investigationId: string;
  sequence: number;
  eventType: string;
  timestamp: string;
  payload: Record<string, unknown>;
};

export function useInvestigationSse(investigationId: string) {
  const [events, setEvents] = useState<InvestigationEvent[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!investigationId) return;

    let cancelled = false;
    apiFetch<Array<InvestigationEvent & { createdAt?: string }>>(`/investigations/${investigationId}/event-log`)
      .then((rows) => {
        if (cancelled || !rows) return;
        setEvents(
          rows
            .map((row) => ({
              ...row,
              investigationId,
              timestamp: row.createdAt ?? row.timestamp,
            }))
            .sort((a, b) => a.sequence - b.sequence),
        );
      })
      .catch(() => {
        /* live SSE still attaches */
      });

    const source = new EventSource(`${API_V1}/investigations/${investigationId}/events`);
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);

    const onEvent = (message: MessageEvent<string>) => {
      try {
        const parsed = JSON.parse(message.data) as InvestigationEvent;
        setEvents((prev) => {
          if (prev.some((e) => e.id === parsed.id)) return prev;
          return [...prev, parsed].sort((a, b) => a.sequence - b.sequence);
        });
      } catch {
        /* ignore malformed frames */
      }
    };

    source.addEventListener("investigation.created", onEvent);
    source.addEventListener("hypothesis.recorded", onEvent);
    source.addEventListener("experiment.started", onEvent);
    source.addEventListener("experiment.finished", onEvent);
    source.addEventListener("saga.trace.verified", onEvent);
    source.addEventListener("saga.trace.failed", onEvent);
    source.addEventListener("investigation.completed", onEvent);
    source.addEventListener("investigation.failed", onEvent);
    source.addEventListener("investigation.state", onEvent);
    source.addEventListener("model.plan.started", onEvent);
    source.addEventListener("model.plan.received", onEvent);
    source.addEventListener("model.plan.fallback", onEvent);
    source.addEventListener("model.plan.failed", onEvent);
    source.addEventListener("model.explain.received", onEvent);
    source.addEventListener("model.summary.received", onEvent);
    source.addEventListener("model.patch.received", onEvent);
    source.addEventListener("model.objective.refined", onEvent);

    const poll = window.setInterval(() => {
      if (cancelled) return;
      apiFetch<Array<InvestigationEvent & { createdAt?: string }>>(`/investigations/${investigationId}/event-log`)
        .then((rows) => {
          if (cancelled || !rows) return;
          setEvents((prev) => {
            const merged = [...prev];
            for (const row of rows) {
              if (merged.some((e) => e.id === row.id)) continue;
              merged.push({
                ...row,
                investigationId,
                timestamp: row.createdAt ?? row.timestamp,
              });
            }
            return merged.sort((a, b) => a.sequence - b.sequence);
          });
        })
        .catch(() => {
          /* SSE remains the primary path */
        });
    }, 3000);

    return () => {
      cancelled = true;
      window.clearInterval(poll);
      source.close();
      setConnected(false);
    };
  }, [investigationId]);

  return { events, connected };
}
