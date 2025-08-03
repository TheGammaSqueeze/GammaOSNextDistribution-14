# Copyright (C) 2018 The Android Open Source Project
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

HOST_TOOL_NAME := rpmb_dev

HOST_SRCS := \
	$(LOCAL_DIR)/../crypt.c \
	$(LOCAL_DIR)/main.c \
	$(LOCAL_DIR)/rpmb_dev.c \

HOST_FLAGS := -DBUILD_STORAGE_TEST=1

HOST_LIBS := \
	m \

# We need to statically link openssl into the host tool in case the version
# we're building with is unavailable on the host it will be running on.
HOST_DEPS := \
	trusty/user/base/host/boringssl

include make/host_tool.mk
