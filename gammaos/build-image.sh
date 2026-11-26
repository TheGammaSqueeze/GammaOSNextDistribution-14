#!/bin/bash
# Builds one GammaOS system image, start to finish. build.sh, buildtv.sh, buildtv_cc.sh and
# buildfull.sh at the root of the tree are one line each on top of this; nothing else has to
# be set or run by hand.
#
#   bash gammaos/build-image.sh <64VN|64GN|TV64VN> [nosync|sync]
#
# In order:
#   1. the GammaOS Nano programs that are checked out are built (gammaos/build-nano-cores.sh).
#      A tree without those repositories skips this and builds an image without nano mode;
#   2. on a host with little memory, Soong's analysis gets a memory ceiling (GOMEMLIMIT), or it
#      outgrows the machine and the build crawls. Set GOMEMLIMIT yourself to choose another;
#   3. when the last image built in this out/ folder was another product, or the Core image
#      with the other garbage collector, the boot image is removed so that it is compiled
#      again: "make installclean" keeps it, and an image with the wrong one recompiles the
#      boot classpath on every start of the console;
#   4. the image is built (lineage_build_unified/buildbot_unified.sh);
#   5. the result is checked: the build script has reported success after a failed compile,
#      so this stops with an error unless a new image is in out/home/build-output.

set -e
cd "$(dirname "${BASH_SOURCE[0]}")/.."

FLAVOUR="${1:?usage: build-image.sh <64VN|64GN|TV64VN> [nosync|sync]}"
MODE="${2:-nosync}"
case "$FLAVOUR" in
    64VN) PRODUCT=arm64_bvN ;;
    64GN) PRODUCT=arm64_bgN ;;
    TV64VN) PRODUCT=tv_arm64_bvN ;;
    *) echo "build-image.sh: unknown image $FLAVOUR (64VN, 64GN or TV64VN)" >&2; exit 1 ;;
esac

# Ship APEX modules uncompressed. With compressed .capex files every first boot (and every
# boot after an OTA) has to decompress all of them into /data/apex before ART can even check
# its boot artifacts; on SD-card devices that is tens of seconds. Costs image size only.
export OVERRIDE_PRODUCT_COMPRESSED_APEX=false

mkdir -p out/home/build-output

bash gammaos/build-nano-cores.sh

# A full Soong analysis (any Android.bp change causes one) takes about 20 GB of heap. Below
# 30 GB of memory the collector is given two thirds of the machine to aim for instead.
# build/soong/ui/build/soong.go hands the variable on to soong_build.
if [ -z "${GOMEMLIMIT:-}" ]; then
    MEM_KB=$(sed -n 's/^MemTotal: *\([0-9]*\) kB/\1/p' /proc/meminfo 2>/dev/null)
    if [ -n "$MEM_KB" ] && [ "$MEM_KB" -lt 31457280 ]; then
        export GOMEMLIMIT="$((MEM_KB * 2 / 3 / 1024))MiB"
        echo "build-image.sh: $((MEM_KB / 1048576)) GB of memory, GOMEMLIMIT=$GOMEMLIMIT"
    fi
fi

# The collector of the boot image is the Core product's choice (GAMMAOS_BOOT_GC, see
# device/phh/treble/lineage_tv_arm64_bvN.mk); every product compiles its own boot image.
VARIANT="$PRODUCT"
[ "$PRODUCT" = tv_arm64_bvN ] && VARIANT="$PRODUCT ${GAMMAOS_BOOT_GC:-uffd}"
VARIANT_FILE=out/.gammaos-boot-image-variant
if [ "$(cat "$VARIANT_FILE" 2>/dev/null)" != "$VARIANT" ]; then
    echo "build-image.sh: the last image here was not \"$VARIANT\", the boot image is compiled again"
    rm -rf out/soong/dexpreopt*/dex_bootjars out/soong/dexpreopt*/dex_bootjars_unstripped
    rm -f out/target/product/*/system/framework/*/boot*.art \
          out/target/product/*/system/framework/*/boot*.oat \
          out/target/product/*/system/framework/*/boot*.vdex
    rm -f "$VARIANT_FILE"
fi

START_MARK="out/home/build-output/.build-start-$$"
: > "$START_MARK"
trap 'rm -f "$START_MARK"' EXIT

STATUS=0
HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble "$FLAVOUR" "$MODE" || STATUS=$?

IMAGE=$(find out/home/build-output -maxdepth 1 -name "lineage-*-UNOFFICIAL-$PRODUCT.img" \
    -newer "$START_MARK" | sort | tail -1)
if [ "$STATUS" != 0 ] || [ -z "$IMAGE" ]; then
    echo >&2
    echo "build-image.sh: THE BUILD FAILED (build script status $STATUS, no new $PRODUCT image in out/home/build-output)." >&2
    echo "build-image.sh: look for \"FAILED:\" in the output above." >&2
    [ "$STATUS" != 0 ] && exit "$STATUS"
    exit 1
fi
echo "$VARIANT" > "$VARIANT_FILE"

echo
echo "build-image.sh: built $IMAGE ($(du -h "$IMAGE" | cut -f1))"
PROP=out/target/product/tdgsi_arm64_ab/system/build.prop
if [ -f "$PROP" ]; then
    grep -E '^ro\.gammaos\.(build\.version|variant)=|^ro\.build\.date\.utc=' "$PROP" | sed 's/^/build-image.sh:   /'
    if grep -q '^ro\.gammaos\.nano\.available=true' "$PROP"; then
        if grep -q '^ro\.gammaos\.modules\.records=1' "$PROP"; then
            echo "build-image.sh:   GammaOS Nano is in the image, with the signed records of its programs"
        else
            echo "build-image.sh:   GammaOS Nano is in the image, WITHOUT signed records (a program was built with no signing key at hand)"
        fi
    else
        echo "build-image.sh:   no GammaOS Nano in this image (its repositories are not checked out): it boots as plain Android"
    fi
fi

# A nano program that came out different from what its repository has checked in is in this
# image, with a renewed record, but not committed yet.
for repo in frameworks/base/cmds/gammaos-nano gammaos/emulators/drastic-nano gammaos/emulators/melonds-nano \
            gammaos/emulators/azahar-nano gammaos/emulators/retroarch-nano; do
    [ -e "$repo/.git" ] || continue
    if [ -n "$(git -C "$repo" status --porcelain -- prebuilt 2>/dev/null)" ]; then
        echo "build-image.sh:   $repo: prebuilt/ differs from what is checked in (commit it before a release)"
    fi
done
