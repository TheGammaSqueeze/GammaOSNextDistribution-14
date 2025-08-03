# Copyright (C) 2019 The Android Open Source Project
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
	$(LOCAL_DIR)/libc_test.c \
	$(LOCAL_DIR)/libc_test_$(ARCH).S \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/unittest \

# We're doing some strange things like exhausting memory with malloc and passing
# bad format strings to printf. The compiler can interfere with these tests, so
# prevent it from making assumptions about function names. This also prevents
# rewriting like printf => puts.
MODULE_COMPILEFLAGS := -ffreestanding -Wno-format-invalid-specifier

include make/trusted_app.mk
