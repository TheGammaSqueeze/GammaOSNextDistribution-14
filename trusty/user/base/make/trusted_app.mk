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

# Build a userspace app.
#
# This file must be included at the end of userspace app rules.mk files.
#
# args:
# MODULE : module name (required)
# TRUSTY_APP_NAME : Simple name of app (without the path to the source
# 		directory)
# TRUSTY_APP_BUILDDIR : Build directory for trusty apps (app will be built in
# 		$(TRUSTY_APP_BUILDDIR)/$(MODULE))
# TRUSTY_APP_IN_TREE : Boolean indicating if the app is being built in-tree
# MANIFEST : App manifest JSON file
# MANIFEST_OVERLAY : Additional manifest overlay JSON file(s)
# MODULE_CONSTANTS : JSON files with constants used for both the manifest and C
# 		headers (optional) (CONSTANTS is a deprecated equivalent to
# 		MODULE_CONSTANTS)
# TRUSTY_APP_ALLOCATOR : the userspace memory allocator to utilize in the app.
#		Currently dlmalloc and scudo are supported. If unset, dlmalloc will be
#		used.
#
# The following input arguments control app linking behavior and are not cleared
# after building the app:
# TRUSTY_APP_BASE_LDFLAGS : LDFLAGS for the app
# TRUSTY_APP_ALIGNMENT : Alignment of app image (defaults to 1)
# TRUSTY_APP_MEMBASE : App base address, if fixed
# TRUSTY_APP_SYMTAB_ENABLED : If true do not strip symbols from the
# 		resulting app binary
#
#
# All library.mk input variables are also valid for apps when building in tree,
# see library.mk for additional args and usage.

ifeq (true,$(call TOBOOL,$(MODULE_ADD_IMPLICIT_DEPS)))
# Accept explicitly-set scudo or dlmalloc allocators,
# default to dlmalloc if TRUSTY_APP_ALLOCATOR is empty,
# error if non-supported allocator is explicitly specified
ifeq (,$(TRUSTY_APP_ALLOCATOR))
TRUSTY_APP_ALLOCATOR_FOR_$(MODULE) ?= dlmalloc
TRUSTY_APP_ALLOCATOR := $(TRUSTY_APP_ALLOCATOR_FOR_$(MODULE))
endif

ifneq (,$(findstring /scudo/,$(TRUSTY_APP_ALLOCATOR)))
MODULE_LIBRARY_DEPS := $(MODULE_LIBRARY_DEPS) $(TRUSTY_APP_ALLOCATOR)
TRUSTY_APP_ALLOCATOR := scudo
else
ifeq ($(TRUSTY_APP_ALLOCATOR),$(filter $(TRUSTY_APP_ALLOCATOR),dlmalloc scudo ))
ifeq (scudo,$(TRUSTY_APP_ALLOCATOR))
MODULE_LIBRARY_DEPS += \
    trusty/user/base/lib/scudo
endif
ifeq (dlmalloc,$(TRUSTY_APP_ALLOCATOR))
MODULE_LIBRARY_DEPS += \
    trusty/user/base/lib/dlmalloc
endif
else
$(error $(TRUSTY_APP_ALLOCATOR) not a supported allocator)
endif
endif
endif

ifeq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
MODULE_LIBRARY_DEPS += trusty/user/base/lib/line-coverage
endif

MANIFEST_ALLOCATOR := $(subst .json,-$(TRUSTY_APP_ALLOCATOR).json,$(MANIFEST) $(MANIFEST_OVERLAY))
# append allocator-based manifest to the end, so it overrides base manifest values
MANIFEST_OVERLAY += $(wildcard $(MANIFEST_ALLOCATOR))

ifeq ($(strip $(TRUSTY_APP_NAME)),)
TRUSTY_APP_NAME := $(notdir $(MODULE))
endif

TRUSTY_APP := true
BUILDDIR := $(TRUSTY_APP_BUILDDIR)/$(MODULE)
TRUSTY_APP_LOG_NAME := $(MODULE)

TRUSTY_APP_ELF := $(BUILDDIR)/$(TRUSTY_APP_NAME).elf

# Save the path to the output elf file for user-tasks.mk
_MODULES_$(MODULE)_TRUSTY_APP_ELF := $(TRUSTY_APP_ELF)

ifneq ($(filter-out bin,$(MODULE_RUST_CRATE_TYPES)),)
$(error $(MODULE) is an app but MODULE_RUST_CRATE_TYPES is not set to "bin")
endif

MODULE_RUST_CRATE_TYPES := bin

# Only include the rest of the build system if we're building in-tree
ifeq ($(call TOBOOL,$(TRUSTY_APP_IN_TREE)),true)
include make/library.mk
else
# Generate manifest binary if building with the SDK
-include $(SDK_DIR)/make/gen_manifest.mk
all:: $(MODULE_SRCDEPS)
endif

TRUSTY_APP_LDFLAGS := $(TRUSTY_APP_BASE_LDFLAGS)

ifeq ($(TRUSTY_APP_ALIGNMENT), )
TRUSTY_APP_ALIGNMENT := 1
endif

