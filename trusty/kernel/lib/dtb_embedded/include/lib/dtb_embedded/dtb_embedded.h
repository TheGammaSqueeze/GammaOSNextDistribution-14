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
#include <assert.h>
#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

struct dtb_embedded_iterator;

/**
 * dtb_embedded_iterator_new() - Create an iterator for the embedded dtbs
 * @piter:     Pointer to a pointer to store the allocated iterator.
 *
 * Return:    %NO_ERROR in case of success, %ERR_NO_MEMORY or another error code
 *            otherwise. It's the caller's responsibility to free the iterator
 *            with dtb_embedded_iterator_free.
 */
int dtb_embedded_iterator_new(struct dtb_embedded_iterator** piter);

/**
 * dtb_embedded_iterator_free() - Free an embedded dtb iterator
 *
 * @piter:    Pointer to the iterator to free.
 *
 * The function will set @piter to %NULL to prevent double frees.
 */
void dtb_embedded_iterator_free(struct dtb_embedded_iterator** piter);

/**
 * dtb_embedded_iterator_reset() - Reset an iterator to the first embedded dtb
 *
 * @iter:    The pointer to the iterator to reset
 */
void dtb_embedded_iterator_reset(struct dtb_embedded_iterator* iter);

/**
 * dtb_embedded_iterator_next() - Advance an iterator to the next embedded dtb
 *
 * @iter:         The pointer to the iterator to advance
 * @dtb:          Out parameter for the next dtb. This pointer's lifetime is
 *                unrelated to the iterator's and is always valid.
 * @dtb_size:     Out parameter for the next dtb's size
 *
 * Return:        NO_ERROR if the iterator is advanced to the next dtb.
 *                ERR_OUT_OF_RANGE if the iterator initially points to the last
 *                dtb. All other errors indicate invalid embedded dtbs or
 *                arguments.
 */
int dtb_embedded_iterator_next(struct dtb_embedded_iterator* iter,
                               const void** dtb,
                               size_t* dtb_size);

__END_CDECLS
