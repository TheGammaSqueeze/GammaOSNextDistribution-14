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

# Build a userspace library for Trusty
#
# args:
# MODULE : module name (required)
# MODULE_SRCS : list of source files, local path (not required for header-only
# 		libraries)
# MODULE_LIBRARY_DEPS : libraries that this module depends on. These libraries
# 		must be built using the new library.mk system (i.e. include
# 		make/library.mk at the end of the library's rules)
# MODULE_DEPS : legacy dependencies that do not use the new library.mk system.
# 		These dependencies will be built exclusively for this module and not
# 		shared with other modules). Do not use this for library dependencies
# 		compatible with library.mk, instead use MODULE_LIBRARY_DEPS.
# MODULE_ADD_IMPLICIT_DEPS : Add basic libraries to MODULE_LIBRARY_DEPS.
# 		Defaults to true. (currently adds libc-trusty)
# MODULE_USE_WHOLE_ARCHIVE : use --whole-archive when linking this module
# MODULE_DEFINES : #defines local to this module
# MODULE_CONSTANTS : JSON files with constants used for both the manifest and C
# 		headers (optional) (CONSTANTS is a deprecated equivalent to
# 		MODULE_CONSTANTS)
# MODULE_COMPILEFLAGS : COMPILEFLAGS local to this module
# MODULE_CFLAGS : CFLAGS local to this module
# MODULE_CPPFLAGS : CPPFLAGS local to this module
# MODULE_ASMFLAGS : ASMFLAGS local to this module
# MODULE_INCLUDES : include directories local to this module
# MODULE_SRCDEPS : extra dependencies that all of this module's files depend on
# MODULE_EXTRA_OBJECTS : extra .o files that should be linked with the module
# MODULE_WHOLE_ARCHIVES : extra .a libraries that need --whole-archive, e.g.,
#		prebuilt archive dependencies
# MODULE_ARM_OVERRIDE_SRCS : list of source files, local path that should be
# 		force compiled with ARM (if applicable)
# MODULE_RUST_EDITION : Rust edition to compile this crate for (optional)
# MODULE_RUST_TESTS : If true, this module will be built as both a crate library
#       and a Rust test service (optional, default is false)
# MODULE_SKIP_DOCS : If true, no documentation will be generated for
#       this module (optional, default is false)
# MODULE_SDK_LIB_NAME : Name of library in SDK (if applicable). Default is
# 		libMODULE_NAME where MODULE_NAME is the final path component of MODULE.
# MODULE_SDK_HEADERS : Headers to copy into the SDK. Any headers in
#       MODULE_EXPORT_INCLUDES will be included as well, but generated headers
#       must be listed explicitly.
# MODULE_SDK_HEADER_INSTALL_DIR : Path under include prefix to install SDK
# 		headers into.
# MODULE_LICENSES : Any additional license files for the library other than
# 		$(MODULE)/LICENSE and $(MODULE)/NOTICE
# MODULE_RUST_STEM: The stem of the output .rlib file for this library.
# 	Defaults to $(MODULE_CRATE_NAME) if left empty.
# MANIFEST : App manifest JSON file, only applicable if this module is an app
# MANIFEST_OVERLAY : Additional manifest overlay JSON files(s)
#
# Exported flags:
# The following args are the same as their corresponding variables above, but
# will be exported to all users of this library. These flags are also prepended
# to this module's local flags. To override an exported flag, add the
# corresponding override to e.g. MODULE_COMPILEFLAGS.
#
# MODULE_EXPORT_COMPILEFLAGS
# MODULE_EXPORT_CONSTANTS
# MODULE_EXPORT_CFLAGS
# MODULE_EXPORT_CPPFLAGS
# MODULE_EXPORT_ASMFLAGS
# MODULE_EXPORT_LDFLAGS
# MODULE_EXPORT_INCLUDES
# MODULE_EXPORT_SRCDEPS

# the minimum library rules.mk file is as follows:
#
# LOCAL_DIR := $(GET_LOCAL_DIR)
# MODULE := $(LOCAL_DIR)
#
# MODULE_SRCS := $(LOCAL_DIR)/source_file.c
#
# include make/library.mk

# if QUERY_MODULE is set, the rules.mk that included us was itself included not
# to define a module's make targets but to query the variables it sets for the
# rest of the build. in this case, skip all further processing
ifeq ($(QUERY_MODULE),)

ifeq ($(call TOBOOL,$(TRUSTY_NEW_MODULE_SYSTEM)),false)

MODULE_LOG_NAME := $(MODULE)
$(call INFO,$(MODULE_LOG_NAME),processing,kernel library)

GLOBAL_INCLUDES += $(MODULE_EXPORT_INCLUDES)
GLOBAL_COMPILEFLAGS += $(MODULE_EXPORT_COMPILEFLAGS)
GLOBAL_SRCDEPS += $(MODULE_EXPORT_SRCDEPS)

