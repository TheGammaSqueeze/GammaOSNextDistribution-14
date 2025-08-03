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

# Base platform for car builds
# car packages should be added to car.mk instead of here

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
    LatinIME \
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

# ENABLE_CAMERA_SERVICE must be set as true from the product's makefile if it wants to support
# Android Camera service.
ifneq ($(ENABLE_CAMERA_SERVICE), true)
PRODUCT_PROPERTY_OVERRIDES += config.disable_cameraservice=true
PRODUCT_PACKAGES += HideCameraApps
endif

# ENABLE_EVS_SERVICE must be set as true from the product's makefile if it wants to support
# the Extended View System service.
ifeq ($(ENABLE_EVS_SERVICE), true)
PRODUCT_PACKAGES += evsmanagerd

# CUSTOMIZE_EVS_SERVICE_PARAMETER must be set as true from the product's makefile if it wants
# to use IEvsEnumearor instances other than hw/1.
ifneq ($(CUSTOMIZE_EVS_SERVICE_PARAMETER), true)
PRODUCT_COPY_FILES += \
    packages/services/Car/cpp/evs/manager/aidl/init.evs.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/init.evs.rc
endif

ifeq ($(ENABLE_EVS_SAMPLE), true)
# ENABLE_EVS_SAMPLE should set be true or their vendor specific equivalents should be included in
# the device.mk with the corresponding selinux policies
PRODUCT_PACKAGES += evs_app \
                    android.hardware.automotive.evs-default \
                    cardisplayproxyd
include packages/services/Car/cpp/evs/apps/sepolicy/evsapp.mk
endif  # ENABLE_EVS_SAMPLE

ifeq ($(ENABLE_CAREVSSERVICE_SAMPLE), true)
PRODUCT_PACKAGES += CarEvsCameraPreviewApp
endif
ifeq ($(ENABLE_REAR_VIEW_CAMERA_SAMPLE), true)
PRODUCT_PACKAGES += SampleRearViewCamera
endif
# This is needed to be available to all builds because overlay config doesn't support optional overlays.
PRODUCT_PACKAGE_OVERLAYS += packages/services/Car/tests/SampleRearViewCamera/overlay

endif  # ENABLE_EVS_SERVICE

# Device running Android is a car
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.type.automotive.xml:system/etc/permissions/android.hardware.type.automotive.xml

# Default permission grant exceptions
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/build/preinstalled-packages-product-car-base.xml:system/etc/sysconfig/preinstalled-packages-product-car-base.xml

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_minimal.mk)

# Default dex optimization configurations
PRODUCT_PROPERTY_OVERRIDES += \
    pm.dexopt.disable_bg_dexopt=false \
    pm.dexopt.downgrade_after_inactive_days=10 \
    dalvik.vm.dex2oat-cpu-set=0,1 \
    dalvik.vm.dex2oat-threads=2

# Required init rc files for car
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/init/init.bootstat.rc:system/etc/init/init.bootstat.car.rc \
    packages/services/Car/car_product/init/init.car.rc:system/etc/init/init.car.rc

# Device policy management support
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.device_admin.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.device_admin.xml

# Enable car watchdog
include packages/services/Car/cpp/watchdog/product/carwatchdog.mk

# Enable car power policy
include packages/services/Car/cpp/powerpolicy/product/carpowerpolicy.mk

# Conditionally enable the telemetry service
ifeq ($(ENABLE_CARTELEMETRY_SERVICE), true)
include packages/services/Car/cpp/telemetry/cartelemetryd/products/telemetry.mk
endif

# Disable Dynamic System Update for automotive targets
PRODUCT_NO_DYNAMIC_SYSTEM_UPDATE := true
