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

# This makefile comprises the minimal system partition content for an
# automotive device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_default.mk)
# Add adb keys to debuggable AOSP builds (if they exist)
$(call inherit-product-if-exists, vendor/google/security/adb/vendor_key.mk)

# If your device needs telephony stack for mobile network, please include
# telephony_system.mk and APN configs in your device makefile.

# Enable updating of APEXes
$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)

# Applications
PRODUCT_PACKAGES += \
    LiveWallpapersPicker \
    PartnerBookmarksProvider \
    preinstalled-packages-platform-generic-system.xml \
    Stk \
    Tag \

# OTA support
PRODUCT_PACKAGES += \
    recovery-refresh \
    update_engine \
    update_verifier \

# Wrapped net utils for /vendor access.
PRODUCT_PACKAGES += netutils-wrapper-1.0

# Charger images
PRODUCT_PACKAGES += charger_res_images

# system_other support
PRODUCT_PACKAGES += \
    cppreopts.sh \
    otapreopt_script \

# For ringtones that rely on forward lock encryption
PRODUCT_PACKAGES += libfwdlockengine

# System libraries commonly depended on by things on the system_ext or product partitions.
# These lists will be pruned periodically.
PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint@2.1 \
    android.hardware.radio@1.0 \
    android.hardware.radio@1.1 \
    android.hardware.radio@1.2 \
    android.hardware.radio@1.3 \
    android.hardware.radio@1.4 \
    android.hardware.radio.config@1.0 \
    android.hardware.radio.deprecated@1.0 \
    android.hardware.secure_element@1.0 \
    android.hardware.wifi \
    libaudio-resampler \
    libaudiohal \
    libdrm \
    liblogwrap \
    liblz4 \
    libminui \
    libnl \
    libprotobuf-cpp-full \

# These libraries are empty and have been combined into libhidlbase, but are still depended
# on by things off /system.
# TODO(b/135686713): remove these
PRODUCT_PACKAGES += \
    libhidltransport \
    libhwbinder \

PRODUCT_HOST_PACKAGES += \
    tinyplay

# Enable configurable audio policy
PRODUCT_PACKAGES += \
    libaudiopolicyengineconfigurable \
    libpolicy-subsystem

# Include all zygote init scripts. "ro.zygote" will select one of them.
PRODUCT_COPY_FILES += \
    system/core/rootdir/init.zygote32.rc:system/etc/init/hw/init.zygote32.rc \
    system/core/rootdir/init.zygote64.rc:system/etc/init/hw/init.zygote64.rc \
    system/core/rootdir/init.zygote64_32.rc:system/etc/init/hw/init.zygote64_32.rc \

# Enable dynamic partition size
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true

PRODUCT_ENFORCE_RRO_TARGETS := *

PRODUCT_PACKAGES += \
    CarActivityResolver \
    CarManagedProvisioning \
    StatementService \
    SystemUpdater \
    screenrecord

# Set default Bluetooth profiles
TARGET_SYSTEM_PROP := \
    packages/services/Car/car_product/properties/bluetooth.prop

PRODUCT_SYSTEM_PROPERTIES += \
    config.disable_systemtextclassifier=true

###
### Suggested values for multi-user properties - can be overridden
###

# Enable headless system user mode
PRODUCT_SYSTEM_PROPERTIES += \
    ro.fw.mu.headless_system_user?=true

# Enable User HAL integration
# NOTE: when set to true, VHAL must also implement the user-related properties,
# otherwise CarService will ignore it
PRODUCT_SYSTEM_PROPERTIES += \
    android.car.user_hal_enabled?=true

### end of multi-user properties ###

# TODO(b/255631687): Enable the shell transition as soon as all CTS issues are resolved.
PRODUCT_SYSTEM_PROPERTIES += \
    persist.wm.debug.shell_transit=0

# TODO(b/198516172): Find a better location to add this read only property
# It is added here to check the functionality, will be updated in next CL
PRODUCT_SYSTEM_PROPERTIES += \
    ro.android.car.carservice.overlay.packages?=com.android.car.resources.vendor;com.google.android.car.resources.vendor;

# Vendor layer can override this
PRODUCT_SYSTEM_PROPERTIES += \
    ro.android.car.carservice.package?=com.android.car.updatable

# Update with PLATFORM_VERSION_MINOR_INT update
PRODUCT_SYSTEM_PROPERTIES += ro.android.car.version.platform_minor=0

PRODUCT_PACKAGES += \
    com.android.wifi \
    Home \
    BasicDreams \
    CaptivePortalLogin \
    CertInstaller \
    DownloadProviderUi \
    FusedLocation \
    InputDevices \
    KeyChain \
    PacProcessor \
    PrintSpooler \
    ProxyHandler \
    Settings \
    SharedStorageBackup \
    VpnDialogs \
    MmsService \
    ExternalStorageProvider \
    atrace \
    libandroidfw \
    libaudioutils \
    libmdnssd \
    libpowermanager \
    PackageInstaller \
    carbugreportd \
    vehicle_binding_util \

# Device running Android is a car
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.type.automotive.xml:system/etc/permissions/android.hardware.type.automotive.xml

# Default permission grant exceptions
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/build/preinstalled-packages-product-car-base.xml:system/etc/sysconfig/preinstalled-packages-product-car-base.xml

# Required init rc files for car
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/init/init.bootstat.rc:system/etc/init/init.bootstat.car.rc \
    packages/services/Car/car_product/init/init.car.rc:system/etc/init/init.car.rc

# Device policy management support
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.device_admin.xml:system/etc/permissions/android.software.device_admin.xml

# Enable car watchdog
PRODUCT_PACKAGES += carwatchdogd

# Enable car power policy
PRODUCT_PACKAGES += carpowerpolicyd

PRODUCT_IS_AUTOMOTIVE := true

PRODUCT_PACKAGES += \
    CarFrameworkPackageStubs \
    CarService \
    CarShell \
    CarDialerApp \
    CarRadioApp \
    CarLauncher \
    LocalMediaPlayer \
    CarMediaApp \
    CarMessengerApp \
    CarHTMLViewer \
    CarMapsPlaceholder \
    CarLatinIME \
    CarSettings \
    CarUsbHandler \
    android.car.builtin \
    libcarservicehelperjni \
    car-frameworks-service \
    com.android.car.procfsinspector \
    com.android.permission \

# CAN bus
PRODUCT_PACKAGES += \
    canhalctrl \
    canhaldump \
    canhalsend

# RROs
PRODUCT_PACKAGES += \
    CarPermissionControllerRRO \
    CarSystemUIRRO

$(call inherit-product-if-exists, packages/apps/Car/SystemUI/samples/systemui_sample_rros.mk)

# System Server components
# Order is important: if X depends on Y, then Y should precede X on the list.
PRODUCT_SYSTEM_SERVER_JARS += car-frameworks-service

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

# Disable Dynamic System Update for automotive targets
PRODUCT_NO_DYNAMIC_SYSTEM_UPDATE := true

