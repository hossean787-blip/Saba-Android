#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/saba-domain-smoke.jar"
SRC="$ROOT/app/src/main/java/ir/saba/invoice/domain"
kotlinc \
  "$ROOT/tools/domain-smoke/InvoiceLine.kt" \
  "$SRC/InvoiceCalculator.kt" \
  "$SRC/QuotaRules.kt" \
  "$SRC/LicensePolicy.kt" \
  "$ROOT/tools/domain-smoke/Smoke.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"
