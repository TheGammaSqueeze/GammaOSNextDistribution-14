#
# Makefile for the MbedTLS module
#

# Environment Checks
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif


MBEDTLS_EXT_DIR = $(ANDROID_BUILD_TOP)/system/chre/platform/shared/mbedtls

MBEDTLS_DIR = $(ANDROID_BUILD_TOP)/external/mbedtls/
MBEDTLS_CONFIG_FILE = $(MBEDTLS_EXT_DIR)/mbedtls_config.h

MBEDTLS_CFLAGS += -I$(MBEDTLS_DIR)/include
MBEDTLS_CFLAGS += -DMBEDTLS_CONFIG_FILE=\"$(MBEDTLS_CONFIG_FILE)\"

MBEDTLS_SRCS += $(MBEDTLS_EXT_DIR)/mbedtls_memory.cc

MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/asn1write.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/oid.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/ecp_curves.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/ecp.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/ecdsa.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/constant_time.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/ctr_drbg.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/bignum_core.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/bignum.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/asn1parse.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/md.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/sha256.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/platform_util.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/pkparse.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/pk_wrap.c
MBEDTLS_SRCS += $(MBEDTLS_DIR)/library/pk.c
