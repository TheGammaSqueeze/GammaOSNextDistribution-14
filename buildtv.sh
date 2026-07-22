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
# GC / boot-image variant: this default script builds the uffd/CMC boot image for
# MODERN kernels (>=5.7 / GKI android12+, e.g. the RG DS on 6.1) - the optimized path.
# For OLD (<=4.14, non-uffd) kernels (e.g. ceres on 4.9) use buildtv_cc.sh instead,
# which builds a read-barrier CC boot image; flashing this uffd image to an old-kernel
# device makes it recompile the whole boot classpath (~15s) on every boot. Both scripts
# share the same lunch product + out/ dir (see the GAMMAOS_BOOT_GC block in
# device/phh/treble/lineage_tv_arm64_bvN.mk), so alternating them rebuilds the boot image.

set -e

MODE="${1:-nosync}"

mkdir -p out/home/build-output

HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble TV64VN "$MODE"
