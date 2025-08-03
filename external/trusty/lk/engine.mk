LOCAL_MAKEFILE:=$(lastword $(MAKEFILE_LIST))

BUILDROOT ?= .

ifeq ($(MAKECMDGOALS),spotless)
spotless:
	rm -rf -- "$(BUILDROOT)"/build-*
else

ifndef LKROOT
$(error please define LKROOT to the root of the lk build system)
endif

-include local.mk
include make/macros.mk

# If one of our goals (from the commandline) happens to have a
# matching project/goal.mk, then we should re-invoke make with
# that project name specified...

project-name := $(firstword $(MAKECMDGOALS))

ifneq ($(project-name),)
ifneq ($(strip $(foreach d,$(LKINC),$(wildcard $(d)/project/$(project-name).mk))),)
do-nothing := 1
$(MAKECMDGOALS) _all: make-make
	@:
make-make:
	@PROJECT=$(project-name) $(MAKE) -rR -f $(LOCAL_MAKEFILE) $(filter-out $(project-name), $(MAKECMDGOALS))

.PHONY: make-make
endif
endif

# some additional rules to print some help
include make/help.mk

ifeq ($(do-nothing),)

ifeq ($(PROJECT),)

ifneq ($(DEFAULT_PROJECT),)
PROJECT := $(DEFAULT_PROJECT)
else
$(error No project specified. Use 'make list' for a list of projects or 'make help' for additional help)
endif
endif

TEST_BUILD ?=

DEBUG ?= 2

# LOG_LEVEL_KERNEL controls LK_LOGLEVEL
# when LOG_LEVEL_KERNEL = 1, dprintf INFO level is enabled
# when LOG_LEVEL_KERNEL = 2, dprintf SPEW level is enabled
LOG_LEVEL_KERNEL ?= $(DEBUG)

# LOG_LEVEL_USER controls TLOG_LVL_DEFAULT
# when LOG_LEVEL_USER = 2 TLOG_LVL_DEFAULT = 4 (info)
# when LOG_LEVEL_USER = 3 TLOG_LVL_DEFAULT = 5 (debug)
LOG_LEVEL_USER ?= $(DEBUG)

BUILDDIR := $(BUILDROOT)/build-$(PROJECT)
OUTBIN := $(BUILDDIR)/lk.bin
OUTELF := $(BUILDDIR)/lk.elf
CONFIGHEADER := $(BUILDDIR)/config.h
TOOLCHAIN_CONFIG := $(BUILDDIR)/toolchain.config
TRUSTY_HOST_LIBRARY_BUILDDIR := $(BUILDDIR)/host_lib
TRUSTY_KERNEL_LIBRARY_BUILDDIR := $(BUILDDIR)/kernellib

# Eliminate /usr/local/include and /usr/include to build kernel hermetically
GLOBAL_KERNEL_COMPILEFLAGS += --sysroot=fake_sysroot
GLOBAL_KERNEL_INCLUDES := $(addsuffix /include,$(LKINC))
# For backwards compatibility.
GLOBAL_KERNEL_INCLUDES += $(addsuffix /include/uapi/uapi,$(LKINC)) $(addsuffix /include/shared/lk,$(LKINC))
GLOBAL_UAPI_INCLUDES := $(addsuffix /include/uapi,$(LKINC))
GLOBAL_SHARED_INCLUDES := $(addsuffix /include/shared,$(LKINC))
GLOBAL_USER_INCLUDES := $(addsuffix /include/user,$(LKINC))
GLOBAL_INCLUDES := $(BUILDDIR) $(GLOBAL_UAPI_INCLUDES) $(GLOBAL_SHARED_INCLUDES) $(GLOBAL_KERNEL_INCLUDES)
GLOBAL_OPTFLAGS ?= $(ARCH_OPTFLAGS)
GLOBAL_SHARED_COMPILEFLAGS := -glldb -fdebug-macro -include $(CONFIGHEADER)
GLOBAL_SHARED_COMPILEFLAGS += -Werror -Wall -Wsign-compare -Wno-multichar -Wno-unused-function -Wno-unused-label
GLOBAL_SHARED_COMPILEFLAGS += -fno-short-enums -fno-common
GLOBAL_SHARED_COMPILEFLAGS += -fno-omit-frame-pointer
GLOBAL_SHARED_CFLAGS := --std=c17 -Wstrict-prototypes -Wwrite-strings
GLOBAL_SHARED_CPPFLAGS := --std=c++20 -fno-exceptions -fno-rtti -fno-threadsafe-statics
# c99 array designators are not part of C++, but they are convenient and help avoid errors.
GLOBAL_SHARED_CPPFLAGS += -Wno-c99-designator
#GLOBAL_CPPFLAGS += -Weffc++
GLOBAL_SHARED_ASMFLAGS := -DASSEMBLY
GLOBAL_LDFLAGS :=
GLOBAL_SHARED_LDFLAGS :=
GLOBAL_KERNEL_LDFLAGS :=
GLOBAL_KERNEL_RUSTFLAGS := -L dependency=$(TRUSTY_HOST_LIBRARY_BUILDDIR) -L dependency=$(TRUSTY_KERNEL_LIBRARY_BUILDDIR)

