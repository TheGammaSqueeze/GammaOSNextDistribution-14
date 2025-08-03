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

# Add non-removable cluster by creating a display on hwservicemanager.
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/cluster/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml \

ifeq ($(EMULATOR_MULTIDISPLAY_HW_CONFIG),)
PRODUCT_PRODUCT_PROPERTIES += \
    hwservicemanager.external.displays=1,400,600,120,0 \
    persist.service.bootanim.displays=8140900251843329
else
ifneq ($(EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG),)
    PRODUCT_PRODUCT_PROPERTIES += \
        hwservicemanager.external.displays=$(EMULATOR_MULTIDISPLAY_HW_CONFIG) \
        persist.service.bootanim.displays=$(EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG)
else #  EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG
$(error EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG has to be defined when EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG is defined)
endif # EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG
endif # EMULATOR_HW_MULTIDISPLAY_CONFIG

ifeq (true,$(ENABLE_CLUSTER_OS_DOUBLE))
PRODUCT_PACKAGES += ClusterHomeSampleOverlay CarServiceOverlayEmulatorOsDouble ClusterOsDoubleEmulatorPhysicalDisplayOverlay
else
PRODUCT_PACKAGES += CarServiceOverlayEmulator
endif  # ENABLE_CLUSTER_OS_DOUBLE

# Disable dynamic multidisplay for emulators with display added by
# hwservicemanager.
EMULATOR_DYNAMIC_MULTIDISPLAY_CONFIG := false

