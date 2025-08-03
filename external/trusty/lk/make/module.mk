
# modules
#
# args:
# MODULE : module name (required)
# MODULE_SRCS : list of source files, local path (required)
# MODULE_DEPS : other modules that this one depends on
# MODULE_DEFINES : #defines local to this module
# MODULE_OPTFLAGS : OPTFLAGS local to this module
# MODULE_COMPILEFLAGS : COMPILEFLAGS local to this module
# MODULE_CFLAGS : CFLAGS local to this module
# MODULE_CPPFLAGS : CPPFLAGS local to this module
# MODULE_ASMFLAGS : ASMFLAGS local to this module
# MODULE_RUSTFLAGS : RUSTFLAGS local to this module
# MODULE_RUSTDOCFLAGS : RUSTDOCFLAGS local to this module
# MODULE_RUSTDOC_OBJECT : marker file to use as target when building Rust docs
# MODULE_INCLUDES : include directories local to this module
# MODULE_SRCDEPS : extra dependencies that all of this module's files depend on
# MODULE_EXTRA_ARCHIVES : extra .a files that should be linked with the module
# MODULE_EXTRA_OBJS : extra .o files that should be linked with the module
# MODULE_DISABLE_LTO : disable LTO for this module
# MODULE_DISABLE_CFI : disable CFI for this module
# MODULE_DISABLE_STACK_PROTECTOR : disable stack protector for this module
# MODULE_DISABLE_SCS : disable shadow call stack for this module
# MODULE_SKIP_DOCS : skip generating docs for this module

# MODULE_ARM_OVERRIDE_SRCS : list of source files, local path that should be force compiled with ARM (if applicable)

# the minimum module rules.mk file is as follows:
#
# LOCAL_DIR := $(GET_LOCAL_DIR)
# MODULE := $(LOCAL_DIR)
#
# MODULE_SRCS := $(LOCAL_DIR)/at_least_one_source_file.c
#
# include make/module.mk

# if QUERY_MODULE is set, the rules.mk that included us was itself included not
# to define a module's make targets but to query the variables it sets for the
# rest of the build. in this case, skip all further processing
ifeq ($(QUERY_MODULE),)

# test for old style rules.mk
ifneq ($(MODULE_OBJS),)
$(warning MODULE_OBJS = $(MODULE_OBJS))
$(error MODULE $(MODULE) is setting MODULE_OBJS, change to MODULE_SRCS)
endif
ifneq ($(OBJS),)
$(warning OBJS = $(OBJS))
$(error MODULE $(MODULE) is probably setting OBJS, change to MODULE_SRCS)
endif

ifeq ($(call TOBOOL,$(TRUSTY_NEW_MODULE_SYSTEM)),true)
$(error MODULE $(MODULE) was included through the new module system and therefore must include library.mk or trusted_app.mk)
endif

MODULE_SRCDIR := $(MODULE)
MODULE_BUILDDIR := $(call TOBUILDDIR,$(MODULE_SRCDIR))

# add a local include dir to the global include path
GLOBAL_INCLUDES += $(MODULE_SRCDIR)/include

