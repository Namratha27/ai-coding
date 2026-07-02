# Product Catalog Service — Coding Exercise (Senior Engineer)

Welcome! This is an **AI-assisted** coding exercise. You are encouraged to use
your AI coding agent (GitHub Copilot CLI, Copilot in your IDE, etc.) throughout.
We are interested in *how you direct the agent* and *how you reason about the
code*, not in whether you can type the fix from memory.

**Time:** ~30–45 minutes for the core task. The bonus is a stretch goal.

---

## The scenario

You've joined a team that owns a **Product Catalog Service** — a small HTTP API
backed by Azure:

- **Azure Cosmos DB** stores product documents.
- **Azure Blob Storage** stores product images.
- **Azure Storage Queue** carries domain events (e.g. "an image was added").

So that you can run everything locally with zero cloud setup, each of these Azure
dependencies is **simulated in-memory** (see the `simulated/` package). The
simulators expose the same method shapes as the real SDKs (`createItem`,
`readItem`, `uploadAsync`, `sendMessage`, …) and hold data only for the lifetime
of the process. **Do not** replace them with real Azure services — keep using the
simulators.

## Your task

The service already builds and runs, but **its test suite is failing.** Each
failing test documents a behaviour the service is *supposed* to have but
currently gets wrong. Your job:

1. Get the **entire test suite green** — without weakening, deleting, or
   trivially satisfying the tests. The tests are the spec; treat them as
   correct.
2. Keep the fixes clean and idiomatic, as you would for code review.

There are four behavioural areas covered by the failing tests (pagination,
resource creation, input validation, and HTTP not-found semantics). Use the test
output to localise each problem.

## API surface

| Method & path | Purpose |
|---|---|
| `GET /health` | Liveness probe |
| `GET /products?category=&page=&pageSize=` | List products (filter + paginate) |
| `GET /products/{id}` | Fetch one product |
| `POST /products` | Create a product `{name, category, price, stock}` |
| `PUT /products/{id}` | Update a product |
| `DELETE /products/{id}` | Delete a product |
| `POST /products/{id}/image` | **Bonus** — attach an image |

The store is seeded on startup with 12 products in category `demo`
(`demo-1` … `demo-12`).

## Bonus (stretch) — image upload, end-to-end

Implement `POST /products/{id}/image`. This must work **end-to-end across the
service and the simulated backend**:

1. `404` if the product does not exist.
2. Upload the request-body bytes to the **simulated Blob** container.
3. Persist the returned blob URL on the product (`imageUrl`).
4. Publish an **`ImageAdded`** event onto the **simulated Queue**.
5. Return the updated product.

There are two bonus tests describing exactly this contract.

---

## Local setup

You can set this up by hand, or — recommended — **let your agent do it.**

### Option A: ask your agent (recommended)

Open your AI agent in this folder and give it something like:

> "This folder has a `<language>` web service with a failing test suite. Detect
> the toolchain, install dependencies, build it, and run the tests. Then show me
> the failing tests and summarise what behaviour each one expects."

Then iterate: pick a failing test, ask the agent to explain the relevant code,
form a hypothesis, and instruct the fix. **Review every change** before accepting
it.

### Option B: manual commands

Pick the folder for your language.

**C# (`csharp/`)** — requires the .NET SDK
```powershell
cd csharp
dotnet test                      # build + run tests
dotnet run --project src/CatalogService   # run the API (http://localhost:5xxx)
```

**Python (`python/`)** — requires Python 3.11+
```powershell
cd python
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pytest -q          # run tests
.\.venv\Scripts\python.exe -m uvicorn app.main:create_app --factory --reload   # run the API
```

**Java (`java/`)** — requires JDK 21 + Maven
```powershell
cd java
mvn test                         # build + run tests
mvn spring-boot:run              # run the API (http://localhost:8080)
```

## Ground rules

- Keep using the in-memory simulators; don't add real Azure SDKs or external
  infrastructure.
- Don't change the tests to make them pass (you may *read* them as much as you
  like — they are your spec).
- Think out loud. Tell us what you're asking the agent and why, and what you're
  checking in its output.

Good luck!