# If ASLR is disabled, don't make PIEs, it burns space
ifneq ($(ASLR), false)
    # Generate PIE code to allow ASLR to be applied
    ifeq ($(call TOBOOL,$(TRUSTY_USERSPACE)),true)
        TRUSTY_APP_LDFLAGS += -static -pie --no-dynamic-linker -z text -Bsymbolic
    endif
endif

TRUSTY_APP_TOOLCHAIN_PREFIX := $(ARCH_$(ARCH)_TOOLCHAIN_PREFIX)

TRUSTY_APP_LD := $(CLANG_BINDIR)/ld.lld
TRUSTY_APP_OBJCOPY := $(CLANG_BINDIR)/llvm-objcopy
TRUSTY_APP_STRIP := $(CLANG_BINDIR)/llvm-strip


# App build rules
TRUSTY_APP_BIN := $(BUILDDIR)/$(TRUSTY_APP_NAME).bin
TRUSTY_APP_LST := $(BUILDDIR)/$(TRUSTY_APP_NAME).lst
TRUSTY_APP_SYMS_ELF := $(BUILDDIR)/$(TRUSTY_APP_NAME).syms.elf
TRUSTY_APP_OBJS_NO_WHOLE_ARCHIVES := $(filter-out $(MODULE_WHOLE_ARCHIVES),$(ALLMODULE_OBJS))
TRUSTY_APP_ALL_OBJS := $(TRUSTY_APP_OBJS_NO_WHOLE_ARCHIVES) $(MODULE_EXTRA_OBJECTS)

# Link app elf
ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
MODULE_RUSTFLAGS += \
	-Z pre-link-args="$(filter-out %.rlib %.so,$(MODULE_EXTRA_OBJECTS))" \
	-C link-args="$(filter-out %.rlib %.so,$(TRUSTY_APP_OBJS_NO_WHOLE_ARCHIVES)) $(TRUSTY_APP_LIBGCC)" \
	-C link-args="--push-state --whole-archive $(filter-out %.rlib %.so,$(MODULE_WHOLE_ARCHIVES)) --pop-state" \
	-C link-args="$(TRUSTY_APP_LDFLAGS) $(MODULE_LDFLAGS)" \

$(TRUSTY_APP_SYMS_ELF).d:

$(TRUSTY_APP_SYMS_ELF): GLOBAL_RUSTFLAGS := $(GLOBAL_SHARED_RUSTFLAGS) $(GLOBAL_USER_RUSTFLAGS)
$(TRUSTY_APP_SYMS_ELF): ARCH_RUSTFLAGS := $(ARCH_$(ARCH)_RUSTFLAGS)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LOG_NAME := $(TRUSTY_APP_LOG_NAME)
$(TRUSTY_APP_SYMS_ELF): MODULE_RUSTFLAGS := $(MODULE_RUSTFLAGS) --crate-type=bin
$(TRUSTY_APP_SYMS_ELF): MODULE_RUST_ENV := $(MODULE_RUST_ENV)
$(TRUSTY_APP_SYMS_ELF): MODULE_SDK_LIBS := $(MODULE_SDK_LIBS)
$(TRUSTY_APP_SYMS_ELF): $(TRUSTY_APP_RUST_MAIN_SRC) $(TRUSTY_APP_RUST_SRCDEPS) $(TRUSTY_APP_ALL_OBJS) $(MODULE_SDK_LIBS) $(TRUSTY_APP_SYMS_ELF).d
	@$(MKDIR)
	@$(call ECHO,$(TRUSTY_APP_LOG_NAME),compiling,$<)
	$(NOECHO)$(MODULE_RUST_ENV) $(RUSTC) $(GLOBAL_RUSTFLAGS) $(ARCH_RUSTFLAGS) $(MODULE_RUSTFLAGS) $< --emit "dep-info=$@.d" -o $@
	@$(call ECHO_DONE_SILENT,$(TRUSTY_APP_LOG_NAME),compiling,$<)

-include $(TRUSTY_APP_SYMS_ELF).d

else

$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LD := $(TRUSTY_APP_LD)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LIBGCC := $(TRUSTY_APP_LIBGCC)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LIBCOV := $(TRUSTY_APP_LIBCOV)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LDFLAGS := $(TRUSTY_APP_LDFLAGS)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_MEMBASE := $(TRUSTY_APP_MEMBASE)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_ALL_OBJS := $(TRUSTY_APP_ALL_OBJS)
$(TRUSTY_APP_SYMS_ELF): TRUSTY_APP_LOG_NAME := $(TRUSTY_APP_LOG_NAME)
$(TRUSTY_APP_SYMS_ELF): MODULE_LDFLAGS := $(MODULE_LDFLAGS)
$(TRUSTY_APP_SYMS_ELF): MODULE_WHOLE_ARCHIVES := $(MODULE_WHOLE_ARCHIVES)
$(TRUSTY_APP_SYMS_ELF): MODULE_SDK_LIBS := $(MODULE_SDK_LIBS)
$(TRUSTY_APP_SYMS_ELF): $(TRUSTY_APP_ALL_OBJS) $(MODULE_WHOLE_ARCHIVES) $(MODULE_SDK_LIBS)
	@$(MKDIR)
	@$(call ECHO,$(TRUSTY_APP_LOG_NAME),linking,$@)
	$(NOECHO)$(TRUSTY_APP_LD) $(TRUSTY_APP_LDFLAGS) $(MODULE_LDFLAGS) $(addprefix -Ttext ,$(TRUSTY_APP_MEMBASE)) --start-group $(TRUSTY_APP_ALL_OBJS) $(TRUSTY_APP_LIBGCC) $(TRUSTY_APP_LIBCOV) --whole-archive $(MODULE_WHOLE_ARCHIVES) --end-group -o $@
	@$(call ECHO_DONE_SILENT,$(TRUSTY_APP_LOG_NAME),linking,$@)