ifneq ($(MODULE_EXPORT_CONSTANTS),)
$(error MODULE_EXPORT_CONSTANTS is not supported by library.mk for use in the kernel)
endif
ifneq ($(MODULE_EXPORT_CFLAGS),)
$(error MODULE_EXPORT_CFLAGS is not supported by library.mk for use in the kernel)
endif
ifneq ($(MODULE_EXPORT_CPPFLAGS),)
$(error MODULE_EXPORT_CPPFLAGS is not supported by library.mk for use in the kernel)
endif
ifneq ($(MODULE_EXPORT_ASMFLAGS),)
$(error MODULE_EXPORT_ASMFLAGS is not supported by library.mk for use in the kernel)
endif
ifneq ($(MODULE_EXPORT_LDFLAGS),)
$(error MODULE_EXPORT_LDFLAGS is not supported by library.mk for use in the kernel)
endif

# Building for the kernel, turn off independent library build and fall back to
# lk module system.
include make/module.mk

$(call INFO_DONE_SILENT,$(MODULE_LOG_NAME),processing)

else  # TRUSTY_NEW_MODULE_SYSTEM is true

ifeq ($(call TOBOOL,$(BUILD_AS_RUST_TEST_MODULE)),true)
# Disable Rust tests on architectures that do not support Rust
ifeq ($(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_RUST)),true)
# Allow a project to disable rust tests
ifeq ($(call TOBOOL,$(TRUSTY_DISABLE_RUST_TESTS)),false)

MODULE_RUST_LOG_NAME := $(MODULE)

$(call INFO,$(MODULE_RUST_LOG_NAME),processing,as a rust test service)

MODULE := $(MODULE)-test
MODULE_RUSTFLAGS += --test
MODULE_RUST_CRATE_TYPES := bin
MODULE_LIBRARY_DEPS += trusty/user/base/lib/unittest-rust
MODULE_RUST_ENV += TRUSTY_TEST_PORT=com.android.trusty.rust.$(MODULE_CRATE_NAME).test
MODULE_RUST_TESTS :=
MODULE_SKIP_DOCS := true
TRUSTY_APP_NAME := $(MODULE_CRATE_NAME)-test
BUILD_AS_RUST_TEST_MODULE :=

TRUSTY_RUST_USER_TESTS += $(MODULE)

include make/trusted_app.mk

$(call INFO_DONE_SILENT,$(MODULE_RUST_LOG_NAME),processing)

endif
endif
else # Not building rust test app

# Build with the new module system. Currently, the Trusty userspace libraries
# and apps use the new module system, as does the bootloader/test-runner binary.
$(call INFO,$(MODULE),processing,library or app)

# Reset new module system marker. This will be set again in dependencies by
# userspace_recurse.mk
TRUSTY_NEW_MODULE_SYSTEM :=

MODULE_IS_RUST := $(if $(filter %.rs,$(MODULE_SRCS)),true,false)

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
ifeq ($(strip $(MODULE_RUST_CRATE_TYPES)),)
MODULE_RUST_CRATE_TYPES := rlib
endif
# Disable Rust modules on architectures that do not support Rust
ifeq ($(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_RUST)),false)
MODULE_DISABLED := true
endif
endif

ifeq ($(call TOBOOL,$(MODULE_DISABLED)),false)

ifneq ($(filter proc-macro,$(MODULE_RUST_CRATE_TYPES)),)

# proc macros must be host libraries, and all their dependencies are as well.
# This will be reset after we recursively include all dependencies.
MODULE_RUST_HOST_LIB := true

ifneq ($(strip $(filter-out proc-macro,$(MODULE_RUST_CRATE_TYPES))),)
$(error $(MODULE) cannot be built as both a proc-macro and a target crate)
endif
endif

ifeq ($(call TOBOOL,$(TRUSTY_APP)),false)
ifeq ($(call TOBOOL,$(MODULE_RUST_HOST_LIB)),false)
BUILDDIR := $(TRUSTY_LIBRARY_BUILDDIR)
else
BUILDDIR := $(TRUSTY_HOST_LIBRARY_BUILDDIR)
endif
endif

# Skip docs for apps because dependencies for apps are setup differently than
# for rlibs (apps do use $MODULE_RSOBJS which is the variable we use as an input
# to the rustdoc target to ensure that dependencies are built before generating
# docs) and currently that breaks the rustdoc builds. We don't currently need
# generated docs for apps, but if that changes it should be possible to fix
# this.
ifeq ($(call TOBOOL,$(TRUSTY_APP)),true)
MODULE_SKIP_DOCS := true
endif

ifeq ($(call TOBOOL,$(MODULE_RUST_HOST_LIB)),false)
# Add any common flags to the module
include make/common_flags.mk
endif

