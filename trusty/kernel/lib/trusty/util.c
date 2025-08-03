/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
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

#include <arch/mmu.h>
#include <err.h>
#include <uapi/mm.h>

#include "util.h"

bool is_accessible(uint32_t obj_flags, uint32_t req_flags) {
    req_flags &= MMAP_FLAG_PROT_MASK;
    return (req_flags && ((obj_flags & req_flags) == req_flags));
}

bool is_prot_valid(uint32_t mmap_prot) {
    if (!(mmap_prot & MMAP_FLAG_PROT_MASK)) {
        /* unknown flags set */
        return false;
    }

    if (mmap_prot & MMAP_FLAG_PROT_EXEC) {
        /* exec flags is not supported */
        return false;
    }

    if (mmap_prot & MMAP_FLAG_PROT_WRITE) {
        if (!(mmap_prot & MMAP_FLAG_PROT_READ)) {
            /* write only memory is not supported */
            return false;
        }
    }

    return true;
}

status_t xlat_flags(uint32_t access_prot,
                    uint32_t mmap_prot,
                    uint* arch_mmu_flags) {
    if (!is_prot_valid(mmap_prot)) {
        return ERR_INVALID_ARGS;
    }

    if (!is_accessible(access_prot, mmap_prot)) {
        return ERR_ACCESS_DENIED;
    }

    *arch_mmu_flags |= ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE;

    if (!(mmap_prot & MMAP_FLAG_PROT_WRITE)) {
        *arch_mmu_flags |= ARCH_MMU_FLAG_PERM_RO;
    }

    if (mmap_prot & MMAP_FLAG_PROT_MTE) {
        *arch_mmu_flags |= ARCH_MMU_FLAG_TAGGED;
    }

    return NO_ERROR;
}
