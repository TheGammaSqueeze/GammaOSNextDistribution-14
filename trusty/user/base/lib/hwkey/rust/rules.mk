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

MODULE_SRCS := $(LOCAL_DIR)/src/lib.rs

MODULE_CRATE_NAME := hwkey

MODULE_SDK_LIB_NAME := hwkey-rust

MODULE_LIBRARY_DEPS += \
	trusty/user/base/interface/hwkey \
	$(call FIND_CRATE,log) \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/trusty-std \

MODULE_BINDGEN_ALLOW_TYPES := \
	hwkey.* \

MODULE_BINDGEN_ALLOW_VARS := \
	HWKEY.* \


ifeq (true,$(call TOBOOL,$(WITH_HWCRYPTO_UNITTEST)))
MODULE_RUSTFLAGS += --cfg 'feature="hwcrypto-unittest"'
endif

MODULE_BINDGEN_SRC_HEADER := $(LOCAL_DIR)/bindings.h

MODULE_RUST_TESTS := true
# For test service
MANIFEST := $(LOCAL_DIR)/manifest.json

include make/library.mk