# This function is referenced by the linker-generated exidx tables, but seems to
# be being dropped before it is needed. Force it to be included in the link.
GLOBAL_SHARED_LDFLAGS += --undefined=__aeabi_unwind_cpp_pr0

GLOBAL_KERNEL_LDFLAGS += $(addprefix -L,$(LKINC))

GLOBAL_LTO_COMPILEFLAGS += \
	-fvisibility=hidden \
	-flto=full \
	-fvirtual-function-elimination \
	-fvisibility-inlines-hidden \

# Rust flags for proc macros
GLOBAL_HOST_RUSTFLAGS := -L "$(RUST_HOST_LIBDIR)" -L dependency=$(TRUSTY_HOST_LIBRARY_BUILDDIR)

# Rust flags, based on the flags used in AOSP
GLOBAL_SHARED_RUSTFLAGS := -C codegen-units=1 -C debuginfo=2 -C opt-level=3 -C relocation-model=pic
GLOBAL_SHARED_RUSTFLAGS += -C overflow-checks=on
# Use v0 mangling to distinguish from C++ symbols
GLOBAL_SHARED_RUSTFLAGS += -C symbol-mangling-version=v0
GLOBAL_SHARED_RUSTFLAGS += -C panic=abort -Z link-native-libraries=no
GLOBAL_SHARED_RUSTFLAGS += -Z panic_abort_tests
GLOBAL_SHARED_RUSTFLAGS += --deny warnings
# Enable LTO for all Rust modules.
GLOBAL_SHARED_RUSTFLAGS += -C lto=thin

# Architecture specific compile flags
ARCH_COMPILEFLAGS :=
ARCH_CFLAGS :=
ARCH_CPPFLAGS :=
ARCH_ASMFLAGS :=

# top level rule
all:: $(OUTBIN) $(OUTELF).sym $(OUTELF).sym.sorted $(OUTELF).size $(OUTELF).dump $(OUTELF).lst $(BUILDDIR)/srcfiles.txt $(BUILDDIR)/include_paths.txt

# master module object list
ALLOBJS_MODULE :=

# master object list (for dep generation)
ALLOBJS :=

# master source file list
ALLSRCS :=

# a linker script needs to be declared in one of the project/target/platform files
LINKER_SCRIPT :=

# anything you add here will be deleted in make clean
GENERATED := $(CONFIGHEADER)

# anything added to GLOBAL_DEFINES will be put into $(BUILDDIR)/config.h
GLOBAL_DEFINES := LK=1 __TRUSTY__=1

# Anything added to GLOBAL_SRCDEPS will become a dependency of every source file in the system.
# Useful for header files that may be included by one or more source files.
GLOBAL_SRCDEPS := $(CONFIGHEADER)

# these need to be filled out by the project/target/platform rules.mk files
TARGET :=
PLATFORM :=
ARCH :=
ALLMODULES :=
ALLMODULE_CRATE_STEMS :=

# add any external module dependencies
MODULES := $(EXTERNAL_MODULES)

# any .mk specified here will be included before build.mk
EXTRA_BUILDRULES :=

# any rules you put here will also be built by the system before considered being complete
EXTRA_BUILDDEPS :=

# any rules you put here will be depended on in clean builds
EXTRA_CLEANDEPS :=

