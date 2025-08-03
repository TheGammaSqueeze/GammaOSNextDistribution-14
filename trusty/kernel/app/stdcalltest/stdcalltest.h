/*
 * Copyright (c) 2020 Google, Inc.
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

/**
 * SMC_SC_TEST_VERSION - Return supported test API version.
 * @r1: Version supported by client.
 *
 * Returns version supported by trusty test.
 *
 * If multiple versions are supported, the client should start by calling
 * SMC_SC_TEST_VERSION with the largest version it supports. Trusty will then
 * return a version it supports. If the client does not support the version
 * returned by trusty and the version returned is less than the version
 * requested, repeat the call with the largest supported version less than the
 * last returned version.
 */
#define SMC_SC_TEST_VERSION SMC_STDCALL_NR(SMC_ENTITY_TEST, 0)

/**
 * SMC_SC_TEST_SHARED_MEM_RW - Test shared memory buffer.
 * @r1/r2:  Shared memory id.
 * @r3:     Size.
 *
 * Check that buffer contains the 64 bit integer sqequnce [0, 1, 2, ...,
 * @r3 / 8 - 1] and modify sequence to [@r3, @r3 - 1, @r3 - 2, ...,
 * @r3 - (@r3 / 8 - 1)].
 *
 * Return: 0 on success. SM_ERR_INVALID_PARAMETERS is buffer does not contain
 * expected input pattern. SM_ERR_INTERNAL_FAILURE if @r1/r2 could not be
 * mapped.
 */
#define SMC_SC_TEST_SHARED_MEM_RW SMC_STDCALL_NR(SMC_ENTITY_TEST, 1)

/**
 * SMC_SC_TEST_CLOBBER_SVE - Test save and restore of SVE/SIMD registers during
 * an TFTF <-> TF-A <-> Trusty roundtrip for all participants when multiple cpus
 * are involved.
 *
 * Return: 0 on success. 1 on "technical" error. 2 if registers have not
 * recovered expected value.
 */
#define SMC_SC_TEST_CLOBBER_SVE SMC_STDCALL_NR(SMC_ENTITY_TEST, 2)

#define TRUSTY_STDCALLTEST_API_VERSION 1
