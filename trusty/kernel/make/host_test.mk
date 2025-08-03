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
# HOST_TEST : name of the test binary (required)
# HOST_SRCS : list of source files (required)
# HOST_INCLUDE_DIRS : list of include directories
# HOST_FLAGS : list of flags for the compiler
# HOST_LIBS : list of host-provided libraries to link against
# HOST_DEPS : list of libraries to build and link against. Recursive
#             dependencies are not supported.
# HOST_COVERAGE_ENABLED : true/false enable LLVM Source-based code coverage


# Validate arguments.
ifeq ($(HOST_TEST), )
$(error HOST_TEST must be specified)
endif

ifeq ($(HOST_SRCS), )
$(error HOST_SRCS must be specified)
endif

# Select same builddir when included form user-space or kernel
ifeq ($(strip $(TRUSTY_TOP_LEVEL_BUILDDIR)),)
HOST_TEST_BUILDDIR := $(BUILDDIR)
else
HOST_TEST_BUILDDIR := $(TRUSTY_TOP_LEVEL_BUILDDIR)
endif

# We should use the prebuilt linker rather than the host linker
HOST_LDFLAGS := -B$(CLANG_BINDIR) -fuse-ld=lld

HOST_CC := $(CLANG_BINDIR)/clang
HOST_SANITIZER_FLAGS := -fsanitize=address -fno-omit-frame-pointer
HOST_RUN_ENV := ASAN_OPTIONS=symbolize=1 ASAN_SYMBOLIZER_PATH=$(CLANG_BINDIR)/llvm-symbolizer
HOST_LIBCXX_CPPFLAGS := -stdlib=libc++ -isystem$(CLANG_BINDIR)/../include/c++/v1
HOST_LIBCXX_LDFLAGS := -L$(CLANG_HOST_LIBDIR) -stdlib=libc++ -Wl,-rpath,$(CLANG_HOST_LIBDIR)
# ASAN is not compatible with GDB.
HOST_DEBUGGER :=

HOST_INCLUDE_DIRS += $(GLOBAL_UAPI_INCLUDES) $(GLOBAL_SHARED_INCLUDES) $(GLOBAL_USER_INCLUDES)

# Enable LLVM Source-based Code Coverage
# https://clang.llvm.org/docs/SourceBasedCodeCoverage.html
ifeq (true,$(call TOBOOL,$(HOST_COVERAGE_ENABLED)))
HOST_FLAGS += \
	-fprofile-instr-generate=$(HOST_TEST).profraw \
	-fcoverage-mapping \
	-mllvm -enable-value-profiling=false

HOST_LDFLAGS += \
	-fprofile-instr-generate=$(HOST_TEST).profraw \
	-fcoverage-mapping \
	-mllvm -enable-value-profiling=false
endif

# Compile test library dependencies
HOST_LIB_ARCHIVES :=
$(foreach t,$(HOST_DEPS),\
	$(eval include $(addsuffix /rules.mk,$(t))))

# Compile test sources.
GENERIC_CC := $(HOST_CC)
GENERIC_SRCS := $(HOST_SRCS)
GENERIC_OBJ_DIR := $(HOST_TEST_BUILDDIR)/host_tests/obj/$(HOST_TEST)
GENERIC_FLAGS := -O1 -g -Wall -Wextra -Wno-unused-parameter -Werror $(HOST_SANITIZER_FLAGS) $(HOST_FLAGS) $(addprefix -I, $(HOST_INCLUDE_DIRS))
GENERIC_CFLAGS := -std=c11 -D_POSIX_C_SOURCE=200809 -Wno-missing-field-initializers
GENERIC_CPPFLAGS := -std=c++20 -Wno-c99-designator $(HOST_LIBCXX_CPPFLAGS)
GENERIC_LOG_NAME := $(HOST_TEST)
include make/generic_compile.mk

# Link
HOST_TEST_BIN := $(HOST_TEST_BUILDDIR)/host_tests/$(HOST_TEST)
$(HOST_TEST_BIN): CC := $(HOST_CC)
$(HOST_TEST_BIN): LDFLAGS := -g $(HOST_SANITIZER_FLAGS) $(HOST_LDFLAGS) $(HOST_LIBCXX_LDFLAGS) $(addprefix -l, $(HOST_LIBS))
$(HOST_TEST_BIN): HOST_TEST := $(HOST_TEST)
$(HOST_TEST_BIN): $(GENERIC_OBJS) $(HOST_LIB_ARCHIVES)
	@$(call ECHO,$(HOST_TEST),linking,$@)
	@$(MKDIR)
	$(NOECHO)$(CC) $^ $(LDFLAGS) -o $@
	@$(call ECHO_DONE_SILENT,$(HOST_TEST),linking,$@)

# Build host test by default
all:: $(HOST_TEST_BIN)

# Aliases
host_tests: $(HOST_TEST_BIN)

run_$(HOST_TEST): RUN_ENV := $(HOST_RUN_ENV)
run_$(HOST_TEST): DEBUGGER := $(HOST_DEBUGGER)
run_$(HOST_TEST): $(HOST_TEST_BIN) .PHONY
	@echo running $<
	$(NOECHO)$(RUN_ENV) $(DEBUGGER) $<

run_host_tests: run_$(HOST_TEST) .PHONY

# Cleanup inputs
HOST_TEST :=
HOST_TEST_BUILDDIR :=
HOST_SRCS :=
HOST_INCLUDE_DIRS :=
HOST_FLAGS :=
HOST_LIBS :=
HOST_DEPS :=
HOST_COVERAGE_ENABLED :=
# Cleanup internal
HOST_CC :=
HOST_SANITIZER_FLAGS :=
HOST_RUN_ENV :=
HOST_LIBCXX_CPPFLAGS :=
HOST_LDFLAGS :=
HOST_LIBCXX_LDFLAGS :=
HOST_DEBUGGER :=
HOST_TEST_BIN :=
HOST_OBJ_DIR :=
GENERIC_OBJS :=
HOST_LIB_ARCHIVES :=