# any objects you put here get linked with the final image
EXTRA_OBJS :=

# any extra linker scripts to be put on the command line
EXTRA_LINKER_SCRIPTS :=

# if someone defines this, the build id will be pulled into lib/version
BUILDID ?=

# comment out or override if you want to see the full output of each command
NOECHO ?= @

GLOBAL_SHARED_COMPILEFLAGS += -Wimplicit-fallthrough
# VLAs can have subtle security bugs and assist exploits, so ban them.
GLOBAL_SHARED_COMPILEFLAGS += -Wvla

# set the libc implementation to musl or lk
LK_LIBC_IMPLEMENTATION ?= musl

# try to include the project file
-include project/$(PROJECT).mk
ifndef TARGET
$(error couldn't find project or project doesn't define target)
endif
include target/$(TARGET)/rules.mk
ifndef PLATFORM
$(error couldn't find target or target doesn't define platform)
endif
include platform/$(PLATFORM)/rules.mk

# use linker garbage collection, if requested
ifeq ($(WITH_LINKER_GC),1)
GLOBAL_SHARED_COMPILEFLAGS += -ffunction-sections -fdata-sections
GLOBAL_SHARED_LDFLAGS += --gc-sections
endif

# Control function inlining
KERNEL_INLINE_FUNCTIONS ?= true
ifeq ($(call TOBOOL,$(KERNEL_INLINE_FUNCTIONS)),true)
GLOBAL_KERNEL_COMPILEFLAGS += -finline
else
GLOBAL_KERNEL_COMPILEFLAGS += -fno-inline-functions
endif

# We need all .lk_init entries to be included, even though they are not
# referenced by symbol, so the linker needs to include all objects from each
# module archive.
GLOBAL_KERNEL_LDFLAGS += --whole-archive

# We are not Linux, and some libraries check this macro
# and incorrectly target the wrong OS
# TODO(b/224064243): remove this when we have a proper triple
GLOBAL_SHARED_COMPILEFLAGS += -U__linux__

ifneq ($(GLOBAL_COMPILEFLAGS),)
$(error Setting GLOBAL_COMPILEFLAGS directly from project or platform makefiles is no longer supported. Please use either GLOBAL_SHARED_COMPILEFLAGS or GLOBAL_KERNEL_COMPILEFLAGS.)
endif
ifneq ($(GLOBAL_CFLAGS),)
$(error Setting GLOBAL_CFLAGS directly from project or platform makefiles is no longer supported. Please use either GLOBAL_SHARED_CFLAGS or GLOBAL_KERNEL_CFLAGS.)
endif
ifneq ($(GLOBAL_CPPFLAGS),)
$(error Setting GLOBAL_CPPFLAGS directly from project or platform makefiles is no longer supported. Please use either GLOBAL_SHARED_CPPFLAGS or GLOBAL_KERNEL_CPPFLAGS.)
endif
ifneq ($(GLOBAL_ASMFLAGS),)
$(error Setting GLOBAL_ASMFLAGS directly from project or platform makefiles is no longer supported. Please use either GLOBAL_SHARED_ASMFLAGS or GLOBAL_KERNEL_ASMFLAGS.)
endif
ifneq ($(GLOBAL_LDFLAGS),)
$(error Setting GLOBAL_LDFLAGS directly from project or platform makefiles is no longer supported. Please use either GLOBAL_SHARED_LDFLAGS or GLOBAL_KERNEL_LDFLAGS.)
endif

# Global flags should be set by now, we're moving on to building modules
GLOBAL_COMPILEFLAGS := $(GLOBAL_SHARED_COMPILEFLAGS) $(GLOBAL_KERNEL_COMPILEFLAGS)
GLOBAL_CFLAGS := $(GLOBAL_SHARED_CFLAGS) $(GLOBAL_KERNEL_CFLAGS)
GLOBAL_CPPFLAGS := $(GLOBAL_SHARED_CPPFLAGS) $(GLOBAL_KERNEL_CPPFLAGS)
GLOBAL_ASMFLAGS := $(GLOBAL_SHARED_ASMFLAGS) $(GLOBAL_KERNEL_ASMFLAGS)
GLOBAL_LDFLAGS := $(GLOBAL_SHARED_LDFLAGS) $(GLOBAL_KERNEL_LDFLAGS)

