/*
 * Copyright (c) 2022 Google Inc. All rights reserved
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

/*
 * ARM True Random Number Generator Firmware Interface
 * (https://developer.arm.com/documentation/den0098/latest).
 */

#include <lib/sm/smcall.h>

#define SMC_TRNG_CURRENT_MAJOR_VERSION 1

/**
 * enum trng_error - TRNG error code
 * @TRNG_ERROR_NOT_SUPPORTED:
 *         Operation is not supported by the current implementation.
 * @TRNG_ERROR_INVALID_PARAMETER:
 *         Invalid parameter. Conditions function specific.
 * @TRNG_ERROR_NO_ENTROPY:
 *         No entropy.
 */
enum trng_error {
    TRNG_ERROR_NOT_SUPPORTED = -1,
    TRNG_ERROR_INVALID_PARAMETER = -2,
    TRNG_ERROR_NO_ENTROPY = -3,
};

/**
 * SMC_FC_TRNG_VERSION - SMC opcode to return supported TRNG version
 *
 * Register arguments:
 *
 * * w1-w7:  Must be 0.
 *
 * Return:
 * * w0:     Major version bit[30:16], minor version in bit[15:0], bit[31] must
 *           be 0.
 * * w1-w3:  Must be 0.
 *
 * or
 *
 * * w0:     %TRNG_ERROR_NOT_SUPPORTED.
 */
#define SMC_FC_TRNG_VERSION SMC_FASTCALL_NR(SMC_ENTITY_STD, 0x50)

/**
 * SMC_FC_TRNG_FEATURES - SMC opcode to check optional feature support
 *
 * Register arguments:
 *
 * * w1:     TRNG function ID
 * * w2-w7:  Must be 0.
 *
 * Return:
 * * w0:     Function-specific features if the function is implemented.
 *
 * or
 *
 * * w0:     %TRNG_ERROR_NOT_SUPPORTED.
 */
#define SMC_FC_TRNG_FEATURES SMC_FASTCALL_NR(SMC_ENTITY_STD, 0x51)

/**
 * SMC_FC_GET_UUID - SMC opcode to retrieve the UUID of the TRNG back end.
 *
 * Register arguments:
 *
 * * w1-w7:  Must be 0.
 *
 * Return:
 * * w0:     UUID[31:0].
 * * w1:     UUID[63:32].
 * * w2:     UUID[95:64].
 * * w3:     UUID[127:96].
 *
 * or
 *
 * * w0:     %TRNG_ERROR_NOT_SUPPORTED.
 */
#define SMC_FC_TRNG_GET_UUID SMC_FASTCALL_NR(SMC_ENTITY_STD, 0x52)

/**
 * SMC_FC_TRNG_RND - SMC opcode to request N bits of entropy.
 *
 * Register arguments:
 *
 * * w1:     Bits of entropy requested, between 1 and 96.
 * * w2-w7:  Must be 0.
 *
 * Return:
 * * w0:     Must be 0.
 * * w1:     Entropy[95:64].
 * * w2:     Entropy[63:32].
 * * w3:     Entropy[31:0].
 *
 * or
 *
 * * w0:     One of &enum trng_error.
 * * w1-w3:  Must be 0.
 */
#define SMC_FC_TRNG_RND SMC_FASTCALL_NR(SMC_ENTITY_STD, 0x53)

/**
 * SMC_FC64_TRNG_RND - SMC opcode to request N bits of entropy.
 *
 * Register arguments:
 *
 * * x1:     Bits of entropy requested, between 1 and 192.
 * * x2-x7:  Must be 0.
 *
 * Return:
 * * x0:     Must be 0.
 * * x1:     Entropy[191:128].
 * * x2:     Entropy[127:64].
 * * x3:     Entropy[63:0].
 *
 * or
 *
 * * x0:     One of &enum trng_error.
 * * x1-x3:  Must be 0.
 */
#define SMC_FC64_TRNG_RND SMC_FASTCALL64_NR(SMC_ENTITY_STD, 0x53)
