#
# Copyright (c) 2022, Google, Inc. All rights reserved
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

MODULE_DEPS := \
	lib/io

ifndef WITH_CUSTOM_MALLOC
MODULE_DEPS += lib/heap
endif

# Clang currently generates incorrect code when it simplifies calls to libc
# and then inlines them.  The simplification pass does not set a calling
# convention on the new call, leading to problems when inlining.
# Avoid this bug by disabling LTO for libc.  See: b/161257552
MODULE_DISABLE_LTO := true

MUSL_DIR := external/trusty/musl
LK_DIR := $(LKROOT)
LIBC_TRUSTY_DIR := trusty/user/base/lib/libc-trusty

MODULE_INCLUDES += \
	$(MUSL_DIR)/src/internal \
	$(MUSL_DIR)/src/include \

MODULE_EXPORT_COMPILEFLAGS += \
	-isystem $(MUSL_DIR)/arch/$(STANDARD_ARCH_NAME) \
	-isystem $(MUSL_DIR)/arch/generic \
	-isystem $(MUSL_DIR)/include \

MODULE_EXPORT_INCLUDES += \
	$(LK_DIR)/lib/libc/include_common \

# Musl is scrupulous about exposing prototypes and defines based on what
# standard is requested. When compiling C++ code, however, Clang defines
# _GNU_SOURCE because libcxx's header files depend on prototypes that are only
# available with _GNU_SOURCE specified. To avoid skew where prototypes are
# defined for C++ but not C, turn everything on always.
MODULE_EXPORT_COMPILEFLAGS += -D_ALL_SOURCE

# Musl declares global variables with names like "index" that can conflict with
# function names when _ALL_SOURCE is turned on. Compile Musl as it expects to be
# compiled.
MODULE_COMPILEFLAGS += -U_ALL_SOURCE -D_XOPEN_SOURCE=700

# Musl's source is not warning clean. Suppress warnings we know about.
MODULE_COMPILEFLAGS += \
	-Wno-parentheses \
	-Wno-sign-compare \
	-Wno-incompatible-pointer-types-discards-qualifiers \
	-Wno-string-plus-int \
	-Wno-missing-braces \
	-Wno-implicit-fallthrough \
	-Wno-unused-but-set-variable \

# Musl is generally not strict about its function prototypes.
# This could be fixed, except for "main". The prototype for main is deliberately
# ill-defined.
MODULE_CFLAGS += -Wno-strict-prototypes

# Musl will do something like this:
# weak_alias(a, b); weak_alias(b, c);
# But it appears the second statement will get eagerly evaluated to:
# weak_alias(a, c);
# and overriding b will not affect c.  This is likely not intended behavior, but
# it does not matter for us so ignore it.
MODULE_COMPILEFLAGS += \
	-Wno-ignored-attributes \

# The are compares that make sense in 64-bit but do not make sense in 32-bit.
MODULE_COMPILEFLAGS += \
	-Wno-tautological-constant-compare

# NOTE eabi_unwind_stubs.c because libgcc pulls in unwinding stuff.
MODULE_SRCS := \
	$(LOCAL_DIR)/abort.c \
	$(LOCAL_DIR)/close.c \
	$(LOCAL_DIR)/fflush.c \
	$(LOCAL_DIR)/libc_state.c \
	$(LOCAL_DIR)/writev.c \
	$(LK_DIR)/lib/libc/atoi.c \
	$(LK_DIR)/lib/libc/eabi.c \
	$(LK_DIR)/lib/libc/eabi_unwind_stubs.c \
	$(LK_DIR)/lib/libc/io_handle.c \
	$(LK_DIR)/lib/libc/printf.c \
	$(LK_DIR)/lib/libc/stdio.c \
	$(LK_DIR)/lib/libc/strtol.c \
	$(LK_DIR)/lib/libc/strtoll.c \

# These sources are only necessary to support C++
MODULE_SRCS += \
	$(LIBC_TRUSTY_DIR)/locale_stubs.c \
	$(LK_DIR)/lib/libc/atexit.c \
	$(LK_DIR)/lib/libc/pure_virtual.cpp

MODULE_DEPS += \
	$(LK_DIR)/lib/libc/rand

# These stubs are only needed because binder uses libutils which uses pthreads mutex directly
MODULE_SRCS += \
	$(LIBC_TRUSTY_DIR)/pthreads.c

