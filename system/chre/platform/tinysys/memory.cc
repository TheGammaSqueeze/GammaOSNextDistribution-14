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

#include <cstddef>

#include "chre/platform/memory.h"
#include "chre/platform/shared/dram_vote_client.h"
#include "chre/platform/shared/memory.h"
#include "mt_alloc.h"
#include "mt_dma.h"
#include "portable.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "encoding.h"
#include "mt_heap.h"
#include "resource_req.h"
#include "sensorhub/heap.h"

#ifdef __cplusplus
}  // extern "C"
#endif

namespace chre {

// On tinysys voting/devoting dram are done automatically by the platform APIs
// so issueDramVote() should be a no-op.
void DramVoteClient::issueDramVote(bool /*enabled*/) {}

// no-op since the dma access is controlled by the kernel automatically
void forceDramAccess() {}

void nanoappBinaryFree(void *pointer) {
  aligned_free(pointer);
}

void nanoappBinaryDramFree(void *pointer) {
  aligned_dram_free(pointer);
}

void *memoryAllocDram(size_t size) {
  return pvPortDramMalloc(size);
}

void memoryFreeDram(void *pointer) {
  vPortDramFree(pointer);
}

void *palSystemApiMemoryAlloc(size_t size) {
  return memoryAlloc(size);
}

void palSystemApiMemoryFree(void *pointer) {
  memoryFree(pointer);
}

void *nanoappBinaryAlloc(size_t size, size_t alignment) {
  return aligned_malloc(size, alignment);
}

void *nanoappBinaryDramAlloc(size_t size, size_t alignment) {
  // aligned_dram_malloc() requires the alignment being multiple of
  // CACHE_LINE_SIZE (128 bytes), we will align to page size (4k)
  return aligned_dram_malloc(size, alignment);
}

void *memoryAlloc(size_t size) {
  void *address = heap_alloc(size);
  if (address == nullptr && size > 0) {
    // Try dram if allocation from sram fails.
    // DramVoteClient tracks the duration of the allocations falling back to
    // dram. The idea is that only transient allocations are allowed to fall
    // back to dram. Any long-lived allocation should be done explicitly via
    // corresponding memory allocation APIs.
    DramVoteClientSingleton::get()->incrementDramVoteCount();
    address = pvPortDramMalloc(size);

    // DRAM allocation failed too.
    if (address == nullptr) {
      DramVoteClientSingleton::get()->decrementDramVoteCount();
    }
  }
  return address;
}

void memoryFree(void *pointer) {
  if (isInDram(pointer)) {
    vPortDramFree(pointer);
    DramVoteClientSingleton::get()->decrementDramVoteCount();
  } else {
    heap_free(pointer);
  }
}
}  // namespace chre
