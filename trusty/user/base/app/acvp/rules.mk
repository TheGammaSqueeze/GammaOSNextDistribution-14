# Copyright (C) 2021 The Android Open Source Project
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

MODULEWRAPPER_ROOT := external/boringssl/src/util/fipstools/acvp/modulewrapper
KEYMASTER_ROOT := system/keymaster

MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_INCLUDES += \
	$(MODULEWRAPPER_ROOT) \
	$(KEYMASTER_ROOT)/include \
	$(TRUSTY_TOP)/hardware/libhardware/include \

MODULE_SRCS += \
	$(LOCAL_DIR)/acvp.cpp \
	$(LOCAL_DIR)/keymaster_ckdf.cpp \
	$(MODULEWRAPPER_ROOT)/modulewrapper.cc \
	$(KEYMASTER_ROOT)/android_keymaster/android_keymaster_utils.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/logger.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ckdf.cpp \
	$(KEYMASTER_ROOT)/km_openssl/openssl_err.cpp \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/interface/acvp \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/tipc \
	external/boringssl \

include make/trusted_app.mk
