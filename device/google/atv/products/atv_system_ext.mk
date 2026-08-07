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
    TvFrameworkPackageStubs

# GammaOS: ship BOTH the full phone Settings app AND the leanback TvSettings. A full ATV boot
# (skip nano) uses the full com.android.settings as the default (TvSettings no longer claims the
# android.settings.SETTINGS action - see its manifest), giving the complete settings tree (network,
# apps, storage, accessibility). nano's Quick Menu > System Settings launches the lighter,
# d-pad-first TvSettings on Core/ATV builds (it avoids the minimal_boot service crashes the full
# Settings hits and reads better on the TV form factor).
PRODUCT_PACKAGES += \
    Settings \
    TvSettings

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += device/google/atv/sepolicy/system_ext/public
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += device/google/atv/sepolicy/system_ext/private
