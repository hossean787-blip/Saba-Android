# SURA startup branding — verification checkpoint (2026-10-30)

Scope: approved SURA Android repository, main only.

## Implemented
- Existing approved SURA launcher PNG stays intact as the application icon.
- Cold-start Compose intro renders 960 deterministic light particles which converge on the central S mark.
- A transparent cutout is generated in memory from the PNG, omitting its navy matte and gold launcher border.
- The mark fades in gradually; a button skips the intro; navigation completion is single-shot.
- Rotation/Activity recreation does not start a second intro; no session data is persisted.
- No external video file, runtime internet request, new permissions, or database migrations.

## Verified
- Main commit 4a30a6456268f40ef224a1c07497fad3ecf4216b: Android CI run 41097851476 completed successfully.
- CI log: 160 JVM tests passed, 0 failures, 0 errors, 0 skipped; Gradle build and lint succeeded.
- APK artifact: 12995969855 (ZIP bytes 11,734,946).
- New emulator screenshot test and rotation correction are included in the next commit; Android device validation for those remains pending.
- Real device installation and cinematic visual acceptance remain pending; no commercial release claim.

## Recovery and scope
No other project or branch modified. Reverting these branding commits does not require database changes.