ifneq ($(INCMODULES),)
$(error $(MODULE) should only be included from other userspace modules that use library.mk. One of the following modules needs to be updated to use the new library system: $(LIB_SAVED_MODULE) $(ALLMODULES))
endif
ifneq ($(GLOBAL_OPTFLAGS),)
$(error $(MODULE) has modified GLOBAL_OPTFLAGS, this variable is deprecated)
endif
ifneq ($(GLOBAL_COMPILEFLAGS),)
$(error $(MODULE) has modified GLOBAL_COMPILEFLAGS, this variable is deprecated, please use MODULE_EXPORT_COMPILEFLAGS)
endif
ifneq ($(GLOBAL_CFLAGS),)
$(error $(MODULE) has modified GLOBAL_CFLAGS, this variable is deprecated, please use MODULE_EXPORT_CFLAGS)
endif
ifneq ($(GLOBAL_CPPFLAGS),)
$(error $(MODULE) has modified GLOBAL_CPPFLAGS, this variable is deprecated, please use MODULE_EXPORT_CPPFLAGS)
endif
ifneq ($(GLOBAL_ASMFLAGS),)
$(error $(MODULE) has modified GLOBAL_ASMFLAGS, this variable is deprecated, please use MODULE_EXPORT_ASMFLAGS)
endif
ifneq ($(GLOBAL_DEFINES),)
$(error $(MODULE) has modified GLOBAL_DEFINES, this variable is deprecated)
endif
ifneq ($(GLOBAL_INCLUDES),)
$(error $(MODULE) has modified GLOBAL_INCLUDES, this variable is deprecated, please use MODULE_EXPORT_INCLUDES)
endif
ifneq ($(MODULE_OPTFLAGS),)
$(error $(MODULE) sets MODULE_OPTFLAGS, which is deprecated. Please move these flags to another variable.)
endif

ifneq ($(MODULE_EXPORT_RUSTFLAGS),)
$(error $(MODULE) sets MODULE_EXPORT_RUSTFLAGS, which is not supported)
endif

ifneq ($(strip $(MODULE_DEPS)),)
$(warning $(MODULE) is a userspace library module but has deprecated MODULE_DEPS: $(MODULE_DEPS).)
endif

# ALLMODULES is only used for the legacy dependency system, so if a library is
# included in it, something must have gone wrong.
ifneq ($(filter $(MODULE),$(ALLMODULES)),)
ifeq ($(LIB_SAVED_MODULE),)
# We don't know who our parent was because it was a legacy module, so we can't
# give a very good error message here.
$(error Please move $(MODULE) from MODULE_DEPS into MODULE_LIBRARY_DEPS)
else
$(error MODULE $(LIB_SAVED_MODULE) depends on $(MODULE) via MODULE_DEPS, but $(MODULE) is only compatible with MODULE_LIBRARY_DEPS)
endif
endif

ifneq ($(CONSTANTS),)
$(warning $(MODULE) has set CONSTANTS, this variable is deprecated, please use MODULE_CONSTANTS or MODULE_EXPORT_CONSTANTS)
endif
MODULE_CONSTANTS += $(CONSTANTS)

ifneq ($(MODULE_SRCS)$(MODULE_SRCS_FIRST),)
# Add this module to the SDK LDFLAGS and objects lists. This needs to be done
# before including our dependencies in case of recursive deps.
ifneq ($(filter $(MODULE),$(TRUSTY_SDK_MODULES)),)
ifeq ($(strip $(MODULE_SDK_LIB_NAME)),)
MODULE_SDK_LIB_NAME := $(call TOSDKLIBNAME,$(MODULE))
endif

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),false)
# If this module isn't rust, we can link against it from the sdk using -lmodule
MODULE_SDK_LIBS += $(TRUSTY_SDK_LIB_DIR)/lib$(MODULE_SDK_LIB_NAME).a
MODULE_EXPORT_LDFLAGS += $(filter-out $(MODULE_EXPORT_LDFLAGS),-l$(MODULE_SDK_LIB_NAME))
endif

endif # SDK module
endif # not header only

# Add this library's headers to the SDK.
ifneq ($(filter $(MODULE),$(TRUSTY_SDK_MODULES)),)
MODULE_EXPORT_SDK_HEADERS :=

define copy-headers-rule
# Some libraries include symlinked headers. For now, follow
# those symlinks and copy their targets instead so SDK users
# can still include the symlink sources.
HEADERS := $$(shell cd "$(1)" 2>/dev/null && find -L . -type f)
OUTPUT_HEADERS := $$(filter-out $$(MODULE_EXPORT_SDK_HEADERS),$$(addprefix $(TRUSTY_SDK_INCLUDE_DIR)/$(MODULE_SDK_HEADER_INSTALL_DIR)/,$$(HEADERS)))
MODULE_EXPORT_SDK_HEADERS += $$(OUTPUT_HEADERS)
$$(OUTPUT_HEADERS): $(TRUSTY_SDK_INCLUDE_DIR)/$(MODULE_SDK_HEADER_INSTALL_DIR)/% : $(1)/% $(MODULE_SRCDEPS)
	@$$(MKDIR)
	$$(NOECHO)cp -L $$< $$@
endef

$(foreach include_dir,$(MODULE_EXPORT_INCLUDES),$(eval $(call copy-headers-rule,$(include_dir))))

