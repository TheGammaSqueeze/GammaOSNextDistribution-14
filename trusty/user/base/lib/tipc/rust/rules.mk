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

MODULE_CRATE_NAME := tipc

MODULE_SDK_LIB_NAME := tipc-rust

MODULE_INCLUDES += \
	trusty/user/base/lib/tipc/test/include \

MODULE_LIBRARY_EXPORTED_DEPS += \
	external/rust/crates/arrayvec \
	trusty/user/base/lib/libc-trusty \
	$(call FIND_CRATE,log) \
	trusty/user/base/lib/trusty-std \
	trusty/user/base/lib/trusty-sys \

MODULE_BINDGEN_ALLOW_TYPES := \
	handle_t \

MODULE_BINDGEN_ALLOW_VARS := \
	HSET_.* \
	IPC_.* \
	INFINITE_TIME \
	MAX_USER_HANDLES \
	USER_BASE_HANDLE \
	UUID_STR_SIZE \

MODULE_BINDGEN_SRC_HEADER := $(LOCAL_DIR)/bindings.h

MODULE_RUST_TESTS := true

# For test service
MANIFEST := $(LOCAL_DIR)/manifest.json

include make/library.mk
