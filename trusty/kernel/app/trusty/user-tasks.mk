#
# Copyright (c) 2014-2018, Google, Inc. All rights reserved
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

#
# Input variables
#
#   ALLHOSTMODULES             - list of all host modules (e.g. Rust proc-macros) to be built
#   TRUSTY_BUILTIN_USER_TASKS  - list of compiled from source user tasks to be included into final image
#   TRUSTY_PREBUILT_USER_TASKS - list of precompiled user tasks to be included into final image
#   	These prebuilt task modules must include a manifest binary and app elf binary, e.g.:
#   		TRUSTY_PREBUILT_USER_TASKS += trusty/app/some_prebuilt_app
#
#			Add the following files from the pre-compiled app:
#			- trusty/app/some_prebuilt_app/some_prebuilt_app.elf
#			- trusty/app/some_prebuilt_app/some_prebuilt_app.manifest
#	TRUSTY_BUILTIN_USER_TESTS - list of user test to include in the final image.
#                               These tests must also be included in
#                               TRUSTY_USER_TESTS or TRUSTY_RUST_USER_TESTS to
#                               be built. If not set, defaults to including all
#                               built tests in the image. Projects may set this
#                               variable to a subset of the available tests, but
#                               this should be done with the ?= assignment to
#                               still allow overriding via environment variable.
#

$(call INFO_LOG,Include Trusty user tasks support)

TRUSTY_APP_DIR := $(GET_LOCAL_DIR)

# generate trusty app or library build rules:
# $(1): path to app source dir (module name)
#
# Note: this function must be eval'd after calling it
#
# Other input variables, shared across all apps
# TRUSTY_APP_BASE_LDFLAGS: LDFLAGS for the app
# ARCH: Architecture of the app
# TRUSTY_APP_ALIGNMENT: Alignment of app image (defaults to 1)
# TRUSTY_APP_MEMBASE: App base address, if fixed
# TRUSTY_APP_SYMTAB_ENABLED: If true do not strip symbols from the
# 		resulting app binary
# TRUSTY_USERSPACE: Boolean indicating that the app should be built for the
# 		trusty userspace
#
define trusty-build-rule
# MODULE should be set to the parent module when including userspace_recurse.mk.
# In this case we are trying to build a top-level app or library, and need to
# isolate this build from the kernel build. In order to isolate the top level
# library (or app) module from the kernel build system, we save the kernel
# module flags (to a synthetic parent module, KERNEL), clear those flags, then
# include the library via DEPENDENCY_MODULE. After finishing with the rules for
# the library, we will restore the kernel flags from their saved values.
DEPENDENCY_MODULE := $(1)
MODULE := KERNEL
include make/userspace_recurse.mk
endef

# Strip out flags not applicable to SDK
define prepare-sdk-flags
$(patsubst -fsanitize-blacklist=%,,\
	$(patsubst -include $(BUILDDIR)/config.h,,$(1)))
endef

TRUSTY_TOP_LEVEL_BUILDDIR := $(BUILDDIR)
TRUSTY_APP_BUILDDIR := $(BUILDDIR)/user_tasks
TRUSTY_SDK_DIR := $(BUILDDIR)/sdk
TRUSTY_SDK_SYSROOT := $(TRUSTY_SDK_DIR)/sysroot/
TRUSTY_SDK_INCLUDE_DIR := $(TRUSTY_SDK_SYSROOT)/usr/include
TRUSTY_SDK_LIB_DIR := $(TRUSTY_SDK_SYSROOT)/usr/lib
TRUSTY_SDK_LICENSE_DIR := $(TRUSTY_SDK_DIR)/licenses
TRUSTY_SDK_LICENSE := $(TRUSTY_SDK_DIR)/LICENSE
TRUSTY_LIBRARY_BUILDDIR := $(BUILDDIR)/lib

# Host modules required by userspace are built in passing as needed, as the
# userspace build system respects MODULE_RUST_HOST_LIB. But the kernel may also
# need some host modules, e.g. for proc-macro crates. So we explicitly iterate
# over the set of host modules required by the kernel, generating their build
# rules with the userspace build system.
$(foreach lib,$(ALLHOSTMODULES),\
	$(eval $(call trusty-build-rule,$(lib))))


