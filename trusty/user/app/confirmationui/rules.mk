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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/src/main.cpp \
	$(LOCAL_DIR)/src/secure_input_tracker.cpp \
	$(LOCAL_DIR)/src/trusty_operation.cpp \
	$(LOCAL_DIR)/src/trusty_confirmation_ui.cpp \
	$(LOCAL_DIR)/src/trusty_time_stamper.cpp \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/keymaster \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/secure_fb \
	trusty/user/base/lib/teeui-stub \
	trusty/user/base/lib/tipc \
	external/boringssl \

# Enable handle prot if required
ifeq (true,$(call TOBOOL,$(CONFIRMATIONUI_HANDLE_PROT)))
MODULE_DEFINES += WITH_HANDLE_PROT
MODULE_LIBRARY_DEPS += \
	trusty/user/whitechapel/tz/base/lib/handle_prot
endif

# Use the example layouts unless we have a vendor specific layout defined.
ifeq ($(CONFIRMATIONUI_LAYOUTS),)
MODULE_LIBRARY_DEPS += $(LOCAL_DIR)/examples/layouts
else
MODULE_LIBRARY_DEPS += $(CONFIRMATIONUI_LAYOUTS)
endif

ifeq ($(CONFIRMATIONUI_DEVICE_PARAMS),)
MODULE_LIBRARY_DEPS += $(LOCAL_DIR)/examples/devices/emulator
else
MODULE_LIBRARY_DEPS += $(CONFIRMATIONUI_DEVICE_PARAMS)
endif


MODULE_EXPORT_INCLUDES += $(LOCAL_DIR)/include

include make/trusted_app.mk
