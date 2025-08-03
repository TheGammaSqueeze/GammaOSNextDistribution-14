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

/**
 * @file stdlib_wrapper.cc
 *
 * This file provides nanoapps with wrapper functions for standard library
 * functions in the standard library functions that are not supported alongside
 * CHRE, which either redirect the function to an analogous CHRE function, or
 * fail silently. The targeted usage of this is expected to be only for third-
 * party or generated functions, where changes to either the upstream third-
 * party code or the code generators might not be straightforward. It is
 * expected that the nanoapp developers are aware of the 'fail silently' clause
 * in the wrappers, and handle those cases appropriately.
 */

#include <stdio.h>
#include <stdlib.h>
#include <cinttypes>
#include <cstdlib>
#include <new>

#include "chre/util/nanoapp/assert.h"

#include "chre_api/chre.h"

#if defined(stderr) && !defined(_CSTD)
// Provides a definition for stderr when the macro has been defined, but the
// file has been externed. Some platforms might define their own macros for
// stderr (vs the glibc style of matching the macro to the filename), in which
// case we have to guard against those definitions as well (eg: _CSTD check
// above).
FILE *stderr = NULL;
#endif

void *malloc(size_t size) {
  // On platforms where size(size_t) might be 8 bytes, we need a cast to
  // maintain adherence to CHRE's heap alloc API. The size check to reject
  // requests of size > 4Gb could be used for debugging, though any requests
  // that even remotely approach this limit is bound to fail anyway.
  return size > UINT32_MAX ? nullptr
                           : chreHeapAlloc(static_cast<uint32_t>(size));
}

void free(void *ptr) {
  chreHeapFree(ptr);
}

void *realloc(void * /*ptr*/, size_t /*newSize*/) {
  // realloc() is not supported, verify that there's no call to it!
  CHRE_ASSERT(false);
  return NULL;
}

void exit(int exitCode) {
  chreAbort(static_cast<uint32_t>(exitCode));
  // Add an explicit forever-loop to bypass compilation warnings on platforms
  // that might have defined exit with a noreturn tag. The loop shouldn't ever
  // execute, since abort terminates the program.
  while (42)
    ;
}

void abort(void) {
  exit(CHRE_ERROR);
}

int fprintf(FILE * /*stream*/, const char * /*fmt*/, ...) {
  return 0;
}

size_t fwrite(const void * /*ptr*/, size_t /*size*/, size_t /*count*/,
              FILE * /*stream*/) {
  return 0;
}

void operator delete(void * /*ptr*/) {
  CHRE_ASSERT(false);
}

void operator delete(void * /*ptr*/, std::size_t /*sz*/) {
  CHRE_ASSERT(false);
}

void operator delete(void * /*ptr*/, std::align_val_t /*al*/) {
  CHRE_ASSERT(false);
}

void operator delete(void * /*ptr*/, std::size_t /*sz*/,
                     std::align_val_t /*al*/) {
  CHRE_ASSERT(false);
}

void operator delete[](void * /*ptr*/) {
  CHRE_ASSERT(false);
}

void operator delete[](void * /*ptr*/, std::size_t /*sz*/) {
  CHRE_ASSERT(false);
}

void operator delete[](void * /*ptr*/, std::align_val_t /*al*/) {
  CHRE_ASSERT(false);
}

void operator delete[](void * /*ptr*/, std::size_t /*sz*/,
                       std::align_val_t /*al*/) {
  CHRE_ASSERT(false);
}

void *operator new[](std::size_t /* count */) noexcept(false) {
  // We return a static pointer here since in development build, using new will
  // lead to crash so the returned pointer is not important.
  CHRE_ASSERT(false);
  return reinterpret_cast<void *>(0xDEADBEEF);
}

void *operator new[](std::size_t /* count */,
                     std::align_val_t /* al */) noexcept(false) {
  CHRE_ASSERT(false);
  return reinterpret_cast<void *>(0xDEADBEEF);
}