# The license file construction assumes that all projects will contain the same
# set of SDK modules and thus the same set of respective license files. If this
# ever changes, SDK zip construction in build.py will need to be adjusted to
# account for differing licenses across projects.
TRUSTY_SDK_MODULES := \
	external/boringssl \
	trusty/kernel/lib/libc-ext \
	trusty/kernel/lib/ubsan \
	trusty/user/base/interface/hwaes \
	trusty/user/base/interface/hwkey \
	trusty/user/base/interface/keymaster \
	trusty/user/base/interface/spi \
	trusty/user/base/interface/storage \
	trusty/user/base/interface/system_state \
	trusty/user/base/lib/dlmalloc \
	trusty/user/base/lib/googletest \
	trusty/user/base/lib/hwaes \
	trusty/user/base/lib/hwbcc/rust \
	trusty/user/base/lib/hwkey \
	trusty/user/base/lib/hwkey/rust \
	trusty/user/base/lib/keymaster \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libcxxabi-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/line-coverage \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/spi/client \
	trusty/user/base/lib/spi/common \
	trusty/user/base/lib/storage \
	trusty/user/base/lib/syscall-stubs \
	trusty/user/base/lib/system_state \
	trusty/user/base/lib/system_state/rust \
	trusty/user/base/lib/tipc \
	trusty/user/base/lib/tipc/rust \
	trusty/user/base/lib/unittest \
	trusty/user/base/lib/unittest-rust \

# scudo does not build on 32-bit yet
ifeq (false,$(call TOBOOL,$(KERNEL_32BIT)))
ifeq (false,$(call TOBOOL,$(USER_32BIT)))
TRUSTY_SDK_MODULES += trusty/user/base/lib/scudo
endif
endif

TRUSTY_SDK_MODULES += $(EXTRA_TRUSTY_SDK_MODULES)

ALL_SDK_EXTRA_FILES :=
ALL_SDK_INCLUDES :=
ALL_SDK_LIBS :=
ALL_SDK_LICENSES :=

define TOSDKLIBNAME
$(patsubst lib%,%,$(notdir $(1)))
endef

GLOBAL_USER_RUSTFLAGS += -L dependency=$(TRUSTY_LIBRARY_BUILDDIR)

# We need the host library dir to pick up recursive dependencies that are proc
# macros and therefore built in the host build dir.
GLOBAL_USER_RUSTFLAGS += -L dependency=$(TRUSTY_HOST_LIBRARY_BUILDDIR)

GLOBAL_CRATE_COUNT := 0
RUST_ANALYZER_CRATES :=

# Save userspace-global variables so we can restore kernel state
TRUSTY_KERNEL_SAVED_ARCH := $(ARCH)
TRUSTY_KERNEL_SAVED_ALLOW_FP_USE := $(ALLOW_FP_USE)
TRUSTY_KERNEL_SAVED_SCS_ENABLED := $(SCS_ENABLED)

# while compiling user space we allow FP support
ALLOW_FP_USE := true

# tell the arch-specific makefiles to set flags required for SCS if supported
SCS_ENABLED := $(call TOBOOL,$(USER_SCS_ENABLED))

# Building trusty userspace
TRUSTY_USERSPACE := true

# Used by LTO, could be combined with TRUSTY_USERSPACE after this lands
USER_TASK_MODULE := true

ARCH := $(TRUSTY_USER_ARCH)
# Re-derive the standard arch name using the new arch.
$(eval $(call standard_name_for_arch,STANDARD_ARCH_NAME,$(ARCH),$(SUBARCH)))

# Override tools for the userspace arch
include arch/$(ARCH)/toolchain.mk

include $(TRUSTY_APP_DIR)/arch/$(TRUSTY_USER_ARCH)/rules.mk

# generate list of all user tasks we need to build
# include the legacy TRUSTY_ALL_USER_TASKS variable for projects that still use
# it. This will be removed in the future and all projects should use
# TRUSTY_BUILTIN_USER_TASKS directly.
TRUSTY_BUILTIN_USER_TASKS := $(TRUSTY_BUILTIN_USER_TASKS) \
                             $(TRUSTY_ALL_USER_TASKS) \


# Rust crate tests have a -test suffix to their module name to distinguish from
# the crate itself with the same path.
ifeq ($(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_RUST)),true)
RUST_USER_TEST_MODULES := $(addsuffix -test,$(TRUSTY_RUST_USER_TESTS))
endif

ifneq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
# Default to including all user tests in the image if the set of builtin tests
# is not selected.
TRUSTY_BUILTIN_USER_TESTS ?= $(TRUSTY_USER_TESTS) $(RUST_USER_TEST_MODULES)
TRUSTY_BUILTIN_USER_TASKS += $(TRUSTY_BUILTIN_USER_TESTS)
endif

# remove duplicates
TRUSTY_BUILTIN_USER_TASKS := $(sort $(TRUSTY_BUILTIN_USER_TASKS))

