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

# The system image of gsi_arm64-userdebug is the GSI for devices with:
# - ARM 64-bit userspace
# - 64-bit binder interface
# - system-as-root
# - VNDK enforcement
# - compatible property override enabled

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := true

# Always build modules from source
MODULE_BUILD_FROM_SOURCE := true

#
# All components inherited here go to system_ext image
#
$(call inherit-product, device/generic/common/gsi_system_ext.mk)

# pKVM
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, device/generic/common/gsi_product.mk)

#
# Special settings for GSI releasing
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)


PRODUCT_NAME := gsi_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := Android
PRODUCT_MODEL := GSI on ARM64
