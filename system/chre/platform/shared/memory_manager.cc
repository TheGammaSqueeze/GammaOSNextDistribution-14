/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/platform/memory_manager.h"

#include "chre/platform/assert.h"
#include "chre/util/system/debug_dump.h"

namespace chre {

void *MemoryManager::nanoappAlloc(Nanoapp *app, uint32_t bytes) {
  HeapBlockHeader *header = nullptr;
  if (bytes > 0) {
    if (mAllocationCount >= kMaxAllocationCount) {
      LOGE("Failed to allocate memory from Nanoapp ID %" PRIu16
           ": allocation count exceeded limit.",
           app->getInstanceId());
    } else if ((bytes > kMaxAllocationBytes) ||
               ((mTotalAllocatedBytes + bytes) > kMaxAllocationBytes)) {
      LOGE("Failed to allocate memory from Nanoapp ID %" PRIu16
           ": not enough space.",
           app->getInstanceId());
    } else {
      header = static_cast<HeapBlockHeader *>(
          doAlloc(app, sizeof(HeapBlockHeader) + bytes));

      if (header != nullptr) {
        app->setTotalAllocatedBytes(app->getTotalAllocatedBytes() + bytes);
        mTotalAllocatedBytes += bytes;
        if (mTotalAllocatedBytes > mPeakAllocatedBytes) {
          mPeakAllocatedBytes = mTotalAllocatedBytes;
        }
        mAllocationCount++;
        app->linkHeapBlock(header);
        header->data.bytes = bytes;
        header->data.instanceId = app->getInstanceId();
        header++;
      }
    }
  }
  return header;
}

void MemoryManager::nanoappFree(Nanoapp *app, void *ptr) {
  if (ptr != nullptr) {
    HeapBlockHeader *header = static_cast<HeapBlockHeader *>(ptr);
    header--;

    // TODO: Clean up API contract of chreSendEvent to specify nanoapps can't
    // release ownership of data to other nanoapps so a CHRE_ASSERT_LOG can be
    // used below and the code can return.
    if (app->getInstanceId() != header->data.instanceId) {
      LOGW("Nanoapp ID=%" PRIu16 " tried to free data from nanoapp ID=%" PRIu16,
           app->getInstanceId(), header->data.instanceId);
    }

    size_t nanoAppTotalAllocatedBytes = app->getTotalAllocatedBytes();
    if (nanoAppTotalAllocatedBytes >= header->data.bytes) {
      app->setTotalAllocatedBytes(nanoAppTotalAllocatedBytes -
                                  header->data.bytes);
    } else {
      app->setTotalAllocatedBytes(0);
    }

    if (mTotalAllocatedBytes >= header->data.bytes) {
      mTotalAllocatedBytes -= header->data.bytes;
    } else {
      mTotalAllocatedBytes = 0;
    }
    if (mAllocationCount > 0) {
      mAllocationCount--;
    }

    app->unlinkHeapBlock(header);
    doFree(app, header);
  }
}

uint32_t MemoryManager::nanoappFreeAll(Nanoapp *app) {
  HeapBlockHeader *current = app->getFirstHeapBlock();

  // totalNumBlocks is used a safeguard to avoid entering an infinite loop if
  // some headers got corrupted. It represents the number of blocks currently
  // allocated for all the nanoapps and is used as an upper bound for the number
  // of blocks allocated by the current nanoapp.
  size_t totalNumBlocks = mAllocationCount;
  uint32_t numFreedBlocks = 0;

  while (current != nullptr && totalNumBlocks > 0) {
    HeapBlockHeader *next = current->data.next;
    // nanoappFree expects a pointer past the header.
    HeapBlockHeader *pointerAfterHeader = current + 1;
    nanoappFree(app, pointerAfterHeader);
    numFreedBlocks++;
    current = next;
    totalNumBlocks--;
  }

  return numFreedBlocks;
}

void MemoryManager::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print(
      "\nNanoapp heap usage: %zu bytes allocated, %zu peak bytes"
      " allocated, count %zu\n",
      getTotalAllocatedBytes(), getPeakAllocatedBytes(), getAllocationCount());
}

}  // namespace chre
