#
# Copyright (c) 2023, Google, Inc. All rights reserved
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Ensure we have frame pointers in Rust, for stack back-tracing and tools
MODULE_RUSTFLAGS += -C force-frame-pointers=y

ifeq ($(call TOBOOL,$(MODULE_ADD_IMPLICIT_DEPS)),true)
# Add global library dependencies to the build path
MODULE_LIBRARY_DEPS += $(GLOBAL_USER_LIBRARY_DEPS)

ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),true)
MODULE_LIBRARY_DEPS += trusty/user/base/lib/libstd-rust
else
MODULE_LIBRARY_DEPS += trusty/user/base/lib/libc-trusty
endif
endif

# Remaining flags only apply to the trusty userspace, not the test-runner, which
# is also built with the library system.
ifeq (true,$(call TOBOOL,$(TRUSTY_USERSPACE)))

# Control function inlining
USERSPACE_INLINE_FUNCTIONS ?= true
ifeq ($(call TOBOOL,$(USERSPACE_INLINE_FUNCTIONS)),true)
MODULE_COMPILEFLAGS += -finline
else
MODULE_COMPILEFLAGS += -fno-inline-functions
endif

# If ASLR is disabled, don't make PIEs, it burns space
ifneq ($(ASLR), false)
	# Generate PIE code to allow ASLR to be applied
	MODULE_COMPILEFLAGS += -fPIC
	MODULE_RUSTFLAGS += -C relocation-model=pic
else
	MODULE_RUSTFLAGS += -C relocation-model=static
endif

# LTO
ifneq (true,$(call TOBOOL,$(MODULE_DISABLE_LTO)))
ifeq (true,$(call TOBOOL,$(USER_LTO_ENABLED)))
MODULE_COMPILEFLAGS += \
	-fvisibility=hidden \
	-flto=thin \
	-fsplit-lto-unit \

endif

# CFI
MODULE_CFI_ENABLED := false
# TODO(192512327): Re-enable CFI for Rust modules
ifeq ($(call TOBOOL,$(MODULE_IS_RUST)),false)
ifneq (true,$(call TOBOOL,$(MODULE_DISABLE_CFI)))
ifeq (true,$(call TOBOOL,$(CFI_ENABLED)))
MODULE_CFI_ENABLED := true
endif

ifdef USER_CFI_ENABLED
MODULE_CFI_ENABLED := $(call TOBOOL,$(USER_CFI_ENABLED))
endif
endif # !MODULE_DISABLE_CFI
endif

ifeq (true,$(call TOBOOL,$(MODULE_CFI_ENABLED)))
MODULE_COMPILEFLAGS += \
	-fsanitize-blacklist=trusty/kernel/lib/ubsan/exemptlist \
	-fsanitize=cfi \
	-DCFI_ENABLED
MODULE_LIBRARY_DEPS += trusty/kernel/lib/ubsan

ifeq (true,$(call TOBOOL,$(CFI_DIAGNOSTICS)))
MODULE_COMPILEFLAGS += -fno-sanitize-trap=cfi
endif
endif # MODULE_CFI_ENABLED

endif # !MODULE_DISABLE_LTO

# Branch Target Identification
MODULE_ENABLE_BTI:=false
ifeq (true,$(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_BTI)))
ifeq (false,$(call TOBOOL,$(MODULE_DISABLE_BTI)))
MODULE_ENABLE_BTI:=$(call TOBOOL,$(KERNEL_BTI_ENABLED))
endif
endif

# Pointer Authentication Codes
MODULE_ENABLE_PAC:=false
ifeq (true,$(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_PAC)))
ifeq (false,$(call TOBOOL,$(MODULE_DISABLE_PAC)))
MODULE_ENABLE_PAC:=$(call TOBOOL,$(KERNEL_PAC_ENABLED))
endif
endif

# Decide on the branch protection scheme
ifeq (true,$(call TOBOOL,$(MODULE_ENABLE_BTI)))
ifeq (true,$(call TOBOOL,$(MODULE_ENABLE_PAC)))
MODULE_COMPILEFLAGS += -mbranch-protection=bti+pac-ret \
                       -DBTI_ENABLED \
                       -DPAC_ENABLED
MODULE_RUSTFLAGS += -Z branch-protection=bti,pac-ret
else # !MODULE_ENABLE_PAC
MODULE_COMPILEFLAGS += -mbranch-protection=bti \
                       -DBTI_ENABLED
