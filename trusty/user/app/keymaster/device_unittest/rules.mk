# Copyright (C) 2017 The Android Open Source Project
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
KEYMASTER_ROOT := system/keymaster
KEYMASTER_DIR := trusty/user/app/keymaster
NANOPB_DIR := external/nanopb-c
MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

# Uncomment the following lines to generate protobuf files and remove
# $(KEYMASTER_DIR)/keymaster_attributes.pb.c from MODULE_SRCS. For detail
# explanation, please see the comments in *.proto file.
#
# PB_GEN_DIR := $(call TOBUILDDIR,proto)
# include trusty/user/base/make/compile_proto.mk
# $(eval $(call compile_proto,$(KEYMASTER_DIR)/keymaster_attributes.proto,$(PB_GEN_DIR)))
# MODULE_SRCS += $(NANOPB_DEPS) $(NANOPB_GENERATED_C)
# MODULE_SRCDEPS += $(NANOPB_GENERATED_HEADER)
# MODULE_INCLOUDES += $(PB_GEN_DIR)

MODULE_SRCS += \
	$(KEYMASTER_DIR)/secure_storage_manager.cpp \
	$(KEYMASTER_DIR)/second_imei_attestation.cpp \
	$(LOCAL_DIR)/main.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/logger.cpp \
	$(KEYMASTER_DIR)/keymaster_attributes.pb.c \
	$(NANOPB_DIR)/pb_common.c \
	$(NANOPB_DIR)/pb_encode.c \
	$(NANOPB_DIR)/pb_decode.c \

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/storage \
	trusty/user/base/lib/unittest \

MODULE_COMPILEFLAGS += -DPB_FIELD_16BIT
MODULE_COMPILEFLAGS += -DPB_NO_STATIC_ASSERT

MODULE_INCLUDES += \
	$(KEYMASTER_ROOT) \
	$(LOCAL_DIR) \
	$(KEYMASTER_DIR) \
	$(KEYMASTER_ROOT)/include \
	hardware/libhardware/include \
	lib/lib/storage/include \
	lib/interface/storage/include \
	$(NANOPB_DIR) \

include make/trusted_app.mk

