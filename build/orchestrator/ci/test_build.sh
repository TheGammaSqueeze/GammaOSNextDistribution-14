#!/bin/bash
set -eu

################################################################################
# This script is intended to be a test to verify that the orchestrator still
# works for others (assuming that they use a similar workflow.)
#
# NOT INTENDED to be used for general building and development, since it removes
# out/ and does other semi-hardcoded things.
################################################################################

# Typical usage:
#
# orchestrator/test_build.sh
#   This builds the default target
# orchestrator/test_build.sh vendor/nothing
#   Build "vendor/nothing", rather than the default target.
#
# Environment variables that affect this script:
#   OUT_DIR: Output directory.  We assume "out" if not set.
#   MCOMBO_DIR: Directory with (test) mcombo files.
#   MCOMBO_FILE: Mcombo file to use.  Default: aosp_cf_arm64_phone.mcombo
#
# Any arguments passed to the script are passed to multitree_build.

TOP="$(repo --show-toplevel || git rev-parse --show-toplevel || echo .)"
if [[ ${TOP} != . ]]; then
  echo "running build in ${TOP}" >&2
  cd "${TOP}"
fi

: ${MCOMBO_DIR:=orchestrator/build/orchestrator/multitree_combos/}
# Force a trailing /
MCOMBO_DIR="${MCOMBO_DIR%%/}/"
: ${MCOMBO_FILE:=aosp_cf_arm64_phone.mcombo}

# In aosp/2328802, build/make/core/envsetup.mk must exist under the top of the
# workspace. For now, make {WORKSPACE}/build a symlink to orchestrator/build.
if [[ ! -d build/ ]]; then
  ln -s orchestrator/build build
fi

(
  set +u # envsetup has unset variable references.
  . orchestrator/build/make/envsetup.sh
  multitree_lunch "${MCOMBO_DIR}${MCOMBO_FILE}" userdebug
  rm -rf "${OUT_DIR:-out}"
  multitree_build "$@"
)
