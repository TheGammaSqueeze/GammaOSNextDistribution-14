# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/trusty_gatekeeper.cpp \

IPC := ipc

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/hwkey \
	trusty/user/base/lib/storage \
	trusty/user/base/lib/keymaster \
	system/gatekeeper \
	external/boringssl \

MODULE_INCLUDES += \
	$(LOCAL_DIR) \
	$(TRUSTY_TOP)/hardware/libhardware/include

# Gatekeeper uses variable-length arrays to concatinate a salt with a password.
# The code should be changed to either use malloc or explicitly pass each piece
# of data to the hash function.
# TODO(ncbray): remove the VLA and turn the warning back on.
MODULE_COMPILEFLAGS := -Wno-vla


# This should be set to an appropriate storage service port.
# For example, if a device implements factory reset protection using gatekeeper,
# it should be set to STORAGE_CLIENT_TDP_PORT or STORAGE_CLIENT_TP_PORT.
GATEKEEPER_STORAGE_PORT ?= STORAGE_CLIENT_TD_PORT

MODULE_DEFINES += \
        GATEKEEPER_STORAGE_PORT=$(GATEKEEPER_STORAGE_PORT) \

include $(LOCAL_DIR)/$(IPC)/rules.mk

include make/trusted_app.mk
