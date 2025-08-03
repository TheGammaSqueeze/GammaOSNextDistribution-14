/*
 * Copyright (c) 2023, Google Inc. All rights reserved
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

/* Tests the cache line lengths for the d-caches and compares against CACHE_LINE.
 * This also prints some info about the cache types and line lengths.
 * Since this uses ARM64 system registers, it is skipped for other arches.
 */

#include <arch/ops.h>
#include <inttypes.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>
#include <stdint.h>
#include <stdio.h>

/* Architecturally there can be no more than 7 cache levels */
#define MAX_CACHE_LEVELS 7

/* Cache-type bits in CLIDR_EL1 */
#define CTYPE_NO_CACHE 0x0
#define CTYPE_I_CACHE 0x1
#define CTYPE_D_CACHE 0x2
#define CTYPE_ID_CACHE 0x3
#define CTYPE_UNIFIED_CACHE 0x4

#ifndef ARCH_ARM64
TEST(cachetest, DISABLED_line_length) {}
#else
/* This tests that CACHE_LINE is configured appriately for the system, while
 * also displaying some basic info on the caches.
 */
TEST(cachetest, line_length) {
    uint64_t cache_types = ARM64_READ_SYSREG(CLIDR_EL1);
    uint32_t max_dline = 0;

    for (int level = 0; level < MAX_CACHE_LEVELS; level++) {
        /* Determine the cache type for the level */
        const uint8_t ctype = cache_types & 0x7;
        cache_types >>= 3;

        /* Ignore if 'no-cache' at this level */
        if (ctype != CTYPE_NO_CACHE) {
            uint32_t dline_size = 0, iline_size = 0;

            /* Read data or unified cache line size */
            if ((ctype & CTYPE_D_CACHE) || ctype == CTYPE_UNIFIED_CACHE) {
                ARM64_WRITE_SYSREG(CSSELR_EL1, (uint64_t)(level << 1u));
                dline_size = 1u << ((ARM64_READ_SYSREG(CCSIDR_EL1) & 0x7) + 4);

                if (dline_size > max_dline)
                    max_dline = dline_size;
            }

            /* Read instruction cache line size */
            if (ctype & CTYPE_I_CACHE) {
                ARM64_WRITE_SYSREG(CSSELR_EL1,
                                   (uint64_t)((level << 1u) | 0x1u));
                iline_size = 1u << ((ARM64_READ_SYSREG(CCSIDR_EL1) & 0x7) + 4);
            }

            /* Print summary of the line size(s) */
            switch (ctype) {
            case CTYPE_I_CACHE:
                trusty_unittest_printf(
                        "[   DATA   ] level %d line:           icache=%" PRIu32
                        "\n",
                        level + 1, iline_size);
                break;
            case CTYPE_D_CACHE:
                trusty_unittest_printf(
                        "[   DATA   ] level %d line: dcache=%" PRIu32 "\n",
                        level + 1, dline_size);
                break;
            case CTYPE_ID_CACHE:
                trusty_unittest_printf(
                        "[   DATA   ] level %d line: dcache=%" PRIu32
                        " icache=%" PRIu32 "\n",
                        level + 1, dline_size, iline_size);
                break;
            case CTYPE_UNIFIED_CACHE:
                trusty_unittest_printf(
                        "[   DATA   ] level %d line:      unified=%" PRIu32
                        "\n",
                        level + 1, dline_size);
                break;
            }

        } else {
            /* Stop at first no-cache entry */
            break;
        }
    }

    EXPECT_GE(CACHE_LINE, max_dline,
              "cpu%u: max d-cache line exceeds CACHE_LINE (%" PRIu32 " > %u)\n",
              arch_curr_cpu_num(), max_dline, CACHE_LINE);
}
#endif

PORT_TEST(cachetest, "com.android.kernel.cachetest");
