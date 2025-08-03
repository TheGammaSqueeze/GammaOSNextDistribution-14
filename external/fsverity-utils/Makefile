# SPDX-License-Identifier: MIT
# Copyright 2020 Google LLC
#
# Use of this source code is governed by an MIT-style
# license that can be found in the LICENSE file or at
# https://opensource.org/licenses/MIT.


# Use 'make help' to list available targets.
#
# Define V=1 to enable "verbose" mode, showing all executed commands.
#
# Define USE_SHARED_LIB=1 to link the fsverity binary to the shared library
# libfsverity.so rather than to the static library libfsverity.a.
#
# Define PREFIX to override the installation prefix, like './configure --prefix'
# in autotools-based projects (default: /usr/local)
#
# Define BINDIR to override where to install binaries, like './configure
# --bindir' in autotools-based projects (default: PREFIX/bin)
#
# Define INCDIR to override where to install headers, like './configure
# --includedir' in autotools-based projects (default: PREFIX/include)
#
# Define LIBDIR to override where to install libraries, like './configure
# --libdir' in autotools-based projects (default: PREFIX/lib)
#
# Define MANDIR to override where to install man pages, like './configure
# --mandir' in autotools-based projects (default: PREFIX/share/man)
#
# Define DESTDIR to override the installation destination directory
# (default: empty string)
#
# You can also specify custom CC, CFLAGS, CPPFLAGS, LDFLAGS, and/or PKGCONF.
#
##############################################################################

cc-option = $(shell if $(CC) $(1) -c -x c /dev/null -o /dev/null > /dev/null 2>&1; \
	      then echo $(1); fi)

# Support building with MinGW for minimal Windows fsverity.exe, but not for
# libfsverity. fsverity.exe will be statically linked.
ifneq ($(findstring -mingw,$(shell $(CC) -dumpmachine 2>/dev/null)),)
MINGW = 1
endif

# Set the CFLAGS.  First give the warning-related flags (unconditionally, though
# the user can override any of them by specifying the opposite flag); then give
# the user-specified CFLAGS, defaulting to -O2 if none were specified.
#
# Use -Wno-deprecated-declarations to avoid warnings about the Engine API having
# been deprecated in OpenSSL 3.0; the replacement isn't ready yet.
CFLAGS ?= -O2
override CFLAGS := -Wall -Wundef				\
	$(call cc-option,-Wdeclaration-after-statement)		\
	$(call cc-option,-Wimplicit-fallthrough)		\
	$(call cc-option,-Wmissing-field-initializers)		\
	$(call cc-option,-Wmissing-prototypes)			\
	$(call cc-option,-Wstrict-prototypes)			\
	$(call cc-option,-Wunused-parameter)			\
	$(call cc-option,-Wvla)					\
	$(call cc-option,-Wno-deprecated-declarations)		\
	$(CFLAGS)

override CPPFLAGS := -Iinclude -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE $(CPPFLAGS)

ifneq ($(V),1)
QUIET_CC        = @echo '  CC      ' $@;
QUIET_CCLD      = @echo '  CCLD    ' $@;
QUIET_AR        = @echo '  AR      ' $@;
QUIET_LN        = @echo '  LN      ' $@;
QUIET_GEN       = @echo '  GEN     ' $@;
QUIET_PANDOC    = @echo '  PANDOC  ' $@;
endif
USE_SHARED_LIB  ?=
PREFIX          ?= /usr/local
BINDIR          ?= $(PREFIX)/bin
INCDIR          ?= $(PREFIX)/include
LIBDIR          ?= $(PREFIX)/lib
MANDIR          ?= $(PREFIX)/share/man
DESTDIR         ?=
ifneq ($(MINGW),1)
PKGCONF         ?= pkg-config
else
PKGCONF         := false
EXEEXT          := .exe
endif
FSVERITY        := fsverity$(EXEEXT)

# Rebuild if a user-specified setting that affects the build changed.
.build-config: FORCE
	@flags=$$(							\
		echo 'CC=$(CC)';					\
		echo 'CFLAGS=$(CFLAGS)';				\
		echo 'CPPFLAGS=$(CPPFLAGS)';				\
		echo 'LDFLAGS=$(LDFLAGS)';				\
		echo 'LDLIBS=$(LDLIBS)';				\
		echo 'USE_SHARED_LIB=$(USE_SHARED_LIB)';		\
	);								\
	if [ "$$flags" != "`cat $@ 2>/dev/null`" ]; then		\
		[ -e $@ ] && echo "Rebuilding due to new settings";	\
		echo "$$flags" > $@;					\
	fi