ALL_USER_TASKS := $(TRUSTY_BUILTIN_USER_TASKS) $(TRUSTY_LOADABLE_USER_TASKS)  \
		  $(TRUSTY_USER_TESTS) $(TRUSTY_LOADABLE_USER_TESTS) $(TRUSTY_RUST_USER_TESTS)

# sort and remove duplicates
ALL_USER_TASKS := $(sort $(ALL_USER_TASKS))

# TODO: we could find the runtime like this.
# TRUSTY_APP_LIBGCC := $(shell $(CC) $(GLOBAL_COMPILEFLAGS) $(ARCH_$(ARCH)_COMPILEFLAGS) $(THUMBCFLAGS) --rtlib=compiler-rt -print-libgcc-file-name)
# However the compiler currently does not contain non-x86 prebuilts for the
# linux-gnu ABI. We could either get those prebuilts added to the toolchain or
# switch to the android ABI.
# Note there are two copies of compiler-rt in the toolchain - framework and NDK.
# We're using the NDK version because the path is more stable and the difference
# should not matter for this library. (The main difference is which version of
# libcxx they link against, and the builtins do not use C++.)
TRUSTY_APP_LIBGCC := $(CLANG_BINDIR)/../runtimes_ndk_cxx/libclang_rt.builtins-$(STANDARD_ARCH_NAME)-android.a
ifeq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
TRUSTY_APP_LIBCOV := -u__llvm_profile_runtime $(CLANG_BINDIR)/../runtimes_ndk_cxx/libclang_rt.profile-aarch64-android.a
endif

TRUSTY_APP_BASE_LDFLAGS := $(GLOBAL_SHARED_LDFLAGS) -z max-page-size=4096 -z separate-loadable-segments
TRUSTY_APP_ALIGNMENT := 4096
TRUSTY_APP_MEMBASE :=
TRUSTY_APP_SYMTAB_ENABLED := $(SYMTAB_ENABLED)

$(info ALL_USER_TASKS: $(ALL_USER_TASKS))

# GLOBAL_CPPFLAGS comes before GLOBAL_INCLUDES on the compile command-line. This
# is important because we need libcxx's math.h to be picked up before musl's
# when building C++.
GLOBAL_USER_IN_TREE_CPPFLAGS += -I$(TRUSTY_SDK_INCLUDE_DIR)/c++/v1
GLOBAL_USER_IN_TREE_COMPILEFLAGS += \
	--sysroot=$(TRUSTY_SDK_SYSROOT) \
	-isystem $(TRUSTY_SDK_INCLUDE_DIR) \
	-D_LIBCPP_HAS_THREAD_API_PTHREAD \

TRUSTY_APP_BASE_LDFLAGS += -L$(TRUSTY_SDK_LIB_DIR)

# Generate build rules for each sdk library, if they have not already been
# generated.
#
# Rules are the first time a library is required, so libraries may already be
# processed before we get to them in the list of SDK libraries.
#
$(call INFO_LOG,Generate build rules for SDK libraries)

$(foreach lib,$(TRUSTY_SDK_MODULES),\
	$(if $(_MODULES_$(lib)),,$(eval $(call trusty-build-rule,$(lib)))))

ALL_SDK_LICENSES :=
$(foreach lib,$(TRUSTY_SDK_MODULES),\
	$(eval ALL_SDK_LICENSES += $(_MODULES_$(lib)_LICENSES)))

$(TRUSTY_SDK_LICENSE): $(ALL_SDK_LICENSES)
	@$(MKDIR)
	@echo Generating SDK license
	$(NOECHO)rm -f $@.tmp
	$(NOECHO)cat trusty/user/base/sdk/LICENSE >> $@.tmp;
	$(NOECHO)for license in $^; do \
		printf "\n-------------------------------------------------------------------\n" >> $@.tmp;\
		printf "Copied from $$license\n\n\n" >> $@.tmp;\
		cat "$$license" >> $@.tmp;\
		done
	$(call TESTANDREPLACEFILE,$@.tmp,$@)

#
# Generate build rules for each user task
#
$(call INFO_LOG,Generate build rules for user tasks)

$(foreach t,$(ALL_USER_TASKS),\
   $(eval $(call trusty-build-rule,$(t))))

# Add any prebuilt apps to the build.

PREBUILT_OBJECTS := $(foreach t,$(TRUSTY_PREBUILT_USER_TASKS),\
	$(t)/$(notdir $(t)).manifest $(t)/$(notdir $(t)).elf)
