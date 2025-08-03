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

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_SRCS += \
	$(LOCAL_DIR)/lib.rs \

MODULE_CRATE_NAME := keymint

MODULE_LIBRARY_DEPS += \
	trusty/user/base/interface/keybox \
	trusty/user/base/lib/hwbcc/rust \
	trusty/user/base/lib/hwkey/rust \
	trusty/user/base/lib/hwwsk/rust \
	trusty/user/base/lib/keymint-rust/boringssl \
	trusty/user/base/lib/keymint-rust/common \
	trusty/user/base/lib/keymint-rust/ta \
	$(call FIND_CRATE,log) \
	$(call FIND_CRATE,protobuf)/2.27.1 \
	trusty/user/base/lib/storage/rust \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/system_state/rust \
	trusty/user/base/lib/trusty-log \
	trusty/user/base/lib/trusty-std \

ifdef TRUSTY_KM_RUST_ACCESS_POLICY
    MODULE_LIBRARY_DEPS+= $(TRUSTY_KM_RUST_ACCESS_POLICY)
else
    MODULE_LIBRARY_DEPS+= trusty/user/app/keymint/generic_access_policy
endif

MODULE_RUSTFLAGS += \
	--cfg 'feature="soft_attestation_fallback"' \
	--cfg 'feature="auto_second_imei"' \

MODULE_RUST_TESTS := true

# The port tests are built and installed regardless of whether the KeyMint Rust TA
# is enabled, so set a config value to allow tests that involve the TA to be skipped.
ifeq (rust,$(TRUSTY_KEYMINT_IMPL))
     MODULE_RUSTFLAGS += --cfg 'kmr_enabled'
endif

MODULE_BINDGEN_ALLOW_TYPES := \
	keybox.* \

MODULE_BINDGEN_ALLOW_FUNCTIONS := \
	trusty_rng_.* \

MODULE_BINDGEN_ALLOW_VARS := \
	KEYBOX.* \

MODULE_BINDGEN_SRC_HEADER := $(LOCAL_DIR)/bindings.h

include make/library.mk
