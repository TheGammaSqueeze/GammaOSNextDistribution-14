LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

# Clang currently generates incorrect code when it simplifies calls to libc
# and then inlines them.  The simplification pass does not set a calling
# convention on the new call, leading to problems when inlining.
# Avoid this bug by disabling LTO for libc.  See: b/161257552
MODULE_DISABLE_LTO := true

MUSL_DIR := external/trusty/musl

MODULE_EXPORT_INCLUDES += \
	$(MUSL_DIR)/arch/$(STANDARD_ARCH_NAME) \
	$(MUSL_DIR)/arch/generic \
	$(MUSL_DIR)/include  \

# Internal includes. Should mask public includes - but -isystem guarentees this.
MODULE_INCLUDES += \
	$(MUSL_DIR)/src/internal \
	$(MUSL_DIR)/src/include \

MODULE_EXPORT_INCLUDES += $(LOCAL_DIR)/include

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

# libc should be freestanding, but the rest of the app should not be.
MODULE_COMPILEFLAGS += -ffreestanding

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

# Musl's math code uses pragma STDC FENV_ACCESS ON.
# Neither Clang nor GCC support this pragma.
# https://wiki.musl-libc.org/mathematical-library.html
MODULE_COMPILEFLAGS += \
	-Wno-unknown-pragmas \
	-Wno-ignored-pragmas \

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
	$(LKROOT)/lib/libc/eabi_unwind_stubs.c \
	$(LOCAL_DIR)/__dso_handle.c \
	$(LOCAL_DIR)/__set_thread_area.c \
	$(LOCAL_DIR)/file_stubs.c \
	$(LOCAL_DIR)/locale_stubs.c \
	$(LOCAL_DIR)/time_stubs.c \


# Trusty-specific syscalls
MODULE_SRCS += \
	$(LOCAL_DIR)/ipc.c \
	$(LOCAL_DIR)/logging.c \
	$(LOCAL_DIR)/memref.c \
	$(LOCAL_DIR)/mman.c \
	$(LOCAL_DIR)/time.c \
	$(LOCAL_DIR)/trusty_err.c \
	$(LOCAL_DIR)/trusty_uio.c \

ifeq ($(ASLR),false)
MODULE_SRCS_FIRST := $(MUSL_DIR)/crt/crt1.c
else
MODULE_SRCS_FIRST := $(MUSL_DIR)/crt/rcrt1.c
endif

