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

COMPILER_BUILTINS_DIR = $(RUST_BINDIR)/../src/stdlibs/vendor/compiler_builtins

MODULE_SRCS := $(COMPILER_BUILTINS_DIR)/src/lib.rs

MODULE_CRATE_NAME := compiler_builtins

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libcore-rust \

MODULE_RUST_EDITION := 2015

MODULE_RUSTFLAGS += \
	--cfg 'feature="weak-intrinsics"' \
	--cfg 'feature="compiler-builtins"' \
	--cfg 'feature="core"' \
	--cfg 'feature="default"' \
	-C panic=abort \
	-C overflow-checks=off \
	-A rustdoc::all \

# src/float/pow.rs has a collision with a future std item: i32::abs_diff
MODULE_RUSTFLAGS += \
	-A unstable-name-collisions

MODULE_ADD_IMPLICIT_DEPS := false

MODULE_SKIP_DOCS := true

include make/library.mk
