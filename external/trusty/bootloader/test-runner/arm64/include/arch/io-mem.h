/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#pragma once

/*
 * Defines primitives for accessing memory mapped devices.
 * As both VirtIO and PCI use little endian, and we support only
 * AArch64, we do not perform endianness conversions.
 */

#include <stdint.h>

#define rmb() __asm__ __volatile__("dmb ld" : : : "memory")
#define wmb() __asm__ __volatile__("dmb st" : : : "memory")

#define u(width) uint##width##_t

#define build_io(width, prefix, suffix)                                      \
    static inline u(width) io_read_##width(const volatile u(width) * addr) { \
        u(width) out;                                                        \
        __asm__ __volatile__("ldr" suffix " %" prefix "0, [%1]"              \
                             : "=r"(out)                                     \
                             : "r"(addr));                                   \
        rmb();                                                               \
        return out;                                                          \
    }                                                                        \
    static inline void io_write_##width(volatile u(width) * addr,            \
                                        u(width) val) {                      \
        wmb();                                                               \
        __asm__ __volatile__("str" suffix " %" prefix "0, [%1]"              \
                             :                                               \
                             : "rZ"(val), "r"(addr)                          \
                             : "memory");                                    \
    }

build_io(8, "w", "b");
build_io(16, "w", "h");
build_io(32, "w", "");
build_io(64, "x", "");

#undef u
#undef build_io
