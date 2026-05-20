# Take-Home Exercise — Wave Pick & Reservation Service

**Timebox:** 2 hours. Please track your time honestly and stop at 2 hours, even if incomplete.
Send us whatever state you're in — we care about how you spent the time, not how much you crammed in.

**Stack:** Java. A starter scaffold is provided in `starter/` (Spring Boot 3, Java 21,
Maven, H2 in-memory, `JdbcTemplate`, JUnit 5). You may use it as-is, extend it, or
replace any piece you disagree with — but please stay in Java.

**Working in version control:** Please complete the exercise inside a Git repository
and push your commit history alongside the final code. Commit cadence is yours; we
look at the history to understand how you approached the work.

---

## 1. Context

You're joining the platform team at a third-party logistics (3PL) company. Our warehouses fulfill
e-commerce orders. When an order arrives, the system breaks it into **pick tasks** that a human
"picker" performs with a handheld barcode scanner: the scanner directs them to a bin, they
scan the item barcode, the scanner confirms the pick.

Inventory of a given SKU is spread across **multiple bins** (a SKU can exist in bin `A-12-3`
and bin `B-04-1` simultaneously, with different quantities in each). When we **allocate**
an order, we decide which bin(s) to pull from and reserve that stock so no other order can
take it.

You are building the backend service that manages **inventory, reservations, and pick events**
for a single warehouse. The scanners are HTTP clients that hit your API.

## 2. Your task

Implement a service that exposes the endpoints below, preserves the listed invariants under
concurrency, and ships with tests demonstrating those invariants.

You do **not** need a UI. You do **not** need authentication. You do **not** need deployment
artifacts. Focus on the domain.

## 3. Domain model (suggested — adapt as you see fit)

```
SKU         { sku_id, description }
Bin         { bin_id, location_code }
BinStock    { bin_id, sku_id, quantity_on_hand, quantity_reserved }
Order       { order_id, status: NEW | ALLOCATED | PICKING | PICKED | SHORT | CANCELLED }
OrderLine   { order_id, line_id, sku_id, quantity_required, quantity_picked }
Reservation { reservation_id, line_id, bin_id, quantity_reserved, quantity_picked, status }
PickEvent   { event_id, client_event_id, reservation_id, quantity, scanned_barcode, picker_id, at }
```

`client_event_id` is generated on the scanner and is **the dedup key for retries**.

## 4. Required endpoints

### 4.1 `POST /orders`
Create an order with one or more lines.
```jsonc
// Request
{
  "order_id": "ORD-1001",
  "lines": [
    { "line_id": "L1", "sku_id": "SKU-RED-MUG", "quantity": 3 },
    { "line_id": "L2", "sku_id": "SKU-BLUE-PEN", "quantity": 10 }
  ]
}
// 201 Response: created order in NEW status
```

### 4.2 `POST /orders/{order_id}/allocate`
Reserve inventory for the order across bins. **Idempotent**: calling twice must not double-reserve.
- If full allocation is possible: reserve, transition order to `ALLOCATED`, return per-line bin breakdown.
- If only partial allocation is possible: return `409` with what *could* be reserved, reserve nothing.
- Document your allocation strategy in the README (FIFO by `received_at`? Bin with most stock? Etc.).

```jsonc
// 200 Response
{
  "order_id": "ORD-1001",
  "status": "ALLOCATED",
  "reservations": [
    { "reservation_id": "R1", "line_id": "L1", "bin_id": "A-12-3", "quantity": 3 },
    { "reservation_id": "R2", "line_id": "L2", "bin_id": "B-04-1", "quantity": 7 },
    { "reservation_id": "R3", "line_id": "L2", "bin_id": "C-09-2", "quantity": 3 }
  ]
}
```

### 4.3 `POST /pick-events`
A scanner reports a successful pick. **Idempotent** via `client_event_id`.
```jsonc
// Request
{
  "client_event_id": "scn-9f3a-001",   // dedup key
  "reservation_id": "R2",
  "quantity": 7,
  "scanned_barcode": "SKU-BLUE-PEN",
  "picker_id": "USR-42",
  "at": "2026-05-13T09:14:22Z"
}
// 200 Response: updated reservation + order status
```
Decrement `quantity_on_hand` on the bin, increment `quantity_picked` on the reservation.
If `scanned_barcode` does not match the reservation's expected SKU, reject with `409`.
When all reservations on an order are fully picked, transition order to `PICKED`.