# Copy any generated headers explicitly listed in MODULE_SDK_HEADERS
ifneq ($(strip $(MODULE_SDK_HEADERS)),)
OUTPUT_HEADERS := $(foreach header,$(MODULE_SDK_HEADERS),$(TRUSTY_SDK_INCLUDE_DIR)/$(MODULE_SDK_HEADER_INSTALL_DIR)/$(notdir $(header)))
MODULE_EXPORT_SDK_HEADERS += $(OUTPUT_HEADERS)
$(OUTPUT_HEADERS): MODULE_SDK_HEADERS := $(MODULE_SDK_HEADERS)
$(OUTPUT_HEADERS): MODULE_SDK_HEADER_INSTALL_DIR := $(MODULE_SDK_HEADER_INSTALL_DIR)
$(OUTPUT_HEADERS): $(MODULE_SDK_HEADERS) $(MODULE_SRCDEPS)
	@$(MKDIR)
	$(NOECHO)cp $(MODULE_SDK_HEADERS) $(TRUSTY_SDK_INCLUDE_DIR)/$(MODULE_SDK_HEADER_INSTALL_DIR)/
OUTPUT_HEADERS :=
endif

# Make sure we copy all SDK headers even if they are not needed by the build
ALL_SDK_INCLUDES += $(MODULE_EXPORT_SDK_HEADERS)

endif # SDK MODULE

# Stem defaults to the crate name
ifeq ($(MODULE_RUST_STEM),)
MODULE_RUST_STEM := $(MODULE_CRATE_NAME)
endif

# Register the module in a global registry. This is used to avoid repeatedly
# generating rules for this module from modules that depend on it.
_MODULES_$(MODULE) := T

# Cache exported flags for use in modules that depend on this library.
_MODULES_$(MODULE)_COMPILEFLAGS := $(MODULE_EXPORT_COMPILEFLAGS)
_MODULES_$(MODULE)_CONSTANTS := $(MODULE_EXPORT_CONSTANTS)
_MODULES_$(MODULE)_CFLAGS := $(MODULE_EXPORT_CFLAGS)
_MODULES_$(MODULE)_CPPFLAGS := $(MODULE_EXPORT_CPPFLAGS)
_MODULES_$(MODULE)_ASMFLAGS := $(MODULE_EXPORT_ASMFLAGS)
_MODULES_$(MODULE)_INCLUDES := $(MODULE_EXPORT_INCLUDES)
_MODULES_$(MODULE)_LDFLAGS := $(MODULE_EXPORT_LDFLAGS)
_MODULES_$(MODULE)_SRCDEPS := $(MODULE_EXPORT_SRCDEPS)
ifeq ($(filter $(MODULE),$(TRUSTY_SDK_MODULES)),)
ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
_MODULES_$(MODULE)_CRATE_NAME := $(MODULE_CRATE_NAME)
_MODULES_$(MODULE)_RUST_STEM := $(MODULE_RUST_STEM)

# Memorize the output headers for this module so that we can add them as srcdeps
# to dependencies
_MODULES_$(MODULE)_SDK_HEADERS := $(MODULE_EXPORT_SDK_HEADERS)

# We need to populate rlibs here, before recursing, in case we have a circular
# dependency. This is analogous to _INCLUDES above.
ifneq ($(filter rlib,$(MODULE_RUST_CRATE_TYPES)),)
_MODULES_$(MODULE)_LIBRARIES := $(call TOBUILDDIR,lib$(MODULE_RUST_STEM)).rlib
_MODULES_$(MODULE)_RLIBS := $(MODULE_CRATE_NAME)=$(call TOBUILDDIR,lib$(MODULE_RUST_STEM).rlib)
endif

ifneq ($(filter proc-macro,$(MODULE_RUST_CRATE_TYPES)),)
_MODULES_$(MODULE)_LIBRARIES := $(call TOBUILDDIR,lib$(MODULE_RUST_STEM)).so
_MODULES_$(MODULE)_RLIBS := $(MODULE_CRATE_NAME)=$(call TOBUILDDIR,lib$(MODULE_RUST_STEM).so)
endif

else
_MODULES_$(MODULE)_LIBRARIES := $(call TOBUILDDIR,$(MODULE)).mod.a
endif
endif # not SDK module

# Will contain a list of SDK libraries that this library depends on. Used for
# dependency resolution, not for including the libraries directly in the link.
_MODULES_$(MODULE)_SDK_LIBS := $(MODULE_SDK_LIBS)

DEPENDENCY_MODULE :=
DEPENDENCY_MODULE_PATH :=

# Recurse into dependencies that this module re-exports flags from. This needs
# to happen before we recurse into regular dependencies in the case of recursive
# dependencies, which need to pick up this module's re-exported flags.
$(foreach dep,$(sort $(MODULE_LIBRARY_EXPORTED_DEPS)),\
	$(eval EXPORT_DEPENDENCY_MODULE := $(dep))\
	$(eval include make/userspace_recurse.mk))

