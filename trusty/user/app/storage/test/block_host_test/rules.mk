# Copyright (C) 2016 The Android Open Source Project
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
COMMON_DIR := $(LOCAL_DIR)/../common
STORAGE_DIR := $(LOCAL_DIR)/../..

HOST_TEST := storage_block_test

HOST_SRCS := \
	$(STORAGE_DIR)/block_allocator.c \
	$(STORAGE_DIR)/block_cache.c \
	$(STORAGE_DIR)/block_mac.c \
	$(STORAGE_DIR)/block_map.c \
	$(STORAGE_DIR)/block_set.c \
	$(STORAGE_DIR)/block_tree.c \
	$(STORAGE_DIR)/checkpoint.c \
	$(STORAGE_DIR)/crypt.c \
	$(STORAGE_DIR)/file.c \
	$(STORAGE_DIR)/super.c \
	$(STORAGE_DIR)/transaction.c \
	$(LOCAL_DIR)/block_test.c \
	$(COMMON_DIR)/error_reporting_mock.c \

HOST_FLAGS := -DBUILD_STORAGE_TEST=1

HOST_INCLUDE_DIRS += \
	$(STORAGE_DIR) \
	$(COMMON_DIR) \

HOST_LIBS := \
	m \

HOST_DEPS := \
	trusty/user/base/host/boringssl

include make/host_test.mk
