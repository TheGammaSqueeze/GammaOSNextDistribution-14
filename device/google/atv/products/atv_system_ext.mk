#
# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This makefile contains the system_ext partition contents for
# a generic TV device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_system_ext.mk)

PRODUCT_PACKAGES += \
    blur_sysprop_notifier \
    TvSystemUI \
    TvFeedbackConsent \
    TvFrameworkPackageStubs \
    TvSettings

ifeq ($(MDNS_OFFLOAD_SUPPORT),true)
    PRODUCT_PACKAGES += MdnsOffloadManagerService
endif

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += device/google/atv/sepolicy/system_ext/public
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += device/google/atv/sepolicy/system_ext/private