PREBUILT_OBJECTS_DEST := $(addprefix $(BUILDDIR)/user_tasks/,$(PREBUILT_OBJECTS))
$(PREBUILT_OBJECTS_DEST): $(BUILDDIR)/user_tasks/%: %
	$(MKDIR)
	cp $^ $(dir $@)/

define prebuilt-app-build-rule
$(eval _MODULES_$(1)_TRUSTY_APP_MANIFEST_BIN := $(1)/$(notdir $(1)).manifest)\
$(eval _MODULES_$(1)_TRUSTY_APP_ELF := $(1)/$(notdir $(1)).elf)
endef

# Set up global variables describing each prebuilt app
$(foreach t,$(TRUSTY_PREBUILT_USER_TASKS),\
	$(call prebuilt-app-build-rule,$(t)))

TRUSTY_BUILTIN_USER_TASKS += $(TRUSTY_PREBUILT_USER_TASKS)

# Build the SDK makefile
$(call INFO_LOG,Build SDK makefile)
$(eval $(call trusty-build-rule,trusty/user/base/sdk))

# Ensure that includes and libs are installed
all:: $(ALL_SDK_INCLUDES) $(ALL_SDK_LIBS) $(ALL_SDK_EXTRA_FILES) $(TRUSTY_SDK_LICENSE)

ifeq (false,$(call TOBOOL,$(TRUSTY_APPLOADER_ENABLED)))

ifneq ($(strip $(TRUSTY_LOADABLE_USER_TASKS) $(TRUSTY_LOADABLE_USER_TESTS)),)
$(error Loadable apps ($(TRUSTY_LOADABLE_USER_TASKS) $(TRUSTY_LOADABLE_USER_TESTS)) requested but this build does not include the apploader service)
endif

else # TRUSTY_APPLOADER_ENABLED

#
# Generate loadable application packages
#
$(call INFO_LOG,Generate loadable application packages)
define loadable-app-build-rule
$(eval APP_NAME := $(notdir $(1)))\
$(eval APP_TOP_MODULE := $(1))\
$(eval APP_BUILDDIR := $(BUILDDIR)/user_tasks/$(1))\
$(eval include make/loadable_app.mk)
endef

# Sort and remove duplicates
TRUSTY_LOADABLE_USER_TASKS := $(sort $(TRUSTY_LOADABLE_USER_TASKS))

#
# Generate build rules for each application
#
$(call INFO_LOG,Generate loadable apps build rules)
$(foreach t,$(TRUSTY_LOADABLE_USER_TASKS),\
   $(call loadable-app-build-rule,$(t)))

# Clear the list of loadable apps
LOADABLE_APP_LIST :=

# Sort and remove duplicates
TRUSTY_USER_TESTS := $(sort \
                       $(TRUSTY_USER_TESTS) \
                       $(TRUSTY_LOADABLE_USER_TESTS) \
                       $(RUST_USER_TEST_MODULES) \
                     )

#
# Generate build rules for test application
#
$(call INFO_LOG,Generate test apps build rules)
$(foreach t,$(TRUSTY_USER_TESTS),\
   $(call loadable-app-build-rule,$(t)))

# At this point LOADABLE_APP_LIST only contains user tests
TRUSTY_LOADABLE_TEST_APPS := $(LOADABLE_APP_LIST)

ifneq ($(strip $(TRUSTY_LOADABLE_TEST_APPS)),)

TEST_PACKAGE_ZIP ?= $(BUILDDIR)/trusty_test_package.zip
TEST_PACKAGE_ZIP_ARGS ?= -q -u -r

$(TEST_PACKAGE_ZIP): BUILDDIR := $(BUILDDIR)
$(TEST_PACKAGE_ZIP_ARGS): TEST_PACKAGE_ZIP_ARGS := $(TEST_PACKAGE_ZIP_ARGS)
$(TEST_PACKAGE_ZIP): $(TRUSTY_LOADABLE_TEST_APPS)
	@$(MKDIR)
	@$(call ECHO,KERNEL,creating Trusty test archive package,$^)
	$(NOECHO)rm -f $@
	$(NOECHO)(cd $(BUILDDIR) && zip $(TEST_PACKAGE_ZIP_ARGS)  $@ $(subst $(BUILDDIR)/,,$^))
	@$(call ECHO_DONE_SILENT,KERNEL,creating Trusty test archive package,$^)

EXTRA_BUILDDEPS += $(TEST_PACKAGE_ZIP)

endif

endif # TRUSTY_APPLOADER_ENABLED


