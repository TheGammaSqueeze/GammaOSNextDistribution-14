#
# Copyright (C) 2023 The Android Open Source Project
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

$(call inherit-product, device/generic/car/common/car_md.mk)
$(call inherit-product, device/generic/car/sdk_car_arm64.mk)

# TODO(b/266978709): Set it to true after cleaning up the system partition
# changes from this makefile
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

PRODUCT_NAME := sdk_car_md_arm64
PRODUCT_DEVICE := emulator_car64_arm64
PRODUCT_BRAND := Android
PRODUCT_MODEL := Car multi-display on arm64 emulator

$(warning ${PRODUCT_NAME} is for development purposes only.)
