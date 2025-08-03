#
# Copyright (c) 2024, Google, Inc. All rights reserved
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

# Query variables set by another module's rules.mk file
#
# args:
# QUERY_MODULE : the path to the module directory to query
# QUERY_VARIABLES : a space-separated list of variables to query
#
# sets:
# QUERY_foo for each variable "foo" named in QUERY_VARIABLES

# these come from module.mk, rust.mk, and library.mk (in sections):
define QUERY_STOMPED_VARIABLES
MODULE
MODULE_SRCDIR
MODULE_BUILDDIR
MODULE_DEPS
MODULE_SRCS
MODULE_OBJS
MODULE_DEFINES
MODULE_OPTFLAGS
MODULE_COMPILEFLAGS
MODULE_CFLAGS
MODULE_CPPFLAGS
MODULE_ASMFLAGS
MODULE_RUSTFLAGS
MODULE_RUSTDOCFLAGS
MODULE_SRCDEPS
MODULE_INCLUDES
MODULE_EXTRA_ARCHIVES
MODULE_EXTRA_OBJS
MODULE_CONFIG
MODULE_OBJECT
MODULE_ARM_OVERRIDE_SRCS
MODULE_SRCS_FIRST
MODULE_INIT_OBJS
MODULE_DISABLE_LTO
MODULE_LTO_ENABLED
MODULE_DISABLE_CFI
MODULE_DISABLE_STACK_PROTECTOR
MODULE_DISABLE_SCS
MODULE_RSSRC
MODULE_IS_RUST
MODULE_RSOBJS
MODULE_RUST_EDITION
MODULE_RUSTDOC_OBJECT
MODULE_RUSTDOCFLAGS
MODULE_KERNEL_RUST_DEPS
MODULE_SKIP_DOCS
MODULE_ADD_IMPLICIT_DEPS

MODULE_RUST_CRATE_TYPES

MODULE
MODULE_CRATE_NAME
MODULE_RUST_STEM
MODULE_SRCDEPS
MODULE_LIBRARY_DEPS
MODULE_LIBRARY_EXPORTED_DEPS
MODULE_USE_WHOLE_ARCHIVE
MODULE_LIBRARIES
MODULE_LICENSES
MODULE_RLIBS
MODULE_RSOBJS
MODULE_RUSTDOC_OBJECT
MODULE_RUSTDOCFLAGS
MODULE_SKIP_DOCS
MODULE_DISABLED
MODULE_SDK_LIB_NAME
MODULE_SDK_HEADER_INSTALL_DIR
MODULE_SDK_HEADERS
endef

ifeq ($(QUERY_MODULE),)
$(error QUERY_MODULE must be set to a module path before including query.mk)
endif

# save state
$(foreach var,$(QUERY_STOMPED_VARIABLES),$(eval QUERY_SAVED_$(var) := $($(var))))

# clear state
$(foreach var,$(QUERY_STOMPED_VARIABLES),$(eval $(var) :=))

# include specified target rules.mk
include $(QUERY_MODULE)/rules.mk

# "return" queried variables
$(foreach var,$(QUERY_VARIABLES),$(eval QUERY_$(var) := $($(var))))

# restore state
$(foreach var,$(QUERY_STOMPED_VARIABLES),$(eval $(var) := $(QUERY_SAVED_$(var))))
QUERY_MODULE :=
