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

bootstrap=
all=
root=
block_apexes=

function usage() {
  echo "usage: $0 [--bootstrap|--all] [--block apexes(colol-separated)] --root root" && exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bootstrap)
      bootstrap=yes
      shift
      ;;
    --all)
      all=yes
      shift
      ;;
    --block)
      block_apexes=$2
      shift
      shift
      ;;
    --root)
      root=$2
      shift
      shift
      ;;
    *)
      usage
  esac
done

if [ -z $root ]; then
  usage
fi

if [ ! -z $bootstrap ] && [ ! -z $all ]; then
  usage
fi

activate_level=0
if [ ! -z $bootstrap ]; then
  activate_level=1
elif [ ! -z $all ]; then
  activate_level=2
fi

function get_level() {
  case $1 in
    com.android.art|com.android.runtime|com.android.i18n|com.android.tzdata|com.android.vndk.vR)
      echo 1 ;;
    *)
      echo 2 ;;
  esac
}

function abs() {
  if [[ $1 = /* ]]; then
    echo $1
  else
    echo $(realpath $(pwd)/$1)
  fi
}

ROOT=$(abs $root)

# to use relative paths
cd $(dirname $0)

# clean /apex directory
rm -iRf $ROOT/apex

# prepare /apex directory
# 1) activate APEXes
# 2) generate /apex/apex-info-list.xml

mkdir -p $ROOT/apex

blockIndex=1
apexInfo=$ROOT/apex/apex-info-list.xml
echo "<?xml version=\"1.0\" encoding=\"utf-8\"?>" > $apexInfo
echo "<apex-info-list>" > $apexInfo

for partition in system product system_ext vendor; do
  if [ -d $ROOT/$partition/apex ]; then
    for src in $ROOT/$partition/apex/*/; do
      if test ! -d $src; then
        continue
      fi
      name=$(basename $src)
      dst=$ROOT/apex/$name
      # b/234792422 Atest is running with PATH prebuilts/build-tools/path/linux-x86
      # where the realpath does not support --relative-to option. Use shell
      # parameter expansion to avoid invalid options.
      module_path="${src/$ROOT/}"
      # simulate block apexes are activated from /dev/block/vdaN
      if [[ "$block_apexes" == *"$name"* ]]; then
        module_path=/dev/block/vda$blockIndex
        ((blockIndex=blockIndex+1))
      fi
      if [ $(get_level $name) -le $activate_level ]; then
        # simulate "activation" by copying "apex dir" into /apex
        cp -r $src $dst
        echo " <apex-info moduleName=\"$name\" modulePath=\"$module_path\" preinstalledModulePath=\"$module_path\" isFactory=\"true\" isActive=\"true\" />" >> $apexInfo
      fi
    done
  fi
done

echo "</apex-info-list>" >> $apexInfo
