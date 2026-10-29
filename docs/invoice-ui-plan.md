# Invoice and UI completion

Goal: complete the existing local invoice workflow, retaining the Room v2 schema and the explicit offline tax-service boundary.

Design: keep Kotlin/Compose/Room; split the invoice list, editor and read-only details into a focused screen. Support multiple integer-quantity lines, item code, discount, tax, visible totals, editing saved drafts, and confirmation before finalization/deletion. Preserve editor state across recreation. Keep final invoices immutable. Respect company boundaries in repository operations. Do not claim support for fractional quantities or official tax submission.

Alternatives: replacing the application or changing numeric storage would expand migration risk. Extend the current repository and UI instead. This is an implementation choice, not a new product commitment.

Initial delivery constraints: no commit, PR, merge or publication. The user subsequently explicitly authorized integrating all prepared changes into main. Continuing constraints: no production tax requests, no destructive migration, no application release publication; preserve existing finalized totals and rounding policy.

Tasks:
- [x] Repository tests: multiline save/reload/edit, cross-company rejection, finalized immutability, delete-draft rules and quota idempotency.
- [x] Implement scoped read/delete and duplicate-number feedback in InvoiceRepository.
- [x] Implement persistent multiline editor, details, search/status filters, totals and guarded actions in InvoiceScreen.
- [x] Fix global Snackbar, scroll/IME, loading errors and company switching; add company/customer editing.
- [x] Run JVM tests, Android compilation/lint and instrumentation where available; review diff and package a patch plus source bundle.

Review focus: pending edits on back/company switch; invalid or Persian digits; duplicate invoice number; rapid repeated clicks; failed database operation retaining input. Existing financial rounding remains unchanged until separately specified against official documentation.

Verification: 86 JVM/Robolectric tests passed, zero failed/skipped. Debug APK, Android-test APK and lint tasks succeeded. Lint retains 12 warnings. Instrumentation execution and real-device installation were not performed. Independent review found and verified a save/refresh retry fix with two red-to-green regression tests. No remote repository mutation occurred.

Integration follow-up: preserve PR9's dashboard intent and ancestry with the completed invoice UI. PR10 is under concurrent development and is handled separately rather than overwriting its moving branch. Reverify the exact integration tree before merging. The earlier 86-test result records the initial local delivery.
