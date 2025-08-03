# Copyright (C) 2014-2015 The Android Open Source Project
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
NANOPB_DIR := external/nanopb-c
LIBCPPBOR_DIR := $(if $(wildcard system/libcppbor),system/libcppbor,external/libcppbor)

MODULE := $(LOCAL_DIR)

MANIFEST := $(LOCAL_DIR)/manifest.json

KEYMASTER_ROOT := $(TRUSTY_TOP)/system/keymaster

# Uncomment the following lines to generate protobuf files and remove
# $(KEYMASTER_DIR)/keymaster_attributes.pb.c from MODULE_SRCS. For detail
# explanation, please see the comments in *.proto file.
#
#PB_GEN_DIR := $(call TOBUILDDIR,proto)
#include trusty/user/base/make/compile_proto.mk
#$(eval $(call compile_proto,$(LOCAL_DIR)/keymaster_attributes.proto,$(PB_GEN_DIR)))
#MODULE_SRCS += $(NANOPB_DEPS) $(NANOPB_GENERATED_C)
#MODULE_SRCDEPS += $(NANOPB_GENERATED_HEADER)
#MODULE_INCLOUDES += $(PB_GEN_DIR)

MODULE_SRCS += \
	$(KEYMASTER_ROOT)/android_keymaster/android_keymaster.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/android_keymaster_messages.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/android_keymaster_utils.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/keymaster_enforcement.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/logger.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/authorization_set.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/operation.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/operation_table.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/serializable.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/keymaster_tags.cpp \
	$(KEYMASTER_ROOT)/android_keymaster/remote_provisioning_utils.cpp \
	$(KEYMASTER_ROOT)/cppcose/cppcose.cpp \
	$(KEYMASTER_ROOT)/key_blob_utils/auth_encrypted_key_blob.cpp \
	$(KEYMASTER_ROOT)/key_blob_utils/ocb.c \
	$(KEYMASTER_ROOT)/key_blob_utils/ocb_utils.cpp \
	$(KEYMASTER_ROOT)/km_openssl/aes_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/aes_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/asymmetric_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/asymmetric_key_factory.cpp \
	$(KEYMASTER_ROOT)/km_openssl/attestation_record.cpp \
	$(KEYMASTER_ROOT)/km_openssl/attestation_utils.cpp \
	$(KEYMASTER_ROOT)/km_openssl/block_cipher_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/certificate_utils.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ckdf.cpp \
	$(KEYMASTER_ROOT)/km_openssl/curve25519_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ec_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ec_key_factory.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ecdsa_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/ecdh_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/hmac.cpp \
	$(KEYMASTER_ROOT)/km_openssl/hmac_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/hmac_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/openssl_err.cpp \
	$(KEYMASTER_ROOT)/km_openssl/openssl_utils.cpp \
	$(KEYMASTER_ROOT)/km_openssl/rsa_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/rsa_key_factory.cpp \
	$(KEYMASTER_ROOT)/km_openssl/rsa_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/software_random_source.cpp \
	$(KEYMASTER_ROOT)/km_openssl/symmetric_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/triple_des_key.cpp \
	$(KEYMASTER_ROOT)/km_openssl/triple_des_operation.cpp \
	$(KEYMASTER_ROOT)/km_openssl/wrapped_key.cpp \
	$(LOCAL_DIR)/openssl_keymaster_enforcement.cpp \
	$(LOCAL_DIR)/trusty_aes_key.cpp \
	$(LOCAL_DIR)/trusty_keymaster.cpp \
	$(LOCAL_DIR)/trusty_keymaster_context.cpp \
	$(LOCAL_DIR)/trusty_keymaster_enforcement.cpp \
	$(LOCAL_DIR)/trusty_remote_provisioning_context.cpp \
	$(LOCAL_DIR)/trusty_secure_deletion_secret_storage.cpp \
	$(LOCAL_DIR)/second_imei_attestation.cpp \
	$(LOCAL_DIR)/secure_storage_manager.cpp \
	$(LOCAL_DIR)/keymaster_attributes.pb.c \
	$(NANOPB_DIR)/pb_common.c \
	$(NANOPB_DIR)/pb_encode.c \
	$(NANOPB_DIR)/pb_decode.c \

