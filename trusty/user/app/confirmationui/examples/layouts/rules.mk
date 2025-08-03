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

LIBTEEUI_ROOT := $(TRUSTY_TOP)/system/teeui/libteeui

MODULE_SRCS += \
	$(LOCAL_DIR)/fonts.S \

MODULE_EXPORT_INCLUDES += $(LOCAL_DIR)/include

MODULE_INCLUDES += \
	$(LIBTEEUI_ROOT)/include \
	$(LOCAL_DIR)/include \

MODULE_COMPILEFLAGS := -U__ANDROID__

# The assembler need the search path set to this directory so that the incbin directive finds
# the font files to include.
MODULE_ASMFLAGS := -I $(LOCAL_DIR) -D__ASSEMBLY__

include make/library.mk
