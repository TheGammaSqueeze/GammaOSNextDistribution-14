/*
 * Copyright (c) 2015 Google, Inc.
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

#include <compiler.h>
#include <string.h>
#include <sys/types.h>

__BEGIN_CDECLS;

/**
 * tc_memcmp() - Timing resistent memory compare
 *
 * @p1:  points to first memory area to compare
 * @p2:  points to second memory area to compare
 * @len: number of bytes to compare
 *
 * This function runs in constant time. Note, that return value
 * differ from memcmp.
 *
 * Return: 0 if first @len bytes in both areas are identical,
 *         non-zero otherwise.
 */
int tc_memcmp(const void* p1, const void* p2, size_t len);

/**
 *  tc_memset() - variant of memset that disables optimization
 *  so it is not optimized away.
 *
 *  @p - pointer to memory area to fill with @c
 *  @c - constant byte to fill memory area @p with
 *  @n - number of bytes to fill
 *
 *  Return: pointer to memory area @p.
 */
inline __OPTIMIZE(O0) void* tc_memset(void* p, int c, size_t n) {
    if (!p)
        return p;
    return memset(p, c, n);
}

__END_CDECLS;