#
# Build a rust-project.json for rust-analyzer
#
RUST_PROJECT_JSON := $(BUILDDIR)/rust-project.json
define RUST_PROJECT_JSON_CONTENTS :=
{
	"crates": [
		$(call STRIP_TRAILING_COMMA,$(RUST_ANALYZER_CRATES))
	]
}
endef
RUST_PROJECT_JSON_CONTENTS := $(subst $(NEWLINE),\n,$(RUST_PROJECT_JSON_CONTENTS))
RUST_PROJECT_JSON_CONTENTS := $(subst %,%%,$(RUST_PROJECT_JSON_CONTENTS))
.PHONY: $(RUST_PROJECT_JSON)
$(RUST_PROJECT_JSON): CONTENTS :=  $(RUST_PROJECT_JSON_CONTENTS)
$(RUST_PROJECT_JSON):
	@$(MKDIR)
	@echo Creating rust-project.json for rust-analyzer
	$(NOECHO)printf '$(CONTENTS)' > $@

EXTRA_BUILDDEPS += $(RUST_PROJECT_JSON)


# Restore kernel state
ARCH := $(TRUSTY_KERNEL_SAVED_ARCH)
ALLOW_FP_USE := $(TRUSTY_KERNEL_SAVED_ALLOW_FP_USE)
SCS_ENABLED := $(TRUSTY_KERNEL_SAVED_SCS_ENABLED)

#
# Generate combined user task obj/bin if necessary
#
ifneq ($(strip $(TRUSTY_BUILTIN_USER_TASKS)),)

BUILTIN_TASK_MANIFESTS_BINARY := $(foreach t, $(TRUSTY_BUILTIN_USER_TASKS),\
   $(_MODULES_$(t)_TRUSTY_APP_MANIFEST_BIN))

BUILTIN_TASK_ELFS := $(foreach t, $(TRUSTY_BUILTIN_USER_TASKS),\
   $(_MODULES_$(t)_TRUSTY_APP_ELF))

BUILTIN_TASK_OBJS := $(patsubst %.elf,%.o,$(BUILTIN_TASK_ELFS))

$(BUILTIN_TASK_OBJS): CC := $(CC)
$(BUILTIN_TASK_OBJS): GLOBAL_COMPILEFLAGS := $(GLOBAL_COMPILEFLAGS)
$(BUILTIN_TASK_OBJS): ARCH_COMPILEFLAGS := $(ARCH_$(ARCH)_COMPILEFLAGS)
$(BUILTIN_TASK_OBJS): USER_TASK_OBJ_ASM:=$(TRUSTY_APP_DIR)/appobj.S
$(BUILTIN_TASK_OBJS): LOG_NAME:=$(TRUSTY_APP_DIR)
$(BUILTIN_TASK_OBJS): %.o: %.elf %.manifest $(USER_TASK_OBJ_ASM)
	@$(MKDIR)
	@$(call ECHO,$(LOG_NAME),converting,$< to $@)
	$(NOECHO)$(CC) -DUSER_TASK_ELF=\"$<\" -DMANIFEST_DATA=\"$(word 2,$^)\" $(GLOBAL_COMPILEFLAGS) $(ARCH_COMPILEFLAGS) -c $(USER_TASK_OBJ_ASM) -o $@
	@$(call ECHO_DONE_SILENT,$(LOG_NAME),converting,$< to $@)

EXTRA_OBJS += $(BUILTIN_TASK_OBJS)

endif

# Reset app variables
BUILDDIR := $(TRUSTY_TOP_LEVEL_BUILDDIR)
TRUSTY_APP :=
TRUSTY_APP_NAME :=
TRUSTY_APP_BASE_LDFLAGS :=
TRUSTY_APP_ARCH :=
TRUSTY_APP_ALIGNMENT :=
TRUSTY_APP_MEMBASE :=
TRUSTY_APP_SYMTAB_ENABLED :=
TRUSTY_APPLOADER_ENABLED :=
TRUSTY_TOP_LEVEL_BUILDDIR :=
TRUSTY_USERSPACE :=
TRUSTY_USERSPACE_SAVED_ARCH :=
TRUSTY_USERSPACE_SAVED_ALLOW_FP_USE :=
TRUSTY_USERSPACE_SAVED_SCS_ENABLED :=
USER_TASK_MODULE :=
LOADABLE_APP_LIST :=
TRUSTY_LOADABLE_USER_TASKS :=
TEST_PACKAGE_ZIP :=
RUST_PROJECT_JSON :=
RUST_PROJECT_JSON_CONTENTS :=
RUST_USER_TEST_MODULES :=
RUST_ANALYZER_CRATES :=
GLOBAL_CRATE_COUNT :=
