/* SPDX-License-Identifier: MIT */
/*
 * WIN32 compat definitions for libfsverity and the 'fsverity' program
 *
 * Copyright 2020 Microsoft
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
#ifndef COMMON_WIN32_DEFS_H
#define COMMON_WIN32_DEFS_H

/* Some minimal definitions to allow the digest/sign commands to run under Windows */

/* All file reads we do need this flag on _WIN32 */
#ifndef O_BINARY
#  define O_BINARY 0
#endif

#ifdef _WIN32

#include <stdint.h>
#include <inttypes.h>

#ifndef ENOPKG
#   define ENOPKG 65
#endif

#ifndef __cold
#  define __cold
#endif

/* For %zu in printf() */
#ifndef __printf
#  define __printf(fmt_idx, vargs_idx) \
       __attribute__((format(gnu_printf, fmt_idx, vargs_idx)))
#endif

typedef __signed__ char __s8;
typedef unsigned char __u8;
typedef __signed__ short __s16;
typedef unsigned short __u16;
typedef __signed__ int __s32;
typedef unsigned int __u32;
typedef __signed__ long long  __s64;
typedef unsigned long long  __u64;
typedef __u16 __le16;
typedef __u16 __be16;
typedef __u32 __le32;
typedef __u32 __be32;
typedef __u64 __le64;
typedef __u64 __be64;

#endif /* _WIN32 */

#endif /* COMMON_WIN32_DEFS_H */
