#
# Copyright (C) 2018 The Android Open-Source Project
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
    art/tools/public.libraries.buildbot.txt:system/etc/public.libraries.txt

$(call inherit-product, build/target/product/core_minimal.mk)

PRODUCT_NAME := armv8_kryo385
PRODUCT_DEVICE := armv8_kryo385
PRODUCT_BRAND := Android
PRODUCT_MODEL := Qualcomm Kryo385

# Force 64bits executables.
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.zygote=zygote64
