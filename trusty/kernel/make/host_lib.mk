#
# Copyright (c) 2022, Google, Inc. All rights reserved
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

# Rather than relying on the libraries provided by the host, these rules build
# library dependencies for host tools and tests. Note that for simplicity,
# building library dependencies recursively is not supported; any dependencies
# for the library being built using these rules must be provided by the host.

# args:
# HOST_LIB_NAME : name of the library (required)
# HOST_LIB_SRCS : list of source files (required)
# HOST_LIB_FLAGS : list of flags for the compiler
# HOST_LIB_VARIANT : suffix for the host lib to support build variants
# HOST_INCLUDE_DIRS : list of include directories that all of the host tool/test depends on

# output
# lib$(HOST_LIB_NAME).a is appended to HOST_LIB_ARCHIVES

# Validate arguments.
ifeq ($(HOST_LIB_NAME), )
$(error HOST_LIB_NAME must be specified)
endif

ifeq ($(HOST_LIB_SRCS), )
$(error HOST_LIB_SRCS must be specified)
endif

# Build a static archive variant if requested
ifeq (true, $(call TOBOOL,$(HOST_STATIC_LINK)))
HOST_LIB_FLAGS += -static
HOST_LIB_VARIANT += -static
endif

HOST_LIB_ARCHIVE := $(BUILDDIR)/host_libs/lib$(HOST_LIB_NAME)$(HOST_LIB_VARIANT).a

# Guard against multiple rules for the same targets which produces make warnings
ifndef HEADER_GUARD_HOST_LIB_$(BUILDDIR)_$(HOST_LIB_NAME)_$(HOST_LIB_VARIANT)
HEADER_GUARD_HOST_LIB_$(BUILDDIR)_$(HOST_LIB_NAME)_$(HOST_LIB_VARIANT):=1

# Compile library sources.
GENERIC_CC := $(HOST_CC)
GENERIC_SRCS := $(HOST_LIB_SRCS)
GENERIC_OBJ_DIR := $(BUILDDIR)/host_libs/obj/$(HOST_LIB_NAME)$(HOST_LIB_VARIANT)
GENERIC_FLAGS := -O1 -g -Wall -Wextra -Wno-unused-parameter -Werror $(HOST_SANITIZER_FLAGS) $(HOST_LIB_FLAGS) $(addprefix -I, $(HOST_INCLUDE_DIRS))
GENERIC_CFLAGS := -std=c11 -D_POSIX_C_SOURCE=200809 -Wno-missing-field-initializers
GENERIC_CPPFLAGS := -std=c++20 $(HOST_LIBCXX_CPPFLAGS)
GENERIC_LOG_NAME := $(HOST_LIB_NAME)
include make/generic_compile.mk

# Build static library
$(HOST_LIB_ARCHIVE): HOST_LIB_NAME := $(HOST_LIB_NAME)
$(HOST_LIB_ARCHIVE): $(GENERIC_OBJS)
	@$(call ECHO,$(HOST_LIB_NAME),aring,$@)
	@$(MKDIR)
	$(NOECHO)$(AR) crs $@ $^
	@$(call ECHO_DONE_SILENT,$(HOST_LIB_NAME),aring,$@)

endif
HOST_LIB_ARCHIVES += $(HOST_LIB_ARCHIVE)

# cleanup input variables
HOST_LIB_NAME :=
HOST_LIB_SRCS :=
HOST_LIB_FLAGS :=
HOST_LIB_VARIANT :=
# cleanup internal variables
HOST_LIB_ARCHIVE :=
GENERIC_OBJS :=
