# SURA owner account redesign — 2026-10-31

Scope: Saba-Android main, owner account and app settings navigation only.

- User's account is a six-action two-column hub: personal information, password change, mobile change, transfer charge, purchase charge and logout.
- "Settings" moved to the top-left hamburger menu; its font and biometric controls remain functional in AppSettingsScreen.
- Verified email is displayed from the just-authenticated credential (only after server login); verified registration name is shown only if the registration API issued a session. No invented mobile, name or account metadata.
- Existing local-only charge transfer workflow is routed from its tile; buyer quota transactions remain explicitly local/testing.
- No server endpoint currently exists in the authenticated Android gateway for changing password, changing mobile, retrieving profile or purchasing charge. Until a secure server contract and payment/OTP flow are implemented, these tiles show clear informational dialogs, never simulate success or submit sensitive data.
- No database/schema changes, sensitive logging, permission updates or purchases.

Validation: Re-run main JVM/Compose tests, build, lint and emulator smoke. Real owner account mobile and password changes remain blocked on backend contract/deployment.
