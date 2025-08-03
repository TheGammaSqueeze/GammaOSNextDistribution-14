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

//#define LOG_NDEBUG 0

#include "Buffer.h"
#include <inttypes.h>
#include <log/log.h>

namespace android {
namespace webcam {

BufferManager::BufferManager(BufferCreatorAndDestroyer* crD) : mCrD(crD) {
    if (crD == nullptr) {
        return;
    }

    std::vector<std::shared_ptr<Buffer>> producerBuffers;
    if (mCrD->allocateAndMapBuffers(&mConsumerBufferItem.buffer, &producerBuffers) != Status::OK) {
        return;
    }
    mConsumerBufferItem.state = BufferState::FREE;
    for (auto& buf : producerBuffers) {
        mProducerBufferItems.emplace_back(buf, BufferState::FREE);
    }
    mInited = true;
}

BufferManager::~BufferManager() {
    std::vector<std::shared_ptr<Buffer>> producerBuffers;
    for (auto& buf : mProducerBufferItems) {
        producerBuffers.push_back(buf.buffer);
    }
    mCrD->destroyBuffers(mConsumerBufferItem.buffer, producerBuffers);
    mConsumerBufferItem.buffer = nullptr;
    mProducerBufferItems.clear();
}

Buffer* BufferManager::getFreeBufferIfAvailable() {
    // Producer call
    std::unique_lock<std::mutex> l(mBufferLock);
    for (auto& bufferItem : mProducerBufferItems) {
        if (bufferItem.state == BufferState::FREE) {
            bufferItem.state = BufferState::IN_USE;
            return bufferItem.buffer.get();
        }
    }
    for (const auto& bufferItem : mProducerBufferItems) {
        uint64_t bufferTs = bufferItem.buffer->getTimestamp();
        ALOGV("%s: Buffer at state %d, ts %" PRIu64 ", v4l2 index %u",
              __FUNCTION__, (int)bufferItem.state, bufferTs,
              bufferItem.buffer->getIndex());
    }
    return nullptr;
}

bool BufferManager::filledProducerBufferAvailableLocked(uint32_t* index) {
    uint32_t i = 0;
    bool found = false;
    uint32_t foundIndex = 0;
    uint64_t ts = 0;
    // Try to get the latest filled buffer.
    for (auto& bufferItem : mProducerBufferItems) {
        uint64_t bufferTs = bufferItem.buffer->getTimestamp();
        ALOGV("%s: Buffer at index i %u : state %d, ts %" PRIu64 ", v4l2 index %u", __FUNCTION__,
                i, (int)bufferItem.state, bufferTs, bufferItem.buffer->getIndex());
        if (bufferItem.state == BufferState::FILLED) {
            if (bufferTs > ts) {
                if (index != nullptr) {
                    *index = i;
                }
                ts = bufferTs;
                found = true;
                foundIndex = i;
            }
        }
        i++;
    }
    // Actually cancel older buffers
    uint32_t j = 0;
    for (auto& bufferItem : mProducerBufferItems) {
        if (bufferItem.state == BufferState::FILLED && j != foundIndex) {
            bufferItem.state = BufferState::FREE;
        }
        j++;
    }
    return found;
}

Buffer* BufferManager::getFilledBufferAndSwap() {
    // Consumer call
    // Wait for a producer buffer item state to be FILLED
    // and swap consumer and producer buffer
    std::unique_lock<std::mutex> l(mBufferLock);
    uint32_t index = 0;
    while (!filledProducerBufferAvailableLocked(&index)) {
        // TODO(b/267794640): Add a timeout to recover in case of a deadlock.
        // Wait till the producer side has filled the buffer.
        mProducerBufferFilled.wait(l);
    }
    // Mark it free so that the producer can start filling it.
    mConsumerBufferItem.state = BufferState::FREE;
    std::swap(mConsumerBufferItem, mProducerBufferItems[index]);
    // Now the consumer buffer is busy
    mConsumerBufferItem.state = BufferState::IN_USE;
    return mConsumerBufferItem.buffer.get();
}

bool BufferManager::changeProducerBufferStateLocked(Buffer* buffer, BufferState newState) {
    bool found = false;
    uint32_t i = 0;
    for (auto& bufferItem : mProducerBufferItems) {
        if (buffer->getIndex() == bufferItem.buffer->getIndex()) {
            found = true;
            break;
        }
        i++;
    }
    if (!found) {
        ALOGE("%s queuing incorrect buffer, filled buffer index %u", __FUNCTION__,
              buffer->getIndex());
        return false;
    }

    mProducerBufferItems[i].state = newState;
    return true;
}

Status BufferManager::cancelBuffer(Buffer* buffer) {
    std::unique_lock<std::mutex> l(mBufferLock);
    if (!changeProducerBufferStateLocked(buffer, BufferState::FREE)) {
        return Status::ERROR;
    }
    return Status::OK;
}

Status BufferManager::queueFilledBuffer(Buffer* buffer) {
    std::unique_lock<std::mutex> l(mBufferLock);

    if (!changeProducerBufferStateLocked(buffer, BufferState::FILLED)) {
        return Status::ERROR;
    }

    mProducerBufferFilled.notify_one();
    return Status::OK;
}

}  // namespace webcam
}  // namespace android
