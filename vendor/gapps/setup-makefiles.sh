#!/bin/bash
#
# Copyright (C) 2016 The CyanogenMod Project
# Copyright (C) 2017-2021 The LineageOS Project
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

export DEVICE_COMMON=common
export VENDOR=gapps

# Load extract_utils and do some sanity checks
MY_DIR="${BASH_SOURCE%/*}"
if [[ ! -d "$MY_DIR" ]]; then MY_DIR="$PWD"; fi

ANDROID_ROOT="$MY_DIR/../.."

export TARGET_ENABLE_CHECKELF=true

HELPER="$ANDROID_ROOT/tools/extract-utils/extract_utils.sh"
if [ ! -f "$HELPER" ]; then
    echo "Unable to find helper script at $HELPER"
    exit 1
fi
. "$HELPER"

# Initialize the helper for common gapps
setup_vendor "$DEVICE_COMMON" "$VENDOR" "$ANDROID_ROOT" true

# Copyright headers
write_headers "arm arm64 x86_64"

# Common gapps
write_makefiles "$MY_DIR"/proprietary-files-common.txt

# Gapps that are too large for grouper
printf "\n" >> "$PRODUCTMK"
echo "ifeq (\$(TARGET_IS_GROUPER),)" >> "$PRODUCTMK"
write_makefiles "$MY_DIR"/proprietary-files-common-nongrouper.txt
echo "endif" >> "$PRODUCTMK"

# Overlays
cd overlay
OVERLAYS=$(for dir in $(ls -d */); do echo ${dir%%/}; done)
OVERLAYS=$(echo $OVERLAYS | paste -s -d ' ')
cd - >/dev/null
printf "\n" >> "$PRODUCTMK"
echo "PRODUCT_SOONG_NAMESPACES += vendor/$VENDOR/overlay" >> "$PRODUCTMK"
echo "PRODUCT_PACKAGES += $OVERLAYS" >> "$PRODUCTMK"

sed -i 's/TARGET_DEVICE/TARGET_ARCH/g' "$ANDROIDMK"

# We are done with common
write_footers

for DEVICE in arm arm64 x86_64; do

# Reinitialize the helper for target gapps
setup_vendor "$DEVICE" "$VENDOR" "$ANDROID_ROOT"

# Copyright headers and guards
write_headers "$DEVICE"

write_makefiles "$MY_DIR"/proprietary-files-$DEVICE.txt

# Gapps that are too large for grouper
printf "\n" >> "$PRODUCTMK"
echo "ifeq (\$(TARGET_IS_GROUPER),)" >> "$PRODUCTMK"
write_makefiles "$MY_DIR"/proprietary-files-$DEVICE-nongrouper.txt
echo "endif" >> "$PRODUCTMK"

# Workaround for VelvetTitan on tangorpro
if grep -q "VelvetTitan" "$PRODUCTMK"; then
    sed -i '/VelvetTitan/d' "$PRODUCTMK"
    printf "\n" >> "$PRODUCTMK"
    echo "ifneq (\$(filter %tangorpro,\$(TARGET_PRODUCT)),)" >> "$PRODUCTMK"
    echo "PRODUCT_PACKAGES += VelvetTitan" >> "$PRODUCTMK"
    echo "endif" >> "$PRODUCTMK"
fi

printf '\n%s\n' "\$(call inherit-product, vendor/gapps/common/common-vendor.mk)" >> "$PRODUCTMK"

sed -i 's/TARGET_DEVICE/TARGET_ARCH/g' "$ANDROIDMK"

# We are done with target
write_footers

done