# Re-cache exported flags after adding any flags from exported deps
_MODULES_$(MODULE)_COMPILEFLAGS := $(MODULE_EXPORT_COMPILEFLAGS)
_MODULES_$(MODULE)_CFLAGS := $(MODULE_EXPORT_CFLAGS)
_MODULES_$(MODULE)_CPPFLAGS := $(MODULE_EXPORT_CPPFLAGS)
_MODULES_$(MODULE)_ASMFLAGS := $(MODULE_EXPORT_ASMFLAGS)
_MODULES_$(MODULE)_INCLUDES := $(MODULE_EXPORT_INCLUDES)
_MODULES_$(MODULE)_LDFLAGS := $(MODULE_EXPORT_LDFLAGS)
_MODULES_$(MODULE)_SDK_HEADERS := $(MODULE_EXPORT_SDK_HEADERS)
_MODULES_$(MODULE)_SRCDEPS := $(MODULE_EXPORT_SRCDEPS)

# We need to process each dependent module only once.
# Therefore we get the realpath to avoid different relative-path references to the same module,
# then sort to remove any duplicates.
# Module dependencies are then make relative to to top of the build environment.
MODULE_REAL_LIBRARY_DEPS := $(realpath $(MODULE_LIBRARY_DEPS))
ifneq ($(words MODULE_REAL_LIBRARY_DEPS), $(words MODULE_LIBRARY_DEPS))
	$(error some modules path do not exist)
endif

MODULE_UNIQUE_LIBRARY_DEPS := $(sort $(foreach dep, $(MODULE_REAL_LIBRARY_DEPS), $(subst $(TRUSTY_TOP)/,,$(dep))))
$(foreach dep,$(MODULE_UNIQUE_LIBRARY_DEPS),\
	$(eval DEPENDENCY_MODULE := $(dep))\
	$(eval include make/userspace_recurse.mk))

# Include exported flags in the local build
MODULE_LIBRARIES := $(filter-out $(MODULE_LIBRARIES),$(MODULE_EXPORT_LIBRARIES)) $(MODULE_LIBRARIES)
MODULE_EXTRA_OBJECTS := $(filter-out $(MODULE_EXTRA_OBJECTS),$(MODULE_EXPORT_EXTRA_OBJECTS)) $(MODULE_EXTRA_OBJECTS)
MODULE_WHOLE_ARCHIVES := $(filter-out $(MODULE_WHOLE_ARCHIVES),$(MODULE_EXPORT_WHOLE_ARCHIVES)) $(MODULE_WHOLE_ARCHIVES)
MODULE_RLIBS := $(filter-out $(MODULE_RLIBS),$(MODULE_EXPORT_RLIBS)) $(MODULE_RLIBS)
MODULE_COMPILEFLAGS := $(MODULE_EXPORT_COMPILEFLAGS) $(MODULE_COMPILEFLAGS)
MODULE_CONSTANTS := $(MODULE_EXPORT_CONSTANTS) $(MODULE_CONSTANTS)
MODULE_CFLAGS := $(MODULE_EXPORT_CFLAGS) $(MODULE_CFLAGS)
MODULE_CPPFLAGS := $(MODULE_EXPORT_CPPFLAGS) $(MODULE_CPPFLAGS)
MODULE_ASMFLAGS := $(MODULE_EXPORT_ASMFLAGS) $(MODULE_ASMFLAGS)
MODULE_LDFLAGS := $(filter-out $(MODULE_LDFLAGS),$(MODULE_EXPORT_LDFLAGS)) $(MODULE_LDFLAGS)
MODULE_SDK_LIBS := $(filter-out $(MODULE_SDK_LIBS),$(MODULE_EXPORT_SDK_LIBS)) $(MODULE_SDK_LIBS)
MODULE_SDK_HEADERS := $(filter-out $(MODULE_SDK_HEADERS),$(MODULE_EXPORT_SDK_HEADERS)) $(MODULE_SDK_HEADERS)
MODULE_SRCDEPS := $(MODULE_EXPORT_SRCDEPS) $(MODULE_SRCDEPS)

ifeq ($(filter $(MODULE),$(TRUSTY_SDK_MODULES)),)
# Only add in tree header paths to this module's include path if this module
# isn't part of the SDK
MODULE_INCLUDES := $(MODULE_EXPORT_INCLUDES) $(MODULE_INCLUDES)
endif

# Make sure the headers this module requires are copied before the module is
# compiled
MODULE_SRCDEPS += $(MODULE_SDK_HEADERS)
MODULE_EXPORT_SRCDEPS += $(MODULE_SDK_HEADERS)

# Generate constant headers and manifest, if needed.
include make/gen_manifest.mk

# Generate Rust bindings with bindgen if requested
ifneq ($(strip $(MODULE_BINDGEN_SRC_HEADER)),)
include make/bindgen.mk
endif

ifneq ($(MODULE_SRCS)$(MODULE_SRCS_FIRST),)
# Not a header-only library, so we need to build the source files

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)

include make/rust.mk

_MODULES_$(MODULE)_CRATE_INDEX := $(GLOBAL_CRATE_COUNT)
GLOBAL_CRATE_COUNT := $(shell echo $$(($(GLOBAL_CRATE_COUNT)+1)))

