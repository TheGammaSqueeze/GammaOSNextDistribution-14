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

LIBSTDCPP_TRUSTY_TEST_DIR := trusty/user/base/lib/libstdc++-trusty/test

MODULE_SRCS += \
	$(LIBSTDCPP_TRUSTY_TEST_DIR)/libcxx_test.cpp \

# Don't let the compiler optimize out the code we're testing. new, delete, etc.
MODULE_COMPILEFLAGS := -ffreestanding

MODULE_DEPS += \
	trusty/kernel/lib/libcxx-trusty \

include make/module.mk
