#
# Copyright (c) 2023, Google, Inc. All rights reserved
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

# Normalize inputs for building Rust modules
#
# args:
# MODULE : name of the module
# MODULE_SRCS : source files of the module, which should be a single .rs file
# MODULE_RUST_CRATE_TYPES : list of crate types to build, rlib if not set
# MODULE_CRATE_NAME : name of the crate, which can differ from module name. this
# variable's value read outside Make for recursive dependencies, so it must be
# unconditionally assigned a literal string, e.g. MODULE_CRATE_NAME := foo
# MODULE_RUST_STEM : The stem of the output .rlib file for this library.
# 	Defaults to $(MODULE_CRATE_NAME) if left empty.
# MODULE_RUSTFLAGS : any extra flags to pass to rustc for the module
# MODULE_RUST_HOST_LIB : whether the module is a host library
# MODULE_SKIP_DOCS : whether to skip building SDK docs for a module
#
# sets:
# MODULE_RUSTFLAGS
# MODULE_RUSTDOCFLAGS
# MODULE_RSOBJS
# MODULE_RUST_CRATE_TYPES
# MODULE_RUSTDOC_OBJECT
# MODULE_EXPORT_RLIBS
# (for trusted apps):
# TRUSTY_APP_RUST_MAIN_SRC
# TRUSTY_APP_RUST_SRCDEPS
#
# This file is not intended to be included directly by modules, just used from
# the library (new) and module (old/kernel) build systems.

ifneq ($(strip $(MODULE_SRCS_FIRST)),)
$(error $(MODULE) sets MODULE_SRCS_FIRST but is a Rust module, which does not support MODULE_SRCS_FIRST)
endif

ifneq ($(filter-out %.rs,$(MODULE_SRCS)),)
$(error $(MODULE) includes both Rust source files and other source files. Rust modules must only contain Rust sources.)
endif

ifneq ($(words $(filter %.rs,$(MODULE_SRCS))),1)
$(error $(MODULE) includes more than one Rust file in MODULE_SRCS)
endif

ifneq ($(filter-out rlib staticlib bin proc-macro,$(MODULE_RUST_CRATE_TYPES)),)
$(error $(MODULE) contains unrecognized crate type $(filter-out rlib staticlib bin proc-macro,$(MODULE_RUST_CRATE_TYPES)) in MODULE_RUST_CRATE_TYPES)
endif

ifeq ($(MODULE_CRATE_NAME),)
$(error $(MODULE) is a Rust module but does not set MODULE_CRATE_NAME)
endif

# Stem defaults to the crate name
ifeq ($(MODULE_RUST_STEM),)
MODULE_RUST_STEM := $(MODULE_CRATE_NAME)
endif

MODULE_RUSTFLAGS += --crate-name=$(MODULE_CRATE_NAME)

# Throw the module name into the stable crate id so rustc distinguishes
# between different crates with the same name
MODULE_RUSTFLAGS += -C metadata=$(MODULE_RUST_STEM)

# Default Rust edition unless otherwise specified
ifeq ($(MODULE_RUST_EDITION),)
MODULE_RUST_EDITION := 2021
endif

MODULE_RUSTFLAGS += --edition $(MODULE_RUST_EDITION)

# Specify paths of dependency libraries because we are not going through Cargo
MODULE_RUSTFLAGS += $(addprefix --extern ,$(MODULE_RLIBS))

MODULE_RUSTFLAGS_PRELINK := $(MODULE_RUSTFLAGS)
MODULE_RUSTFLAGS += --emit link

# Allow all lints if the module is in external/. This matches the behavior of
# soong.
ifneq ($(filter external/%,$(MODULE_SRCS)),)
MODULE_RUSTFLAGS += --cap-lints allow
MODULE_RUSTDOCFLAGS += --cap-lints allow
endif

MODULE_RSOBJS :=

# default to rlib-only if no crate types specified
ifeq ($(strip $(MODULE_RUST_CRATE_TYPES)),)
MODULE_RUST_CRATE_TYPES := rlib
$(warn MODULE_RUST_CRATE_TYPES defaulting to rlib)
endif

ifneq ($(filter proc-macro,$(MODULE_RUST_CRATE_TYPES)),)
MODULE_CRATE_OUTPUT := $(call TOBUILDDIR,lib$(MODULE_RUST_STEM).so)
MODULE_RSOBJS += $(MODULE_CRATE_OUTPUT)
$(MODULE_CRATE_OUTPUT): MODULE_RUSTFLAGS := $(MODULE_RUSTFLAGS) \
	--crate-type=proc-macro --extern proc_macro -C prefer-dynamic
