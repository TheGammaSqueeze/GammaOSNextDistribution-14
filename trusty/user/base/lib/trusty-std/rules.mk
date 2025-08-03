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

MODULE_CRATE_NAME := trusty_std

MODULE_LIBRARY_EXPORTED_DEPS += \
	trusty/user/base/lib/liballoc-rust \
	$(call FIND_CRATE,libc) \

ifeq ($(call TOBOOL,$(TRUSTY_USERSPACE)),true)

MODULE_LIBRARY_EXPORTED_DEPS += \
	trusty/user/base/lib/trusty-sys \

# In userspace (non-`#[no_std]`), we don't want to depend on `std`.
# However, we do want implicit deps on `core`.
MODULE_ADD_IMPLICIT_DEPS := false

endif

include make/library.mk
