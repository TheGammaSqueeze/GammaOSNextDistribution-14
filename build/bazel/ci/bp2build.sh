#!/bin/bash -eux
# Verifies that bp2build-generated BUILD files result in successful Bazel
# builds.
#
# This verification script is designed to be used for continuous integration
# tests, though may also be used for manual developer verification.

#######
# Setup
#######

# Set the test output directories.
SOURCE_ROOT="$(dirname $0)/../../.."
OUT_DIR=$(realpath ${OUT_DIR:-${SOURCE_ROOT}/out})
if [[ -z ${DIST_DIR+x} ]]; then
  DIST_DIR="${OUT_DIR}/dist"
  echo "DIST_DIR not set. Using ${OUT_DIR}/dist. This should only be used for manual developer testing."
fi

# Before you add flags to this list, cosnider adding it to the "ci" bazelrc
# config instead of this list so that flags are not duplicated between scripts
# and bazelrc, and bazelrc is the Bazel-native way of organizing flags.
FLAGS=(
  --config=bp2build
  --config=ci
)
FLAGS="${FLAGS[@]}"

source "$(dirname $0)/build_with_bazel.sh"
source "$(dirname $0)/target_lists.sh"

###############
# Build and test targets for device target platform.
###############

build_for_device BUILD_TARGETS TEST_TARGETS DEVICE_ONLY_TARGETS

declare -a host_targets
host_targets+=( "${BUILD_TARGETS[@]}" )
host_targets+=( "${TEST_TARGETS[@]}" )
host_targets+=( "${HOST_ONLY_TEST_TARGETS[@]}" )

build_and_test_for_host ${host_targets[@]}

#########################################################################
# Check that rule wrappers have the same providers as the rules they wrap
#########################################################################

source "$(dirname $0)/../rules/java/wrapper_test.sh"
test_wrapper_providers

###################
# bp2build progress
###################

function get_soong_names_from_queryview() {
  names=$( build/bazel/bin/bazel query --config=ci --config=queryview --output=xml "${@}" \
    | awk -F'"' '$2 ~ /soong_module_name/ { print $4 }' \
    | sort -u )
  echo "${names[@]}"
}

# Generate bp2build progress reports and graphs for these modules into the dist
# dir so that they can be downloaded from the CI artifact list.
BP2BUILD_PROGRESS_MODULES=(
  NetworkStackNext  # not updatable but will be
  android_module_lib_stubs_current
  android_stubs_current
  android_system_server_stubs_current
  android_system_stubs_current
  android_test_stubs_current
  build-tools  # host sdk
  com.android.runtime  # not updatable but will be
  core-lambda-stubs  # DefaultLambdaStubsPath, StableCorePlatformBootclasspathLibraries
  core-public-stubs-system-modules
  ext  # FrameworkLibraries
  framework  # FrameworkLibraries
  framework-minus-apex
  framework-res # sdk dep Framework Res Module
  legacy-core-platform-api-stubs-system-modules
  legacy.core.platform.api.stubs
  platform-tools  # host sdk
  sdk
  stable-core-platform-api-stubs-system-modules  # StableCorePlatformSystemModules
  stable.core.platform.api.stubs # StableCorePlatformBootclasspathLibraries
)

# Query for some module types of interest so that we don't have to hardcode the
# lists
"${SOURCE_ROOT}/build/soong/soong_ui.bash" --make-mode BP2BUILD_VERBOSE=1 --skip-soong-tests queryview
rm -f out/ninja_build

# Only apexes/apps that specify updatable=1 are mainline modules, the other are
# "just" apexes/apps. Often this is not specified in the process of becoming a
# mainline module as enables a number of validations.
# Ignore defaults and test rules.
APEX_QUERY='attr(updatable, 1, //...) - kind("_defaults rule", //...) - kind("apex_test_ rule", //...)'
APEX_VNDK_QUERY="kind(\"apex_vndk rule\", //...)"

BP2BUILD_PROGRESS_MODULES+=( $(get_soong_names_from_queryview "${APEX_QUERY}"" + ""${APEX_VNDK_QUERY}" ) )

bp2build_progress_script="//build/bazel/scripts/bp2build_progress:bp2build_progress"
bp2build_progress_output_dir="${DIST_DIR}/bp2build-progress"
mkdir -p "${bp2build_progress_output_dir}"

report_args=""
for m in "${BP2BUILD_PROGRESS_MODULES[@]}"; do
  report_args="$report_args -m ""${m}"
  if [[ "${m}" =~ (media.swcodec|neuralnetworks)$ ]]; then
    build/bazel/bin/bazel run ${FLAGS} --config=linux_x86_64 "${bp2build_progress_script}" -- graph  -m "${m}" --out-file=$( realpath "${bp2build_progress_output_dir}" )"/${m}_graph.dot"
  fi
done

build/bazel/bin/bazel run ${FLAGS} --config=linux_x86_64 "${bp2build_progress_script}" -- \
  report ${report_args} \
  --proto-file=$( realpath "${bp2build_progress_output_dir}" )"/bp2build-progress.pb" \
  --out-file=$( realpath "${bp2build_progress_output_dir}" )"/progress_report.txt" \
  --bp2build-metrics-location=$( realpath "${DIST_DIR}" )"/logs" \
