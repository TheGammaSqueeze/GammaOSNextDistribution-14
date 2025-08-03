# Copyright (C) 2019 The Android Open Source Project
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

FREETYPE_ROOT := $(TRUSTY_TOP)/external/freetype

MODULE_SRCS += \
	$(LOCAL_DIR)/mock_libc.c \

MODULE_COMPILEFLAGS := -U__ANDROID__

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	$(FREETYPE_ROOT)/devel-teeui \

include make/library.mk
