/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_SHARED_DLFCN_H_
#define CHRE_PLATFORM_SHARED_DLFCN_H_

//! This file is intended to be used when a platform doesn't provide a dlfcn.h
//! implementation so that dynamic loading support can be provided to nanoapps.

#include <cstdlib>

//! Indicates that the dlsym call is attempting to lookup the provided symbol
//! in another library (CHRE).
#ifndef RTLD_NEXT
#define RTLD_NEXT ((void *)-1L)
#endif  // RTLD_NEXT

#ifdef __cplusplus
extern "C" {
#endif

/**
 * This function parses, verifies, and loads a buffer containing an ELF
 * file, and returns an opaque handle for symbol lookup via dlsym.
 * Note that there is no requirement to pass in a 'buffer size' argument,
 * since all the necessary information is gathered from the headers
 * embedded in the elf file itself. The buffer is expected to contain the
 * entire elf file in memory (cannot be a FILE pointer, for example).
 *
 * @param elfBinary is a buffer containing the elf file
 * @param mapIntoTcm Indicates whether the elfBinary should be mapped into
 *     tightly coupled memory.
 */
void *dlopenbuf(void *elfBinary, bool mapIntoTcm);

/**
 * Returns a (function) pointer to the symbol named by the input argument.
 *
 * @return pointer to the symbol if it was found, nullptr if not found or
 * the handle was invalid
 */
void *dlsym(void *handle, const char *symbol);

/**
 * Invalidate the handle used for symbol lookup, and free up any
 * allocated memory.
 *
 * @return 0 on success
 */
int dlclose(void *handle);

#ifdef __cplusplus
}
#endif

#endif  // CHRE_PLATFORM_SHARED_DLFCN_H_
