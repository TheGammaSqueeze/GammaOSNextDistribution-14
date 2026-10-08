#!/bin/bash
# Build the TV (ATV) GammaOS Next image for LEGACY / OLD-KERNEL devices - the CC
# (read-barrier Concurrent Copying) boot-image variant.
#
# Use this for devices whose kernel does NOT support userfaultfd (<= 4.14, e.g. the
# ceres RG DS on 4.9): their ART runtime uses the CC collector, so they need a CC
# boot.art. The default buildtv.sh builds the uffd/CMC image for modern kernels
# (>= 5.7 / GKI android12+, e.g. the RG DS on 6.1); flashing that uffd image to an
# old-kernel device makes it recompile the whole boot classpath (~15s) on every boot.
#
# This is the same build as buildtv.sh but exports GAMMAOS_BOOT_GC=cc, which flips
# PRODUCT_ENABLE_UFFD_GC to false in device/phh/treble/lineage_tv_arm64_bvN.mk. Both
# scripts share the same lunch product + out/ dir, so alternating between buildtv.sh
# and buildtv_cc.sh rebuilds the boot image - build the variant you need, flash it,
# then rebuild the other when you switch device classes.
#
# Pass `sync` as the first arg if you want repo sync + patch apply before the build
# runs. Default is nosync (current local tree).

set -e

export GAMMAOS_BOOT_GC=cc
echo "buildtv_cc.sh: building the CC (read-barrier) boot image for old-kernel (<=4.14) devices"

MODE="${1:-nosync}"

# Ship APEX modules uncompressed. With compressed .capex files every first boot (and every
# boot after an OTA) has to decompress all of them into /data/apex before ART can even check
# its boot artifacts; on SD-card devices that is tens of seconds. Costs image size only.
export OVERRIDE_PRODUCT_COMPRESSED_APEX=false

mkdir -p out/home/build-output

# azahar-nano (3DS) core: built from external/azahar-nano with the NDK and CMake
# (fetched once into out/azahar-nano when missing), installed into
# gammaos/emulators/azahar-nano-lib. AZAHAR_NANO_SKIP_BUILD=1 keeps the committed libraries.
bash external/azahar-nano/gammaos/build-core.sh

HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble TV64VN "$MODE"
