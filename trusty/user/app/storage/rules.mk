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

MODULE := $(LOCAL_DIR)

STORAGE_RPMB_PROTOCOL ?= MMC

CONSTANTS := $(LOCAL_DIR)/storage_consts.json

MANIFEST := $(LOCAL_DIR)/manifest.json

MODULE_DEFINES := \
	RPMB_PROTOCOL=RPMB_PROTOCOL_$(STORAGE_RPMB_PROTOCOL) \

# WITH_HKDF_RPMB_KEY indicates that storage server derives rpmb key
# from a random sequence stored in the rpmb partition itself,
# and that the storage server supports auto provisioning.
# Otherwise, keyslot will provide the rpmb key.
# By default we obtain rpmb key from keyslot.
WITH_HKDF_RPMB_KEY ?= false

ifeq (true,$(call TOBOOL,$(WITH_HKDF_RPMB_KEY)))
    MODULE_DEFINES += WITH_HKDF_RPMB_KEY=1
endif

STORAGE_HAS_FS_TDP ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_HAS_FS_TDP)))
    MODULE_DEFINES += HAS_FS_TDP=1
endif

STORAGE_HAS_FS_NONSECURE_PERSIST ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_HAS_FS_NONSECURE_PERSIST)))
    MODULE_DEFINES += HAS_FS_NSP=1
endif

SS_DATA_DEBUG_IO ?= false
ifeq (true,$(call TOBOOL,$(SS_DATA_DEBUG_IO)))
    MODULE_DEFINES += SS_DATA_DEBUG_IO=1
endif

STORAGE_NS_RECOVERY_CLEAR_ALLOWED ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_NS_RECOVERY_CLEAR_ALLOWED)))
    MODULE_DEFINES += STORAGE_NS_RECOVERY_CLEAR_ALLOWED=1
endif

STORAGE_NS_ALTERNATE_SUPERBLOCK_ALLOWED ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_NS_ALTERNATE_SUPERBLOCK_ALLOWED)))
    MODULE_DEFINES += STORAGE_NS_ALTERNATE_SUPERBLOCK_ALLOWED=1
endif

STORAGE_ENABLE_ERROR_REPORTING ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_ENABLE_ERROR_REPORTING)))
    MODULE_DEFINES += STORAGE_ENABLE_ERROR_REPORTING=1
endif

STORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED)))
    MODULE_DEFINES += STORAGE_TDP_RECOVERY_CHECKPOINT_RESTORE_ALLOWED=1
endif

STORAGE_TDP_AUTO_CHECKPOINT_ENABLED ?= false
ifeq (true,$(call TOBOOL,$(STORAGE_TDP_AUTO_CHECKPOINT_ENABLED)))
    MODULE_DEFINES += STORAGE_TDP_AUTO_CHECKPOINT_ENABLED=1
endif

MODULE_SRCS := \
	$(LOCAL_DIR)/block_allocator.c \
	$(LOCAL_DIR)/block_cache.c \
	$(LOCAL_DIR)/block_device_tipc.c \
	$(LOCAL_DIR)/block_mac.c \
	$(LOCAL_DIR)/block_map.c \
	$(LOCAL_DIR)/block_set.c \
	$(LOCAL_DIR)/block_tree.c \
	$(LOCAL_DIR)/checkpoint.c \
	$(LOCAL_DIR)/client_tipc.c \
	$(LOCAL_DIR)/crypt.c \
	$(LOCAL_DIR)/error_reporting.c \
	$(LOCAL_DIR)/file.c \
	$(LOCAL_DIR)/ipc.c \
	$(LOCAL_DIR)/main.c \
	$(LOCAL_DIR)/proxy.c \
	$(LOCAL_DIR)/rpmb.c \
	$(LOCAL_DIR)/super.c \
	$(LOCAL_DIR)/tipc_ns.c \
	$(LOCAL_DIR)/transaction.c \

MODULE_LIBRARY_DEPS := \
	trusty/user/base/interface/storage \
	trusty/user/base/lib/hwkey \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/system_state \
	external/boringssl \

ifeq (true,$(call TOBOOL,$(STORAGE_ENABLE_ERROR_REPORTING)))
MODULE_LIBRARY_DEPS += \
	trusty/user/base/interface/stats/nw \
	trusty/user/base/interface/stats_setter \
	trusty/user/base/interface/stats/tz \
	trusty/user/base/lib/metrics_atoms \
	trusty/user/base/lib/stats
endif

MODULE_DEPS += \
	trusty/user/app/storage/test/block_host_test \
	trusty/user/app/storage/test/storage_host_test \

include make/trusted_app.mk

# Build host side unit tests for mock storage implementation.
include trusty/user/app/storage/storage_mock/test_mock_storage_rules.mk