DEFAULT_TARGETS :=
EXTRA_TARGETS   :=
COMMON_HEADERS  := $(wildcard common/*.h)
LDLIBS          := $(shell "$(PKGCONF)" libcrypto --libs 2>/dev/null || echo -lcrypto)
CFLAGS          += $(shell "$(PKGCONF)" libcrypto --cflags 2>/dev/null || echo)

# If we are dynamically linking, when running tests we need to override
# LD_LIBRARY_PATH as no RPATH is set
ifdef USE_SHARED_LIB
RUN_FSVERITY    = LD_LIBRARY_PATH=./ $(TEST_WRAPPER_PROG) ./$(FSVERITY)
else
RUN_FSVERITY    = $(TEST_WRAPPER_PROG) ./$(FSVERITY)
endif

##############################################################################

#### Library

SOVERSION       := 0
LIB_CFLAGS      := $(CFLAGS) -fvisibility=hidden
LIB_SRC         := $(wildcard lib/*.c)
ifeq ($(MINGW),1)
LIB_SRC         := $(filter-out lib/enable.c,${LIB_SRC})
endif
LIB_HEADERS     := $(wildcard lib/*.h) $(COMMON_HEADERS)
STATIC_LIB_OBJ  := $(LIB_SRC:.c=.o)
SHARED_LIB_OBJ  := $(LIB_SRC:.c=.shlib.o)

# Compile static library object files
$(STATIC_LIB_OBJ): %.o: %.c $(LIB_HEADERS) .build-config
	$(QUIET_CC) $(CC) -o $@ -c $(CPPFLAGS) $(LIB_CFLAGS) $<

# Compile shared library object files
$(SHARED_LIB_OBJ): %.shlib.o: %.c $(LIB_HEADERS) .build-config
	$(QUIET_CC) $(CC) -o $@ -c $(CPPFLAGS) $(LIB_CFLAGS) -fPIC $<

# Create static library
libfsverity.a:$(STATIC_LIB_OBJ)
	$(QUIET_AR) $(AR) cr $@ $+

DEFAULT_TARGETS += libfsverity.a

# Create shared library
libfsverity.so.$(SOVERSION):$(SHARED_LIB_OBJ)
	$(QUIET_CCLD) $(CC) -o $@ -Wl,-soname=$@ -shared $+ \
		$(CFLAGS) $(LDFLAGS) $(LDLIBS)

DEFAULT_TARGETS += libfsverity.so.$(SOVERSION)

# Create the symlink libfsverity.so => libfsverity.so.$(SOVERSION)
libfsverity.so:libfsverity.so.$(SOVERSION)
	$(QUIET_LN) ln -sf $+ $@

DEFAULT_TARGETS += libfsverity.so

##############################################################################

#### Programs

ALL_PROG_SRC      := $(wildcard programs/*.c)
ALL_PROG_OBJ      := $(ALL_PROG_SRC:.c=.o)
ALL_PROG_HEADERS  := $(wildcard programs/*.h) $(COMMON_HEADERS)
PROG_COMMON_SRC   := programs/utils.c
PROG_COMMON_OBJ   := $(PROG_COMMON_SRC:.c=.o)
FSVERITY_PROG_OBJ := $(PROG_COMMON_OBJ)		\
		     programs/cmd_digest.o	\
		     programs/cmd_sign.o	\
		     programs/fsverity.o
ifneq ($(MINGW),1)
FSVERITY_PROG_OBJ += \
		     programs/cmd_dump_metadata.o \
		     programs/cmd_enable.o	\
		     programs/cmd_measure.o
endif
TEST_PROG_SRC     := $(wildcard programs/test_*.c)
TEST_PROGRAMS     := $(TEST_PROG_SRC:programs/%.c=%$(EXEEXT))

# Compile program object files
$(ALL_PROG_OBJ): %.o: %.c $(ALL_PROG_HEADERS) .build-config
	$(QUIET_CC) $(CC) -o $@ -c $(CPPFLAGS) $(CFLAGS) $<

# Link the fsverity program
ifdef USE_SHARED_LIB
$(FSVERITY): $(FSVERITY_PROG_OBJ) libfsverity.so
	$(QUIET_CCLD) $(CC) -o $@ $(FSVERITY_PROG_OBJ) \
		$(CFLAGS) $(LDFLAGS) -L. -lfsverity
else
$(FSVERITY): $(FSVERITY_PROG_OBJ) libfsverity.a
	$(QUIET_CCLD) $(CC) -o $@ $+ $(CFLAGS) $(LDFLAGS) $(LDLIBS)
endif

DEFAULT_TARGETS += $(FSVERITY)

# Link the test programs
$(TEST_PROGRAMS): %$(EXEEXT): programs/%.o $(PROG_COMMON_OBJ) libfsverity.a
	$(QUIET_CCLD) $(CC) -o $@ $+ $(CFLAGS) $(LDFLAGS) $(LDLIBS)

EXTRA_TARGETS += $(TEST_PROGRAMS)

##############################################################################

#### Manual pages

man/fsverity.1:man/fsverity.1.md
	$(QUIET_PANDOC) pandoc $+ -s -t man > $@

MAN_PAGES := man/fsverity.1
EXTRA_TARGETS += $(MAN_PAGES)

##############################################################################

# Support for downloading and building BoringSSL.  The purpose of this is to
# allow testing builds of fsverity-utils that link to BoringSSL instead of
# OpenSSL, without having to use a system that uses BoringSSL natively.

boringssl:
	rm -rf boringssl boringssl.tar.gz
	curl -s -o boringssl.tar.gz \
		https://boringssl.googlesource.com/boringssl/+archive/refs/heads/master.tar.gz
	mkdir boringssl
	tar xf boringssl.tar.gz -C boringssl
	cmake -B boringssl/build boringssl
	$(MAKE) -C boringssl/build $(MAKEFLAGS)

##############################################################################

SPECIAL_TARGETS := all test_programs check install install-man uninstall \
		   help clean

FORCE:

.PHONY: $(SPECIAL_TARGETS) FORCE

.DEFAULT_GOAL = all

all:$(DEFAULT_TARGETS)

test_programs:$(TEST_PROGRAMS)

# This just runs some quick, portable tests.  Use scripts/run-tests.sh if you
# want to run the full tests.
check:$(FSVERITY) test_programs
	for prog in $(TEST_PROGRAMS); do \
		$(TEST_WRAPPER_PROG) ./$$prog || exit 1; \
	done
	$(RUN_FSVERITY) --help > /dev/null
	$(RUN_FSVERITY) --version > /dev/null
	$(RUN_FSVERITY) sign $(FSVERITY) fsverity.sig \
		--key=testdata/key.pem --cert=testdata/cert.pem > /dev/null
	$(RUN_FSVERITY) sign $(FSVERITY) fsverity.sig --hash=sha512 \
		--block-size=512 --salt=12345678 \
		--key=testdata/key.pem --cert=testdata/cert.pem > /dev/null
	$(RUN_FSVERITY) digest $(FSVERITY) --hash=sha512 \
		--block-size=512 --salt=12345678 > /dev/null
	rm -f fsverity.sig
	@echo "All tests passed!"

install:all
	install -d $(DESTDIR)$(LIBDIR)/pkgconfig $(DESTDIR)$(INCDIR) $(DESTDIR)$(BINDIR)
	install -m755 $(FSVERITY) $(DESTDIR)$(BINDIR)
	install -m644 libfsverity.a $(DESTDIR)$(LIBDIR)
	install -m755 libfsverity.so.$(SOVERSION) $(DESTDIR)$(LIBDIR)
	ln -sf libfsverity.so.$(SOVERSION) $(DESTDIR)$(LIBDIR)/libfsverity.so
	install -m644 include/libfsverity.h $(DESTDIR)$(INCDIR)
	sed -e "s|@PREFIX@|$(PREFIX)|" \
		-e "s|@LIBDIR@|$(LIBDIR)|" \
		-e "s|@INCDIR@|$(INCDIR)|" \
		lib/libfsverity.pc.in \
		> $(DESTDIR)$(LIBDIR)/pkgconfig/libfsverity.pc
	chmod 644 $(DESTDIR)$(LIBDIR)/pkgconfig/libfsverity.pc

install-man:$(MAN_PAGES)
	install -d $(DESTDIR)$(MANDIR)/man1
	install -m644 $+ $(DESTDIR)$(MANDIR)/man1/

uninstall:
	rm -f $(DESTDIR)$(BINDIR)/$(FSVERITY)
	rm -f $(DESTDIR)$(LIBDIR)/libfsverity.a
	rm -f $(DESTDIR)$(LIBDIR)/libfsverity.so.$(SOVERSION)
	rm -f $(DESTDIR)$(LIBDIR)/libfsverity.so
	rm -f $(DESTDIR)$(LIBDIR)/pkgconfig/libfsverity.pc
	rm -f $(DESTDIR)$(INCDIR)/libfsverity.h
	for page in $(notdir $(MAN_PAGES)); do \
		rm -f $(DESTDIR)$(MANDIR)/man1/$$page; \
	done

help:
	@echo "Available targets:"
	@echo "------------------"
	@for target in $(DEFAULT_TARGETS) $(EXTRA_TARGETS) $(SPECIAL_TARGETS); \
	do \
		echo $$target; \
	done

clean:
	rm -f $(DEFAULT_TARGETS) $(EXTRA_TARGETS) \
		lib/*.o programs/*.o .build-config fsverity.sig
