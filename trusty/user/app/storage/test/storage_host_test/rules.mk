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
COMMON_DIR := $(LOCAL_DIR)/../common
STORAGE_DIR := $(LOCAL_DIR)/../..

HOST_TEST := storage_host_test

HOST_SRCS := \
	$(STORAGE_DIR)/block_allocator.c \
	$(STORAGE_DIR)/block_cache.c \
	$(STORAGE_DIR)/block_device_tipc.c \
	$(STORAGE_DIR)/block_mac.c \
	$(STORAGE_DIR)/block_map.c \
	$(STORAGE_DIR)/block_set.c \
	$(STORAGE_DIR)/block_tree.c \
	$(STORAGE_DIR)/checkpoint.c \
	$(STORAGE_DIR)/crypt.c \
	$(STORAGE_DIR)/file.c \
	$(STORAGE_DIR)/rpmb_dev/rpmb_dev.c \
	$(STORAGE_DIR)/rpmb.c \
	$(STORAGE_DIR)/super.c \
	$(STORAGE_DIR)/transaction.c \
	$(LOCAL_DIR)/library_shims.c \
	$(LOCAL_DIR)/storage_host_test.c \
	$(LOCAL_DIR)/storageproxy_shim.c \
	$(COMMON_DIR)/error_reporting_mock.c \

HOST_INCLUDE_DIRS += \
	$(LOCAL_DIR) \
	$(STORAGE_DIR) \
	$(COMMON_DIR) \
	trusty/user/base/interface/storage/include \

# block_device_tipc.h requires hwkey and system_state for declarations even
# though we aren't linking against it.
HOST_INCLUDE_DIRS += \
	trusty/user/base/interface/hwkey/include \
	trusty/user/base/interface/system_state/include \
	trusty/user/base/lib/hwkey/include \
	trusty/user/base/lib/system_state/include \

# Turn on FULL_ASSERTs
HOST_FLAGS := -DBUILD_STORAGE_TEST=1

STORAGE_RPMB_PROTOCOL ?= MMC
HOST_FLAGS += \
	-DRPMB_PROTOCOL=RPMB_PROTOCOL_$(STORAGE_RPMB_PROTOCOL) \

HOST_FLAGS += -DSTORAGE_NS_RECOVERY_CLEAR_ALLOWED=1
HOST_FLAGS += -DSTORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED=1
HOST_FLAGS += -DHAS_FS_TDP=1

HOST_LIBS := \
	m

HOST_DEPS := \
	trusty/user/base/host/boringssl

include make/host_test.mk
