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
LIBCPPBOR_DIR := $(if $(wildcard system/libcppbor),system/libcppbor,external/libcppbor)

MODULE := $(LOCAL_DIR)

MODULE_SRCS := $(LOCAL_DIR)/src/lib.rs

MODULE_CRATE_NAME := hwbcc

MODULE_SDK_LIB_NAME := hwbcc-rust

MODULE_LIBRARY_DEPS += \
	external/boringssl \
	$(LIBCPPBOR_DIR) \
	external/open-dice \
	trusty/user/base/interface/hwbcc \
	trusty/user/base/lib/trusty-std \
	$(call FIND_CRATE,log) \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/system_state/rust \
	trusty/user/base/lib/hwbcc/common \

MODULE_BINDGEN_ALLOW_TYPES := \
	hwbcc.* \

MODULE_BINDGEN_ALLOW_VARS := \
	HWBCC.* \
	DICE.* \
	ED25519.* \

MODULE_BINDGEN_ALLOW_FUNCTIONS := \
	validate.* \

MODULE_BINDGEN_FLAGS := \
	--use-array-pointers-in-arguments \

MODULE_BINDGEN_SRC_HEADER := $(LOCAL_DIR)/bindings.h

# Enable tests specific to the generic emulator build,
# which depend on the device-specific BCC key
ifeq (generic-arm64, $(PLATFORM))
MODULE_RUSTFLAGS += --cfg 'feature="generic-arm-unittest"'
endif

MODULE_RUST_TESTS := true

# For test service
MANIFEST := $(LOCAL_DIR)/manifest.json

include make/library.mk
