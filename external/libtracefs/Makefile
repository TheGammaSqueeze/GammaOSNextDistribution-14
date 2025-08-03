# SPDX-License-Identifier: LGPL-2.1
# libtracefs version
TFS_VERSION = 1
TFS_PATCHLEVEL = 6
TFS_EXTRAVERSION = 4
TRACEFS_VERSION = $(TFS_VERSION).$(TFS_PATCHLEVEL).$(TFS_EXTRAVERSION)

export TFS_VERSION
export TFS_PATCHLEVEL
export TFS_EXTRAVERSION
export TRACEFS_VERSION

LIBTRACEEVENT_MIN_VERSION = 1.3

# taken from trace-cmd
MAKEFLAGS += --no-print-directory

# Makefiles suck: This macro sets a default value of $(2) for the
# variable named by $(1), unless the variable has been set by
# environment or command line. This is necessary for CC and AR
# because make sets default values, so the simpler ?= approach
# won't work as expected.
define allow-override
  $(if $(or $(findstring environment,$(origin $(1))),\
            $(findstring command line,$(origin $(1)))),,\
    $(eval $(1) = $(2)))
endef

# Allow setting CC and AR, or setting CROSS_COMPILE as a prefix.
$(call allow-override,CC,$(CROSS_COMPILE)gcc)
$(call allow-override,AR,$(CROSS_COMPILE)ar)
$(call allow-override,PKG_CONFIG,pkg-config)
$(call allow-override,LD_SO_CONF_PATH,/etc/ld.so.conf.d/)
$(call allow-override,LDCONFIG,ldconfig)

EXT = -std=gnu99
INSTALL = install

# Use DESTDIR for installing into a different root directory.
# This is useful for building a package. The program will be
# installed in this directory as if it was the root directory.
# Then the build tool can move it later.
DESTDIR ?=
DESTDIR_SQ = '$(subst ','\'',$(DESTDIR))'

LP64 := $(shell echo __LP64__ | ${CC} ${CFLAGS} -E -x c - | tail -n 1)
ifeq ($(LP64), 1)
  libdir_relative_temp = lib64
else
  libdir_relative_temp = lib
endif

libdir_relative ?= $(libdir_relative_temp)
prefix ?= /usr/local
man_dir ?= $(prefix)/share/man
man_dir_SQ = '$(subst ','\'',$(man_dir))'
libdir ?= $(prefix)/$(libdir_relative)
libdir_SQ = '$(subst ','\'',$(libdir))'
includedir_relative ?= include/tracefs
includedir ?= $(prefix)/$(includedir_relative)
includedir_SQ = '$(subst ','\'',$(includedir))'
pkgconfig_dir ?= $(word 1,$(shell $(PKG_CONFIG) 		\
			--variable pc_path pkg-config | tr ":" " "))

TEST_LIBTRACEEVENT = $(shell sh -c "$(PKG_CONFIG) --atleast-version $(LIBTRACEEVENT_MIN_VERSION) libtraceevent > /dev/null 2>&1 && echo y")

ifeq ("$(TEST_LIBTRACEEVENT)", "y")
LIBTRACEEVENT_INCLUDES = $(shell sh -c "$(PKG_CONFIG) --cflags libtraceevent")
LIBTRACEEVENT_LIBS = $(shell sh -c "$(PKG_CONFIG) --libs libtraceevent")
else
 ifneq ($(MAKECMDGOALS),clean)
   $(error libtraceevent.so minimum version of $(LIBTRACEEVENT_MIN_VERSION) not installed)
 endif
endif

etcdir ?= /etc
etcdir_SQ = '$(subst ','\'',$(etcdir))'

export man_dir man_dir_SQ html_install html_install_SQ INSTALL
export img_install img_install_SQ
export DESTDIR DESTDIR_SQ

pound := \#

HELP_DIR = -DHELP_DIR=$(html_install)
HELP_DIR_SQ = '$(subst ','\'',$(HELP_DIR))'
#' emacs highlighting gets confused by the above escaped quote.

BASH_COMPLETE_DIR ?= $(etcdir)/bash_completion.d

# copy a bit from Linux kbuild

ifeq ("$(origin V)", "command line")
  VERBOSE = $(V)
