#
# Copyright (C) 2021 The Android Open Source Project
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
# This file overwrites the handheld_core_hardware.xml from goldfish
#

# Copy slim_core_hardware and overwrite handheld_core_hardware.xml with a disable config.
PRODUCT_COPY_FILES += \
    device/generic/goldfish/slim/data/etc/android.hardware.disable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml \
    device/generic/goldfish/slim/data/etc/slim_handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/slim_handheld_core_hardware.xml \

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    debug.hwui.drawing_enabled=0