# Musl
MODULE_SRCS += \
	$(MUSL_DIR)/src/env/__environ.c \
	$(MUSL_DIR)/src/env/__init_tls.c \
	$(MUSL_DIR)/src/env/__libc_start_main.c \
	$(MUSL_DIR)/src/env/__stack_chk_fail.c \
	$(MUSL_DIR)/src/env/getenv.c \
	$(MUSL_DIR)/src/internal/defsysinfo.c \
	$(MUSL_DIR)/src/internal/floatscan.c \
	$(MUSL_DIR)/src/internal/intscan.c \
	$(MUSL_DIR)/src/internal/libc.c \
	$(MUSL_DIR)/src/internal/shgetc.c \
	$(MUSL_DIR)/src/ctype/__ctype_b_loc.c \
	$(MUSL_DIR)/src/ctype/__ctype_get_mb_cur_max.c \
	$(MUSL_DIR)/src/ctype/__ctype_tolower_loc.c \
	$(MUSL_DIR)/src/ctype/__ctype_toupper_loc.c \
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
	$(MUSL_DIR)/src/ctype/iswalnum.c \
	$(MUSL_DIR)/src/ctype/iswalpha.c \
	$(MUSL_DIR)/src/ctype/iswblank.c \
	$(MUSL_DIR)/src/ctype/iswcntrl.c \
	$(MUSL_DIR)/src/ctype/iswctype.c \
	$(MUSL_DIR)/src/ctype/iswdigit.c \
	$(MUSL_DIR)/src/ctype/iswgraph.c \
	$(MUSL_DIR)/src/ctype/iswlower.c \
	$(MUSL_DIR)/src/ctype/iswprint.c \
	$(MUSL_DIR)/src/ctype/iswpunct.c \
	$(MUSL_DIR)/src/ctype/iswspace.c \
	$(MUSL_DIR)/src/ctype/iswupper.c \
	$(MUSL_DIR)/src/ctype/iswxdigit.c \
	$(MUSL_DIR)/src/ctype/isxdigit.c \
	$(MUSL_DIR)/src/ctype/toascii.c \
	$(MUSL_DIR)/src/ctype/tolower.c \
	$(MUSL_DIR)/src/ctype/toupper.c \
	$(MUSL_DIR)/src/ctype/towctrans.c \
	$(MUSL_DIR)/src/ctype/wcswidth.c \
	$(MUSL_DIR)/src/ctype/wctrans.c \
	$(MUSL_DIR)/src/ctype/wcwidth.c \
	$(MUSL_DIR)/src/errno/strerror.c \
	$(MUSL_DIR)/src/errno/__errno_location.c \
	$(MUSL_DIR)/src/exit/abort.c \
	$(MUSL_DIR)/src/exit/assert.c \
	$(MUSL_DIR)/src/exit/atexit.c \
	$(MUSL_DIR)/src/exit/exit.c \
	$(MUSL_DIR)/src/exit/_Exit.c \
	$(MUSL_DIR)/src/misc/getauxval.c \
	$(MUSL_DIR)/src/multibyte/btowc.c \
	$(MUSL_DIR)/src/multibyte/c16rtomb.c \
	$(MUSL_DIR)/src/multibyte/c32rtomb.c \
	$(MUSL_DIR)/src/multibyte/internal.c \
	$(MUSL_DIR)/src/multibyte/mblen.c \
	$(MUSL_DIR)/src/multibyte/mbrlen.c \
	$(MUSL_DIR)/src/multibyte/mbrtoc16.c \
	$(MUSL_DIR)/src/multibyte/mbrtoc32.c \
	$(MUSL_DIR)/src/multibyte/mbrtowc.c \
	$(MUSL_DIR)/src/multibyte/mbsinit.c \
	$(MUSL_DIR)/src/multibyte/mbsnrtowcs.c \
	$(MUSL_DIR)/src/multibyte/mbsrtowcs.c \
	$(MUSL_DIR)/src/multibyte/mbstowcs.c \
	$(MUSL_DIR)/src/multibyte/mbtowc.c \
	$(MUSL_DIR)/src/multibyte/wcrtomb.c \
	$(MUSL_DIR)/src/multibyte/wcsnrtombs.c \
	$(MUSL_DIR)/src/multibyte/wcsrtombs.c \
	$(MUSL_DIR)/src/multibyte/wcstombs.c \
	$(MUSL_DIR)/src/multibyte/wctob.c \
	$(MUSL_DIR)/src/multibyte/wctomb.c \
	$(MUSL_DIR)/src/network/htonl.c \
	$(MUSL_DIR)/src/network/htons.c \
	$(MUSL_DIR)/src/network/ntohl.c \
	$(MUSL_DIR)/src/network/ntohs.c \
	$(MUSL_DIR)/src/prng/rand.c \
	$(MUSL_DIR)/src/stdlib/abs.c \
	$(MUSL_DIR)/src/stdlib/atof.c \
	$(MUSL_DIR)/src/stdlib/atoi.c \
	$(MUSL_DIR)/src/stdlib/atol.c \
	$(MUSL_DIR)/src/stdlib/atoll.c \
	$(MUSL_DIR)/src/stdlib/bsearch.c \
	$(MUSL_DIR)/src/stdlib/div.c \
	$(MUSL_DIR)/src/stdlib/ecvt.c \
	$(MUSL_DIR)/src/stdlib/fcvt.c \
	$(MUSL_DIR)/src/stdlib/gcvt.c \
	$(MUSL_DIR)/src/stdlib/imaxabs.c \
	$(MUSL_DIR)/src/stdlib/imaxdiv.c \
	$(MUSL_DIR)/src/stdlib/labs.c \
	$(MUSL_DIR)/src/stdlib/ldiv.c \
	$(MUSL_DIR)/src/stdlib/llabs.c \
	$(MUSL_DIR)/src/stdlib/lldiv.c \
	$(MUSL_DIR)/src/stdlib/qsort.c \
	$(MUSL_DIR)/src/stdlib/strtod.c \
	$(MUSL_DIR)/src/stdlib/strtol.c \
	$(MUSL_DIR)/src/stdlib/wcstod.c \
	$(MUSL_DIR)/src/stdlib/wcstol.c \
	$(MUSL_DIR)/src/string/bcmp.c \
	$(MUSL_DIR)/src/string/memccpy.c \
	$(MUSL_DIR)/src/string/memchr.c \
	$(MUSL_DIR)/src/string/memcmp.c \
	$(MUSL_DIR)/src/string/memcpy.c \
	$(MUSL_DIR)/src/string/memmem.c \
	$(MUSL_DIR)/src/string/memmove.c \
	$(MUSL_DIR)/src/string/mempcpy.c \
	$(MUSL_DIR)/src/string/memrchr.c \
	$(MUSL_DIR)/src/string/memset.c \
	$(MUSL_DIR)/src/string/stpcpy.c \
	$(MUSL_DIR)/src/string/stpncpy.c \
	$(MUSL_DIR)/src/string/strcasecmp.c \
	$(MUSL_DIR)/src/string/strcasestr.c \
	$(MUSL_DIR)/src/string/strcat.c \
	$(MUSL_DIR)/src/string/strchr.c \
	$(MUSL_DIR)/src/string/strchrnul.c \
	$(MUSL_DIR)/src/string/strcmp.c \
	$(MUSL_DIR)/src/string/strcpy.c \
	$(MUSL_DIR)/src/string/strcspn.c \
	$(MUSL_DIR)/src/string/strdup.c \
	$(MUSL_DIR)/src/string/strerror_r.c \
	$(MUSL_DIR)/src/string/strlen.c \
	$(MUSL_DIR)/src/string/strncasecmp.c \
	$(MUSL_DIR)/src/string/strncat.c \
	$(MUSL_DIR)/src/string/strncmp.c \
	$(MUSL_DIR)/src/string/strncpy.c \
	$(MUSL_DIR)/src/string/strndup.c \
	$(MUSL_DIR)/src/string/strnlen.c \
	$(MUSL_DIR)/src/string/strpbrk.c \
	$(MUSL_DIR)/src/string/strrchr.c \
	$(MUSL_DIR)/src/string/strsep.c \
	$(MUSL_DIR)/src/string/strsignal.c \
	$(MUSL_DIR)/src/string/strspn.c \
	$(MUSL_DIR)/src/string/strstr.c \
	$(MUSL_DIR)/src/string/strtok.c \
	$(MUSL_DIR)/src/string/strtok_r.c \
	$(MUSL_DIR)/src/string/strverscmp.c \
	$(MUSL_DIR)/src/string/swab.c \
	$(MUSL_DIR)/src/string/wcpcpy.c \
	$(MUSL_DIR)/src/string/wcpncpy.c \
	$(MUSL_DIR)/src/string/wcscasecmp.c \
	$(MUSL_DIR)/src/string/wcscasecmp_l.c \
	$(MUSL_DIR)/src/string/wcscat.c \
	$(MUSL_DIR)/src/string/wcschr.c \
	$(MUSL_DIR)/src/string/wcscmp.c \
	$(MUSL_DIR)/src/string/wcscpy.c \
	$(MUSL_DIR)/src/string/wcscspn.c \
	$(MUSL_DIR)/src/string/wcsdup.c \
	$(MUSL_DIR)/src/string/wcslen.c \
	$(MUSL_DIR)/src/string/wcsncasecmp.c \
	$(MUSL_DIR)/src/string/wcsncasecmp_l.c \
	$(MUSL_DIR)/src/string/wcsncat.c \
	$(MUSL_DIR)/src/string/wcsncmp.c \
	$(MUSL_DIR)/src/string/wcsncpy.c \
	$(MUSL_DIR)/src/string/wcsnlen.c \
	$(MUSL_DIR)/src/string/wcspbrk.c \
	$(MUSL_DIR)/src/string/wcsrchr.c \
	$(MUSL_DIR)/src/string/wcsspn.c \
	$(MUSL_DIR)/src/string/wcsstr.c \
	$(MUSL_DIR)/src/string/wcstok.c \
	$(MUSL_DIR)/src/string/wcswcs.c \
	$(MUSL_DIR)/src/string/wmemchr.c \
	$(MUSL_DIR)/src/string/wmemcmp.c \
	$(MUSL_DIR)/src/string/wmemcpy.c \
	$(MUSL_DIR)/src/string/wmemmove.c \
	$(MUSL_DIR)/src/string/wmemset.c \
	$(MUSL_DIR)/src/stdio/asprintf.c \
	$(MUSL_DIR)/src/stdio/fclose.c \
	$(MUSL_DIR)/src/stdio/fflush.c \
	$(MUSL_DIR)/src/stdio/fileno.c \
	$(MUSL_DIR)/src/stdio/fputc.c \
	$(MUSL_DIR)/src/stdio/fputs.c \
	$(MUSL_DIR)/src/stdio/fprintf.c \
	$(MUSL_DIR)/src/stdio/fread.c \
	$(MUSL_DIR)/src/stdio/fseek.c \
	$(MUSL_DIR)/src/stdio/ftell.c \
	$(MUSL_DIR)/src/stdio/fwrite.c \
	$(MUSL_DIR)/src/stdio/getc.c \
	$(MUSL_DIR)/src/stdio/ofl.c \
	$(MUSL_DIR)/src/stdio/printf.c \
	$(MUSL_DIR)/src/stdio/putc_unlocked.c \
	$(MUSL_DIR)/src/stdio/putchar.c \
	$(MUSL_DIR)/src/stdio/puts.c \
	$(MUSL_DIR)/src/stdio/sscanf.c \
	$(MUSL_DIR)/src/stdio/snprintf.c \
	$(MUSL_DIR)/src/stdio/sprintf.c \
	$(MUSL_DIR)/src/stdio/stderr.c \
	$(MUSL_DIR)/src/stdio/stdin.c \
	$(MUSL_DIR)/src/stdio/stdout.c \
	$(MUSL_DIR)/src/stdio/ungetc.c \
	$(MUSL_DIR)/src/stdio/vasprintf.c \
	$(MUSL_DIR)/src/stdio/vprintf.c \
	$(MUSL_DIR)/src/stdio/vfprintf.c \
	$(MUSL_DIR)/src/stdio/vsnprintf.c \
	$(MUSL_DIR)/src/stdio/vsprintf.c \
	$(MUSL_DIR)/src/stdio/vfscanf.c \
	$(MUSL_DIR)/src/stdio/vsscanf.c \
	$(MUSL_DIR)/src/stdio/__lockfile.c \
	$(MUSL_DIR)/src/stdio/__overflow.c \
	$(MUSL_DIR)/src/stdio/__stdio_close.c \
	$(MUSL_DIR)/src/stdio/__stdio_exit.c \
	$(MUSL_DIR)/src/stdio/__stdio_read.c \
	$(MUSL_DIR)/src/stdio/__stdio_write.c \
	$(MUSL_DIR)/src/stdio/__stdio_seek.c \
	$(MUSL_DIR)/src/stdio/__string_read.c \
	$(MUSL_DIR)/src/stdio/__toread.c \
	$(MUSL_DIR)/src/stdio/__towrite.c \
	$(MUSL_DIR)/src/stdio/__uflow.c \
	$(MUSL_DIR)/src/thread/__lock.c \
	$(MUSL_DIR)/src/thread/__wait.c \
	$(MUSL_DIR)/src/thread/default_attr.c \
	$(MUSL_DIR)/src/thread/pthread_once.c \
	$(MUSL_DIR)/src/thread/pthread_cleanup_push.c \
	$(MUSL_DIR)/src/time/gettimeofday.c \
	$(MUSL_DIR)/src/time/localtime.c \
	$(MUSL_DIR)/src/time/localtime_r.c \
	$(MUSL_DIR)/src/time/gmtime.c \
	$(MUSL_DIR)/src/time/gmtime_r.c \
	$(MUSL_DIR)/src/time/time.c \
	$(MUSL_DIR)/src/time/__secs_to_tm.c \
	$(MUSL_DIR)/src/unistd/sleep.c \
	$(MUSL_DIR)/src/unistd/usleep.c \

