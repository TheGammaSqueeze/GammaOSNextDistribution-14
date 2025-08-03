/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#ifndef __REFCOUNT_H
#define __REFCOUNT_H

#include <arch/ops.h>
#include <compiler.h>
#include <limits.h>
#include <panic.h>

struct refcount {
    volatile int cnt;
};

typedef void (*refcount_destroy_func)(struct refcount* ref);

static inline __ALWAYS_INLINE void refcount_init(struct refcount* ref) {
    /* TODO: silly, but don't have atomic_set, or a clean
     * write barrier */
    atomic_and(&ref->cnt, 0);
    atomic_add(&ref->cnt, 1);
}

static inline __ALWAYS_INLINE void refcount_inc(struct refcount* ref) {
    if (atomic_add(&ref->cnt, 1) == INT_MAX) {
        panic("ref overflow\n");
    }
}

static inline __ALWAYS_INLINE void refcount_dec(struct refcount* ref,
                                                refcount_destroy_func destroy) {
    /* decerementing from 1? destroy */
    if (atomic_add(&ref->cnt, -1) == 1)
        destroy(ref);
}

#endif
