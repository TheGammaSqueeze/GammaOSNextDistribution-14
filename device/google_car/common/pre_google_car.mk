#
# Copyright 2020 The Android Open Source Project
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

####
#### This file should be included at the top of the aosp_PHONE_car.mk file
####

# CarServiceHelperService accesses the hidden api in the system server.
SYSTEM_OPTIMIZE_JAVA := false

DEVICE_FRAMEWORK_MANIFEST_FILE += device/google_car/common/manifest.xml

# generic_system.mk sets 'PRODUCT_ENFORCE_RRO_TARGETS := *'
# but this breaks phone_car. So undo it here.
PRODUCT_ENFORCE_RRO_TARGETS :=

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

# Set Car Service RRO
PRODUCT_PACKAGES += CarServiceOverlayPhoneCar
GOOGLE_CAR_SERVICE_OVERLAY += CarServiceOverlayPhoneCarGoogle

# All components inherited here go to system image
# Skip this for 64 bit only devices
ifneq ($(DEVICE_IS_64BIT_ONLY),true)
    $(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
endif
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

# Auto modules

ifneq ($(PIXEL_2023_GEN),)
    PRODUCT_PACKAGES += \
        android.hardware.broadcastradio \
        android.hardware.automotive.vehicle@V3-default-service
else
    PRODUCT_PACKAGES += \
        android.hardware.broadcastradio@2.0-service \
        android.hardware.automotive.vehicle@2.0-default-service
endif

# Additional selinux policy
BOARD_SEPOLICY_DIRS += device/google_car/common/sepolicy

# Car init.rc
PRODUCT_COPY_FILES += \
            packages/services/Car/car_product/init/init.bootstat.rc:root/init.bootstat.rc \
            packages/services/Car/car_product/init/init.car.rc:root/init.car.rc

# Override heap growth limit due to high display density on device
PRODUCT_PROPERTY_OVERRIDES += \
            dalvik.vm.heapgrowthlimit=256m

PRODUCT_PACKAGE_OVERLAYS += device/google_car/common/overlay

ifneq ($(PORTRAIT_UI), true)
        # Enable landscape
        PRODUCT_COPY_FILES += \
            frameworks/native/data/etc/android.hardware.screen.landscape.xml:system/etc/permissions/android.hardware.screen.landscape.xml
endif


TARGET_USES_CAR_FUTURE_FEATURES := true

PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/car_core_hardware.xml:system/etc/permissions/car_core_hardware.xml \
        frameworks/native/data/etc/android.hardware.type.automotive.xml:system/etc/permissions/android.hardware.type.automotive.xml \
        frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml \
        frameworks/native/data/etc/android.hardware.telephony.cdma.xml:system/etc/permissions/android.hardware.telephony.cdma.xml \
        frameworks/native/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
        frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
        frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
        frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
        frameworks/native/data/etc/android.software.sip.voip.xml:system/etc/permissions/android.software.sip.voip.xml \
        frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
        frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:system/etc/permissions/android.hardware.sensor.gyroscope.xml \
        frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
        frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
        frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
        frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml

# broadcast radio feature
 PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.broadcastradio.xml:system/etc/permissions/android.hardware.broadcastradio.xml

# Include EVS reference implementations
ENABLE_EVS_SAMPLE := true

#
# All components inherited here go to vendor image
#
# TODO(b/136525499): move *_vendor.mk into the vendor makefile later
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_vendor.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_vendor.mk)
