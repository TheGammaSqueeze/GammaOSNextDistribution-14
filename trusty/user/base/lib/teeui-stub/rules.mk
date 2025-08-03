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
FREETYPE_ROOT := $(TRUSTY_TOP)/external/freetype

MODULE_SRCS += \
	$(LIBTEEUI_ROOT)/prebuilt/localization/ConfirmationUITranslations.cpp \
	$(LIBTEEUI_ROOT)/src/cbor.cpp \
	$(LIBTEEUI_ROOT)/src/button.cpp \
	$(LIBTEEUI_ROOT)/src/font_rendering.cpp \
	$(LIBTEEUI_ROOT)/src/generic_messages.cpp \
	$(LIBTEEUI_ROOT)/src/label.cpp \
	$(LIBTEEUI_ROOT)/src/msg_formatting.cpp \
	$(LIBTEEUI_ROOT)/src/utils.cpp \

MODULE_EXPORT_INCLUDES += \
	$(LIBTEEUI_ROOT)/include \
	$(LIBTEEUI_ROOT)/prebuilt/localization/include \

MODULE_CPPFLAGS := -std=c++17 -fno-short-enums -fno-exceptions
MODULE_CPPFLAGS += -fno-threadsafe-statics -fno-rtti -DNDEBUG

MODULE_COMPILEFLAGS := -U__ANDROID__

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/freetype-stub \
	$(FREETYPE_ROOT)/devel-teeui \

include make/library.mk
