#
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
#

# Generate source.properties for image target
PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := \
    device/google/atv/sdk/images_source.prop_template

PRODUCT_USE_DYNAMIC_PARTITIONS := true

# The system image of aosp_tv_x86-userdebug is a GSI for the devices with:
# - x86 32 bits user space
# - 64 bits binder interface
# - VNDK enforcement
# - compatible property override enabled

#
# All components inherited here go to system image
#
$(call inherit-product, device/google/atv/products/atv_generic_system.mk)

# Enable mainline checking for excat this product name
ifeq (aosp_tv_x86,$(TARGET_PRODUCT))
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
endif

#
# All components inherited here go to system_ext image
#
$(call inherit-product, device/google/atv/products/atv_system_ext.mk)
# Packages required for ATV GSI
PRODUCT_PACKAGES += \
    TvProvision

#
# All components inherited here go to product image
#
$(call inherit-product, device/google/atv/products/atv_product.mk)
# Default sound experience
$(call inherit-product-if-exists, frameworks/base/data/sounds/AudioTv.mk)
# Packages required for ATV GSI
ifeq (aosp_tv_x86,$(TARGET_PRODUCT))
PRODUCT_PACKAGES += \
    LeanbackIME \
    TvSampleLeanbackLauncher
endif

#
# All components inherited here go to vendor image
#
$(call inherit-product, device/google/atv/products/atv_emulator_vendor.mk)
$(call inherit-product, device/generic/goldfish/board/emu64x/kernel_fstab_32.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/board/generic_x86/device.mk)

ifeq (aosp_tv_x86,$(TARGET_PRODUCT))
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
endif

PRODUCT_NAME := aosp_tv_x86
PRODUCT_DEVICE := generic_x86
PRODUCT_BRAND := Android
PRODUCT_MODEL := AOSP TV on x86
