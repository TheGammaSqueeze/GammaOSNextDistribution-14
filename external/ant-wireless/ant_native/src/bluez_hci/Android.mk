#
# Copyright (C) 2009 Dynastream Innovations
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

LOCAL_CFLAGS := -g -c -W -Wall -O2

# Check which chip is used so correct values in messages
ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"wl12xx")
LOCAL_CFLAGS += -DBOARD_ANT_DEVICE_WL12XX
else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"bcm433x")
LOCAL_CFLAGS += -DBOARD_ANT_DEVICE_BCM433X
endif

LOCAL_C_INCLUDES := \
   $(LOCAL_PATH)/src/common/inc \
   $(LOCAL_PATH)/$(ANT_DIR)/inc \
   system/bluetooth/bluez-clean-headers

ifeq ($(BOARD_ANT_WIRELESS_POWER),"bluedroid")
LOCAL_CFLAGS += \
   -DBOARD_HAVE_ANT_WIRELESS \
   -DUSE_EXTERNAL_POWER_LIBRARY

LOCAL_C_INCLUDES += system/bluetooth/bluedroid/include/bluedroid
endif # BOARD_ANT_WIRELESS_POWER = bluedroid

LOCAL_SRC_FILES := \
   $(COMMON_DIR)/JAntNative.cpp \
   $(COMMON_DIR)/ant_utils.c \
   $(ANT_DIR)/ant_native_hci.c \
   $(ANT_DIR)/ant_rx.c \
   $(ANT_DIR)/ant_tx.c

# JNI
LOCAL_C_INCLUDE += $(JNI_H_INCLUDE)

LOCAL_SHARED_LIBRARIES += \
   libnativehelper

# chip power
LOCAL_SHARED_LIBRARIES += \
   libbluedroid

# logging
LOCAL_SHARED_LIBRARIES += \
   libcutils \
   liblog

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libantradio
LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_SHARED_LIBRARY)