$(call INFO_LOG,Project entry)
$(info PROJECT = $(PROJECT))
$(info PLATFORM = $(PLATFORM))
$(info TARGET = $(TARGET))

# Derive the standard arch name.
$(eval $(call standard_name_for_arch,STANDARD_ARCH_NAME,$(ARCH),$(SUBARCH)))

# Set arch-specific flags for shadow call stack?
SCS_ENABLED = $(KERNEL_SCS_ENABLED)

include arch/$(ARCH)/rules.mk
include top/rules.mk

$(call INFO_LOG,Include recurse.mk)
# recursively include any modules in the MODULE variable, leaving a trail of included
# modules in the ALLMODULES list
include make/recurse.mk

# add some automatic configuration defines
GLOBAL_DEFINES += \
	PROJECT_$(PROJECT)=1 \
	PROJECT=\"$(PROJECT)\" \
	TARGET_$(TARGET)=1 \
	TARGET=\"$(TARGET)\" \
	PLATFORM_$(PLATFORM)=1 \
	PLATFORM=\"$(PLATFORM)\" \
	ARCH_$(ARCH)=1 \
	ARCH=\"$(ARCH)\" \
	$(addsuffix =1,$(addprefix WITH_,$(ALLMODULES)))

GLOBAL_DEFINES += \
	LK_DEBUGLEVEL=$(DEBUG) \
	LK_LOGLEVEL=$(LOG_LEVEL_KERNEL) \
	TLOG_LVL_DEFAULT=$$(($(LOG_LEVEL_USER)+2)) \

GLOBAL_USER_INCLUDES += $(addsuffix /arch/$(ARCH)/include,$(LKINC))

# test build?
ifneq ($(TEST_BUILD),)
GLOBAL_DEFINES += \
	TEST_BUILD=1
endif

# By default we can accept 64 connections on a port. Currently we only need more
# if coverage is enabled (this is only true on fuzzing)
IPC_MAX_HANDLES ?= 64
GLOBAL_DEFINES += \
	IPC_MAX_HANDLES=$(IPC_MAX_HANDLES)

#check if we are doing a release build, if not sure assume release
RELEASE_BUILD ?= true
ifeq ($(RELEASE_BUILD),false)
GLOBAL_DEFINES += \
	RELEASE_BUILD=0
else
GLOBAL_DEFINES += \
	RELEASE_BUILD=1
endif

# ASLR
ifneq ($(ASLR),false)
GLOBAL_DEFINES += \
	ASLR=1
endif

# build with shadow call stacks used in user tasks
ifeq (true,$(call TOBOOL,$(USER_SCS_ENABLED)))
GLOBAL_DEFINES += \
	USER_SCS_ENABLED=1
endif

# build with shadow call stacks used in the kernel
ifeq (true,$(call TOBOOL,$(KERNEL_SCS_ENABLED)))
GLOBAL_DEFINES += \
	KERNEL_SCS_ENABLED=1
endif

# build the kernel with SCS support for user tasks, unless overridden
USER_SCS_SUPPORTED ?= true

ifeq (true,$(call TOBOOL,$(USER_SCS_SUPPORTED)))
# guards allocation and deallocation of the SCS guard region in the kernel
GLOBAL_DEFINES += \
	USER_SCS_SUPPORTED=1
endif


ifeq (true,$(call TOBOOL,$(PIE_KERNEL)))
# Build a PIE kernel binary
GLOBAL_COMPILEFLAGS += -fPIE -fvisibility=hidden
GLOBAL_LDFLAGS += -pie --no-dynamic-linker -z text -Bsymbolic
# Use the very compact SHT_RELR encoding for dynamic relative relocations.
GLOBAL_LDFLAGS += --pack-dyn-relocs=relr
# lld can emit either the DT_RELR or DT_ANDROID_RELR tags.
# Neither objcopy nor objdump recognize the former tags
# and complain very loudly when seeing them, while silently
# ignoring the DT_ANDROID_RELR tags because they're above DT_LOOS.
# Passing --use-android-relr-tags tells lld to use DT_ANDROID_RELR.
GLOBAL_LDFLAGS += --use-android-relr-tags
endif

