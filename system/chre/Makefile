#
# CHRE Makefile
#

# Environment Setup ############################################################

# Building CHRE is always done in-tree so the CHRE_PREFIX can be assigned to the
# current directory.
CHRE_PREFIX = .

# Variant Configuration ########################################################

include $(CHRE_VARIANT_MK_INCLUDES)

# Build Configuration ##########################################################

OUTPUT_NAME = libchre

# Compiler Flags ###############################################################

# Symbols required by the runtime for conditional compilation.
COMMON_CFLAGS += -DCHRE_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_DEBUG
COMMON_CFLAGS += -DCHRE_INCLUDE_DEFAULT_STATIC_NANOAPPS

ifneq ($(CHRE_ASSERTIONS_ENABLED), false)
COMMON_CFLAGS += -DCHRE_ASSERTIONS_ENABLED
else
COMMON_CFLAGS += -DCHRE_ASSERTIONS_DISABLED
endif

# Place nanoapps in a namespace.
COMMON_CFLAGS += -DCHRE_NANOAPP_INTERNAL

# Optional audio support.
ifeq ($(CHRE_AUDIO_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_AUDIO_SUPPORT_ENABLED
endif

# Optional BLE support
ifeq ($(CHRE_BLE_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_BLE_SUPPORT_ENABLED
endif

# Optional GNSS support.
ifeq ($(CHRE_GNSS_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_GNSS_SUPPORT_ENABLED
endif

# Optional sensors support.
ifeq ($(CHRE_SENSORS_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_SENSORS_SUPPORT_ENABLED
endif

# Optional Wi-Fi support.
ifeq ($(CHRE_WIFI_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_WIFI_SUPPORT_ENABLED
endif

# Optional WWAN support.
ifeq ($(CHRE_WWAN_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_WWAN_SUPPORT_ENABLED
endif

# Optional tokenized logging support.
ifeq ($(CHRE_TOKENIZED_LOGGING_ENABLED), true)
COMMON_CFLAGS += -DCHRE_TOKENIZED_LOGGING_ENABLED
include $(CHRE_PREFIX)/external/pigweed/pw_tokenizer.mk
endif

# Optional nanoapp tokenized logging support.
ifeq ($(CHRE_NANOAPP_TOKENIZED_LOGGING_SUPPORT_ENABLED), true)
COMMON_CFLAGS += -DCHRE_NANOAPP_TOKENIZED_LOGGING_SUPPORT_ENABLED
include $(CHRE_PREFIX)/external/pigweed/pw_tokenizer.mk
endif

# Optional tokenized tracing support.
ifeq ($(CHRE_TRACING_ENABLED), true)
COMMON_CFLAGS += -DCHRE_TRACING_ENABLED
include $(CHRE_PREFIX)/external/pigweed/pw_trace.mk
endif

# Optional on-device unit tests support
include $(CHRE_PREFIX)/test/test.mk

# Determine the CHRE_HOST_OS to resolve build discrepancies across Darwin and
# Linux.
CHRE_HOST_OS := $(shell uname)

ifeq ($(CHRE_PATCH_VERSION),)
ifeq ($(CHRE_HOST_OS),Darwin)
DATE_CMD=gdate
else
DATE_CMD=date
endif

# Compute the patch version as the number of hours since the start of some
# arbitrary epoch. This will roll over 16 bits after ~7 years, but patch version
# is scoped to the API version, so we can adjust the offset when a new API
# version is released.
EPOCH=$(shell $(DATE_CMD) --date='2017-01-01' +%s)
CHRE_PATCH_VERSION = $(shell echo $$(((`$(DATE_CMD) +%s` - $(EPOCH)) / (60 * 60))))
endif

COMMON_CFLAGS += -DCHRE_PATCH_VERSION=$(CHRE_PATCH_VERSION)

# Makefile Includes ############################################################

# Common includes.
include $(CHRE_PREFIX)/build/defs.mk
include $(CHRE_PREFIX)/build/common.mk

# CHRE Implementation includes.
include $(CHRE_PREFIX)/apps/apps.mk
include $(CHRE_PREFIX)/chre_api/chre_api.mk
include $(CHRE_PREFIX)/core/core.mk
include $(CHRE_PREFIX)/external/external.mk
include $(CHRE_PREFIX)/pal/pal.mk
include $(CHRE_PREFIX)/platform/platform.mk
include $(CHRE_PREFIX)/util/util.mk

# Supported variants includes.
ifneq ($(CHRE_TARGET_EXTENSION),)
include $(CHRE_TARGET_EXTENSION)
endif
include $(CHRE_PREFIX)/build/variant/aosp_cm4_exynos-embos.mk
include $(CHRE_PREFIX)/build/variant/aosp_riscv55e03_tinysys.mk
include $(CHRE_PREFIX)/build/variant/aosp_riscv55e300_tinysys.mk
include $(CHRE_PREFIX)/build/variant/google_arm64_android.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv62_slpi.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv62_slpi-uimg.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv65_adsp-see.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv65_adsp-see-uimg.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv65_slpi-see.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv65_slpi-see-uimg.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv66_adsp-see.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv66_adsp-see-uimg.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv66_slpi-see.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv66_slpi-see-uimg.mk
include $(CHRE_PREFIX)/build/variant/google_hexagonv66_slpi-qsh.mk
include $(CHRE_PREFIX)/build/variant/google_x86_linux.mk
include $(CHRE_PREFIX)/build/variant/google_x86_googletest.mk
