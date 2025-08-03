#
# Copyright (c) 2017, Google, Inc. All rights reserved
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

# args:
# HOST_TOOL_NAME : name of the host binary (required)
# HOST_SRCS : list of source files (required)
# HOST_INCLUDE_DIRS : list of include directories
# HOST_FLAGS : list of flags for the compiler
# HOST_LDFLAGS : list of flags for the compiler
# HOST_LIBS : list of host-provided libraries to link against
# HOST_DEPS : list of libraries to build and link against. Recursive
#             dependencies are not supported.
# HOST_SRCDEPS : extra source dependencies
# HOST_STATIC_LINK : statically link the host tool
# HOST_COVERAGE_ENABLED : true/false enable LLVM Source-based code coverage

# Validate arguments.
ifeq ($(HOST_TOOL_NAME), )
$(error HOST_TOOL_NAME must be specified)
endif

ifeq ($(HOST_SRCS), )
$(error HOST_SRCS must be specified)
endif

HOST_CC := $(CLANG_BINDIR)/clang

ifeq (false, $(call TOBOOL,$(HOST_STATIC_LINK)))
# ASAN is not compatible with GDB or static linking.
HOST_SANITIZER_FLAGS := -fsanitize=address -fno-omit-frame-pointer
else
HOST_FLAGS += -static
HOST_LDFLAGS += -static
HOST_SANITIZER_FLAGS :=
# b/319927400: There is a bug that causes a linker conflict if pthread is
# linked _after_ libc.  Add pthread explicitly to avoid this possibility.
HOST_LDFLAGS += -lpthread
endif

# We should use the prebuilt linker rather than the host linker
HOST_LDFLAGS += -B$(CLANG_BINDIR) -fuse-ld=lld

# When using clang, we need to always use the prebuilt libc++ library
# because we can't be sure what version of libstdc++ the host system
# has, or even if it exists at all.
ifneq ($(filter stdc++ c++,$(HOST_LIBS)),)
# Add the prebuilt libraries directory to the tool's rpath,
# so it can use those libraries, e.g., libc++.so
HOST_LIBCXX_CPPFLAGS := -stdlib=libc++ -isystem$(CLANG_BINDIR)/../include/c++/v1
HOST_LIBCXX_LDFLAGS := -L$(CLANG_HOST_LIBDIR) -stdlib=libc++ -Wl,-rpath,$(CLANG_HOST_LIBDIR)
# Add relative path inside the SDK package to RPATH
HOST_SDK_LIBCXX_DIR := $(subst $(CLANG_BINDIR)/..,clang,$(CLANG_HOST_LIBDIR))
HOST_LIBCXX_LDFLAGS += -Wl,-rpath,'$$ORIGIN/../../../toolchain/$(HOST_SDK_LIBCXX_DIR)'
else
HOST_LIBCXX_CPPFLAGS :=
HOST_LIBCXX_LDFLAGS :=
endif

HOST_INCLUDE_DIRS += $(GLOBAL_UAPI_INCLUDES) $(GLOBAL_SHARED_INCLUDES) $(GLOBAL_USER_INCLUDES)

# Enable LLVM Source-based Code Coverage
# https://clang.llvm.org/docs/SourceBasedCodeCoverage.html
ifeq (true,$(call TOBOOL,$(HOST_COVERAGE_ENABLED)))
HOST_FLAGS += \
	-fprofile-instr-generate=$(HOST_TOOL_NAME).profraw \
	-fcoverage-mapping

HOST_LDFLAGS += \
	-fprofile-instr-generate=$(HOST_TOOL_NAME).profraw \
	-fcoverage-mapping
endif

# Compile tool library dependencies
HOST_LIB_ARCHIVES :=
include $(addsuffix /rules.mk, $(HOST_DEPS))

# Compile tool sources.
GENERIC_CC := $(HOST_CC)
GENERIC_SRCS := $(HOST_SRCS)
GENERIC_OBJ_DIR := $(BUILDDIR)/host_tools/obj/$(HOST_TOOL_NAME)
GENERIC_FLAGS := -O1 -g -Wall -Wextra -Wno-unused-parameter -Werror $(HOST_SANITIZER_FLAGS) $(HOST_FLAGS) $(addprefix -I, $(HOST_INCLUDE_DIRS))
GENERIC_CFLAGS := -std=c11 -D_POSIX_C_SOURCE=200809 -Wno-missing-field-initializers
GENERIC_CPPFLAGS := -std=c++20 $(HOST_LIBCXX_CPPFLAGS)
GENERIC_SRCDEPS := $(HOST_SRCDEPS)
GENERIC_LOG_NAME := $(HOST_TOOL_NAME)
include make/generic_compile.mk

# Link
HOST_TOOL_BIN := $(BUILDDIR)/host_tools/$(HOST_TOOL_NAME)
$(HOST_TOOL_BIN): CC := $(HOST_CC)
$(HOST_TOOL_BIN): LDFLAGS := -g $(HOST_SANITIZER_FLAGS) $(HOST_LDFLAGS) $(HOST_LIBCXX_LDFLAGS) $(addprefix -l, $(HOST_LIBS))
$(HOST_TOOL_BIN): HOST_TOOL_NAME := $(HOST_TOOL_NAME)
$(HOST_TOOL_BIN): $(GENERIC_OBJS) $(HOST_LIB_ARCHIVES)
	@$(call ECHO,$(HOST_TOOL_NAME),linking,$@)
	@$(MKDIR)
	$(NOECHO)$(CC) $^ $(LDFLAGS) -o $@
	@$(call ECHO_DONE_SILENT,$(HOST_TOOL_NAME),linking,$@)

EXTRA_BUILDDEPS += $(HOST_TOOL_BIN)

# Cleanup inputs
HOST_TOOL_NAME:=
HOST_SRCS :=
HOST_INCLUDE_DIRS :=
HOST_FLAGS :=
HOST_LDFLAGS :=
HOST_LIBS :=
HOST_DEPS :=
HOST_SRCDEPS :=
HOST_STATIC_LINK :=
# Cleanup internal
HOST_CC :=
HOST_SANITIZER_FLAGS :=
HOST_SDK_LIBCXX_DIR :=
HOST_TOOL_BIN :=
HOST_OBJ_DIR :=
GENERIC_OBJS :=
HOST_LIB_ARCHIVES :=
