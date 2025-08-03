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

#ifndef CHRE_PLATFORM_HEAP_BLOCK_HEADER_H_
#define CHRE_PLATFORM_HEAP_BLOCK_HEADER_H_

#include <cstddef>
#include <cstdint>

namespace chre {

/**
 * Header to store allocation details for tracking.
 * We use a union to ensure proper memory alignment.
 */
union HeapBlockHeader {
  struct {
    /**
     * Pointer to the next header (to form a linked list).
     * @see mFirstHeader
     */
    HeapBlockHeader *next = nullptr;

    //! The amount of memory in bytes allocated (not including header).
    uint32_t bytes;

    //! The ID of nanoapp requesting memory allocation.
    uint16_t instanceId;
  } data;

  //! Makes sure header is a multiple of the size of max_align_t
  max_align_t aligner;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_HEAP_BLOCK_HEADER_H_
