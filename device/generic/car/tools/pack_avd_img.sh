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

echo pack_avd_img.sh packs AVD images you built to be used on other hosts.
echo It copies necessary files from ANDROID_PRODUCT_OUT to OUT_DIR.

if [[ -z $ANDROID_PRODUCT_OUT ]]; then
    echo "err: please set ANDROID_PRODUCT_OUT='/android/out/target/product/generic_x86_64'"
    exit
fi

if [[ -z $ANDROID_BUILD_TOP ]]; then
    echo "err: please set ANDROID_BUILD_TOP='/android'"
    exit
fi

if [[ -z $OUT_DIR ]]; then
    echo "err: please set OUT_DIR='/my/out/dir/x86_64'"
    exit
fi

echo Packing AVD images from $ANDROID_PRODUCT_OUT to $OUT_DIR

if [[ ! -d $OUT_DIR ]]; then
    mkdir $OUT_DIR
fi

echo System, qemu verion
cp $ANDROID_PRODUCT_OUT/system-qemu.img $OUT_DIR/system.img

echo Vendor, qemu verion
cp $ANDROID_PRODUCT_OUT/vendor-qemu.img $OUT_DIR/vendor.img


if [[ -f $ANDROID_PRODUCT_OUT/kernel-ranchu-64 ]]; then
    echo Kernel: 64-bit, prebuilt
    cp $ANDROID_PRODUCT_OUT/kernel-ranchu-64 $OUT_DIR/kernel-ranchu-64
else
    echo Kernel: 64-bit for 32-bit userspaces, prebuilt
    cp $ANDROID_PRODUCT_OUT/kernel-ranchu $OUT_DIR/kernel-ranchu
fi

echo Ramdisk, prebuilt
cp $ANDROID_PRODUCT_OUT/ramdisk-qemu.img $OUT_DIR/ramdisk.img

echo Encryptionkey, prebuilt
cp $ANDROID_PRODUCT_OUT/encryptionkey.img $OUT_DIR/encryptionkey.img

echo Userdata, prebuilt
# take prebuilt userdata.img
#Ref:https://cs.android.com/android/platform/superproject/+/master:development/build/sdk.atree?q=userdata.img&ss=android%2Fplatform%2Fsuperproject:development%2Fbuild%2F
cp $ANDROID_BUILD_TOP/device/generic/goldfish/data/etc/userdata.img $OUT_DIR/userdata.img

echo User data
#Todo: will this replace the need of userdata.img?
cp -r $ANDROID_PRODUCT_OUT/data $OUT_DIR/data

echo build properties
cp $ANDROID_PRODUCT_OUT/system/build.prop $OUT_DIR/build.prop

echo Verified Boot Parameters
cp $ANDROID_PRODUCT_OUT/VerifiedBootParams.textproto $OUT_DIR/VerifiedBootParams.textproto

echo Default AVD config.ini
cp $ANDROID_PRODUCT_OUT/config.ini $OUT_DIR/config.ini

echo Android Emulator Adviced Feature settings
cp $ANDROID_PRODUCT_OUT/advancedFeatures.ini $OUT_DIR/advancedFeatures.ini

echo
echo In $OUT_DIR:
ls -l $OUT_DIR

if [[ -n $AVD_IMAGE_ZIP ]]; then

    rm $AVD_IMAGE_ZIP

    echo Zipping $OUT_DIR to $AVD_IMAGE_ZIP
    # to perserve ABI in the zip, assuming OUT_DIR always ends with ABI
    # e.g. /path/to/x86_64
    ABI=${OUT_DIR##*/}  # = "x86_64"
    PATH_TO_IMG_DIR=${OUT_DIR%/$ABI}  # = "/path/to"
    cd $PATH_TO_IMG_DIR
    zip -r $AVD_IMAGE_ZIP $ABI
    cd -
    unzip -l $AVD_IMAGE_ZIP
fi
