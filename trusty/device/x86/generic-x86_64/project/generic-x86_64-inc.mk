# Copyright (c) 2012-2018 LK Trusty Authors. All Rights Reserved.
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

ARCH := x86
SUBARCH := x86-64
TARGET := generic-x86_64

WITH_LINKER_GC := 1

TRUSTY_USER_ARCH := x86

# Limit heap grows
GLOBAL_DEFINES += HEAP_GROW_SIZE=8192

# Use lk libc in the kernel for now. musl does not currently link
LK_LIBC_IMPLEMENTATION := lk

WITH_TRUSTY_VIRTIO_IPC_DEV ?= false

# Derive RPMB key using HKDF
WITH_HKDF_RPMB_KEY ?= true

# Always allow provisioning for emulator builds
STATIC_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED := 1

MODULES += \
	trusty/kernel/lib/trusty \
	trusty/kernel/services/apploader \

#
## user tasks to be compiled into lk.bin
#

## prebuilt
TRUSTY_PREBUILT_USER_TASKS :=

# compiled from source
TRUSTY_BUILTIN_USER_TASKS := \
	trusty/user/app/gatekeeper \
	trusty/user/app/keymaster \
	trusty/user/app/sample/hwaes \
	trusty/user/app/sample/hwbcc \
	trusty/user/app/sample/hwcrypto \
	trusty/user/app/storage \
	trusty/user/base/app/apploader \
	trusty/user/base/app/system_state_server_static \

WITH_TRUSTY_IPC := true

# On generic-x86_64 hwcrypto requires FAKE HWRNG and HWKEY services
WITH_FAKE_HWRNG ?= true
WITH_FAKE_HWKEY ?= true

ASLR ?= false

EXTRA_BUILDRULES += trusty/kernel/app/trusty/user-tasks.mk
