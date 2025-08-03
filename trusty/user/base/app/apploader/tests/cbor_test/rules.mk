# Copyright (C) 2022 The Android Open Source Project
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

LOCAL_DIR := $(GET_LOCAL_DIR)

HOST_TEST := cbor_test

GTEST_DIR := external/googletest/googletest
PACKAGE_DIR := trusty/user/base/lib/apploader_package
OPEN_DICE_DIR := external/open-dice
LIBCPPBOR_DIR := $(if $(wildcard system/libcppbor),system/libcppbor,external/libcppbor)

# libcppbor checks if __TRUSTY__ is defined to determine whether it's linked
# into Android or Trusty; the library uses some Android-specific logging and
# other APIs that host tools don't provide, so we define __TRUSTY__ here to
# disable all the Android-specific code in libcppbor.
HOST_FLAGS := -D__TRUSTY__

HOST_SRCS := \
        $(LOCAL_DIR)/cbor_test.cpp \
        $(GTEST_DIR)/src/gtest-all.cc \
        $(GTEST_DIR)/src/gtest_main.cc \
        $(OPEN_DICE_DIR)/src/cbor_reader.c \
        $(OPEN_DICE_DIR)/src/cbor_writer.c \
        $(LIBCPPBOR_DIR)/src/cppbor.cpp \
        $(LIBCPPBOR_DIR)/src/cppbor_parse.cpp \

HOST_INCLUDE_DIRS := \
        $(LOCAL_DIR)/../.. \
	$(PACKAGE_DIR)/include \
        $(OPEN_DICE_DIR)/include \
        $(LIBCPPBOR_DIR)/include/cppbor \
        $(GTEST_DIR)/include \
        $(GTEST_DIR) \

# Build and statically link in boringssl so we don't have to worry about what
# version the host environment provides. OpenSSL 3.0 deprecates several of the
# low-level APIs used for trusty app signing and encryption.
HOST_DEPS := \
	trusty/user/base/host/boringssl

HOST_LIBS := \
        stdc++ \
        pthread \

include make/host_test.mk
