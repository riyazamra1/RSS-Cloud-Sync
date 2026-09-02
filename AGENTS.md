# RSS CLOUD SYNC — Codex Cloud Development Instructions

## Mission
Work on the Android application in this repository as **RSS CLOUD SYNC v4 / Release**. Treat the existing `main` branch as the baseline and preserve working behavior unless a change is required for correctness, reliability, security, or release readiness.

## Repository facts
- Android application module: `app`
- Namespace: `com.riyaz.rsscloudsync`
- Application ID: `com.riyaz.rsscloudsync`
- Compile/target SDK: 36
- Minimum SDK: 23
- Java/Kotlin JVM target: 17
- Android Gradle Plugin: 8.11.0
- Kotlin Android plugin: 1.9.22
- Current app version on baseline: 1.8 / versionCode 8

## Working rules
1. Inspect the repository and existing implementation before changing code. Do not rebuild the application from scratch.
2. Prefer small, focused, production-quality changes. Preserve existing public behavior unless it is demonstrably incorrect.
3. Do not commit secrets, keystores, passwords, API keys, tokens, or generated credentials. `release.properties` must remain local/secret if present.
4. Do not weaken authentication, authorization, transfer verification, sync integrity, or deletion safety.
5. For synchronization changes, prioritize data integrity, resumability, idempotency, retry/backoff, cancellation, and safe recovery from partial failures.
6. Avoid unnecessary dependency upgrades. Change dependency versions only when required and verify compatibility.
7. Keep Android background work compatible with modern Android restrictions; use the existing WorkManager approach where appropriate.
8. Update or add automated tests for behavior that changes. Do not delete tests merely to make a build pass.
9. Keep release builds reproducible and do not disable security or correctness checks to hide failures.
10. If a requirement is ambiguous, infer from existing code/tests/documentation first and choose the least destructive implementation.

## Validation
From repository root, run the strongest practical checks available in the cloud environment. At minimum, attempt:
- `./gradlew test`
- `./gradlew lint`
- `./gradlew assembleDebug`

For release-specific changes, also attempt:
- `./gradlew assembleRelease`

If a check cannot run because of missing cloud credentials, unavailable Android SDK components, signing material, or another environment limitation, report the exact limitation rather than bypassing it.

## Release acceptance
A change is ready only when:
- the app compiles;
- relevant tests pass;
- no new critical lint/build errors are introduced;
- sync operations remain safe under retry/interruption;
- no secrets are added;
- user-visible behavior is consistent with RSS CLOUD SYNC's purpose;
- the final response identifies changed files, validation performed, and any remaining blockers.

## Git workflow
For cloud coding work, use a dedicated branch and open a pull request against `main`. Do not force-push or rewrite history. Keep commits focused and descriptive.