define CRATE_CONFIG :=
{
	"display_name": "$(MODULE_RUST_STEM)",
	"root_module": "$(filter %.rs,$(MODULE_SRCS))",
	"edition": "$(MODULE_RUST_EDITION)",
	"deps": [
		$(call STRIP_TRAILING_COMMA,$(foreach dep,$(sort $(MODULE_LIBRARY_DEPS)),\
				$(if $(_MODULES_$(dep)_RUST_STEM),{"name": "$(_MODULES_$(dep)_RUST_STEM)"$(COMMA) "crate": $(_MODULES_$(dep)_CRATE_INDEX)}$(COMMA))))
	]
},

endef
RUST_ANALYZER_CRATES := $(RUST_ANALYZER_CRATES)$(CRATE_CONFIG)
CRATE_CONFIG :=

endif # MODULE_IS_RUST

# Save our current module because module.mk clears it.
LIB_SAVED_MODULE := $(MODULE)
LIB_SAVED_MODULE_LIBRARY_DEPS := $(MODULE_LIBRARY_DEPS)
LIB_SAVED_MODULE_SRCDEPS := $(MODULE_SRCDEPS)

# Save the rust flags for use in trusted_app.mk. userspace_recurse.mk will clean
# up after us.
LIB_SAVED_MODULE_RUSTFLAGS := $(MODULE_RUSTFLAGS)
LIB_SAVED_MODULE_RUSTDOCFLAGS := $(MODULE_RUSTDOCFLAGS)
LIB_SAVED_MODULE_RUSTDOC_OBJECT := $(MODULE_RUSTDOC_OBJECT)
LIB_SAVED_MODULE_IS_RUST := $(MODULE_IS_RUST)

ALLMODULE_OBJS :=
MODULE_LIBRARY_DEPS :=

include make/module.mk

# Handle any MODULE_DEPS
include make/recurse.mk

MODULE_LIBRARY_DEPS := $(LIB_SAVED_MODULE_LIBRARY_DEPS)
MODULE_SRCDEPS := $(LIB_SAVED_MODULE_SRCDEPS)
MODULE := $(LIB_SAVED_MODULE)
MODULE_RUSTFLAGS := $(LIB_SAVED_MODULE_RUSTFLAGS)
MODULE_RUSTDOCFLAGS := $(LIB_SAVED_MODULE_RUSTDOCFLAGS)
MODULE_RUSTDOC_OBJECT := $(LIB_SAVED_MODULE_RUSTDOC_OBJECT)
MODULE_IS_RUST := $(LIB_SAVED_MODULE_IS_RUST)

$(BUILDDIR)/%: CC := $(CCACHE) $(CLANG_BINDIR)/clang
$(BUILDDIR)/%: RUSTC := $(RUST_BINDIR)/rustc
$(BUILDDIR)/%.o: GLOBAL_OPTFLAGS := $(GLOBAL_SHARED_OPTFLAGS) $(GLOBAL_USER_OPTFLAGS) $(GLOBAL_USER_IN_TREE_OPTFLAGS) $(ARCH_OPTFLAGS)
$(BUILDDIR)/%.o: GLOBAL_COMPILEFLAGS := $(GLOBAL_SHARED_COMPILEFLAGS) $(GLOBAL_USER_COMPILEFLAGS) $(GLOBAL_USER_IN_TREE_COMPILEFLAGS)
$(BUILDDIR)/%.o: GLOBAL_CFLAGS   := $(GLOBAL_SHARED_CFLAGS) $(GLOBAL_USER_CFLAGS) $(GLOBAL_USER_IN_TREE_CFLAGS)
$(BUILDDIR)/%.o: GLOBAL_CPPFLAGS := $(GLOBAL_SHARED_CPPFLAGS) $(GLOBAL_USER_CPPFLAGS) $(GLOBAL_USER_IN_TREE_CPPFLAGS)
$(BUILDDIR)/%.o: GLOBAL_ASMFLAGS := $(GLOBAL_SHARED_ASMFLAGS) $(GLOBAL_USER_ASMFLAGS) $(GLOBAL_USER_IN_TREE_ASMFLAGS)
$(BUILDDIR)/%.o: GLOBAL_INCLUDES := $(addprefix -I,$(GLOBAL_UAPI_INCLUDES) $(GLOBAL_SHARED_INCLUDES) $(GLOBAL_USER_INCLUDES))
$(BUILDDIR)/%.o: ARCH_COMPILEFLAGS := $(ARCH_$(ARCH)_COMPILEFLAGS)
$(BUILDDIR)/%.o: ARCH_CFLAGS := $(ARCH_$(ARCH)_CFLAGS)
$(BUILDDIR)/%.o: THUMBCFLAGS := $(ARCH_$(ARCH)_THUMBCFLAGS)
$(BUILDDIR)/%.o: ARCH_CPPFLAGS := $(ARCH_$(ARCH)_CPPFLAGS)
$(BUILDDIR)/%.o: ARCH_ASMFLAGS := $(ARCH_$(ARCH)_ASMFLAGS)

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
LIBRARY_ARCHIVE := $(filter %.rlib %.so,$(ALLMODULE_OBJS))
else
LIBRARY_ARCHIVE := $(filter %.mod.a,$(ALLMODULE_OBJS))
endif

