#
# Copyright (C) 2012 The Android Open Source Project
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
# This file is included by other product makefiles to add all the
# emulator-related modules to PRODUCT_PACKAGES.
#

$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_vendor.mk)
ifneq ($(EMULATOR_DISABLE_RADIO),true)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_vendor.mk)
endif

ifeq ($(EMULATOR_DISABLE_RADIO),true)
DEVICE_PACKAGE_OVERLAYS += device/generic/goldfish/tablet/overlay
else
DEVICE_PACKAGE_OVERLAYS := device/generic/goldfish/overlay
endif

PRODUCT_CHARACTERISTICS := emulator

$(call inherit-product, device/generic/goldfish/product/generic.mk)
