#
# Copyright (C) 2011 Dynastream Innovations
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

include $(CLEAR_VARS)

LOCAL_CFLAGS := -g -c -W -Wall -O2 -Wno-unused-parameter

LOCAL_C_INCLUDES := \
   $(LOCAL_PATH)/src/common/inc \
   $(LOCAL_PATH)/$(ANT_DIR)/inc \

ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"cg29xx")
LOCAL_C_INCLUDES += \
   $(LOCAL_PATH)/$(ANT_DIR)/ste/cg29xx \

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"qualcomm-smd")
LOCAL_C_INCLUDES += \
   $(LOCAL_PATH)/$(ANT_DIR)/qualcomm/smd \

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"vfs-prerelease")
LOCAL_C_INCLUDES += \
   $(LOCAL_PATH)/$(ANT_DIR)/prerelease \

endif # BOARD_ANT_WIRELESS_DEVICE = "vfs-prerelease"

LOCAL_SRC_FILES := \
   $(COMMON_DIR)/JAntNative.cpp \
   $(COMMON_DIR)/ant_utils.c \
   $(ANT_DIR)/ant_native_chardev.c \
   $(ANT_DIR)/ant_rx_chardev.c \

# JNI
LOCAL_C_INCLUDE += $(JNI_H_INCLUDE)

LOCAL_SHARED_LIBRARIES += \
   libnativehelper

# logging
LOCAL_SHARED_LIBRARIES += \
   libcutils \
   liblog

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libantradio
LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_SHARED_LIBRARY)
