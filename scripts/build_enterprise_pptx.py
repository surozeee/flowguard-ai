"""Build a professional widescreen briefing deck for FlowGuard AI — LedgerGuard."""

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN
from pptx.util import Inches, Pt
from pathlib import Path

NAVY = RGBColor(0x07, 0x0D, 0x18)
NAVY_2 = RGBColor(0x10, 0x1B, 0x2E)
NAVY_3 = RGBColor(0x15, 0x22, 0x38)
ACCENT = RGBColor(0x38, 0xBD, 0xF8)
ACCENT_2 = RGBColor(0x3B, 0x82, 0xF6)
WHITE = RGBColor(0xEE, 0xF3, 0xFB)
MUTED = RGBColor(0x8E, 0xA0, 0xB8)
OK = RGBColor(0x34, 0xD3, 0x99)
LINE = RGBColor(0x24, 0x36, 0x56)

W = Inches(13.333)
H = Inches(7.5)


def set_run(run, size, bold=False, color=WHITE, font="Calibri"):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font


def add_rect(slide, l, t, w, h, fill):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, l, t, w, h)
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.fill.background()
    return shape


def add_round(slide, l, t, w, h, fill):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, l, t, w, h)
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.fill.background()
    return shape


def textbox(slide, l, t, w, h, text, size, bold=False, color=WHITE, align=PP_ALIGN.LEFT):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    set_run(run, size, bold, color)
    return box


def bullets(slide, l, t, w, h, items, size=18):
    box = slide.shapes.add_textbox(l, t, w, h)
    tf = box.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.level = 0
        p.space_after = Pt(10)
        run = p.add_run()
        run.text = "•  " + item
        set_run(run, size, False, WHITE)
    return box


def footer(slide, page, total):
    add_rect(slide, Inches(0), Inches(7.28), W, Inches(0.22), ACCENT_2)
    textbox(slide, Inches(0.5), Inches(7.02), Inches(8), Inches(0.28),
            "FlowGuard AI — LedgerGuard  ·  Confidential briefing  ·  Synthetic demo", 11, False, MUTED)
    textbox(slide, Inches(11.4), Inches(7.02), Inches(1.4), Inches(0.28),
            f"{page}  /  {total}", 11, False, MUTED, PP_ALIGN.RIGHT)


def chrome(slide, kicker):
    add_rect(slide, Inches(0), Inches(0), W, H, NAVY)
    add_rect(slide, Inches(0), Inches(0), Inches(0.12), H, ACCENT)
    textbox(slide, Inches(0.5), Inches(0.22), Inches(10), Inches(0.32),
            kicker.upper(), 12, True, ACCENT)


def card(slide, l, t, w, h, title, body):
    add_round(slide, l, t, w, h, NAVY_3)
    textbox(slide, l + Inches(0.22), t + Inches(0.16), w - Inches(0.4), Inches(0.4), title, 16, True, ACCENT)
    textbox(slide, l + Inches(0.22), t + Inches(0.55), w - Inches(0.4), h - Inches(0.7), body, 14, False, WHITE)