### 4.4 `POST /pick-events/short-pick`
The picker couldn't find the expected quantity (damaged, miscount, missing).
```jsonc
// Request
{
  "client_event_id": "scn-9f3a-002",
  "reservation_id": "R3",
  "quantity_short": 2,                  // could only find 1 of the 3
  "reason": "NOT_FOUND",                // NOT_FOUND | DAMAGED | WRONG_ITEM
  "picker_id": "USR-42"
}
```
Decide and document what this does to (a) the reservation, (b) the bin's `quantity_on_hand`,
(c) the order, and (d) any other reservations against that bin. There is no single right
answer — show your reasoning.

### 4.5 `GET /orders/{order_id}`
Return the full state of the order, including reservations and audit trail.

### 4.6 `GET /inventory/{sku_id}`
Return per-bin `on_hand`, `reserved`, and `available` for the SKU, plus totals.

## 5. Invariants you must preserve

These must hold at all times, including under concurrent requests:

1. **Conservation**: `bin.quantity_on_hand >= bin.quantity_reserved >= 0` for every bin, always.
2. **No double-reservation**: Two concurrent allocations of the last unit must not both succeed.
3. **No double-pick**: Two requests with the same `client_event_id` must produce exactly one
   inventory decrement.
4. **No overpick**: A reservation's `quantity_picked` must never exceed `quantity_reserved`.
5. **Auditability**: Every state change to a reservation or bin must be reconstructable from
   an append-only history. We should be able to answer "what happened to unit X?" after the fact.

How you enforce these is up to you. Make your choice explicit in the README.

## 6. Seed data

Start the service with this state (provide it as a fixture or migration):

```jsonc
{
  "skus": [
    { "sku_id": "SKU-RED-MUG",  "description": "Red ceramic mug 12oz" },
    { "sku_id": "SKU-BLUE-PEN", "description": "Blue ballpoint pen" }
  ],
  "bins": [
    { "bin_id": "A-12-3", "sku_id": "SKU-RED-MUG",  "quantity_on_hand": 5,  "received_at": "2026-05-01" },
    { "bin_id": "A-12-4", "sku_id": "SKU-RED-MUG",  "quantity_on_hand": 2,  "received_at": "2026-05-10" },
    { "bin_id": "B-04-1", "sku_id": "SKU-BLUE-PEN", "quantity_on_hand": 7,  "received_at": "2026-04-20" },
    { "bin_id": "C-09-2", "sku_id": "SKU-BLUE-PEN", "quantity_on_hand": 4,  "received_at": "2026-05-05" }
  ]
}
```

## 7. End-to-end worked example

```
POST /orders                 → ORD-1001 with 3× RED-MUG, 10× BLUE-PEN  → status NEW
POST /orders/ORD-1001/allocate
   → reserves 3 from A-12-3 (mug), 7 from B-04-1 + 3 from C-09-2 (pen) → status ALLOCATED
POST /pick-events            (client_event_id=e1, R1, 3 mugs)          → R1 PICKED
POST /pick-events            (client_event_id=e1, R1, 3 mugs)  ← retry → no change, same response
POST /pick-events            (client_event_id=e2, R2, 7 pens)          → R2 PICKED
POST /pick-events/short-pick (client_event_id=e3, R3, short 2, NOT_FOUND) → R3 partial
GET  /orders/ORD-1001        → status SHORT (only 8 of 10 pens picked), with audit trail
```

## 8. One way to slice the work

The scaffold's stubs are tagged with these task numbers, and the disabled
reference tests in `starter/src/test/...` map to the same numbers. You don't have
to follow this order — it's one workable path through the brief.

