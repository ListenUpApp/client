#!/bin/bash
# generate-strings.sh — Generate Localizable.strings from shared JSON
#
# To wire as an Xcode Build Phase:
#   1. Select the iosApp target in Xcode
#   2. Build Phases → + → New Run Script Phase
#   3. Set the shell script to: "${SRCROOT}/Scripts/generate-strings.sh"
#   4. Drag it above "Compile Sources"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STRINGS_DIR="$REPO_ROOT/shared/src/commonMain/resources/strings"
RESOURCES_DIR="$SCRIPT_DIR/../iosApp/Resources"

for json_file in "$STRINGS_DIR"/*.json; do
    locale="$(basename "$json_file" .json)"
    out_dir="$RESOURCES_DIR/${locale}.lproj"
    mkdir -p "$out_dir"

    python3 -c "
import json, sys

def flatten(obj, prefix=''):
    items = []
    for k, v in obj.items():
        key = f'{prefix}.{k}' if prefix else k
        if isinstance(v, dict):
            items.extend(flatten(v, key))
        else:
            items.append((key, str(v)))
    return items

with open(sys.argv[1]) as f:
    data = json.load(f)

for key, value in sorted(flatten(data)):
    escaped = value.replace('\\\\', '\\\\\\\\').replace('\"', '\\\\\"')
    print(f'\"{key}\" = \"{escaped}\";')
" "$json_file" > "$out_dir/Localizable.strings"

    echo "Generated ${locale}.lproj/Localizable.strings"
done
