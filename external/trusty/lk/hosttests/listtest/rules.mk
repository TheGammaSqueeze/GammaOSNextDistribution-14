#
# Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

HOST_TEST := list_test

GTEST_DIR := external/googletest/googletest

HOST_SRCS := \
        $(LOCAL_DIR)/list_test.cpp \
        $(GTEST_DIR)/src/gtest-all.cc \
        $(GTEST_DIR)/src/gtest_main.cc \

HOST_INCLUDE_DIRS := \
        $(LOCAL_DIR)/.. \
        $(GTEST_DIR)/include \
        $(GTEST_DIR) \

HOST_LIBS := \
        stdc++ \
        pthread \

include make/host_test.mk
