#
# Copyright (C) 2019 The Android Open Source Project
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

$(call inherit-product, device/generic/car/emulator/usbpt/bluetooth/bluetooth.mk)
$(call inherit-product, device/generic/car/emulator/usbpt/protocan/protocan.mk)
$(call inherit-product, device/generic/car/emulator/usbpt/usbip-service/usbip-service.mk)
$(call inherit-product, device/generic/car/emulator/usbpt/wifi/wifi.mk)

# Required for USB passthrough
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    device/generic/car/emulator/usbpt/modules.blocklist:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/lib/modules/modules.blocklist \
