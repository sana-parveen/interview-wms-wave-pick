# Starter Scaffold — WMS Wave Pick Service (Java / Spring Boot)

This is an **optional** starting point. You can build on it, fork it, ignore it,
or rewrite it. We grade outcomes and reasoning, not adoption of this scaffold.

## Stack

- Java 21, Spring Boot 3.3
- Maven
- H2 in-memory database (with `MODE=PostgreSQL`)
- `JdbcTemplate` for persistence — raw SQL, deliberately, so that concurrency
  primitives and transaction boundaries are yours to write
- JUnit 5 + `MockMvc` for tests

## Run

```sh
./mvnw spring-boot:run     # (or: mvn spring-boot:run)
# service starts on http://localhost:8000

./mvnw test                # runs the test suite
```

If you don't have a wrapper, plain `mvn spring-boot:run` and `mvn test` work
identically (the scaffold doesn't ship a wrapper to keep the repo tiny).

`http://localhost:8000/h2-console` is enabled if you want to peek at the data
(JDBC URL: `jdbc:h2:mem:wms`, user `sa`, no password).

## What's provided

- `POST /orders` and `GET /orders/{orderId}` — fully implemented, use as a
  style reference for layering and error handling
- `GET /inventory/{skuId}` — fully implemented
- `POST /orders/{orderId}/allocate` — **stub** returning 501
- `POST /pick-events` — **stub** returning 501
- `POST /pick-events/short-pick` — **stub** returning 501
- `schema.sql` and `data.sql` matching §6 of the brief, with a couple of
  `-- TODO` markers where you should be making decisions
- `OrderControllerTest`, `InventoryControllerTest` — 5 passing tests showing
  the testing style we'd like to see
- `AllocationControllerTest`, `PickEventControllerTest`, `InvariantTest` —
  ~13 disabled reference tests. Each one's `@DisplayName` and inline comment
  describes the scenario. Enable them (and finish them) as you complete each
  TODO.

On a fresh checkout, `mvn test` should pass.

## What's not provided (your work)

The endpoint stubs throw 501 with a reference to the TODOs inside the
controller. Read them — each TODO is tagged with a task number (T1 … T8) that
maps to the suggested sequence in `01_candidate_take_home.md`.

The schema is incomplete on purpose:
- `pick_events` has no `client_event_id` uniqueness constraint — you decide what
  it should look like.
- There is no `audit_log` table — you decide whether you need one and what
  shape it takes.

## Design choices baked in (called out so you can change them deliberately)

- **JdbcTemplate, not JPA.** You write SQL. This makes concurrency primitives
  (`SELECT ... FOR UPDATE`, conditional `UPDATE`) explicit instead of hidden
  behind Hibernate.
- **`MODE=PostgreSQL`.** H2 accepts Postgres-flavored SQL, including
  `SELECT ... FOR UPDATE`. Write SQL you'd be comfortable shipping to Postgres.
- **CHECK constraints in `schema.sql`.** `quantity_on_hand >= 0` and
  `quantity_reserved <= quantity_on_hand` are enforced at the DB. You can rely
  on these or remove them; if you remove them, do so deliberately and explain why.
- **No service layer.** Controllers talk to repositories directly. Refactor if
  you prefer; we don't grade adherence to any particular layering.
- **Tests use `@Transactional` rollback** so each test starts from the seed
  state. Concurrency tests can't rely on this — handle their state explicitly.

## You may

- Change the schema, models, framework, or DB.
- Rewrite any provided code if you disagree with it.
- Delete tests that don't fit and write better ones.

## You should not

- Spend time on the scaffold's already-resolved decisions (deployment, auth,
  config management). Treat the scaffold as the contract. The brief is the contract.
