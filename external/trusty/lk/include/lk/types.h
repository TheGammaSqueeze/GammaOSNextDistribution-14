/*
 * Copyright (c) 2008-2012 Travis Geiselbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#pragma once

#include <inttypes.h>
#include <limits.h>
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

typedef unsigned char uchar;
typedef unsigned short ushort;
typedef unsigned int uint;
typedef unsigned long ulong;
typedef unsigned char u_char;
typedef unsigned short u_short;
typedef unsigned int u_int;
typedef unsigned long u_long;

typedef int status_t;

typedef uintptr_t addr_t;
#define ADDR_MAX UINTPTR_MAX
#define PRIxADDR PRIxPTR
#define PRIdADDR PRIdPTR
typedef uintptr_t vaddr_t;
#define VADDR_MAX UINTPTR_MAX
#define PRIxVADDR PRIxPTR
#define PRIdVADDR PRIdPTR
typedef uintptr_t paddr_t;
#define PADDR_MAX UINTPTR_MAX
#define PRIxPADDR PRIxPTR
#define PRIdPADDR PRIdPTR

typedef int kobj_id;

typedef uint32_t lk_time_t;
typedef unsigned long long lk_time_ns_t;
#define INFINITE_TIME UINT32_MAX

/* The overflow here is intended to deal with timestamps near wrapping */
__attribute__((no_sanitize("unsigned-integer-overflow")))
static inline int64_t time_delta(lk_time_ns_t a, lk_time_ns_t b) {
    return (int64_t)(a - b);
}

static inline bool time_gte(lk_time_ns_t a, lk_time_ns_t b) {
    return time_delta(a, b) >= 0;
}

static inline bool time_lte(lk_time_ns_t a, lk_time_ns_t b) {
    return time_delta(a, b) <= 0;
}

static inline bool time_gt(lk_time_ns_t a, lk_time_ns_t b) {
    return time_delta(a, b) > 0;
}

static inline bool time_lt(lk_time_ns_t a, lk_time_ns_t b) {
    return time_delta(a, b) < 0;
}

enum handler_return {
    INT_NO_RESCHEDULE = 0,
    INT_RESCHEDULE,
};

typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;

typedef int8_t s8;
typedef int16_t s16;
typedef int32_t s32;
typedef int64_t s64;

typedef uint8_t u_int8_t;
typedef uint16_t u_int16_t;
typedef uint32_t u_int32_t;
typedef uint64_t u_int64_t;
