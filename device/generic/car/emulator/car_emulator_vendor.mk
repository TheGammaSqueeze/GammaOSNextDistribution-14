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

$(call inherit-product, packages/services/Car/car_product/build/car_vendor.mk)

# Need this for gles libraries to load properly
# after moving to /vendor/lib/
PRODUCT_PACKAGES += \
    vndk-sp

DEVICE_PACKAGE_OVERLAYS := device/generic/goldfish/overlay

PRODUCT_CHARACTERISTICS := emulator

PRODUCT_FULL_TREBLE_OVERRIDE := true

# Enable Google-specific location features,
# like NetworkLocationProvider and LocationCollector
PRODUCT_VENDOR_PROPERTIES += \
    ro.com.google.locationfeatures=1

# Enable setupwizard
PRODUCT_VENDOR_PROPERTIES += \
    ro.setupwizard.mode?=OPTIONAL

# More configurations
PRODUCT_VENDOR_PROPERTIES += \
    ro.carwatchdog.client_healthcheck.interval=20 \
    ro.carwatchdog.vhal_healthcheck.interval=10 \
    ro.surface_flinger.has_wide_color_display=false \
    ro.surface_flinger.has_HDR_display=false \
    ro.surface_flinger.protected_contents=false \
    ro.surface_flinger.use_color_management=false

ifeq (,$(ENABLE_REAR_VIEW_CAMERA_SAMPLE))
ENABLE_REAR_VIEW_CAMERA_SAMPLE := true
endif

# Auto modules
PRODUCT_PACKAGES += \
    android.hardware.automotive.vehicle@V3-emulator-service \
    android.hardware.broadcastradio-service.default \
    android.hardware.audio.service-caremu \
    android.hardware.automotive.remoteaccess@V2-default-service \
    android.hardware.automotive.ivn@V1-default-service

# Copy car_core_hardware and overwrite handheld_core_hardware.xml with a disable config.
# Overwrite goldfish related xml with a disable config.
PRODUCT_COPY_FILES += \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml \
    device/generic/car/common/car_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/car_core_hardware.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.ar.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.autofocus.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.concurrent.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.full.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.any.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.raw.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.fingerprint.xml \
    device/generic/car/common/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \

# Overwrite goldfish fstab.ranchu to turn off adoptable_storage
PRODUCT_COPY_FILES += \
    device/generic/car/common/fstab.ranchu.car:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.ranchu

# Enable landscape
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.screen.landscape.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.screen.landscape.xml

# Used to embed a map in an activity view
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.activities_on_secondary_displays.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.activities_on_secondary_displays.xml

# Permission for Wi-Fi passpoint support
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml

# Additional permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.broadcastradio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.broadcastradio.xml \
    frameworks/native/data/etc/android.hardware.type.automotive.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.type.automotive.xml \

# Sensor features
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer_limited_axes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer_limited_axes.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope_limited_axes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.gyroscope_limited_axes.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer_limited_axes_uncalibrated.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer_limited_axes_uncalibrated.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope_limited_axes_uncalibrated.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.gyroscope_limited_axes_uncalibrated.xml \

# Additional selinux policy
BOARD_SEPOLICY_DIRS += device/generic/car/common/sepolicy

# This overrides device/generic/car/common/car.mk
$(call inherit-product, device/generic/car/emulator/audio/car_emulator_audio.mk)
$(call inherit-product, device/generic/car/emulator/rotary/car_rotary.mk)
# Enables USB related passthrough
$(call inherit-product, device/generic/car/emulator/usbpt/car_usbpt.mk)

# EVS
# By default, we enable EvsManager, a sample EVS app, and a mock EVS HAL implementation.
# If you want to use your own EVS HAL implementation, please set ENABLE_MOCK_EVSHAL as false
# and add your HAL implementation to the product.  Please also check init.evs.rc and see how
# you can configure EvsManager to use your EVS HAL implementation.  Similarly, please set
# ENABLE_SAMPLE_EVS_APP as false if you want to use your own EVS app configuration or own EVS
# app implementation.
ENABLE_EVS_SAMPLE ?= false
ENABLE_EVS_SERVICE ?= true
ENABLE_MOCK_EVSHAL ?= true
ENABLE_CAREVSSERVICE_SAMPLE ?= false
ENABLE_SAMPLE_EVS_APP ?= false
ENABLE_CARTELEMETRY_SERVICE ?= false
ifeq ($(ENABLE_MOCK_EVSHAL), true)
CUSTOMIZE_EVS_SERVICE_PARAMETER := true
endif  # ENABLE_MOCK_EVSHAL
$(call inherit-product, device/generic/car/emulator/evs/evs.mk)

ifeq ($(EMULATOR_DYNAMIC_MULTIDISPLAY_CONFIG),true)
# This section configures multi-display without hardcoding the
# displays on hwservicemanager.
$(call inherit-product, device/generic/car/emulator/multi-display-dynamic/multi-display-dynamic.mk)
else # EMULATOR_DYNAMIC_MULTIDISPLAY_CONFIG
ifeq (true,$(BUILD_EMULATOR_CLUSTER_DISPLAY))
$(call inherit-product, device/generic/car/emulator/cluster/cluster-hwservicemanager.mk)
endif # BUILD_EMULATOR_CLUSTER_DISPLAY
endif # EMULATOR_DYNAMIC_MULTIDISPLAY_CONFIG

# Should use car bluetooth.prop.
# This replaces value from device/generic/goldfish/product/generic.mk below
ifeq (,$(ENABLE_CAR_USB_PASSTHROUGH))
ENABLE_CAR_USB_PASSTHROUGH := false
endif
ifeq (true,$(ENABLE_CAR_USB_PASSTHROUGH))
TARGET_PRODUCT_PROP := device/generic/car/emulator/usbpt/bluetooth/bluetooth.prop
endif

# Goldfish vendor partition configurations
$(call inherit-product, device/generic/goldfish/product/generic.mk)

# Enable socket for qemu VHAL
BOARD_SEPOLICY_DIRS += device/generic/car/emulator/sepolicy

$(call inherit-product-if-exists, device/generic/car/emulator/skins/overlays/car_emu_skin_overlays.mk)