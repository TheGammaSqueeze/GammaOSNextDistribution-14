/*
 * Copyright (c) 2021 Google, Inc.
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

#include <assert.h>
#include <debug.h>
#include <platform/random.h>
#include <rand.h>

/*
 * default implementations of these routines, if the platform code
 * chooses not to implement.
 */

__WEAK void platform_random_get_bytes(uint8_t *buf, size_t len) {
    /* Print a warning about using this, but only once per boot */
    static bool printed_warning = false;
    if (unlikely(!printed_warning)) {
        dprintf(CRITICAL,
                "FAKE RNG implementation MUST be replaced with the REAL one\n");
        printed_warning = true;
    }

    DEBUG_ASSERT(buf);
    while (len) {
        /* lk's rand() returns 32 pseudo random bits */
        uint32_t val = (uint32_t) rand();
        size_t todo = len;
        for (size_t i = 0; i < sizeof(val) && i < todo; i++, len--) {
            *buf++ = val & 0xff;
            val >>= 8;
        }
    }
}
