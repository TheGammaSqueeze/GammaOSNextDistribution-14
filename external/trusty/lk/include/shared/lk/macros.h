/*
 * Copyright (c) 2008-2014 Travis Geiselbrecht
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
#ifndef __LK_MACROS_H
#define __LK_MACROS_H

#include <stddef.h>
#include <stdint.h>

#define MIN(a, b) (((a) < (b)) ? (a) : (b))
#define MAX(a, b) (((a) > (b)) ? (a) : (b))

__attribute__((no_sanitize("unsigned-integer-overflow")))
static inline uintptr_t round_up(uintptr_t val, size_t alignment) {
    return (val + (alignment - 1)) & ~(alignment - 1);
}

static inline uintptr_t round_down(uintptr_t val, size_t alignment) {
    return val & ~(alignment - 1);
}

#define ROUND_UP(n, d) (((n) + (size_t)(d) - 1) & ~((size_t)(d) - 1))
#define DIV_ROUND_UP(n,d) (((n) + (d) - 1) / (d))

static inline uintptr_t align(uintptr_t ptr, size_t alignment) {
    return round_up(ptr, alignment);
}

#define IS_ALIGNED(a, b) (!(((uintptr_t)(a)) & (((uintptr_t)(b))-1)))

#define containerof(ptr, type, member) \
    ((type *)((uintptr_t)(ptr) - offsetof(type, member)))

#define containerof_null_safe(ptr, type, member) ({ \
    __typeof__(ptr) __ptr = ptr;\
    type *__t;\
    if(__ptr)\
        __t = containerof(__ptr, type, member);\
    else\
        __t = (type *)0;\
    __t;\
})

#endif

