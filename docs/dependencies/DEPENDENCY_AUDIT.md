# Saba Android dependency register — 2026-10-29

All declarations are in `app/build.gradle.kts`. This register was reconciled with the completed invoice UI during PR10 conflict resolution. Actual validation results are recorded in `docs/build/CI_STATUS_FA.md` and the build evidence; declarations alone do not prove compatibility.

## Runtime dependency used by this integration
- `androidx.datastore:datastore-preferences:1.1.1` — non-sensitive UI settings, used by `SabaPreferences`, including migration from the existing `display` SharedPreferences. Keep this Kotlin/SDK-compatible version until a separately tested dependency upgrade.

## Deferred unused declarations from the draft
- `material-icons-extended` is unnecessary for the current icons; the existing Compose dependencies already compile them.
- OkHttp, MockWebServer and coroutines-test are not used by the offline boundary or current tests; adding them would not implement taxpayer transport.
- `room-testing` is not used by the existing migration tests. Add it when introducing tests that actually consume its helpers.

## Existing; not changed
- AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28.
- Compose BOM 2024.12.01, activity-compose 1.9.3, lifecycle 2.8.7, Room 2.6.1.
- Target/min SDK 35/23.

## Deliberately not installed
- No GPL/AGPL upstream accounting code.
- No arbitrary taxpayer API SDK without checking official specifications and exact licensing.
- No SQLCipher/native database encryption yet: migration, key lifecycle, restore and failure recovery have not been designed.
- No untested declaration is presented as an implemented product capability.

## Validation gates
1. Use the committed Gradle 8.9 wrapper with its distribution checksum; GitHub-hosted execution additionally requires a working account billing/quota state.
2. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` using JDK 17.
3. Check dependency resolution, licensing, vulnerability advisories, method count, and application size.
4. Implement database migration testing and mock-network request tests before live taxpayer APIs.
5. Record executable verification and code review before merging. The owner explicitly requested main integration while GitHub-hosted CI is blocked by account billing; retain that limitation and never describe the hosted run as green.

Official AndroidX docs:
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/develop/ui/compose/bom
