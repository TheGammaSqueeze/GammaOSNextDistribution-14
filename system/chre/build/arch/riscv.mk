#
# Build targets for a risc-v based architecture
#

# Environment Checks ###########################################################

ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

ifeq ($(RISCV_TOOLCHAIN_PATH),)
$(error "The risc-v toolchain directory needs to be exported as the \
         RISCV_TOOLCHAIN_PATH environment variable")
endif

# Tools ########################################################################

TARGET_AR = $(RISCV_TOOLCHAIN_PATH)/bin/llvm-ar
TARGET_CC = $(RISCV_TOOLCHAIN_PATH)/bin/clang
TARGET_LD = $(RISCV_TOOLCHAIN_PATH)/bin/ld.lld

# Shared Object Linker Flags ###################################################

TARGET_SO_LDFLAGS += --gc-sections
TARGET_SO_LDFLAGS += -shared

# Optimization Level ###########################################################

TARGET_CFLAGS += -O$(OPT_LEVEL)
