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

$(call inherit-product, packages/services/Car/car_product/build/car_product.mk)

# Runtime Resource Overlay for Connectivity
PRODUCT_PACKAGES += \
    CarConnectivityOverlay

# Allowed packages per user type
PRODUCT_COPY_FILES += \
    device/generic/car/common/preinstalled-packages-product-car-emulator.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/preinstalled-packages-product-car-emulator.xml

# This file is used by qemu to provide supported VHAL property list.
PRODUCT_COPY_FILES += hardware/interfaces/automotive/vehicle/aidl/emu_metadata/android.hardware.automotive.vehicle-types-meta.json:android.hardware.automotive.vehicle-types-meta.json

# TODO(b/253459812): find a better way to handle it.
DEVICE_PRODUCT_COMPATIBILITY_MATRIX_FILE += \
    device/generic/car/common/device_framework_matrix_product.xml