$(foreach MOD,$(MODULE_DEPS), $(if $(call FIND_MODULE,$(MOD)),,$(error Module doesn't exist: $(MOD) (included from $(MODULE)))))

# add the listed module deps to the global list
MODULES += $(MODULE_DEPS)

#$(info module $(MODULE))
#$(info MODULE_SRCDIR $(MODULE_SRCDIR))
#$(info MODULE_BUILDDIR $(MODULE_BUILDDIR))
#$(info MODULE_DEPS $(MODULE_DEPS))
#$(info MODULE_SRCS $(MODULE_SRCS))

# Turn spaces into underscores and escape quotes for the module_config.h header
define clean_defines
$(subst $(SPACE),_,$(subst \",\\\\\",$(1)))
endef

MODULE_DEFINES += MODULE_COMPILEFLAGS=\"$(call clean_defines,$(MODULE_COMPILEFLAGS))\"
MODULE_DEFINES += MODULE_CFLAGS=\"$(call clean_defines,$(MODULE_CFLAGS))\"
MODULE_DEFINES += MODULE_CPPFLAGS=\"$(call clean_defines,$(MODULE_CPPFLAGS))\"
MODULE_DEFINES += MODULE_ASMFLAGS=\"$(call clean_defines,$(MODULE_ASMFLAGS))\"
MODULE_DEFINES += MODULE_RUSTFLAGS=\"$(call clean_defines,$(MODULE_RUSTFLAGS))\"
MODULE_DEFINES += MODULE_RUSTDOCFLAGS=\"$(call clean_defines,$(MODULE_RUSTDOCFLAGS))\"
MODULE_DEFINES += MODULE_RUST_ENV=\"$(call clean_defines,$(MODULE_RUST_ENV))\"
MODULE_DEFINES += MODULE_LDFLAGS=\"$(call clean_defines,$(MODULE_LDFLAGS))\"
MODULE_DEFINES += MODULE_OPTFLAGS=\"$(call clean_defines,$(MODULE_OPTFLAGS))\"
MODULE_DEFINES += MODULE_INCLUDES=\"$(call clean_defines,$(MODULE_INCLUDES))\"
MODULE_DEFINES += MODULE_SRCDEPS=\"$(call clean_defines,$(MODULE_SRCDEPS))\"
MODULE_DEFINES += MODULE_DEPS=\"$(call clean_defines,$(MODULE_DEPS))\"
MODULE_DEFINES += MODULE_SRCS=\"$(call clean_defines,$(MODULE_SRCS))\"

# Handle common kernel module flags. Common userspace flags are found in
# user/base/make/common_flags.mk
ifneq (true,$(call TOBOOL,$(USER_TASK_MODULE)))

# LTO
ifneq (true,$(call TOBOOL,$(MODULE_DISABLE_LTO)))
ifeq (true,$(call TOBOOL,$(KERNEL_LTO_ENABLED)))
MODULE_COMPILEFLAGS += $(GLOBAL_LTO_COMPILEFLAGS)

# CFI
MODULE_CFI_ENABLED := false
ifneq (true,$(call TOBOOL,$(MODULE_DISABLE_CFI)))
ifeq (true,$(call TOBOOL,$(CFI_ENABLED)))
MODULE_CFI_ENABLED := true
endif

ifdef KERNEL_CFI_ENABLED
MODULE_CFI_ENABLED := $(call TOBOOL,$(KERNEL_CFI_ENABLED))
endif

endif

ifeq (true,$(call TOBOOL,$(MODULE_CFI_ENABLED)))
MODULE_COMPILEFLAGS += \
	-fsanitize-blacklist=trusty/kernel/lib/ubsan/exemptlist \
	-fsanitize=cfi \
	-DCFI_ENABLED

MODULES += trusty/kernel/lib/ubsan

ifeq (true,$(call TOBOOL,$(CFI_DIAGNOSTICS)))
MODULE_COMPILEFLAGS += -fno-sanitize-trap=cfi
endif
endif

endif
endif

# Branch Target Identification
ifeq (true,$(call TOBOOL,$(KERNEL_BTI_ENABLED)))
MODULE_COMPILEFLAGS += -DKERNEL_BTI_ENABLED \
                       -DBTI_ENABLED
endif

# Pointer Authentication Codes
ifeq (true,$(call TOBOOL,$(KERNEL_PAC_ENABLED)))
ifeq (true,$(call TOBOOL,$(SCS_ENABLED)))
# See https://github.com/llvm/llvm-project/issues/63457
$(error Error: Kernel shadow call stack is not supported when Kernel PAC is enabled)
endif

MODULE_COMPILEFLAGS += -DKERNEL_PAC_ENABLED
endif

# Decide on the branch protection scheme
ifeq (true,$(call TOBOOL,$(KERNEL_BTI_ENABLED)))
ifeq (true,$(call TOBOOL,$(KERNEL_PAC_ENABLED)))
MODULE_COMPILEFLAGS += -mbranch-protection=bti+pac-ret
else
MODULE_COMPILEFLAGS += -mbranch-protection=bti
endif
else # !KERNEL_BTI_ENABLED
ifeq (true,$(call TOBOOL,$(KERNEL_PAC_ENABLED)))
MODULE_COMPILEFLAGS += -mbranch-protection=pac-ret
endif
endif

# Shadow call stack
ifeq (true,$(call TOBOOL,$(SCS_ENABLED)))
# set in arch/$(ARCH)/toolchain.mk iff shadow call stack is supported
ifeq (false,$(call TOBOOL,$(ARCH_$(ARCH)_SUPPORTS_SCS)))
$(error Error: Shadow call stack is not supported for $(ARCH))
endif

ifeq (false,$(call TOBOOL,$(MODULE_DISABLE_SCS)))
# architectures that support SCS should set the flag that reserves
# a register for the shadow call stack in their toolchain.mk file
MODULE_COMPILEFLAGS += \
	-fsanitize=shadow-call-stack \

endif
endif

endif

# Rebuild every module if the toolchain changes
MODULE_SRCDEPS += $(TOOLCHAIN_CONFIG)

MODULE_IS_RUST := $(if $(filter %.rs,$(MODULE_SRCS)),true,false)

# generate a per-module config.h file
ifeq ($(MODULE_IS_RUST),false)
MODULE_CONFIG := $(MODULE_BUILDDIR)/module_config.h

$(MODULE_CONFIG): MODULE_DEFINES:=$(MODULE_DEFINES)
$(MODULE_CONFIG): MODULE:=$(MODULE)
$(MODULE_CONFIG): configheader
	@$(call INFO_DONE,$(MODULE),generating config header, $@)
	@$(call MAKECONFIGHEADER,$@,MODULE_DEFINES)

GENERATED += $(MODULE_CONFIG)

MODULE_COMPILEFLAGS += --include $(MODULE_CONFIG)

MODULE_SRCDEPS += $(MODULE_CONFIG)

MODULE_INCLUDES := $(addprefix -I,$(MODULE_INCLUDES))
endif

# include the rules to compile the module's object files
include make/compile.mk

# MODULE_OBJS is passed back from compile.mk
#$(info MODULE_OBJS = $(MODULE_OBJS))

ifeq ($(MODULE_IS_RUST),true)

# ensure that proc-macro libraries are considered host libraries. userspace does
# this in library.mk, but we also compile proc-macro crates for the kernel here
ifeq ($(MODULE_RUST_CRATE_TYPES),proc-macro)
MODULE_RUST_HOST_LIB := true
endif

MODULE_IS_KERNEL :=
# is module using old module system? (using module.mk directly)
ifeq ($(TRUSTY_USERSPACE),)
ifeq ($(call TOBOOL,$(MODULE_RUST_HOST_LIB)),false)
MODULE_IS_KERNEL := true
endif
endif

# is module being built as kernel code?
ifeq ($(call TOBOOL,$(MODULE_IS_KERNEL)),true)

# validate crate name
ifeq ($(MODULE_CRATE_NAME),)
$(error rust module $(MODULE) does not set MODULE_CRATE_NAME. It must be set with a simple assignment, i.e. "MODULE_CRATE_NAME := foo")
endif

# if specific kernel rust deps not specified, rust modules use other deps.
# library and module deps are set mutually exclusively, so it's safe to simply
# concatenate them to use whichever is set
MODULE_KERNEL_RUST_DEPS := $(MODULE_LIBRARY_DEPS) $(MODULE_LIBRARY_EXPORTED_DEPS) $(MODULE_DEPS)

ifeq ($(call TOBOOL,$(MODULE_ADD_IMPLICIT_DEPS)),true)

# In userspace, MODULE_ADD_IMPLICIT_DEPS adds std.
# In the kernel, it adds core and compiler_builtins.
MODULE_KERNEL_RUST_DEPS += \
	trusty/user/base/lib/libcore-rust/ \
	trusty/user/base/lib/libcompiler_builtins-rust/ \

endif

define READ_CRATE_INFO
QUERY_MODULE := $1
QUERY_VARIABLES := MODULE_CRATE_NAME MODULE_RUST_STEM MODULE_RUST_CRATE_TYPES
$$(eval include make/query.mk)

# crate name has no default; error if it is not given
ifeq ($$(QUERY_MODULE_CRATE_NAME),)
$$(error could not determine crate name for module $1)
endif

# assign queried variables for later use
MODULE_$(1)_CRATE_NAME := $$(QUERY_MODULE_CRATE_NAME)
MODULE_$(1)_CRATE_STEM := $$(if $$(QUERY_MODULE_RUST_STEM),$$(QUERY_MODULE_RUST_STEM),$$(QUERY_MODULE_CRATE_NAME))
MODULE_$(1)_RUST_CRATE_TYPES := $$(if $$(QUERY_MODULE_RUST_CRATE_TYPES),$$(QUERY_MODULE_RUST_CRATE_TYPES),rlib)
endef

# ensure that MODULE_..._CRATE_NAME, _CRATE_STEM, and _RUST_CRATE_TYPES are populated
$(foreach rust_dep,$(MODULE_KERNEL_RUST_DEPS),$(eval $(call READ_CRATE_INFO,$(rust_dep))))

# split deps into proc-macro and non- because the former are built for the host
KERNEL_RUST_DEPS := $(foreach dep, $(MODULE_KERNEL_RUST_DEPS), $(if $(filter proc-macro,$(MODULE_$(dep)_RUST_CRATE_TYPES)),,$(dep)))

HOST_RUST_DEPS := $(foreach dep, $(MODULE_KERNEL_RUST_DEPS), $(if $(filter proc-macro,$(MODULE_$(dep)_RUST_CRATE_TYPES)),$(dep),))

# add kernel rust deps to the set of modules
MODULES += $(KERNEL_RUST_DEPS)
HOST_MODULES += $(HOST_RUST_DEPS)

# determine crate names of dependency modules so we can depend on their rlibs.
# because of ordering, we cannot simply e.g. set/read MODULE_$(dep)_CRATE_NAME,
# so we must manually read the variable value from the Makefile
DEP_CRATE_NAMES := $(foreach dep, $(KERNEL_RUST_DEPS), $(MODULE_$(dep)_CRATE_NAME))
DEP_CRATE_STEMS := $(foreach dep, $(KERNEL_RUST_DEPS), $(MODULE_$(dep)_CRATE_STEM))

# compute paths of host (proc-macro) dependencies
HOST_DEP_CRATE_NAMES := $(foreach dep, $(HOST_RUST_DEPS), $(MODULE_$(dep)_CRATE_NAME))
HOST_DEP_CRATE_STEMS := $(foreach dep, $(HOST_RUST_DEPS), $(MODULE_$(dep)_CRATE_STEM))
MODULE_KERNEL_RUST_HOST_LIBS := $(foreach stem, $(HOST_DEP_CRATE_STEMS), $(TRUSTY_HOST_LIBRARY_BUILDDIR)/lib$(stem).so)
gen_host_rlib_assignment = $(1)=$(TRUSTY_HOST_LIBRARY_BUILDDIR)/lib$(2).so
MODULE_RLIBS += $(call pairmap,gen_host_rlib_assignment,$(HOST_DEP_CRATE_NAMES),$(HOST_DEP_CRATE_STEMS))

# Stem defaults to the crate name
ifeq ($(MODULE_RUST_STEM),)
MODULE_RUST_STEM := $(MODULE_CRATE_NAME)
endif

# save dep crate names so we can topologically sort them for top-level rust build
MODULE_$(MODULE_RUST_STEM)_CRATE_DEPS := $(DEP_CRATE_STEMS)
ALL_KERNEL_HOST_CRATE_NAMES := $(ALL_KERNEL_HOST_CRATE_NAMES) $(HOST_DEP_CRATE_NAMES)
ALL_KERNEL_HOST_CRATE_STEMS := $(ALL_KERNEL_HOST_CRATE_STEMS) $(HOST_DEP_CRATE_STEMS)

# change BUILDDIR so RSOBJS for kernel are distinct targets from userspace ones
OLD_BUILDDIR := $(BUILDDIR)
BUILDDIR := $(TRUSTY_KERNEL_LIBRARY_BUILDDIR)

# compute paths of dependencies
MODULE_KERNEL_RUST_LIBS := $(foreach dep, $(DEP_CRATE_STEMS), $(call TOBUILDDIR,lib$(dep).rlib))
gen_rlib_assignment = $(1)=$(call TOBUILDDIR,lib$(2).rlib)
MODULE_RLIBS += $(call pairmap,gen_rlib_assignment,$(DEP_CRATE_NAMES),$(DEP_CRATE_STEMS))

# include rust lib deps in lib deps
MODULE_LIBRARIES += $(MODULE_KERNEL_RUST_LIBS) $(MODULE_KERNEL_RUST_HOST_LIBS)

# determine MODULE_RSOBJS and MODULE_RUST_CRATE_TYPES for rust kernel modules
include make/rust.mk

# only allow rlibs because we build rlibs, then link them all into one .a
ifneq ($(MODULE_RUST_CRATE_TYPES),rlib)
$(error rust crates for the kernel must be built as rlibs only, but $(MODULE) builds $(MODULE_RUST_CRATE_TYPES))
endif

# accumulate list of all crates we built (for linking, so skip proc-macro crates)
ALLMODULE_CRATE_STEMS := $(MODULE_RUST_STEM) $(ALLMODULE_CRATE_STEMS)

# reset BUILDDIR
BUILDDIR := $(OLD_BUILDDIR)

else # userspace rust

MODULE_OBJECT := $(MODULE_RSOBJS)

# make the rest of the build depend on our output
ALLMODULE_OBJS := $(MODULE_INIT_OBJS) $(ALLMODULE_OBJS) $(MODULE_OBJECT) $(MODULE_EXTRA_ARCHIVES)

endif # kernel/userspace rust

# Build Rust sources
$(addsuffix .d,$(MODULE_RSOBJS)):

MODULE_RSSRC := $(filter %.rs,$(MODULE_SRCS))
$(MODULE_RSOBJS): MODULE := $(MODULE)
$(MODULE_RSOBJS): $(MODULE_RSSRC) $(MODULE_SRCDEPS) $(MODULE_EXTRA_OBJECTS) $(MODULE_LIBRARIES) $(addsuffix .d,$(MODULE_RSOBJS))
	@$(MKDIR)
	@$(call ECHO,$(MODULE),compiling rust module,$<)
	$(NOECHO)$(MODULE_RUST_ENV) $(RUSTC) $(GLOBAL_RUSTFLAGS) $(ARCH_RUSTFLAGS) $(MODULE_RUSTFLAGS) $< --emit "dep-info=$@.d" -o $@
	@$(call ECHO_DONE_SILENT,$(MODULE),compiling rust module,$<)

ifneq ($(call TOBOOL,$(MODULE_SKIP_DOCS)),true)

# Pass rustdoc the same flags as rustc such that the generated documentation
# matches the code that gets compiled and run. Note: $(GLOBAL_RUSTFLAGS) adds
# $(TRUSTY_HOST_LIBRARY_BUILDDIR) to the library search path. This is necessary
# to pick up dependencies that are proc macros and thus built in the host dir.
$(MODULE_RUSTDOC_OBJECT): $(MODULE_RSSRC) | $(MODULE_RSOBJS)
	@$(MKDIR)
	@$(call ECHO,rustdoc,generating documentation,for $(MODULE_CRATE_NAME))
	$(NOECHO)$(MODULE_RUST_ENV) $(RUSTDOC) $(GLOBAL_RUSTFLAGS) $(ARCH_RUSTFLAGS) $(MODULE_RUSTDOCFLAGS) -L $(TRUSTY_LIBRARY_BUILDDIR) --out-dir $(MODULE_RUSTDOC_OUT_DIR) $<
	@touch $@
	@$(call ECHO_DONE_SILENT,rustdoc,generating documentation,for $(MODULE_CRATE_NAME))

EXTRA_BUILDDEPS += $(MODULE_RUSTDOC_OBJECT)

endif

-include $(addsuffix .d,$(MODULE_RSOBJS))

# track the module rlib for make clean
GENERATED += $(MODULE_RSOBJS)


else # not rust
# Archive the module's object files into a static library.
MODULE_OBJECT := $(call TOBUILDDIR,$(MODULE_SRCDIR).mod.a)
$(MODULE_OBJECT): MODULE := $(MODULE)
$(MODULE_OBJECT): $(MODULE_OBJS) $(MODULE_EXTRA_OBJS)
	@$(MKDIR)
	@$(call ECHO,$(MODULE),creating,$@)
	$(NOECHO)rm -f $@
	$(NOECHO)$(AR) rcs $@ $^
	@$(call ECHO_DONE_SILENT,$(MODULE),creating,$@)

# track the module object for make clean
GENERATED += $(MODULE_OBJECT)

# make the rest of the build depend on our output
ALLMODULE_OBJS := $(MODULE_INIT_OBJS) $(ALLMODULE_OBJS) $(MODULE_OBJECT) $(MODULE_EXTRA_ARCHIVES)

endif # rust or not

# track all of the source files compiled
ALLSRCS += $(MODULE_SRCS_FIRST) $(MODULE_SRCS)

# track all the objects built
ALLOBJS += $(MODULE_INIT_OBJS) $(MODULE_OBJS)

# empty out any vars set here
MODULE :=
MODULE_SRCDIR :=
MODULE_BUILDDIR :=
MODULE_DEPS :=
MODULE_SRCS :=
MODULE_OBJS :=
MODULE_DEFINES :=
MODULE_OPTFLAGS :=
MODULE_COMPILEFLAGS :=
MODULE_CFLAGS :=
MODULE_CPPFLAGS :=
MODULE_ASMFLAGS :=
MODULE_RUSTFLAGS :=
MODULE_RUSTDOCFLAGS :=
MODULE_SRCDEPS :=
MODULE_INCLUDES :=
MODULE_EXTRA_ARCHIVES :=
MODULE_EXTRA_OBJS :=
MODULE_CONFIG :=
MODULE_OBJECT :=
MODULE_ARM_OVERRIDE_SRCS :=
MODULE_SRCS_FIRST :=
MODULE_INIT_OBJS :=
MODULE_DISABLE_LTO :=
MODULE_LTO_ENABLED :=
MODULE_DISABLE_CFI :=
MODULE_DISABLE_STACK_PROTECTOR :=
MODULE_DISABLE_SCS :=
MODULE_RSSRC :=
MODULE_IS_RUST :=
MODULE_RSOBJS :=
MODULE_RUST_EDITION :=
MODULE_RUSTDOC_OBJECT :=
MODULE_RUSTDOCFLAGS :=
MODULE_KERNEL_RUST_DEPS :=
MODULE_SKIP_DOCS :=
MODULE_ADD_IMPLICIT_DEPS := true

endif # QUERY_MODULE (this line should stay after all other processing)
