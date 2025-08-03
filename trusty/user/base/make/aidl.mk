# Copyright (c) 2022, Google, Inc. All rights reserved
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

# Build an AIDL module for Trusty
#
# args:
# MODULE : module name (required)
# MODULE_AIDLS: list of AIDL files
# MODULE_AIDL_FLAGS: optional flags for the AIDL_TOOL binary
# MODULE_AIDL_PACKAGE: a path that matches the directory structure of the AIDL
#     package utilized in the module. For example, declaring
#     `package com.android.trusty.binder` should correspond to a
#     MODULE_AIDL_PACKAGE of com/android/trusty/binder. If MODULE_AIDL_PACKAGE
#     is not defined, it implies that there is no top-level `package`
#     declaration.
# MODULE_AIDL_LANGUAGE: the language to auto-generate the files for. Current
#     options are `cpp` and `rust`.
# MODULE_AIDL_RUST_DEPS: names of Rust AIDL crates that the current module
#     depends on. Until we find a way to automatically infer these, users
#     will have to specify them manually.

# Check that there are is at most one package specified
ifeq ($(filter $(words $(MODULE_AIDL_PACKAGE)),0 1),)
$(error $(MODULE) has the following packages $(MODULE_AIDL_PACKAGE), but only one is supported)
endif

ifeq ($(MODULE_AIDL_LANGUAGE),)
MODULE_AIDL_LANGUAGE := cpp
endif

ifeq ($(MODULE_AIDL_LANGUAGE),cpp)
AIDL_EXT := cpp
AIDL_HEADER_DIR := $(BUILDDIR)/include
else ifeq ($(MODULE_AIDL_LANGUAGE),rust)
AIDL_EXT := rs
AIDL_HEADER_DIR :=
else
$(error "Unsupported AIDL language: $(MODULE_AIDL_LANGUAGE)")
endif

# TODO: this implies all sources are in MODULE_AIDL_PACKAGE or are subpackages
# of MODULE_AIDL_PACKAGE; support multiple packages
GET_AIDL_PACKAGE_ROOT = $(if $(MODULE_AIDL_PACKAGE),$(firstword $(subst $(MODULE_AIDL_PACKAGE), ,$1)),$(dir $1))

ifneq (,$(wildcard out/host/linux-x86/bin/aidl))
# Use the aidl tool from the build output if it exists
AIDL_TOOL := out/host/linux-x86/bin/aidl
else
AIDL_TOOL := prebuilts/build-tools/linux-x86/bin/aidl
endif

MODULE_AIDL_INCLUDES ?=
AIDL_SRCS := $(call TOBUILDDIR,$(patsubst %.aidl,%.$(AIDL_EXT),$(MODULE_AIDLS)))
AIDL_RUST_GLUE_TOOL := system/tools/aidl/build/aidl_rust_glue.py
MODULE_AIDL_INCLUDES += $(foreach dir,$(sort $(foreach src,$(MODULE_AIDLS),$(call GET_AIDL_PACKAGE_ROOT,$(src)))), -I $(patsubst %/,%,$(dir)))

