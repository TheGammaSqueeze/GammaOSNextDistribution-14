# Copyright (C) 2017 The Android Open Source Project
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

DEBUG ?= 2
SMP_MAX_CPUS ?= 2
SMP_CPU_CLUSTER_SHIFT ?= 1
WITH_SMP := 1
KERNEL_32BIT := true

# select timer
GLOBAL_DEFINES += TIMER_ARM_GENERIC_SELECTED=CNTPS

#
# GLOBAL definitions
#

# requires linker GC
WITH_LINKER_GC := 1

# Need support for Non-secure memory mapping
WITH_NS_MAPPING := true

# do not relocate kernel in physical memory
GLOBAL_DEFINES += WITH_NO_PHYS_RELOCATION=1

# limit heap grows
GLOBAL_DEFINES += HEAP_GROW_SIZE=0x400000

# limit physical memory to 38 bit to prevert tt_trampiline from getting larger than arm64_kernel_translation_table
GLOBAL_DEFINES += MMU_IDENT_SIZE_SHIFT=38

# Set max RPMB block to 1024 means it will get 1024*512=512KB space to store critical information.
GLOBAL_DEFINES += APP_STORAGE_RPMB_BLOCK_COUNT=1024

GLOBAL_DEFINES += \
	WITH_LIB_SM_MONITOR=1

GLOBAL_DEFINES += \
	WITH_LIB_VERSION=1

#
# Modules to be compiled into lk.bin
#
MODULES += \
	trusty/kernel/lib/sm \
	trusty/kernel/lib/trusty \
	trusty/kernel/lib/memlog \

TRUSTY_USER_ARCH := arm

#
# user tasks to be compiled into lk.bin
#

# prebuilt
TRUSTY_PREBUILT_USER_TASKS :=

# compiled from source
TRUSTY_ALL_USER_TASKS := \
	trusty/user/app/avb \
	trusty/hardware/nxp/app/hwcrypto \
	trusty/user/app/keymaster \
	trusty/user/app/gatekeeper \
	trusty/user/app/storage \

# This project requires trusty IPC
WITH_TRUSTY_IPC := true

EXTRA_BUILDRULES += trusty/kernel/app/trusty/user-tasks.mk
