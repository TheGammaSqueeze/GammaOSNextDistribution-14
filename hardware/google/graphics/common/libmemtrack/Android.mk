# Copyright (C) 2013 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

# HAL module implemenation stored in
# hw/<POWERS_HARDWARE_MODULE_ID>.<ro.hardware>.so
include $(CLEAR_VARS)

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_HEADER_LIBRARIES := libcutils_headers libsystem_headers libhardware_headers
LOCAL_SHARED_LIBRARIES := liblog libion_google
LOCAL_SRC_FILES := memtrack_exynos.cpp mali.cpp ion.cpp dmabuf.cpp
LOCAL_MODULE := memtrack.$(TARGET_BOARD_PLATFORM)
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)
