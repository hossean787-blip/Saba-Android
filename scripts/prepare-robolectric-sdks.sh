#!/usr/bin/env bash
set -euo pipefail
# Retry dependency preparation only. Never mask a failed application test.
for attempt in 1 2 3; do
  if bash ./gradlew --no-daemon :app:prepareRobolectricSdks; then
    exit 0
  fi
  if [[ "$attempt" == 3 ]]; then
    echo "::error::Robolectric SDK preparation failed after three attempts; tests were not started."
    exit 1
  fi
  echo "::warning::SDK preparation failed (attempt $attempt); retrying Maven resolution."
  sleep "$((attempt * 10))"
done
