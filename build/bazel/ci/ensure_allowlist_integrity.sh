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

# Verifies that the soong mixed_build allowlists are fully-Bazelable.
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

build/soong/soong_ui.bash --make-mode \
  --mk-metrics \
  BAZEL_STARTUP_ARGS="--max_idle_secs=5" \
  BAZEL_BUILD_ARGS="--color=no --curses=no --show_progress_rate_limit=5" \
  TARGET_PRODUCT=${TARGET_PRODUCT} \
  TARGET_BUILD_VARIANT=${TARGET_BUILD_VARIANT} \
  --bazel-mode-staging \
  --ensure-allowlist-integrity \
  nothing \
  dist DIST_DIR=$DIST_DIR
