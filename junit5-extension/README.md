# flakyhunter-junit5

A ~150-line JUnit 5 extension that **skips tests quarantined in FlakyHunter**, so a known-flaky test stops
blocking your builds while someone fixes it. No dependencies besides JUnit; Java 17+.

## Use it

1. Add the dependency (via [JitPack](https://jitpack.io/#Devisri4/flakyhunter)):

   ```xml
   <repositories>
     <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
   </repositories>
   <dependency>
     <groupId>com.github.Devisri4.flakyhunter</groupId>
     <artifactId>flakyhunter-junit5</artifactId>
     <version>main-SNAPSHOT</version>
     <scope>test</scope>
   </dependency>
   ```

2. Turn it on, either per class with `@ExtendWith(FlakyHunterExtension.class)`, or for all tests by adding
   `junit.jupiter.extensions.autodetection.enabled=true` to `src/test/resources/junit-platform.properties`.

3. In CI, set `FLAKYHUNTER_API_KEY` (a repository secret) — and `FLAKYHUNTER_URL` if you host FlakyHunter yourself.

Quarantined tests then show as **skipped** with the reason, e.g.
`Quarantined in FlakyHunter: times out on CI (until 2026-11-19T10:00:00Z)`.

## How it decides

- The list comes from `GET /api/v1/quarantine` once per test JVM (not once per test).
- A test matches when SHA-256 of `ClassName::methodName` (or `methodName()` / its display name) equals a
  quarantined test's fingerprint — the same identity FlakyHunter computes from your JUnit reports.
- **Fail open:** no key, FlakyHunter down, timeout (5 s) → every test runs. A monitoring tool must never be the
  reason a build breaks.
