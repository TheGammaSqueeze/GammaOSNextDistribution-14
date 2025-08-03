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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_EXPORT_INCLUDES += $(LOCAL_DIR)/include

MODULE_SRCS := \
	$(LOCAL_DIR)/cose.cpp \
	$(LOCAL_DIR)/package.cpp \

MODULE_LIBRARY_DEPS := \
	external/boringssl \
	external/open-dice \
	trusty/user/base/interface/apploader \
	trusty/user/base/lib/apploader_policy_engine \
	trusty/user/base/lib/hwaes \
	trusty/user/base/lib/hwkey \

# Select app package signing variant
ifeq (true,$(call TOBOOL,$(APPLOADER_PACKAGE_SIGN_P384)))
MODULE_COMPILEFLAGS += -DAPPLOADER_PACKAGE_SIGN_P384
endif

# Select app package cipher variant
ifeq (true,$(call TOBOOL,$(APPLOADER_PACKAGE_CIPHER_A256)))
MODULE_COMPILEFLAGS += -DAPPLOADER_PACKAGE_CIPHER_A256
endif

include make/library.mk
