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

MODULE_CRATE_NAME := trusty_sys

MODULE_LIBRARY_EXPORTED_DEPS += \
	trusty/user/base/lib/libcompiler_builtins-rust \
	trusty/user/base/lib/libcore-rust \
	trusty/user/base/lib/syscall-stubs \

MODULE_ADD_IMPLICIT_DEPS := false

# Get the path to the generated Rust file
include trusty/user/base/lib/syscall-stubs/common-inc.mk

# Ensure that we have the syscall rust file
MODULE_RUST_ENV += SYSCALL_INC_FILE=$(SYSCALL_RS)

MODULE_SRCDEPS += $(SYSCALL_RS)

MODULE_BINDGEN_ALLOW_TYPES := \
	iovec \
	dma_pmem \
	uuid \
	handle_t \
	uevent \
	ipc_msg \
	ipc_msg_info \

MODULE_BINDGEN_ALLOW_VARS := \
	NO_ERROR \
	ERR_.* \
	IPC_CONNECT_.* \
	MMAP_FLAG_PROT_READ \
	MMAP_FLAG_PROT_WRITE \
	MMAP_FLAG_IO_HANDLE \

MODULE_BINDGEN_SRC_HEADER := $(LOCAL_DIR)/bindings.h

# Derive eq and hash for uuid
MODULE_BINDGEN_FLAGS += --with-derive-eq --with-derive-hash

include make/library.mk