# Math
MODULE_SRCS += \
	$(MUSL_DIR)/src/math/acos.c \
	$(MUSL_DIR)/src/math/acosf.c \
	$(MUSL_DIR)/src/math/acosh.c \
	$(MUSL_DIR)/src/math/acoshf.c \
	$(MUSL_DIR)/src/math/acoshl.c \
	$(MUSL_DIR)/src/math/acosl.c \
	$(MUSL_DIR)/src/math/asin.c \
	$(MUSL_DIR)/src/math/asinf.c \
	$(MUSL_DIR)/src/math/asinh.c \
	$(MUSL_DIR)/src/math/asinhf.c \
	$(MUSL_DIR)/src/math/asinhl.c \
	$(MUSL_DIR)/src/math/asinl.c \
	$(MUSL_DIR)/src/math/atan2.c \
	$(MUSL_DIR)/src/math/atan2f.c \
	$(MUSL_DIR)/src/math/atan2l.c \
	$(MUSL_DIR)/src/math/atan.c \
	$(MUSL_DIR)/src/math/atanf.c \
	$(MUSL_DIR)/src/math/atanh.c \
	$(MUSL_DIR)/src/math/atanhf.c \
	$(MUSL_DIR)/src/math/atanhl.c \
	$(MUSL_DIR)/src/math/atanl.c \
	$(MUSL_DIR)/src/math/cbrt.c \
	$(MUSL_DIR)/src/math/cbrtf.c \
	$(MUSL_DIR)/src/math/cbrtl.c \
	$(MUSL_DIR)/src/math/ceil.c \
	$(MUSL_DIR)/src/math/ceilf.c \
	$(MUSL_DIR)/src/math/ceill.c \
	$(MUSL_DIR)/src/math/copysign.c \
	$(MUSL_DIR)/src/math/copysignf.c \
	$(MUSL_DIR)/src/math/copysignl.c \
	$(MUSL_DIR)/src/math/__cos.c \
	$(MUSL_DIR)/src/math/cos.c \
	$(MUSL_DIR)/src/math/__cosdf.c \
	$(MUSL_DIR)/src/math/cosf.c \
	$(MUSL_DIR)/src/math/cosh.c \
	$(MUSL_DIR)/src/math/coshf.c \
	$(MUSL_DIR)/src/math/coshl.c \
	$(MUSL_DIR)/src/math/__cosl.c \
	$(MUSL_DIR)/src/math/cosl.c \
	$(MUSL_DIR)/src/math/erf.c \
	$(MUSL_DIR)/src/math/erff.c \
	$(MUSL_DIR)/src/math/erfl.c \
	$(MUSL_DIR)/src/math/exp10.c \
	$(MUSL_DIR)/src/math/exp10f.c \
	$(MUSL_DIR)/src/math/exp10l.c \
	$(MUSL_DIR)/src/math/exp2.c \
	$(MUSL_DIR)/src/math/exp2f.c \
	$(MUSL_DIR)/src/math/exp2f_data.c \
	$(MUSL_DIR)/src/math/exp2l.c \
	$(MUSL_DIR)/src/math/exp.c \
	$(MUSL_DIR)/src/math/exp_data.c \
	$(MUSL_DIR)/src/math/expf.c \
	$(MUSL_DIR)/src/math/expl.c \
	$(MUSL_DIR)/src/math/expm1.c \
	$(MUSL_DIR)/src/math/expm1f.c \
	$(MUSL_DIR)/src/math/expm1l.c \
	$(MUSL_DIR)/src/math/__expo2.c \
	$(MUSL_DIR)/src/math/__expo2f.c \
	$(MUSL_DIR)/src/math/fabs.c \
	$(MUSL_DIR)/src/math/fabsf.c \
	$(MUSL_DIR)/src/math/fabsl.c \
	$(MUSL_DIR)/src/math/fdim.c \
	$(MUSL_DIR)/src/math/fdimf.c \
	$(MUSL_DIR)/src/math/fdiml.c \
	$(MUSL_DIR)/src/math/finite.c \
	$(MUSL_DIR)/src/math/finitef.c \
	$(MUSL_DIR)/src/math/floor.c \
	$(MUSL_DIR)/src/math/floorf.c \
	$(MUSL_DIR)/src/math/floorl.c \
	$(MUSL_DIR)/src/math/fma.c \
	$(MUSL_DIR)/src/math/fmaf.c \
	$(MUSL_DIR)/src/math/fmal.c \
	$(MUSL_DIR)/src/math/fmax.c \
	$(MUSL_DIR)/src/math/fmaxf.c \
	$(MUSL_DIR)/src/math/fmaxl.c \
	$(MUSL_DIR)/src/math/fmin.c \
	$(MUSL_DIR)/src/math/fminf.c \
	$(MUSL_DIR)/src/math/fminl.c \
	$(MUSL_DIR)/src/math/fmod.c \
	$(MUSL_DIR)/src/math/fmodf.c \
	$(MUSL_DIR)/src/math/fmodl.c \
	$(MUSL_DIR)/src/math/__fpclassify.c \
	$(MUSL_DIR)/src/math/__fpclassifyf.c \
	$(MUSL_DIR)/src/math/__fpclassifyl.c \
	$(MUSL_DIR)/src/math/frexp.c \
	$(MUSL_DIR)/src/math/frexpf.c \
	$(MUSL_DIR)/src/math/frexpl.c \
	$(MUSL_DIR)/src/math/hypot.c \
	$(MUSL_DIR)/src/math/hypotf.c \
	$(MUSL_DIR)/src/math/hypotl.c \
	$(MUSL_DIR)/src/math/ilogb.c \
	$(MUSL_DIR)/src/math/ilogbf.c \
	$(MUSL_DIR)/src/math/ilogbl.c \
	$(MUSL_DIR)/src/math/__invtrigl.c \
	$(MUSL_DIR)/src/math/j0.c \
	$(MUSL_DIR)/src/math/j0f.c \
	$(MUSL_DIR)/src/math/j1.c \
	$(MUSL_DIR)/src/math/j1f.c \
	$(MUSL_DIR)/src/math/jn.c \
	$(MUSL_DIR)/src/math/jnf.c \
	$(MUSL_DIR)/src/math/ldexp.c \
	$(MUSL_DIR)/src/math/ldexpf.c \
	$(MUSL_DIR)/src/math/ldexpl.c \
	$(MUSL_DIR)/src/math/lgamma.c \
	$(MUSL_DIR)/src/math/lgammaf.c \
	$(MUSL_DIR)/src/math/lgammaf_r.c \
	$(MUSL_DIR)/src/math/lgammal.c \
	$(MUSL_DIR)/src/math/lgamma_r.c \
	$(MUSL_DIR)/src/math/llrint.c \
	$(MUSL_DIR)/src/math/llrintf.c \
	$(MUSL_DIR)/src/math/llrintl.c \
	$(MUSL_DIR)/src/math/llround.c \
	$(MUSL_DIR)/src/math/llroundf.c \
	$(MUSL_DIR)/src/math/llroundl.c \
	$(MUSL_DIR)/src/math/log10.c \
	$(MUSL_DIR)/src/math/log10f.c \
	$(MUSL_DIR)/src/math/log10l.c \
	$(MUSL_DIR)/src/math/log1p.c \
	$(MUSL_DIR)/src/math/log1pf.c \
	$(MUSL_DIR)/src/math/log1pl.c \
	$(MUSL_DIR)/src/math/log2.c \
	$(MUSL_DIR)/src/math/log2_data.c \
	$(MUSL_DIR)/src/math/log2f.c \
	$(MUSL_DIR)/src/math/log2f_data.c \
	$(MUSL_DIR)/src/math/log2l.c \
	$(MUSL_DIR)/src/math/logb.c \
	$(MUSL_DIR)/src/math/logbf.c \
	$(MUSL_DIR)/src/math/logbl.c \
	$(MUSL_DIR)/src/math/log.c \
	$(MUSL_DIR)/src/math/log_data.c \
	$(MUSL_DIR)/src/math/logf.c \
	$(MUSL_DIR)/src/math/logf_data.c \
	$(MUSL_DIR)/src/math/logl.c \
	$(MUSL_DIR)/src/math/lrint.c \
	$(MUSL_DIR)/src/math/lrintf.c \
	$(MUSL_DIR)/src/math/lrintl.c \
	$(MUSL_DIR)/src/math/lround.c \
	$(MUSL_DIR)/src/math/lroundf.c \
	$(MUSL_DIR)/src/math/lroundl.c \
	$(MUSL_DIR)/src/math/__math_divzero.c \
	$(MUSL_DIR)/src/math/__math_divzerof.c \
	$(MUSL_DIR)/src/math/__math_invalid.c \
	$(MUSL_DIR)/src/math/__math_invalidf.c \
	$(MUSL_DIR)/src/math/__math_oflow.c \
	$(MUSL_DIR)/src/math/__math_oflowf.c \
	$(MUSL_DIR)/src/math/__math_uflow.c \
	$(MUSL_DIR)/src/math/__math_uflowf.c \
	$(MUSL_DIR)/src/math/__math_xflow.c \
	$(MUSL_DIR)/src/math/__math_xflowf.c \
	$(MUSL_DIR)/src/math/modf.c \
	$(MUSL_DIR)/src/math/modff.c \
	$(MUSL_DIR)/src/math/modfl.c \
	$(MUSL_DIR)/src/math/nan.c \
	$(MUSL_DIR)/src/math/nanf.c \
	$(MUSL_DIR)/src/math/nanl.c \
	$(MUSL_DIR)/src/math/nearbyint.c \
	$(MUSL_DIR)/src/math/nearbyintf.c \
	$(MUSL_DIR)/src/math/nearbyintl.c \
	$(MUSL_DIR)/src/math/nextafter.c \
	$(MUSL_DIR)/src/math/nextafterf.c \
	$(MUSL_DIR)/src/math/nextafterl.c \
	$(MUSL_DIR)/src/math/nexttoward.c \
	$(MUSL_DIR)/src/math/nexttowardf.c \
	$(MUSL_DIR)/src/math/nexttowardl.c \
	$(MUSL_DIR)/src/math/__polevll.c \
	$(MUSL_DIR)/src/math/pow.c \
	$(MUSL_DIR)/src/math/pow_data.c \
	$(MUSL_DIR)/src/math/powf.c \
	$(MUSL_DIR)/src/math/powf_data.c \
	$(MUSL_DIR)/src/math/powl.c \
	$(MUSL_DIR)/src/math/remainder.c \
	$(MUSL_DIR)/src/math/remainderf.c \
	$(MUSL_DIR)/src/math/remainderl.c \
	$(MUSL_DIR)/src/math/__rem_pio2.c \
	$(MUSL_DIR)/src/math/__rem_pio2f.c \
	$(MUSL_DIR)/src/math/__rem_pio2_large.c \
	$(MUSL_DIR)/src/math/__rem_pio2l.c \
	$(MUSL_DIR)/src/math/remquo.c \
	$(MUSL_DIR)/src/math/remquof.c \
	$(MUSL_DIR)/src/math/remquol.c \
	$(MUSL_DIR)/src/math/rint.c \
	$(MUSL_DIR)/src/math/rintf.c \
	$(MUSL_DIR)/src/math/rintl.c \
	$(MUSL_DIR)/src/math/round.c \
	$(MUSL_DIR)/src/math/roundf.c \
	$(MUSL_DIR)/src/math/roundl.c \
	$(MUSL_DIR)/src/math/scalb.c \
	$(MUSL_DIR)/src/math/scalbf.c \
	$(MUSL_DIR)/src/math/scalbln.c \
	$(MUSL_DIR)/src/math/scalblnf.c \
	$(MUSL_DIR)/src/math/scalblnl.c \
	$(MUSL_DIR)/src/math/scalbn.c \
	$(MUSL_DIR)/src/math/scalbnf.c \
	$(MUSL_DIR)/src/math/scalbnl.c \
	$(MUSL_DIR)/src/math/__signbit.c \
	$(MUSL_DIR)/src/math/__signbitf.c \
	$(MUSL_DIR)/src/math/__signbitl.c \
	$(MUSL_DIR)/src/math/signgam.c \
	$(MUSL_DIR)/src/math/significand.c \
	$(MUSL_DIR)/src/math/significandf.c \
	$(MUSL_DIR)/src/math/__sin.c \
	$(MUSL_DIR)/src/math/sin.c \
	$(MUSL_DIR)/src/math/sincos.c \
	$(MUSL_DIR)/src/math/sincosf.c \
	$(MUSL_DIR)/src/math/sincosl.c \
	$(MUSL_DIR)/src/math/__sindf.c \
	$(MUSL_DIR)/src/math/sinf.c \
	$(MUSL_DIR)/src/math/sinh.c \
	$(MUSL_DIR)/src/math/sinhf.c \
	$(MUSL_DIR)/src/math/sinhl.c \
	$(MUSL_DIR)/src/math/__sinl.c \
	$(MUSL_DIR)/src/math/sinl.c \
	$(MUSL_DIR)/src/math/sqrt.c \
	$(MUSL_DIR)/src/math/sqrtf.c \
	$(MUSL_DIR)/src/math/sqrtl.c \
	$(MUSL_DIR)/src/math/__tan.c \
	$(MUSL_DIR)/src/math/tan.c \
	$(MUSL_DIR)/src/math/__tandf.c \
	$(MUSL_DIR)/src/math/tanf.c \
	$(MUSL_DIR)/src/math/tanh.c \
	$(MUSL_DIR)/src/math/tanhf.c \
	$(MUSL_DIR)/src/math/tanhl.c \
	$(MUSL_DIR)/src/math/__tanl.c \
	$(MUSL_DIR)/src/math/tanl.c \
	$(MUSL_DIR)/src/math/tgamma.c \
	$(MUSL_DIR)/src/math/tgammaf.c \
	$(MUSL_DIR)/src/math/tgammal.c \
	$(MUSL_DIR)/src/math/trunc.c \
	$(MUSL_DIR)/src/math/truncf.c \
	$(MUSL_DIR)/src/math/truncl.c \


