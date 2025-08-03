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

# this overwrites Android Emulator's default input devices for virtual displays in device/generic/goldfish/input/
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display/input/virtio_input_multi_touch_7.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_7.idc \
    device/generic/car/emulator/multi-display/input/virtio_input_multi_touch_8.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_8.idc \
    device/generic/car/emulator/multi-display/input/virtio_input_multi_touch_9.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_9.idc

PRODUCT_COPY_FILES += device/generic/car/common/config.ini.car_md:config.ini

# Overrides Goldfish's default display settings
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display/display_layout_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_layout_configuration.xml \
    device/generic/car/emulator/multi-display/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.managed_users.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.managed_users.xml

PRODUCT_PACKAGE_OVERLAYS += \
    device/generic/car/emulator/multi-display/overlay

PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display/car_audio_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/car_audio_configuration.xml

EMULATOR_DYNAMIC_MULTIDISPLAY_CONFIG := false
BUILD_EMULATOR_CLUSTER_DISPLAY := true
# Set up additional displays
EMULATOR_MULTIDISPLAY_HW_CONFIG := 1,968,792,160,0,2,1408,792,160,0,3,1408,792,160,0
EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG := 4619827551948147201,4619827124781842690,4619827540095559171
ENABLE_CLUSTER_OS_DOUBLE:=true

PRODUCT_PACKAGES += CarServiceOverlayMdEmulatorOsDouble

# Enable MZ audio by default
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.vendor.simulateMultiZoneAudio=true \
    persist.sys.max_profiles=5 \
    com.android.car.internal.debug.num_auto_populated_users=1

PRODUCT_PACKAGES += \
    MultiDisplaySecondaryHomeTestLauncher \
    MultiDisplayTest

# enables the rro package for passenger(secondary) user.
ENABLE_PASSENGER_SYSTEMUI_RRO := true
