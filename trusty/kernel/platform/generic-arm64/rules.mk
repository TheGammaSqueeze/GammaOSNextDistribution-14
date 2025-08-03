#
# Copyright (c) 2015, Google, Inc. All rights reserved
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

ifeq (false,$(call TOBOOL,$(KERNEL_32BIT)))
ARCH := arm64
else
ARCH := arm
endif
ARM_CPU := armv8-a
WITH_SMP := 1

ifneq (2,$(GIC_VERSION))
ARM_MERGE_FIQ_IRQ := true
endif

MEMBASE ?= 0
MEMSIZE ?= 1

GLOBAL_INCLUDES += \
	$(LOCAL_DIR)/include

MODULE_DEFINES += \
	GIC_VERSION=$(GIC_VERSION) \

MODULE_SRCS += \
	$(LOCAL_DIR)/debug.c \
	$(LOCAL_DIR)/dtb.c \
	$(LOCAL_DIR)/platform.c \
	$(LOCAL_DIR)/smc.c \

MODULE_DEPS += \
	dev/interrupt/arm_gic \
	dev/timer/arm_generic

GLOBAL_DEFINES += \
	MEMBASE=$(MEMBASE) \
	MEMSIZE=$(MEMSIZE) \
	MMU_WITH_TRAMPOLINE=1

LINKER_SCRIPT += \
	$(BUILDDIR)/system-onesegment.ld

# SMC service support
MODULE_INCLUDES += \
	$(TRUSTY_TOP)/trusty/kernel/services/smc/include \
	$(TRUSTY_TOP)/trusty/kernel/include \
	$(TRUSTY_TOP)/trusty/user/base/lib/smc/include \

MODULE_SRCS += \
	$(LOCAL_DIR)/smc_service_access_policy.c \

MODULE_DEPS += \
	trusty/kernel/lib/dtb_embedded \
	trusty/kernel/lib/dtb_service \
	trusty/kernel/lib/ktipc \
	external/dtc/libfdt \

include make/module.mk
