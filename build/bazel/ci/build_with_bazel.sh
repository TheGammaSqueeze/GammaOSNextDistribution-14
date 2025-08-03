#!/bin/bash -eux

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
#
# Verifies mixed builds does not run if neither --bazel-mode-dev nor --bazel-mode
# is set.
# This verification script is designed to be used for continuous integration
# tests, though may also be used for manual developer verification.

STARTUP_FLAGS=(
  # Keep the Bazel server alive, package cache hot and reduce excessive I/O
  # and wall time by ensuring that max_idle_secs is longer than bp2build which
  # runs in every loop. bp2build takes ~20 seconds to run, so set this to a
  # minute to account for resource contention, but still ensure that the bazel
  # server doesn't stick around after.
  --max_idle_secs=60
)

# Before you add flags to this list, cosnider adding it to the "ci" bazelrc
# config instead of this list so that flags are not duplicated between scripts
# and bazelrc, and bazelrc is the Bazel-native way of organizing flags.
FLAGS=(
  --config=bp2build
  --config=ci
  --keep_going
)

function build_for_device() {
  local -n build_targets=$1
  local -n test_targets=$2
  ###########
  # Iterate over various products supported in the platform build.
  ###########
  product_prefix="aosp_"
  for arch in arm arm64 x86 x86_64; do
    # Re-run product config and bp2build for every TARGET_PRODUCT. This is
    # necessary as long as bp2build workspaces are not product independent.
    product=${product_prefix}${arch}
    "${SOURCE_ROOT}/build/soong/soong_ui.bash" --make-mode BP2BUILD_VERBOSE=1 TARGET_PRODUCT=${product} --skip-soong-tests bp2build dist
    # Remove the ninja_build output marker file to communicate to buildbot that this is not a regular Ninja build, and its
    # output should not be parsed as such.
    rm -f out/ninja_build

    # Dist the entire workspace of generated BUILD files, rooted from
    # out/soong/bp2build. This is done early so it's available even if
    # builds/tests fail. Currently the generated BUILD files can be different
    # between products due to Soong plugins and non-deterministic codegeneration.
    # We tar and gzip in separate steps because when using tar -z, you can't tell it to not include
    # a timestamp in the gzip header.
    tar c --mtime='1970-01-01' -C out/soong/bp2build . | gzip -n > "${DIST_DIR}/bp2build_generated_workspace_${product}.tar.gz"

    local device_startup_flags=(
      # Unique output bases per product to help with incremental builds across
      # invocations of this script.
      # e.g. the second invocation of this script for aosp_x86 would use the output_base
      # of aosp_x86 from the first invocation.
      --output_base="${OUT_DIR}/bazel/test_output_bases/${product}"
    )
    device_startup_flags+=( "${STARTUP_FLAGS[@]}" )

    # Use a loop to prevent unnecessarily switching --platforms because that drops
    # the Bazel analysis cache.
    #
    # 1. Build every target in $BUILD_TARGETS
    build/bazel/bin/bazel ${device_startup_flags[@]} \
      build ${FLAGS[@]} --config=android -- \
      ${build_targets[@]}

    # 2. Test every target that is compatible with an android target platform (e.g. analysis_tests, sh_tests, diff_tests).
    build/bazel/bin/bazel ${device_startup_flags[@]} \
      test ${FLAGS[@]} --build_tests_only --config=android -- \
      ${test_targets[@]}

    # 3. Dist mainline modules.
    build/bazel/bin/bazel ${device_startup_flags[@]} \
      run //build/bazel/ci/dist:mainline_modules ${FLAGS[@]} \
      --config=android -- \
      --dist_dir="${DIST_DIR}/mainline_modules_${arch}"
  done
}

function build_and_test_for_host() {
  targets=("$@")
  # We can safely build and test all targets on the host linux config, and rely on
  # incompatible target skipping for tests that cannot run on the host.
  build/bazel/bin/bazel \
    "${STARTUP_FLAGS[@]}" test ${FLAGS[@]} \
    --build_tests_only=false \
    --test_lang_filters=-tradefed_device_driven,-tradefed_host_driven_device,-tradefed_deviceless \
    -- ${targets[@]}
}
