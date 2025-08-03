# Copyright (C) 2018 The Android Open Source Project
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

# Default syscall stub gen tool
SYSCALL_STUBGEN_TOOL ?= trusty/kernel/lib/syscall/stubgen/stubgen.py

# syscall table definition
SYSCALL_TABLE ?= trusty/kernel/lib/trusty/include/syscall_table.h

# include common-inc.mk to set SYSCALL_SRCS and friends
include $(LOCAL_DIR)/common-inc.mk

$(SYSCALL_S): ARCH:=$(ARCH)
$(SYSCALL_S): SYSCALL_H:=$(SYSCALL_H)
$(SYSCALL_S): SYSCALL_S:=$(SYSCALL_S)
$(SYSCALL_S): SYSCALL_RS:=$(SYSCALL_RS)
$(SYSCALL_S): $(SYSCALL_TABLE) $(SYSCALL_STUBGEN_TOOL)
	@$(MKDIR)
	@echo generating syscalls stubs $@
	$(NOECHO)$(SYSCALL_STUBGEN_TOOL) --arch $(ARCH) -d $(SYSCALL_H) -s $(SYSCALL_S) -r $(SYSCALL_RS) $<

$(SYSCALL_H): $(SYSCALL_S)
$(SYSCALL_RS): $(SYSCALL_S)

# Track them as generated
GENERATED += $(SYSCALL_SRCS)

# Add generated trusty_syscall.S to build
MODULE_SRCS += $(SYSCALL_S)

# Add path to generated .h to module includes.
# Because we are not exporting all generated files to MODULE_EXPORT_INCLUDES
# this module must be part of TRUSTY_SDK_MODULES define in user-tasks.mk
# otherwise build would break.
MODULE_SDK_HEADERS += $(SYSCALL_H)

MODULE_ADD_IMPLICIT_DEPS := false

include make/library.mk