MODULE_EXPORT_RLIBS += $(MODULE_CRATE_NAME)=$(MODULE_CRATE_OUTPUT)

MODULE_RUSTDOCFLAGS += --crate-type=proc-macro --extern proc_macro
endif # proc-macro crate

ifneq ($(filter rlib,$(MODULE_RUST_CRATE_TYPES)),)
MODULE_CRATE_OUTPUT := $(call TOBUILDDIR,lib$(MODULE_RUST_STEM).rlib)
MODULE_RSOBJS += $(MODULE_CRATE_OUTPUT)
$(MODULE_CRATE_OUTPUT): MODULE_RUSTFLAGS := $(MODULE_RUSTFLAGS) --crate-type=rlib
MODULE_EXPORT_RLIBS += $(MODULE_CRATE_NAME)=$(MODULE_CRATE_OUTPUT)
endif # rlib crate

ifneq ($(filter staticlib,$(MODULE_RUST_CRATE_TYPES)),)
MODULE_CRATE_OUTPUT := $(call TOBUILDDIR,lib$(MODULE_RUST_STEM).a)
MODULE_RSOBJS += $(MODULE_CRATE_OUTPUT)
$(MODULE_CRATE_OUTPUT): MODULE_RUSTFLAGS := $(MODULE_RUSTFLAGS) --crate-type=staticlib
endif # staticlib crate

ifneq ($(filter bin,$(MODULE_RUST_CRATE_TYPES)),)
# Used in trusted_app.mk
TRUSTY_APP_RUST_MAIN_SRC := $(filter %.rs,$(MODULE_SRCS))

TRUSTY_APP_RUST_SRCDEPS := $(MODULE_SRCDEPS)
endif

ifeq ($(call TOBOOL,$(MODULE_SKIP_DOCS)),false)
ifneq ($(TRUSTY_SDK_LIB_DIR),)
MODULE_RUSTDOC_OBJECT := $(TRUSTY_SDK_LIB_DIR)/doc/built/$(MODULE_RUST_STEM)
endif
else
MODULE_RUSTDOC_OBJECT :=
endif

MODULE_CRATE_OUTPUT :=

ifeq ($(call TOBOOL,$(MODULE_RUST_HOST_LIB)),true)
# Remove the target-specific flags
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): ARCH_RUSTFLAGS :=
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): GLOBAL_RUSTFLAGS := $(GLOBAL_HOST_RUSTFLAGS)
else
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): ARCH_RUSTFLAGS := $(ARCH_$(ARCH)_RUSTFLAGS)
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): MODULE_RUST_ENV := $(MODULE_RUST_ENV)
# Is module a kernel module? (not an app or using the library build system)
ifeq ($(call TOBOOL,$(SAVED_MODULE_STACK)$(TRUSTY_NEW_MODULE_SYSTEM)$(TRUSTY_APP)),false)
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): GLOBAL_RUSTFLAGS := $(GLOBAL_SHARED_RUSTFLAGS) $(GLOBAL_KERNEL_RUSTFLAGS)
else
$(MODULE_RSOBJS) $(MODULE_RUSTDOC_OBJECT): GLOBAL_RUSTFLAGS := $(GLOBAL_SHARED_RUSTFLAGS) $(GLOBAL_USER_RUSTFLAGS)
endif
endif

$(MODULE_RSOBJS): MODULE_CRATE_NAME := $(MODULE_CRATE_NAME)
$(MODULE_RSOBJS): MODULE_RUST_STEM := $(MODULE_RUST_STEM)

$(MODULE_RUSTDOC_OBJECT): RUSTDOC := $(RUST_BINDIR)/rustdoc
$(MODULE_RUSTDOC_OBJECT): MODULE_RUSTDOC_OUT_DIR := $(TRUSTY_SDK_LIB_DIR)/doc
$(MODULE_RUSTDOC_OBJECT): MODULE_RUSTDOCFLAGS := $(MODULE_RUSTFLAGS_PRELINK) $(MODULE_RUSTDOCFLAGS)
$(MODULE_RUSTDOC_OBJECT): MODULE_CRATE_NAME := $(MODULE_CRATE_NAME)
