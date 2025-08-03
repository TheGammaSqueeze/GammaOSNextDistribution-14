/*
 * Copyright (c) 2021, Google Inc. All rights reserved
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

#include <stdbool.h>
#include <stdint.h>

/**
 * mmutest_arch_rodata_pnx() - Test that rodata section is mapped pnx
 *
 * Return:
 * * ERR_FAULT - if rodata is not executable
 * * 0         - if rodata is executable
 */
int mmutest_arch_rodata_pnx(void);

/**
 * mmutest_arch_data_pnx() - Test that data section is mapped pnx
 *
 * Return:
 * * ERR_FAULT - if data is not executable
 * * 0         - if data is executable
 */
int mmutest_arch_data_pnx(void);

/**
 * mmutest_arch_rodata_ro() - Test that rodata section is mapped read-only
 *
 * Return:
 * * ERR_FAULT - if rodata is not writable
 * * 1         - if write to rodata is silently dropped
 * * 0         - if rodata is writable
 */
int mmutest_arch_rodata_ro(void);

/**
 * mmutest_arch_store_uint32() - Test if ptr is writable
 * @ptr:  Memory location to test
 * @user: Use unprivileged store
 *
 * Return:
 * * ERR_FAULT   - if ptr is not writable
 * * ERR_GENERIC - if ptr is not readable
 * * 2           - if write does not fault, but data is lost on readback from
 *                 memory
 * * 1           - if write does not fault, but data is lost on readback from
 *                 cache
 * * 0           - if ptr is writable
 */
int mmutest_arch_store_uint32(uint32_t* ptr, bool user);

/**
 * mmutest_arch_pan_supported() - Test is FEAT_PAN indicates PAN support.
 *
 * Return:
 * * true - if PAN is supported.
 * * false - if PAN is not supported.
 */
bool mmutest_arch_pan_supported(void);

/**
 * mmutest_arch_pan_enabled() - Test if PAN is supported and enabled.
 *
 * Return:
 * * true - PAN is supported and has been enabled.
 * * false - PAN is not enabled or not supported.
 */
bool mmutest_arch_pan_enabled(void);
