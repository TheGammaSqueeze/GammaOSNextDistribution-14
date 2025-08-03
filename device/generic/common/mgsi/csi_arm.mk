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

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

#
# All components inherited here go to system_ext image
#
# VNDK snapshot is needed to support older vendor images
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_system_ext.mk)

#
# All components below go to product image
#

#
# Special settings to skip mount product and system_ext on the device,
# so this product can be tested isolated from those partitions.
#
$(call inherit-product, device/generic/common/mgsi/mgsi_release.mk)

# Don't build super.img.
PRODUCT_BUILD_SUPER_PARTITION := false

# Instruct AM to enable framework's fallback home activity
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.system_user_home_needed=true
# Add RRO needed by CSI
PRODUCT_PACKAGE_OVERLAYS := device/generic/common/mgsi/overlay

PRODUCT_SOONG_NAMESPACES += device/generic/goldfish

PRODUCT_NAME := csi_arm
PRODUCT_DEVICE := dummy_arm
PRODUCT_BRAND := Android
PRODUCT_MODEL := arm CSI
