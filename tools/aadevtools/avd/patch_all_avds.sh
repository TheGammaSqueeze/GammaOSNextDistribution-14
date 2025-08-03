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
Change all AVD configuration for bigger RAM, heap & data-disk sizes. So it can perform better.
If TAG_ID of AVD is empty, it will change all AVDs. e.g.

RAM=4096 HEAP=576 DATA_DISK=6000 AVD_ROOT_DIR="$HOME/.android/avd" TAG_ID="android-automotive" ./patch_all_avds.sh
'''
}

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

# Export VAR with the value of the key in a file as:
# getValue "VAR" "KEY" "config.ini FILE"
# e.g. getValue "MY_AVD_ID" "AvdId" "$avd_dir/config.ini"
getValue() {
    VAR=$1
    KEY=$2
    FILE=$3
    LINE=$(cat $FILE | grep $KEY)
    VALUE=${LINE##*=}
    export "${VAR}=$VALUE"
    echo "${VAR}=$VALUE"
}

if [[ -z $AVD_ROOT_DIR ]]; then
  AVD_ROOT_DIR="$HOME/.android/avd"
fi
echo "AVD_ROOT_DIR=$AVD_ROOT_DIR"

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

for file in $(ls "$AVD_ROOT_DIR"); do
  avd_dir="$AVD_ROOT_DIR/$file"
  if [[ $file == *.avd ]]; then
    # Filter AVD type by TAG_ID
    config_file="$avd_dir/config.ini"
    getValue "MY_AVD_ID"  "AvdId" $config_file
    if [[ -n $TAG_ID  ]]; then
      getValue "MY_TAG_ID" "tag.id" $config_file
      if [[ $TAG_ID != $MY_TAG_ID ]]; then
        echo "SKIP: $MY_AVD_ID is $MY_TAG_ID rather $TAG_ID "
        echo
        continue
      fi
    fi
    RAM=$RAM HEAP=$HEAP DATA_DISK=$DATA_DISK AVD_DIR=$avd_dir $SCRIPT_DIR/patch_avd.sh
    echo
  fi
done