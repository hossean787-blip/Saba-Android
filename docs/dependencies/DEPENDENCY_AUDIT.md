# Saba Android dependency register — 2026-10-29

All declarations are in `app/build.gradle.kts`. Gradle resolution/build has **not** been run in this session.

## Added for existing code or next implementation
- `androidx.compose.material:material-icons-extended` — existing MainActivity imports extended Material icons; version governed by existing Compose BOM.
- `androidx.datastore:datastore-preferences:1.2.1` — persistent non-sensitive UI settings, used by `SabaPreferences`.
- `com.squareup.okhttp3:okhttp:4.12.0` — foundation for a future authenticated taxpayer transport; no live client wired.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` — unit tests; aligned with existing coroutines 1.9.0.
- `com.squareup.okhttp3:mockwebserver:4.12.0` — isolated network test fixtures.
- `androidx.room:room-testing:2.6.1` — instrumentation tests and database migrations.

## Existing; not changed
- AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28.
- Compose BOM 2024.12.01, activity-compose 1.9.3, lifecycle 2.8.7, Room 2.6.1.
- Target/min SDK 35/23.

## Deliberately not installed
- No GPL/AGPL upstream accounting code.
- No arbitrary taxpayer API SDK without checking official specifications and exact licensing.
- No SQLCipher/native database encryption yet: migration, key lifecycle, restore and failure recovery have not been designed.
- No dependency claims of installation: GitHub commit only alters build declarations; Gradle must resolve them.

## Validation gates
1. Confirm Gradle wrapper and CI availability.
2. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` using JDK 17.
3. Check dependency resolution, licensing, vulnerability advisories, method count, and application size.
4. Implement database migration testing and mock-network request tests before live taxpayer APIs.
5. Merge only after successful CI and code review.

Official AndroidX docs:
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/develop/ui/compose/bom
