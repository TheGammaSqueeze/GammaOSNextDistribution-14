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

# This makefile comprises the minimal product partition content for an
# automotive device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_product.mk)

# Default AOSP sounds
$(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)

PRODUCT_PACKAGES += \
    CarSettingsIntelligence

# Additional settings for AAOS builds
PRODUCT_PRODUCT_PROPERTIES += \
    ro.com.android.dataroaming?=true \
    ro.config.ringtone=Girtab.ogg \
    ro.config.notification_sound=Tethys.ogg \
    ro.config.alarm_alert=Oxygen.ogg \

# More AOSP packages
PRODUCT_PACKAGES += \
    messaging \
    PhotoTable \
    preinstalled-packages-platform-aosp-product.xml \
    WallpaperPicker \

PRODUCT_PACKAGES_DEBUG += \
    KitchenSinkServerlessRemoteTaskClientRRO \

PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/public
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/private
PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/cpp/powerpolicy/sepolicy/public
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/powerpolicy/sepolicy/private
PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/cpp/watchdog/sepolicy/public
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/watchdog/sepolicy/private

ifeq ($(ENABLE_CARTELEMETRY_SERVICE), true)
PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/cpp/telemetry/cartelemetryd/sepolicy/public
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/telemetry/cartelemetryd/sepolicy/private
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/cartelemetry
endif

ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
# SEPolicy for test apps/services
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/test
endif

ifeq ($(DISABLE_CAR_PRODUCT_CONFIG_OVERLAY),)
PRODUCT_PACKAGES += \
    CarFrameworkResConfigRRO \
    CarCertInstallerConfigRRO \
    CarSettingsProviderConfigRRO \
    CarTelecommConfigRRO
endif

ifeq ($(DISABLE_CAR_PRODUCT_VISUAL_OVERLAY),)
PRODUCT_PACKAGE_OVERLAYS += packages/services/Car/car_product/overlay-visual
PRODUCT_PACKAGES += CarFrameworkResVisualRRO
endif

# CarSystemUIPassengerOverlay is an RRO package required for enabling unique look
# and feel for Passenger(Secondary) User.
ifeq ($(ENABLE_PASSENGER_SYSTEMUI_RRO), true)
PRODUCT_PACKAGES += CarSystemUIPassengerOverlay
endif  # ENABLE_PASSENGER_SYSTEMUI_RRO

ifneq (,$(filter true,$(ENABLE_EVS_SAMPLE) $(ENABLE_SAMPLE_EVS_APP)))
include packages/services/Car/cpp/evs/apps/sepolicy/evsapp.mk
endif

$(call inherit-product, device/sample/products/location_overlay.mk)
$(call inherit-product, packages/services/Car/car_product/rro/ThemeSamples/product.mk)
