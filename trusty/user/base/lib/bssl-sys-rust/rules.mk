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

BSSL_SRC_DIR := external/boringssl/src
BSSL_RUST_DIR := $(BSSL_SRC_DIR)/rust/bssl-sys

MODULE_SRCS := $(BSSL_RUST_DIR)/src/lib.rs

MODULE_CRATE_NAME := bssl_sys

MODULE_LIBRARY_DEPS += \
	external/boringssl \
	trusty/user/base/lib/bssl-rust-support \
	trusty/user/base/lib/trusty-sys \

MODULE_BINDGEN_SRC_HEADER := $(BSSL_RUST_DIR)/wrapper.h

MODULE_BINDGEN_FLAGS += \
	--no-derive-default \
	--enable-function-attribute-detection \
	--default-macro-constant-type="signed" \
	--rustified-enum="point_conversion_form_t" \

# These regexes use [[:punct:]] instead of / to handle Windows file paths.
# Ideally we would write [/\\], but escaping rules are complex, and often
# ill-defined, in some build systems, so align on [[:punct:]].
MODULE_BINDGEN_ALLOW_FILES += \
	".*[[:punct:]]include[[:punct:]]openssl[[:punct:]].*\\.h" \
	".*[[:punct:]]rust_wrapper\\.h" \

# Specifying the correct clang target results in __builtin_va_list being
# declared as a 4 item array of u64 for aarch64 targets. This is not FFI-safe,
# so we can't declare va_list functions for aarch64 until bindgen supports
# mapping va_list to its Rust equivalent
# (https://github.com/rust-lang/rust/issues/44930)
MODULE_BINDGEN_FLAGS += \
	--blocklist-function="BIO_vsnprintf" \
	--blocklist-function="OPENSSL_vasprintf" \

# bssl-sys expects the bindgen output to be placed in BINDGEN_RS_FILE.
MODULE_BINDGEN_OUTPUT_ENV_VAR := BINDGEN_RS_FILE

MODULE_INCLUDES += \
	$(BSSL_SRC_DIR)/include \

include make/library.mk
