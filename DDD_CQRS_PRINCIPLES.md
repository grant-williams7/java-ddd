# Domain-Driven Design (DDD) and CQRS Principles Guide

> **This guide has grown into a full tutorial site: [&lt;owner&gt;.github.io/java-ddd](https://<owner>.github.io/java-ddd/)**
>
> Everything that used to live in this file is now covered there in more depth, with every pattern anchored to the actual code in this repository.

Where to find what:

| Topic | Now lives at |
|---|---|
| Why DDD, core concepts | [Why DDD makes your life easier](https://<owner>.github.io/java-ddd/) |
| Ubiquitous language, the marketplace domain | [Tutorial 1: The domain and its language](https://<owner>.github.io/java-ddd/tutorial/01-the-domain/) |
| Entity design, validation, the validated-entity pattern | [Tutorial 2: Entities that guard themselves](https://<owner>.github.io/java-ddd/tutorial/02-entities/) |
| Value objects, the `Money` implementation | [Tutorial 3: Value objects, starting with Money](https://<owner>.github.io/java-ddd/tutorial/03-value-objects/) |
| Aggregates, boundaries, reference by Id | [Tutorial 4: Aggregates and their boundaries](https://<owner>.github.io/java-ddd/tutorial/04-aggregates/) |
| Repository interfaces and the `JdbcClient` implementation | [Tutorial 5: Repositories](https://<owner>.github.io/java-ddd/tutorial/05-repositories/) |
| CQRS: commands, queries, results | [Tutorial 6: CQRS, commands and queries](https://<owner>.github.io/java-ddd/tutorial/06-cqrs/) |
| Domain events and the transactional outbox | [Tutorial 7: Domain events and the outbox](https://<owner>.github.io/java-ddd/tutorial/07-domain-events-outbox/) |
| Race-safe idempotency keys | [Tutorial 8: Idempotent commands](https://<owner>.github.io/java-ddd/tutorial/08-idempotency/) |
| Testing strategy per layer | [Tutorial 9: Testing a DDD codebase](https://<owner>.github.io/java-ddd/tutorial/09-testing/) |
| Architecture layers, conventions, best practices | [Architecture reference](https://<owner>.github.io/java-ddd/reference/architecture/) |
| Applying the patterns to your own domain, trade-offs | [FAQ](https://<owner>.github.io/java-ddd/reference/faq/) |

Prefer reading in the repo? The same pages are plain markdown under [`docs/`](docs/).

For a packaged outbox in the Spring ecosystem, see the [Spring Modulith Event Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html).
