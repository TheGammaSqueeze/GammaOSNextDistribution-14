#
# Copyright (C) 2022 The Android Open Source Project
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

# Declare here to not install modules which are not supported on TV
PRODUCT_IS_ATV := true

#
# All components inherited here go to system image
#
$(call inherit-product, device/google/atv/products/atv_generic_system.mk)

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := true

#
# All components inherited here go to system_ext image
#
$(call inherit-product, device/google/atv/products/atv_system_ext.mk)

# Packages required for ATV GSI
PRODUCT_PACKAGES += \
    TvProvision

# Install a copy of the debug policy to the system_ext partition, and allow
# init-second-stage to load debug policy from system_ext.
# This option is only meant to be set by compliance GSI targets.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := true
PRODUCT_PACKAGES += system_ext_userdebug_plat_sepolicy.cil

#
# All components inherited here go to product image
#
$(call inherit-product, device/google/atv/products/atv_product.mk)
# Default sound experience
$(call inherit-product-if-exists, frameworks/base/data/sounds/AudioTv.mk)
# Packages required for ATV GSI
PRODUCT_PACKAGES += \
    LeanbackIME \
    TvSampleLeanbackLauncher
# Specify product type
PRODUCT_CHARACTERISTICS := tv

#
# Special settings for GSI releasing
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)
