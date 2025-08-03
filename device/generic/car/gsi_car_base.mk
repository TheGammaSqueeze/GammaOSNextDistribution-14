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

PRODUCT_PACKAGE_OVERLAYS := device/generic/car/common/overlay
EMULATOR_VENDOR_NO_SENSORS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true
DO_NOT_INCLUDE_BT_SEPOLICY := true
EMULATOR_VENDOR_NO_SOUND := true

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, packages/services/Car/car_product/build/car_generic_system.mk)

PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := strict

#
# All components inherited here go to system_ext image
#
$(call inherit-product, packages/services/Car/car_product/build/car_system_ext.mk)

# Install a copy of the debug policy to the system_ext partition, and allow
# init-second-stage to load debug policy from system_ext.
# This option is only meant to be set by compliance GSI targets.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := true
PRODUCT_PACKAGES += system_ext_userdebug_plat_sepolicy.cil

# pKVM is required to support nested virtualization for CF. Ideally we should
# move it out of /system. But it seems to be infeasible for now (b/207336449).
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, packages/services/Car/car_product/build/car_product.mk)

PRODUCT_BRAND := Android
#
# Special settings for GSI releasing
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