def build():
    prs = Presentation()
    prs.slide_width = W
    prs.slide_height = H
    blank = prs.slide_layouts[6]
    total = 8

    # 1 Title
    s = prs.slides.add_slide(blank)
    add_rect(s, 0, 0, W, H, NAVY)
    add_rect(s, 0, 0, Inches(0.18), H, ACCENT)
    add_rect(s, 0, Inches(6.95), W, Inches(0.55), ACCENT_2)
    textbox(s, Inches(0.7), Inches(1.35), Inches(12), Inches(0.35),
            "ENTERPRISE BRIEFING", 14, True, ACCENT)
    textbox(s, Inches(0.7), Inches(1.85), Inches(12), Inches(1.4),
            "FlowGuard AI — LedgerGuard", 40, True, WHITE)
    textbox(s, Inches(0.7), Inches(3.35), Inches(11.2), Inches(1.4),
            "Addressing real payment-ledger failures — duplicate credits, debit races, "
            "and broken sagas — without letting a model grade its own work.", 22, False, MUTED)
    textbox(s, Inches(0.7), Inches(5.15), Inches(11), Inches(0.8),
            "Independent Java and SQL checks decide. Models only hypothesize.\n"
            "Synthetic demo  ·  No real funds  ·  Docker Compose lab", 16, False, WHITE)
    textbox(s, Inches(0.7), Inches(7.05), Inches(11), Inches(0.3),
            "Stakeholder presentation", 12, False, WHITE)

    # 2 Real life
    s = prs.slides.add_slide(blank)
    chrome(s, "01  /  The real-world problem")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.7),
            "Payment systems fail in the seams, not in the happy path", 28, True, WHITE)
    textbox(s, Inches(0.5), Inches(1.35), Inches(12.2), Inches(0.7),
            "When a customer double-taps Pay, a partner retries a timeout, or two services "
            "read the same balance, the ledger can credit twice or drift — even if each service looks healthy.",
            16, False, MUTED)
    issues = [
        ("Duplicate credit", "Two overlapping posts with the same idempotency key both pass the “not seen yet” check before either insert commits."),
        ("Concurrent debit", "Two transfers read the same available balance, then both write. The account goes negative or the books no longer reconcile."),
        ("Saga gap", "The orchestrator marks a transfer complete while choreography never emits a terminal event — money moved, the trail did not."),
        ("AI as judge", "If a model both picks the test and scores the result, a fluent explanation can hide a broken invariant."),
    ]
    positions = [(0.5, 2.2), (6.85, 2.2), (0.5, 4.55), (6.85, 4.55)]
    for (x, y), (title, body) in zip(positions, issues):
        card(s, Inches(x), Inches(y), Inches(5.95), Inches(2.15), title, body)
    footer(s, 2, total)

    # 3 Why it matters
    s = prs.slides.add_slide(blank)
    chrome(s, "02  /  Why this matters in production")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.6),
            "These are not lab curiosities", 28, True, WHITE)
    rows = [
        ("Retail and wallets", "Retries after a gateway timeout are normal. Without at-most-once processing, a single payment becomes two credits."),
        ("Core banking / ledgers", "Balance checks that are not serialized become overdrafts and reconciliation breaks that finance teams see days later."),
        ("Partner rails", "Sagas across wallet, ledger, and notification services leave money in flight with no terminal event to audit."),
        ("Risk and audit", "Regulators and internal audit need evidence that is hashed, ordered, and independent of whoever suggested the test."),
    ]
    y = 1.4
    for title, body in rows:
        add_round(s, Inches(0.5), Inches(y), Inches(12.3), Inches(1.2), NAVY_3)
        add_rect(s, Inches(0.5), Inches(y), Inches(0.12), Inches(1.2), ACCENT)
        textbox(s, Inches(0.9), Inches(y + 0.15), Inches(11.6), Inches(0.35), title, 18, True, ACCENT)
        textbox(s, Inches(0.9), Inches(y + 0.5), Inches(11.6), Inches(0.55), body, 15, False, WHITE)
        y += 1.35
    footer(s, 3, total)

    # 4 How we address
    s = prs.slides.add_slide(blank)
    chrome(s, "03  /  How FlowGuard addresses each issue")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.55),
            "Reproduce the failure. Score it independently. Keep a chain of evidence.", 24, True, WHITE)
    pairs = [
        ("Real-life failure", "What FlowGuard does"),
        ("Duplicate payment / double credit", "Concurrent duplicate probe against a seeded race in the demo wallet. Oracle: payment takes effect at most once."),
        ("Overlapping debits / ledger drift", "Concurrent debit probe. Oracle: accounts still reconcile after the race."),
        ("Incomplete saga / missing terminal event", "Transfer starts an orchestrator saga; backend consumers verify choreography independently."),
        ("Model-written “all clear”", "Meta may plan and explain. Java/SQL oracles own the verdict. The model cannot override them."),
    ]
    y = 1.3
    for i, (left, right) in enumerate(pairs):
        fill = ACCENT_2 if i == 0 else NAVY_3
        lc = WHITE if i == 0 else ACCENT
        add_round(s, Inches(0.5), Inches(y), Inches(5.9), Inches(0.95), fill)
        add_round(s, Inches(6.7), Inches(y), Inches(6.1), Inches(0.95), fill)
        textbox(s, Inches(0.7), Inches(y + 0.22), Inches(5.5), Inches(0.55), left, 15, True, lc)
        textbox(s, Inches(6.9), Inches(y + 0.18), Inches(5.7), Inches(0.65), right, 14, i == 0, WHITE)
        y += 1.05
    footer(s, 4, total)

    # 5 Loop
    s = prs.slides.add_slide(blank)
    chrome(s, "04  /  Investigation loop")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.55),
            "A closed loop that still keeps the model off the scoreboard", 26, True, WHITE)
    steps = [
        ("1", "Refine", "Meta rewrites the objective in plain English and suggests a first probe type."),
        ("2", "Plan", "Only allowed experiments: duplicate credit, concurrent debit, saga verify."),
        ("3", "Execute", "The fixture wallet runs the race. Status commits as the run proceeds."),
        ("4", "Judge", "Independent oracles return clean, break, or inconclusive. Meta explains; it does not score."),
        ("5", "Steer", "The last explain chooses the next probe. Evaluations compare Meta vs random."),
        ("6", "Evidence", "Hashed chain and timeline a reviewer can read without event codes."),
    ]
    coords = [(0.5, 1.35), (4.7, 1.35), (8.9, 1.35), (0.5, 4.05), (4.7, 4.05), (8.9, 4.05)]
    for (x, y), (n, title, body) in zip(coords, steps):
        add_round(s, Inches(x), Inches(y), Inches(3.9), Inches(2.4), NAVY_3)
        textbox(s, Inches(x + 0.25), Inches(y + 0.2), Inches(1), Inches(0.4), n, 22, True, ACCENT)
        textbox(s, Inches(x + 0.25), Inches(y + 0.65), Inches(3.4), Inches(0.4), title, 20, True, WHITE)
        textbox(s, Inches(x + 0.25), Inches(y + 1.1), Inches(3.4), Inches(1.05), body, 14, False, MUTED)
    footer(s, 5, total)

    # 6 Product / UX
    s = prs.slides.add_slide(blank)
    chrome(s, "05  /  Product, UI, and operating model")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.5),
            "A control plane, not a chatbot", 28, True, WHITE)
    bullets(s, Inches(0.5), Inches(1.3), Inches(6.2), Inches(5), [
        "Title: FlowGuard AI — LedgerGuard",
        "Console for investigators and ops, not a free-form AI chat",
        "Human timeline: asking Meta, probe finished, looked clean",
        "Create credit, replay the same key, watch idempotency hold",
        "Create transfer to compare saga vs Kafka choreography",
        "Evaluations: Meta-guided arm vs random baseline",
        "Sign-in at the gateway (investigator or ops)",
    ], 16)
    add_round(s, Inches(7.0), Inches(1.3), Inches(5.7), Inches(5.2), NAVY_3)
    textbox(s, Inches(7.25), Inches(1.5), Inches(5.2), Inches(0.4), "Stack (lab)", 18, True, ACCENT)
    bullets(s, Inches(7.25), Inches(2.05), Inches(5.2), Inches(4.1), [
        "Browser on Vite :5173",
        "API gateway :8088 — auth, rate limits, partner inbox",
        "Spring orchestrator — plans, oracles, evidence",
        "Fixture wallet — synthetic payments and races",
        "Postgres, Redis, Kafka",
        "Python worker hashes the evidence chain",
        "Not a production bank. No real money.",
    ], 15)
    footer(s, 6, total)

    # 7 Guardrails
    s = prs.slides.add_slide(blank)
    chrome(s, "06  /  What this is — and is not")
    textbox(s, Inches(0.5), Inches(0.6), Inches(12), Inches(0.5),
            "Honest scope keeps the briefing credible", 26, True, WHITE)
    add_round(s, Inches(0.5), Inches(1.35), Inches(6.0), Inches(5.3), NAVY_3)
    textbox(s, Inches(0.75), Inches(1.55), Inches(5.5), Inches(0.4), "This project is", 18, True, OK)
    bullets(s, Inches(0.75), Inches(2.15), Inches(5.5), Inches(4.2), [
        "A reproducible lab for ledger races",
        "A way to prove at-most-once and reconciliation",
        "A pattern: AI proposes, oracles dispose",
        "Evidence you can replay and hash",
        "A stakeholder deck plus a live console",
    ], 16)
    add_round(s, Inches(6.85), Inches(1.35), Inches(5.95), Inches(5.3), NAVY_3)
    textbox(s, Inches(7.1), Inches(1.55), Inches(5.5), Inches(0.4), "This project is not", 18, True, RGBColor(0xF8, 0x71, 0x71))
    bullets(s, Inches(7.1), Inches(2.15), Inches(5.5), Inches(4.2), [
        "A licensed core-banking system",
        "A replacement for production fraud engines",
        "A model that is allowed to certify itself",
        "A claim of quantified industry loss figures",
        "Live money or customer PII",
    ], 16)
    footer(s, 7, total)

    # 8 Close
    s = prs.slides.add_slide(blank)
    add_rect(s, 0, 0, W, H, NAVY)
    add_rect(s, 0, 0, Inches(0.18), H, ACCENT)
    add_rect(s, 0, Inches(6.95), W, Inches(0.55), ACCENT_2)
    textbox(s, Inches(0.7), Inches(1.7), Inches(12), Inches(0.4), "CLOSING", 14, True, ACCENT)
    textbox(s, Inches(0.7), Inches(2.2), Inches(12), Inches(1.2),
            "Models hypothesize. Ledgers decide.", 36, True, WHITE)
    textbox(s, Inches(0.7), Inches(3.6), Inches(11.5), Inches(1.4),
            "Use the lab to reproduce the races that actually lose money in production: "
            "duplicate credits, overlapping debits, and unfinished sagas — then keep the verdict "
            "in Java and SQL, not in the model’s last paragraph.", 18, False, MUTED)
    textbox(s, Inches(0.7), Inches(5.3), Inches(11.5), Inches(1.1),
            "Live console  ·  http://localhost:5173/present\n"
            "Investigate  ·  Create credit  ·  Create transfer  ·  Evaluations", 16, False, WHITE)
    textbox(s, Inches(0.7), Inches(7.05), Inches(12), Inches(0.3),
            "FlowGuard AI — LedgerGuard", 12, False, WHITE)

    out = Path(__file__).resolve().parents[1] / "docs" / "FlowGuard-AI-LedgerGuard-briefing.pptx"
    out.parent.mkdir(parents=True, exist_ok=True)
    prs.save(out)
    print(out)


if __name__ == "__main__":
    build()
