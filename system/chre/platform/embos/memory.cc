/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre/platform/memory.h"
#include "chre/platform/shared/pal_system_api.h"

#include <cstdlib>

#include "RTOS.h"

namespace chre {

void *memoryAlloc(size_t size) {
  return OS_malloc(size);
}

void *palSystemApiMemoryAlloc(size_t size) {
  return OS_malloc(size);
}

void memoryFree(void *pointer) {
  OS_free(pointer);
}

void palSystemApiMemoryFree(void *pointer) {
  OS_free(pointer);
}

void *nanoappBinaryAlloc(size_t size, size_t /* alignment */) {
  return OS_malloc(size);
}

void nanoappBinaryFree(void *pointer) {
  OS_free(pointer);
}

void *nanoappBinaryDramAlloc(size_t size, size_t /* alignment */) {
  return OS_malloc(size);
}

void nanoappBinaryDramFree(void *pointer) {
  OS_free(pointer);
}

void *memoryAllocDram(size_t size) {
  return OS_malloc(size);
}

void memoryFreeDram(void *pointer) {
  OS_free(pointer);
}

}  // namespace chre
