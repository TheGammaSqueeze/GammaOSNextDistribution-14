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
Create an AVD. e.g.
  AVD_IMG_NAME="sdk_gcar" \
  API_LEVEL=30 \
  DEFAULT_DEVICE_XML="./resource/devices.xml" \
  ./create_avd.sh
'''
}

# Export a variable=default_value if it's empty, e.g.
# exportVar "VAR" "defaultValue"
exportVar()
{
  var=$1
  defaultValue=$2
  if [[ -z "${!var}" ]]; then
    export "${var}=$defaultValue"
    echo "${var}=$defaultValue"
  fi
}

# Export a variable with the value of an XML element in a file as, e.g.
# getValue "TAG_ID" "d:id" "./resource/devices.xml"
getValue() {
  var=$1
  elementTag=$2
  file=$3
  #e.g.        <d:id>aosp_car_10_landscape</d:id>
  value=$(sed -n -e "s/.*<${elementTag}>\(.*\)<\/${elementTag}>.*/\1/p" "${file}")
  export "${var}=${value}"
  echo "${var}=${value}"
}

if [[ -z "${AVD_IMG_NAME}" || -z "${API_LEVEL}" ]]; then
  readme
  exit
fi

MY_NAME=$0
SCRIPT_NAME="${MY_NAME##*/}"
SCRIPT_DIR="${MY_NAME%/$SCRIPT_NAME}"
echo "Running from ${SCRIPT_DIR}"

# set up for Linux or macOS
OS="$(uname -s)"
echo "Running on $OS"
if [[ $OS == "Linux" ]]; then
  export ANDROID_SDK_ROOT="${HOME}/Android/Sdk"
  SED_I_CMD="sed -i "
elif [[ $OS == "Darwin" ]]; then
  export ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
  SED_I_CMD="sed -i ''"
else
  echo "ERROR: this does not work on $OS"
  exit
fi
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"

if [[ -z ${AVD_IMG_DIR} ]]; then
  AVD_IMG_DIR="${ANDROID_SDK_ROOT}/system-images/android-${API_LEVEL}/${AVD_IMG_NAME}"
fi
echo "AVD_IMG_DIR=${AVD_IMG_DIR}"

if [[ ! -e "${AVD_IMG_DIR}" ]]; then
  echo "ERROR: no AVD image found at ${AVD_IMG_DIR}"
  exit
fi

ABI=$(ls "${AVD_IMG_DIR}")
if [[ "${ABI}" != "x86" && "${ABI}" != "x86_64" && "${ABI}" != "arm64" && "${ABI}" != "arm64-v8a" ]]; then
  echo "ERROR: AVD image zip file format incorrect as ${AVD_DIR} should contain x86, x86_64, arm64 or arm64-v8a as: https://source.android.com/devices/automotive/start/avd#pack-an-avd-image-zip-file "
  exit
fi
echo "ABI=${ABI}"

# sdk_gcar_30
AVD_NAME="${AVD_IMG_NAME}_${API_LEVEL}"
AVD_INI="${HOME}/.android/avd/${AVD_NAME}.ini"
AVD_PATH_REL="avd/${AVD_NAME}.avd"
AVD_PATH="${HOME}/.android/${AVD_PATH_REL}"
AVD_CONFIG_INI="${AVD_PATH}/config.ini"

if [[ -e "${AVD_PATH}" ]]; then
  echo "NOTE: ${AVD_PATH} exists. Delete them if to recreate, e.g."
  echo "rm -r ${AVD_PATH} || rm ${AVD_INI}"
  exit
fi

# It should be aligned with avdmanager.
# May use https://developer.android.com/studio/command-line/avdmanager, but
# 1. more setup steps required for users & 2. slow on parsing image folders
# So here just add  the bare minimum set up.
echo "Create ${AVD_INI}"
echo "avd.ini.encoding=UTF-8
path=${AVD_PATH}
path.rel=${AVD_PATH_REL}
target=android-${API_LEVEL}" >> "${AVD_INI}"

echo
mkdir -p "${AVD_PATH}"
USERDATA_DIR="${AVD_PATH}/userdata.img"
echo "Create Avd Userdata at ${USERDATA_DIR}"
cp "${AVD_IMG_DIR}/${ABI}/userdata.img" "${USERDATA_DIR}"

DEVICE_XML="${AVD_IMG_DIR}/${ABI}/devices.xml"
if [[ ! -e "${DEVICE_XML}" ]]; then
  echo "NOTE: No ${DEVICE_XML} in the AVD image."
  if [[ ! -e "${DEFAULT_DEVICE_XML}" ]]; then
    echo "ERROR: Need to set DEFAULT_DEVICE_XML at least."
    exit
  else
    echo "COPY: ${DEFAULT_DEVICE_XML} ${DEVICE_XML}"
    cp "${DEFAULT_DEVICE_XML}" "${DEVICE_XML}"
    echo "NOTE: Please restart Android Studio, so the new devics.xml will be properly loaded."
  fi
fi
echo "DEVICE_XML=${DEVICE_XML}"
getValue "DEVICE_PROFILE_NAME" "d:id" "${DEVICE_XML}"
getValue "MANUFACTURER" "d:manufacturer" "${DEVICE_XML}"
getValue "ORIENTATION" "d:screen-orientation" "${DEVICE_XML}"
getValue "TAG_ID" "d:tag-id" "${DEVICE_XML}"
getValue "SKIN" "d:skin" "${DEVICE_XML}"


if [[ "${ORIENTATION}" == "land" ]]; then
  ORIENTATION="landscape"
fi

echo "Create ${AVD_CONFIG_INI}"
exportVar "TAG_ID" "android-automotive"
# MANUFACTURER & DEVICE_PROFILE_NAME need to match those in devices.xml
exportVar "MANUFACTURER" "Android"
exportVar "DEVICE_PROFILE_NAME" "aosp_car_10_landscape"
exportVar "ORIENTATION" "landscape"
exportVar "NCORE" 4
exportVar "RAM" "4096"
exportVar "HEAP" "576"
exportVar "DATA" "6G"

if [[ "${ABI}" == "arm64-v8a" ]]; then
  HW_ARCH="arm64"
else
  HW_ARCH=${ABI}
fi

echo "avd.ini.encoding=UTF-8
AvdId=${AVD_NAME}
avd.ini.displayname=${AVD_NAME}
tag.display=${AVD_NAME}
tag.id=${TAG_ID}
image.sysdir.1=system-images/android-${API_LEVEL}/${AVD_IMG_NAME}/${ABI}/
abi.type=${ABI}
hw.arc=false
hw.device.manufacturer=${MANUFACTURER}
hw.device.name=${DEVICE_PROFILE_NAME}
hw.cpu.arch=${HW_ARCH}
hw.cpu.ncore=${NCORE}
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.keyboard=yes
hw.initialOrientation=${ORIENTATION}
hw.ramSize=${RAM}
vm.heapSize=${HEAP}
disk.dataPartition.size=${DATA}" > "${AVD_CONFIG_INI}"

if [[ -z "${SKIN}" ]]; then
  skinSettings="showDeviceFrame=no"
else
  skinSettings="showDeviceFrame=yes
skin.dynamic=yes
skin.name=${SKIN}
skin.path=${AVD_IMG_DIR}/${ABI}/skins/${SKIN}"
fi
echo "${skinSettings}" >> "${AVD_CONFIG_INI}"
