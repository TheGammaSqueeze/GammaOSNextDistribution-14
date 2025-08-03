#
# Copyright (C) 2016 The Android Open-Source Project
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

# Common make file for all car builds

PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/public
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/private
ifeq ($(ENABLE_CARTELEMETRY_SERVICE), true)
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/cartelemetry
endif

PRODUCT_PACKAGES += \
    CarActivityResolver \
    CarDeveloperOptions \
    CarSettingsIntelligence \
    CarManagedProvisioning \
    CarProvision \
    StatementService \
    SystemUpdater

PRODUCT_PACKAGES += \
    screenrecord

# This is for testing
ifeq ($(TARGET_BUILD_VARIANT),eng)
PRODUCT_PACKAGES += \
    DefaultStorageMonitoringCompanionApp \
    EmbeddedKitchenSinkApp \
    GarageModeTestApp \
    ExperimentalCarService \
    BugReportApp \
    NetworkPreferenceApp \
    SampleCustomInputService \
    AdasLocationTestApp \
    curl \
    CarTelemetryApp \
    RailwayReferenceApp \
    CarHotwordDetectionServiceOne \
    KitchenSinkServerlessRemoteTaskClientRRO \

# SEPolicy for test apps / services
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/car_product/sepolicy/test
endif

# ClusterOsDouble is the testing app to test Cluster2 framework and it can handle Cluster VHAL
# and do some Cluster OS role.
ifeq ($(ENABLE_CLUSTER_OS_DOUBLE), true)
PRODUCT_PACKAGES += ClusterHomeSample ClusterOsDouble
else
# DirectRenderingCluster is the sample app for the old Cluster framework.
PRODUCT_PACKAGES += DirectRenderingCluster
endif  # ENABLE_CLUSTER_OS_DOUBLE

PRODUCT_PROPERTY_OVERRIDES += \
    ro.carrier=unknown \
    ro.hardware.type=automotive \

# Disable developer options activity embedding
PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.fflag.override.settings_support_large_screen=false

# Set default Bluetooth profiles
TARGET_SYSTEM_PROP += \
    packages/services/Car/car_product/properties/bluetooth.prop

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    config.disable_systemtextclassifier=true

###
### Suggested values for multi-user properties - can be overridden
###

# Enable headless system user mode
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.fw.mu.headless_system_user?=true

# Enable User HAL integration
# NOTE: when set to true, VHAL must also implement the user-related properties,
# otherwise CarService will ignore it
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    android.car.user_hal_enabled?=true

### end of multi-user properties ###

# Overlay for Google network and fused location providers
$(call inherit-product, device/sample/products/location_overlay.mk)
$(call inherit-product-if-exists, frameworks/webview/chromium/chromium.mk)
$(call inherit-product, packages/services/Car/car_product/build/car_base.mk)

# Window Extensions
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)

# Overrides
PRODUCT_BRAND := generic
PRODUCT_DEVICE := generic
PRODUCT_NAME := generic_car_no_telephony

PRODUCT_IS_AUTOMOTIVE := true

PRODUCT_PROPERTY_OVERRIDES := \
    ro.config.ringtone=Girtab.ogg \
    ro.config.notification_sound=Tethys.ogg \
    ro.config.alarm_alert=Oxygen.ogg \
    $(PRODUCT_PROPERTY_OVERRIDES) \

PRODUCT_PROPERTY_OVERRIDES += \
    keyguard.no_require_sim=true

# TODO(b/255631687): Enable the shell transition as soon as all CTS issues are resolved.
PRODUCT_SYSTEM_PROPERTIES += \
    persist.wm.debug.shell_transit=0

# TODO(b/198516172): Find a better location to add this read only property
# It is added here to check the functionality, will be updated in next CL
PRODUCT_SYSTEM_PROPERTIES += \
    ro.android.car.carservice.overlay.packages?=com.android.car.resources.vendor;com.google.android.car.resources.vendor;

# vendor layer can override this
PRODUCT_SYSTEM_PROPERTIES += \
    ro.android.car.carservice.package?=com.android.car.updatable

# Update with PLATFORM_VERSION_MINOR_INT update
PRODUCT_SYSTEM_PROPERTIES += ro.android.car.version.platform_minor=0

# Automotive specific packages
PRODUCT_PACKAGES += \
    CarFrameworkPackageStubs \
    CarService \
    CarShell \
    CarDialerApp \
    CarRadioApp \
    CarLauncher \
    CarSystemUI \
    LocalMediaPlayer \
    CarMediaApp \
    CarMessengerApp \
    CarHTMLViewer \
    CarMapsPlaceholder \
    CarLatinIME \
    CarSettings \
    CarUsbHandler \
    android.car.builtin \
    car-frameworks-service \
    libcarservicehelperjni \
    com.android.car.procfsinspector \
    com.android.permission \

