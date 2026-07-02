# Order Processing Service — Coding Exercise (Principal Engineering Manager)

This is an **AI-assisted** coding exercise. Use your AI coding agent freely. We
are evaluating how you **direct the agent** and the **technical judgement** you
bring to a distributed-systems problem — idempotency, inventory reservation,
compensation, and event-driven design — not your ability to type code unaided.

**Time:** ~30–45 minutes for the core task. The bonus is a stretch goal.

---

## The scenario

You own the **Order Processing Service**. It places customer orders, reserves
stock, and emits domain events. It is backed by Azure:

- **Azure Cosmos DB** — two containers: `orders` and `inventory`.
- **Azure Storage Queue** — `order-events` (OrderPlaced / OrderCancelled / …).

Every Azure dependency is **simulated in-memory** (see the `simulated/` package),
mirroring the real SDK shapes. No cloud, Docker, or emulator required. Keep using
the simulators — don't swap in real Azure services.

Inventory is seeded on startup:

| SKU | available | reserved |
|---|---|---|
| `SKU-1` | 10 | 0 |
| `SKU-2` | 5 | 0 |
| `SKU-3` | 0 | 0 |

## Your task

The service builds and runs, but the **test suite is failing.** The failing tests
specify correct behaviour the service currently violates. Get the **whole suite
green without weakening the tests.** The failing areas are all about
*correctness under real-world conditions*:

- **Idempotency** — a retried `POST /orders` (same `Idempotency-Key`) must not
  create a duplicate order or double-reserve stock.
- **Inventory reservation** — placing an order must atomically reserve stock;
  **oversell** (and unknown SKUs) must be rejected with `409`.
- **Cancellation / compensation** — cancelling an order must release the reserved
  stock, and must be **idempotent** (no double-restore).
- **Order total** — must account for line **quantity**, not just unit price.

Read the failing tests; they pin down every number and status code.

## API surface

| Method & path | Purpose |
|---|---|
| `GET /health` | Liveness probe |
| `POST /orders` | Place an order (header `Idempotency-Key`) `{lines:[{sku,quantity,unitPrice}]}` |
| `GET /orders/{id}` | Fetch an order |
| `POST /orders/{id}/cancel` | Cancel an order (release stock) |
| `GET /inventory/{sku}` | Inspect inventory levels |
| `POST /internal/drain-events` | **Bonus** — process queued events |
| `GET /internal/events` | Inspect the simulated queue (observability) |

## Bonus (stretch) — fulfillment saga, end-to-end

Design and implement an **event-driven fulfillment flow** across the service and
the simulated backend:

1. `POST /orders` already publishes an **`OrderPlaced`** event to the queue.
2. Implement `POST /internal/drain-events` so that a **queue consumer** drains
   new `OrderPlaced` events, transitions each referenced order to **`Fulfilled`**,
   and publishes an **`OrderFulfilled`** event.
3. It must be safe to call repeatedly (don't re-fulfill an already-fulfilled
   order).

Two bonus tests describe this contract. This is where we want to see you **design
the contract between layers**, not just patch a function.

---

## Local setup

Set this up by hand, or — recommended — **let your agent do it.**

### Option A: ask your agent (recommended)

> "This folder has a `<language>` web service with a failing test suite. Detect
> the toolchain, install dependencies, build, and run the tests. List the failing
> tests and summarise the behaviour each expects."

Then work test-by-test: have the agent explain the relevant code, form a
hypothesis with it, and **review every diff** — especially that fixes are atomic
and don't introduce race conditions or weaken assertions.

### Option B: manual commands

**C# (`csharp/`)** — .NET SDK
```powershell
cd csharp
dotnet test
dotnet run --project src/OrderService
```

**Python (`python/`)** — Python 3.11+
```powershell
cd python
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m uvicorn app.main:create_app --factory --reload
```

**Java (`java/`)** — JDK 21 + Maven
```powershell
cd java
mvn test
mvn spring-boot:run
```

## Ground rules

- Keep the in-memory simulators; no real Azure SDKs or external infra.
- Don't modify tests to make them pass — they are the spec.
- Narrate your reasoning: what you ask the agent, why, and what you verify in its
  output. We care as much about *how* you get there as the final diff.

Good luck!
