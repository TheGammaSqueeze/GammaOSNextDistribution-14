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

#
# This .mk is included both by rules.mk and add-dependency.mk
# and exports the following variables:
#
# SYSCALL_MODULE      - syscall stub module
# SYSCALL_SRCS_DIR    - location of generated sources
# SYSCALL_{H,S,RS}    - names of individual generated files
# SYSCALL_SRCS        - list of all generated files
#

#
SYSCALL_MODULE := $(GET_LOCAL_DIR)

# Location of generated sources
SYSCALL_SRCS_DIR := $(TRUSTY_TOP_LEVEL_BUILDDIR)/generated/$(SYSCALL_MODULE)

# Need to generate these
SYSCALL_H := $(SYSCALL_SRCS_DIR)/trusty_syscalls.h
SYSCALL_S := $(SYSCALL_SRCS_DIR)/trusty_syscalls.S
SYSCALL_RS := $(SYSCALL_SRCS_DIR)/trusty_syscalls.rs

# all generated files
SYSCALL_SRCS := $(SYSCALL_H) $(SYSCALL_S) $(SYSCALL_RS)
