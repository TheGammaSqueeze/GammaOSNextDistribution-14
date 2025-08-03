#!/bin/bash

# Copyright (C) 2018 The Android Open Source Project
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

set -e

# Run ART APEX tests.

SCRIPT_DIR=$(dirname $0)

# Status of whole test script.
exit_status=0
# Status of current test suite.
test_status=0

function say {
  echo "$0: $*"
}

function die {
  echo "$0: $*"
  exit 1
}

if [[ -z "$ANDROID_BUILD_TOP" ]]; then
  export ANDROID_BUILD_TOP=$(pwd)
  if [[ ! -x "$ANDROID_BUILD_TOP/build/soong/soong_ui.bash" ]]; then
    die "Run from the root of the Android tree, or run lunch/banchan/tapas first."
  fi
fi

query_build_vars=(
  HOST_OUT
  PRODUCT_COMPRESSED_APEX
  PRODUCT_OUT
)
vars="$($ANDROID_BUILD_TOP/build/soong/soong_ui.bash \
        --dumpvars-mode --vars="${query_build_vars[*]}")"
# Assign to a variable and eval that, since bash ignores any error status from
# the command substitution if it's directly on the eval line.
eval $vars

# Switch the build system to unbundled mode in the reduced manifest branch.
if [ ! -d $ANDROID_BUILD_TOP/frameworks/base ]; then
  export TARGET_BUILD_UNBUNDLED=true
fi

deapex_binaries=(
  deapexer
  debugfs_static
  fsck.erofs
)

have_deapex_binaries=true
for f in ${deapex_binaries[@]}; do
  if [ ! -e "$HOST_OUT/bin/$f" ]; then
    have_deapex_binaries=false
  fi
done
if $have_deapex_binaries; then :; else
  deapex_targets=( ${deapex_binaries[@]/%/-host} )
  say "Building host binaries for deapexer: ${deapex_targets[*]}"
  build/soong/soong_ui.bash --make-mode ${deapex_targets[@]} || \
    die "Failed to build: ${deapex_targets[*]}"
fi

# Fail early.
set -e

build_apex_p=true
list_image_files_p=false
print_image_tree_p=false
print_file_sizes_p=false

function usage {
  cat <<EOF
Usage: $0 [OPTION] [apexes...]
Build (optional) and run tests on ART APEX package (on host). Defaults to all
applicable APEXes if none is given on the command line.

  -B, --skip-build    skip the build step
  -l, --list-files    list the contents of the ext4 image (\`find\`-like style)
  -t, --print-tree    list the contents of the ext4 image (\`tree\`-like style)
  -s, --print-sizes   print the size in bytes of each file when listing contents
  --bitness=32|64|multilib|auto  passed on to art_apex_test.py
  -h, --help          display this help and exit

EOF
  exit
}

device_bitness_arg=""
apex_modules=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    (-B|--skip-build)  build_apex_p=false;;
    (-l|--list-files)  list_image_files_p=true;;
    (-t|--print-tree)  print_image_tree_p=true;;
    (-s|--print-sizes) print_file_sizes_p=true;;
    (--bitness=*)      device_bitness_arg=$1;;
    (-h|--help) usage;;
    (-*) die "Unknown option: '$1'
Try '$0 --help' for more information.";;
    (*) apex_modules+=($1);;
  esac
  shift
done

# build_apex APEX_MODULES
# -----------------------
# Build APEX packages APEX_MODULES.
function build_apex {
  if $build_apex_p; then
    say "Building $@" && build/soong/soong_ui.bash --make-mode "$@" || die "Cannot build $@"
  fi
}

# maybe_list_apex_contents_apex APEX TMPDIR [other]
function maybe_list_apex_contents_apex {
  local print_options=()
  if $print_file_sizes_p; then
    print_options+=(--size)
  fi

  # List the contents of the apex in list form.
  if $list_image_files_p; then
    say "Listing image files"
    $SCRIPT_DIR/art_apex_test.py --list ${print_options[@]} $@
  fi

  # List the contents of the apex in tree form.
  if $print_image_tree_p; then
    say "Printing image tree"
    $SCRIPT_DIR/art_apex_test.py --tree ${print_options[@]} $@
  fi
}

function fail_check {
  echo "$0: FAILED: $*"
  test_status=1
  exit_status=1
}

if [ ${#apex_modules[@]} -eq 0 ]; then
  # Test as many modules as possible.
  apex_modules=(
    "com.android.art"
    "com.android.art.debug"
    "com.android.art.testing"
  )
fi

# Build the APEX packages (optional).
build_apex ${apex_modules[@]}

# Clean-up.
function cleanup {
  rm -rf "$work_dir"
}

# Garbage collection.
function finish {
  # Don't fail early during cleanup.
  set +e
  cleanup
}

for apex_module in ${apex_modules[@]}; do
  test_status=0
  say "Checking APEX package $apex_module"
  work_dir=$(mktemp -d)
  trap finish EXIT

  art_apex_test_args="--tmpdir $work_dir"
  test_only_args=""
  art_apex_test_args="$art_apex_test_args $device_bitness_arg"
  # Note: The Testing ART APEX is never built as a Compressed APEX.
  if [[ "$PRODUCT_COMPRESSED_APEX" = true && $apex_module != *.testing ]]; then
    apex_path="$PRODUCT_OUT/system/apex/${apex_module}.capex"
  else
    apex_path="$PRODUCT_OUT/system/apex/${apex_module}.apex"
  fi
  if $have_deapex_binaries; then
    art_apex_test_args="$art_apex_test_args --deapexer $HOST_OUT/bin/deapexer"
    art_apex_test_args="$art_apex_test_args --debugfs $HOST_OUT/bin/debugfs_static"
    art_apex_test_args="$art_apex_test_args --fsckerofs $HOST_OUT/bin/fsck.erofs"
  fi
  case $apex_module in
    (*.debug)   test_only_args="--flavor debug";;
    (*.testing) test_only_args="--flavor testing";;
    (*)         test_only_args="--flavor release";;
  esac
  say "APEX package path: $apex_path"

  # List the contents of the APEX image (optional).
  maybe_list_apex_contents_apex $art_apex_test_args $apex_path

  # Run tests on APEX package.
  $SCRIPT_DIR/art_apex_test.py $art_apex_test_args $test_only_args $apex_path \
    || fail_check "Checks failed on $apex_module"

  # Clean up.
  trap - EXIT
  cleanup

  [[ "$test_status" = 0 ]] && say "$apex_module tests passed"
  echo
done

[[ "$exit_status" = 0 ]] && say "All ART APEX tests passed"

exit $exit_status
