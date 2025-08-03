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

#include "chpp/memory.h"

#include <stdatomic.h>
#include <stddef.h>
#include <stdlib.h>

#ifdef CHPP_LINUX_MEMORY_DEBUG
#include "chpp/log.h"
#endif  // CHPP_LINUX_MEMORY_DEBUG
#include "chpp/macros.h"
#include "chpp/platform/utils.h"

//! A header used to track the amount of allocated memory.
struct ChppAllocHeader {
  size_t bytes;
};

static atomic_size_t gTotalAllocBytes = 0;

void *chppMalloc(const size_t size) {
  void *ptr = NULL;
  if (size != 0) {
    ptr = malloc(sizeof(struct ChppAllocHeader) + size);

    if (ptr != NULL) {
      struct ChppAllocHeader *header = (struct ChppAllocHeader *)ptr;
      header->bytes = size;
      header++;
      ptr = header;

      gTotalAllocBytes += size;
    }
  }

#ifdef CHPP_LINUX_MEMORY_DEBUG
  CHPP_LOGI("%s: size %zu total (after malloc) %zu", __func__, size,
            gTotalAllocBytes);
#endif  // CHPP_LINUX_MEMORY_DEBUG

  return ptr;
}

void chppFree(void *ptr) {
  if (ptr != NULL) {
    struct ChppAllocHeader *header = (struct ChppAllocHeader *)ptr;
    header--;
    size_t size = header->bytes;
    gTotalAllocBytes -= size;

#ifdef CHPP_LINUX_MEMORY_DEBUG
    CHPP_LOGI("%s: size %zu total (after free) %zu", __func__, size,
              gTotalAllocBytes);
#endif  // CHPP_LINUX_MEMORY_DEBUG

    free(header);
  }
}

void *chppRealloc(void *oldPtr, const size_t newSize, const size_t oldSize) {
  UNUSED_VAR(oldSize);
  void *ptr = NULL;
  if (newSize != oldSize) {
    struct ChppAllocHeader *oldHeader = (struct ChppAllocHeader *)oldPtr;
    oldHeader--;

    void *reallocPtr =
        realloc(oldHeader, newSize + sizeof(struct ChppAllocHeader));
    if (reallocPtr != NULL) {
      struct ChppAllocHeader *newHeader = (struct ChppAllocHeader *)reallocPtr;
      newHeader->bytes = newSize;
      newHeader++;
      ptr = newHeader;

      if (newSize > oldSize) {
        gTotalAllocBytes += (newSize - oldSize);
      } else {
        gTotalAllocBytes -= (oldSize - newSize);
      }
    }
  }

#ifdef CHPP_LINUX_MEMORY_DEBUG
  CHPP_LOGI("%s: size %zu total (after realloc) %zu", __func__, newSize,
            gTotalAllocBytes);
#endif  // CHPP_LINUX_MEMORY_DEBUG

  return ptr;
}

void chppClearTotalAllocBytes(void) {
  gTotalAllocBytes = 0;
}

size_t chppGetTotalAllocBytes(void) {
  return gTotalAllocBytes;
}
