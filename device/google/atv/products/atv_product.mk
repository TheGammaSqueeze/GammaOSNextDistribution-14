#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This makefile contains the product partition contents for
# a generic TV device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_product.mk)

$(call inherit-product-if-exists, frameworks/base/data/sounds/AudioTv.mk)

PRODUCT_PUBLIC_SEPOLICY_DIRS += device/google/atv/audio_proxy/sepolicy/public

PRODUCT_PACKAGES += \
    SettingsIntelligence \
    TvFrameworkOverlay \
    TvSettingsProviderOverlay \
    TvWifiOverlay

PRODUCT_COPY_FILES += \
    device/google/atv/atv-component-overrides.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/atv-component-overrides.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.gamepad.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/android.hardware.gamepad.xml

# Too many tombstones can cause bugreports to grow too large to be uploaded.
PRODUCT_PRODUCT_PROPERTIES += \
    tombstoned.max_tombstone_count?=10
