#
# Copyright (C) 2021 The Android Open-Source Project
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

$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-customizations/product.mk)
$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-toolbar-customizations/product.mk)
$(call inherit-product-if-exists, vendor/auto/embedded/products/coolwhip/car-ui-lib-rros/product.mk)
$(call inherit-product-if-exists, vendor/google/nexus_overlay/fonts/fonts.mk)

# All RROs to be included in car_ui_portrait builds.
PRODUCT_PACKAGES += \
    CarEvsCameraPreviewAppRRO \
    CarUiPortraitAppGridReferenceRRO \
    CarUiPortraitCarServiceRRO \
    CarUiPortraitCommon \
    CarUiPortraitDialerRRO \
    CarUiPortraitFrameworkResRRO \
    CarUiPortraitLauncherAppsRRO \
    CarUiPortraitLauncherMediaRRO \
    CarUiPortraitLauncherReferenceRRO \
    CarUiPortraitMediaCommonRRO \
    CarUiPortraitMediaRRO \
    CarUiPortraitMessengerRRO \
    CarUiPortraitNotificationRRO \
    CarUiPortraitRadioRRO \
    CarUiPortraitSettingsRRO \

ifneq ($(INCLUDE_SEAHAWK_ONLY_RROS),)
PRODUCT_PACKAGES += \
    CarUiPortraitSettingsProviderRRO
endif

# Set necessary framework configs for SUW to run at boot.
ifneq ($(filter $(TARGET_PRODUCT), gcar_portrait_suw),)
PRODUCT_PACKAGES += \
    CarUiPortraitSettingsProviderEmuRRO
endif

PRODUCT_PRODUCT_PROPERTIES += \
    car.ui.config=rickybobby

PORTRAIT_RRO_PACKAGES := com.android.car.calendar.googlecaruiportrait.rro; \
    com.android.car.carlauncher.googlecaruiportrait.rro; \
    com.android.car.developeroptions.googlecaruiportrait.rro; \
    com.android.car.dialer.googlecaruiportrait.rro; \
    com.android.car.dialer.googlecaruiportrait.toolbar.rro; \
    com.android.car.faceenroll.googlecaruiportrait.rro; \
    com.android.car.home.googlecaruiportrait.rro; \
    com.android.car.linkviewer.googlecaruiportrait.rro; \
    com.android.car.media.googlecaruiportrait.rro; \
    com.android.car.media.googlecaruiportrait.toolbar.rro; \
    com.android.car.messenger.googlecaruiportrait.rro; \
    com.android.car.messenger.googlecaruiportrait.toolbar.rro; \
    com.android.car.portraitlauncher.googlecaruiportrait.rro; \
    com.android.car.radio.googlecaruiportrait.rro; \
    com.android.car.radio.googlecaruiportrait.toolbar.rro; \
    com.android.car.rotaryplayground.googlecaruiportrait.rro; \
    com.android.car.settings.googlecaruiportrait.rro; \
    com.android.car.systemupdater.googlecaruiportrait.rro; \
    com.android.car.themeplayground.googlecaruiportrait.rro; \
    com.android.car.ui.paintbooth.googlecaruiportrait.rro; \
    com.android.car.ui.paintbooth.googlecaruiportrait.rro; \
    com.android.car.voicecontrol.googlecaruiportrait.rro; \
    com.android.htmlviewer.googlecaruiportrait.rro; \
    com.android.managedprovisioning.googlecaruiportrait.rro; \
    com.android.permissioncontroller.googlecaruiportrait.rro; \
    com.android.settings.intelligence.googlecaruiportrait.rro; \
    com.android.vending.googlecaruiportrait.rro; \
    com.chassis.car.ui.plugin.googlecaruiportrait.rro; \
    com.google.android.apps.automotive.inputmethod.dev.googlecaruiportrait.rro; \
    com.google.android.apps.automotive.inputmethod.googlecaruiportrait.rro; \
    com.google.android.apps.automotive.templates.host.googlecaruiportrait.rro; \
    com.google.android.carassistant.googlecaruiportrait.rro; \
    com.google.android.carui.ats.googlecaruiportrait.rro; \
    com.google.android.companiondevicesupport.googlecaruiportrait.rro; \
    com.google.android.embedded.projection.googlecaruiportrait.rro; \
    com.google.android.gms.googlecaruiportrait.rro; \
    com.google.android.gsf.googlecaruiportrait.rro; \
    com.google.android.packageinstaller.googlecaruiportrait.rro; \
    com.google.android.permissioncontroller.googlecaruiportrait.rro; \
    com.google.android.tts.googlecaruiportrait.rro


PRODUCT_PROPERTY_OVERRIDES += \
    ro.boot.vendor.overlay.theme=$(subst $(space),,$(PORTRAIT_RRO_PACKAGES))