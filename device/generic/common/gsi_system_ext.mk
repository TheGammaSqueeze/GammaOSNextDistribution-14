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

# This makefile contains the system_ext partition contents for CTS on
# GSI compliance testing. Only add something here for this purpose.
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_system_ext.mk)

#  handheld packages
PRODUCT_PACKAGES += \
    Launcher3QuickStep \
    Provision \
    Settings \
    StorageManager \
    SystemUI

#  telephony packages
PRODUCT_PACKAGES += \
    CarrierConfig

# Add all of the packages used to support older/upgrading devices
# These can be removed as we drop support for the older API levels
PRODUCT_PACKAGES += \
    $(PRODUCT_PACKAGES_SHIPPING_API_LEVEL_29) \
    $(PRODUCT_PACKAGES_SHIPPING_API_LEVEL_33) \
    $(PRODUCT_PACKAGES_SHIPPING_API_LEVEL_34)

# Install a copy of the debug policy to the system_ext partition, and allow
# init-second-stage to load debug policy from system_ext.
# This option is only meant to be set by compliance GSI targets.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := true
PRODUCT_PACKAGES += system_ext_userdebug_plat_sepolicy.cil
