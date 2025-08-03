# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_DIR := $(GET_LOCAL_DIR)
LIBCPPBOR_DIR := $(if $(wildcard system/libcppbor),system/libcppbor,external/libcppbor)

MODULE := $(LOCAL_DIR)

MODULE_SRCS := \
	$(LOCAL_DIR)/swbcc.c \
	$(LOCAL_DIR)/common.cpp \

MODULE_EXPORT_INCLUDES := \
	$(LOCAL_DIR)/include \

MODULE_LIBRARY_DEPS := \
	external/open-dice \
	$(LIBCPPBOR_DIR) \
	external/boringssl \
	trusty/user/base/lib/unittest \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/interface/hwbcc \
	trusty/user/base/lib/hwkey \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/system_state \

include make/library.mk
