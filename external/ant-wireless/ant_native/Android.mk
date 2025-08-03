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

ifneq ($(BOARD_ANT_WIRELESS_DEVICE),)
ifneq ($(BOARD_ANT_WIRELESS_DEVICE),"qualcomm-hidl")

LOCAL_PATH := $(call my-dir)

#
# ANT native library
#

include $(CLEAR_VARS)

# For known qualcomm smd devices we remap the chip name to "qualcomm-smd"

ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"wcn3680")

BOARD_ANT_WIRELESS_DEVICE := "qualcomm-smd"

endif

ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"wl12xx")

ANT_DIR := src/bluez_hci

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"bcm433x")

ANT_DIR := src/bluez_hci

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"cg29xx")

ANT_DIR := src/vfs

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"qualcomm-smd")

ANT_DIR := src/vfs

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"qualcomm-uart")

ANT_DIR := src/bt-vendor_vfs

else ifeq ($(BOARD_ANT_WIRELESS_DEVICE),"vfs-prerelease")

ANT_DIR := src/vfs

else

$(error Unsupported BOARD_ANT_WIRELESS_DEVICE := $(BOARD_ANT_WIRELESS_DEVICE))

endif # BOARD_ANT_WIRELESS_DEVICE type

COMMON_DIR := src/common

include $(LOCAL_PATH)/$(ANT_DIR)/Android.mk

#
# ANT Application
#

include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/src/common/inc \
	$(LOCAL_PATH)/app

LOCAL_C_INCLUDES += frameworks/base/core/jni/include

LOCAL_CFLAGS:= -g -c -W -Wall -O2

LOCAL_SRC_FILES:= \
	app/ant_app.c

LOCAL_SHARED_LIBRARIES := \
	libantradio \
	libcutils

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
LOCAL_MODULE:=antradio_app

LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_EXECUTABLE)

endif
endif # BOARD_ANT_WIRELESS_DEVICE defined
