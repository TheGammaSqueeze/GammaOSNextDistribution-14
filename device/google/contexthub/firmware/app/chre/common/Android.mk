#
# Copyright (C) 2017 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

########################################################
# CHRE 1.0 Library
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanochre10
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../../NOTICE
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=      \
    chre10_app.c          \
    chre10_app_syscalls.c \

LOCAL_STATIC_LIBRARIES += libnanobuiltins
LOCAL_STATIC_LIBRARIES += libnanolibc

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)

########################################################
# CHRE 1.1 Library
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanochre11
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../../NOTICE
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=      \
    chre_app.c          \
    chre11_app_syscalls.c \

LOCAL_STATIC_LIBRARIES += libnanobuiltins
LOCAL_STATIC_LIBRARIES += libnanolibc

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)

########################################################
# CHRE 1.2 Library
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanochre
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../../NOTICE
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=      \
    chre_app.c          \
    chre_app_syscalls.c \

LOCAL_STATIC_LIBRARIES += libnanobuiltins
LOCAL_STATIC_LIBRARIES += libnanolibc

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)
