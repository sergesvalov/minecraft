#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_REPORT="$PROJECT_ROOT/data/plugins/WardenLog/backup_verify.json"

HAS_REPORT=false
for arg in "$@"; do
    if [[ "$arg" == "--report" || "$arg" == --report=* ]]; then
        HAS_REPORT=true
        break
    fi
done

if [ "$HAS_REPORT" = true ]; then
    exec python3 "$PROJECT_ROOT/scripts/verify_backup.py" "$@"
else
    exec python3 "$PROJECT_ROOT/scripts/verify_backup.py" --report "$DEFAULT_REPORT" "$@"
fi

