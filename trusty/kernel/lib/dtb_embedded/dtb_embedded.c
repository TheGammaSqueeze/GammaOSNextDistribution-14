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

#include <inttypes.h>
#include <lib/dtb_embedded/dtb_embedded.h>
#include <libfdt.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <lk/trace.h>
#include <stdint.h>
#include <uapi/uapi/err.h>

#define LOCAL_TRACE (0)

extern char __trusty_dtb_start;
extern char __trusty_dtb_end;

struct dtb_embedded_iterator {
    uintptr_t offset;
};

void dtb_embedded_iterator_reset(struct dtb_embedded_iterator* iter) {
    if (iter) {
        iter->offset = 0;
    }
}

int dtb_embedded_iterator_new(struct dtb_embedded_iterator** piter) {
    ASSERT(piter);

    struct dtb_embedded_iterator* iter = (struct dtb_embedded_iterator*)calloc(
            1, sizeof(struct dtb_embedded_iterator));
    if (!iter) {
        TRACEF("failed to allocate iterator\n");
        return ERR_NO_MEMORY;
    }

    dtb_embedded_iterator_reset(iter);
    *piter = iter;

    return NO_ERROR;
}

void dtb_embedded_iterator_free(struct dtb_embedded_iterator** piter) {
    ASSERT(piter);

    if (*piter) {
        free(*piter);
        *piter = NULL;
    }
}

int dtb_embedded_iterator_next(struct dtb_embedded_iterator* iter,
                               const void** dtb,
                               size_t* dtb_size) {
    if (!iter) {
        TRACEF("Invalid iterator\n");
        return ERR_INVALID_ARGS;
    }
    if (!dtb) {
        TRACEF("Invalid dtb pointer\n");
        return ERR_INVALID_ARGS;
    }
    if (!dtb_size) {
        TRACEF("Invalid dtb size pointer\n");
        return ERR_INVALID_ARGS;
    }

    /* Check that the iterator is within the embedded dtb range */
    uintptr_t end = (uintptr_t)&__trusty_dtb_end;
    uintptr_t start = (uintptr_t)&__trusty_dtb_start;
    uintptr_t total_size = end - start;
    if (iter->offset >= total_size) {
        TRACEF("Embedded dtb iterator at offset %" PRIuPTR "out of range\n",
               iter->offset);
        return ERR_OUT_OF_RANGE;
    }

    /* Validate the header of the next dtb */
    const void* dtb_start = (const void*)(start + iter->offset);
    int rc = fdt_check_header(dtb_start);
    if (rc < 0) {
        TRACEF("Embedded dtb at offset %" PRIuPTR
               " has an invalid header (%d)\n",
               iter->offset, rc);
        return ERR_BAD_STATE;
    }

    /*
     * Check that the dtb size is in the expected range. Each dtb's size is read
     * from its header plus 4 bytes from the symbol size inserted by
     * INCBIN_ALIGNED. Then we round up to 8 bytes since each individual dtb is
     * 8-byte aligned.
     */
    const size_t dtb_alignment = 8;
    const size_t sym_size = 4;
    size_t next_dtb_size =
            round_up(fdt_totalsize(dtb_start) + sym_size, dtb_alignment);
    if (next_dtb_size + iter->offset > total_size) {
        TRACEF("Embedded dtb at offset %" PRIuPTR " has invalid size %zu\n",
               iter->offset, next_dtb_size);
        return ERR_BAD_LEN;
    }
    iter->offset += next_dtb_size;
    *dtb = dtb_start;
    *dtb_size = next_dtb_size;
    LTRACEF("Found embedded dtb at offset %" PRIuPTR "\n", iter->offset);
    return NO_ERROR;
}
