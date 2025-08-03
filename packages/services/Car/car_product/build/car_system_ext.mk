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

# This makefile comprises the minimal system_ext partition content for an
# automotive device.
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)

# Window Extensions
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)

PRODUCT_PACKAGES += \
    CarDeveloperOptions \
    CarProvision \
    CarSystemUI \

PRODUCT_PACKAGES_DEBUG += \
    BugReportApp \

# Default dex optimization configurations
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    dalvik.vm.dex2oat-cpu-set=0,1 \
    dalvik.vm.dex2oat-threads=2 \
    pm.dexopt.disable_bg_dexopt=false \
    pm.dexopt.downgrade_after_inactive_days=10 \

# Disable Prime Shader Cache in SurfaceFlinger to make it available faster
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    service.sf.prime_shader_cache=0

# More configurations for AOSP cars
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    keyguard.no_require_sim=true \
    ro.carrier=unknown \
    ro.com.android.dataroaming?=true \
    ro.hardware.type=automotive \
