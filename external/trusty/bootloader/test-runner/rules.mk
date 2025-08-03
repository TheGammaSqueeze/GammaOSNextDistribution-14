#
# Copyright (c) 2018, Google, Inc. All rights reserved
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

# Override default build directory
TRUSTY_APP_BUILDDIR := $(BUILDDIR)/test-runner

# test-runner does not run in trusty, to enable CFI it depends on
# trusty modules so anything that needs runtime support is probably
# not worth the effort
MODULE_DISABLE_CFI := true

# SCS requires us to allocate a guard region from the heap so it is
# probably not worth the effort for the same reasons that CFI isn't
MODULE_DISABLE_SCS := true

QL_TIPC = $(LOCAL_DIR)/../ql-tipc

MODULE_DEFINES += \
	NS_ARCH_ARM64=1 \

MODULE_INCLUDES += \
	$(LOCAL_DIR)/include \
	$(LOCAL_DIR)/$(ARCH)/include \
	$(QL_TIPC)/include \
	$(LOCAL_DIR)/../interface/include \
	$(LKROOT)/include \
	$(LKROOT)/include/shared/lk \
	$(LKROOT)/lib/libc/include \

MODULE_SRCS += \
	$(LOCAL_DIR)/test-runner.c \
	$(LOCAL_DIR)/test-runner-storage.c \
	$(LOCAL_DIR)/test-runner-sysdeps.c \
	$(LOCAL_DIR)/test-runner-comm-port.c \
	$(LOCAL_DIR)/virtio.c \
	$(LOCAL_DIR)/virtio-console.c \
	$(QL_TIPC)/hwbcc.c \
	$(QL_TIPC)/ipc.c \
	$(QL_TIPC)/ipc_dev.c \
	$(QL_TIPC)/keymaster.c \
	$(QL_TIPC)/keymaster_serializable.c \
	$(QL_TIPC)/rpmb_proxy.c \
	$(QL_TIPC)/secretkeeper.c \
	$(QL_TIPC)/trusty_dev_common.c \
	$(QL_TIPC)/util.c \

ifeq (true,$(call TOBOOL,$(VIRTIO_MMIO_DEVICE)))
MODULE_SRCS += $(LOCAL_DIR)/virtio-mmio.c
else
MODULE_SRCS += $(LOCAL_DIR)/virtio-pci.c
endif

# Do not include implicit dependencies
MODULE_ADD_IMPLICIT_DEPS := false

MODULE_COMPILEFLAGS += -fno-builtin

include $(LOCAL_DIR)/$(ARCH)/rules.mk
include make/trusted_app.mk