endif
ifndef VERBOSE
  VERBOSE = 0
endif

SILENT := $(if $(findstring s,$(filter-out --%,$(MAKEFLAGS))),1)

# $(call test-build, snippet, ret) -> ret if snippet compiles
#                                  -> empty otherwise
test-build = $(if $(shell sh -c 'echo "$(1)" | \
	$(CC) -o /dev/null -c -x c - > /dev/null 2>&1 && echo y'), $2)

ifeq ("$(origin O)", "command line")

  saved-output := $(O)
  BUILD_OUTPUT := $(shell cd $(O) && /bin/pwd)
  $(if $(BUILD_OUTPUT),, \
    $(error output directory "$(saved-output)" does not exist))

else
  BUILD_OUTPUT = $(CURDIR)
endif

srctree		:= $(if $(BUILD_SRC),$(BUILD_SRC),$(CURDIR))
objtree		:= $(BUILD_OUTPUT)
src		:= $(srctree)
obj		:= $(objtree)
bdir		:= $(obj)/lib

export prefix src obj bdir

LIBTRACEFS_STATIC = $(bdir)/libtracefs.a
LIBTRACEFS_SHARED = $(bdir)/libtracefs.so.$(TRACEFS_VERSION)

LIBTRACEFS_SHARED_SO = $(bdir)/libtracefs.so
LIBTRACEFS_SHARED_VERSION = $(bdir)/libtracefs.so.$(TFS_VERSION)

PKG_CONFIG_SOURCE_FILE = libtracefs.pc
PKG_CONFIG_FILE := $(addprefix $(obj)/,$(PKG_CONFIG_SOURCE_FILE))

LPTHREAD ?= -lpthread
LIBS = $(LIBTRACEEVENT_LIBS) $(LPTHREAD)

export LIBS
export LIBTRACEFS_STATIC LIBTRACEFS_SHARED
export LIBTRACEEVENT_LIBS LIBTRACEEVENT_INCLUDES
export LIBTRACEFS_SHARED_SO LIBTRACEFS_SHARED_VERSION

export Q SILENT VERBOSE EXT

# Include the utils
include scripts/utils.mk

INCLUDES = -I$(src)/include
INCLUDES += -I$(src)/include/tracefs

include $(src)/scripts/features.mk

# Set compile option CFLAGS if not set elsewhere
CFLAGS ?= -g -Wall
CPPFLAGS ?=
LDFLAGS ?=

CUNIT_INSTALLED := $(shell if (printf "$(pound)include <CUnit/Basic.h>\n void main(){CU_initialize_registry();}" | $(CC) -x c - -lcunit -o /dev/null >/dev/null 2>&1) ; then echo 1; else echo 0 ; fi)
export CUNIT_INSTALLED

export CFLAGS
export INCLUDES

# Append required CFLAGS
override CFLAGS += -D_GNU_SOURCE $(LIBTRACEEVENT_INCLUDES) $(INCLUDES)

# Make sure 32 bit stat() works on large file systems
override CFLAGS += -D_FILE_OFFSET_BITS=64

all: all_cmd

LIB_TARGET  = libtracefs.a libtracefs.so.$(TRACEFS_VERSION)
LIB_INSTALL = libtracefs.a libtracefs.so*
LIB_INSTALL := $(addprefix $(bdir)/,$(LIB_INSTALL))

TARGETS = libtracefs.so libtracefs.a

all_cmd: $(TARGETS) $(PKG_CONFIG_FILE)

libtracefs.a: $(bdir) $(LIBTRACEFS_STATIC)
libtracefs.so: $(bdir) $(LIBTRACEFS_SHARED)

libs: libtracefs.a libtracefs.so

VALGRIND = $(shell which valgrind)
UTEST_DIR = utest
UTEST_BINARY = trace-utest

test: force $(LIBTRACEFS_STATIC)
ifneq ($(CUNIT_INSTALLED),1)
	$(error CUnit framework not installed, cannot build unit tests))
endif
	$(Q)$(call descend,$(src)/$(UTEST_DIR),$@)

test_mem: test
ifeq (, $(VALGRIND))
	$(error "No valgrind in $(PATH), cannot run memory test")
endif
ifneq ($(shell id -u), 0)
	$(error "The unit test should be run as root, as it reuqires full access to tracefs")
