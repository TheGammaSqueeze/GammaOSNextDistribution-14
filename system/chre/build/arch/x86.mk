#
# Build targets for an x86 processor
#

# x86 Environment Checks #######################################################

ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

include $(CHRE_PREFIX)/build/clang.mk

# x86 Tools ####################################################################

TARGET_AR  = $(CLANG_TOOLCHAIN_PATH)/bin/llvm-ar
TARGET_CC  = $(CLANG_TOOLCHAIN_PATH)/bin/clang++
TARGET_LD  = $(CLANG_TOOLCHAIN_PATH)/bin/clang++

# x86 Compiler Flags ###########################################################

# Add x86 compiler flags.
TARGET_CFLAGS += $(X86_CFLAGS)

# x86 is purely used for testing, so always include debugging symbols
TARGET_CFLAGS += -g

# Enable position independence.
TARGET_CFLAGS += -fpic

# Disable double promotion warning for logging
TARGET_CFLAGS += -Wno-double-promotion

# x86 Shared Object Linker Flags ###############################################

TARGET_SO_LDFLAGS += -shared
TARGET_SO_LDFLAGS += -Wl,-gc-sections

# Optimization Level ###########################################################

TARGET_CFLAGS += -O$(OPT_LEVEL)

# Variant Specific Sources #####################################################

TARGET_VARIANT_SRCS += $(X86_SRCS)
