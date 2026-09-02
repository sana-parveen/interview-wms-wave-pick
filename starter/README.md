# WMS Wave Pick & Reservation Service

My submission for the Helix take-home. The service runs from this `starter/` directory.

## Stack

Java 21, Spring Boot 3.3, Maven, H2 (`MODE=PostgreSQL`), and `JdbcTemplate`.

I kept the starter's choice of raw SQL over JPA on purpose — when you're doing `SELECT ... FOR UPDATE` and conditional updates for concurrency, I wanted that logic visible rather than buried in an ORM. H2 in Postgres mode was enough for the exercise; the SQL should port to a real database without much change.

I added a thin service layer (`AllocationService`, `PickEventService`) on top of the starter's controller → repository pattern. Mostly to keep transactions and business rules in one place once allocation and picking got more involved.

## Run

```sh
mvn spring-boot:run    # http://localhost:8000
mvn test
```

H2 console: `http://localhost:8000/h2-console` — JDBC URL `jdbc:h2:mem:wms`, user `sa`, no password.

## Allocation strategy

FIFO by `received_at` — oldest stock gets picked first. If one bin can't cover a full line, the remainder spills into the next oldest bin. Allocation is all-or-nothing: if the order can't be fully satisfied, nothing gets reserved and you get a `409` with a breakdown of what *could* have been allocated.

Re-allocating an already-allocated order is idempotent — second call returns the same reservations, no double-booking.

## Short-pick semantics

When a picker reports `quantity_short`, that's how many units they *couldn't* find. Found quantity = what's still unpicked on the reservation minus `quantity_short`.

- **Reservation:** `quantity_picked` goes up by the found amount; status becomes `SHORT` if the line isn't fully satisfied.
- **Bin:** `on_hand` drops by what was physically taken; `reserved` drops by all remaining unpicked quantity (releases the commitment).
- **Order:** moves to `PICKING` during partial work, `SHORT` if a line can't be fully met, `PICKED` when everything lines up.

I didn't try to cascade short-picks to other reservations on the same bin — that's a real warehouse problem, but out of scope for a 2-hour window.

## Concurrency

For allocation: lock the order row and bin rows with `FOR UPDATE`, plan the full allocation before writing anything, and use conditional `UPDATE`s so two callers can't grab the last unit.

For picks: unique constraint on `client_event_id`, insert the event first, then apply inventory changes. Retries re-check after locking the reservation so they don't hit overpick validation by mistake.

## Audit trail

Append-only `audit_log` table. Events get written on order create, allocate, pick, short-pick, and status changes. `GET /orders/{orderId}` returns them as `auditTrail` in chronological order.

---

## How I built this

I read through the brief and starter first, then worked task-by-task (T1 → T8) instead of trying to wire everything at once. Allocation came first since everything downstream depends on reservations existing. T3 (all-or-nothing) and T4 (concurrency) took longer than the happy path — especially getting the plan-then-commit flow right so a failed allocation doesn't leave half-written reservations.

Pick idempotency (T6) had a subtle bug at first: concurrent retries were failing with overpick errors because I validated before checking if the event was already recorded. Fixing the order of checks (lock → dedup check → validate) sorted that out.

I leaned on the disabled reference tests in the scaffold as a checklist and enabled them as I went. Concurrency tests needed their own classes without `@Transactional` rollback — easy to miss if you're not reading the starter README carefully.

## Tradeoffs & Decisions

**What I cut or kept simple:**
- No allocation expiry or order cancellation flows
- No short-pick cascade when a bin count turns out wrong
- Audit `detail` is hand-built JSON strings, not a proper serializer — fine for now, wouldn't ship it that way
- `ORDER_STATUS_CHANGED` can log even when status hasn't actually changed — minor noise, didn't polish it

**What I'd do with another 2 hours:**
- Clean up the README/scaffold mismatch (this file still lived inside the starter template for a while)
- Add integration test for the full worked example from the brief (ORD-1001 end-to-end)
- Tighten audit event ordering with an explicit sequence number instead of relying on timestamps
- Maybe extract shared test helpers for create-and-allocate — there's some copy-paste across test classes

**Where the solution is weakest:**
- Short-pick and bin inventory corrections in a real warehouse are messier than what I modelled
- I haven't load-tested the locking strategy under heavy contention — it should be correct, but that's an assumption
- Error responses are functional but not as descriptive as they'd be in a production API (no structured problem details everywhere)

---

## AI assistance disclosure

I used Cursor for a few things:

1. Walking through the assignment at the start to make sure I understood the tasks and invariants.
2. Putting together a T1–T8 plan so I didn't lose track of what to do next.
3. Scaffolding tests and running `mvn test` to check each task before moving on.

The implementation choices above are mine — I went through the code task by task and committed as I went. Happy to talk through any of it in the pairing session.