# TODO: support multiple, disparate packages; for AIDL interfaces with package paths,
# the output directory for the tool should be at the root of
# the package path. The compiler creates one subdirectory
# per package component, e.g., com.foo.IFoo goes into com/foo/IFoo.cpp.
# Luckily the .aidl files are also required to follow this structure,
# so the input file is also com/foo/IFoo.aidl.
$(AIDL_SRCS): AIDL_HEADER_DIR := $(AIDL_HEADER_DIR)
$(AIDL_SRCS): AIDL_EXT := $(AIDL_EXT)
$(AIDL_SRCS): AIDL_TOOL := $(AIDL_TOOL)
$(AIDL_SRCS): MODULE_AIDL_INCLUDES := $(MODULE_AIDL_INCLUDES)
$(AIDL_SRCS): MODULE_AIDL_FLAGS := $(MODULE_AIDL_FLAGS)
$(AIDL_SRCS): MODULE_AIDL_LANGUAGE := $(MODULE_AIDL_LANGUAGE)
$(AIDL_SRCS): MODULE_AIDL_PACKAGE := $(MODULE_AIDL_PACKAGE)
$(AIDL_SRCS): MODULE := $(MODULE)
$(AIDL_SRCS): $(BUILDDIR)/%.$(AIDL_EXT): %.aidl
	@$(MKDIR)
	@if [ -n "$(AIDL_HEADER_DIR)" ]; then mkdir -p $(AIDL_HEADER_DIR); fi
	@$(call ECHO,$(MODULE),generating from AIDL,$@)
	$(NOECHO)$(AIDL_TOOL) --lang=$(MODULE_AIDL_LANGUAGE) --structured $(MODULE_AIDL_INCLUDES) \
		$(foreach dir,$(AIDL_HEADER_DIR),-h $(dir)) -o $(call GET_AIDL_PACKAGE_ROOT,$@) $(MODULE_AIDL_FLAGS) $<
	@$(call ECHO_DONE_SILENT,$(MODULE),generating from AIDL,$@)

ifeq ($(MODULE_AIDL_LANGUAGE),cpp)
MODULE_SRCS += $(AIDL_SRCS)

# AIDL generates .cpp files which depend on the binder and C++ modules
ifeq ($(call TOBOOL,$(TRUSTY_NEW_MODULE_SYSTEM)),false)
MODULE_DEPS += \
	trusty/kernel/lib/libcxx-trusty \
	frameworks/native/libs/binder/trusty/kernel
else
MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libstdc++-trusty \
	frameworks/native/libs/binder/trusty
endif

MODULE_EXPORT_INCLUDES += $(AIDL_HEADER_DIR)

# Ensure that all auto-generated code, including headers, is
# emitted before downstream dependencies
MODULE_EXPORT_SRCDEPS += $(AIDL_SRCS)
else # Rust
AIDL_ROOT_RS := $(sort $(foreach src,$(AIDL_SRCS),$(call GET_AIDL_PACKAGE_ROOT,$(src))/$(MODULE_CRATE_NAME).rs))

ifneq ($(words $(AIDL_ROOT_RS)),1)
$(error Unable to determine root AIDL .rs file for $(MODULE))
endif

# Generate the top-level aidl_lib.rs for this module
$(AIDL_ROOT_RS): AIDL_RUST_GLUE_TOOL := $(AIDL_RUST_GLUE_TOOL)
$(AIDL_ROOT_RS): MODULE_AIDL_RUST_DEPS := $(foreach crate,$(MODULE_AIDL_RUST_DEPS),-I $(crate))
$(AIDL_ROOT_RS): $(AIDL_SRCS)
	@echo generating $@ from AIDL Rust glue
	$(NOECHO)$(AIDL_RUST_GLUE_TOOL) $(MODULE_AIDL_RUST_DEPS) $@ $(dir $@) $^

MODULE_LIBRARY_DEPS += \
	frameworks/native/libs/binder/trusty/rust \
	external/rust/crates/async-trait \
	external/rust/crates/lazy_static \

# The AIDL compiler marks an aidl_data variable as mutable and rustc complains
MODULE_RUSTFLAGS += -Aunused-mut -Aunused-variables

MODULE_SRCS += $(AIDL_ROOT_RS)
MODULE_EXPORT_SRCDEPS += $(AIDL_ROOT_RS)
endif

# Build the AIDL module into a library
include make/library.mk

MODULE_AIDLS :=
MODULE_AIDL_INCLUDES :=
MODULE_AIDL_FLAGS :=
MODULE_AIDL_PACKAGE :=
MODULE_AIDL_LANGUAGE :=
MODULE_AIDL_RUST_DEPS :=
AIDL_EXT :=
AIDL_HEADER_DIR :=
AIDL_SRCS :=
AIDL_TOOL :=
AIDL_RUST_GLUE_TOOL :=
AIDL_ROOT_RS :=
