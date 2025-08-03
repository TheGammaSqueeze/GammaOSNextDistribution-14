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

MODULE_SRCS := system/authgraph/boringssl/src/lib.rs

MODULE_CRATE_NAME := authgraph_boringssl

MODULE_RUSTFLAGS += \
	--cfg 'soong' \
	--allow rustdoc::broken-intra-doc-links \

MODULE_LIBRARY_EXPORTED_DEPS += \
	trusty/user/base/lib/openssl-rust \
	$(call FIND_CRATE,coset) \
	$(call FIND_CRATE,zeroize) \
	trusty/user/base/lib/authgraph-rust/core \
	trusty/user/base/lib/authgraph-rust/wire \

include make/library.mk