# Locale
MODULE_SRCS += \
	$(MUSL_DIR)/src/locale/bind_textdomain_codeset.c \
	$(MUSL_DIR)/src/locale/catclose.c \
	$(MUSL_DIR)/src/locale/catgets.c \
	$(MUSL_DIR)/src/locale/catopen.c \
	$(MUSL_DIR)/src/locale/c_locale.c \
	$(MUSL_DIR)/src/locale/iconv.c \
	$(MUSL_DIR)/src/locale/iconv_close.c \
	$(MUSL_DIR)/src/locale/langinfo.c \
	$(MUSL_DIR)/src/locale/__lctrans.c \
	$(MUSL_DIR)/src/locale/localeconv.c \
	$(MUSL_DIR)/src/locale/__mo_lookup.c \
	$(MUSL_DIR)/src/locale/pleval.c \
	$(MUSL_DIR)/src/locale/strcoll.c \
	$(MUSL_DIR)/src/locale/strfmon.c \
	$(MUSL_DIR)/src/locale/strxfrm.c \
	$(MUSL_DIR)/src/locale/textdomain.c \
	$(MUSL_DIR)/src/locale/wcscoll.c \
	$(MUSL_DIR)/src/locale/wcsxfrm.c \


# Uses VLAs.
#	$(MUSL_DIR)/src/locale/dcngettext.c \

