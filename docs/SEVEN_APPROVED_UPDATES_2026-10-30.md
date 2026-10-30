# SURA Android — seven approved changes / 2026-10-30

Repository: hossean787-blip/Saba-Android
Authorized branch: main only.
Scope: this task only; no unrelated projects or assets.

## Implemented
1. Reused this repository's approved `sura_brand_icon.png`; launcher adaptive icon based on that exact PNG, with a dark backing to avoid white borders.
2. Android launch theme uses SURA navy and a transparent platform splash icon, blending into Compose particle intro. The mandatory Android 12+ system splash cannot be fully disabled.
3. Replaced the intro's bottom text with «به دنیای سورا خوش آمدید»; retained cinematic 960-particle progression and frameless center mark.
4. Updated Android launcher label to «صورتحساب سورا».
5. Hardened biometric opt-in: recover broken keys, accept valid 12–16-byte AES-GCM IV, don't mistake transient sensor-manager state for failed saved enrollment; improve user-safe error categories and confirmation styling. No plaintext credentials, cloud credential copies or Auth bypass. Real hardware acceptance remains pending.
6. New shared SURA brand header, light accessible premium home cards, real locally sourced metrics, taxpayer selection anchored to the dashboard card; no duplicate header selector.
7. Light taxpayer overview with card list/search/actions, 4 taxpayer types on a single accessible row, contextual info icons next to field titles, search magnifier; database and edit/delete operations preserved.

## Actual quality evidence before the final search-icon adjustment
- main `d7d11b9499144f1ec3af3c511fde0010d7afd321`: 170 JVM tests passed, 0 failed, Gradle build/lint succeeded (GitHub run 41119100647).
- main `bf6da5dbf7e2de8fe885e511acae5c8bbfe007d1`: API29 emulator 14/14 instrumentation tests passed (run 41119727731).
- main `03692ffd36a6de3028beba9c9365f9731f86d752`: Gradle + 170 JVM tests passed (run 41120551191; logs) and emulator 14/14 + 3 targeted visual tests passed (run 41120551142; logs).
- Five real emulator screenshot PNGs obtained and visually inspected; contrast issue identified and corrected; Home, Moadian list and form now legible on light surfaces.
- Final search icon change pending its own CI proof at time of this record.

## Residual blockers / safeguards
- A biometric prompt and actual enrollment/unlock need validation on the user's Samsung handset; emulator cannot establish that real fingerprint hardware works.
- Debug APKs from different GitHub Actions runs were proven to have different certificate SHA-256 values. Existing app data cannot be protected by simply uninstalling/reinstalling. Stable private signing key and signature continuity are prerequisites for an in-place upgrade. No signing key committed.
- The mandatory native Android splash has been visually harmonized, not literally removed.
- No commercial release or verified in-place update is claimed.
- No database migration; original backups/records not modified by this UI task.
