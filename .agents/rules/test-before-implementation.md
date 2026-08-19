# Rule: Test Before Implementation (TDD)

## Purpose
No production code without tests. Tests are written BEFORE or in parallel,
never after "it compiles".

## Rules
1. A feature/bugfix requires tests that fail before the implementation and pass after.
2. Domain/use cases: pure unit tests (no Android).
3. Data: instrumented tests or unit tests with fakes for Room/SQLCipher.
4. UI: Compose tests for critical states (not mandatory for every component).
5. A PR/commit without tests covering the change = rejected.
6. If a test's cost is disproportionate (trivial screen), document the reason
   in the milestone summary.

## Minimum expected coverage by layer
- **Domain**: business logic (calculations, invariants, LWW rules).
- **Data**: entity↔model mapping, critical DAO queries, SQLCipher (encrypt/decrypt).
- **UI**: high-priority flows (login, new movement, visible conflict).

## Tooling
- JUnit 4 + kotlinx-coroutines-test (unit).
- Compose UI Test (UI).
- In-memory Room / fakes for DAOs.