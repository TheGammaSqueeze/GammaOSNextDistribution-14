/*
 * Copyright (c) 2020 Google Inc. All rights reserved
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
#include <lib/rand/rand.h>
#include <platform/random.h>
#include <rand.h>
#include <stdint.h>
#include <sys/types.h>

size_t rand_get_size(size_t max) {
    size_t rand_size;
    rand_get_bytes((uint8_t*)(&rand_size), sizeof(rand_size));
    if (max == SIZE_MAX) {
        return rand_size;
    }
    size_t retry = SIZE_MAX / (max + 1) * (max + 1);
    while (rand_size >= retry) {
        rand_get_bytes((uint8_t*)(&rand_size), sizeof(rand_size));
    }
    return rand_size % (max + 1);
}

int rand_get_bytes(uint8_t* buf, size_t len) {
    DEBUG_ASSERT(buf);
    platform_random_get_bytes(buf, len);
    return 0;
}
