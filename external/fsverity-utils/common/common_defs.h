/* SPDX-License-Identifier: MIT */
/*
 * Common definitions for libfsverity and the 'fsverity' program
 *
 * Copyright 2018 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
#ifndef COMMON_COMMON_DEFS_H
#define COMMON_COMMON_DEFS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "win32_defs.h"

typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;

#ifndef __force
#  ifdef __CHECKER__
#    define __force	__attribute__((force))
#  else
#    define __force
#  endif
#endif

#ifndef __printf
#  define __printf(fmt_idx, vargs_idx) \
	__attribute__((format(printf, fmt_idx, vargs_idx)))
#endif

#ifndef __noreturn
#  define __noreturn	__attribute__((noreturn))
#endif

#ifndef __cold
#  define __cold	__attribute__((cold))
#endif

#define min(a, b) ({			\
	__typeof__(a) _a = (a);		\
	__typeof__(b) _b = (b);		\
	_a < _b ? _a : _b;		\
})

#define max(a, b) ({			\
	__typeof__(a) _a = (a);		\
	__typeof__(b) _b = (b);		\
	_a > _b ? _a : _b;		\
})

#define roundup(x, y) ({		\
	__typeof__(y) _y = (y);		\
	(((x) + _y - 1) / _y) * _y;	\
})

#define ARRAY_SIZE(A)		(sizeof(A) / sizeof((A)[0]))

#define DIV_ROUND_UP(n, d)	(((n) + (d) - 1) / (d))

static inline bool is_power_of_2(unsigned long n)
{
	return n != 0 && ((n & (n - 1)) == 0);
}

static inline int ilog2(unsigned long n)
{
	return (8 * sizeof(n) - 1) - __builtin_clzl(n);
}

/* ========== Endianness conversion ========== */

#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
#  define cpu_to_le16(v)	((__force __le16)(u16)(v))
#  define le16_to_cpu(v)	((__force u16)(__le16)(v))
#  define cpu_to_le32(v)	((__force __le32)(u32)(v))
#  define le32_to_cpu(v)	((__force u32)(__le32)(v))
#  define cpu_to_le64(v)	((__force __le64)(u64)(v))
#  define le64_to_cpu(v)	((__force u64)(__le64)(v))
#else
#  define cpu_to_le16(v)	((__force __le16)__builtin_bswap16(v))
#  define le16_to_cpu(v)	(__builtin_bswap16((__force u16)(v)))
#  define cpu_to_le32(v)	((__force __le32)__builtin_bswap32(v))
#  define le32_to_cpu(v)	(__builtin_bswap32((__force u32)(v)))
#  define cpu_to_le64(v)	((__force __le64)__builtin_bswap64(v))
#  define le64_to_cpu(v)	(__builtin_bswap64((__force u64)(v)))
#endif

#endif /* COMMON_COMMON_DEFS_H */
