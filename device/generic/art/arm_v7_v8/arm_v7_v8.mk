#
# Copyright (C) 2017 The Android Open-Source Project
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

$(call inherit-product, build/target/product/core_minimal.mk)

PRODUCT_NAME := arm_v7_v8
PRODUCT_DEVICE := arm_v7_v8
PRODUCT_BRAND := Android
PRODUCT_MODEL := ARM v7 and v8

# Disable debug binaries for an unbundled ART build.
PRODUCT_ART_TARGET_INCLUDE_DEBUG_BUILD := false
