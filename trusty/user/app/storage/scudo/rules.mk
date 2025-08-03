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

MODULE := $(LOCAL_DIR)

SCUDO_DIR := $(call FIND_EXTERNAL,scudo)

MODULE_INCLUDES += \
	$(LOCAL_DIR)/include \
	$(SCUDO_DIR)/standalone \
	$(SCUDO_DIR)/standalone/include \

# These C/C++ flags are copied from the Android.bp build rules for Scudo.
MODULE_CFLAGS += \
	-fno-rtti \
	-fno-stack-protector \
	-fno-emulated-tls \
	-Wno-unused-result \
	-DSCUDO_MIN_ALIGNMENT_LOG=4 \

MODULE_DEFINES += \
	SCUDO_USE_CUSTOM_CONFIG=1

MODULE_CPPFLAGS += \
	-fno-exceptions \
	-nostdinc++ \

# scudo should be freestanding, but the rest of the app should not be.
MODULE_COMPILEFLAGS += -ffreestanding

# WARNING: while libstdc++-trusty continues to define `new` and `delete`,
# it's possible that the symbols for those will be chosen over the ones
# Scudo defines (also weak). None of the C++ sources below require any
# STL headers but, if that changes, care will need to be taken to avoid
# non-Scudo-defined `new` and `delete` from getting linked when STL headers
# are desired.
MODULE_SRCS += \
	$(SCUDO_DIR)/standalone/checksum.cpp \
	$(SCUDO_DIR)/standalone/common.cpp \
	$(SCUDO_DIR)/standalone/crc32_hw.cpp \
	$(SCUDO_DIR)/standalone/flags.cpp \
	$(SCUDO_DIR)/standalone/flags_parser.cpp \
	$(SCUDO_DIR)/standalone/mem_map.cpp \
	$(SCUDO_DIR)/standalone/release.cpp \
	$(SCUDO_DIR)/standalone/report.cpp \
	$(SCUDO_DIR)/standalone/string_utils.cpp \
	$(SCUDO_DIR)/standalone/trusty.cpp \
	$(SCUDO_DIR)/standalone/rss_limit_checker.cpp \
	$(LOCAL_DIR)/wrappers.cpp

# Add dependency on syscall-stubs
MODULE_LIBRARY_DEPS += trusty/user/base/lib/syscall-stubs

# Add src dependency on syscall header to ensure it is generated before we try
# to build
include trusty/user/base/lib/syscall-stubs/common-inc.mk
MODULE_SRCDEPS += $(SYSCALL_H)

include make/library.mk