endif
	CK_FORK=no LD_LIBRARY_PATH=$(bdir) $(VALGRIND) \
		--show-leak-kinds=all --leak-resolution=high \
		--leak-check=full --show-possibly-lost=yes \
		--track-origins=yes -s \
		$(src)/$(UTEST_DIR)/$(UTEST_BINARY)

define find_tag_files
	find $(src) -name '\.pc' -prune -o -name '*\.[ch]' -print -o -name '*\.[ch]pp' \
		! -name '\.#' -print
endef

define do_make_pkgconfig_file
	cp -f ${PKG_CONFIG_SOURCE_FILE}.template ${PKG_CONFIG_FILE};	\
	sed -i "s|INSTALL_PREFIX|${1}|g" ${PKG_CONFIG_FILE}; 		\
	sed -i "s|LIB_VERSION|${TRACEFS_VERSION}|g" ${PKG_CONFIG_FILE}; \
	sed -i "s|LIB_DIR|${libdir_relative}|g" ${PKG_CONFIG_FILE}; \
	sed -i "s|HEADER_DIR|$(includedir_relative)|g" ${PKG_CONFIG_FILE}; \
	sed -i "s|LIBTRACEEVENT_MIN|$(LIBTRACEEVENT_MIN_VERSION)|g" ${PKG_CONFIG_FILE};
endef

BUILD_PREFIX := $(BUILD_OUTPUT)/build_prefix

VERSION_FILE = tfs_version.h

$(BUILD_PREFIX): force
	$(Q)$(call build_prefix,$(prefix))

$(PKG_CONFIG_FILE) : ${PKG_CONFIG_SOURCE_FILE}.template $(BUILD_PREFIX) $(VERSION_FILE)
	$(Q) $(call do_make_pkgconfig_file,$(prefix))

VIM_TAGS = $(obj)/tags
EMACS_TAGS = $(obj)/TAGS
CSCOPE_TAGS = $(obj)/cscope

$(VIM_TAGS): force
	$(RM) $@
	$(call find_tag_files) | (cd $(obj) && xargs ctags --extra=+f --c-kinds=+px)

$(EMACS_TAGS): force
	$(RM) $@
	$(call find_tag_files) | (cd $(obj) && xargs etags)

$(CSCOPE_TAGS): force
	$(RM) $(obj)/cscope*
	$(call find_tag_files) | cscope -b -q

tags: $(VIM_TAGS)
TAGS: $(EMACS_TAGS)
cscope: $(CSCOPE_TAGS)

