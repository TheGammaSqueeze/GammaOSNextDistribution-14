#
# Build targets for a Cortex-M-based processor
#

# Cortex-M Environment Checks ##################################################

# If building for the Cortex-M target, ensure that the user has specified a path
# to the Cortex-M toolchain that they wish to use.
ifeq ($(CORTEXM_TOOLS_PREFIX),)
# Try to default to Android's Clang prebuilts if the tools prefix isn't
# specified.
include $(CHRE_PREFIX)/build/clang.mk
endif # CORTEXM_TOOLS_PREFIX

# Cortex-M Tools ###############################################################

ifeq ($(IS_CLANG_TOOLCHAIN),)
TARGET_AR = $(CORTEXM_TOOLS_PREFIX)/bin/arm-none-eabi-ar
TARGET_CC = $(CORTEXM_TOOLS_PREFIX)/bin/arm-none-eabi-g++
TARGET_LD = $(CORTEXM_TOOLS_PREFIX)/bin/arm-none-eabi-ld
else
TARGET_AR = $(CLANG_TOOLCHAIN_PATH)/bin/llvm-ar
TARGET_CC = $(CLANG_TOOLCHAIN_PATH)/bin/clang
TARGET_LD = $(CLANG_TOOLCHAIN_PATH)/bin/ld.lld
endif

# Cortex-M Compiler Flags ######################################################

# Add Cortex-M compiler flags.
TARGET_CFLAGS += $(CORTEXM_CFLAGS)

# Code generation flags.
GCC_CFLAGS += -mthumb
GCC_CFLAGS += -mno-thumb-interwork
GCC_CFLAGS += -ffast-math
GCC_CFLAGS += -fsingle-precision-constant

TARGET_CFLAGS += -ffunction-sections
TARGET_CFLAGS += -fdata-sections
TARGET_CFLAGS += -fshort-enums
TARGET_CFLAGS += -fno-unwind-tables
TARGET_CFLAGS += -mabi=aapcs

ifneq ($(IS_NANOAPP_BUILD),)
TARGET_CFLAGS += -fpic
endif

ifeq ($(IS_ARCHIVE_ONLY_BUILD), true)
COMMON_CXX_CFLAGS += -fno-use-cxa-atexit
endif

# Sadly we must disable double promotion warnings due to logging macros. There
# is a bug for this here: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=53431
TARGET_CFLAGS += -Wno-double-promotion

# Cortex-M Shared Object Linker Flags ##########################################

TARGET_SO_LDFLAGS += -shared
TARGET_SO_LDFLAGS += -z max-page-size=0x8

# Supported Cortex-M Architectures #############################################

CORTEXM_SUPPORTED_ARCHS = m4 m4_hardfp

# Environment Checks ###########################################################

# Ensure that an architecture is chosen.
ifeq ($(filter $(CORTEXM_ARCH), $(CORTEXM_SUPPORTED_ARCHS)),)
$(error "The CORTEXM_ARCH argument must be set to a supported architecture (\
         $(CORTEXM_SUPPORTED_ARCHS))")
endif

# Target Architecture ##########################################################

# Set the Cortex-M architecture.
ifeq ($(CORTEXM_ARCH), m4)
GCC_CFLAGS += -mcpu=cortex-m4
CLANG_CFLAGS += --target=arm-none-eabi
TARGET_CFLAGS += -mfloat-abi=softfp
TARGET_CFLAGS += -mfpu=fpv4-sp-d16
endif

ifeq ($(CORTEXM_ARCH), m4_hardfp)
GCC_CFLAGS += -mcpu=cortex-m4
CLANG_CFLAGS += --target=arm-none-eabi
TARGET_CFLAGS += -mfloat-abi=hard
TARGET_CFLAGS += -mfpu=fpv4-sp-d16
endif


ifeq ($(IS_CLANG_TOOLCHAIN),)
TARGET_CFLAGS += $(GCC_CFLAGS)
else
TARGET_CFLAGS += $(CLANG_CFLAGS)
endif

# Optimization Level ###########################################################

TARGET_CFLAGS += -O$(OPT_LEVEL)

# Variant Specific Sources #####################################################

TARGET_VARIANT_SRCS += $(CORTEXM_SRCS)
