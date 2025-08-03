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

HOST_LIB_NAME := ssl

BORINGSSL_DIR := $(TRUSTY_TOP)/external/boringssl

HOST_ARCH := $(shell uname -m)
ifneq ($(HOST_ARCH),x86_64)
$(error Unsupported host architecture: $(HOST_ARCH), expected x86_64)
endif

include $(BORINGSSL_DIR)/sources.mk
HOST_LIB_SRCS := \
	$(addprefix external/boringssl/,$(crypto_sources)) \
	$(addprefix external/boringssl/,$(crypto_sources_asm)) \

HOST_INCLUDE_DIRS += \
	external/boringssl/src/include \

include make/host_lib.mk

HOST_ARCH :=