# Musl
MODULE_SRCS += \
	$(MUSL_DIR)/src/ctype/isalnum.c \
	$(MUSL_DIR)/src/ctype/isalpha.c \
	$(MUSL_DIR)/src/ctype/isascii.c \
	$(MUSL_DIR)/src/ctype/isblank.c \
	$(MUSL_DIR)/src/ctype/iscntrl.c \
	$(MUSL_DIR)/src/ctype/isdigit.c \
	$(MUSL_DIR)/src/ctype/isgraph.c \
	$(MUSL_DIR)/src/ctype/islower.c \
	$(MUSL_DIR)/src/ctype/isprint.c \
	$(MUSL_DIR)/src/ctype/ispunct.c \
	$(MUSL_DIR)/src/ctype/isspace.c \
	$(MUSL_DIR)/src/ctype/isupper.c \
	$(MUSL_DIR)/src/ctype/isxdigit.c \
	$(MUSL_DIR)/src/ctype/toascii.c \
	$(MUSL_DIR)/src/ctype/tolower.c \
	$(MUSL_DIR)/src/ctype/toupper.c \
	$(MUSL_DIR)/src/locale/c_locale.c \
	$(MUSL_DIR)/src/stdlib/abs.c \
	$(MUSL_DIR)/src/stdlib/bsearch.c \
	$(MUSL_DIR)/src/stdlib/div.c \
	$(MUSL_DIR)/src/stdlib/imaxabs.c \
	$(MUSL_DIR)/src/stdlib/imaxdiv.c \
	$(MUSL_DIR)/src/stdlib/labs.c \
	$(MUSL_DIR)/src/stdlib/ldiv.c \
	$(MUSL_DIR)/src/stdlib/llabs.c \
	$(MUSL_DIR)/src/stdlib/lldiv.c \
	$(MUSL_DIR)/src/stdlib/qsort.c \
	$(MUSL_DIR)/src/string/bcmp.c \
	$(MUSL_DIR)/src/string/memccpy.c \
	$(MUSL_DIR)/src/string/memmem.c \
	$(MUSL_DIR)/src/string/mempcpy.c \
	$(MUSL_DIR)/src/string/memrchr.c \
	$(MUSL_DIR)/src/string/stpcpy.c \
	$(MUSL_DIR)/src/string/stpncpy.c \
	$(MUSL_DIR)/src/string/strcasecmp.c \
	$(MUSL_DIR)/src/string/strcasestr.c \
	$(MUSL_DIR)/src/string/strchrnul.c \
	$(MUSL_DIR)/src/string/strcspn.c \
	$(MUSL_DIR)/src/string/strerror_r.c \
	$(MUSL_DIR)/src/string/strncasecmp.c \
	$(MUSL_DIR)/src/string/strndup.c \
	$(MUSL_DIR)/src/string/strsep.c \
	$(MUSL_DIR)/src/string/strtok_r.c \
	$(MUSL_DIR)/src/string/strverscmp.c \
	$(MUSL_DIR)/src/string/swab.c \

# We use the lk implementation of stdio but export
# the musl stdio.h headers, which expose different
# definitions of stdin/out/err. The files below contain
# the musl definitions of those FILE structures, along
# with their dependencies.
MODULE_SRCS += \
	$(MUSL_DIR)/src/stdio/stderr.c \
	$(MUSL_DIR)/src/stdio/stdin.c \
	$(MUSL_DIR)/src/stdio/stdout.c \
	$(MUSL_DIR)/src/stdio/__stdio_close.c \
	$(MUSL_DIR)/src/stdio/__stdio_read.c \
	$(MUSL_DIR)/src/stdio/__stdio_write.c \
	$(MUSL_DIR)/src/stdio/__stdio_seek.c \

# These sources are only necessary to support C++
MODULE_SRCS += \
	$(MUSL_DIR)/src/ctype/__ctype_get_mb_cur_max.c \
	$(MUSL_DIR)/src/multibyte/internal.c \
	$(MUSL_DIR)/src/multibyte/mbtowc.c \
	$(MUSL_DIR)/src/multibyte/wcrtomb.c \

include $(LK_DIR)/lib/libc/string/rules.mk

include make/library.mk
