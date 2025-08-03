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
Change forceColdBoot for all AVD config.ini files.
If TAG_ID of AVD is empty, it will change all AVDs. e.g.
FORCE_COLD_BOOT="yes" AVD_ROOT_DIR="$HOME/.android/avd" TAG_ID="android-automotive" ./set_avds_force_cold_boot.sh
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


# Set Cold Boot or Quick/Fast Boot for an AVD as:
# setForceColdBoot "ForceColdBoot" "ForceFastBoot" "config.ini FILE"
# e.g. setForceColdBoot "yes" "no" "$avd_dir/config.ini"
setForceColdBoot() {
  forceColdBoot=$1
  forceFastBoot=$2
  avdConfig=$3

  echo "CHANGE: fastboot.forceColdBoot=$forceColdBoot & fastboot.forceFastBoot=$forceFastBoot in $avdConfig"
  $SED_I_CMD '/^fastboot.forceColdBoot/d' $avdConfig
  $SED_I_CMD '/^fastboot.forceFastBoot/d' $avdConfig
  echo "fastboot.forceColdBoot=$forceColdBoot" >> $avdConfig
  echo "fastboot.forceFastBoot=$forceFastBoot" >> $avdConfig
  cat $avdConfig | grep "fastboot.forceColdBoot"
  cat $avdConfig | grep "fastboot.forceFastBoot"
}

# Set Cold Boot or Quick/Fast Boot for an AVD as:
# setAllAVDsForceColdBoot "ForceColdBoot" "ForceFastBoot" "AVD/ROOT/DIR" "TAG.ID"
# e.g. setAllAVDsForceColdBoot "yes" "no" "$HOME/.android/avd" "android-automotive"
setAllAVDsForceColdBoot() {
  forceColdBoot=$1
  forceFastBoot=$2
  avdRootDir=$3
  avdTagId=$4

  for file in $(ls "$avdRootDir"); do
    avd_dir="$avdRootDir/$file"
    if [[ $file == *.avd ]]; then
      # Filter AVD type by TAG_ID
      config_file="$avd_dir/config.ini"
      getValue "myAvdId"  "AvdId" $config_file
      if [[ -n $avdTagId  ]]; then
        getValue "myAvdTagId" "tag.id" $config_file
        if [[ $myAvdTagId != $avdTagId ]]; then
          echo "SKIP: $myAvdId is $myAvdTagId rather $avdTagId"
          echo
          continue
        fi
      fi
      setForceColdBoot $forceColdBoot $forceFastBoot $config_file
      echo
    fi
  done
}

if [[ -z $AVD_ROOT_DIR ]]; then
  AVD_ROOT_DIR="$HOME/.android/avd"
fi
echo "AVD_ROOT_DIR=$AVD_ROOT_DIR"

if [[ -z $FORCE_COLD_BOOT ]]; then
  FORCE_COLD_BOOT="yes"
fi
echo "FORCE_COLD_BOOT=$FORCE_COLD_BOOT"

case "$FORCE_COLD_BOOT" in
  yes)
    setAllAVDsForceColdBoot "yes" "no" $AVD_ROOT_DIR $TAG_ID
    ;;
  no)
    setAllAVDsForceColdBoot "no" "yes" $AVD_ROOT_DIR $TAG_ID
    ;;
  *)
    readme
    ;;
esac
