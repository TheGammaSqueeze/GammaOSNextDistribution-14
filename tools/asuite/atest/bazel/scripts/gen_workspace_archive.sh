#!/usr/bin/env bash

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A script to generate an Atest Bazel workspace for execution on the Android CI.

# Exit immediately on failures and disallow undefined variables.
set -euo pipefail
# List commands as they are executed. This helps debug the error
# if the script exits mid-way through.
set -x

function check_env_var()
{
  if [ ! -n "${!1}" ] ; then
    echo "Necessary environment variable ${1} missing, exiting."
    exit 1
  fi
}

# Check for necessary environment variables.
check_env_var "ANDROID_BUILD_TOP"
check_env_var "TARGET_PRODUCT"
check_env_var "TARGET_BUILD_VARIANT"

function get_build_var()
{
  (${ANDROID_BUILD_TOP}/build/soong/soong_ui.bash --dumpvar-mode --abs $1)
}

out=$(get_build_var PRODUCT_OUT)

# ANDROID_BUILD_TOP is deprecated, so don't use it throughout the script.
# But if someone sets it, we'll respect it.
cd ${ANDROID_BUILD_TOP:-.}

# Use the versioned Python binaries in prebuilts/ for a reproducible
# build with minimal reliance on host tools.
export PATH=`pwd`/prebuilts/build-tools/path/linux-x86:${PATH}

export \
  ANDROID_PRODUCT_OUT=${out} \
  OUT=${out} \
  ANDROID_HOST_OUT=$(get_build_var HOST_OUT) \
  ANDROID_TARGET_OUT_TESTCASES=$(get_build_var TARGET_OUT_TESTCASES)

if [ ! -n "${OUT_DIR:-}" ] ; then
  OUT_DIR=$(get_build_var "OUT_DIR")
fi

if [ ! -n "${DIST_DIR:-}" ] ; then
  echo "dist dir not defined, defaulting to OUT_DIR/dist."
  export DIST_DIR=${OUT_DIR}/dist
fi

# Build:
#  - Atest from source to pick up the latest changes
#  - Bazel test suite needed by BazelTest
#  - EXTRA_TARGETS requested on the commandline (used by git_master.gcl)
targets="atest dist empty-bazel-test-suite ${EXTRA_TARGETS:-}"
build/soong/soong_ui.bash --make-mode WRAPPER_TOOL=atest $targets

# TODO(b/277656887): Fix the underlying atest issue that causes the workspace to not be
# regenerated.
rm -rf ${OUT_DIR}/atest_bazel_workspace

# Generate the initial workspace via Atest Bazel mode.
${OUT_DIR}/host/linux-x86/bin/atest-dev \
  --no-metrics \
  --bazel-mode \
  --host-unit-test-only \
  --host \
  -c \
  -b # Builds dependencies without running tests.


# TODO(b/201242197): Create a stub workspace for the remote_coverage_tools
# package so that Bazel does not attempt to fetch resources online which is not
# allowed on build bots.
mkdir -p ${OUT_DIR}/atest_bazel_workspace/remote_coverage_tools
touch ${OUT_DIR}/atest_bazel_workspace/remote_coverage_tools/WORKSPACE
cat << EOF > ${OUT_DIR}/atest_bazel_workspace/remote_coverage_tools/BUILD
package(default_visibility = ["//visibility:public"])

filegroup(
    name = "coverage_report_generator",
    srcs = ["coverage_report_generator.sh"],
)
EOF

# Create the workspace archive.
prebuilts/build-tools/linux-x86/bin/soong_zip \
  -o ${DIST_DIR}/atest_bazel_workspace.zip \
  -P android-bazel-suite/ \
  -D out/atest_bazel_workspace/ \
  -f "out/atest_bazel_workspace/**/.*" \
  -symlinks=false  `# Follow symlinks and store the referenced files.` \
  -sha256  `# Store SHA256 checksum for each file to enable CAS.` \
  `# Avoid failing for dangling symlinks since these are expected` \
  `# because we don't build all targets.` \
  -ignore_missing_files

# Merge the workspace into bazel-test-suite.
prebuilts/build-tools/linux-x86/bin/merge_zips \
  ${DIST_DIR}/bazel-test-suite.zip \
  ${DIST_DIR}/empty-bazel-test-suite.zip \
  ${DIST_DIR}/atest_bazel_workspace.zip

# Remove the old archives we no longer need
rm -f \
  ${DIST_DIR}/atest_bazel_workspace.zip \
  ${DIST_DIR}/empty-bazel-test-suite.zip