# Implicitly loads files, stub out.
#	$(MUSL_DIR)/src/locale/duplocale.c \
#	$(MUSL_DIR)/src/locale/freelocale.c \
#	$(MUSL_DIR)/src/locale/locale_map.c \
#	$(MUSL_DIR)/src/locale/newlocale.c \
#	$(MUSL_DIR)/src/locale/setlocale.c \
#	$(MUSL_DIR)/src/locale/uselocale.c \

# Fake pthreads
MODULE_SRCS += $(LOCAL_DIR)/pthreads.c

# TODO extract the early startup code from this module and turn on the stack
# protector for most of libc.
MODULE_DISABLE_STACK_PROTECTOR := true

# Do not include implicit dependencies to avoid recursively depending on libc
MODULE_ADD_IMPLICIT_DEPS := false

# Defined by kernel/lib/ubsan/enable.mk if in use for the build
ifeq ($(UBSAN_ENABLED), true)
MODULE_LIBRARY_DEPS += trusty/kernel/lib/ubsan
endif

# Add Trusty libc extensions (separated due to use both in the kernel and here)
MODULE_LIBRARY_EXPORTED_DEPS += trusty/kernel/lib/libc-ext

# Add dependency on syscall-stubs
MODULE_LIBRARY_DEPS += trusty/user/base/lib/syscall-stubs

# Add src dependency on syscall header to ensure it is generated before we try
# to build
include trusty/user/base/lib/syscall-stubs/common-inc.mk
MODULE_SRCDEPS += $(SYSCALL_H)

MODULE_LICENSES += $(MUSL_DIR)/LICENSE

include make/library.mk
