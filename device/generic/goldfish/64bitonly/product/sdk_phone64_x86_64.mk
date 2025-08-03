#
# Copyright (C) 2009 The Android Open Source Project
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

# 1.5G + 8M
BOARD_SUPER_PARTITION_SIZE ?= 1619001344
BOARD_EMULATOR_DYNAMIC_PARTITIONS_SIZE ?= 1610612736

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking for excat this product name
ifeq (sdk_phone64_x86_64,$(TARGET_PRODUCT))
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
endif

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := \
    development/sys-img/images_x86_64_source.prop_template

PRODUCT_PACKAGES += \
    GoldfishSkinConfig

#
# All components inherited here go to vendor image
#
$(call inherit-product, device/generic/goldfish/board/emu64x/details.mk)
$(call inherit-product, device/generic/goldfish/64bitonly/product/emulator64_vendor.mk)

# Overrides
PRODUCT_BRAND := Android
PRODUCT_NAME := sdk_phone64_x86_64
PRODUCT_DEVICE := emu64x
PRODUCT_MODEL := Android SDK built for x86_64
