include $(CHRE_PREFIX)/build/clean_build_template_args.mk

TARGET_NAME = aosp_cm4_exynos-embos
ifneq ($(filter $(TARGET_NAME)% all, $(MAKECMDGOALS)),)

ifeq ($(RAINBOW_SDK_DIR),)
$(error "The Rainbow SDK directory needs to be exported as the RAINBOW_SDK_DIR \
         environment variable")
endif

EMBOS_V422_INCLUDE_DIR := $(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/embos/Start/Inc/Embos422

CORTEXM_ARCH := m4_hardfp

TARGET_CFLAGS += -I$(EMBOS_V422_INCLUDE_DIR)

# Sized based on the buffer allocated in the host daemon (4096 bytes), minus
# FlatBuffer overhead (max 88 bytes), minus some extra space to make a nice
# round number and allow for addition of new fields to the FlatBuffer
TARGET_CFLAGS += -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=4000

# Used to expose libc headers to nanoapps that aren't supported on the given platform
TARGET_CFLAGS += -I$(CHRE_PREFIX)/platform/shared/include/chre/platform/shared/libc

TARGET_CFLAGS += $(ARM_CFLAGS)
TARGET_CFLAGS += $(EMBOS_CFLAGS)
TARGET_CFLAGS += $(EXYNOS_CFLAGS)
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/firmware/os/platform/exynos/inc
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/firmware/os/platform/exynos/inc/plat
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/firmware/os/platform/exynos/inc/plat/cmsis
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/firmware/os/platform/exynos/inc/plat/csp
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/firmware/os/platform/exynos/inc/plat/mailbox
TARGET_CFLAGS += -I$(RAINBOW_SDK_DIR)/OEM/LSI/exynos9925/embos/Project/erd9925/DeviceSupport

# TODO(b/242765122): The target won't build out of the box until the
# aforementioned bug is resolved since a set of standard library headers
# that CHRE requires are missing. Please contact the CHRE team for a
# workaround.

# IAR interlinking compatibility flags
TARGET_CFLAGS += -D__ARM7EM__
TARGET_CFLAGS += -D__CORE__=__ARM7EM__
TARGET_CFLAGS += -D__FPU_PRESENT=1
TARGET_CFLAGS += -D_LIBCPP_HAS_THREAD_API_EXTERNAL
GCC_SO_LDFLAGS += --no-wchar-size-warning

# The Exynos lib has a macro that includes common headers based on a 'Chip' ID. Eg:
# CSP_HEADER(csp_common) includes csp_common{CHIP}.h.
TARGET_CFLAGS += -DCHIP=9925

# There are quite a few macros gated by 'EMBOS' in the csp library.
TARGET_CFLAGS += -DEMBOS

# CMSIS uses the register keyword liberally, which results in a warning when
# compiled with GCC.
COMMON_CXX_CFLAGS += -Wno-register

# Temporarily need the following define, since we use printfs for logging
# until the logcat redirection is implemented.
# Reference: https://en.cppreference.com/w/cpp/types/integer#Notes
TARGET_CFLAGS += -D__int64_t_defined

# Temporarily disable implicit double promotion warnings until logcat
# redirection is implemented.
TARGET_CFLAGS += -Wno-double-promotion

# GCC is unnecessarily strict with shadow warnings in legal C++ constructor
# syntax.
TARGET_CFLAGS += -Wno-shadow

TARGET_CFLAGS += -DCHRE_FIRST_SUPPORTED_API_VERSION=CHRE_API_VERSION_1_6

TARGET_VARIANT_SRCS += $(EMBOS_SRCS)
TARGET_VARIANT_SRCS += $(EXYNOS_SRCS)
TARGET_VARIANT_SRCS += $(ARM_SRCS)
TARGET_VARIANT_SRCS += $(DSO_SUPPORT_LIB_SRCS)

TARGET_CFLAGS += $(DSO_SUPPORT_LIB_CFLAGS)

TARGET_PLATFORM_ID = 0x476F6F676C002000

include $(CHRE_PREFIX)/build/arch/cortexm.mk
include $(CHRE_PREFIX)/build/build_template.mk
endif
