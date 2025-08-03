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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

LIBCXXABI_DIR = external/libcxxabi

GLOBAL_INCLUDES += $(LIBCXXABI_DIR)/include

# Internal libcxxabi build requires std::unexpected_handler to be defined, even
# though it is removed as of C++17. Building with LIBCPP_BUILDING_LIBRARY
# includes this required non-spec definition in the build.
MODULE_CPPFLAGS += -D_LIBCPP_BUILDING_LIBRARY

# The way we define LIBCPP_BUILDING_LIBRARY above conflicts with a definition in
# the fallback allocator. The resulting error is safe to ignore.
MODULE_COMPILEFLAGS += -Wno-macro-redefined

MODULE_COMPILEFLAGS += -D_LIBCXXABI_BUILDING_LIBRARY -D_LIBCXXABI_HAS_NO_THREADS

# Required if compiling without exceptions.
MODULE_COMPILEFLAGS += -D_LIBCXXABI_NO_EXCEPTIONS

# Required if compiling without RTTI, but also helps binary size.
MODULE_COMPILEFLAGS += -DLIBCXXABI_SILENT_TERMINATE

MODULE_SRCS := \
	$(LIBCXXABI_DIR)/src/cxa_aux_runtime.cpp \
	$(LIBCXXABI_DIR)/src/cxa_default_handlers.cpp \
	$(LIBCXXABI_DIR)/src/cxa_demangle.cpp \
	$(LIBCXXABI_DIR)/src/cxa_exception_storage.cpp \
	$(LIBCXXABI_DIR)/src/cxa_guard.cpp \
	$(LIBCXXABI_DIR)/src/cxa_handlers.cpp \
	$(LIBCXXABI_DIR)/src/cxa_unexpected.cpp \
	$(LIBCXXABI_DIR)/src/cxa_vector.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_exception.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_stdexcept.cpp \
	$(LIBCXXABI_DIR)/src/stdlib_typeinfo.cpp \
	$(LIBCXXABI_DIR)/src/abort_message.cpp \
	$(LIBCXXABI_DIR)/src/fallback_malloc.cpp \

# Exceptions disabled
MODULE_SRCS += \
        $(LIBCXXABI_DIR)/src/cxa_noexception.cpp \

# Files that do not compile without exceptions
# $(LIBCXXABI_DIR)/src/cxa_exception.cpp \
# $(LIBCXXABI_DIR)/src/cxa_personality.cpp \

# Files that do not compile without RTTI
# $(LIBCXXABI_DIR)/src/private_typeinfo.cpp \

MODULE_DEPS := \
	trusty/kernel/lib/libcxx-trusty

include make/module.mk
