/*
 * Copyright (c) 2021, Google, Inc. All rights reserved
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

#include <lib/trusty/trusty_app.h>
#include <lk/init.h>

/*
 * Add the allowed ranges for the apploader test apps
 */
#define DELETE_PAREN(args...) args
#define MMIO_TEST_APP(name, uuid) \
    TRUSTY_APP_MMIO_ALLOWED_RANGE(name, DELETE_PAREN uuid, 0x10001000, 0x1000)

MMIO_TEST_APP(allowed_test_app_range,
              ({0x66860d77,
                0x662a,
                0x4ba8,
                {0x90, 0x70, 0xc8, 0xb0, 0x49, 0x06, 0x63, 0xbc}}));
MMIO_TEST_APP(bad_range_low_test_app_range,
              ({0xbf18f7ff,
                0x5664,
                0x4ecc,
                {0xba, 0x5b, 0xed, 0x0e, 0x5f, 0x70, 0x8d, 0xfe}}));
MMIO_TEST_APP(bad_range_high_test_app_range,
              ({0xda528e05,
                0xd037,
                0x4fbb,
                {0x92, 0xd5, 0x5c, 0x3f, 0x47, 0xb9, 0x56, 0xa2}}));

static void add_test_app_ranges(uint level) {
    trusty_app_allow_mmio_range(&allowed_test_app_range);
    trusty_app_allow_mmio_range(&bad_range_low_test_app_range);
    trusty_app_allow_mmio_range(&bad_range_high_test_app_range);
}

LK_INIT_HOOK(libtrusty_test_app_ranges,
             add_test_app_ranges,
             LK_INIT_LEVEL_APPS - 1);