MODULE_RUSTFLAGS += -Z branch-protection=bti
endif
else # !MODULE_ENABLE_BTI
ifeq (true,$(call TOBOOL,$(MODULE_ENABLE_PAC)))
MODULE_COMPILEFLAGS += -mbranch-protection=pac-ret \
                       -DPAC_ENABLED
MODULE_RUSTFLAGS += -Z branch-protection=pac-ret
endif
endif

# Stack protector
ifneq (true,$(call TOBOOL,$(MODULE_DISABLE_STACK_PROTECTOR)))
ifeq (true,$(call TOBOOL,$(USER_STACK_PROTECTOR)))
MODULE_COMPILEFLAGS += -fstack-protector-strong
endif
else
MODULE_COMPILEFLAGS += -fno-stack-protector
endif

# Shadow call stack
ifeq (true,$(call TOBOOL,$(SCS_ENABLED)))
# set in arch/$(ARCH)/toolchain.mk iff shadow call stack is supported
ifeq (false,$(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_SCS)))
$(error Error: Shadow call stack is not supported for $(ARCH))
endif

ifeq (false,$(call TOBOOL,$(TRUSTY_APP_DISABLE_SCS)))
ifeq (false,$(call TOBOOL,$(MODULE_DISABLE_SCS)))
# architectures that support SCS should set the flag that reserves
# a register for the shadow call stack in their toolchain.mk file
MODULE_COMPILEFLAGS += -fsanitize=shadow-call-stack

# The Rust target aarch64-unknown-trusty enables reserve-x18 by default.
# Doing so through rustc args causes a warning and is unnecessary, so we
# don't pass it explicitly via MODULE_RUSTFLAGS if TRUSTY_USER_ARCH is arm64.
# See https://github.com/rust-lang/rust/issues/96472#issuecomment-1200319324
# for why rustc warns on this and it is not a rustc bug.

endif
else  # TRUSTY_APP_DISABLE_SCS
$(warning $(MODULE) has set TRUSTY_APP_DISABLE_SCS, this flag only works as intended for apps w/o dependencies)
endif
endif # SCS_ENABLED

# Code coverage
ifeq (true,$(call TOBOOL,$(USER_COVERAGE_ENABLED)))
ifeq (false,$(call TOBOOL, $(MODULE_DISABLE_COVERAGE)))
MODULE_LIBRARY_DEPS += trusty/user/base/lib/sancov

# -fno-optimize-sibling-calls/-fno-inline is necessary to get correct caller
# information in the sancov instrumentation.
MODULE_COMPILEFLAGS += \
	-fsanitize-coverage-ignorelist=trusty/user/base/lib/sancov/exemptlist \
	-fsanitize-coverage=trace-pc-guard \
	-fno-inline \
	-fno-optimize-sibling-calls

endif
endif

# Source based code coverage
ifeq (true,$(call TOBOOL,$(UNITTEST_COVERAGE_ENABLED)))
ifeq (false,$(call TOBOOL, $(MODULE_DISABLE_COVERAGE)))
MODULE_COMPILEFLAGS += -DUNITTEST_COVERAGE  \
	-fprofile-instr-generate \
	-fcoverage-mapping \
	-mllvm \
	-enable-value-profiling=false

endif
endif

# Fuzzing build
ifeq (true,$(call TOBOOL,$(FUZZING_BUILD_ENABLED)))
MODULE_COMPILEFLAGS += \
	-DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION \

endif

# HWASan
ifeq (true,$(call TOBOOL,$(USER_HWASAN_ENABLED)))
MODULE_DEFINES += \
	HWASAN_ENABLED=1 \
	HWASAN_SHADOW_SCALE=4 \

MODULE_LIBRARY_DEPS += trusty/user/base/lib/hwasan
MODULE_COMPILEFLAGS += \
	-fsanitize-blacklist=trusty/user/base/lib/hwasan/exemptlist \
	-fsanitize=hwaddress \
	-mllvm -hwasan-with-tls=0 \
	-mllvm -hwasan-globals=0 \
	-mllvm -hwasan-use-short-granules=0 \

endif

MODULE_DEFINES += TRUSTY_USERSPACE=1

endif # TRUSTY_USERSPACE

MODULE_CFI_ENABLED :=
MODULE_DISABLE_BTI :=
MODULE_DISABLE_CFI :=
MODULE_DISABLE_COVERAGE :=
MODULE_DISABLE_LTO :=
MODULE_DISABLE_SCS :=
MODULE_DISABLE_STACK_PROTECTOR :=
