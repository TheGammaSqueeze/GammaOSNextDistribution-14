#!/bin/bash
# Build the non-TV GammaOS Next image (lineage_arm64_bvN).
# Equivalent to:  bash lineage_build_unified/buildbot_unified.sh treble 64VN
#
# The buildbot_unified.sh script will: lunch lineage_arm64_bvN,
# run `make installclean`, build `systemimage`, then rename the
# resulting system.img into out/home/build-output/lineage-21.0-*.img.
#
# Pass `sync` as the first arg if you want repo sync + patch apply
# before the build runs. Default is nosync (current local tree).

set -e

MODE="${1:-nosync}"

# Ship APEX modules uncompressed. With compressed .capex files every first boot (and every
# boot after an OTA) has to decompress all of them into /data/apex before ART can even check
# its boot artifacts; on SD-card devices that is tens of seconds. Costs image size only.
export OVERRIDE_PRODUCT_COMPRESSED_APEX=false

mkdir -p out/home/build-output

HOME="$PWD/out/home" bash lineage_build_unified/buildbot_unified.sh treble 64VN "$MODE"
