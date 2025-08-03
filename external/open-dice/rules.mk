# Copyright (C) 2021 The Android Open Source Project.
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
# SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
# OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

# This file is not used in the Android build process! It's used only by Trusty.

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_SRCS := \
	$(LOCAL_DIR)/src/android.c \
	$(LOCAL_DIR)/src/boringssl_hash_kdf_ops.c \
	$(LOCAL_DIR)/src/boringssl_ed25519_ops.c \
	$(LOCAL_DIR)/src/cbor_cert_op.c \
	$(LOCAL_DIR)/src/cbor_ed25519_cert_op.c \
	$(LOCAL_DIR)/src/cbor_reader.c \
	$(LOCAL_DIR)/src/cbor_writer.c \
	$(LOCAL_DIR)/src/clear_memory.c \
	$(LOCAL_DIR)/src/dice.c \
	$(LOCAL_DIR)/src/utils.c \

MODULE_EXPORT_INCLUDES += \
	$(LOCAL_DIR)/include/ \
	$(LOCAL_DIR)/include/dice/config/boringssl_ed25519 \

MODULE_LIBRARY_DEPS := \
	external/boringssl \

include make/library.mk
