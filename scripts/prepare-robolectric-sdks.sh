#!/usr/bin/env bash
set -euo pipefail
# Retry dependency preparation only. Use documented HTTPS Central mirrors.
for repository in mirror eu central; do
  if bash ./gradlew --no-daemon "-PsuraRobolectricRepository=$repository" :app:prepareRobolectricSdks; then
    exit 0
  fi
  echo "::warning::SDK preparation failed via $repository."
  if [[ "$repository" != central ]]; then sleep 15; fi
done
echo "::error::Robolectric SDK preparation failed on all configured repositories; tests were not started."
exit 1