endif

ifeq ($(call TOBOOL,$(TRUSTY_APP_SYMTAB_ENABLED)),true)
TRUSTY_APP_STRIPFLAGS := --strip-debug
else
TRUSTY_APP_STRIPFLAGS := -s
endif

ifeq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
TRUSTY_APP_STRIPFLAGS += \
    --remove-section=__llvm_covfun \
    --remove-section=__llvm_covmap
endif

# And strip it and pad with zeros to be page aligned
$(TRUSTY_APP_ELF): TRUSTY_APP_STRIP := $(TRUSTY_APP_STRIP)
$(TRUSTY_APP_ELF): TRUSTY_APP_ALIGNMENT := $(TRUSTY_APP_ALIGNMENT)
$(TRUSTY_APP_ELF): TRUSTY_APP_STRIPFLAGS := $(TRUSTY_APP_STRIPFLAGS)
$(TRUSTY_APP_ELF): TRUSTY_APP_LOG_NAME := $(TRUSTY_APP_LOG_NAME)
$(TRUSTY_APP_ELF): $(TRUSTY_APP_SYMS_ELF)
	@$(MKDIR)
	@$(call ECHO,$(TRUSTY_APP_LOG_NAME),stripping and page aligning,$<)
	$(NOECHO)$(TRUSTY_APP_STRIP) $(TRUSTY_APP_STRIPFLAGS) $< -o $@
	$(NOECHO)truncate -s %$(TRUSTY_APP_ALIGNMENT) $@
	@$(call ECHO_DONE_SILENT,$(TRUSTY_APP_LOG_NAME),stripping and page aligning,$<)

# build app binary
$(TRUSTY_APP_BIN): TRUSTY_APP_OBJCOPY := $(TRUSTY_APP_OBJCOPY)
$(TRUSTY_APP_BIN): TRUSTY_APP_LOG_NAME := $(TRUSTY_APP_LOG_NAME)
$(TRUSTY_APP_BIN): $(TRUSTY_APP_ELF)
	@$(call ECHO,$(TRUSTY_APP_LOG_NAME),generating image,$@)
	$(NOECHO)$(TRUSTY_APP_OBJCOPY) -O binary $< $@
	@$(call ECHO_DONE_SILENT,$(TRUSTY_APP_LOG_NAME),generating image,$@)

# Generate listing file for the app
$(TRUSTY_APP_LST): $(TRUSTY_APP_SYMS_ELF)
	@$(call ECHO,$(TRUSTY_APP_LOG_NAME),generating lst,$@)
	$(NOECHO)$(OBJDUMP) -dS $< > $@ 2> /dev/null
	@$(call ECHO_DONE_SILENT,$(TRUSTY_APP_LOG_NAME),generating lst$@)


# Also generate listings
all:: $(TRUSTY_APP_BIN) $(TRUSTY_APP_MANIFEST_BIN) $(TRUSTY_APP_ELF) $(TRUSTY_APP_LST)

# Reset local variables
TRUSTY_APP :=
TRUSTY_APP_NAME :=
TRUSTY_APP_ALLOCATOR :=

TRUSTY_APP_BIN :=
TRUSTY_APP_ELF :=
TRUSTY_APP_LST :=
TRUSTY_APP_SYMS_ELF :=
TRUSTY_APP_OBJS_NO_WHOLE_ARCHIVES :=
TRUSTY_APP_ALL_OBJS :=
TRUSTY_APP_CONFIGHEADER :=

TRUSTY_APP_TOOLCHAIN_PREFIX :=
TRUSTY_APP_CC :=
TRUSTY_APP_LD :=
TRUSTY_APP_LDFLAGS :=
TRUSTY_APP_OBJCOPY :=
TRUSTY_APP_STRIP :=
TRUSTY_APP_STRIPFLAGS :=
TRUSTY_APP_RUST_MAIN_SRC :=
TRUSTY_APP_RUST_SRCDEPS :=

TRUSTY_APP_APP :=

TRUSTY_APP_MANIFEST_BIN :=
TRUSTY_APP_DISABLE_SCS :=

ALLMODULE_OBJS :=

MODULE_CONSTANTS :=
MODULE_LDFLAGS :=
MODULE_WHOLE_ARCHIVES :=
MODULE_RUSTFLAGS :=
MODULE_RUST_ENV :=
MODULE_SDK_LIBS :=

MANIFEST_COMPILER :=
