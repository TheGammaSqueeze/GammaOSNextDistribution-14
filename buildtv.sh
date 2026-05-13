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

set -e

MODE="${1:-nosync}"

mkdir -p out/home/build-output

HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble TV64VN "$MODE"
