#!/bin/bash
# Update the bundled Google-apps seeds before cutting a bgN (Full Google) release.
#
# The gapps prebuilts in vendor/gapps are only bootstrap seeds. When they are old
# (GmsCore especially), a freshly flashed device spends its first boot downloading,
# reinstalling and re-dexopting hundreds of MB of Google apps right after setup,
# which pins a low-power handheld for a long time. This refreshes the seeds to the
# newest builds that still run on the target platform and trims optional apps, so
# that work is done at build time instead of on the user's device.
#
# Usage:
#   ./update-gapps.sh check     # report seed vs latest, download nothing
#   ./update-gapps.sh refresh   # refresh seed APKs in place
#   ./update-gapps.sh trim      # remove optional apps + regenerate makefiles
#   ./update-gapps.sh all       # refresh + trim (release prep, the usual one)
#
# What/which apps are refreshed or trimmed is controlled by
# vendor/gapps/tools/gapps-seeds.json.

set -e
cd "$(dirname "$0")"

TOOL="vendor/gapps/tools/refresh_gapps_seeds.py"
MODE="${1:-check}"

case "$MODE" in
    check)   python3 "$TOOL" --check ;;
    refresh) python3 "$TOOL" --refresh ;;
    trim)    python3 "$TOOL" --trim ;;
    all)     python3 "$TOOL" --refresh --trim ;;
    *) echo "usage: $0 {check|refresh|trim|all}"; exit 2 ;;
esac

echo
echo "Review the changes before committing:"
echo "  git -C . status vendor/gapps"
echo "  git -C . diff --stat vendor/gapps"