ifeq ("$(DESTDIR)", "")
# If DESTDIR is not defined, then test if after installing the library
# and running ldconfig, if the library is visible by ld.so.
# If not, add the path to /etc/ld.so.conf.d/trace.conf and run ldconfig again.
define install_ld_config
	if $(LDCONFIG); then \
		if ! grep -q "^$(libdir)$$" $(LD_SO_CONF_PATH)/* ; then \
			$(CC) -o $(objtree)/test $(srctree)/test.c -I $(includedir_SQ) \
				-L $(libdir_SQ) -ltracefs &> /dev/null; \
			if ! $(objtree)/test &> /dev/null; then \
				$(call print_install, trace.conf, $(LD_SO_CONF_PATH)) \
				echo $(libdir_SQ) >> $(LD_SO_CONF_PATH)/trace.conf; \
				$(LDCONFIG); \
			fi; \
			$(RM) $(objtree)/test; \
		fi; \
	fi
endef
else
# If installing to a location for another machine or package, do not bother
# with running ldconfig.
define install_ld_config
endef
endif # DESTDIR = ""

install_libs: libs install_pkgconfig
	$(Q)$(call do_install,$(LIBTRACEFS_SHARED),$(libdir_SQ)); \
		cp -fpR $(LIB_INSTALL) $(DESTDIR)$(libdir_SQ)
	$(Q)$(call do_install,$(src)/include/tracefs.h,$(includedir_SQ),644)
	$(Q)$(call install_ld_config)

install: install_libs

install_pkgconfig: $(PKG_CONFIG_FILE)
	$(Q)$(call , $(PKG_CONFIG_FILE)) \
		$(call do_install_pkgconfig_file,$(prefix))

doc: check_doc
	$(Q)$(call descend,$(src)/Documentation,all)

doc_clean:
	$(Q)$(call descend,$(src)/Documentation,clean)

install_doc:
	$(Q)$(call descend,$(src)/Documentation,install)

check_doc: force
	$(Q)$(src)/check-manpages.sh $(src)/Documentation

define build_uninstall_script
	$(Q)mkdir $(BUILD_OUTPUT)/tmp_build
	$(Q)$(MAKE) -C $(src) DESTDIR=$(BUILD_OUTPUT)/tmp_build/ O=$(BUILD_OUTPUT) $1 > /dev/null
	$(Q)find $(BUILD_OUTPUT)/tmp_build ! -type d -printf "%P\n" > $(BUILD_OUTPUT)/build_$2
	$(Q)$(RM) -rf $(BUILD_OUTPUT)/tmp_build
endef

build_uninstall: $(BUILD_PREFIX)
	$(call build_uninstall_script,install,uninstall)

$(BUILD_OUTPUT)/build_uninstall: build_uninstall

define uninstall_file
	if [ -f $(DESTDIR)/$1 -o -h $(DESTDIR)/$1 ]; then \
		$(call print_uninstall,$(DESTDIR)/$1)$(RM) $(DESTDIR)/$1; \
	fi;
endef

uninstall: $(BUILD_OUTPUT)/build_uninstall
	@$(foreach file,$(shell cat $(BUILD_OUTPUT)/build_uninstall),$(call uninstall_file,$(file)))

PHONY += force
force:

# Declare the contents of the .PHONY variable as phony.  We keep that
# information in a variable so we can use it in if_changed and friends.
.PHONY: $(PHONY)

DEFAULT_TARGET = $(LIBTRACEFS_STATIC)

OBJS =
OBJS += tracefs-utils.o
OBJS += tracefs-instance.o
OBJS += tracefs-events.o

OBJS := $(OBJS:%.o=$(bdir)/%.o)

all: $(DEFAULT_TARGET)

$(bdir):
	@mkdir -p $(bdir)

VERSION = $(TFS_VERSION)
PATCHLEVEL = $(TFS_PATCHLEVEL)
EXTRAVERSION = $(TFS_EXTRAVERSION)

define make_version.h
  (echo '/* This file is automatically generated. Do not modify. */';		\
   echo \#define VERSION_CODE $(shell						\
   expr $(VERSION) \* 256 + $(PATCHLEVEL));					\
   echo '#define EXTRAVERSION ' $(EXTRAVERSION);				\
   echo '#define VERSION_STRING "'$(VERSION).$(PATCHLEVEL).$(EXTRAVERSION)'"';	\
  ) > $1
endef

define update_version.h
  ($(call make_version.h, $@.tmp);		\
    if [ -r $@ ] && cmp -s $@ $@.tmp; then	\
      rm -f $@.tmp;				\
    else					\
      echo '  UPDATE             $@';		\
      mv -f $@.tmp $@;				\
    fi);
endef

$(VERSION_FILE): force
	$(Q)$(call update_version.h)

$(LIBTRACEFS_STATIC): force
	$(Q)$(call descend,$(src)/src,$@)

$(bdir)/libtracefs.so.$(TRACEFS_VERSION): force
	$(Q)$(call descend,$(src)/src,libtracefs.so)

samples/sqlhist: libtracefs.a
	$(Q)$(call descend,$(src)/samples,sqlhist)

sqlhist: samples/sqlhist

samples: libtracefs.a force
	$(Q)$(call descend,$(src)/samples,all)

clean:
	$(Q)$(call descend_clean,utest)
	$(Q)$(call descend_clean,src)
	$(Q)$(call descend_clean,samples)
	$(Q)$(call do_clean, \
	  $(TARGETS) $(bdir)/*.a $(bdir)/*.so $(bdir)/*.so.* $(bdir)/*.o $(bdir)/.*.d \
	  $(PKG_CONFIG_FILE) \
	  $(VERSION_FILE) \
	  $(BUILD_PREFIX))

.PHONY: clean

# libtracefs.a and libtracefs.so would concurrently enter the same directory -
# a recipe for collisions.
.NOTPARALLEL:
