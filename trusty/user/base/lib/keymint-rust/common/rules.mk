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

MODULE_SRCS := system/keymint/common/src/lib.rs

MODULE_CRATE_NAME := kmr_common

MODULE_RUSTFLAGS += \
	--allow rustdoc::broken-intra-doc-links \

MODULE_LIBRARY_EXPORTED_DEPS += \
	$(call FIND_CRATE,enumn) \
	trusty/user/base/host/keymint-rust/derive \
	$(call FIND_CRATE,ciborium-io) \
	$(call FIND_CRATE,ciborium) \
	$(call FIND_CRATE,coset) \
	$(call FIND_CRATE,der) \
	trusty/user/base/lib/keymint-rust/wire \
	$(call FIND_CRATE,log) \
	$(call FIND_CRATE,pkcs1) \
	$(call FIND_CRATE,pkcs8) \
	$(call FIND_CRATE,sec1) \
	$(call FIND_CRATE,spki) \
	$(call FIND_CRATE,zeroize) \

include make/library.mk
