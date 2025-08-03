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

LIBSTD_DIR = $(RUST_BINDIR)/../src/stdlibs/library/std

MODULE_SRCS := $(LIBSTD_DIR)/src/lib.rs

MODULE_CRATE_NAME := std

MODULE_RUST_EDITION := 2018

MODULE_LIBRARY_EXPORTED_DEPS += \
	$(call FIND_CRATE,cfg-if) \
	trusty/user/base/lib/libcore-rust \
	$(call FIND_CRATE,libc) \
	trusty/user/base/lib/liballoc-rust \
	trusty/user/base/lib/libunwind-rust \
	trusty/user/base/lib/libhashbrown-rust \
	trusty/user/base/lib/libstd_detect-rust \
	trusty/user/base/lib/librustc-demangle-rust \
	trusty/user/base/lib/libpanic_abort-rust \

MODULE_RUSTFLAGS += \
	--cfg 'backtrace_in_libstd' \
	-Z force-unstable-if-unmarked \

# Suppress known warnings. The libstd source code generates warnings which are
# normally suppressed with `--cap-lints=allow` during the rustbuild process. We
# opt to suppress lints individually in order to avoid missing warnings that
# might be specific to the Trusty build process. These may need to be updated
# for future Rust releases.
MODULE_RUSTFLAGS += \
	-A unused-variables \
	-A non-fmt-panics \
	-A deprecated \

MODULE_ADD_IMPLICIT_DEPS := false

# `STD_ENV_ARCH` needs to be set when building libstd. For ARM64 `ARCH` needs to
# be translated to the architecture name that rustc expects, but for the
# remaining targets `ARCH` already matches. This will need to be updated
# accordingly if we add support for additional targets where `ARCH` doesn't line
# up with rustc's architecture names.
ifeq ($(ARCH),arm64)
MODULE_RUST_ENV += STD_ENV_ARCH=aarch64
else
MODULE_RUST_ENV += STD_ENV_ARCH=$(ARCH)
endif

MODULE_SKIP_DOCS := true

include make/library.mk
