import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

type Issue = {
  problem: string;
  inLife: string;
  inLab: string;
};

type Slide = {
  kicker: string;
  title: string;
  body: string;
  points?: string[];
  issues?: Issue[];
  foot?: string;
};

const SLIDES: Slide[] = [
  {
    kicker: "Enterprise briefing",
    title: "FlowGuard AI — LedgerGuard",
    body: "An investigation console for payment systems. Meta may suggest the next probe. Independent Java and SQL checks decide whether the books still balance. No real money moves here.",
    points: [
      "Synthetic wallet with a seeded duplicate-credit race",
      "Ops and investigator sessions through the API gateway",
      "Compose stack: Postgres, Redis, Kafka, fixture wallet, orchestrator",
    ],
    foot: "Synthetic demo · Docker Compose · not a production bank",
  },
  {
    kicker: "Project description",
    title: "What this system is for",
    body: "You can break the demo wallet on purpose, then watch whether at-most-once processing, saga choreography, and ledger invariants still hold.",
    points: [
      "Create a credit, then replay the same idempotency key",
      "Start a transfer and compare orchestrator saga with Kafka choreography",
      "Run an investigation: Meta plans, the wallet executes, oracles score",
      "Compare Meta-guided probes against a random baseline",
    ],
  },
  {
    kicker: "Product loop",
    title: "How an investigation runs",
    body: "The model is not the judge. It only narrows the question and picks a safe experiment type.",
    points: [
      "Meta rewrites the objective in plain English",
      "A constrained probe hits the fixture wallet",
      "Ledger checks return a verdict the model cannot override",
      "The last explain steers the next probe type",
    ],
  },
  {
    kicker: "UI & UX",
    title: "How the console is meant to feel",
    body: "A control-plane, not a chatbot. Status, probes, and evidence should read like an ops brief.",
    points: [
      "Dark workspace, IBM Plex, short sentences over event codes",
      "Timeline in human steps: started, asking Meta, probe finished",
      "Verdicts in English: looked clean, found a break, stopped",
      "Create credit, transfer, and operations stay behind sign-in",
      "Public overview shows health; presentation mode hides chrome",
    ],
  },
  {
    kicker: "Console map",
    title: "Where people actually work",
    body: "Each surface has one job. The presentation deck sits beside the live product so a walkthrough can jump into the UI.",
    points: [
      "Overview — is Postgres, Redis, and Kafka up?",
      "Investigate — start a run and read Meta’s briefing",
      "Create credit / transfer — prove idempotency and saga",
      "Evaluations — Meta arm versus random arm",
      "Dashboard & operations — inbox, outbox, DLQ (ops)",
    ],
  },
  {
    kicker: "Close",
    title: "Models hypothesize. Ledgers decide.",
    body: "Use this deck for a stakeholder walkthrough, then open the console and run the same path on the live stack.",
    points: [
      "Sign in as investigator or ops",
      "Post a credit, replay the key, start an investigation",
      "Read the wrap-up — the oracle verdict is the source of truth",
    ],
    foot: "investigator / investigator123 · ops / ops123",
  },
];

export function PresentationPage() {
  const [index, setIndex] = useState(0);
  const slide = SLIDES[index];
  const last = index === SLIDES.length - 1;

  const go = useCallback((next: number) => {
    setIndex(Math.min(SLIDES.length - 1, Math.max(0, next)));
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowRight" || event.key === " " || event.key === "PageDown") {
        event.preventDefault();
        go(index + 1);
      }
      if (event.key === "ArrowLeft" || event.key === "PageUp") {
        event.preventDefault();
        go(index - 1);
      }
      if (event.key === "Home") go(0);
      if (event.key === "End") go(SLIDES.length - 1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [go, index]);

  return (
    <article className="deck" aria-label="FlowGuard enterprise presentation">
      <header className="deck-top">
        <p className="deck-brand">FlowGuard AI</p>
        <p className="deck-progress">
          Slide {index + 1} of {SLIDES.length}
        </p>
      </header>

      <div className="deck-stage">
        <p className="eyebrow">{slide.kicker}</p>
        <h2>{slide.title}</h2>
        <p className="deck-body">{slide.body}</p>
        {slide.points && (
          <ul className="deck-points">
            {slide.points.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        )}
        {slide.foot && <p className="deck-foot">{slide.foot}</p>}
      </div>

      <nav className="deck-nav" aria-label="Slide controls">
        <button type="button" onClick={() => go(index - 1)} disabled={index === 0}>
          Previous
        </button>
        <ol className="deck-dots">
          {SLIDES.map((item, i) => (
            <li key={item.title}>
              <button
                type="button"
                className={i === index ? "on" : undefined}
                aria-label={`Go to ${item.title}`}
                aria-current={i === index ? "true" : undefined}
                onClick={() => go(i)}
              />
            </li>
          ))}
        </ol>
        {last ? (
          <Link className="deck-cta" to="/investigate">
            Open the console
          </Link>
        ) : (
          <button type="button" onClick={() => go(index + 1)}>
            Next
          </button>
        )}
      </nav>
    </article>
  );
}
