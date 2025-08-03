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
#

# This makefile comprises the common content for the vendor partition of an
# automotive device.

$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_vendor.mk)

ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
# Include carwatchdog testclient for debug builds
PRODUCT_PACKAGES += carwatchdog_testclient
BOARD_SEPOLICY_DIRS += packages/services/Car/cpp/watchdog/testclient/sepolicy
endif

ifeq ($(ENABLE_EVS_SAMPLE), true)
# Include the reference EVS HAL implementation.
PRODUCT_PACKAGES += android.hardware.automotive.evs-default
endif  # ENABLE_EVS_SAMPLE
