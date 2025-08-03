#!/bin/bash -eu
# Verifies Bazel "staging mode"  succeeds when building "droid".
# This verification script is designed to be used for continuous integration
# tests, though may also be used for manual developer verification.

if [[ -z ${DIST_DIR+x} ]]; then
  echo "DIST_DIR not set. Using out/dist. This should only be used for manual developer testing."
  DIST_DIR="out/dist"
fi
if [[ -z ${TARGET_PRODUCT+x} ]]; then
  echo "TARGET_PRODUCT not set. Have you run lunch?"
  exit 1
fi

# Run a mixed build of "droid"
build/soong/soong_ui.bash --make-mode \
  --mk-metrics \
  --bazel-mode-staging \
  BP2BUILD_VERBOSE=1 \
  BAZEL_STARTUP_ARGS="--max_idle_secs=5" \
  BAZEL_BUILD_ARGS="--color=no --curses=no --show_progress_rate_limit=5" \
  droid platform_tests \
  dist DIST_DIR=$DIST_DIR

# Verify there are artifacts under the out directory that originated from bazel.
echo "Verifying OUT_DIR contains bazel-out..."
if find out/ -type d -name bazel-out &>/dev/null; then
  echo "bazel-out found."
else
  echo "bazel-out not found. This may indicate that mixed builds are silently not running."
  exit 1
fi
