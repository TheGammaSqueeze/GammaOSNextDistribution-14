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

PACKAGE_DIR := $(TRUSTY_TOP)/trusty/user/base/lib/apploader_package
OPEN_DICE_DIR := $(TRUSTY_TOP)/external/open-dice

HOST_TOOL_NAME := apploader_package_tool

HOST_SRCS := \
	$(LOCAL_DIR)/apploader_package_tool.cpp \
	$(LOCAL_DIR)/../app_manifest_parser.cpp \
	$(PACKAGE_DIR)/cose.cpp \
	$(OPEN_DICE_DIR)/src/cbor_reader.c \
	$(OPEN_DICE_DIR)/src/cbor_writer.c \

HOST_INCLUDE_DIRS := \
	trusty/user/base/interface/apploader/include \
	trusty/user/base/lib/apploader_package/include \
	trusty/user/base/lib/apploader_policy_engine/include \
	external/open-dice/include \

# libcppbor checks if __TRUSTY__ is defined to determine whether it's linked
# into Android or Trusty; the library uses some Android-specific logging and
# other APIs that host tools don't provide, so we define __TRUSTY__ here to
# disable all the Android-specific code in libcppbor.
HOST_FLAGS := -D__TRUSTY__

# The COSE code also needs to use different APIs/macros for error printing
# depending on whether it's compiled for a host tool or Trusty application.
HOST_FLAGS += -D__COSE_HOST__

# Select app package signing variant
ifeq (true,$(call TOBOOL,$(APPLOADER_PACKAGE_SIGN_P384)))
HOST_FLAGS += -DAPPLOADER_PACKAGE_SIGN_P384
endif

# Select app package cipher variant
ifeq (true,$(call TOBOOL,$(APPLOADER_PACKAGE_CIPHER_A256)))
HOST_FLAGS += -DAPPLOADER_PACKAGE_CIPHER_A256
endif

# Build as a static binary for portability
HOST_STATIC_LINK := true

HOST_LIBS := \
	c++ \

# Build and statically link in boringssl so we don't have to worry about what
# version the host environment provides. OpenSSL 3.0 deprecates several of the
# low-level APIs used for trusty app signing and encryption.
HOST_DEPS := \
	trusty/user/base/host/boringssl \
	trusty/kernel/lib/app_manifest/host \

# If requested, the apploader can be built with the policy engine for key
# checking functionality.  This requires the policy engine API be
# implemented in a host library.
ifeq (true,$(call TOBOOL,$(APPLOADER_PACKAGE_TOOL_WITH_POLICY_ENGINE)))
HOST_FLAGS += -DWITH_APPLOADER_POLICY_ENGINE
HOST_DEPS += $(TRUSTY_APPLOADER_POLICY_ENGINE)/host
endif

include make/host_tool.mk
