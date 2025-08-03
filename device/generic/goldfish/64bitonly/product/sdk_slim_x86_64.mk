#
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
#
PRODUCT_USE_DYNAMIC_PARTITIONS := true
# This is a build configuration for the 'slim' image targeted
# for headless automated testing. Compared to the full AOSP 'sdk_phone'
# image it removes/replaces most product apps, and turns off rendering
# by default.
#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)
# Enable mainline checking for exact this product name
ifeq (sdk_slim_x86_64,$(TARGET_PRODUCT))
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
endif
#
# All components inherited here go to system_ext image
#
# don't include full handheld_system_Ext which includes SystemUi, Settings etc
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)
#
# All components inherited here go to product image
#
# Just include webview, do not include most other apps
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_product.mk)
# Include FakeSystemApp which replaces core system apps like Settings,
# Launcher
PRODUCT_PACKAGES += \
    FakeSystemApp \
#
# All components inherited here go to vendor image
#
# this must go first - overwrites the goldfish handheld_core_hardware.xml
$(call inherit-product, device/generic/goldfish/slim/vendor.mk)
$(call inherit-product, device/generic/goldfish/board/emu64x/details.mk)
$(call inherit-product, device/generic/goldfish/64bitonly/product/emulator64_vendor.mk)

# include the overlay that overrides systemui definitions with fakesystemapp
PRODUCT_PACKAGES += slim_overlay_frameworks_base_core

PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := \
    development/sys-img/images_atd_source.prop_template

# Overrides
PRODUCT_BRAND := Android
PRODUCT_NAME := sdk_slim_x86_64
PRODUCT_DEVICE := emu64x
PRODUCT_MODEL := Android SDK built for x86_64
# Disable <uses-library> checks for SDK product. It lacks some libraries (e.g.
# RadioConfigLib), which makes it impossible to translate their module names to
# library name, so the check fails.
PRODUCT_BROKEN_VERIFY_USES_LIBRARIES := true
