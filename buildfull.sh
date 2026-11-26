#!/bin/bash
# Build the GammaOS Next Full image (lineage_arm64_bgN): Android with Google apps.
# The same as build.sh, which builds the Lite image without them.
#
# The result is out/home/build-output/lineage-21.0-*-arm64_bgN.img.
#
# Pass `sync` as the first arg if you want repo sync + patch apply
# before the build runs. Default is nosync (current local tree).

set -e

exec bash "$(dirname "${BASH_SOURCE[0]}")/gammaos/build-image.sh" 64GN "${1:-nosync}"
