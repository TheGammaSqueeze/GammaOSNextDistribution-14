#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This makefile contains the vendor partition contents for
# a generic TV device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_vendor.mk)

PRODUCT_PACKAGES += \
    tv_input.default

PRODUCT_PACKAGES += \
    audio.primary.default \
    local_time.default

# To enable access to /dev/dvb*
BOARD_SEPOLICY_DIRS += device/google/atv/sepolicy/vendor

# Configure Bluetooth
# Class of Device
#   Service Class: 0x2C -> 44 (Rendering, Capturing, Audio)
#   Major Device Class: 0x04 -> 4 (Audio/Video)
#   Minor Device Class: 0x20 -> 32 (Set-top box) // default value, should be set to 0x3C for a TV
PRODUCT_VENDOR_PROPERTIES += \
    bluetooth.device.class_of_device?=44,4,32
