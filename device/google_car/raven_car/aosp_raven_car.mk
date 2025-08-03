#
# Copyright 2021 The Android Open Source Project
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

PRODUCT_COPY_FILES += \
    device/google_car/raven_car/displayconfig/display_id_4619827677550801152.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4619827677550801152.xml

$(call inherit-product, device/google_car/common/pre_google_car.mk)
$(call inherit-product, device/google_car/raven_car/device-raven-car.mk)
$(call inherit-product-if-exists, vendor/google_devices/raviole/proprietary/raven/device-vendor-raven.mk)
$(call inherit-product, device/google_car/common/post_google_car.mk)

PRODUCT_MANUFACTURER := Google
PRODUCT_BRAND := Android
PRODUCT_NAME := aosp_raven_car
PRODUCT_DEVICE := raven
PRODUCT_MODEL := AOSP on raven
