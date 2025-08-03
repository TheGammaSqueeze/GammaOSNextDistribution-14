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

echo Pack AVD images from ANDROID_PRODUCT_OUT to $HOME/Downloads/x86_64

if [[ -z $ANDROID_PRODUCT_OUT ]]; then
    echo "err: please set ANDROID_PRODUCT_OUT='/android/out/target/product/generic_x86_64'"
    exit
fi

if [[ -z $ANDROID_BUILD_TOP ]]; then
    echo "err: please set ANDROID_BUILD_TOP='/android'"
    exit
fi

# Defaults
if [[ -z $OUT_DIR ]]; then
    OUT_DIR="$HOME/Downloads/x86_64"
fi

if [[ -z $AVD_IMAGE_ZIP ]]; then
    AVD_IMAGE_ZIP="$HOME/Downloads/my_aosp_car_x86_64-qt-v123456.zip"
fi

echo Delete $OUT_DIR
rm -rf $OUT_DIR

echo Packing AVD images
OUT_DIR=$OUT_DIR \
    AVD_IMAGE_ZIP=$AVD_IMAGE_ZIP \
    $ANDROID_BUILD_TOP/device/generic/car/tools/pack_avd_img.sh
