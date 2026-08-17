#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

exec "$BASE_PROJECT_DIR/payhip/test_payhip_webhook.sh" "$@"
