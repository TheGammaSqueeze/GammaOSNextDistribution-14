# Copyright (C) 2013-2015 The Android Open Source Project
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

TARGET := vexpress-a15

KERNEL_32BIT := true

#
# GLOBAL definitions
#

# requires linker GC
WITH_LINKER_GC := 1

# force enums to be 4bytes
ARCH_arm_COMPILEFLAGS := -mabi=aapcs-linux

# Need support for Non-secure memory mapping
WITH_NS_MAPPING := true

# This project requires trusty IPC
WITH_TRUSTY_IPC := true

# do not relocate kernel in physical memory
GLOBAL_DEFINES += WITH_NO_PHYS_RELOCATION=1

# limit heap grows
GLOBAL_DEFINES += HEAP_GROW_SIZE=65536

GLOBAL_DEFINES += \
	WITH_LIB_SM_MONITOR=1

#
# Modules to be compiled into lk.bin
#
MODULES += \
	trusty/kernel/lib/sm \
	trusty/kernel/lib/trusty \

TRUSTY_USER_ARCH := arm

#
# user tasks to be compiled into lk.bin
#

# prebuilt
TRUSTY_PREBUILT_USER_TASKS :=

# compiled from source
TRUSTY_ALL_USER_TASKS := \
	trusty/user/app/sample/skel \
	trusty/user/app/sample/skel2\
	trusty/user/app/sample/timer\

EXTRA_BUILDRULES += trusty/kernel/app/trusty/user-tasks.mk