| #   | Task                                                                                                                                   | Rough time |
| --- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| T1  | **Allocate — happy path.** Reserve from a single bin per line. Bin's `quantity_reserved` updates; order moves to `ALLOCATED`.          | ~15 min    |
| T2  | **Allocate — multi-bin.** Split a line across bins when no single bin has enough. Document the bin-selection strategy in your README.  | ~10 min    |
| T3  | **Allocate — all-or-nothing.** When full allocation is impossible, return 409 with detail; persist nothing.                            | ~10 min    |
| T4  | **Allocate — concurrency-safe.** Two callers racing for the last unit must not both succeed.                                           | ~15 min    |
| T5  | **Pick event — happy path.** Decrement bin on-hand and reserved; advance reservation; reject barcode mismatch; reject overpick.        | ~15 min    |
| T6  | **Pick event — idempotent.** Dedup by `client_event_id`, including under concurrent retries.                                           | ~15 min    |
| T7  | **Short-pick.** Implement it. Decide and document semantics for the reservation, bin, order, and other reservations on that bin.      | ~10 min    |
| T8  | **Audit trail.** Make state changes reconstructable — pick the shape (table, event log, etc.) that fits your model.                    | ~15 min    |

Reserve the final ~15 minutes for your `README.md` "Tradeoffs & Decisions"
section — what you cut, what you'd add with more time, and where your solution
is weakest. That writeup matters as much as the code.

## 9. Hidden complexity (you don't have to handle everything — show judgment)

A strong submission acknowledges these even if it doesn't solve them all:

- **Allocation strategy.** FIFO by `received_at`? Single-bin-preferred to minimize picker walking?
  Document your choice and why.
- **Partial allocation.** Some warehouses allow it, some don't. What does *this* business need?
- **Scanner retry storms.** The scanner has flaky WiFi. It may send the same event 5 times in 30
  seconds. Your dedup must survive concurrent retries of the same `client_event_id`.
- **Allocation expiry / release.** What if an order is cancelled while picking? What if a
  reservation sits idle for hours?
- **Short-pick cascade.** When a short-pick reveals a bin actually has fewer units than recorded,
  what happens to *other* reservations against that bin? (You don't need to implement this —
  but call out that you see it.)
- **Audit semantics.** Is it a separate `events` table? Event-sourced? Triggers? CDC?

## 10. Explicit non-goals (do NOT spend time on)

- Authentication, authorization, user management
- A frontend or admin UI
- Containerization / deployment / CI
- Real-time websockets or pub/sub infrastructure
- Multi-warehouse routing
- Wave planning, picker assignment, path optimization
- Production-grade observability (logs/metrics are fine but don't wire Prometheus)
- Migrations framework if using SQLite — a `schema.sql` is fine

## 11. Deliverables

Zip or push to a private repo, and include:

1. **Source code** that runs with a single command (`mvn spring-boot:run` is fine).
   Include a `README.md` with:
   - How to run the service and the tests
   - Your stack and why
   - **A "Tradeoffs & Decisions" section** — this is the most important thing you ship.
     Tell us what you cut, what you'd add with another 2 hours, and where you think your
     solution is weakest.
   - **A "How I built this" note (3–5 sentences).** What did you tackle first? What slowed
     you down? What would you refactor with another hour? We read this — it helps us have
     a better pairing conversation with you.
   - **AI assistance disclosure.** If you used AI tools (Copilot, Cursor, Claude, ChatGPT,
     etc.) for any part of this exercise, briefly note which parts at the bottom of your
     README. There's no penalty for disclosure — we just want to know what's yours so we
     can evaluate accordingly and have the right conversation in pairing.
2. **Tests** — at minimum, one test per required invariant in section 5. We will read these.
3. **A `NOTES.md`** (optional but encouraged) — anything you want us to know before the pairing
   session: questions, assumptions, things you considered and rejected.

## 12. What we evaluate

Roughly, in descending weight:
1. Correctness of the consistency invariants under concurrency
2. API design and data model clarity
3. Test quality (do the tests actually catch the bugs they appear to?)
4. Code readability
5. Quality of written reasoning (README, NOTES)
6. Operational/domain awareness (do you see what could go wrong in a real warehouse?)

We do **not** evaluate:
- Whether you finished every endpoint
- Lines of code, framework choice, or test count

## 13. What comes next

In the pairing session we will **extend** what you built with a realistic operational issue.
Bring your code; we will work in it together. You will not be asked to whiteboard from scratch.