# KERNEL_BASE_ASLR controls run-time randomization for the
# base virtual address of the kernel image, i.e., the dynamic
# value of KERNEL_BASE. This is currently disabled by default
# and should be enabled manually per project because it has
# several requirements:
# * The platform must provide a RNG by either linking in libsm
#   or implementing the appropriate APIs.
# * An ARM platform must use the new dynamic GIC initialization
#   function arm_gic_init_map() to allocate dynamic addresses for the GIC
#   registers instead of using fixed addresses.
# * Platforms should not use any hard-coded virtual addresses.
ifeq ($(call TOBOOL,$(KERNEL_BASE_ASLR)), true)
GLOBAL_DEFINES += KERNEL_BASE_ASLR=1
endif

# allow additional defines from outside the build system
ifneq ($(EXTERNAL_DEFINES),)
GLOBAL_DEFINES += $(EXTERNAL_DEFINES)
$(info EXTERNAL_DEFINES = $(EXTERNAL_DEFINES))
endif


# prefix all of the paths in GLOBAL_INCLUDES with -I
GLOBAL_INCLUDES := $(addprefix -I,$(GLOBAL_INCLUDES))

# test for some old variables
ifneq ($(INCLUDES),)
$(error INCLUDES variable set, please move to GLOBAL_INCLUDES: $(INCLUDES))
endif
ifneq ($(DEFINES),)
$(error DEFINES variable set, please move to GLOBAL_DEFINES: $(DEFINES))
endif

# default to no ccache
CCACHE ?=
ifeq ($(CLANG_BINDIR),)
$(error clang directory not specified, please set CLANG_BINDIR)
endif
CC := $(CCACHE) $(CLANG_BINDIR)/clang
AR := $(CLANG_BINDIR)/llvm-ar
LD := $(CLANG_BINDIR)/ld.lld
OBJDUMP := $(CLANG_BINDIR)/llvm-objdump
OBJCOPY := $(CLANG_BINDIR)/llvm-objcopy
CPPFILT := $(CLANG_BINDIR)/llvm-cxxfilt
SIZE := $(CLANG_BINDIR)/llvm-size
NM := $(CLANG_BINDIR)/llvm-nm
STRIP := $(CLANG_BINDIR)/llvm-strip
RUSTC := $(RUST_BINDIR)/rustc

# Save the toolchain paths in order to rebuild the world if they change. This is
# needed to force a rebuild when changing compiler versions.
TOOLCHAIN_DEFINES := CLANG_BINDIR=\"$(subst $(SPACE),_,$(CLANG_BINDIR))\"
TOOLCHAIN_DEFINES += CLANG_TOOLS_BINDIR=\"$(subst $(SPACE),_,$(CLANG_TOOLS_BINDIR))\"
TOOLCHAIN_DEFINES += RUST_BINDIR=\"$(subst $(SPACE),_,$(RUST_BINDIR))\"
$(TOOLCHAIN_CONFIG): configheader
	@$(call INFO_DONE,toolchain,generating config file,$@)
	@$(call MAKECONFIGHEADER,$@,TOOLCHAIN_DEFINES)

GENERATED += $(TOOLCHAIN_CONFIG)

GLOBAL_HOST_RUSTFLAGS += -C linker="$(CLANG_BINDIR)/clang++" -C link-args="-B $(CLANG_BINDIR) -fuse-ld=lld"
GLOBAL_SHARED_RUSTFLAGS += -C linker="$(LD)"

# TODO: we could find the runtime like this.
# LIBGCC := $(shell $(CC) $(GLOBAL_COMPILEFLAGS) $(ARCH_COMPILEFLAGS) $(THUMBCFLAGS) --rtlib=compiler-rt -print-libgcc-file-name)
# However the compiler currently does not contain non-x86 prebuilts for the
# linux-gnu ABI. We could either get those prebuilts added to the toolchain or
# switch to the android ABI.
# Note there are two copies of compiler-rt in the toolchain - framework and NDK.
# We're using the NDK version because the path is more stable and the difference
# should not matter for this library. (The main difference is which version of
# libcxx they link against, and the builtins do not use C++.)
LIBGCC := $(CLANG_BINDIR)/../runtimes_ndk_cxx/libclang_rt.builtins-$(STANDARD_ARCH_NAME)-android.a

