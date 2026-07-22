#!/bin/bash
# Build the TV (ATV) GammaOS Next image (lineage_tv_arm64_bvN, branded
# "GammaOS Core"). Targets handheld emulator devices like the TrimUI
# Brick and other devices that use the ATV stack.
#
# Equivalent to: bash lineage_build_unified/buildbot_unified.sh treble TV64VN
#
# The buildbot_unified.sh script will: lunch lineage_tv_arm64_bvN,
# run `make installclean`, build `systemimage`, then rename the
# resulting system.img into out/home/build-output/lineage-21.0-*.img.
#
# Pass `sync` as the first arg if you want repo sync + patch apply
# before the build runs. Default is nosync (current local tree).
#
# GC / boot-image variant:
#   buildtv.sh            -> uffd/CMC boot image (DEFAULT), for modern (>=5.7 / GKI
#                            android12+) kernels like the RG DS (6.1). Optimized path.
#   buildtv.sh cc         -> read-barrier CC boot image, for old (<=4.14, non-uffd)
#                            kernels like ceres (4.9), which run CC natively and would
#                            otherwise recompile the whole boot classpath every boot.
# The two variants share the same lunch product + out/ dir, so switching between them
# rebuilds the boot image. See the GAMMAOS_BOOT_GC block in
# device/phh/treble/lineage_tv_arm64_bvN.mk. `cc` combines with sync/nosync, e.g.
# `buildtv.sh cc sync`.

set -e

# CC variant selector (must come before the sync/nosync mode arg).
if [ "$1" = "cc" ]; then
    export GAMMAOS_BOOT_GC=cc
    echo "buildtv.sh: building the CC (read-barrier) boot image for old-kernel devices"
    shift
fi

MODE="${1:-nosync}"

mkdir -p out/home/build-output

HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble TV64VN "$MODE"
