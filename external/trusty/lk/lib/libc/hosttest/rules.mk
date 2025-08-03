#
# Copyright (c) 2022 LK Trusty Authors. All Rights Reserved.
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

HOST_TEST := libc_printf_test

GTEST_DIR := external/googletest/googletest

HOST_FLAGS += -DUTEST_BUILD -DRELEASE_BUILD=0

HOST_SRCS := \
        $(LOCAL_DIR)/../test_common/libc_printf_tests.cpp \
        $(GTEST_DIR)/src/gtest-all.cc \
        $(GTEST_DIR)/src/gtest_main.cc \
        $(LOCAL_DIR)/../include/printf.h \
        $(LOCAL_DIR)/../printf.c \

HOST_INCLUDE_DIRS := \
        $(GTEST_DIR)/include \
        $(GTEST_DIR) \
        $(LOCAL_DIR)/../test_includes \

HOST_LIBS := \
        stdc++ \
        pthread \

include make/host_test.mk