# RROs
PRODUCT_PACKAGES += \
    CarPermissionControllerRRO \
    CarSystemUIRRO \

# CarSystemUIPassengerOverlay is an RRO package required for enabling unique look
# and feel for Passenger(Secondary) User.
ifeq ($(ENABLE_PASSENGER_SYSTEMUI_RRO), true)
PRODUCT_PACKAGES += CarSystemUIPassengerOverlay
endif  # ENABLE_PASSENGER_SYSTEMUI_RRO

# System Server components
# Order is important: if X depends on Y, then Y should precede X on the list.
PRODUCT_SYSTEM_SERVER_JARS += car-frameworks-service
# TODO: make the order optimal by appending 'car-frameworks-service' at the end
# after its dependency 'services'. Currently the order is violated because this
# makefile is included before AOSP makefile.
PRODUCT_BROKEN_SUBOPTIMAL_ORDER_OF_SYSTEM_SERVER_JARS := true

# Boot animation
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/bootanimations/bootanimation-832.zip:system/media/bootanimation.zip

PRODUCT_LOCALES := \
    en_US \
    af_ZA \
    am_ET \
    ar_EG ar_XB \
    as_IN \
    az_AZ \
    be_BY \
    bg_BG \
    bn_BD \
    bs_BA \
    ca_ES \
    cs_CZ \
    da_DK \
    de_DE \
    el_GR \
    en_AU en_CA en_GB en_IN en_XA \
    es_ES es_US \
    et_EE \
    eu_ES \
    fa_IR \
    fi_FI \
    fil_PH \
    fr_CA fr_FR \
    gl_ES \
    gu_IN \
    hi_IN \
    hr_HR \
    hu_HU \
    hy_AM \
    id_ID \
    is_IS \
    it_IT \
    iw_IL \
    ja_JP \
    ka_GE \
    kk_KZ \
    km_KH km_MH \
    kn_IN \
    ko_KR \
    ky_KG \
    lo_LA \
    lv_LV \
    lt_LT \
    mk_MK \
    ml_IN \
    mn_MN \
    mr_IN \
    ms_MY \
    my_MM \
    ne_NP \
    nl_NL \
    no_NO \
    or_IN \
    pa_IN \
    pl_PL \
    pt_BR pt_PT \
    ro_RO \
    ru_RU \
    si_LK \
    sk_SK \
    sl_SI \
    sq_AL \
    sr_RS \
    sv_SE \
    sw_TZ \
    ta_IN \
    te_IN \
    th_TH \
    tr_TR \
    uk_UA \
    ur_PK \
    uz_UZ \
    vi_VN \
    zh_CN zh_HK zh_TW \
    zu_ZA

PRODUCT_BOOT_JARS += \
    android.car.builtin

USE_CAR_FRAMEWORK_APEX ?= false

ifeq ($(USE_CAR_FRAMEWORK_APEX),true)
    PRODUCT_PACKAGES += com.android.car.framework

    PRODUCT_APEX_BOOT_JARS += com.android.car.framework:android.car-module
    PRODUCT_APEX_SYSTEM_SERVER_JARS += com.android.car.framework:car-frameworks-service-module

    $(call soong_config_set,bootclasspath,car_bootclasspath_fragment,true)

    PRODUCT_HIDDENAPI_STUBS := android.car-module.stubs
    PRODUCT_HIDDENAPI_STUBS_SYSTEM := android.car-module.stubs.system
    PRODUCT_HIDDENAPI_STUBS_TEST := android.car-module.stubs.test
else # !USE_CAR_FRAMEWORK_APEX
    PRODUCT_BOOT_JARS += android.car
    PRODUCT_PACKAGES += android.car CarServiceUpdatableNonModule car-frameworks-service-module
    PRODUCT_SYSTEM_SERVER_JARS += car-frameworks-service-module

    PRODUCT_HIDDENAPI_STUBS := android.car-stubs-dex
    PRODUCT_HIDDENAPI_STUBS_SYSTEM := android.car-system-stubs-dex
    PRODUCT_HIDDENAPI_STUBS_TEST := android.car-test-stubs-dex
endif # USE_CAR_FRAMEWORK_APEX

# Disable Prime Shader Cache in SurfaceFlinger to make it available faster
PRODUCT_PROPERTY_OVERRIDES += \
    service.sf.prime_shader_cache=0
