# build a top-level wrapper crate as a staticlib to link into lk.elf

# collect paths for proc-macro deps with BUILDDIR for host libs
WRAPPER_RUST_EXTERN_PATHS := $(foreach stem, $(ALL_KERNEL_HOST_CRATE_STEMS), $(stem)=$(TRUSTY_HOST_LIBRARY_BUILDDIR)/lib$(stem).so)

# change BUILDDIR so RSOBJS for kernel are distinct targets from userspace ones
OLD_BUILDDIR := $(BUILDDIR)
BUILDDIR := $(BUILDDIR)/kernellib

WRAPPER_RUSTFLAGS := --crate-type=staticlib

# compute the set of rust module rlibs to depend on
ALLMODULE_RLIBS := $(foreach stem, $(ALLMODULE_CRATE_STEMS), $(call TOBUILDDIR,lib$(stem).rlib))

# topologically sort crates based on dependency order.

# first, emit every pair of depender and dependency into $(CRATE_DEPS_FILE)
CRATE_DEPS_FILE := $(BUILDDIR)/crate-dependencies
$(CRATE_DEPS_FILE): $(ALLMODULE_RLIBS)
	@$(MKDIR)
	: > $(CRATE_DEPS_FILE)
	$(foreach mod, $(ALLMODULE_CRATE_STEMS), $(foreach dep, $(MODULE_$(mod)_CRATE_DEPS), echo $(mod) $(dep) >> $(CRATE_DEPS_FILE);))

# process these pairs with tsort to generate an ordering
SORTED_CRATE_STEMS_FILE := $(BUILDDIR)/crate-dependency-ordering
$(SORTED_CRATE_STEMS_FILE): $(CRATE_DEPS_FILE)
	@$(MKDIR)
	echo -n "ALLMODULE_CRATE_STEMS_SORTED := " > $(SORTED_CRATE_STEMS_FILE)
	tsort < $(CRATE_DEPS_FILE) | tac | tr "\n" " " >> $(SORTED_CRATE_STEMS_FILE)

# load our ordering in ALLMODULE_CRATE_STEMS_SORTED via include
include $(SORTED_CRATE_STEMS_FILE)


# build "--extern foo=/path/to/foo" flags for rustc
WRAPPER_RUST_EXTERN_PATHS += $(foreach stem,$(ALLMODULE_CRATE_STEMS_SORTED),$(subst .,_,$(stem))=$(call TOBUILDDIR,lib$(stem).rlib))
WRAPPER_RUSTFLAGS += $(addprefix --extern ,$(WRAPPER_RUST_EXTERN_PATHS))

# generate a .rs source file for the wrapper crate
# we must not explicitly "extern crate" core or compiler_builtins
CRATES_TO_IMPORT := $(filter-out core compiler_builtins,$(ALL_KERNEL_HOST_CRATE_STEMS) $(ALLMODULE_CRATE_STEMS_SORTED))
RUST_WRAPPER_SRC := \#![feature(panic_abort)] \#![no_std] \
    $(foreach crate, $(CRATES_TO_IMPORT), extern crate $(subst .,_,$(crate));) \
    \#[panic_handler] fn handle_panic(_: &core::panic::PanicInfo) -> ! {loop {}}

RUST_WRAPPER := $(BUILDDIR)/lk-crates.rs

$(RUST_WRAPPER): RUST_WRAPPER_SRC := $(RUST_WRAPPER_SRC)
$(RUST_WRAPPER): $(SORTED_CRATE_NAMES_FILE)
	@$(MKDIR)
	echo "$(RUST_WRAPPER_SRC)" > "$@"

RUST_WRAPPER_OBJ := $(BUILDDIR)/lk-crates.a

$(RUST_WRAPPER_OBJ): WRAPPER_RUSTFLAGS := $(WRAPPER_RUSTFLAGS)
$(RUST_WRAPPER_OBJ): ARCH_RUSTFLAGS := $(ARCH_$(ARCH)_RUSTFLAGS)

$(RUST_WRAPPER_OBJ): $(ALLMODULE_RLIBS) $(RUST_WRAPPER)
	$(RUSTC) $(GLOBAL_KERNEL_RUSTFLAGS) $(GLOBAL_SHARED_RUSTFLAGS) $(ARCH_RUSTFLAGS) $(WRAPPER_RUSTFLAGS) -o $@ $(RUST_WRAPPER)

# if there were no rust crates, don't build the .a
ifneq ($(ALLMODULE_CRATE_STEMS),)
EXTRA_OBJS += $(RUST_WRAPPER_OBJ)
endif

# restore BUILDDIR
BUILDDIR := $(OLD_BUILDDIR)

RUST_WRAPPER_SRC :=
WRAPPER_RUSTFLAGS :=
WRAPPER_RUST_EXTERN_PATHS :=
ALLMODULE_CRATE_STEMS_SORTED :=
