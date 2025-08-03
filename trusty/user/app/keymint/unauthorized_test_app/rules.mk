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

MODULE_SRCS += \
	$(LOCAL_DIR)/lib.rs \

MODULE_CRATE_NAME := keymint_unauthorized_test_app

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/trusty-std \

MODULE_RUST_TESTS := true

# The port tests are built and installed regardless of whether the KeyMint Rust TA
# is enabled, so set a config value to allow tests that involve the TA to be skipped.
ifeq (rust,$(TRUSTY_KEYMINT_IMPL))
     MODULE_RUSTFLAGS += --cfg 'kmr_enabled'
endif

include make/library.mk
