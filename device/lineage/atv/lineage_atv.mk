#
# Copyright (C) 2019-2022 The LineageOS Project
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

# System properties
include $(LOCAL_PATH)/system_prop.mk

# Overlays
PRODUCT_PACKAGE_OVERLAYS += \
    device/lineage/atv/overlay

# Init files
PRODUCT_PACKAGES += \
    init.lineage.atv.rc

ifneq ($(TARGET_ATV_FORCE_1080_SCALING),false)
PRODUCT_PACKAGES += \
    init.lineage.atv.scaling.rc
endif

# Dynalink 4k
PRODUCT_PACKAGES += \
    Vendor_0110_Product_0508.idc \
    Vendor_0110_Product_0508.kl

# Onn remote
PRODUCT_PACKAGES += \
    Vendor_0957_Product_0005.idc \
    Vendor_0957_Product_0005.kl

# Sabrina remote
PRODUCT_PACKAGES += \
    Vendor_18d1_Product_9450.idc \
    Vendor_18d1_Product_9450.kl

# Stadia controller
PRODUCT_PACKAGES += \
    Vendor_18d1_Product_9400.kl

# IDCs for shield controllers
PRODUCT_PACKAGES += \
    Vendor_0955_Product_7212.idc \
    Vendor_0955_Product_7213.idc \
    Vendor_0955_Product_7214.idc \
    Vendor_0955_Product_7217.idc

# Key layouts for shield controllers
PRODUCT_PACKAGES += \
    Vendor_0955_Product_7212.kl \
    Vendor_0955_Product_7213.kl \
    Vendor_0955_Product_7214.kl \
    Vendor_0955_Product_7217.kl

# Steam Controller Wireless Adapter and BLE
PRODUCT_COPY_FILES += \
    frameworks/base/data/keyboards/Vendor_28de_Product_1102.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_28de_Product_1106.kl \
    frameworks/base/data/keyboards/Vendor_28de_Product_1102.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_28de_Product_1142.kl

# Key layouts for xbox controllers
PRODUCT_PACKAGES += \
    Vendor_045e_Product_0b05.kl \
    Vendor_045e_Product_0b13.kl

# ADT-2/3 reference remote idcs
PRODUCT_PACKAGES += \
    Vendor_000d_Product_3838.idc \
    Vendor_000d_Product_3839.idc \
    Vendor_7545_Product_0021.idc

# ADT-2/3 reference remote keylayouts
PRODUCT_PACKAGES += \
    Vendor_000d_Product_3838.kl \
    Vendor_000d_Product_3839.kl \
    Vendor_7545_Product_0021.kl

# Assorted remote keylayouts from ADT-3 build
PRODUCT_PACKAGES += \
    Vendor_0002_Product_0002.kl \
    Vendor_005d_Product_0001.kl \
    Vendor_005d_Product_0002.kl \
    Vendor_0484_Product_5738.kl \
    Vendor_0508_Product_0110.kl \
    Vendor_0957_Product_0006.kl \
    Vendor_0c45_Product_1109.kl \
    Vendor_1915_Product_0001.kl \
    Vendor_7045_Product_1820.kl \
    Vendor_7545_Product_0180.kl \
    Vendor_7545_Product_0190.kl

# Assorted idc's from ONN 2021 build
PRODUCT_PACKAGES += \
    Vendor_0508_Product_0110.idc \
    Vendor_0957_Product_0004.idc \
    Vendor_1d5a_Product_c082.idc \
    Vendor_248a_Product_8266.idc \
    Vendor_7545_Product_0180.idc

# Assorted remote keylayouts from ONN 2021 build
PRODUCT_PACKAGES += \
    Vendor_0957_Product_0004.kl \
    Vendor_1d5a_Product_c081.kl \
    Vendor_1d5a_Product_c082.kl \
    Vendor_248a_Product_8266.kl

# Overlays
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += \
    device/google/atv/overlay

# GMS RROs
PRODUCT_PACKAGES += \
    LineageGoogleSetupWraithOverlay \
    LineageGoogleSetupWraithPairingOverlay

# Settings
PRODUCT_PACKAGES += \
    TvSettingsTwoPanel

# TVLauncher
PRODUCT_PACKAGES += \
    TVLauncherNoGMS \
    TVRecommendationsNoGMS

# priv-app permissions
PRODUCT_COPY_FILES +=\
    device/lineage/atv/permissions/privapp-permissions-lineage-atv.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/privapp-permissions-lineage-atv.xml

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)
