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

LIBCXX_DIR = external/libcxx

GLOBAL_INCLUDES += $(LIBCXX_DIR)/include
GLOBAL_INCLUDES += $(LOCAL_DIR)/include

# The header files change if they're being used to build the library.
# For example, adding "public" methods that are only used internally.
MODULE_CPPFLAGS += -D_LIBCPP_BUILDING_LIBRARY

# libcxx defines fallback functions unless it knows they'll be found in libcxxabi.
MODULE_CPPFLAGS += -DLIBCXX_BUILDING_LIBCXXABI

# The following should be CXXFLAGS, compile.mk uses CPPFLAGS for C++ rather than
# preprocessor flags and implicit variables are never used so it should not be
# an issue.
GLOBAL_CPPFLAGS += \
	-D_LIBCPP_BUILD_STATIC \
	-D_LIBCPP_HAS_MUSL_LIBC \
	-D_LIBCPP_HAS_C11_FEATURES \

# This enables a libcxx build flag which disables visibility attributes so that
# the global -fvisibility=hidden flag that we set in engine.mk will apply to
# this module's sources. -fvisibility=hidden is required for
# -fvirtual-function-elimination which we use to remove dead floating point code
# in libcxx.
GLOBAL_CPPFLAGS += -D_LIBCPP_DISABLE_VISIBILITY_ANNOTATIONS

# This libcxx module implements an external threading API using LK's
# thread.h/mutex.h.
GLOBAL_CPPFLAGS += -D_LIBCPP_HAS_THREAD_API_EXTERNAL

MODULE_SRCS := \
	$(LIBCXX_DIR)/src/algorithm.cpp \
	$(LIBCXX_DIR)/src/exception.cpp \
	$(LIBCXX_DIR)/src/ios.cpp \
	$(LIBCXX_DIR)/src/iostream.cpp \
	$(LIBCXX_DIR)/src/locale.cpp \
	$(LIBCXX_DIR)/src/memory.cpp \
	$(LIBCXX_DIR)/src/mutex.cpp \
	$(LIBCXX_DIR)/src/new.cpp \
	$(LIBCXX_DIR)/src/string.cpp \
	$(LIBCXX_DIR)/src/vector.cpp \


MODULE_DEPS += \
	trusty/kernel/lib/libcxxabi-trusty \

include make/module.mk
