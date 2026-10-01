# Migration plan: Go (Echo, pgx, sqlc) → Java 25 + Spring Boot 4

> **Status: executed (2026-09-29) on the `java-migration` branch, uncommitted.** Phases 0–10 are
> done. [§2](#2-decisions) records your decisions; the execution notes below record where the
> work departed from or added to this plan.

### Execution notes

- **Maven Central is blocked at the project level.** `~/.m2/settings.xml` routes to JFrog
  through a profile, not a `<mirror>`, so Maven would still fall back to Central for anything
  JFrog lacks. `.mvn/maven.config` enables Maven's remote repository filter, and
  `.mvn/rrf/groupId-central.txt` (`!*`) rejects every request to Central.
- **Docker needs a second build secret.** The passwords in `settings.xml` are encrypted, so the
  build also mounts `~/.m2/settings-security.xml` as a BuildKit secret. Neither file ends up in
  the image.
- **No Maven Wrapper (your decision).** A wrapper pointed at JFrog would need credentials for its
  first download (`MVNW_USERNAME` / `MVNW_PASSWORD`). Builds use Maven from the PATH instead
  (`MVN ?= mvn` in the Makefile), and the enforcer requires Maven 3.9 or newer. Wherever this
  plan still says `./mvnw`, read `mvn`.
- **One more Jackson strictness rule than §3 lists.** Numbers and booleans are also rejected for
  string fields (`"name": 5` → 400), matching Go and the spec's `type: string`.
- **Path ids are bound with a property editor, not a converter.** When a converter rejects a
  value, Spring falls back to its lenient built-in UUID editor, which accepted `1-1-1-1-1`.
- **HikariCP fails fast.** `connection-timeout` is 2 s (Spring's default is 30 s), so `/readyz`
  and the 500 fallbacks answer in seconds during a database outage, as Go's did.
- **500 fallbacks are logged at ERROR** with the cause. Go returned them silently.
- **Test placement.** Go's two `Money` JSON tests live in `MoneyJsonModuleTest` (infrastructure),
  because the domain has no Jackson. The unused Go `ProductQueryResult`/`SellerQueryResult` list
  types were not ported.
- **Tests added beyond §7:** `pullEvents_clearsEvents` (quoted in chapter 9), `OutboxRelayIT`,
  `HealthControllerTest`, and an empty-`DATABASE_URL` fallback test.
- **Contract suite:** 66 scenarios. Against Go, the 56 untagged ones pass and the 10
  `changed-from-go` ones fail for their stated reasons; against Java, all 66 pass, including the
  database outage.
- **Left as-is:** `.github/dependabot.yml` still targets `gomod`, so after the cutover it updates
  nothing; `SECURITY.md` no longer claims Dependabot coverage.

## Contents

1. [Goal](#1-goal)
2. [Decisions](#2-decisions)
3. [Target stack and dependency policy](#3-target-stack-and-dependency-policy)
4. [Behavioral contract](#4-behavioral-contract)
5. [Target architecture and Go → Java mapping](#5-target-architecture-and-go--java-mapping)
6. [Execution phases](#6-execution-phases)
7. [Test porting inventory](#7-test-porting-inventory)
8. [Documentation and tutorial rewrite inventory](#8-documentation-and-tutorial-rewrite-inventory)
9. [Go behaviors intentionally not carried over](#9-go-behaviors-intentionally-not-carried-over)
10. [Risks and mitigations](#10-risks-and-mitigations)
11. [Final acceptance checklist](#11-final-acceptance-checklist)
- [Appendix A: helper specifications](#appendix-a-helper-specifications)

---

## 1. Goal

### Deliverables

1. A Java 25 / Spring Boot 4 application that replaces the Go `marketplace` service.
2. Every code example and every Go-specific explanation in the tutorial, reference docs, README,
   and contributor docs rewritten for Java and Spring, keeping the same teaching points.
3. Build, tests, Docker image, docker-compose, and Makefile moved to the Java toolchain.
   **CI is out of scope:** `.github/workflows/`, `.github/dependabot.yml`, and `codecov.yml` are
   left untouched.
4. The Go sources, vendored modules (2,324 files under `vendor/`), and Go tooling removed at the
   end.

### What "function exactly like the current app" means after your decisions

| Area | Rule |
|---|---|
| **API contract**: routes, methods, status codes, JSON field names and types, the `{"error": …}` bodies and their messages, the `Idempotency-Key` header, `X-Request-Id` | Must match the Go app, except where the docs say otherwise (§2.2) |
| **Business behavior**: domain rules and messages, idempotency semantics, soft deletes, transactions, the outbox and relay, health checks | Must match |
| **Configuration and running it**: `DATABASE_URL`, `PORT`, one-command `docker compose up` | Must match |
| **Framework-level edges**: 404/405/500 bodies, OPTIONS/HEAD, JSON parsing leniency, log format, startup/shutdown log lines | Spring defaults (D10, D13) |
| **Where docs and code disagree** | The docs win (D11, §2.2) |
| **Not carried over** | Byte-level output details, reading Go-written idempotency rows, the Go `migrate` CLI and down migrations. Full list in [§9](#9-go-behaviors-intentionally-not-carried-over) |

### How we'll verify it

- A small **black-box contract test suite** is written first (Phase 0). It encodes the Java
  target contract and runs against the **Go** app, to confirm my reading of today's behavior. The
  tests that are *meant* to differ (the §2.2 fixes and the §9 items) are tagged, so the Go run
  shows exactly those failures and no others. The Java app then has to pass every test in the
  suite.
- All **165 Go test functions** are ported to Java ([§7](#7-test-porting-inventory)).

---

## 2. Decisions

### 2.1 Your decisions and what they mean for the plan

| ID | Your answer | Effect on the plan |
|---|---|---|
| D1 Repo layout | Default | The Java app is built alongside the Go app on a `java-migration` branch; Go is deleted in the last phase |
| D2 Build tool | Default | Maven 3.9+ from the PATH; no wrapper (see the execution notes) |
| D3 Parent POM | Default | `spring-boot-starter-parent` 4.1.1 |
| D4 Coordinates | Default | `com.example:java-ddd`, base package `com.example.marketplace` |
| D5 Identity | Default | Repo URL, docs site, image name, and badges use `<owner>/java-ddd` placeholders until you provide real values. `LICENSE` keeps the original `Copyright (c) 2023 Simon Klinkert` line and adds one for the port. The author's first-person voice is kept, reworded only where a claim would be false for Java |
| D6 Data access | Default | `JdbcClient` with SQL in Java text blocks, copied verbatim from `sql/queries/*.sql` |
| D7 Migrations | **Flyway** | The four migrations become `V1__…` through `V4__…` under `src/main/resources/db/migration`, with identical SQL. The `.down.sql` files and the `migrate` CLI are removed. How migrations run: see §2.3 item 1 |
| D8 Web stack | Default, **no virtual threads** | Spring MVC on Tomcat's standard thread pool |
| D9 Persisted JSON | **Java-natural** | Idempotency fingerprints and cached responses are written by the app's own Jackson mapper. There's no attempt to read Go-written idempotency rows |
| D10 HTTP edges | **Spring defaults** | `@RequestBody` binding. Spring's standard handling of unknown routes, wrong methods, unhandled errors, OPTIONS, and HEAD. No byte-level emulation. The app's own error bodies (`{"error": …}` with the existing messages and status codes) stay, because the OpenAPI spec documents them |
| D11 Quirks | **Preserve, but docs win** | Quirks are kept unless a doc contradicts them (§2.2) |
| D12 Content types | **JSON only, per the spec** | Write endpoints declare `consumes = application/json`; any other content type gets Spring's standard 415 |
| D13 Logging | **Spring defaults** | Spring Boot's default console logging. The app keeps its own log messages, with attributes written as `key=value` text. Spring's startup and shutdown lines replace Go's custom ones |
| D14 Linting | **None** | No formatter, linter, or static analysis. The `make lint` and `make fmt` targets are dropped. The layer-rule test (§2.3 item 4) is a unit test, not a linter |
| D15 Vulnerability scanning | **None** | The `make vulncheck` target is dropped |
| D16 Dependency source | **CFA JFrog via your `~/.m2/settings.xml`** | No settings file in the repo. Docker builds mount `~/.m2/settings.xml` as a BuildKit build secret, so it never lands in an image layer |
| — CI | **Out of scope** | `.github/workflows/`, `.github/dependabot.yml`, and `codecov.yml` are not touched |
| D17 Images | **Eclipse Temurin, non-root** | Build stage `maven:3.9-eclipse-temurin-25` (Temurin with Maven preinstalled). Runtime `eclipse-temurin:25-jre` running as a dedicated non-root user. Both pinned by digest |
| D18 Layering | **Single module, strict access** | Package-private by default. The infrastructure and interface layers expose **no public classes at all** (§5.2) |
| D19 Test doubles | Default | Hand-written fakes for application tests; Mockito for controller tests (where Go used testify/mock) |
| D20 `README.zh-CN.md` | **Remove** | Deleted, together with the README's link to it |
| D21 go-outbox mention | Default | Replaced with Spring Modulith's Event Publication Registry as the packaged Java alternative |
| D22 Onion diagram | Default | Kept as attribution |

### 2.2 Where the docs and the code disagree (the docs win, per D11)

| # | What the docs say | What Go does today | What Java will do |
|---|---|---|---|
| 1 | OpenAPI: `sellers` is an array | An empty list returns `{"sellers":null}` | Return `{"sellers":[]}` |
| 2 | OpenAPI: `id` is required in `UpdateSellerRequest`, and `PUT /api/v1/sellers` documents only 200 and 400 | A missing or `null` `id` becomes the nil UUID, and the service answers 404 `seller not found` | Return 400 `{"error":"Failed to parse request body"}` |
| 3 | OpenAPI: `price_minor_units` is an integer. README: prices are "integer minor units — never floats" | Go rejects floats and numeric strings | Keep rejecting them. This needs two Jackson leniencies turned off; otherwise `49.99` would silently become `49` and `"4999"` would be accepted |
| 4 | Chapter 7 "Try it": the relay logs `publishing domain event` with `event_name=product.created` | The attribute is called `event` | Log `event_name=product.created` |
| 5 | Chapter 9: the outbox tests prove a failed product insert also rolls back its outbox rows | No test asserts this (the behavior itself is correct) | Add the test |

Quirks kept, because no doc contradicts them:

- Seller PUT takes the id in the body (the spec documents that).
- An invalid `seller_id` on product create reports `"Invalid product Id format"`.
- An idempotent create replay returns 201 (chapter 8 says "the same `201` body").
- The cached status code is recorded as 200.

### 2.3 Choices within your decisions (all confirmed)

1. **Flyway runs when the app starts.** *Confirmed.* This is Spring Boot's default once Flyway
   is on the classpath.
   - The compose file loses its one-shot `migrate` container. `app` waits for Postgres to be
     healthy and migrates itself; Flyway's own lock makes concurrent starts safe.
   - `make migrate-up` stays (it runs the Flyway Maven plugin), and a new `make migrate-info`
     shows the applied versions.
   - `make migrate-down` goes away, because Flyway Community can't undo migrations.
2. **Outbox payload keys become Java-style camelCase.** *Confirmed.*
   - The event JSON the relay publishes uses `eventId`, `aggregateId`, `occurredAt` (ISO-8601
     UTC), `name`, `priceMinorUnits`, `currency`, `sellerId`.
   - Go writes its untagged field names instead (`Id`, `Aggregate`, `OccurredAtT`, `Name`,
     `PriceMinorUnits`, `Currency`, `SellerId`). `OccurredAtT` exists only because Go can't have
     a field and a method with the same name.
   - The format isn't documented anywhere, and nothing consumes it yet: the publisher only logs.
   - The V4 migration SQL stays verbatim. It's history, and it only touches legacy rows that
     still contain `PriceCents`.
   - Rows the Go app wrote but hadn't published at switch-over still go out in the old shape. The
     relay drains them within seconds.
3. **Ids must be canonical 36-character UUIDs.** *Confirmed.* `api/openapi.yaml` marks every id
   (the path parameter, `id`, and `seller_id`) as `format: uuid`. The OpenAPI format registry
   defines that as a UUID per RFC 4122 (now RFC 9562), whose string form is 8-4-4-4-12 hex digits
   with hyphens, case-insensitive on input. Go also accepts `{…}`, `urn:uuid:…`, and 32-hex
   forms. Java's `UUID.fromString` accepts odd strings like `1-1-1-1-1`. A small strict parser
   (Appendix A.1) rejects both kinds.
4. **The layer table in `docs/reference/architecture.md` is enforced by a test: Spring Modulith
   plus an ArchUnit allowlist.** *Confirmed.* Details are in §5.2.
   - Access modifiers already stop anything from importing infrastructure or interfaces. Two
     rules are beyond them: domain must not import application's public records, and domain and
     application must not use Spring, Jackson, or JDBC.
   - Each layer package is a Spring Modulith module whose `allowedDependencies` mirror the docs
     table. `ModuleStructureTest` runs `ApplicationModules.verify()`, which rejects cycles and any
     undeclared dependency.
   - Modulith only checks dependencies between your own modules, not on libraries. So the same
     test adds an ArchUnit allowlist for what domain and application may use.
   - Accepted trade-off: each layer's `package-info.java` carries a Modulith annotation,
     including domain and application. The allowlist excludes those files.

---

## 3. Target stack and dependency policy

### Stack mapping

| Concern | Go today | Java target |
|---|---|---|
| Language | Go 1.26 | Java 25 LTS (Temurin 25.0.2 is installed locally) |
| HTTP | Echo v4.15.4 | Spring Boot 4.1.1 (current GA), Spring Framework 7, Spring MVC (`spring-boot-starter-webmvc`) |
| DB driver and pool | pgx/v5, pgxpool | PostgreSQL JDBC driver, HikariCP (`spring-boot-starter-jdbc`) |
| SQL access | sqlc-generated code | `JdbcClient` + text blocks (D6) |
| Transactions | `pgx.Tx` | `TransactionTemplate` |
| Migrations | golang-migrate v4.19.1 + `migrate.go` | Flyway (`spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`), run at startup; `flyway-maven-plugin` for manual runs |
| JSON | `encoding/json` | Jackson 3 (`tools.jackson.*`) with Spring Boot defaults, plus the two changes below |
| UUIDv7 | `google/uuid` | `com.fasterxml.uuid:java-uuid-generator:5.2.0` (`Generators.timeBasedEpochGenerator()`) |
| Logging | `log/slog` JSON | SLF4J + Logback with Spring Boot's default console format (D13) |
| Scheduling (relay) | goroutine + `time.Ticker` | `@Scheduled` |
| Unit tests | `testing` + testify | JUnit Jupiter (version managed by Boot) + AssertJ; Mockito for controller tests |
| Integration tests | testcontainers-go, `postgres:17-alpine` | Testcontainers 2.0.x (`testcontainers-postgresql`, `testcontainers-junit-jupiter`), `postgres:17-alpine` |
| HTTP tests | `httptest` | MockMvc (`spring-boot-starter-webmvc-test`) |
| Coverage | `go test -coverprofile` (`make cover`) | JaCoCo (`make cover`) |
| Layer rules | Code review against `docs/reference/architecture.md` | Spring Modulith 2.x (`ApplicationModules.verify()`) + ArchUnit, in `ModuleStructureTest` |
| Lint, format, vulnerability scan | golangci-lint, gofmt, govulncheck | None (D14, D15) |
| Build | `go build`, Makefile | Maven, with the Makefile kept as a front end |
| Image | distroless static, non-root | `eclipse-temurin:25-jre`, non-root (D17) |
| Docs | mkdocs-material | Unchanged |

### Jackson changes from Spring Boot's defaults

Everything else stays at Spring Boot's defaults.

| Setting | Why |
|---|---|
| Reject floats for integer fields (disable `ACCEPT_FLOAT_AS_INT`) | §2.2 item 3: money is never a float, and truncating `49.99` to `49` would be a silent data error |
| Reject numeric strings for integer fields (coercion textual → integer = fail) | §2.2 item 3: the spec types `price_minor_units` as an integer |
| Keep declaration order for response fields | The README examples show `id, name, price_minor_units, …`, and Jackson 3 sorts properties alphabetically by default |

### Dependency and supply-chain policy

This follows the workspace security rule.

- **Dependency source.** Every artifact resolves through the mirror configured in your
  `~/.m2/settings.xml` (CFA JFrog). `pom.xml` declares no `<repositories>` or
  `<pluginRepositories>`, and the enforcer rule `requireNoRepositories` guarantees that stays
  true.
- **Checksums.** `.mvn/maven.config` contains `--strict-checksums`, so any checksum mismatch
  fails the build, whether it runs from the CLI, the IDE, or Docker.
- **Maven Wrapper.** Its `distributionUrl` points at the approved mirror, and
  `distributionSha256Sum` pins the Maven download.
- **Pinning.** Docker base images are pinned by `@sha256:` digest.
- **Versions.** Exact patch versions are looked up in JFrog at execution time; no version number
  is made up. If something can't be resolved from the mirror, I stop and ask you instead of
  pulling it from anywhere else.
- **Auth errors.** Authentication or authorization errors (JFrog, Docker Hub) are reported
  to you with troubleshooting options. I'll wait for your guidance rather than working around
  them.

---

## 4. Behavioral contract

This section is the specification. The Phase 0 contract suite encodes it, and the Java code
implements it. "Go" means the current app.

### 4.1 Routes

| Method | Path | Success | Request body |
|---|---|---|---|
| POST | `/api/v1/products` | 201 `ProductResponse` | `CreateProductRequest` |
| GET | `/api/v1/products` | 200 `{"products":[…]}` | — |
| GET | `/api/v1/products/{id}` | 200 `ProductResponse` | — |
| PUT | `/api/v1/products/{id}` | 200 `ProductResponse` | `UpdateProductRequest` |
| DELETE | `/api/v1/products/{id}` | 204, no body | — (idempotency key via header only) |
| POST | `/api/v1/sellers` | 201 `SellerResponse` | `CreateSellerRequest` |
| GET | `/api/v1/sellers` | 200 `{"sellers":[…]}` | — |
| PUT | `/api/v1/sellers` | 200 `SellerResponse` | `UpdateSellerRequest` (id in the body) |
| GET | `/api/v1/sellers/{id}` | 200 `SellerResponse` | — |
| DELETE | `/api/v1/sellers/{id}` | 204, no body | — (idempotency key via header only) |
| GET | `/healthz` | 200 `{"status":"ok"}` | — |
| GET | `/readyz` | 200 `{"status":"ok"}`, or 503 | — |

Unknown paths, unsupported methods, OPTIONS, HEAD, and unhandled exceptions all use Spring
Boot's standard behavior (D10).

### 4.2 Requests

- **Content type.** Write endpoints accept only `application/json` (charset parameters are fine).
  Anything else → 415 (D12).
- **Body parse failures** → 400 `{"error":"Failed to parse request body"}`. This covers:
  - a missing or empty body;
  - malformed JSON;
  - wrong JSON types, including a float or string `price_minor_units` (§2.2 item 3);
  - a non-UUID `id` on seller PUT;
  - a missing or `null` `id` on seller PUT (§2.2 item 2).
- **JSON field names** (the snake_case names from the Go tags):
  - `CreateProductRequest`, `UpdateProductRequest`: `idempotency_key`, `name`,
    `price_minor_units` (int64), `currency`, `seller_id` (string).
  - `CreateSellerRequest`: `idempotency_key`, `name`.
  - `UpdateSellerRequest`: `idempotency_key`, `id` (UUID), `name`.
- Unknown JSON fields are ignored.
- **Idempotency key.** A non-empty `Idempotency-Key` header wins over the body's
  `idempotency_key`. DELETE uses only the header.
- **Ids.** Path ids and `seller_id` must be canonical UUIDs (Appendix A.1); upper and lower case
  are both accepted.
- **Check order:**
  - PUT product: path id, then body, then `seller_id`.
  - PUT seller: body (including `id`), then the service.

### 4.3 Responses

- `Content-Type: application/json`.
- `ProductResponse` fields, in this order: `id, name, price_minor_units, currency, seller_id,
  created_at, updated_at`.
- `SellerResponse` fields: `id, name, created_at, updated_at`.
- Lists are `{"products":[…]}` and `{"sellers":[…]}`: both are `[]` when empty (§2.2 item 1) and
  both are ordered by `created_at DESC`.
- UUIDs are lower-case canonical. Timestamps are ISO-8601 in UTC, e.g.
  `2026-07-14T09:00:00Z`, with fractional seconds when non-zero.
- Create and update responses are built from the **validated in-memory entity**, not from a
  database read-back (as in Go).
- `X-Request-Id` is on every response. It echoes a non-empty incoming `X-Request-Id`; otherwise
  it's 32 random characters from `[A-Za-z0-9]` (the same as Echo's generator).

### 4.4 Error responses by endpoint

| Endpoint | Condition | Status | `error` message |
|---|---|---|---|
| POST products | body parse failure | 400 | `Failed to parse request body` |
| | `seller_id` not a UUID (including empty or missing) | 400 | `Invalid product Id format` (sic) |
| | service error | §4.5 | fallback `Failed to create product` |
| GET products | repository error | 500 | `Failed to fetch products` |
| GET products/{id} | bad id | 400 | `Invalid product Id format` |
| | repository error | 500 | `Failed to fetch product` |
| | not found (including a product whose seller was soft-deleted) | 404 | `Product not found` |
| PUT products/{id} | bad path id | 400 | `Invalid product Id format` |
| | body parse failure | 400 | `Failed to parse request body` |
| | bad `seller_id` | 400 | `Invalid seller Id format` |
| | service error | §4.5 | fallback `Failed to update product` |
| DELETE products/{id} | bad id | 400 | `Invalid product Id format` |
| | service error | §4.5 | fallback `Failed to delete product` |
| POST sellers | body parse failure | 400 | `Failed to parse request body` |
| | service error | §4.5 | fallback `Failed to create seller` |
| GET sellers | repository error | 500 | `Failed to fetch sellers` |
| GET sellers/{id} | bad id | 400 | `Invalid seller Id format` |
| | repository error | 500 | `Failed to fetch seller` |
| | not found | 404 | `Seller not found` |
| PUT sellers | body parse failure, including a bad or missing `id` | 400 | `Failed to parse request body` |
| | service error | §4.5 | fallback `Failed to update seller` |
| DELETE sellers/{id} | bad id | 400 | `Invalid seller Id format` |
| | service error | §4.5 | fallback `Failed to delete seller` |
| GET readyz | database check fails | 503 | body `{"status":"unavailable","reason":"database unreachable"}` |

### 4.5 Service error mapping

This is Go's `writeCommandError`, ported as `RestErrors`. The first match wins:

1. Product or seller not found → 404 `{"error": <message>}`.
2. Validation → 400 `{"error": <message>}`.
3. Request in flight → 409 `{"error": <message>}`.
4. Idempotency key reuse → 422 `{"error": <message>}`.
5. Anything else → 500 `{"error": <fallback>}`.

These are the exact messages that reach clients:

- `product not found`
- `seller not found`
- `validation failed: name must not be empty`
- `validation failed: price must be greater than 0`
- `validation failed: seller id must not be empty`
- `validation failed: created_at must be before updated_at`
- `validation failed: amount must not be negative`
- `validation failed: unsupported currency "XYZ"`
- `a request with this idempotency key is already in progress`
- `idempotency key was already used with a different request`

**Rule for the Java code:** each exception's `getMessage()` is exactly the Go error string for
the same failure.

### 4.6 Domain rules

**Money**

- Holds `minorUnits` (a long) and a `Currency` (a string code). Supported currencies and their
  exponents: EUR → 2, USD → 2.
- Checks, in this order: negative → `amount must not be negative`; unsupported →
  `unsupported currency "<code>"`. Zero is allowed at the Money level.
- `toString()` uses the currency exponent: `12.34 USD`; with exponent 0, `5000 JPY`; with
  exponent 3, `0.005 BHD`.
- JSON form, used inside cached idempotency responses and documented in chapter 3:
  `{"minor_units":N,"currency":"XXX"}`. Decoding goes through the constructor, so validation
  runs.

**Product**

- Fields: `id` (UUIDv7), `createdAt`, `updatedAt`, `name`, `price`, `sellerId`, and pending
  domain events.
- `validate()` checks, in order: empty name → price minor units == 0 → nil/null seller id →
  created after updated.
- `Product.create(name, price, ValidatedSeller)` assigns a new v7 id, sets
  created = updated = now, validates, and records a `ProductCreated` event.
- `updateName`, `updatePrice`, and `assignSeller` mutate the field, set `updatedAt = now`, then
  validate. **The mutation stays in place when validation fails**, and tests pin that.
- `pullEvents()` returns the pending events and clears them.

**Seller**

- Validation: empty name → error; created after updated → error.
- Names are not trimmed, so a whitespace-only name is valid.
- `updateName` follows the same mutate-then-validate order.

**ValidatedProduct / ValidatedSeller**

- Built only through a factory that validates a **copy** of the entity.
- Repository write methods accept only these validated types.

**IdempotencyRecord**

- `id` (v7), `key`, `request`, `response` (`""`), `statusCode` (0), `createdAt` (now).
- `isCompleted()` is `statusCode != 0`.

**Events**

- `ProductCreated`, name `product.created`.
- Base fields: `id` (v7), `aggregate` (the product id), `occurredAt` (now).
- Plus `name`, `priceMinorUnits`, `currency` (string), `sellerId`.

### 4.7 Application behavior

All six commands (create, update, and delete, for both products and sellers) run inside the
idempotency wrapper (§4.8).

- **CreateProduct**
  1. Find the seller. Missing or soft-deleted → `seller not found`.
  2. Build the `Money`.
  3. `Product.create`, then the validated wrapper.
  4. `repository.create`.
  5. Return a result built from the validated entity.
- **UpdateProduct**
  1. Find the product. Missing → `product not found`.
  2. If the seller id changed, find that seller and `assignSeller`.
  3. `updateName`.
  4. Build the `Money`, then `updatePrice`.
  5. Validated wrapper, then `repository.update`.
  6. Return a result built from the validated entity.
- **DeleteProduct**: find the product (missing → `product not found`), delete it, return
  `success = true`.
- **Sellers**: create, update, and delete follow the same pattern.
- **Queries**: `findAll` returns a list. `findById` returns an empty result when the row is
  missing, and the controller turns that into 404.

The check order has visible effects, which the tests pin:

- Unknown seller + bad currency on create → 404, not 400.
- Empty name + bad currency on update → the name error.

### 4.8 Idempotency algorithm

This is Go's `withIdempotency`, with the same steps.

1. If the key is empty, execute directly.
2. The fingerprint is the command serialized to JSON (via the `ResultCodec` port), including the
   key itself.
3. Make up to 3 attempts. Each attempt:
   - Reserve the key (`INSERT … ON CONFLICT (key) DO NOTHING`). If reserved, stop looping.
   - Otherwise look the record up by key:
     - Missing → next attempt.
     - Stored fingerprint differs → **key reuse**.
     - Completed → decode the cached response and return it.
     - Younger than 1 minute → **in flight**.
     - Otherwise it's stale: delete it and try again.
4. Still not reserved after 3 attempts → **in flight**.
5. Execute the command. On failure, release the key in a way that can't be cancelled (see
   §5.4). If the release fails, log WARN `failed to release idempotency key` with
   `idempotency_key=… error=…`. Return the original error.
6. On success, store the response best-effort with status 200. A failure logs WARN
   `failed to marshal idempotency response` or `failed to persist idempotency response`.

Details:

- Replays go back through the controllers as if they were fresh: 201 for create, 200 for update,
  204 for delete. The body is identical to the original one.
- Errors from reserve or lookup propagate and become the endpoint's 500 fallback.

### 4.9 Persistence

- **Schema.** Flyway migrations `V1__initial_schema.sql`, `V2__price_as_money.sql`,
  `V3__outbox.sql`, and `V4__price_minor_units.sql` contain the SQL of today's `*.up.sql` files,
  unchanged.
- **Query semantics**, copied verbatim from `sql/queries/*.sql`:
  - Product reads join sellers and require `deleted_at IS NULL` on **both** tables. So
    soft-deleting a seller hides its products: GET gives 404, and update or delete gives 404
    `product not found`.
  - Lists use `ORDER BY created_at DESC`.
  - Updates use `WHERE id = :id AND deleted_at IS NULL`; 0 affected rows → not found.
  - Deletes are soft (`deleted_at = NOW()`).
  - Idempotency reserve: `INSERT … VALUES (…, '', 0, …) ON CONFLICT (key) DO NOTHING`. More than
    0 affected rows means reserved.
- **Product create** runs in one transaction: insert the product, insert outbox rows for the
  pulled events, read the product back. Any failure rolls everything back.
- **Seller create** inserts and then reads back, with no explicit transaction.
- **Seller update** with 0 affected rows → `seller not found`.
- **Row mapping** goes through the `Money` constructor, so an unsupported currency stored in the
  database surfaces as an error (500), as it does in Go.
- "Not found" from `findById` is `Optional.empty()`.

### 4.10 Outbox and relay

- **Row:** `id` = event id, `aggregate_id`, `event_name`, `payload` (JSONB), `occurred_at`,
  `published_at` (NULL).
- **Payload keys:** `eventId, aggregateId, occurredAt, name, priceMinorUnits, currency, sellerId`
  (§2.3 item 2).
- **Relay:**
  - Runs as a single instance: first run 5 s after start, then every 5 s at a fixed rate.
  - Selects unpublished rows `ORDER BY occurred_at LIMIT 100`.
  - For each row it publishes, then sets `published_at = NOW()`.
  - A publish or mark error stops the batch and logs ERROR `outbox relay batch failed`.
  - Delivery is at-least-once.
- **Publisher:** logs INFO `publishing domain event event_name=<name> payload=<json>` (§2.2
  item 4).

### 4.11 Health

- `/healthz` → 200 `{"status":"ok"}`, always.
- `/readyz` checks a database connection (`Connection.isValid`). Failure → 503 (body in §4.4);
  success → 200 `{"status":"ok"}`.

### 4.12 Configuration and lifecycle

- **`DATABASE_URL`** may be a libpq keyword DSN or a `postgres://` / `postgresql://` URL, as the
  README documents. The default is
  `host=localhost user=marketplace password=marketplace dbname=marketplace port=5432 sslmode=disable`.
  It's converted to JDBC settings (Appendix A.2).
- **`PORT`** defaults to `8080`.
- **An empty value counts as unset**, as in Go.
- **Startup.** Flyway migrates, then the app starts serving. An unreachable database fails
  startup with exit code 1.
- **Shutdown** is graceful, with a 10 s limit (Go's value; Spring's default is 30 s). The relay
  stops with the context.
- Spring's standard startup, shutdown, and failure log lines replace Go's custom ones (D13).

### 4.13 Logging

- Spring Boot's default console format.
- One line per request at INFO:
  `request method=GET uri=/api/v1/products status=200 latency=3.2ms request_id=…`.
- The app's other messages keep their Go text and attribute names, written as `key=value`
  (§4.8, §4.10).

### 4.14 Container and compose

- Image: Temurin 25 JRE, a non-root user, `EXPOSE 8080`, `ENTRYPOINT java -jar /app/marketplace.jar`.
- Compose:
  - `postgres:17-alpine`, with user, password, and database all `marketplace` and a
    `pg_isready` healthcheck.
  - `app`, which depends on Postgres being healthy, migrates on startup, and listens on `PORT`
    8080.
  - The one-shot `migrate` service is removed (§2.3 item 1).
- `.env.example` is unchanged.

---

## 5. Target architecture and Go → Java mapping

### 5.1 Package layout

`interface` is a Java keyword, so the interface layer becomes `interfaces`. Where Go's
subpackage split would force classes to be `public` only so a sibling package can use them, Java
merges those packages so the classes can stay package-private (D18).

```text
src/main/java/com/example/marketplace/
├── MarketplaceApplication.java        # @SpringBootApplication, @EnableScheduling
├── bootstrap/
│   └── ApplicationWiring.java         # @Bean ProductService / SellerService (application has no annotations)
├── domain/                            # pure Java: no Spring, Jackson, JDBC, or SLF4J
│   ├── entities/                      # Product, Seller, Money, Currency, ValidatedProduct,
│   │                                  # ValidatedSeller, IdempotencyRecord, Uuids,
│   │                                  # DomainException, ValidationException,
│   │                                  # ProductNotFoundException, SellerNotFoundException
│   ├── events/                        # DomainEvent, BaseEvent, ProductCreated
│   └── repositories/                  # ProductRepository, SellerRepository, IdempotencyRepository
├── application/                       # domain + SLF4J only
│   ├── command/                       # records; Create/Update/Delete × Product/Seller commands and results
│   ├── query/                         # records; GetAllProductsQueryResult, GetProductByIdQuery, …
│   ├── common/                        # records; ProductResult, SellerResult
│   ├── interfaces/                    # ProductService, SellerService
│   └── services/                      # public: ApplicationServices (factory), ResultCodec (port),
│                                      #   RequestInFlightException, IdempotencyKeyReuseException
│                                      # package-private: DefaultProductService, DefaultSellerService,
│                                      #   Idempotency, ProductResultMapper, SellerResultMapper
├── infrastructure/                    # NO public classes; Spring discovers them by component scan
│   ├── config/                        # DatabaseConfiguration, DatabaseUrl
│   ├── db/postgres/                   # JdbcProductRepository, JdbcSellerRepository,
│   │                                  # JdbcIdempotencyRepository, OutboxWriter, OutboxPayloads
│   ├── json/                          # JacksonResultCodec, MoneyJsonModule
│   └── outbox/                        # Publisher, LoggingPublisher, OutboxRelay
└── interfaces/api/rest/               # NO public classes
    ├── ProductController, SellerController, HealthController
    ├── RestErrors                     # service-error mapping + body-parse handler
    ├── IdempotencyKeys, CanonicalUuid
    ├── RequestIdFilter, RequestLogFilter, JsonStrictnessConfiguration
    └── request/response records and response mappers (merged from Go's dto/{request,response,mapper})

src/main/resources/
├── application.yaml
└── db/migration/V1__initial_schema.sql … V4__price_minor_units.sql
```

Each of the five layer packages (`bootstrap`, `domain`, `application`, `infrastructure`,
`interfaces`) has a `package-info.java` that declares it a Spring Modulith module (§5.2).

### 5.2 Access rules (D18)

- **Package-private and `final` by default.** A type is `public` only if another package must
  use it.
- **Public, and why:**
  - Domain entities, value objects, events, exceptions, and repository interfaces: every layer
    uses them.
  - Application commands, queries, results, and service interfaces: the interface layer uses
    them.
  - Application exceptions: the interface layer maps them.
  - `ResultCodec`: infrastructure implements it.
  - `ApplicationServices`: bootstrap uses it to build the services.
- **Infrastructure and interfaces have zero public classes.** Their Spring components
  (repositories, codec, relay, controllers, filters, configuration) are package-private and found
  by component scanning. That means no other package can import them, which is the compile-time
  equivalent of Go's "only `main.go` knows every layer".
- **Entities:**
  - Fields are `private`, with no setters. Constructors are private; public static factories
    (`create`) are the way in.
  - `validate()` and `copy()` are package-private.
  - The one deliberate public door is `reconstitute(…)`, which repositories need in order to
    load rows without validating (chapter 2 explains why).
- **Value carriers** (`Money`, `Currency`, events, commands, queries, results, DTOs) are records.
  Their canonical constructors are public by language rule; `Money` validates in its
  constructor, so there's no bypass.
- **Module rules** (§2.3 item 4). Access modifiers can't stop domain code from importing
  application's public records, or domain/application code from using Spring or Jackson.
  `ModuleStructureTest` covers both. Each layer's `package-info.java` declares:

  | Module | `type` | `allowedDependencies` |
  |---|---|---|
  | `domain` | `OPEN` | `{}` (none) |
  | `application` | `OPEN` | `domain` |
  | `infrastructure` | closed (default) | `domain`, `application` |
  | `interfaces` | closed | `domain`, `application` |
  | `bootstrap` | closed | `domain`, `application` |

  - Domain and application are `OPEN` because their public types live in subpackages
    (`domain.entities`, `application.command`, …). Modulith otherwise treats subpackages as
    private to their module.
  - Infrastructure and interfaces stay closed. Nothing depends on them anyway.
  - `bootstrap` needs only domain and application. Its `@Bean` methods take repository
    interfaces and `ResultCodec`, never an infrastructure class.
  - `MarketplaceApplication` sits in the root package, which belongs to no module.
  - The same test class holds an ArchUnit allowlist that mirrors the docs' "May import" column,
    ignoring `package-info`:
    - domain may use only `java..`, `com.fasterxml.uuid..` (the UUID generator), and its own
      packages;
    - application may use only `java..`, `org.slf4j..`, domain, and its own packages.

### 5.3 File mapping

| Go | Java |
|---|---|
| `cmd/marketplace/main.go` | `MarketplaceApplication`, `bootstrap/ApplicationWiring`, component scanning |
| `migrate.go` | Removed (Flyway, D7) |
| `migrations/00000N_*.up.sql` | `src/main/resources/db/migration/VN__*.sql` (same SQL) |
| `migrations/*.down.sql` | Removed (Flyway Community is forward-only) |
| `internal/domain/entities/errors.go` | `DomainException` (sealed) + three subclasses |
| `internal/domain/entities/money.go` | `Money`, `Currency` (records) |
| `internal/domain/entities/product.go`, `seller.go` | `Product`, `Seller` (final classes) |
| `internal/domain/entities/validated_*.go` | `ValidatedProduct`, `ValidatedSeller` |
| `internal/domain/entities/idempotency_record.go` | `IdempotencyRecord` |
| `internal/domain/events/*.go` | `DomainEvent`, `BaseEvent`, `ProductCreated` |
| `internal/domain/repositories/*.go` | Java interfaces of the same names |
| `internal/application/command`, `query`, `common` | Records in the same-named packages |
| `internal/application/interfaces/*.go` | `ProductService`, `SellerService` |
| `internal/application/mapper/*.go` | Package-private mappers inside `application/services` |
| `internal/application/services/idempotency.go` | `Idempotency` (package-private), `ResultCodec`, the two exceptions |
| `internal/application/services/*_service.go` | `DefaultProductService`, `DefaultSellerService` (package-private) + `ApplicationServices` |
| `internal/infrastructure/config/config.go` | `DatabaseConfiguration`, `DatabaseUrl`; `PORT` via `server.port: ${PORT:8080}` |
| `internal/infrastructure/db/postgres/connection.go` | `DatabaseConfiguration` (HikariCP DataSource) |
| `internal/infrastructure/db/postgres/sqlc_*_repository.go` | `Jdbc*Repository` |
| `internal/infrastructure/db/postgres/outbox.go` | `OutboxWriter`, `OutboxPayloads` |
| `internal/infrastructure/db/postgres/helpers.go` | Row-mapping helpers inside the repositories |
| `internal/infrastructure/db/sqlc/*` (generated), `sql/queries/*.sql`, `sqlc.yaml` | Removed; the SQL moves into text blocks |
| `internal/infrastructure/outbox/relay.go` | `Publisher`, `LoggingPublisher`, `OutboxRelay` |
| `internal/interface/api/rest/*_controller.go` | `ProductController`, `SellerController`, `HealthController` |
| `internal/interface/api/rest/errors.go` | `RestErrors` |
| `internal/interface/api/rest/idempotency.go` | `IdempotencyKeys` |
| `internal/interface/api/rest/dto/**` | Package-private records and mappers in `interfaces/api/rest` |
| `internal/testhelpers/*` | `src/test/java/…/testhelpers/PostgresTestContainer` (applies Flyway) |
| `api/openapi.yaml` | Unchanged apart from title, description, and license URL |

### 5.4 Idiom mapping

This table is also the translation guide for the docs rewrite.

| Go idiom | Java equivalent in this port |
|---|---|
| Struct with exported fields + `NewX` | Final class, private fields, static `create(…)`; `reconstitute(…)` for loading |
| `(T, error)` + sentinel errors + `errors.Is` | Unchecked exception hierarchy, caught by type |
| `fmt.Errorf("%w: detail", ErrValidation)` | `new ValidationException("detail")`, whose message is `validation failed: detail` |
| `(nil, nil)` for "not found" | `Optional.empty()` |
| `json:"minor_units"` on the domain `Money` | Jackson module in infrastructure; the domain stays annotation-free |
| `withIdempotency[T]` (generics) | `<T> T withIdempotency(…, Class<T> type, Supplier<T> action)`; `Class<T>` is needed because of type erasure |
| `context.WithoutCancel` for cleanup | Cleanup must not share the failed work's fate. The release clears the thread's interrupt flag for its database call and restores it afterwards. Services aren't `@Transactional`, so the release is its own statement; the docs explain `REQUIRES_NEW` for readers whose services are transactional |
| Goroutine + `time.Ticker` | `@Scheduled(initialDelay = 5, fixedRate = 5, timeUnit = SECONDS)` |
| `slog.Info("msg", slog.String("k", v))` | `log.info("msg k={}", v)` |
| Echo middleware | Servlet filters (`OncePerRequestFilter`) |
| `pgx.Tx` + `defer tx.Rollback()` | `transactionTemplate.execute(status -> …)` (rolls back on exception) |
| sqlc `:execrows` | `jdbcClient.sql(…).update()` returns the row count |
| `internal/` + import rules | Package-private types + component scanning (§5.2) |
| golang-migrate up/down pairs | Flyway forward-only `V<N>__name.sql` |
| testify `assert` / `require` | AssertJ |
| testify `mock` | Mockito (`@MockitoBean`) |
| Table-driven tests, `t.Run` | `@ParameterizedTest` + `@MethodSource`, `@Nested` |
| `go test -race` | No equivalent. Concurrency tests use a `CountDownLatch` start gate plus `@RepeatedTest` |
| `Id` not `ID` (golangci rule) | Java camelCase (`sellerId`, `getId()`), so the rule becomes a non-issue |

---

## 6. Execution phases

Each layer's tests are ported in the same phase as that layer, and each phase ends with a green
build. I only commit when you ask me to.

### Phase 0 — Baseline and contract tests

- [ ] 0.1 Create the `java-migration` branch.
- [ ] 0.2 Record the Go baseline: `make test` and `docker compose up --build` are green.
- [ ] 0.3 Add the Maven Wrapper and `.mvn/maven.config` (`--strict-checksums`), as described in
  §3.
- [ ] 0.4 Create `contract-tests/` as a **standalone** Maven project (JUnit, AssertJ, the JDK
  `HttpClient`, PostgreSQL JDBC; no Spring). It talks to `CONTRACT_BASE_URL` and checks database
  state through `CONTRACT_DATABASE_URL`.
- [ ] 0.5 Write the scenarios:
  - **A. Happy paths:** all 12 routes, response fields, list ordering.
  - **B. Request errors:** malformed JSON, missing body, wrong types, float or string price,
    non-JSON content type (415), unknown fields ignored.
  - **C. Ids:** canonical ids accepted in either case; invalid ids get the controller messages.
  - **D. Error table:** every row of §4.4, plus the check-order cases in §4.7.
  - **E. Idempotency:**
    - header vs body precedence;
    - replays return an identical body and status (201/200/204);
    - a different payload under the same key → 422, including a key reused across operations;
    - 20 concurrent creates with one key → exactly one product;
    - a failed command releases its key, so a retry succeeds;
    - a stale reservation inserted directly into the database is taken over.
  - **F. Soft deletes:** deleting a seller hides its products; double delete → 404; updating a
    deleted row → 404.
  - **G. Health and database outage:** `/readyz` 200 and 503 (with `docker compose stop
    postgres`); the controller 500 fallbacks while the database is down.
  - **H. `X-Request-Id`:** echoed, or generated with the 32-alphanumeric format.
  - **I. Outbox:** after a create, the row exists with the §4.10 payload keys, and `published_at`
    is set within 11 s.
- [ ] 0.6 Tag every test whose expectation intentionally differs from Go (the §2.2 fixes and the
  §9 items) with `@Tag("changed-from-go")`, each with a one-line reason.
- [ ] 0.7 Add `make contract`. Run it against the Go compose stack: untagged tests must pass, and
  tagged tests must fail for their stated reason. Anything else means I misread the Go behavior,
  and the plan gets corrected before Java work starts.

**Exit criteria:** the contract suite is validated against Go.

### Phase 1 — Project scaffold

- [ ] 1.1 `pom.xml`:
  - Parent `spring-boot-starter-parent` 4.1.1; `java.version` 25.
  - Dependencies: `spring-boot-starter-webmvc`, `spring-boot-starter-jdbc`,
    `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`,
    `org.postgresql:postgresql`, `com.fasterxml.uuid:java-uuid-generator`.
  - Test dependencies: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`,
    `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`.
  - Spring Modulith:
    - import `org.springframework.modulith:spring-modulith-bom` in `<dependencyManagement>`,
      using the 2.x line that supports Boot 4.1 (exact version checked in JFrog in 1.5);
    - `spring-modulith-api` (compile scope; annotations only);
    - `spring-modulith-starter-test` (test);
    - `com.tngtech.archunit:archunit` (test), declared explicitly because the test uses its API,
      at the version Modulith uses.
- [ ] 1.2 Plugins:
  - surefire for unit tests (`*Test`) and failsafe for integration tests (`*IT`);
  - JaCoCo;
  - `spring-boot-maven-plugin` (produces `target/marketplace.jar`);
  - `flyway-maven-plugin` (for `make migrate-up` / `migrate-info`);
  - `maven-enforcer-plugin` with `requireJavaVersion [25,)`, `requireMavenVersion [3.9,)`, and
    `requireNoRepositories`.
- [ ] 1.3 `application.yaml`:
  - `server.port: ${PORT:8080}`;
  - `spring.lifecycle.timeout-per-shutdown-phase: 10s`;
  - banner off (Go explicitly hides Echo's banner).
- [ ] 1.4 `.gitignore`: add `target/`. The Go entries stay until Phase 10.
- [ ] 1.5 Confirm every dependency resolves from JFrog with strict checksums. If anything is
  missing from the mirror, stop and ask you.
- [ ] 1.6 Create the five layer packages, each with its `package-info.java` (§5.2), and add
  `ModuleStructureTest`. From here on, every phase is checked against the layer rules; the test
  is a plain unit test, so it also runs in `make test-unit`.

**Exit criteria:** `./mvnw verify` is green on an empty application.

### Phase 2 — Domain layer (pure Java)

- [ ] 2.1 `DomainException` (sealed, extends `RuntimeException`):
  - `ValidationException(detail)` → message `validation failed: <detail>`;
  - `ProductNotFoundException` → `product not found`;
  - `SellerNotFoundException` → `seller not found`.
- [ ] 2.2 `Currency` record: accepts any string (the currency is checked when a `Money` is
  built); constants `EUR`, `USD`; `toString()` returns the code.
- [ ] 2.3 `Money` record `(long minorUnits, Currency currency)`:
  - The compact constructor validates in Go's order; a `null` currency is treated as `""`.
  - A private exponent map.
  - `toString()` is exponent-aware; a package-private static `format(long, int, String)` lets the
    tests cover JPY and BHD. (Go's test mutates the supported-currency map; Java doesn't expose
    one.)
  - Go's `IsZero()` has no counterpart: Java has no zero-value `Money`, so a missing price is
    `null`. The Go test is replaced by one asserting that a `Money` can't be built with a bad
    currency.
- [ ] 2.4 `Uuids`: `newV7()` (wrapping JUG) and `NIL`.
- [ ] 2.5 `Product`:
  - private constructor; `create(name, price, ValidatedSeller)`;
    `reconstitute(id, createdAt, updatedAt, name, price, sellerId)`;
  - getters;
  - `updateName`, `updatePrice`, `assignSeller` (mutate → touch → validate);
  - `pullEvents()`;
  - package-private `validate()` (treats `null` as Go's zero value) and `copy()`.
- [ ] 2.6 `Seller`: `create(name)`, `reconstitute(…)`, `updateName`, package-private `validate()`
  and `copy()`.
- [ ] 2.7 `ValidatedProduct` / `ValidatedSeller`: final class, private constructor, static
  `of(entity)` that copies and validates; `product()` / `seller()` accessors; `isValid()`.
- [ ] 2.8 `IdempotencyRecord`: `create(key, request)`, `reconstitute(…)`,
  `setResponse(response, statusCode)`, `isCompleted()`.
- [ ] 2.9 Events:
  - `DomainEvent` (`eventId()`, `eventName()`, `occurredAt()`, `aggregateId()`);
  - `BaseEvent` record `(eventId, aggregateId, occurredAt)` with `forAggregate(UUID)`;
  - `ProductCreated` record composing a `BaseEvent`, with `NAME = "product.created"`.
- [ ] 2.10 Repository interfaces:
  - `ProductRepository`: `Product create(ValidatedProduct)`, `Optional<Product> findById(UUID)`,
    `List<Product> findAll()`, `Product update(ValidatedProduct)`, `void delete(UUID)`.
  - `SellerRepository`: the same shape.
  - `IdempotencyRepository`: `boolean reserve(IdempotencyRecord)`,
    `Optional<IdempotencyRecord> findByKey(String)`, `void setResponse(String, String, int)`,
    `void delete(String)`.
- [ ] 2.11 Port the domain tests (39 functions; §7).

**Exit criteria:** domain tests are green.

### Phase 3 — Application layer

- [ ] 3.1 Command, query, and result records mirroring the Go structs. `CreateProductCommand`
  keeps Go's always-nil `id` component, so the fingerprint content matches the Go command.
- [ ] 3.2 `ResultCodec` port: `String encode(Object)` and `<T> T decode(String, Class<T>)`. The
  application layer never touches Jackson.
- [ ] 3.3 `Idempotency.withIdempotency(repo, codec, key, cmd, Class<T>, Supplier<T>)`:
  - implements §4.8, with `RESERVATION_TTL = Duration.ofMinutes(1)`;
  - the interrupt-safe release (§5.4).
- [ ] 3.4 `RequestInFlightException` and `IdempotencyKeyReuseException`, with the exact messages.
- [ ] 3.5 `DefaultProductService` / `DefaultSellerService` (package-private, no annotations,
  check order as in §4.7), plus `ApplicationServices`, which has static factories returning the
  service interfaces.
- [ ] 3.6 Result mappers (package-private): `fromEntity(null)` returns `null`, as the Go tests
  pin.
- [ ] 3.7 Test doubles (D19):
  - `FakeProductRepository`, `FakeSellerRepository`;
  - `FakeIdempotencyRepository` (synchronized; counts `reserveCalls` / `deleteCalls` /
    `deletedKeys`; injectable errors);
  - `RaceLosingIdempotencyRepository`;
  - `TestJsonCodec`, a Jackson-backed codec in test scope.
- [ ] 3.8 Port the application tests (45 functions). The concurrency test uses 8 threads, a
  `CountDownLatch` start gate, and `@RepeatedTest(20)`, and asserts the action ran exactly once.

**Exit criteria:** application tests are green.

### Phase 4 — Infrastructure layer

- [ ] 4.1 **Config**
  - `DatabaseUrl.parse(String)` returns the JDBC URL, user, password, and properties
    (Appendix A.2).
  - `DatabaseConfiguration` builds the HikariCP `DataSource` from `DATABASE_URL` (empty = unset).
    Flyway, `JdbcClient`, and `TransactionTemplate` come from Spring Boot's auto-configuration on
    top of it.
- [ ] 4.2 **Repositories**
  - `JdbcClient` with SQL text blocks copied verbatim from `sql/queries/*.sql` (sqlc's `$n`
    become named `:param`).
  - Row mappers go through `new Money(…)` and `reconstitute(…)`.
  - `.optional()` for single-row reads; `update()` row counts become not-found exceptions.
  - The product create transaction via `TransactionTemplate`.
- [ ] 4.3 **Outbox writer**
  - `OutboxWriter.insert(…)` runs inside the create transaction.
  - `OutboxPayloads.toJson(event)` writes the §4.10 key names, bound as `jsonb`.
- [ ] 4.4 **Relay**
  - `OutboxRelay` uses `@Scheduled(initialDelay = 5, fixedRate = 5, timeUnit = SECONDS)`, batch
    100, and stops the batch on the first error.
  - `LoggingPublisher` logs `publishing domain event event_name=… payload=…`.
  - Error line: `outbox relay batch failed error=…`.
- [ ] 4.5 **JSON codec**
  - `JacksonResultCodec implements ResultCodec`, using Spring's `JsonMapper`.
  - `MoneyJsonModule` gives `Money` the documented `{"minor_units","currency"}` form and decodes
    through the constructor.
- [ ] 4.6 **Migrations**
  - Copy each `*.up.sql` into `src/main/resources/db/migration/VN__name.sql` unchanged.
  - Verify that a fresh database migrated by Flyway has the same schema as one migrated by Go
    (compare `pg_dump --schema-only` output, ignoring the two history tables).
- [ ] 4.7 **Test infrastructure**
  - `PostgresTestContainer` (`postgres:17-alpine`) migrates with the Flyway API and provides
    `truncateTables()`.
- [ ] 4.8 **Port the integration tests** (49 functions, including the test-helper tests).
  - The outbox-migration test migrates to V3 (Flyway `target`), seeds a legacy `PriceCents`
    payload, migrates to V4, and asserts the rewrite.
  - Add the outbox-rollback test (§2.2 item 5).
  - Add `DatabaseUrlTest`.

**Exit criteria:** all infrastructure tests are green on Testcontainers, and the schema matches
the Go-migrated one.

### Phase 5 — Interface layer (REST)

- [ ] 5.1 Request and response records with `@JsonProperty` names mirroring the Go tags.
  `UpdateSellerRequest.id` is a `UUID`.
- [ ] 5.2 Controllers (package-private `@RestController`):
  - the §4.1 mappings, with `consumes = APPLICATION_JSON_VALUE` on POST and PUT;
  - path ids taken as `String` and parsed with `CanonicalUuid`, so each controller returns its
    own message;
  - checks in Go's order;
  - `null` seller `id` → 400 (§2.2 item 2);
  - empty seller list → `[]` (§2.2 item 1).
- [ ] 5.3 `RestErrors`:
  - the service-error mapping from §4.5;
  - an `@ExceptionHandler` that turns `HttpMessageNotReadableException` into 400
    `{"error":"Failed to parse request body"}`.
- [ ] 5.4 `IdempotencyKeys.resolve(request, bodyKey)`: the header wins.
- [ ] 5.5 `HealthController`: `/healthz`, `/readyz`.
- [ ] 5.6 `RequestIdFilter` and `RequestLogFilter` (§4.3, §4.13).
- [ ] 5.7 `JsonStrictnessConfiguration`: the Jackson changes from §3 (floats and numeric strings
  rejected for integer fields; declaration order kept).
- [ ] 5.8 Port the controller, DTO, mapper, and idempotency-header tests (32 functions) with
  MockMvc. testify/mock becomes Mockito; hand-written fakes stay hand-written. Add tests for
  `CanonicalUuid`, `RestErrors` (parse errors, 415), and both filters.

**Exit criteria:** interface tests are green.

### Phase 6 — Bootstrap and runtime

- [ ] 6.1 `MarketplaceApplication` (`@SpringBootApplication`, `@EnableScheduling`) and
  `ApplicationWiring`, which declares `@Bean` `ProductService` and `SellerService` built with
  `ApplicationServices`.
- [ ] 6.2 `ApplicationStartupIT` covers:
  - Flyway migrates an empty database on startup;
  - an unreachable database gives a non-zero exit;
  - `PORT` overrides the port, and an empty `PORT` falls back to 8080;
  - graceful shutdown completes within 10 s.

**Exit criteria:** the contract suite (Phase 0) passes against the Java app started locally.

### Phase 7 — Ops tooling

- [ ] 7.1 **Makefile.** Target names are kept wherever they still make sense:

  | Target | Java |
  |---|---|
  | `help` | Unchanged |
  | `build` | `./mvnw -B package -DskipTests` → `target/marketplace.jar` |
  | `run` | `./mvnw spring-boot:run` (uses `DATABASE_URL` / `PORT`) |
  | `test` | `./mvnw -B verify` (unit + Testcontainers) |
  | `test-unit` | `./mvnw -B test` (no Docker) |
  | `cover` | `./mvnw -B verify`, then print the JaCoCo summary |
  | `migrate-up` | `./mvnw flyway:migrate` against `DB_JDBC_URL` / `DB_USER` / `DB_PASSWORD` |
  | `migrate-info` | New: `./mvnw flyway:info` |
  | `docker-up`, `docker-down` | Unchanged |
  | `contract` | New: runs the contract suite against `CONTRACT_BASE_URL` |
  | `lint`, `fmt`, `vulncheck`, `tidy`, `vendor`, `sqlc`, `migrate-down` | Removed (D14, D15, D7) |

- [ ] 7.2 **Dockerfile** (D16, D17):
  - Build stage: `maven:3.9-eclipse-temurin-25`, running `mvn -B --strict-checksums package
    -DskipTests`, with your `~/.m2/settings.xml` mounted as a BuildKit secret and a cache mount
    for `~/.m2/repository`.
  - Runtime stage: `eclipse-temurin:25-jre`, creates a system user `app`, `USER app`,
    `WORKDIR /app`, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/marketplace.jar"]`.
  - Both images pinned by digest.
- [ ] 7.3 **docker-compose.yml**:
  - Remove the `migrate` service.
  - `app` depends on `postgres: service_healthy`.
  - The build secret `maven_settings` comes from `${HOME}/.m2/settings.xml`.

**Exit criteria:** every `make` target works, and `docker compose up --build` serves traffic.

### Phase 8 — Verification

- [ ] 8.1 Run the contract suite against the Java compose stack: 100% green, including the
  `changed-from-go` tests.
- [ ] 8.2 `make test`: all ported and new tests green; test counts reconciled against §7.
- [ ] 8.3 Compose smoke test following the README's "Try the API in 30 seconds" steps.
- [ ] 8.4 Review §9 with you.

### Phase 9 — Documentation and tutorial rewrite

- [ ] 9.1 Rewrite every file in [§8](#8-documentation-and-tutorial-rewrite-inventory). Snippets
  are **copied from the real Java sources**, not written freehand.
- [ ] 9.2 Run every "Try it" exercise once against the Java app and adjust the instructions to
  match.
- [ ] 9.3 Check every snippet that claims an output (e.g. the float-drift loop in chapter 3) by
  running it with `jshell`.
- [ ] 9.4 `mkdocs build --strict` passes (mkdocs-material installed in a venv from the approved
  PyPI source). All GitHub links point at Java paths (D5).

### Phase 10 — Cutover (remove Go)

- [ ] 10.1 Delete:
  - `cmd/`, `internal/`, `migrate.go`, `go.mod`, `go.sum`, `vendor/`;
  - `sqlc.yaml`, `sql/`, and `migrations/` (now under `src/main/resources/db/migration`);
  - `.golangci.yml`, `README.zh-CN.md`;
  - the Go entries in `.gitignore`.

  CI files (`.github/workflows/`, `.github/dependabot.yml`, `codecov.yml`) are left untouched.
- [ ] 10.2 Keep `contract-tests/` as a black-box regression suite.
- [ ] 10.3 Search the repo (excluding the CI files) for leftover Go references (`\.go\b`,
  `go run`, `golang`, `sqlc`, `pgx`, `Echo`, `goroutine`, `go-ddd`, `vulncheck`, `golangci`) and
  resolve each one.
- [ ] 10.4 Final full run: `make test`, `make contract`, `docker compose up --build`,
  `mkdocs build --strict`.

---

## 7. Test porting inventory

Each Go test function becomes a Java test method named after it (e.g.
`TestMoney_String_ExponentAware` → `string_exponentAware`). Subtests become
`@ParameterizedTest` or `@Nested`.

| Go test file | Funcs | Java test class | Notes |
|---|---:|---|---|
| `domain/entities/money_test.go` | 9 | `MoneyTest` | JPY/BHD via the package-private `format`; the `IsZero` test is replaced (Phase 2.3) |
| `domain/entities/product_test.go` | 1 | `ProductTest` | Go zero-value literals → `reconstitute` with nulls |
| `domain/entities/product_business_logic_test.go` | 7 | `ProductBusinessLogicTest` | Pins mutate-then-validate |
| `domain/entities/seller_test.go` | 1 | `SellerTest` | |
| `domain/entities/seller_business_logic_test.go` | 6 | `SellerBusinessLogicTest` | Whitespace-only names are valid |
| `domain/entities/validated_product_test.go` | 2 | `ValidatedProductTest` | |
| `domain/entities/validated_seller_test.go` | 2 | `ValidatedSellerTest` | |
| `domain/entities/idempotency_record_test.go` | 9 | `IdempotencyRecordTest` | |
| `domain/events/events_test.go` | 2 | `DomainEventsTest` | |
| `application/services/idempotency_test.go` | 12 | `IdempotencyTest` | Threads + latch instead of goroutines |
| `application/services/failure_paths_test.go` | 20 | `FailurePathsTest` | |
| `application/services/product_service_test.go` | 3 | `ProductServiceTest` | |
| `application/services/seller_service_test.go` | 4 | `SellerServiceTest` | |
| `application/mapper/result_test.go` | 6 | `ResultMapperTest` | Lives in `application/services` |
| `infrastructure/db/postgres/sqlc_product_repository_test.go` | 10 | `JdbcProductRepositoryIT` | Plus the outbox-rollback test |
| `infrastructure/db/postgres/sqlc_seller_repository_test.go` | 12 | `JdbcSellerRepositoryIT` | |
| `infrastructure/db/postgres/sqlc_idempotency_repository_test.go` | 11 | `JdbcIdempotencyRepositoryIT` | |
| `infrastructure/db/postgres/outbox_test.go` | 1 | `OutboxIT` | Payload keys `name`, `priceMinorUnits`, `currency` (§2.3 item 2) |
| `infrastructure/db/postgres/outbox_migration_test.go` | 1 | `OutboxMigrationIT` | Flyway `target` V3 → seed → V4 |
| `infrastructure/db/postgres/connection_test.go` | 5 | `DatabaseConfigurationIT` | Valid and invalid `DATABASE_URL`, unreachable database |
| `infrastructure/db/postgres/helpers_test.go` | 5 | `RowMappingTest` | pgtype helpers become JDBC mapping helpers |
| `testhelpers/postgres_test_container_test.go` | 4 | `PostgresTestContainerIT` | |
| `interface/api/rest_test/product_controller_test.go` | 3 | `ProductControllerTest` | MockMvc + Mockito |
| `interface/api/rest_test/product_controller_extra_test.go` | 6 | `ProductControllerExtraTest` | |
| `interface/api/rest_test/seller_controller_test.go` | 5 | `SellerControllerTest` | Includes the `null` id → 400 change |
| `interface/api/rest/idempotency_test.go` | 3 | `IdempotencyKeysTest` | |
| `interface/api/rest/dto/request/request_test.go` | 8 | `RequestDtoTest` | |
| `interface/api/rest/dto/mapper/response_mapper_test.go` | 7 | `ResponseMapperTest` | Empty sellers list is now `[]` (§2.2 item 1) |
| **Total** | **165** | | |

New tests with no Go counterpart:

- `ModuleStructureTest` (Spring Modulith `verify()` + the ArchUnit allowlist, §5.2)
- `DatabaseUrlTest`, `CanonicalUuidTest`, `RestErrorsTest`
- `RequestIdFilterTest`, `RequestLogFilterTest`
- `ApplicationStartupIT`
- The outbox-rollback test
- The contract suite

---

## 8. Documentation and tutorial rewrite inventory

General rules:

- Keep each chapter's structure, teaching point, and "Try it" section.
- Replace each Go code block with the Java code it now describes.
- Rewrite prose only where it's Go-specific.
- Update every `github.com/sklinkert/go-ddd/blob/main/internal/...*.go` link to the matching
  `src/main/java/...*.java` path (D5).

### Root and contributor files

| File | Changes |
|---|---|
| `README.md` | Remove the link to `README.zh-CN.md`. New title and intro. Badges: drop Go Reference / Go Version, add a Java badge (the CI and codecov badges are left alone). Features and tech stack (§3). Project structure (§5.1). Mermaid diagram labels (`NewProduct` → `Product.create`). Rewrite "Database Migrations" for Flyway: file location and naming; applied automatically at startup; `make migrate-up` / `migrate-info`; forward-only, so to undo something you write a new migration; the best-practices list changes from "always create both up and down" accordingly; and a note for databases created by the Go version: `spring.flyway.baseline-on-migrate=true` with `baseline-version=4`. Getting Started: Java 25+, Docker, and the Docker build reading `~/.m2/settings.xml` (D16). Local development without sqlc. The make targets list (§6, 7.1). The curl examples stay as they are |
| `README.zh-CN.md` | Deleted (D20) |
| `DDD_CQRS_PRINCIPLES.md` | Go terminology → Java; site links (D5); the go-outbox reference (D21) |
| `CONTRIBUTING.md` | Prerequisites (Java 25, Docker); `make test`. Drop `make lint`, `make fmt`, "`Id` instead of `ID` (see .golangci.yml)", and "run sqlc generate" |
| `SECURITY.md` | Repo links; remove govulncheck / `make vulncheck` references |
| `.github/ISSUE_TEMPLATE/bug_report.yml` | "Go version" → "Java version" (placeholder `25.0.2`) |
| `.github/ISSUE_TEMPLATE/pattern_question.yml` | Links and wording |
| `.github/pull_request_template.md` | The checklist becomes `make test`; drop lint, vulncheck, and "regenerated sqlc code" |
| `mkdocs.yml` | `site_name`, description, `repo_url`, `site_url`, `edit_uri` (D5) |
| `api/openapi.yaml` | `title`, description, license URL |

### `docs/index.md`

- "writing Go backends for over a decade" → a Java framing (D5 voice).
- The anemic-model example: Go "structs are bags of public fields, `product.Price = -5`" → a Java
  class with public setters (a Lombok `@Data` entity is the familiar Java version).
- Stack sentence: Echo / pgx / sqlc → Spring MVC / JDBC / `JdbcClient` / Flyway.
- Chapter blurbs: "`float64` money" → "`double` money"; "sqlc in the infrastructure" →
  "`JdbcClient` in the infrastructure".
- `git clone` URL (D5).

### `docs/tutorial/01-the-domain.md`

- "Before a single line of Go" → Java; "structs" → classes.
- The package-tree block → §5.1.
- The import-block observation → "`java.*`, the UUID generator, events; no Spring, no JDBC, no
  Jackson annotations", plus a pointer to `ModuleStructureTest`, which fails the build if that
  stops being true.
- The "moved this template from GORM to sqlc" history → the Go original swapped its persistence
  layer without touching a domain rule, and this port changed languages the same way.
- Naming bullets: `NewProduct(name string, price Money, seller ValidatedSeller)` →
  `Product.create(String name, Money price, ValidatedSeller seller)`; `SellerId uuid.UUID` →
  `UUID sellerId`; "`Money`, not `float64`" → "`Money`, not `double`".
- "Try it": paths and commands.

### `docs/tutorial/02-entities.md`

- Anemic struct → anemic Java class.
- `NewProduct` → `Product.create`.
- `ValidatedProduct`: Go embedding + unexported flag → final class, private constructor, `of()`
  that validates a copy. Point out that Java's access control makes this *stronger* than the Go
  version.
- `validate()` → Java, throwing `ValidationException`.
- Repository interface → Java.
- "Every check wraps `ErrValidation` … `errors.Is`" → "every check throws a
  `ValidationException`, which the REST layer maps by type".
- `UpdatePrice` → `updatePrice`.
- The "Is this bulletproof? No — fields are exported, Go has no access control" paragraph →
  rewrite: Java fields are private, and the one deliberate hole is the public `reconstitute`
  factory repositories need. Same lesson, different hole.
- "Try it": `./mvnw test`; passing a plain `Product` to `create` is still a compile error.

### `docs/tutorial/03-value-objects.md`

- Float-drift snippet → a Java `double` loop (output checked in Phase 9.3).
- `Money` → the Java record with its compact constructor.
- Design-decision prose:
  - "unexported fields, one constructor" → "the canonical constructor is the only way in,
    including for Jackson";
  - "comparable struct, `==`" → record `equals()`, plus a warning that `==` compares references;
  - "not `big.Rat` or a decimal library" → "not `BigDecimal`", with the Java-specific pitfall
    `new BigDecimal("1.0").equals(new BigDecimal("1.00")) == false`.
- `Add` → `Money add(Money other)`.
- JSON section: Go `UnmarshalJSON` → `MoneyJsonModule` in infrastructure, which decodes through
  the constructor and keeps the domain annotation-free.
- `String()` → `toString()`.

### `docs/tutorial/04-aggregates.md`

- Product struct → Java class fields (`UUID sellerId`, a reference by id).
- Embedded-entity counterexample → a JPA `@ManyToOne(cascade = ALL)` analogy.
- `NewProduct` signature → Java.
- "sqlc-generated SQL" → "hand-written SQL via `JdbcClient`".
- "Try it" paths.

### `docs/tutorial/05-repositories.md`

- Interface → Java; "no `*sql.DB`, no pgx types" → "no `DataSource`, no `JdbcClient`, no
  `ResultSet`".
- "sqlc, not an ORM" → "plain SQL with `JdbcClient`, not JPA".
  - Keep the SQL block.
  - Be honest about the trade-off: queries are checked by integration tests against real
    Postgres, not at generation time.
- `productFromRow` → a `RowMapper` that calls `new Money(…)` and `Product.reconstitute(…)`.
- Errors: `pgx.ErrNoRows → nil, nil` → `.optional()` / `Optional.empty()`;
  `rows == 0 → ErrProductNotFound` → throw.
- Transaction snippet → `transactionTemplate.execute(…)`; "hand every service a transaction
  handle" → "`@Transactional` on every service method".
- "Try it": `map` + mutex → `ConcurrentHashMap`; adding `findBySellerId` loses the "generated
  function" step.

### `docs/tutorial/06-cqrs.md`

- Command struct → record.
- Service method → Java, using `withIdempotency(…, CreateProductCommandResult.class, () -> …)`.
- Query and result types → records.
- "sqlc makes this pleasant" → `JdbcClient`.
- "Try it": the controller path; "swap Echo for chi" → "swap Spring MVC for another web layer".

### `docs/tutorial/07-domain-events-outbox.md`

- Dual-write example → Java with `kafkaTemplate.send(…)`.
- `NewProduct` / `PullEvents` → Java.
- `DomainEvent` + `ProductCreated` → Java.
- Outbox table: link to `V3__outbox.sql`; the SQL is unchanged.
- "one pgx transaction" → one JDBC transaction (`TransactionTemplate`).
- The relay → the `@Scheduled` relay; "logs via slog" → SLF4J.
- The go-outbox tip (D21).
- "Try it": the `event_name=product.created` line is now accurate (§2.2 item 4).

### `docs/tutorial/08-idempotency.md`

- The naive version → Java.
- Reserve SQL unchanged; "sqlc `:execrows`" → "`JdbcClient.update()` returns the affected-row
  count".
- The loop → Java.
- Generics → Java generics, plus why `Class<T>` is needed (type erasure).
- **Fix three (`context.WithoutCancel`)** → the Java version of the same trap:
  - cleanup must not share the failed work's fate;
  - the interrupt-safe release;
  - if your services are `@Transactional`, the release needs `REQUIRES_NEW`, or it rolls back
    with the failure.
- The `slog.WarnContext` snippet → SLF4J.
- Concurrency test → threads + latch; "run it with `-race`" → Java has no race detector, so the
  test uses a start gate and repetition.
- The curl loop is unchanged.
- "Try it": `RESERVATION_TTL`; `RestErrors.java`.

### `docs/tutorial/09-testing.md`

- Table: "pure Go" → "plain JUnit".
- Domain test → JUnit + AssertJ.
- "sentinel errors" → exception types.
- Fake repository → Java.
- "Eight goroutines" → eight threads.
- `SetupTestDB` → Testcontainers Java + Flyway.
- The outbox-rollback claim is now backed by a real test.
- "Generated code is excluded from coverage" → the bootstrap is excluded.
- "Try it" commands.

### `docs/reference/architecture.md`

- Package tree and layer table (§5.1, §5.2), including the access rules and the
  "no public classes in outer layers" convention.
- The layer table is now enforced, not just reviewed:
  - show a `package-info.java` with its `@ApplicationModule(allowedDependencies = …)`;
  - explain `ModuleStructureTest` (Modulith `verify()` + the ArchUnit allowlist);
  - reword the intro's "enforcing conventions in review" to match.
- Wiring: `main.go` → component scanning + `ApplicationWiring`.
- Controllers → Spring MVC; `errors.go` → `RestErrors`.
- Conventions:
  - `NewX` → static factories;
  - sentinel errors → exception hierarchy, matched by type;
  - "Id not ID / .golangci.yml" → camelCase;
  - "Migrations are append-only … up/down pairs; sqlc regenerates …" → Flyway forward-only
    migrations.
- Tooling map: drop the lint and vulnerability rows; migrations → Flyway; logging →
  SLF4J/Logback; add "Layer rules | Spring Modulith + ArchUnit | `ModuleStructureTest`".

### `docs/reference/faq.md`

- Ports/adapters paths.
- The `context.WithoutCancel` mention → interrupt-safe cleanup.
- "Why sqlc instead of GORM or ent?" → "Why `JdbcClient` instead of JPA/Hibernate or Spring
  Data?" (same reasoning).
- "Why do entities have exported fields?" → "Why is there a public `reconstitute` factory?"
- "A domain service … plain function in the domain package" → "a plain class, no Spring
  annotation".
- "Add an aggregate" checklist:
  - `V<N>__…sql` replaces the migration pair and `make sqlc`;
  - `JdbcXRepository`;
  - controller + `ApplicationWiring`.
- "Why `Id` and not `ID`?" → shortened for Java camelCase.
- Issue links (D5).

---

## 9. Go behaviors intentionally not carried over

| Go behavior | Java behavior | Reason |
|---|---|---|
| Echo's `{"message":"Not Found"}` / `{"message":"Method Not Allowed"}` / `{"message":"Internal Server Error"}` | Spring Boot's standard error JSON | D10 |
| OPTIONS → 204; HEAD → 405; `Allow` in Echo's order | Spring's standard OPTIONS (200 + `Allow`) and HEAD (served like GET, no body) | D10 |
| `?pretty`, a trailing newline after JSON, HTML-escaped `<>&` | Not emulated | D10 |
| An empty body binds as all-zero fields (so e.g. an empty create → 400 `Invalid product Id format`) | 400 `Failed to parse request body` | D10 |
| JSON keys matched case-insensitively; trailing content after the JSON value ignored; `null` means zero value | Jackson defaults: case-sensitive keys, trailing content rejected (400), `null` for a number rejected (400) | D10 |
| Non-JSON content types → 400; XML and form bodies partially bound | 415 | D12 |
| UUIDs in `{…}`, `urn:uuid:…`, or 32-hex form accepted | Canonical form only | §2.3 item 3 |
| Timestamps in the server's local zone, with nanosecond precision in create/update responses | UTC, microsecond precision | JVM clock resolution; UTC matches the README examples |
| `slog` JSON log lines; Go's custom startup/shutdown lines | Spring Boot's default console format and lifecycle lines | D13 |
| `migrate` CLI (`up`/`down`/`version`/`force`), `.down.sql` files, `schema_migrations` table | Flyway at startup, `make migrate-up` / `migrate-info`, `flyway_schema_history` | D7 |
| One-shot `migrate` container in compose | The app migrates itself | §2.3 item 1 |
| Reading idempotency rows written by the Go app | Not supported | D9 |
| `go test -race` | Latch-based concurrency tests with repetition | No JVM equivalent |
| pgx-specific error text in logs | pgjdbc error text | Different driver |
| libpq service files / password files (`PGSERVICE`, `PGPASSFILE`) | Not supported; the common `PG*` variables are | Rarely used |
| Outbox payload keys are Go field names (`Id`, `Aggregate`, `OccurredAtT`, …) | camelCase keys (`eventId`, `aggregateId`, `occurredAt`, …) | §2.3 item 2 |
| Millisecond startup | A few seconds of JVM startup | JVM |

---

## 10. Risks and mitigations

| Item | Handling |
|---|---|
| The Maven Wrapper downloads Maven itself and doesn't read `settings.xml`, so the download may need JFrog credentials | It did. Resolved by not using a wrapper: Maven 3.9+ from the PATH |
| A dependency missing from JFrog, or not permitted there | Found in Phase 1.5; I stop and ask you rather than add a repository |
| Jackson 3 / Boot 4 configuration names differ from Boot 3 | Verify against the Boot 4.1 docs during Phase 1, with tests covering each setting |
| The Flyway version managed by Boot 4.1 must support Postgres 17 | Checked in Phase 1; Testcontainers runs `postgres:17-alpine` |
| Spring Modulith is designed for modules split by feature. Using it per layer relies on `OPEN` modules, which its docs call a transitional setup | Accepted (§2.3 item 4). When upgrading Modulith, check the release notes for changes to `OPEN` modules or `allowedDependencies` |
| The Modulith version must support Boot 4.1 | Checked in Phase 1.5 |
| Schema drift between the Go and Flyway migrations | Phase 4.6 compares schema-only dumps |
| Docs drifting from code | Snippets copied from real sources; "Try it" steps executed; `mkdocs build --strict` |
| Scope creep | No new features; anything that isn't a translation goes to a follow-up list |

---

## 11. Final acceptance checklist

- [x] Contract suite 100% green against the Java app.
- [x] 165 ported tests plus the new tests green (`make test`).
- [x] `docker compose up --build`: the app migrates, becomes healthy, and `/readyz` returns 200.
- [x] Every §2.2 fix is covered by a test.
- [x] `ModuleStructureTest` green: layer dependencies match `docs/reference/architecture.md`.
- [x] No `.go` files, `vendor/`, or Go tooling left; the Go-reference search is clean.
- [x] `mkdocs build --strict` passes; the runnable "Try it" steps (README quick start, chapter 7's
  relay log, chapter 8's curl loop) were run against the Java stack. The rest are coding
  exercises.
- [x] `LICENSE` attribution retained (D5).

---

## Appendix A: helper specifications

### A.1 Canonical UUID parsing (`CanonicalUuid`)

- Accepts exactly 36 characters: hex digits (either case) at every position except 8, 13, 18,
  and 23, which must be `-`.
- Everything else is rejected, including `{…}`, `urn:uuid:…`, 32-hex, and the loose forms that
  `UUID.fromString` would accept (such as `1-1-1-1-1`).
- Output is always lower-case canonical.

### A.2 `DATABASE_URL` → JDBC (`DatabaseUrl`)

The README documents both input forms, so both are supported:

- **Keyword DSN:** `key=value` pairs separated by whitespace. Values may be single-quoted with
  `\'` and `\\` escapes.
- **URL:** `postgres://` or `postgresql://`, with user info (percent-decoded), host (including
  `[::1]`), optional port (default 5432), database path, and query parameters.
- **Defaults**, as libpq applies them: host `localhost`, port `5432`, user = OS user,
  database = user. The `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, and `PGSSLMODE`
  environment variables fill fields that are missing.
- **Parameter mapping:** `sslmode` maps 1:1 to pgjdbc `sslmode`; `connect_timeout` →
  `connectTimeout`; `application_name` → `ApplicationName`; any other key → pgjdbc
  `options=-c key=value`.
- **Tests:** the default DSN, the compose URL, the Makefile URL, quoting edge cases, and the
  environment fallbacks.
