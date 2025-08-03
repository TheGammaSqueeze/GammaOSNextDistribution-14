LOCAL_DIR := $(GET_LOCAL_DIR)

ifndef ARCH_arm64_TOOLCHAIN_PREFIX
$(error Please run envsetup.sh to set ARCH_arm64_TOOLCHAIN_PREFIX)
endif

ifeq (false,$(call TOBOOL,$(ALLOW_FP_USE)))
ARCH_arm64_COMPILEFLAGS := -mgeneral-regs-only -DWITH_NO_FP=1
else
ARCH_arm64_COMPILEFLAGS :=
endif

ARCH_arm64_SUPPORTS_BTI := true
ARCH_arm64_SUPPORTS_PAC := true
ARCH_arm64_SUPPORTS_SCS := true
ARCH_arm64_DEFAULT_USER_SHADOW_STACK_SIZE ?= $(ARCH_DEFAULT_SHADOW_STACK_SIZE)
ifeq (true,$(call TOBOOL,$(SCS_ENABLED)))
# architecture-specific flag required for shadow call stack
ARCH_arm64_COMPILEFLAGS += -ffixed-x18
endif

# PLATFORM_arm64_COMPILEFLAGS allows platform to define additional global
# compile flags that it will be using.
ARCH_arm64_COMPILEFLAGS += $(PLATFORM_arm64_COMPILEFLAGS)

CLANG_ARM64_TARGET_SYS ?= linux
CLANG_ARM64_TARGET_ABI ?= gnu

ARCH_arm64_COMPILEFLAGS += -target aarch64-$(CLANG_ARM64_TARGET_SYS)-$(CLANG_ARM64_TARGET_ABI)

# Set Rust target to match clang target
ARCH_arm64_SUPPORTS_RUST := true
ARCH_arm64_RUSTFLAGS := --target=aarch64-unknown-trusty
