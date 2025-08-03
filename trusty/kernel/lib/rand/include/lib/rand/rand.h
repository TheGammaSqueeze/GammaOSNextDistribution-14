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

#pragma once

#include <compiler.h>
#include <sys/types.h>

__BEGIN_CDECLS;

/**
 * rand_get_size() - Get Random Integer
 *
 * @max:  inclusive upper bound for the random integer
 *
 * Return: a pseudorandom integer in the range 0 to max inclusive
 *         (i.e., the mathematical range [0, max]).
 */
size_t rand_get_size(size_t max);

/**
 * rand_get_bytes() - Get Random Bytes
 *
 * @buf:  points to bytes array that will contain random bytes
 * @len:  number of random bytes
 *
 * Return: 0 if buf is filled up with len random bytes,
 *         non-zero otherwise.
 */
int rand_get_bytes(uint8_t* buf, size_t len);

/**
 * add_entropy() - Add entropy to CSPRNG
 *
 * @buf:  points to data from entropy source
 * @len:  number of bytes from entropy source
 *
 * Return: 0 if new entropy is added to CSPRNG,
 *         non-zero otherwise.
 */
int rand_add_entropy(const uint8_t* buf, size_t len);

__END_CDECLS;
