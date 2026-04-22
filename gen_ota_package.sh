#!/bin/bash
# gen_ota_package.sh — Generate a GammaOS OTA update package from raw partition images.
#
# Usage:
#   ./gen_ota_package.sh -v VERSION -c VERSION_CODE -d DEVICE -o OUTPUT.zip \
#       system.img [vendor.img] [boot.img] [product.img]
#
# Example:
#   ./gen_ota_package.sh -v 1.3.0 -c 13000 -d pocketairmini -o ota_v1.3.0.zip \
#       out/target/product/tdgsi_arm64_ab/system.img \
#       /work/pairmini/GammaOSCoreVendor/vendor.img
#
# The script will:
#   1. XZ-compress each image (parallel, using all cores)
#   2. Compute SHA-256 hashes (compressed + uncompressed)
#   3. Generate manifest.json
#   4. Package everything into a zip (stored, no compression — XZ handles it)

set -euo pipefail

# --- Defaults ---
VERSION=""
VERSION_CODE=""
DEVICE=""
OUTPUT=""
MIN_BATTERY=5
IMAGES=()
XZ_THREADS=0  # 0 = auto (all cores)

# --- Known partition types ---
declare -A PART_TYPES=(
    [system]=logical
    [system_ext]=logical
    [system_dlkm]=logical
    [vendor]=logical
    [vendor_dlkm]=logical
    [product]=logical
    [odm]=logical
    [odm_dlkm]=logical
    [boot]=physical
    [init_boot]=physical
    [vendor_boot]=physical
    [dtbo]=physical
    [vbmeta]=physical
    [vbmeta_system]=physical
    [vbmeta_vendor]=physical
)

usage() {
    cat <<'EOF'
Usage: gen_ota_package.sh [OPTIONS] IMAGE [IMAGE...]

Options:
  -v VERSION        Version string (e.g. "1.3.0")         [required]
  -c VERSION_CODE   Numeric version code (e.g. 13000)     [required]
  -d DEVICE         Device codename (e.g. "pocketairmini") [required]
  -o OUTPUT         Output zip path                        [required]
  -b MIN_BATTERY    Minimum battery % required (default: 5)
  -t XZ_THREADS     XZ compression threads (default: all cores)
  -h                Show this help

Each IMAGE should be a raw .img file. The partition name is derived from the
filename (e.g. "system.img" -> partition "system"). Supported partitions:
  logical:  system, system_ext, system_dlkm, vendor, vendor_dlkm,
            product, odm, odm_dlkm
  physical: boot, init_boot, vendor_boot, dtbo,
            vbmeta, vbmeta_system, vbmeta_vendor

Example:
  ./gen_ota_package.sh -v 1.3.0 -c 13000 -d pocketairmini -o update.zip \
      system.img vendor.img
EOF
    exit "${1:-0}"
}

# --- Parse args ---
while getopts "v:c:d:o:b:t:h" opt; do
    case "$opt" in
        v) VERSION="$OPTARG" ;;
        c) VERSION_CODE="$OPTARG" ;;
        d) DEVICE="$OPTARG" ;;
        o) OUTPUT="$OPTARG" ;;
        b) MIN_BATTERY="$OPTARG" ;;
        t) XZ_THREADS="$OPTARG" ;;
        h) usage 0 ;;
        *) usage 1 ;;
    esac
done
shift $((OPTIND - 1))
IMAGES=("$@")

# --- Validate ---
if [ -z "$VERSION" ] || [ -z "$VERSION_CODE" ] || [ -z "$DEVICE" ] || [ -z "$OUTPUT" ]; then
    echo "Error: -v, -c, -d, and -o are all required." >&2
    usage 1
fi
if [ ${#IMAGES[@]} -eq 0 ]; then
    echo "Error: at least one image file is required." >&2
    usage 1
fi
for img in "${IMAGES[@]}"; do
    if [ ! -f "$img" ]; then
        echo "Error: image not found: $img" >&2
        exit 1
    fi
done

# --- Setup work dir ---
WORKDIR=$(mktemp -d "/tmp/gammaos_ota_pkg.XXXXXX")
trap 'rm -rf "$WORKDIR"' EXIT
echo "Working directory: $WORKDIR"

# --- Process each image ---
PARTITIONS_JSON=""
ZIPFILES=()

for img in "${IMAGES[@]}"; do
    BASENAME=$(basename "$img")
    PARTNAME="${BASENAME%.img}"
    XZNAME="${PARTNAME}.img.xz"
    TYPE="${PART_TYPES[$PARTNAME]:-logical}"
    SIZE=$(stat -c '%s' "$img")

    echo ""
    echo "=== $PARTNAME ($TYPE) ==="
    echo "  Source: $img ($((SIZE / 1024 / 1024)) MB)"

    # SHA-256 of uncompressed image
    echo -n "  SHA-256 (raw)... "
    SHA_UNCOMP=$(sha256sum "$img" | cut -d' ' -f1)
    echo "$SHA_UNCOMP"

    # XZ compress
    XZPATH="$WORKDIR/$XZNAME"
    echo -n "  Compressing with xz (-T$XZ_THREADS)... "
    xz -c -T"$XZ_THREADS" "$img" > "$XZPATH"
    XZSIZE=$(stat -c '%s' "$XZPATH")
    RATIO=$((XZSIZE * 100 / SIZE))
    echo "done ($((XZSIZE / 1024 / 1024)) MB, ${RATIO}%)"

    # SHA-256 of compressed file
    echo -n "  SHA-256 (xz)... "
    SHA_XZ=$(sha256sum "$XZPATH" | cut -d' ' -f1)
    echo "$SHA_XZ"

    # Build JSON fragment
    ENTRY=$(cat <<ENDJSON
        {
            "name": "$PARTNAME",
            "type": "$TYPE",
            "file": "$XZNAME",
            "sha256": "$SHA_XZ",
            "sha256_uncompressed": "$SHA_UNCOMP",
            "size": $SIZE
        }
ENDJSON
)
    if [ -n "$PARTITIONS_JSON" ]; then
        PARTITIONS_JSON="$PARTITIONS_JSON,
$ENTRY"
    else
        PARTITIONS_JSON="$ENTRY"
    fi

    ZIPFILES+=("$XZNAME")
done

# --- Generate manifest.json ---
DATETIME=$(date +%s)
MANIFEST="$WORKDIR/manifest.json"

cat > "$MANIFEST" <<ENDJSON
{
    "version": "$VERSION",
    "version_code": $VERSION_CODE,
    "datetime": $DATETIME,
    "device": ["$DEVICE"],
    "min_battery": $MIN_BATTERY,
    "partitions": [
$PARTITIONS_JSON
    ]
}
ENDJSON

echo ""
echo "=== manifest.json ==="
cat "$MANIFEST"

# --- Create zip ---
echo ""
echo "=== Packaging ==="
OUTPUT_ABS=$(realpath -m "$OUTPUT")
(
    cd "$WORKDIR"
    zip -0 "$OUTPUT_ABS" manifest.json "${ZIPFILES[@]}"
)

OUTSIZE=$(stat -c '%s' "$OUTPUT_ABS")
echo ""
echo "=== Done ==="
echo "  Output: $OUTPUT_ABS ($((OUTSIZE / 1024 / 1024)) MB)"
echo "  Partitions: ${ZIPFILES[*]}"
echo ""
echo "To install via adb:"
echo "  adb push $OUTPUT_ABS /sdcard/Download/"
echo "  Then use Updater app → Install from storage"
