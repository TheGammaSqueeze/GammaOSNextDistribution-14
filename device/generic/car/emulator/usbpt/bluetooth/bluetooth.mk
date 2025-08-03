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

# Enables btlinux when BT passthrough is enabled by starting the emulator with
# -prop vendor.qemu.preferred.bt.service=passthrough

PRODUCT_PACKAGES += \
    android.hardware.bluetooth@1.1-service.btlinux \
    android.hardware.bluetooth.audio@2.2-impl \

PRODUCT_COPY_FILES += \
    device/generic/car/emulator/usbpt/bluetooth/vendor.qemu.preferred.bt.service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.qemu.preferred.bt.service.rc \

PRODUCT_SYSTEM_PROPERTIES += \
    persist.bluetooth.enablenewavrcp=false \

# USB Passthru
PRODUCT_PACKAGES += usb_modeswitch

$(call inherit-product, device/generic/car/emulator/usbpt/bluetooth/btusb/btusb.mk)
