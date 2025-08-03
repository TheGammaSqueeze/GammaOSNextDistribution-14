# x86-32 toolchain
ifeq ($(SUBARCH),x86-32)
ifndef ARCH_x86_TOOLCHAIN_INCLUDED
ARCH_x86_TOOLCHAIN_INCLUDED := 1

ifndef ARCH_x86_TOOLCHAIN_PREFIX
$(error Please run envsetup.sh to set ARCH_x86_TOOLCHAIN_PREFIX)
endif

endif
endif

# x86-64 toolchain
ifeq ($(SUBARCH),x86-64)
ifndef ARCH_x86_64_TOOLCHAIN_INCLUDED
ARCH_x86_64_TOOLCHAIN_INCLUDED := 1

ifndef ARCH_x86_64_TOOLCHAIN_PREFIX
$(error Please run envsetup.sh to set ARCH_x86_64_TOOLCHAIN_PREFIX)
endif

CLANG_X86_64_TARGET_SYS ?= linux
CLANG_X86_64_TARGET_ABI ?= gnu

ARCH_x86_COMPILEFLAGS += -target x86_64-$(CLANG_X86_64_TARGET_SYS)-$(CLANG_X86_64_TARGET_ABI)

# Set Rust target to match clang target
ARCH_x86_SUPPORTS_RUST := true
ARCH_x86_RUSTFLAGS := --target=x86_64-unknown-trusty
endif
endif

