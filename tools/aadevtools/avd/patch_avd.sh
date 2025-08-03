#!/bin/bash

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

readme() {
  echo '''
Change an AVD configuration for bigger RAM, heap & data-disk sizes. So it can perform better. e.g.
RAM=4096 HEAP=576 DATA_DISK=6000 AVD_DIR="$HOME/.android/avd/Automotive_10_landscape_API_30.avd" ./patch_avd.sh

'''
}

if [[ -z $AVD_DIR  ]]; then
  readme
  exit
fi

# set up for Linux or macOS
OS="$(uname -s)"
echo "Running on $OS"
if [[ $OS == "Linux" ]]; then
  SED_I_CMD="sed -i "
elif [[ $OS == "Darwin" ]]; then
  SED_I_CMD="sed -i ''"
else
  echo "ERROR: this does not work on $OS"
  exit
fi

MY_NAME=$0
SCRIPT_NAME=${MY_NAME##*/}
SCRIPT_DIR=${MY_NAME%/$SCRIPT_NAME}
echo Running from $SCRIPT_DIR

if [[ -z $RAM ]]; then
    RAM=4096
fi
echo "RAM=$RAM"

if [[ -z $HEAP ]]; then
    HEAP=576
fi
echo "HEAP=$HEAP"

if [[ -z $DATA_DISK ]]; then
    DATA_DISK=6000
fi
echo "DATA_DISK=$DATA_DISK"

AVD_CONFIG="$AVD_DIR/config.ini"
if [[ ! -e $AVD_CONFIG ]]; then
  echo "ERROR: no AVD config file at: $AVD_CONFIG"
  exit
fi
echo "AVD_CONFIG=$AVD_CONFIG"

echo "CHANGE: hw.ramSize=$RAM, vm.heapSize=$HEAP & disk.dataPartition.size=${DATA_DISK}M in $AVD_CONFIG"
$SED_I_CMD '/^hw.ramSize/d' $AVD_CONFIG
$SED_I_CMD '/^vm.heapSize/d' $AVD_CONFIG
$SED_I_CMD '/^disk.dataPartition.size/d' $AVD_CONFIG
echo "hw.ramSize=$RAM" >> $AVD_CONFIG
echo "vm.heapSize=$HEAP" >> $AVD_CONFIG
echo "disk.dataPartition.size=${DATA_DISK}M" >> $AVD_CONFIG
cat $AVD_CONFIG | grep "hw.ramSize"
cat $AVD_CONFIG | grep "vm.heapSize"
cat $AVD_CONFIG | grep "disk.dataPartition.size"
