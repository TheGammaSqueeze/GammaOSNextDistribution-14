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
#### This file should be included at the bottom of the aosp_PHONE_car.mk file
####

# Auto modules
PRODUCT_PACKAGES += \
            android.hardware.automotive.audiocontrol-service.example

ifneq ($(PIXEL_2023_GEN),)
    PRODUCT_PACKAGES += android.hardware.automotive.can
else
    PRODUCT_PACKAGES += android.hardware.automotive.can@1.0-service
endif

PRODUCT_PACKAGES_DEBUG += \
            canhalctrl \
            canhaldump \
            canhalsend \
            android.hardware.automotive.occupant_awareness@1.0-service \
            android.hardware.automotive.occupant_awareness@1.0-service_mock

BOARD_SEPOLICY_DIRS += device/google_car/common/sepolicy

# Sepolicy for occupant awareness system
include packages/services/Car/car_product/occupant_awareness/OccupantAwareness.mk

# Sepolicy for compute pipe system
include packages/services/Car/cpp/computepipe/products/computepipe.mk

PRODUCT_COPY_FILES += \
    device/google_car/common/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml \

PRODUCT_PROPERTY_OVERRIDES += \
        ro.boot.wificountrycode=00 \
        ro.config.media_vol_default=0 \
        log.tag.CarTrustAgentUnlockEvent=I

# Phone car targets don't support ramdump
PRODUCT_PACKAGES += \
    ExcludeRamdumpUploader

# Disable RCS and EAB for phone car targets
PRODUCT_PRODUCT_PROPERTIES += \
        persist.rcs.supported=0 \
        persist.eab.supported=0

# Explicitly disable support for some Bluetooth profiles included in base phone builds
PRODUCT_PRODUCT_PROPERTIES += \
        bluetooth.profile.asha.central.enabled=false \
        bluetooth.profile.a2dp.source.enabled=false \
        bluetooth.profile.avrcp.target.enabled=false \
        bluetooth.profile.bap.broadcast.assist.enabled=false \
        bluetooth.profile.bap.unicast.client.enabled=false \
        bluetooth.profile.bas.client.enabled=false \
        bluetooth.profile.csip.set_coordinator.enabled=false \
        bluetooth.profile.hap.client.enabled=false \
        bluetooth.profile.hfp.ag.enabled=false \
        bluetooth.profile.hid.device.enabled=false \
        bluetooth.profile.hid.host.enabled=false \
        bluetooth.profile.map.server.enabled=false \
        bluetooth.profile.mcp.server.enabled=false \
        bluetooth.profile.opp.enabled=false \
        bluetooth.profile.pbap.server.enabled=false \
        bluetooth.profile.sap.server.enabled=false \
        bluetooth.profile.ccp.server.enabled=false \
        bluetooth.profile.vcp.controller.enabled=false
