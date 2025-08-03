#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
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

set -e
shopt -s extglob
shopt -s globstar

# to use relative paths
cd $(dirname $0)

RUN_FROM_SERVER=0

# when executed directly from commandline, build dependencies
if [[ $(basename $0) == "rundiff.sh" ]]; then
  if [ -z $ANDROID_BUILD_TOP ]; then
    echo "You need to source and lunch before you can use this script"
    exit 1
  fi
  $ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode linkerconfig conv_apex_manifest conv_linker_config
else
  # workaround to use host tools on build server
  export PATH=$(dirname $0):$PATH
  RUN_FROM_SERVER=1
fi

# $1: target libraries.txt file
# $2: list of libs. ex) a.so:b.so:c.so
function write_libraries_txt {
  rm -rf $1
  IFS=':'
  for lib in $2; do
    echo $lib >> $1
  done
  unset IFS
}

# Simulate build process
# $1: input tree (with *.json)
# $2: output tree (*.json files are converted into *.pb)
function build_root {
  cp -R $1/* $2

  for json in $2/**/linker.config.json; do
    conv_linker_config proto -s $json -o ${json%.json}.pb
    rm $json
  done
  for json in $2/**/apex_manifest.json; do
    conv_apex_manifest proto $json -o ${json%.json}.pb
    rm $json
  done
}

function run_linkerconfig_stage1 {
  # prepare root
  echo "Prepare root for stage 1"
  TMP_PATH=$2/stage1
  mkdir $TMP_PATH
  build_root testdata/root $TMP_PATH
  ./testdata/prepare_root.sh --bootstrap --root $TMP_PATH

  mkdir -p $1/stage1
  echo "Running linkerconfig for stage 1"
  linkerconfig -v R -p R -z -r $TMP_PATH -t $1/stage1

  echo "Stage 1 completed"
}

function run_linkerconfig_stage2 {
  # prepare root
  echo "Prepare root for stage 2"
  TMP_PATH=$2/stage2
  mkdir $TMP_PATH
  build_root testdata/root $TMP_PATH
  ./testdata/prepare_root.sh --all --root $TMP_PATH

  mkdir -p $1/stage2
  echo "Running linkerconfig for stage 2"
  linkerconfig -v R -p R -z -r $TMP_PATH -t $1/stage2

  # skip prepare_root (reuse the previous setup)
  mkdir -p $1/gen-only-a-single-apex
  echo "Running linkerconfig for gen-only-a-single-apex"
  linkerconfig -v R -z -r $TMP_PATH --apex com.vendor.service2 -t $1/gen-only-a-single-apex

  # skip prepare_root in order to use the same apexs
  # but with system/etc/vndkcorevariant.libraries.txt
  vndk_core_variant_libs_file=$TMP_PATH/system/etc/vndkcorevariant.libraries.txt
  write_libraries_txt $vndk_core_variant_libs_file libevent.so:libexif.so:libfmq.so
  mkdir -p $1/vndk-in-system
  echo "Running linkerconfig for vndk-in-system"
  linkerconfig -v R -p R -z -r $TMP_PATH -t $1/vndk-in-system
  # clean up
  rm -if $vndk_core_variant_libs_file
  vndk_core_variant_libs_file=

  echo "Stage 2 completed"
}

function run_linkerconfig_deprecate_vndk {
  # prepare root
  echo "Prepare root for VNDK deprecation"
  TMP_PATH=$2/deprecate_vndk
  mkdir $TMP_PATH
  build_root testdata/root $TMP_PATH
  ./testdata/prepare_root.sh --all --block com.android.art:com.android.vndk.vR --root $TMP_PATH

  mkdir -p $1/deprecate_vndk
  echo "Running linkerconfig with VNDK deprecated"
  linkerconfig -z -r $TMP_PATH -t $1/deprecate_vndk

  # skip prepare_root (reuse the previous setup)
  mkdir -p $1/deprecate_product_vndk
  echo "Running linkerconfig with VNDK deprecated only with product partition"
  linkerconfig -v R -z -r $TMP_PATH -t $1/deprecate_product_vndk

  echo "Stage VNDK deprecation completed"
}

function run_linkerconfig_others {
  # prepare root
  echo "Prepare root for stage others"
  TMP_PATH=$2/others
  mkdir $TMP_PATH
  build_root testdata/root $TMP_PATH
  ./testdata/prepare_root.sh --all --block com.android.art:com.android.vndk.vR --root $TMP_PATH

  mkdir -p $1/guest
  echo "Running linkerconfig for guest"
  linkerconfig -v R -p R -z -r $TMP_PATH -t $1/guest

  echo "Stage others completed"
}

# $1: target output directory
function run_linkerconfig_to {
  # delete old output
  rm -rf $1

  TMP_ROOT=$(mktemp -d -t linkerconfig-root-XXXXXXXX)

  # stage 0 is no longer tested because linkerconfig do not generate ld.config.txt for stage 0

  run_linkerconfig_stage1 $1 $TMP_ROOT &

  run_linkerconfig_stage2 $1 $TMP_ROOT &

  run_linkerconfig_deprecate_vndk $1 $TMP_ROOT &

  run_linkerconfig_others $1 $TMP_ROOT &

  for job in `jobs -p`
  do
    wait $job
  done

  # Remove temp root if required
  if [[ $RUN_FROM_SERVER -ne 1 ]]; then
    rm -rf $TMP_ROOT
  fi
}

# update golden_output
if [[ $1 == "--update" ]]; then
  run_linkerconfig_to ./testdata/golden_output
  echo "Updated"
  exit 0
fi

echo "Running linkerconfig diff test..."

run_linkerconfig_to ./testdata/output

echo "Running diff from test output"
if diff -ruN ./testdata/golden_output ./testdata/output ; then
  echo "No changes."
else
  echo
  echo "------------------------------------------------------------------------------------------"
  echo "if change looks fine, run following:"
  echo "  \$ANDROID_BUILD_TOP/system/linkerconfig/rundiff.sh --update"
  echo "------------------------------------------------------------------------------------------"
  # fail
  exit 1
fi