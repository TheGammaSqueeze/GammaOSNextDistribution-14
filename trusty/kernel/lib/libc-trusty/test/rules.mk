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

LIBC_TRUSTY_TEST_DIR := trusty/user/base/lib/libc-trusty/test

MODULE_SRCS += \
	$(LIBC_TRUSTY_TEST_DIR)/libc_test.c \

# We're doing some strange things like passing bad format strings to printf. The
# compiler can interfere with these tests, so prevent it from making assumptions
# about function names. This also prevents rewriting like printf => puts.
MODULE_COMPILEFLAGS := -ffreestanding -Wno-format-invalid-specifier

include make/module.mk
