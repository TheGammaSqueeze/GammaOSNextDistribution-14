#
# Copyright (C) 2023 The Android Open Source Project
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

include device/generic/goldfish/board/kernel/arm64.mk

PRODUCT_PROPERTY_OVERRIDES += \
       vendor.rild.libpath=/vendor/lib64/libgoldfish-ril.so

PRODUCT_COPY_FILES += \
    device/generic/goldfish/board/fstab/arm:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/first_stage_ramdisk/fstab.ranchu \
    device/generic/goldfish/board/fstab/arm:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.ranchu \
    $(EMULATOR_KERNEL_FILE):kernel-ranchu \
    device/generic/goldfish/data/etc/advancedFeatures.ini:advancedFeatures.ini \
