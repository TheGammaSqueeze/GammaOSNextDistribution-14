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

#pragma once

/* Trusty-specific APIs for memory management */

#include <stdint.h>
#include <uapi/mm.h>

/* Don't use convenience macros here, it will polute the namespace. */
#ifdef __cplusplus
extern "C" {
#endif

/* Trusty specific. */
int prepare_dma(void* uaddr,
                uint32_t size,
                uint32_t flags,
                struct dma_pmem* pmem);
int finish_dma(void* uaddr, uint32_t size, uint32_t flags);

/* Utility Functions */

/**
 * prepare_input_output_dma() - helper utility for using the prepare_dma
 * syscall. prepare_dma can only be called once with an address.  If the same
 * buffer is being used for input and output, we should only call prepare_dma
 * once. This utility will handle address checking, making the prepare_dma
 * sycall, and filling in the appropriate dma_pmem.
 *
 * @input pointer to input buffer
 * @input_len length of input buffer
 * @output pointer to output buffer
 * @output_len length of output buffer
 * @input_pmem pointer to input dma descriptor
 * @output_pmem pointer to output dma descriptor
 *
 * Returns : LK error code
 */
int prepare_input_output_dma(void* input,
                             uint32_t input_len,
                             void* output,
                             uint32_t output_len,
                             struct dma_pmem* input_pmem,
                             struct dma_pmem* output_pmem);

/**
 * finish_input_output_dma() - helper utility for using the finish_dma
 * syscalls. Depending on the input/output address, only one prepare_dma might
 * have been called.  This utility handles calling finish_dma with the correct
 * flags and number of times.
 *
 * @input pointer to input buffer
 * @input_len length of input buffer
 * @output pointer to output buffer
 * @output_len length of output buffer
 *
 * Returns : LK error code
 */
int finish_input_output_dma(void* input,
                            uint32_t input_len,
                            void* output,
                            uint32_t output_len);

#ifdef __cplusplus
}
#endif