# try to have the compiler output colorized error messages if available
export GCC_COLORS ?= 1

# link all rust rlibs into a single top-level .a
include make/rust-toplevel.mk

# the logic to compile and link stuff is in here
include make/build.mk

DEPS := $(ALLOBJS:%o=%d)

# put all of the global build flags in config.h to force a rebuild if any change
GLOBAL_DEFINES += GLOBAL_INCLUDES=\"$(subst $(SPACE),_,$(GLOBAL_INCLUDES))\"
GLOBAL_DEFINES += GLOBAL_COMPILEFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_COMPILEFLAGS))\"
GLOBAL_DEFINES += GLOBAL_OPTFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_OPTFLAGS))\"
GLOBAL_DEFINES += GLOBAL_CFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_CFLAGS))\"
GLOBAL_DEFINES += GLOBAL_CPPFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_CPPFLAGS))\"
GLOBAL_DEFINES += GLOBAL_ASMFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_ASMFLAGS))\"
GLOBAL_DEFINES += GLOBAL_LDFLAGS=\"$(subst $(SPACE),_,$(GLOBAL_LDFLAGS))\"
GLOBAL_DEFINES += ARCH_COMPILEFLAGS=\"$(subst $(SPACE),_,$(ARCH_COMPILEFLAGS))\"
GLOBAL_DEFINES += ARCH_CFLAGS=\"$(subst $(SPACE),_,$(ARCH_CFLAGS))\"
GLOBAL_DEFINES += ARCH_CPPFLAGS=\"$(subst $(SPACE),_,$(ARCH_CPPFLAGS))\"
GLOBAL_DEFINES += ARCH_ASMFLAGS=\"$(subst $(SPACE),_,$(ARCH_ASMFLAGS))\"

ifneq ($(OBJS),)
$(warning OBJS=$(OBJS))
$(error OBJS is not empty, please convert to new module format)
endif
ifneq ($(OPTFLAGS),)
$(warning OPTFLAGS=$(OPTFLAGS))
$(error OPTFLAGS is not empty, please use GLOBAL_OPTFLAGS or MODULE_OPTFLAGS)
endif
ifneq ($(CFLAGS),)
$(warning CFLAGS=$(CFLAGS))
$(error CFLAGS is not empty, please use GLOBAL_CFLAGS or MODULE_CFLAGS)
endif
ifneq ($(CPPFLAGS),)
$(warning CPPFLAGS=$(CPPFLAGS))
$(error CPPFLAGS is not empty, please use GLOBAL_CPPFLAGS or MODULE_CPPFLAGS)
endif

$(info LIBGCC = $(LIBGCC))
$(info GLOBAL_COMPILEFLAGS = $(GLOBAL_COMPILEFLAGS))
$(info GLOBAL_OPTFLAGS = $(GLOBAL_OPTFLAGS))

# make all object files depend on any targets in GLOBAL_SRCDEPS
$(ALLOBJS): $(GLOBAL_SRCDEPS)

# any extra top level build dependencies that someone declared.
# build.mk may add to EXTRA_BUILDDEPS, this must be evalauted after build.mk.
all:: $(EXTRA_BUILDDEPS)

clean: $(EXTRA_CLEANDEPS)
	rm -f $(ALLOBJS) $(DEPS) $(GENERATED) $(OUTBIN) $(OUTELF) $(OUTELF).sym $(OUTELF).sym.sorted $(OUTELF).size $(OUTELF).hex $(OUTELF).dump $(OUTELF).lst

install: all
	scp $(OUTBIN) 192.168.0.4:/tftproot

# generate a config.h file with all of the GLOBAL_DEFINES laid out in #define format
configheader:

$(CONFIGHEADER): configheader
	@$(call INFO_DONE,global,generating config file,$@)
	@$(call MAKECONFIGHEADER,$@,GLOBAL_DEFINES)

# Empty rule for the .d files. The above rules will build .d files as a side
# effect. Only works on gcc 3.x and above, however.
%.d:

ifeq ($(filter $(MAKECMDGOALS), clean), )
-include $(DEPS)
endif

.PHONY: configheader

# all build rules are defined, start build process
$(call INFO_LOG,Start building)

endif

endif # make spotless
