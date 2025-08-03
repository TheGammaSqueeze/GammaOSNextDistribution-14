#!/bin/bash
# rust-analyzer integration test.
# Ensure that the prebuilt version of rust-analyzer is able to parse our
# automatically generated rust-project.json. This script must be run in a valid
# repository tree.
#
# The following environment variables affect the result:
#   DIST_DIR  where the output of `rust-analyzer analysis-stats` will be stored.

set -e

readonly UNAME="$(uname)"
case "$UNAME" in
Linux)
    readonly OS='linux'
    ;;
Darwin)
    readonly OS='darwin'
    ;;
*)
    echo "Unsupported OS '$UNAME'"
    exit 1
    ;;
esac

readonly ANDROID_TOP="$(cd $(dirname $0)/../../..; pwd)"
cd "$ANDROID_TOP"

export OUT_DIR="${OUT_DIR:-out}"
readonly SOONG_OUT="${OUT_DIR}/soong"
readonly RUST_PROJECT_PATH="${SOONG_OUT}/rust-project.json"

# http://b/222532724
# https://github.com/rust-analyzer/rust-analyzer/issues/11614
# Set CHALK_OVERFLOW_DEPTH to work around current limits (300 isn't sufficient).
export CHALK_OVERFLOW_DEPTH="${CHALK_OVERFLOW_DEPTH:-400}"

# Generate rust-project.json.
build/soong/soong_ui.bash --make-mode SOONG_GEN_RUST_PROJECT=1 nothing

# Symlink it from ANDROID_TOP.
[ -f "${RUST_PROJECT_PATH}" ]
ln -s "${RUST_PROJECT_PATH}" .
trap "rm \"${ANDROID_TOP}\"/rust-project.json" EXIT

# Run rust-analyzer analysis-stats. It will return 0 if rust-project.json can be found and parsed.
prebuilts/rust/${OS}-x86/stable/rust-analyzer analysis-stats . 2>"${DIST_DIR}"/rust-analyzer-stats.log
retval=$?
if [[ "$retval" -ne 0 ]]; then
  echo "Error returned by 'rust-analyzer analysis-stats .'"
  echo "Check rust-analyzer-stats.log for more details."
  exit "$retval"
fi
