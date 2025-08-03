#
# Copyright 2023 The Android Open-Source Project
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

DEVICE_IS_64BIT_ONLY := true


PRODUCT_PACKAGE_OVERLAYS += device/google_car/tangorpro_car/overlay

$(call inherit-product, device/google_car/common/pre_google_car.mk)
$(call inherit-product, device/google_car/tangorpro_car/device-tangorpro-car.mk)
$(call inherit-product, device/google_car/common/post_google_car.mk)

PRODUCT_NAME := aosp_tangorpro_car
PRODUCT_DEVICE := tangorpro
PRODUCT_MODEL := AOSP on Tangorpro
PRODUCT_BRAND := Android
PRODUCT_MANUFACTURER := Google
