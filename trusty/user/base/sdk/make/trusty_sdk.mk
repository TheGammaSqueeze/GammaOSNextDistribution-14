#
# Copyright (c) 2020, Google, Inc. All rights reserved
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

# Trusty TEE Userspace SDK
#
# This is a skeleton makefile that can be included in your build system to build
# a trusty userspace app.
#
# Inputs:
# BUILDDIR : Build directory, defaults to current directory
# TRUSTY_APP_NAME : Simple name of app (without the path to the source
# 		directory) (required)
# TRUSTY_APP_OBJECTS : Object files or archives to include in the app
# TRUSTY_APP_LIBRARIES : Trusty SDK libraries to statically link into the app
# TRUSTY_APP_LDFLAGS : LDFLAGS for the app
# TRUSTY_APP_ALIGNMENT : Alignment of app image (defaults to 1)
# TRUSTY_APP_MEMBASE : App base address, if fixed
# TRUSTY_APP_SIGN_KEY_ID : Key ID to use for a loadable app signature
# TRUSTY_APP_SIGN_PRIVATE_KEY_FILE : Path to the private key for the specified
#       key ID
# TRUSTY_APP_SYMTAB_ENABLED : If true do not strip symbols from the resulting app
# 		binary
# MANIFEST : App manifest JSON file
# MODULE_CONSTANTS : JSON files with constants used for both the manifest and C headers
# CLANG_BINDIR : Location of the bin/ directory of the clang to use. (Must be the
# 		same version used to compile the SDK.) Defaults to `toolchain/clang/bin`
# 		inside the SDK.
# PY3 : Path to the Python 3 interpreter to use. Defaults to the `python3` found
#       in $PATH. If the installed `python3` is older than the one Trusty used to
#       build the SDK, some scripts used in the build process may fail.


# Provide an error message if this makefile is run directly instead of included
# into another build.
ifeq ($(words $(MAKEFILE_LIST)),1)
$(warning This makefile should not be invoked directly, please include it in a larger build system.)
endif

BUILDDIR ?= .

# Set up SDK paths
LOCAL_DIR := $(patsubst %/,%,$(dir $(word $(words $(MAKEFILE_LIST)),$(MAKEFILE_LIST))))
TRUSTY_APP_ARCH := $(notdir $(LOCAL_DIR))
TRUSTY_APP_BUILDDIR := $(BUILDDIR)
SDK_DIR := $(LOCAL_DIR)/../../
SDK_SYSROOT_DIR := $(SDK_DIR)/sysroots/$(TRUSTY_APP_ARCH)/
LOADABLE_APP_TOOL := $(SDK_SYSROOT_DIR)/tools/apploader_package_tool

ifeq ($(CLANG_BINDIR),)
CLANG_BINDIR := $(SDK_DIR)/toolchain/clang/bin/
$(warning No $$CLANG_BINDIR provided; using the default: $(CLANG_BINDIR))
endif

ifeq ($(PY3),)
PY3 := $(shell which python3)
$(warning No $$PY3 provided; using python3 from $$PATH: $(PY3))
endif

ARCH_arm_TOOLCHAIN_PREFIX := $(CLANG_BINDIR)/llvm-
ARCH_arm64_TOOLCHAIN_PREFIX := $(CLANG_BINDIR)/llvm-
MANIFEST_COMPILER := $(SDK_SYSROOT_DIR)/tools/manifest_compiler.py

# Use the Trusty toolchain compiler and linker
CC := $(CLANG_BINDIR)/clang
CXX := $(CLANG_BINDIR)/clang++
LD := $(CLANG_BINDIR)/ld.lld

CFLAGS += --sysroot=$(SDK_SYSROOT_DIR) -isystem $(SDK_SYSROOT_DIR)
CXXFLAGS += --sysroot=$(SDK_SYSROOT_DIR) -isystem $(SDK_SYSROOT_DIR)
ASMFLAGS += --sysroot=$(SDK_SYSROOT_DIR) -isystem $(SDK_SYSROOT_DIR)

# We're building for the Trusty userspace, so indicate this for headers that
# depend on this define.
DEFINES += TRUSTY_USERSPACE=1

# Link against Trusty libraries
TRUSTY_APP_LDFLAGS += -L$(SDK_SYSROOT_DIR)/usr/lib/

# Sign loadable apps with the included dev test key by default
ifneq ($(strip $(TRUSTY_APP_SIGN_KEY_ID)),)
APPLOADER_SIGN_KEY_ID := $(TRUSTY_APP_SIGN_KEY_ID)
APPLOADER_SIGN_PRIVATE_KEY_$(TRUSTY_APP_SIGN_KEY_ID)_FILE := $(TRUSTY_APP_SIGN_PRIVATE_KEY_FILE)
else
APPLOADER_SIGN_KEY_ID := 0
APPLOADER_SIGN_PRIVATE_KEY_0_FILE := $(SDK_SYSROOT_DIR)/tools/apploader_sign_test_private_key_0.der
endif

# Define macros from macros.mk needed by trusted_app.mk

# makes sure the target dir exists
MKDIR = if [ ! -d $(dir $@) ]; then mkdir -p $(dir $@); fi

# converts specified variable to boolean value
TOBOOL = $(if $(filter-out 0 false,$1),true,false)

# Add flags for a Trusty userspace library
# $(1): library name, e.g. libc-trusty
define add-trusty-library
$(eval include $(LOCAL_DIR)/$(1).mk)
endef

$(foreach lib,$(TRUSTY_APP_LIBRARIES),$(call add-trusty-library,$(lib)))

# Add defines to {C,CXX,ASM}FLAGS since most makefiles will not pick up defines
# from DEFINES
CFLAGS := $(addprefix -D,$(DEFINES)) $(CFLAGS)
CXXFLAGS := $(addprefix -D,$(DEFINES)) $(CXXFLAGS)
ASMFLAGS := $(addprefix -D,$(DEFINES)) $(ASMFLAGS)

# Set up variables for trusted_app.mk
CLANGBUILD := true
EXTRA_BUILDDEPS :=
ALLMODULE_OBJS := $(TRUSTY_APP_OBJECTS)
TRUSTY_USERSPACE := true
