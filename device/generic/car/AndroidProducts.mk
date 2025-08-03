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

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/gsi_car_arm64.mk \
    $(LOCAL_DIR)/gsi_car_x86_64.mk \
    $(LOCAL_DIR)/sdk_car_arm64.mk \
    $(LOCAL_DIR)/sdk_car_md_arm64.mk \
    $(LOCAL_DIR)/sdk_car_md_x86_64.mk \
    $(LOCAL_DIR)/sdk_car_portrait_x86_64.mk \
    $(LOCAL_DIR)/sdk_car_x86_64.mk \

COMMON_LUNCH_CHOICES := \
    gsi_car_arm64-trunk_staging-userdebug \
    gsi_car_x86_64-trunk_staging-userdebug \
    sdk_car_arm64-trunk_staging-userdebug \
    sdk_car_md_x86_64-trunk_staging-userdebug \
    sdk_car_portrait_x86_64-trunk_staging-userdebug \
    sdk_car_x86_64-trunk_staging-userdebug \

EMULATOR_VENDOR_NO_SOUND_TRIGGER := false