ifneq ($(filter $(MODULE),$(TRUSTY_SDK_MODULES)),)
# Install the library into the SDK

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
# Rust modules aren't added to the SDK sysroot yet. We need to keep track of the
# library archive here so that we can ensure it is built before its dependencies.
#
# TODO: Add proper support for SDK rlibs
MODULE_EXPORT_LIBRARIES += $(LIBRARY_ARCHIVE)
endif

SDK_LIB := $(TRUSTY_SDK_LIB_DIR)/lib$(MODULE_SDK_LIB_NAME).a
ALLMODULE_OBJS := $(filter-out $(LIBRARY_ARCHIVE),$(ALLMODULE_OBJS))
OTHER_SDK_OBJS := $(addprefix $(TRUSTY_SDK_LIB_DIR)/,$(notdir $(ALLMODULE_OBJS)))
$(SDK_LIB): OTHER_OBJS := $(ALLMODULE_OBJS)
$(SDK_LIB): $(LIBRARY_ARCHIVE) $(ALLMODULE_OBJS)
	@$(MKDIR)
	@cp $< $@
	@[ -z "$(OTHER_OBJS)" ] || cp $(OTHER_OBJS) $(TRUSTY_SDK_LIB_DIR)/

# Ensure that any extra SDK objects are copied if they are missing
$(OTHER_SDK_OBJS): $(SDK_LIB)

MODULE_SDK_LIBS += $(OTHER_SDK_OBJS)
ALL_SDK_LIBS += $(SDK_LIB) $(OTHER_SDK_OBJS)

# Add any module licenses, if found
MODULE_LICENSES += $(wildcard $(MODULE)/LICENSE*) $(wildcard $(MODULE)/NOTICE)

# Generate the library makefile

SDK_MAKEFILE := $(TRUSTY_SDK_DIR)/make/lib$(MODULE_SDK_LIB_NAME).mk
$(SDK_MAKEFILE): MODULE_EXPORT_DEFINES := $(MODULE_EXPORT_DEFINES)
$(SDK_MAKEFILE): MODULE_EXPORT_CFLAGS := \
	$(MODULE_EXPORT_OPTFLAGS) $(MODULE_EXPORT_COMPILEFLAGS) $(MODULE_EXPORT_CFLAGS)
$(SDK_MAKEFILE): MODULE_EXPORT_CXXFLAGS := \
	$(MODULE_EXPORT_OPTFLAGS) $(MODULE_EXPORT_COMPILEFLAGS) $(MODULE_EXPORT_CPPFLAGS)
$(SDK_MAKEFILE): MODULE_EXPORT_ASMFLAGS := \
	$(MODULE_EXPORT_OPTFLAGS) $(MODULE_EXPORT_COMPILEFLAGS) $(MODULE_EXPORT_ASMFLAGS)
$(SDK_MAKEFILE): MODULE_EXPORT_LDFLAGS := $(MODULE_EXPORT_LDFLAGS)
$(SDK_MAKEFILE): MODULE_SDK_LIB_NAME := $(MODULE_SDK_LIB_NAME)
$(SDK_MAKEFILE): OTHER_SDK_OBJS := $(addprefix $$(SDK_SYSROOT_DIR)/usr/lib/,$(notdir $(OTHER_SDK_OBJS)))
$(SDK_MAKEFILE):
	@$(MKDIR)
	@$(call ECHO,sdk,generating makefile,for $(MODULE_SDK_LIB_NAME))
	$(NOECHO)rm -f $@.tmp
	$(NOECHO)echo DEFINES += $(call prepare-sdk-flags,$(MODULE_EXPORT_DEFINES)) >> $@.tmp
	$(NOECHO)echo CFLAGS += $(call prepare-sdk-flags,$(MODULE_EXPORT_CFLAGS)) >> $@.tmp
	$(NOECHO)echo CXXFLAGS += $(call prepare-sdk-flags,$(MODULE_EXPORT_CXXFLAGS)) >> $@.tmp
	$(NOECHO)echo ASMFLAGS += $(call prepare-sdk-flags,$(MODULE_EXPORT_ASMFLAGS)) >> $@.tmp
	$(NOECHO)echo LDFLAGS += $(call prepare-sdk-flags,$(MODULE_EXPORT_LDFLAGS)) >> $@.tmp
	$(NOECHO)echo 'TRUSTY_APP_OBJECTS += $(OTHER_SDK_OBJS)' >> $@.tmp
	$(NOECHO)sed -i 's/ \+$$//' $@.tmp
	@$(call TESTANDREPLACEFILE,$@.tmp,$@)
	@$(call ECHO_DONE_SILENT,sdk,generating makefile,for $(MODULE_SDK_LIB_NAME))

