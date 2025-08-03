/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <sys/mman.h>

#include <lk/err_ptr.h>
#include <stddef.h>
#include <stdint.h>
#include <trusty/sys/mman.h>
#include <trusty_syscalls.h>
#ifdef HWASAN_ENABLED
#include <lib/hwasan/hwasan_shadow.h>
#endif /* HWASAN_ENABLED */

#include <trusty_log.h>
#define TLOG_TAG "mman"

void* mmap(void* uaddr,
           size_t size,
           int prot,
           int flags,
           int handle,
           off_t offset) {
    void* result;

    if (offset != 0) {
        return MAP_FAILED;
    }

    /*
     * or the flags together for now since the syscall doesn't have enough
     * arguments and now that we have real mappable handles, we have to dispatch
     * on the flags to switch between regions and handles
     */
    result = (void*)_trusty_mmap(uaddr, size, (uint32_t)prot | flags,
                                 (int32_t)handle);
    if (IS_ERR(result)) {
        return MAP_FAILED;
    }
#ifdef HWASAN_ENABLED
    /*
     * Assume _trusty_mmap() call above gives us a valid region of memory that
     * is mapped into user address space. For such regions hwasan_tag_memory()
     * can not fail.
     */
    result = hwasan_tag_memory(result, size);
#endif
    return result;
}

int munmap(void* uaddr, size_t size) {
#ifdef HWASAN_ENABLED
    /* HWASan memory will be unmapped. No need to worry about untagging it. */
    uaddr = hwasan_remove_ptr_tag(uaddr);
#endif
    return _trusty_munmap(uaddr, size);
}

int prepare_dma(void* uaddr,
                uint32_t size,
                uint32_t flags,
                struct dma_pmem* pmem) {
#ifdef HWASAN_ENABLED
    uaddr = hwasan_remove_ptr_tag(uaddr);
#endif
    return _trusty_prepare_dma(uaddr, size, flags, pmem);
}

int finish_dma(void* uaddr, uint32_t size, uint32_t flags) {
#ifdef HWASAN_ENABLED
    uaddr = hwasan_remove_ptr_tag(uaddr);
#endif
    return _trusty_finish_dma(uaddr, size, flags);
}

int prepare_input_output_dma(void* input,
                             uint32_t input_len,
                             void* output,
                             uint32_t output_len,
                             struct dma_pmem* input_pmem,
                             struct dma_pmem* output_pmem) {
    if (input == output && input_len != output_len) {
        return ERR_INVALID_ARGS;
    }

    int rc = NO_ERROR;
    if (input == output && input_len == output_len) {
        rc = prepare_dma(input, input_len, DMA_FLAG_BIDIRECTION, input_pmem);
        if (rc < 0) {
            TLOGE("Couldn't prepare input/output dma - rc(%d)\n", rc);
            return rc;
        }
        *output_pmem = *input_pmem;
    } else {
        rc = prepare_dma(input, input_len, DMA_FLAG_TO_DEVICE, input_pmem);
        if (rc < 0) {
            TLOGE("Couldn't prepare input dma - rc(%d)\n", rc);
            return rc;
        }
        rc = prepare_dma(output, output_len, DMA_FLAG_FROM_DEVICE, output_pmem);
        if (rc < 0) {
            TLOGE("Couldn't prepare output dma - rc(%d)\n", rc);
            finish_dma(input, input_len, DMA_FLAG_TO_DEVICE);  // Clean DMA
            return rc;
        }
    }
    return NO_ERROR;
}

int finish_input_output_dma(void* input,
                            uint32_t input_len,
                            void* output,
                            uint32_t output_len) {
    if (input == output && input_len != output_len) {
        return ERR_INVALID_ARGS;
    }

    if (input == output && input_len == output_len) {
        return finish_dma(input, input_len, DMA_FLAG_BIDIRECTION);
    } else {
        int rc_in = finish_dma(input, input_len, DMA_FLAG_TO_DEVICE);
        int rc_out = finish_dma(output, output_len, DMA_FLAG_FROM_DEVICE);

        /* Return an error if it exists */
        return rc_in == NO_ERROR ? rc_out : rc_in;
    }
}
