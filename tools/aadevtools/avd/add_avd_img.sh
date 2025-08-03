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
Add an AVD image to Android SDK dir. So you can use team in Android Studio AVD manager. e.g.
OEM_AVD_ZIP="$HOME/Downloads/sdk-repo-linux-system-images-eng.USER.zip" \
  VARIANT="avd_car" \
  API_LEVEL=30 \
  ./add_avd_img.sh
'''
}

if [[ -z $VARIANT || -z $API_LEVEL || -z $OEM_AVD_ZIP  ]]; then
  readme
  exit
fi

MY_NAME=$0
SCRIPT_NAME=${MY_NAME##*/}
SCRIPT_DIR=${MY_NAME%/$SCRIPT_NAME}
echo Running from $SCRIPT_DIR

# set up for Linux or macOS
OS="$(uname -s)"
echo "Running on $OS"
if [[ $OS == "Linux" ]]; then
  export ANDROID_SDK_DIR="$HOME/Android/Sdk"
  SED_I_CMD="sed -i "
elif [[ $OS == "Darwin" ]]; then
  export ANDROID_SDK_DIR="$HOME/Library/Android/sdk"
  SED_I_CMD="sed -i ''"
else
  echo "ERROR: this does not work on $OS"
  exit
fi
echo "ANDROID_SDK_DIR=$ANDROID_SDK_DIR"

PKG_XML_TEMPLATE="$SCRIPT_DIR/resource/package_template.xml"
if [[ ! -e $PKG_XML_TEMPLATE ]]; then
  echo "Make sure there is package.xml template at: $PKG_XML_TEMPLATE."
  exit
fi
echo "PKG_XML_TEMPLATE=$PKG_XML_TEMPLATE"

SYS_IMG_DIR="$ANDROID_SDK_DIR/system-images"
if [[ ! -d $SYS_IMG_DIR ]]; then
  echo "ERROR: can not find the AVD system images dir: $SYS_IMG_DIR"
  echo "Try to reinstall Android Studio & SDK as https://developer.android.com/studio"
fi

# expected system-images folder format: android-apiLevel/variant/arch/
AVD_DIR="$ANDROID_SDK_DIR/system-images/android-$API_LEVEL/$VARIANT"
if [[ -d $AVD_DIR ]]; then
  echo "NOTE: $AVD_DIR exists already. Delete it if to replace: rm -r $AVD_DIR"
else
  mkdir -p $AVD_DIR
  unzip $OEM_AVD_ZIP -d $AVD_DIR
fi

ABI=$(ls $AVD_DIR)
if [[ $ABI != "x86" && $ABI != "x86_64" && $ABI != "arm64" && $ABI != "arm64-v8a" ]]; then
  echo "ERROR: AVD image zip file format incorrect as $AVD_DIR should contain x86, x86_64, arm64 or arm64-v8a as: https://source.android.com/devices/automotive/start/avd#pack-an-avd-image-zip-file "
  exit
fi
echo "ABI=$ABI"

PKG_XML_FILE="$AVD_DIR/$ABI/package.xml"
if [[ -e $PKG_XML_FILE ]]; then
  echo "Delete $PKG_XML_FILE for this setup."
  rm $PKG_XML_FILE
fi

echo "Generate $PKG_XML_FILE accorindly"
sed "s/Pkg.Path/system-images;android-$API_LEVEL;$VARIANT;$ABI/" $PKG_XML_TEMPLATE > $PKG_XML_FILE
$SED_I_CMD "s/AndroidVersion.ApiLevel/$API_LEVEL/" $PKG_XML_FILE
$SED_I_CMD "s/SystemImage.TagId/android-automotive/" $PKG_XML_FILE
$SED_I_CMD "s/SystemImage.TagDisplay/$VARIANT/" $PKG_XML_FILE
$SED_I_CMD "s/SystemImage.Abi/$ABI/" $PKG_XML_FILE
$SED_I_CMD "s/Addon.VendorId/$VARIANT/" $PKG_XML_FILE
$SED_I_CMD "s/Addon.VendorDisplay/$VARIANT/" $PKG_XML_FILE
$SED_I_CMD "s/Display.Name/$VARIANT/" $PKG_XML_FILE

echo
echo "Restart Android Studio & select [System Image] in [Virtual Device Configuration], [AVD Manager]"
echo "ADDED: Hardware Category: Automotive, API Level: $API_LEVEL, ABI: $ABI, Target: $VARIANT"
echo
