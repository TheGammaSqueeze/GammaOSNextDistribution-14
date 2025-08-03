# Copyright (C) 2015 The Android Open Source Project
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
SMP_MAX_CPUS ?= 8
SMP_CPU_CLUSTER_SHIFT ?= 2
GIC_VERSION ?= 2
# Use modern KM wrapping key size (256-bits)
TRUSTY_KM_WRAPPING_KEY_SIZE ?= 32

TARGET := generic-arm64

ifeq (false,$(call TOBOOL,$(KERNEL_32BIT)))

# Arm64 address space configuration
USER_ASPACE_BASE   := 0x0000000000008000

ifeq (false,$(call TOBOOL,$(USER_32BIT)))
USER_ASPACE_SIZE   := 0x0000ffffffff8000
GLOBAL_DEFINES += MMU_USER_SIZE_SHIFT=48
else
USER_ASPACE_SIZE   := 0x00000000ffff8000
GLOBAL_DEFINES += MMU_USER_SIZE_SHIFT=32
endif

KERNEL_BASE_ASLR   ?= true

else

KERNEL_BASE        := 0xc0000000

# ASLR is allowed on 32-bit platforms, but they are usually more space
# conscious, and the extra page tables and weight from PIE may be more than
# they want to pay.
# Set ASLR := true explicitly if you are a 32-bit platform and want ASLR.
ASLR               ?= false

endif

# select timer
ifeq (true,$(call TOBOOL,$(KERNEL_32BIT)))
# 32 bit Secure EL1 with a 64 bit EL3 gets the non-secure physical timer
GLOBAL_DEFINES += TIMER_ARM_GENERIC_SELECTED=CNTP
else
GLOBAL_DEFINES += TIMER_ARM_GENERIC_SELECTED=CNTPS
endif

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
GLOBAL_DEFINES += HEAP_GROW_SIZE=8192

# enable LTO in user-tasks modules
USER_LTO_ENABLED ?= true

# enable LTO in kernel modules
KERNEL_LTO_ENABLED ?= true

# enable cfi in trusty modules
USER_CFI_ENABLED ?= true
KERNEL_CFI_ENABLED ?= true

# Storage should send error reports to the metrics service
STORAGE_ENABLE_ERROR_REPORTING := true

ifeq ($(shell expr $(DEBUG) \>= 2), 1)
CFI_DIAGNOSTICS ?= true
endif

# disable UBSan by default
UBSAN_ENABLED ?= false
ifeq (true,$(call TOBOOL,$(UBSAN_ENABLED)))
include trusty/kernel/lib/ubsan/enable.mk
endif

ifeq (false,$(call TOBOOL,$(KERNEL_32BIT)))
KERNEL_SCS_ENABLED ?= true
ifeq (false,$(call TOBOOL,$(USER_32BIT)))
# enable shadow call stack in user-tasks modules
USER_SCS_ENABLED ?= true
endif
endif

# fall back to user-space stack protector if user-space SCS is off
ifneq (true,$(call TOBOOL,$(USER_SCS_ENABLED)))
USER_STACK_PROTECTOR ?= true
endif

# Allow the KeyMint TA implementation to be selected at build time.  This needs to be
# done in sync with the HAL service implementation included in Android.  Possible values are:
#
# - Rust implementation:   export TRUSTY_KEYMINT_IMPL=rust
# - C++ implementation:    (any other value of TRUSTY_KEYMINT_IMPL)

ifeq ($(TRUSTY_KEYMINT_IMPL),rust)
    TRUSTY_KEYMINT_USER_TASK := trusty/user/app/keymint/app
else
    # Default to the C++ implementation
    TRUSTY_KEYMINT_USER_TASK := trusty/user/app/keymaster
endif

# Allow inclusion of the Secretkeeper TA to be selected at build time.
ifeq (true,$(call TOBOOL,$(SECRETKEEPER_ENABLED)))
     TRUSTY_SECRETKEEPER_USER_TASK := trusty/user/app/secretkeeper/app
endif

#
# Modules to be compiled into lk.bin
#
MODULES += \
	trusty/kernel/lib/sm \
	trusty/kernel/lib/trusty \
	trusty/kernel/lib/memlog \
	trusty/kernel/lib/metrics \
	trusty/kernel/services/apploader \
	trusty/kernel/services/smc \

#
# Set user space arch
#
ifeq (true,$(call TOBOOL,$(KERNEL_32BIT)))
TRUSTY_USER_ARCH := arm
else
ifeq (true,$(call TOBOOL,$(USER_32BIT)))
TRUSTY_USER_ARCH := arm
GLOBAL_DEFINES += USER_32BIT=1
else
TRUSTY_USER_ARCH := arm64
endif
endif

#
# user tasks to be compiled into lk.bin
#

# prebuilt
TRUSTY_PREBUILT_USER_TASKS :=

# compiled from source
TRUSTY_BUILTIN_USER_TASKS := \
	trusty/user/app/avb \
	trusty/user/app/cast-auth/app \
	trusty/user/app/confirmationui \
	trusty/user/app/gatekeeper \
	$(TRUSTY_KEYMINT_USER_TASK) \
	$(TRUSTY_SECRETKEEPER_USER_TASK) \
	trusty/user/app/sample/hwaes \
	trusty/user/app/sample/hwbcc \
	trusty/user/app/sample/hwcrypto \
	trusty/user/app/sample/hwwsk \
	trusty/user/app/sample/secure_fb_mock_impl \
	trusty/user/app/storage \
	trusty/user/base/app/apploader \
	trusty/user/base/app/device_tree \
	trusty/user/base/app/metrics \
	trusty/user/base/app/system_state_server_static \

MODULES += \
	trusty/user/base/app/device_tree/tests/dtb \
	trusty/user/base/app/device_tree/tests/dtb/base \

ifeq (true,$(call TOBOOL,$(USER_COVERAGE_ENABLED)))
TRUSTY_ALL_USER_TASKS += \
	trusty/user/base/app/coverage \

endif

ifeq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
TRUSTY_ALL_USER_TASKS += \
	trusty/user/base/app/line-coverage \

endif

# on generic-arm64 hwcrypto requires FAKE HWRNG and HWKEY services
WITH_FAKE_HWRNG ?= true
WITH_FAKE_HWKEY ?= true

# This project requires trusty IPC
WITH_TRUSTY_IPC := true

SYMTAB_ENABLED ?= true

# include software implementation of a SPI loopback device
WITH_SW_SPI_LOOPBACK ?= true

EXTRA_BUILDRULES += trusty/kernel/app/trusty/user-tasks.mk