MODULE_INCLUDES += \
	$(KEYMASTER_ROOT)/include \
	$(KEYMASTER_ROOT)/contexts \
	$(KEYMASTER_ROOT) \
	$(TRUSTY_TOP)/hardware/libhardware/include \
	$(LOCAL_DIR) \
	$(NANOPB_DIR) \

MODULE_CPPFLAGS := -fno-short-enums

MODULE_COMPILEFLAGS := -U__ANDROID__ -D__TRUSTY__ -std=c++17

# Set to true to fallback to soft_attestation_cert if not provisioned.
# Note that KeyMint1 does not mandate factory provisioning, so the SW
# fallback is a perfectly-legitimate state.  KeyMint2 will disallow
# factory provisioning and SW fallback will become irrelevant.
KEYMASTER_SOFT_ATTESTATION_FALLBACK ?= true
ifeq (true,$(call TOBOOL,$(KEYMASTER_SOFT_ATTESTATION_FALLBACK)))
MODULE_SRCS += \
	$(KEYMASTER_ROOT)/contexts/soft_attestation_cert.cpp \

MODULE_COMPILEFLAGS += -DKEYMASTER_SOFT_ATTESTATION_FALLBACK=1
endif

#
# Defining KEYMASTER_DEBUG will allow configure() to succeed without root of
# trust from bootloader.
#
ifeq (true,$(call TOBOOL,$(KEYMASTER_DEBUG)))
MODULE_COMPILEFLAGS += -DKEYMASTER_DEBUG
endif

# Add support for nanopb tag numbers > 255 and fields larger than 255 bytes or
# 255 array entries.
MODULE_COMPILEFLAGS += -DPB_FIELD_16BIT
# STATIC_ASSERT in pb.h might conflict with STATIC_ASSEET in compiler.h
MODULE_COMPILEFLAGS += -DPB_NO_STATIC_ASSERT

ifdef TRUSTY_KM_WRAPPING_KEY_SIZE
    MODULE_COMPILEFLAGS += -DTRUSTY_KM_WRAPPING_KEY_SIZE=$(TRUSTY_KM_WRAPPING_KEY_SIZE)
endif

ifdef TRUSTY_KM_KAK_SIZE
    MODULE_COMPILEFLAGS += -DTRUSTY_KM_KAK_SIZE=$(TRUSTY_KM_KAK_SIZE)
endif

MODULE_LIBRARY_DEPS += \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \
	trusty/user/base/lib/rng \
	trusty/user/base/lib/hwbcc/client \
	trusty/user/base/lib/hwkey \
	trusty/user/base/lib/hwwsk \
	trusty/user/base/lib/keybox/client \
	trusty/user/base/lib/storage \
	trusty/user/base/lib/system_state \
	trusty/user/base/lib/tipc \
	external/boringssl \
	$(LIBCPPBOR_DIR) \

# If KEYMASTER_WITH_HWWSK_SUPPORT is set Keymaster will be
#  compiled with Hardware Wrapped Storage key support
ifeq (true,$(call TOBOOL,$(KEYMASTER_WITH_HWWSK_SUPPORT)))
MODULE_DEFINES += \
     WITH_HWWSK_SUPPORT=1 \

endif

# If KEYMASTER_WITH_FINGERPRINT_SUPPORT is set Keymaster will be
#  compiled with fingerprint authenticator support.
ifeq (true,$(call TOBOOL,$(KEYMASTER_WITH_FINGERPRINT_SUPPORT)))
MODULE_DEFINES += \
     TEE_FINGERPRINT_AUTH_SUPPORTED=1 \

endif

TRUSTY_KM_RKP_VERSION ?= 3
ifdef TRUSTY_KM_RKP_VERSION
    MODULE_COMPILEFLAGS += -DTRUSTY_KM_RKP_VERSION=$(TRUSTY_KM_RKP_VERSION)
endif

include $(LOCAL_DIR)/ipc/rules.mk

include make/trusted_app.mk

# Include unit tests
ifeq (true,$(call TOBOOL,$(TEST_BUILD)))
include trusty/user/app/keymaster/host_unittest/rules.mk
endif
