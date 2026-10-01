# Java-DDD: Build Domain-Driven Java Services Fast

[![CI](https://github.com/sklinkert/go-ddd/actions/workflows/go.yml/badge.svg)](https://github.com/sklinkert/go-ddd/actions/workflows/go.yml)
[![codecov](https://codecov.io/gh/sklinkert/go-ddd/branch/main/graph/badge.svg)](https://codecov.io/gh/sklinkert/go-ddd)
[![Java 25](https://img.shields.io/badge/Java-25-blue.svg)](pom.xml)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](pom.xml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

`java-ddd` jump-starts production-grade Java backends that keep business rules, infrastructure, and delivery code cleanly separated. Out of the box you get opinionated DDD building blocks, CQRS command and query flows, idempotent write paths, domain events with a transactional outbox, and tooling to keep schema and code in lockstep. It is a Java and Spring Boot port of [go-ddd](https://github.com/sklinkert/go-ddd) by Simon Klinkert.

> 📚 **New to DDD? [Start with the tutorial →](https://<owner>.github.io/java-ddd/)**
> A nine-chapter walkthrough that teaches Domain-Driven Design from zero, using this codebase as the running example.

## Why This Template

- **Model-first defaults** – Onion architecture keeps the domain pure while application services orchestrate infrastructure concerns. A test fails the build if a layer imports something it shouldn't.
- **Battle-tested patterns** – Commands, queries, repositories, value objects, domain events, and soft deletes mirror patterns used in real-world enterprise applications.
- **Idempotent pipelines** – Race-safe idempotency keys (atomic reservation, no check-then-write) make every command retry-safe.
- **Domain events + outbox** – State changes and their events are persisted together; a relay publishes them with at-least-once delivery.
- **Migration discipline** – Versioned Flyway migrations and plain SQL make schema evolution explicit and reproducible.

## What You Get

- Marketplace example that demonstrates aggregates (Seller, Product), a `Money` value object, cross-module interactions, and validation rules.
- Layered packages under `com.example.marketplace` for `domain`, `application`, `infrastructure`, `interfaces`, and `bootstrap`, with strict access: the outer layers have no public classes at all.
- A Spring Boot entry point (`MarketplaceApplication`) and a single wiring class (`bootstrap/ApplicationWiring`) where the layers meet.
- Flyway migrations in `src/main/resources/db/migration`, and hand-written SQL with `JdbcClient`.
- [OpenAPI spec](api/openapi.yaml), `/healthz` + `/readyz` probes, a one-command Docker Compose stack, and a black-box [contract suite](contract-tests/) for the API.

## Tech Stack Essentials

- **Java 25** and **Spring Boot 4.1** (Spring Framework 7).
- **Spring MVC** for REST endpoints, with **Jackson 3** for JSON.
- **`JdbcClient`** with plain SQL, the **PostgreSQL JDBC driver** and **HikariCP** for PostgreSQL access.
- **Flyway** handling SQL schema migrations, applied automatically at startup.
- **JUnit 5**, **AssertJ**, and **Testcontainers** to provision disposable Postgres instances during tests.
- **Spring Modulith** and **ArchUnit** to enforce the layer rules.
- **Time-ordered UUIDs** (version 7) generated inside the domain.
- A `Makefile`, `Dockerfile`, and `docker-compose.yml` for a one-command local stack.

## Design Principles in Action

Domain-Driven Design connects implementation to an evolving model. `java-ddd` showcases this by modelling a simple marketplace where `Sellers` manage `Products`, exercising aggregates, value objects, and validation flows.

Anatomy of a write request:

```mermaid
sequenceDiagram
    participant C as Client
    participant R as REST Controller
    participant S as Application Service
    participant D as Domain
    participant P as Postgres

    C->>R: POST /api/v1/products (Idempotency-Key)
    R->>S: CreateProductCommand
    S->>P: Reserve idempotency key (atomic)
    S->>D: Product.create(name, Money, ValidatedSeller)
    D->>D: validate() → ValidatedProduct + ProductCreated event
    S->>P: INSERT product + outbox event
    P-->>S: persisted row
    S->>P: store response for idempotency key
    S-->>R: CommandResult
    R-->>C: 201 Created (JSON)
    Note over P: Outbox relay publishes<br/>ProductCreated asynchronously
```

## Documentation

📚 **[DDD from zero: the full tutorial](https://<owner>.github.io/java-ddd/)** — nine chapters from "why DDD" to entities, value objects, aggregates, repositories, CQRS, the outbox, idempotency, and testing, all anchored to this codebase.

📖 **[Comprehensive DDD & CQRS Principles Guide](DDD_CQRS_PRINCIPLES.md)** - Learn how to apply these patterns to any business domain.

## Repository Structure

![ddd-diagram-onion.png](ddd-diagram-onion.png)

```text
src/main/java/com/example/marketplace/
├── MarketplaceApplication.java   # Spring Boot entry point
├── bootstrap/                    # ApplicationWiring: turns the application services into beans
├── domain/                       # the model; no framework imports
│   ├── entities/                 # Product, Seller, Money, ValidatedProduct, ...
│   ├── events/                   # DomainEvent, ProductCreated
│   └── repositories/             # repository interfaces
├── application/                  # use cases: commands, queries, results, services
├── infrastructure/               # JDBC repositories, outbox relay, JSON, database config
└── interfaces/api/rest/          # controllers, request/response DTOs, HTTP concerns
src/main/resources/db/migration/  # Flyway migrations V1__ … V4__
contract-tests/                   # black-box HTTP contract suite
```

- `domain`: The heart of the software, representing business logic and rules.
    - `entities`: Fundamental objects within our system, like `Product` and `Seller`. Contains basic validation logic.
- `application`: Contains use-case specific operations that interact with the domain layer.
- `infrastructure`: Supports the higher layers with technical capabilities like database access.
    - `db/postgres`: Concrete implementations of our storage needs.
- `interfaces`: The external layer which interacts with the outside world, like API endpoints.
    - `api/rest`: Controllers for managing HTTP requests and responses.

## Further principles

- Domain
  - Must not depend on other layers.
  - Provides infrastructure with interfaces, but must not access infrastructure.
  - Implements business logic and rules.
  - Executes validations on entities. Validated entities are passed to the infrastructure layer.
  - Domain layer sets defaults of entities (e.g. uuid for ID or creation timestamp). Don't set defaults in the infrastructure layer or even database!
  - Do not leak domain objects to the outside world.
- Application
  - The glue code between the domain and infrastructure layer.
- Infrastructure
   - Repositories are responsible for translating a domain entity to a database model and retrieving it. No business logic is executed here.
   - Implements interfaces defined by the domain layer.
   - Implements persistence logic like accessing a postgres or mysql database.
   - When writing to storage, read written data before returning it. This ensures that the data is written correctly.

## Best Practices

- Don't return validated entities from read methods in the repository. Instead, return the domain entity type directly.
  - Validations will change over time. You don't want to migrate all the data in your database. Instead, you should guarantee you can always load historical data, regardless of how your validation logic has evolved.
  - Otherwise, you won't be able to read data from the database that was written with a different validation logic. You will have to handle errors at runtime.
  - Push validation to the write side-creation (`create`) and update methods - where you must enforce invariants anyway.
- Don't put default values (e.g current timestamp or ID) in the database. Set them in the domain layer (factory!) for several reasons:
  - It's quite dangerous to have two sources of truth.
  - It's easier to test the domain layer.
  - Databases can get replaced, and you don't want to have to change all your default values. 
- Always read the entity after write in the infrastructure layer.
  - This ensures that the data is written correctly, and we are never operating on stale data.
- `find` vs `get`:
  - `find` methods can return an empty `Optional` or an empty list.
  - `get` methods must return a value. If the value is not found, throw an exception.
- Deletion: Always use soft deletion. Create a `deleted_at` column in your database and set it to the current timestamp when deleting an entity. This way, you can always restore the entity if needed.

## CQRS and Idempotency

### Command Query Responsibility Segregation (CQRS)
CQRS separates read operations (queries) from write operations (commands) in your application. In this codebase:
- **Commands** modify state (CreateSellerCommand, CreateProductCommand, UpdateSellerCommand)
- **Queries** retrieve data without side effects (findAllSellers, findSellerById)

This separation enables different optimization strategies:
- **Write optimization**: Commands can use normalized schemas, ACID transactions, and strong consistency
- **Read optimization**: Queries can use denormalized views, caching, read replicas, or even different databases (e.g., PostgreSQL for writes, Elasticsearch for reads)
- **Scalability**: Read and write workloads can be scaled independently based on actual usage patterns
- **Performance**: Complex queries don't impact write performance, and write locks don't block read operations

### Idempotency Keys
Idempotency ensures that multiple identical requests have the same effect as a single request. This is crucial for handling network failures and retries in distributed systems. Implementation:
- Every mutating endpoint (create, update, **and delete**) accepts an optional key. The conventional `Idempotency-Key` HTTP header is preferred; an `idempotency_key` field in the JSON body is still honored as a fallback, and the header wins when both are sent
- The key is **reserved atomically** (`INSERT ... ON CONFLICT DO NOTHING`), so two concurrent requests with the same key can never both execute — no check-then-write race
- A completed request returns its cached response; a still-running one returns an "in progress" error so the client retries later
- Reusing a key with a **different payload** is rejected instead of silently returning the wrong cached response
- If the command fails, the reservation is released so the client can retry; reservations orphaned by a crash expire after a TTL

This prevents duplicate entities from being created when clients retry failed requests.

### Domain Events and the Transactional Outbox

Aggregates record events (e.g. `ProductCreated`) when something business-relevant happens. Instead of publishing them directly to a broker — which risks losing events when the process crashes between the DB commit and the publish — events are stored in an `outbox_events` table. A relay polls the outbox and publishes unpublished events with at-least-once delivery. See `domain/events/` and `infrastructure/outbox/`.

## Database Migrations

This project uses [Flyway](https://documentation.red-gate.com/flyway) for database schema management. Migrations live in `src/main/resources/db/migration/` and are numbered by version.

### Migration Files Structure
```
src/main/resources/db/migration/
├── V1__initial_schema.sql       # Creates initial tables
├── V2__price_as_money.sql       # Money as integer cents + currency
├── V3__outbox.sql               # Transactional outbox table
├── V4__price_minor_units.sql    # Rename to ISO 4217 minor units
└── ...
```

### Running Migrations

The application applies pending migrations itself when it starts, before it serves any traffic. Flyway records what it applied in the `flyway_schema_history` table and takes a lock, so several instances starting at once are safe.

To run them without starting the app, use the Flyway Maven plugin through the Makefile:

```bash
# Apply all pending migrations
make migrate-up DB_JDBC_URL=jdbc:postgresql://localhost:5432/db DB_USER=user DB_PASSWORD=pass

# Show applied and pending migrations
make migrate-info
```

Flyway migrations are forward-only: to undo a change, write a new migration that reverses it.

A database first created by the Go version of this project already has the schema but no Flyway history. Start the app once with `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=4`, and Flyway will adopt it as version 4.

### Creating New Migrations
Add a file named `V<next number>__<description>.sql`, for example `V5__add_user_email_column.sql`, to `src/main/resources/db/migration/`. It is applied on the next start, or with `make migrate-up`.

### Migration Best Practices
- Write a new migration to undo a change; never edit one that has been applied
- Test migrations on a copy of production data
- Keep migrations small and focused
- Never modify existing migration files once they've been applied in production (Flyway's checksums will refuse to start)
- Use descriptive names for migration files

## Getting Started

> Requires **Java 25+**, **Maven 3.9+**, and **Docker**. Run `make help` to see all available targets.
>
> Maven resolves dependencies through the repositories in your `~/.m2/settings.xml`. The Docker build mounts that file (and `settings-security.xml`, if your passwords are encrypted) as build secrets, so they never end up in an image layer. `.mvn/rrf/groupId-central.txt` stops Maven from falling back to Maven Central directly.

### Quickstart with Docker Compose

Bring up Postgres, apply migrations, and start the API in one command:

```bash
make docker-up        # docker compose up --build
# API is now available on http://localhost:8080
make docker-down      # tear everything down
```

### Try the API in 30 seconds

Create a seller:

```bash
curl -s -X POST http://localhost:8080/api/v1/sellers \
  -H 'Content-Type: application/json' \
  -d '{"name": "Acme Corp", "idempotency_key": "create-acme-1"}'
```

```json
{"id":"0197a3c2-...","name":"Acme Corp","created_at":"2026-07-14T09:00:00Z","updated_at":"2026-07-14T09:00:00Z"}
```

Create a product for that seller (prices are integer minor units — never floats):

```bash
curl -s -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name": "Wooden Chair", "price_minor_units": 4999, "currency": "EUR", "seller_id": "<seller-id-from-above>"}'
```

```json
{"id":"0197a3c3-...","name":"Wooden Chair","price_minor_units":4999,"currency":"EUR","seller_id":"0197a3c2-...","created_at":"...","updated_at":"..."}
```

Replay a request with the same `idempotency_key` — you get the cached response back instead of a duplicate seller:

```bash
curl -s -X POST http://localhost:8080/api/v1/sellers \
  -H 'Content-Type: application/json' \
  -d '{"name": "Acme Corp", "idempotency_key": "create-acme-1"}'
# → identical response, no second row created
```

List products and check service health:

```bash
curl -s http://localhost:8080/api/v1/products
curl -s http://localhost:8080/readyz
```

The full API is described in the [OpenAPI spec](api/openapi.yaml).


### Local development

1. Clone this repository:
```bash
git clone https://github.com/<owner>/java-ddd.git
cd java-ddd
```

2. Start a PostgreSQL database, for example the one from the compose file:
```bash
docker compose up -d postgres
```

3. Run the application. It applies the migrations on startup:
```bash
make run
# Override the database via the DATABASE_URL env var (libpq DSN or postgres:// URL),
# and the port via PORT (default 8080).
export DATABASE_URL="postgres://user:password@localhost/dbname?sslmode=disable"
```

### Common Make targets

```bash
make build         # build the application jar into target/marketplace.jar
make test          # run all tests, unit and Testcontainers (needs Docker)
make test-unit     # run only tests that don't require Docker
make cover         # run all tests and print total line coverage
make migrate-up    # apply migrations against DB_JDBC_URL
make migrate-info  # show applied and pending migrations
make contract      # run the black-box contract suite against CONTRACT_BASE_URL
```

### Contributions
Contributions, issues, and feature requests are welcome! Feel free to check the issues page.

### Use This Template

Click **"Use this template"** on GitHub to bootstrap your own service from this structure, or fork it and replace the marketplace domain with your own. The [DDD & CQRS guide](DDD_CQRS_PRINCIPLES.md) walks you through adapting the patterns to any business domain.

If this template helps you, **give it a ⭐** — it helps others find it.

[![Star History Chart](https://api.star-history.com/svg?repos=<owner>/java-ddd&type=Date)](https://star-history.com/#<owner>/java-ddd&Date)

### License
Distributed under the MIT License. See LICENSE for more information.
