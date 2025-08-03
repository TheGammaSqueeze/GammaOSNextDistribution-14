#
# Copyright (C) 2021 Google Inc.
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

# CAN bus HAL
PRODUCT_PACKAGES += android.hardware.automotive.can@1.0-service
PRODUCT_PACKAGES += canhalconfigurator
PRODUCT_COPY_FILES += device/generic/car/emulator/usbpt/protocan/canbus_config.pb:$(TARGET_COPY_OUT_VENDOR)/etc/canbus_config.pb

# TODO(b/259994014): Remove CAN packages from here after AAOS baseline is
# applied to all car emulators
PRODUCT_PACKAGES_DEBUG += \
    canhalctrl \
    canhaldump \
    canhalsend

PRODUCT_PACKAGES += android.device.generic.car.emulator@1.0-protocanbus-service
BOARD_SEPOLICY_DIRS += device/generic/car/emulator/usbpt/protocan/protocanbus/sepolicy
DEVICE_MANIFEST_FILE += device/generic/car/emulator/usbpt/protocan/manifest.protocan.xml
