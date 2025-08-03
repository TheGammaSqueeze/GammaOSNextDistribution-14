# Find the local dir of the make file
GET_LOCAL_DIR    = $(patsubst %/,%,$(dir $(word $(words $(MAKEFILE_LIST)),$(MAKEFILE_LIST))))

# makes sure the target dir exists
MKDIR = if [ ! -d $(dir $@) ]; then mkdir -p $(dir $@); fi

# prepends the BUILD_DIR var to each item in the list
TOBUILDDIR = $(addprefix $(BUILDDIR)/,$(1))

# converts specified variable to boolean value
TOBOOL = $(if $(filter-out 0 false,$1),true,false)

# try to find a Trusty external at external/trusty first
# (if we moved it there), otherwise try the old directory
FIND_EXTERNAL = $(if $(wildcard external/trusty/$1),external/trusty/$1,external/$1)

# try to find a Rust crate at external/rust/crates/$CRATE and fall back to
# trusty/user/base/host/$CRATE and then trusty/user/base/lib/$CRATE-rust
FIND_CRATE = $(if $(wildcard external/rust/crates/$1/rules.mk),external/rust/crates/$1,$(if $(wildcard trusty/user/base/host/$1/rules.mk),trusty/user/base/host/$1,$(if $(wildcard trusty/user/base/host/$1-rust/rules.mk),trusty/user/base/host/$1-rust,trusty/user/base/lib/$1-rust)))

# checks if module with a given path exists
FIND_MODULE = $(wildcard $1/rules.mk)$(wildcard $(addsuffix /$1/rules.mk,$(.INCLUDE_DIRS)))

COMMA := ,
EMPTY :=
SPACE := $(EMPTY) $(EMPTY)

define NEWLINE


endef

STRIP_TRAILING_COMMA = $(if $(1),$(subst $(COMMA)END_OF_LIST_MARKER_FOR_STRIP_TRAILING_COMMA,,$(strip $(1))END_OF_LIST_MARKER_FOR_STRIP_TRAILING_COMMA))

# return $1 with the first word removed
rest-of-words = $(wordlist 2,$(words $1),$1)
# map $1 onto zipped pairs of items from lists $2 and $3
pairmap = $(and $(strip $2),$(strip $3),\
	$(call $1,$(firstword $2),$(firstword $3)) $(call pairmap,$1,$(call rest-of-words,$2),$(call rest-of-words,$3)))

# test if two files are different, replacing the first
# with the second if so
# args: $1 - temporary file to test
#       $2 - file to replace
define TESTANDREPLACEFILE
	if [ -f "$2" ]; then \
		if cmp "$1" "$2"; then \
			rm -f $1; \
		else \
			mv $1 $2; \
		fi \
	else \
		mv $1 $2; \
	fi
endef

# generate a header file at $1 with an expanded variable in $2
define MAKECONFIGHEADER
	$(MKDIR); \
	rm -f $1.tmp; \
	LDEF=`echo $1 | tr '/\\.-' '_' | sed "s/C++/CPP/g;s/c++/cpp/g"`; \
	echo \#ifndef __$${LDEF}_H > $1.tmp; \
	echo \#define __$${LDEF}_H >> $1.tmp; \
	for d in `echo $($2) | tr '[:lower:]' '[:upper:]'`; do \
		echo "#define $$d" | sed "s/=/\ /g;s/-/_/g;s/\//_/g;s/\./_/g;s/\//_/g;s/C++/CPP/g" >> $1.tmp; \
	done; \
	echo \#endif >> $1.tmp; \
	$(call TESTANDREPLACEFILE,$1.tmp,$1)
endef

# Map LK's arch names into a more common form.
define standard_name_for_arch
ifeq ($(2),arm)
$(1) := arm
else
ifeq ($(2),arm64)
$(1) := aarch64
else
ifeq ($(2),x86)
ifeq ($(3),x86-64)
$(1) := x86_64
else
ifeq ($(3),x86-32)
$(1) := i386
else
$$(error "unknown arch: $(2) / $(3)")
endif
endif
else
$$(error "unknown arch: $(2) / $(3)")
endif
endif
endif
endef

# prints task status message
# Format: INFO/ECHO module, status, message
ifneq ($(LOG_POSTPROCESSING),)
# this output will be postprocessed by python later, insert extra markers
# for easier parsing
LOG_PREFIX=@log@
LOG_DONE=@done@
LOG_SDONE=@sdone@
LOG_PRINT=@print@
LOG_SEPARATOR=@:@
INFO_LOG = $(info $(LOG_PREFIX)$(LOG_PRINT)$1)
INFO = $(info $(LOG_PREFIX)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3)
INFO_DONE = $(info $(LOG_PREFIX)$(LOG_DONE)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3)
INFO_DONE_SILENT = $(info $(LOG_PREFIX)$(LOG_SDONE)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3)
ECHO_LOG = echo $(LOG_PREFIX)$(LOG_PRINT)$1
ECHO = echo $(LOG_PREFIX)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3
ECHO_DONE = echo $(LOG_PREFIX)$(LOG_DONE)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3
ECHO_DONE_SILENT = echo $(LOG_PREFIX)$(LOG_SDONE)$1$(LOG_SEPARATOR)$2$(LOG_SEPARATOR)$3
else
# just output as regular
INFO_LOG = $(info $1)
INFO = $(info $2 $3 for $1)
INFO_DONE = $(info $2 $3 for $1)
INFO_DONE_SILENT =
ECHO_LOG = echo $1
ECHO = echo $2 $3
ECHO_DONE = echo $2 $3
ECHO_DONE_SILENT =
endif

