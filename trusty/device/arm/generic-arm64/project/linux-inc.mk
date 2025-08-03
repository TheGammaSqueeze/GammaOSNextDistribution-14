#
# Copyright (c) 2019, Google, Inc. All rights reserved
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

# Inputs:
# LINUX_ARCH contains the architecture to build for (Global)
# Outputs:
# LINUX_BUILD_DIR contains the path to the built linux kernel sources
# LINUX_IMAGE path of the final linux image target

# This Makefile will build the Linux kernel with our configuration.

LINUX_BUILD_DIR := $(abspath $(BUILDDIR)/linux-build)
ifndef LINUX_ARCH
	$(error LINUX_ARCH must be specified)
endif

ifeq ($(LINUX_ARCH),arm)
LINUX_CLANG_TRIPLE := $(LINUX_ARCH)-linux-gnueabi-
else
LINUX_CLANG_TRIPLE := $(LINUX_ARCH)-linux-gnu-
endif

LINUX_SRC := $(call FIND_EXTERNAL,linux)
LINUX_CONFIG_DIR = $(LINUX_SRC)/arch/$(LINUX_ARCH)/configs

# Preserve compatibility with architectures without GKI
ifeq (,$(wildcard $(LINUX_CONFIG_DIR)/gki_defconfig))
LINUX_DEFCONFIG_FRAGMENTS := \
	$(LINUX_CONFIG_DIR)/trusty_qemu_defconfig \

else
LINUX_DEFCONFIG_FRAGMENTS := \
	$(LINUX_CONFIG_DIR)/gki_defconfig \
	$(LINUX_CONFIG_DIR)/trusty_qemu_defconfig.fragment \

endif

LINUX_IMAGE := $(LINUX_BUILD_DIR)/arch/$(LINUX_ARCH)/boot/Image

$(LINUX_IMAGE): LINUX_TMP_DEFCONFIG := $(LINUX_CONFIG_DIR)/tmp_defconfig
$(LINUX_IMAGE): LINUX_SRC := $(LINUX_SRC)
$(LINUX_IMAGE): LINUX_DEFCONFIG_FRAGMENTS := $(LINUX_DEFCONFIG_FRAGMENTS)
$(LINUX_IMAGE): LINUX_MAKE_ARGS := -C $(LINUX_SRC)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += O=$(LINUX_BUILD_DIR)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += ARCH=$(LINUX_ARCH)

# Preserve compatibility with older linux kernel
ifeq (,$(wildcard $(LINUX_SRC)/Documentation/kbuild/llvm.rst))
$(LINUX_IMAGE): CLANG_BINDIR := $(CLANG_BINDIR)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += CROSS_COMPILE=$(ARCH_$(LINUX_ARCH)_TOOLCHAIN_PREFIX)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += CC=$(CLANG_BINDIR)/clang
$(LINUX_IMAGE): LINUX_MAKE_ARGS += LD=$(CLANG_BINDIR)/ld.lld
$(LINUX_IMAGE): LINUX_MAKE_ARGS += CLANG_TRIPLE=$(LINUX_CLANG_TRIPLE)
else
# Newer linux kernel versions need a newer toolchain (optionally specified in
# LINUX_CLANG_BINDIR) than the older linux kernel needs or supports.
LINUX_CLANG_BINDIR ?= $(CLANG_BINDIR)
$(LINUX_IMAGE): CLANG_BINDIR := $(LINUX_CLANG_BINDIR)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += CROSS_COMPILE=$(LINUX_CLANG_TRIPLE)
$(LINUX_IMAGE): LINUX_MAKE_ARGS += LLVM=1
$(LINUX_IMAGE): LINUX_MAKE_ARGS += LLVM_IAS=1
endif

$(LINUX_IMAGE): LINUX_MAKE_ARGS += LEX=$(BUILDTOOLS_BINDIR)/flex
$(LINUX_IMAGE): LINUX_MAKE_ARGS += YACC=$(BUILDTOOLS_BINDIR)/bison
$(LINUX_IMAGE): LINUX_MAKE_ARGS += BISON_PKGDATADIR=$(BUILDTOOLS_COMMON)/bison
$(LINUX_IMAGE): .PHONY
	KCONFIG_CONFIG=$(LINUX_TMP_DEFCONFIG) $(LINUX_SRC)/scripts/kconfig/merge_config.sh -m -r $(LINUX_DEFCONFIG_FRAGMENTS)
	PATH=$(CLANG_BINDIR):$(PATH) $(MAKE) $(LINUX_MAKE_ARGS) $(notdir $(LINUX_TMP_DEFCONFIG))
	rm $(LINUX_TMP_DEFCONFIG)
	PATH=$(CLANG_BINDIR):$(PATH) $(MAKE) $(LINUX_MAKE_ARGS)

# Add LINUX_IMAGE to the list of project dependencies
EXTRA_BUILDDEPS += $(LINUX_IMAGE)

LINUX_DEFCONFIG_FRAGMENTS :=
LINUX_CONFIG_DIR :=
LINUX_SRC :=
LINUX_CLANG_TRIPLE :=
