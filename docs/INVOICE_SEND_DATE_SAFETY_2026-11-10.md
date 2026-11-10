# SURA Invoice Send-Date Safety — 2026-11-10

## Authorized and implemented
- Interpret selected future-send dates/times in **Asia/Tehran** irrespective of device time zone.
- Preserve local records even when their chosen send time has passed.
- Block early official manual submission before any signing, government HTTP request or outbox transition. Unscheduled invoices preserve existing manual workflow.
- Show selected time in official send list, and disable manual send button until the date arrives. Refresh button re-evaluates eligibility.
- Provide dedicated JVM regression tests for clock zone, boundary, invalid input, and no-network pre-due submissions.
- Standard versionCode 35 / versionName 0.35.0; full-catalog variant 36 / 0.36.0.

## Deliberately NOT implemented or authorized
- No background tax submission or timer-based send. A manual review and confirmation remains mandatory.
- No support for official corrections, cancellations, returns or specialized tax patterns without validated protocol mapping and acceptance tests.
- No claim of real acceptance by the government tax system, nor test of production taxpayer credentials.

## Verification
Read the GitHub Actions runs for the exact commit before marking any tests or APK signed.
