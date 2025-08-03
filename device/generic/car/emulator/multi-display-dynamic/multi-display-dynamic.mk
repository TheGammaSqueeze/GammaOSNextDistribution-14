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

# Enable the displays UI on qEmu and add cluster display as default.

# Use the config.ini with the cluster display declared.
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display-dynamic/config.ini:config.ini
# Enable the displays UI in qemu.
PRODUCT_SYSTEM_PROPERTIES += \
    ro.emulator.car.multidisplay=true
# Must be before the emulator's vendor.mk.
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display-dynamic/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml
# Keep the original audio configuration from the MD emulator.
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/multi-display/car_audio_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/car_audio_configuration.xml

# support packages for multi-display
$(call inherit-product, device/generic/goldfish/product/multidisplay.mk)
PRODUCT_PACKAGES += \
    MultiDisplaySecondaryHomeTestLauncher \
    MultiDisplayTest \
    SecondaryHomeApp \
    MultiDisplayProvider \
    CarServiceMultiDisplayOverlayEmulator

PRODUCT_PACKAGES += ClusterHomeSample ClusterOsDouble ClusterHomeSampleOverlay ClusterOsDoubleEmulatorVirtualDisplayOverlay

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.managed_users.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.managed_users.xml

# Selects the MultiDisplaySecondaryHomeTestLauncher as secondaryHome
PRODUCT_PACKAGE_OVERLAYS += \
    device/generic/car/emulator/multi-display-dynamic/overlay
