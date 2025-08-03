# Copyright (c) 2020, Google, Inc. All rights reserved
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

LOCAL_DIR := $(GET_LOCAL_DIR)

LIB_BACKTRACE_ARCHS := \
	arm \
	arm64 \

LIB_BACKTRACE_ARCH_SUPPORTED ?= \
	$(if $(filter $(LIB_BACKTRACE_ARCHS),$(ARCH)),true,false)

LIB_BACKTRACE_ENABLE ?= $(LIB_BACKTRACE_ARCH_SUPPORTED)

ifeq (true,$(call TOBOOL,$(LIB_BACKTRACE_ENABLE)))
MODULE := $(LOCAL_DIR)

MODULE_DEPS := \
	trusty/kernel/lib/libc-ext \
	trusty/kernel/lib/trusty \

MODULE_SRCS := \
	$(LOCAL_DIR)/backtrace.c \
	$(LOCAL_DIR)/symbolize.c \

GLOBAL_DEFINES += LIB_BACKTRACE_ENABLE=1

include $(LOCAL_DIR)/arch/$(ARCH)/rules.mk

include make/module.mk
else
GLOBAL_INCLUDES += $(LOCAL_DIR)/include
endif
