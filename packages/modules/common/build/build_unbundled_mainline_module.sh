#!/bin/bash -ex
#
# Copyright (C) 2021 The Android Open Source Project
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

function usage() {
  cat <<END_OF_USAGE
This script builds mainline modules. It is used from other build scripts that
are run on build servers, and is meant to build both AOSP and internal
variants of the modules.

Basic usage:
  \$ packages/modules/common/build/build_unbundled_mainline_module.sh \
      --dist_dir out/dist/mainline_modules_arm64 \
      --product module_arm64 \
      -j8

Arguments:
   --dist_dir <dir>    a dist directory to store the outputs in.
   --product <product> a target product to use when building.
   \$@ all other arguments are passed through to soong_ui.bash verbatim.
END_OF_USAGE
}

# List of AOSP modules to build if TARGET_BUILD_APPS is not set.
readonly -a DEFAULT_MODULES=(
  com.android.adbd
  com.android.art
  com.android.art.debug
  com.android.art.testing
  com.android.cellbroadcast
  com.android.conscrypt
  com.android.extservices
  com.android.i18n
  # TODO(b/210694291): include ipsec module in the build
  # com.android.ipsec
  com.android.media
  com.android.mediaprovider
  com.android.media.swcodec
  com.android.neuralnetworks
  # com.android.os.statsd
  com.android.permission
  com.android.resolv
  com.android.runtime
  com.android.sdkext
  # TODO(b/210694291): include tethering module in the build
  # com.android.tethering
  com.android.tzdata
  com.android.wifi
  test1_com.android.tzdata
  test_com.android.conscrypt
  test_com.android.media
  test_com.android.media.swcodec
  CaptivePortalLogin
  DocumentsUI
  ExtServices-tplus
  NetworkStack
  NetworkStackNext
  PermissionController
)

# Initializes and parses the command line arguments and environment variables.
#
# Do not rely on environment global variables for DIST_DIT and PRODUCT, since
# the script expects specific values for those, instead of anything that could
# have been lunch'ed in the terminal.
function init() {
  declare -ga ARGV
  while (($# > 0)); do
    case $1 in
    --dist_dir)
      local -r dist_dir="$2"
      shift 2
      ;;
    --product)
      local -r product="$2"
      shift 2
      ;;
    --help)
      usage
      exit
      ;;
    *)
      ARGV+=("$1")
      shift 1
      ;;
    esac
  done
  readonly ARGV

  if [ -z "${dist_dir}" ]; then
    echo "Expected --dist_dir arg is not provided."
    exit 1
  fi
  if [ -z "${product}" ]; then
    echo "Expected --product arg is not provided."
    exit 1
  fi

  DIST_DIR="${dist_dir}"
  declare -grx TARGET_BUILD_APPS="${TARGET_BUILD_APPS:-${DEFAULT_MODULES[*]}}"
  declare -grx TARGET_BUILD_DENSITY="${TARGET_BUILD_DENSITY:-alldpi}"
  declare -grx TARGET_BUILD_TYPE="${TARGET_BUILD_TYPE:-release}"
  declare -grx TARGET_BUILD_VARIANT="${TARGET_BUILD_VARIANT:-user}"
  declare -grx TARGET_PRODUCT="${product}"
  declare -grx BUILD_PRE_S_APEX="${BUILD_PRE_S_APEX:-false}"

  # This script cannot handle compressed apexes
  declare -grx OVERRIDE_PRODUCT_COMPRESSED_APEX=false

  # UNBUNDLED_BUILD_SDKS_FROM_SOURCE defaults to false, which is necessary to
  # use prebuilt SDKs on thin branches that may not have the sources (e.g.
  # frameworks/base).
}

function build_modules() {

  build/soong/soong_ui.bash --make-mode "$@" \
    ALWAYS_EMBED_NOTICES=true \
    MODULE_BUILD_FROM_SOURCE=true \
    ${extra_build_params} \
    "${RUN_ERROR_PRONE:+"RUN_ERROR_PRONE=true"}" \
    "${CHECK_API:+"checkapi"}" \
    apps_only \
    dist \
    lint-check
}

function main() {
  if [ ! -e "build/make/core/Makefile" ]; then
    echo "$0 must be run from the top of the Android source tree."
    exit 1
  fi

  # Run installclean to remove previous artifacts, so they don't accumulate on
  # the buildbots.
  build/soong/soong_ui.bash --make-mode installclean

  DIST_DIR="${DIST_DIR}" build_modules
}

init "$@"
# The wacky ${foo[@]+"${foo[@]}"}, makes bash correctly pass nothing when an
# array is empty (necessary prior to bash 4.4).
main ${ARGV[@]+"${ARGV[@]}"}
