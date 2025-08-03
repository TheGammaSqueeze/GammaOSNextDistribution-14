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

TRUSTY_APP_NAME := keymint

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/../main.rs \

MODULE_CRATE_NAME := keymint_app

MODULE_LIBRARY_DEPS += \
	trusty/user/app/keymint \
	trusty/user/app/keymint/unauthorized_test_app \
	trusty/user/base/lib/keymint-rust/boringssl \
	trusty/user/base/lib/keymint-rust/common \
	trusty/user/base/lib/keymint-rust/ta \
	trusty/user/base/lib/keymint-rust/wire \
	$(call FIND_CRATE,libc) \
	trusty/user/base/lib/libstd-rust \
	$(call FIND_CRATE,log) \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/trusty-log \
	trusty/user/base/lib/trusty-std \

TRUSTY_KM_WITH_HWWSK_SUPPORT ?= true
ifeq (true,$(call TOBOOL,$(TRUSTY_KM_WITH_HWWSK_SUPPORT)))
MODULE_RUSTFLAGS += \
	--cfg 'feature="with_hwwsk_support"'
endif

include make/trusted_app.mk
