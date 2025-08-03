/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_EXTERNAL_MBEDTLS_MEMORY_H_
#define CHRE_EXTERNAL_MBEDTLS_MEMORY_H_

/**
 * This header file provides wrappers around the CHRE memory allocation
 * function @ref chreMemoryAlloc for use within the MbedTLS library.
 */

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Allocates a contiguous zeroed out array with each element having the given
 * item size.
 *
 * @param nItems Number of items to allocate.
 * @param itemSize Size of an individual item.
 *
 * @return A pointer to the allocated memory if successful, nullptr otherwise.
 */
void *mbedtlsMemoryCalloc(size_t nItems, size_t itemSize);

/**
 * Frees a previously allocated memory.
 *
 * @param ptr Pointer to be freed.
 */
void mbedtlsMemoryFree(void *ptr);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // CHRE_EXTERNAL_MBEDTLS_MEMORY_H_