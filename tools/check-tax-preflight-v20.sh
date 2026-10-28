#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
file="$root/app/src/main/java/ir/saba/invoice/integration/TaxSubmissionPreflight.kt"
test -s "$file"
grep -q 'class PreflightOnlyTaxpayerGateway' "$file"
grep -q 'TaxpayerState.NOT_CONFIGURED' "$file"
! grep -Eiq 'https?://|private.?key.?=|BEGIN PRIVATE KEY' "$file"
echo 'PASS: offline gateway exists; no network endpoint or embedded key in adapter'
