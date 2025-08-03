#!/bin/bash -eu

# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Verifies that various intermediate outputs of the build have deterministic
# outputs. Nondeterministic intermediate outputs have incremental performance
# implications, so this is a critical test even if the determinism if the final
# outputs is not in question.
#
# Determinism is verified by running several builds and comparing checksums of
# outputs. This may provides confidence in determinism, but does not guarantee
# it. "Flakiness" in this test should thus be treated as indicative of a
# failure, and investigated promptly.
if [[ -z ${OUT_DIR+x} ]]; then
  OUT_DIR="out"
fi

if [[ -z ${DIST_DIR+x} ]]; then
  echo "DIST_DIR not set. Using ${OUT_DIR}/dist. This should only be used for manual developer testing."
  DIST_DIR="${OUT_DIR}/dist"
fi

if [[ -z ${TARGET_PRODUCT+x} ]]; then
  echo "TARGET_PRODUCT not set. Using aosp_arm64"
  TARGET_PRODUCT=aosp_arm64
fi

if [[ -z ${TARGET_BUILD_VARIANT+x} ]]; then
  echo "TARGET_BUILD_VARIANT not set. Using userdebug"
  TARGET_BUILD_VARIANT=userdebug
fi

UNAME="$(uname)"
case "$UNAME" in
Linux)
  PREBUILTS="prebuilts/build-tools/path/linux-x86"
  ;;
Darwin)
  PREBUILTS="prebuilts/build-tools/path/darwin-x86"
  ;;
*)
  exit 1
  ;;
esac

function clean_build {
  build/soong/soong_ui.bash --make-mode clean

  # Generate the ninja file with default setting. We expect Bazel to be enabled by
  # default.
  build/soong/soong_ui.bash --make-mode \
    --mk-metrics \
    BAZEL_STARTUP_ARGS="--max_idle_secs=5" \
    BAZEL_BUILD_ARGS="--color=no --curses=no --show_progress_rate_limit=5" \
    TARGET_PRODUCT=${TARGET_PRODUCT} \
    TARGET_BUILD_VARIANT=${TARGET_BUILD_VARIANT} \
    nothing \
    dist DIST_DIR=$DIST_DIR
}

function save_hash {
  local -r filepath="$1"
  find $OUT_DIR/soong/workspace -type f,l -iname "BUILD.bazel" -o -iname "*.bzl" | xargs "${PREBUILTS}"/md5sum > $filepath
  find $OUT_DIR/soong/soong_injection -type f,l | xargs "${PREBUILTS}"/md5sum >> $filepath
  "${PREBUILTS}"/md5sum $OUT_DIR/soong/Android-${TARGET_PRODUCT}.mk >> $filepath
  if [[ -z ${SKIP_NINJA_CHECK+x} ]]; then
    "${PREBUILTS}"/md5sum $OUT_DIR/soong/build.${TARGET_PRODUCT}.ninja >> $filepath
  fi
}

TESTDIR=$(mktemp -t testdir.XXXXXX -d)
FIRST_FILE=$TESTDIR/first_hashes
TEST_FILE=$TESTDIR/hashes_to_test

clean_build
save_hash $FIRST_FILE

for i in {1..4} ; do
  clean_build
  save_hash $TEST_FILE
  if cmp -s "$FIRST_FILE" "$TEST_FILE"
  then
    echo "Comparison $i succeeded."
  else
    cp $FIRST_FILE $TEST_FILE $DIST_DIR
    >&2 echo "Comparison $i failed. This likely indicates nondeterminism in the differing files."
    >&2 echo "\n\nFirst file hashes:\n"
    >&2 cat $FIRST_FILE
    >&2 echo "\n\nRerun $i:\n"
    >&2 cat $TEST_FILE
    exit 1
  fi
done
