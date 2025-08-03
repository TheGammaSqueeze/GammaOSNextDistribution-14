# Copyright (C) 2023 The Android Open Source Project
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

MODULE_INCLUDES += \
	$(LOCAL_DIR)/../include \

MODULE_SRCS += \
	$(LOCAL_DIR)/main.cpp \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/unittest \
	trusty/user/base/lib/teeui-stub \

# Use the example layouts unless we have a vendor specific layout defined.
ifeq ($(CONFIRMATIONUI_LAYOUTS),)
MODULE_LIBRARY_DEPS += $(LOCAL_DIR)/../examples/layouts
else
MODULE_LIBRARY_DEPS += $(CONFIRMATIONUI_LAYOUTS)
endif

ifeq ($(CONFIRMATIONUI_DEVICE_PARAMS),)
MODULE_LIBRARY_DEPS += $(LOCAL_DIR)/../examples/devices/emulator
else
MODULE_LIBRARY_DEPS += $(CONFIRMATIONUI_DEVICE_PARAMS)
endif

include make/trusted_app.mk