ALL_SDK_EXTRA_FILES += $(SDK_MAKEFILE)

else # not an SDK module

# Libraries not in the SDK are included directly in the link as archives, rather
# than via `-l`.
MODULE_EXPORT_LIBRARIES += $(LIBRARY_ARCHIVE)

endif # SDK module

MODULE_EXPORT_EXTRA_OBJECTS += $(filter-out $(LIBRARY_ARCHIVE),$(ALLMODULE_OBJS))

ifeq ($(call TOBOOL,$(MODULE_USE_WHOLE_ARCHIVE)),true)
MODULE_EXPORT_WHOLE_ARCHIVES += $(LIBRARY_ARCHIVE)
# Include the current module explicitly in MODULE_WHOLE_ARCHIVES
# in case we were included from trusted_app.mk
MODULE_WHOLE_ARCHIVES += $(LIBRARY_ARCHIVE)
endif

# Append dependency libraries into ALLMODULE_OBJS. This needs to happen after we
# set up the SDK library copies, if necessary, because we need ALLMODULE_OBJS
# without dependencies there.
ALLMODULE_OBJS := $(ALLMODULE_OBJS) $(filter-out $(ALLMODULE_OBJS),$(MODULE_LIBRARIES))

endif # MODULE is not a header-only library

_MODULES_$(MODULE)_LIBRARIES := $(MODULE_EXPORT_LIBRARIES)
_MODULES_$(MODULE)_LICENSES := $(MODULE_LICENSES)
_MODULES_$(MODULE)_EXTRA_OBJECTS := $(MODULE_EXPORT_EXTRA_OBJECTS)
_MODULES_$(MODULE)_WHOLE_ARCHIVES := $(MODULE_EXPORT_WHOLE_ARCHIVES)
_MODULES_$(MODULE)_RLIBS := $(MODULE_EXPORT_RLIBS)
_MODULES_$(MODULE)_SDK_LIBS := $(MODULE_SDK_LIBS)
_MODULES_$(MODULE)_LDFLAGS := $(MODULE_EXPORT_LDFLAGS)
_MODULES_$(MODULE)_SDK_HEADERS := $(MODULE_EXPORT_SDK_HEADERS)

ifeq ($(call TOBOOL,$(MODULE_RUST_TESTS)),true)
# Rebuild this module as a test service as well
BUILD_AS_RUST_TEST_MODULE := true
DEPENDENCY_MODULE := $(MODULE)-test
DEPENDENCY_MODULE_PATH := $(MODULE)
include make/userspace_recurse.mk
endif
endif # module is not disabled
endif # not building rust test app

$(call INFO_DONE_SILENT,$(MODULE),processing,library or app)

endif # building userspace module

# Reset all variables for the next module
MODULE :=
MODULE_CRATE_NAME :=
MODULE_RUST_STEM :=
MODULE_SRCDEPS :=
MODULE_LIBRARY_DEPS :=
MODULE_LIBRARY_EXPORTED_DEPS :=
MODULE_USE_WHOLE_ARCHIVE :=
MODULE_LIBRARIES :=
MODULE_LICENSES :=
MODULE_RLIBS :=
MODULE_RSOBJS :=
MODULE_RUSTDOC_OBJECT :=
MODULE_RUSTDOCFLAGS :=
MODULE_SKIP_DOCS :=
MODULE_DISABLED :=
MODULE_SDK_LIB_NAME :=
MODULE_SDK_HEADER_INSTALL_DIR :=
MODULE_SDK_HEADERS :=
# MODULE_WHOLE_ARCHIVES is used by trusted_app.mk
# so we intentionally do not reset it here

LIB_SAVED_MODULE :=
LIB_SAVED_ALLMODULE_OBJS :=

ifneq ($(filter proc-macro,$(MODULE_RUST_CRATE_TYPES)),)
# Reset host build state only once we finish building the proc-macro and its deps
MODULE_RUST_HOST_LIB :=
endif
MODULE_RUST_CRATE_TYPES :=
MODULE_RUST_TESTS :=
OTHER_SDK_OBJS :=
SDK_LIB :=
OTHER_OBJS :=
OTHER_SDK_OBJS :=

MODULE_EXPORT_LIBRARIES :=
MODULE_EXPORT_RLIBS :=
MODULE_EXPORT_EXTRA_OBJECTS :=
MODULE_EXPORT_WHOLE_ARCHIVES :=
MODULE_EXPORT_COMPILEFLAGS :=
MODULE_EXPORT_CONSTANTS :=
MODULE_EXPORT_CFLAGS :=
MODULE_EXPORT_CPPFLAGS :=
MODULE_EXPORT_ASMFLAGS :=
MODULE_EXPORT_INCLUDES :=
MODULE_EXPORT_LDFLAGS :=
MODULE_EXPORT_SDK_HEADERS :=
MODULE_EXPORT_SRCDEPS :=
MODULE_UNIQUE_LIBRARY_DEPS :=

endif # QUERY_MODULE (this line should stay after all other processing)
