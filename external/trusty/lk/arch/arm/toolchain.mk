LOCAL_DIR := $(GET_LOCAL_DIR)

ifndef ARCH_arm_TOOLCHAIN_PREFIX
$(error Please run envsetup.sh to set ARCH_arm_TOOLCHAIN_PREFIX)
endif

ARCH_arm_COMPILEFLAGS :=

# Arch
ifeq ($(ARM_CPU),armv8-a)
ARCH_arm_COMPILEFLAGS += -march=$(ARM_CPU)
else
ARCH_arm_COMPILEFLAGS += -mcpu=$(ARM_CPU)
endif

# Floating point support
ifneq ($(ARM_WITHOUT_VFP_NEON),true)
# ARM_WITHOUT_VFP_NEON = false
ifeq (false,$(call TOBOOL,$(ALLOW_FP_USE)))
# This is likely kernel space.
# Don't use neon registers but still support FP ASM.
# The kernel will not save NEON register on interrupt.
ARCH_arm_COMPILEFLAGS += -mfpu=vfpv3 -mfloat-abi=softfp -DWITH_NO_FP=1
else # ALLOW_FP_USE = true
# This is likely userspace.
ifeq ($(ARM_CPU),cortex-a7)
ARCH_arm_COMPILEFLAGS += -mfpu=neon-vfpv4 -mfloat-abi=softfp
endif
ifeq ($(ARM_CPU),cortex-a15)
ARCH_arm_COMPILEFLAGS += -mfpu=neon-vfpv4 -mfloat-abi=softfp
endif
ifeq ($(ARM_CPU),armv8-a)
ARCH_arm_COMPILEFLAGS += -mfpu=crypto-neon-fp-armv8 -mfloat-abi=softfp
endif
endif # ALLOW_FP_USE
else # ARM_WITHOUT_VFP_NEON = true
ARCH_arm_COMPILEFLAGS += -mfloat-abi=soft
endif # ARM_WITHOUT_VFP_NEON

CLANG_ARM_TARGET_SYS ?= linux
CLANG_ARM_TARGET_ABI ?= gnu

ARCH_arm_THUMBCFLAGS :=
ifeq ($(ENABLE_THUMB),true)
ARCH_arm_THUMBCFLAGS := -mthumb -D__thumb__
endif

ARCH_arm_COMPILEFLAGS += -target arm-$(CLANG_ARM_TARGET_SYS)-$(CLANG_ARM_TARGET_ABI)

# Set up custom Rust target to match clang target
ARCH_arm_SUPPORTS_RUST := true
ARCH_arm_RUSTFLAGS := --target=armv7-unknown-trusty
