#!/usr/bin/env bash
set -euo pipefail
# Resolve only the pinned SQLCipher dependency, not tests. Mirrors are Maven Central HTTPS endpoints.
for repository in eu mirror central; do
  if bash ./gradlew --no-daemon --stacktrace "-PsuraSqlCipherRepository=$repository" :app:resolveSuraCipher; then
    echo "SURA_CIPHER_REPO=$repository" >> "$GITHUB_ENV"
    exit 0
  fi
done
exit 1
