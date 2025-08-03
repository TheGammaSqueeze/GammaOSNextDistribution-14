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
PRODUCT_USE_DYNAMIC_PARTITIONS := true
EMULATOR_DISABLE_RADIO := true

# 1.5G + 8M
BOARD_SUPER_PARTITION_SIZE := 1619001344
BOARD_EMULATOR_DYNAMIC_PARTITIONS_SIZE := 1610612736

PRODUCT_COPY_FILES += \
    device/generic/goldfish/tablet/data/etc/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml \
    device/generic/goldfish/data/etc/advancedFeatures.ini.tablet:advancedFeatures.ini \
    device/generic/goldfish/data/etc/config.ini.nexus7tab:config.ini

PRODUCT_COPY_FILES+= \
        device/generic/goldfish/data/etc/tablet_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml

PRODUCT_COPY_FILES += device/generic/goldfish/tablet/data/etc/tablet.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/tablet.xml

PRODUCT_CHARACTERISTICS := tablet,nosdcard


#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking for excat this product name
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed


#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)

#
# All components inherited here go to product image
#
# Includes all AOSP product packages
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_product.mk)

# Default AOSP sounds
$(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)

# Additional settings used in all AOSP builds
PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.ringtone?=Ring_Synth_04.ogg \
    ro.config.notification_sound?=pixiedust.ogg \


# More AOSP packages
PRODUCT_PACKAGES += \
    initial-package-stopped-states-aosp.xml \
    PhotoTable \
    preinstalled-packages-platform-aosp-product.xml \
    WallpaperPicker \


# Window Extensions
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)

# Other packages for virtual device testing.
PRODUCT_PACKAGES += \
    LargeScreenSettingsProviderOverlay \
    curl \

PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += system/bin/curl

PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := \
    device/generic/goldfish/64bitonly/product/tablet_images_arm64-v8a_source.prop_template

#
# All components inherited here go to vendor image
#
$(call inherit-product, device/generic/goldfish/board/emu64a/details.mk)
$(call inherit-product, device/generic/goldfish/64bitonly/product/emulator64_vendor.mk)

# Overrides
PRODUCT_BRAND := Android
PRODUCT_NAME := sdk_tablet_arm64
PRODUCT_DEVICE := emu64a
PRODUCT_MODEL := Android SDK Tablet for arm64

