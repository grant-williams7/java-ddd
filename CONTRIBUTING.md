# Contributing to java-ddd

Thanks for your interest in improving this template!

## Getting started

1. Fork and clone the repository.
2. Install Java 25+, Maven 3.9+, and Docker (for integration tests).
3. Run the checks locally before opening a PR:
   ```bash
   make test    # full suite incl. Testcontainers (needs Docker)
   ```

## What makes a good contribution

- **Small and focused.** One pattern, fix, or improvement per PR.
- **Tests included.** Every behavior change needs a test. Integration tests use Testcontainers.
- **Consistent style.** Match the surrounding code: package-private by default, sparse comments, snake_case JSON. `ModuleStructureTest` fails the build if a layer imports something it shouldn't.
- **Schema changes** need a new `V<N>__description.sql` migration in `src/main/resources/db/migration/`. Never edit one that has already been applied.

## Proposing bigger changes

Open an issue first for new patterns (e.g. new bounded contexts, event bus integrations) so we can discuss whether it fits the template's teaching scope. The goal is a template that stays small enough to read in an afternoon.

## Questions

Open an issue — questions about applying DDD in Java are welcome and often turn into documentation improvements.
