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

#include <log/log.h>
#include "ring_buffer.h"

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

RingBuffer::RingBuffer(size_t capacity)
        : mBuffer(new uint8_t[capacity])
        , mCapacity(capacity) {}

size_t RingBuffer::availableToProduce() const {
    std::lock_guard<std::mutex> guard(mMutex);
    return mCapacity - mAvailableToConsume;
}

size_t RingBuffer::availableToConsume() const {
    std::unique_lock<std::mutex> lock(mMutex);
    return mAvailableToConsume;
}

size_t RingBuffer::makeRoomForProduce(size_t atLeast) {
    std::unique_lock<std::mutex> lock(mMutex);
    LOG_ALWAYS_FATAL_IF(atLeast >= mCapacity);

    const size_t toProduce = mCapacity - mAvailableToConsume;
    const size_t toDrop = (atLeast <= toProduce)
        ? 0 : atLeast - toProduce;

    mConsumePos = (mConsumePos + toDrop) % mCapacity;
    mAvailableToConsume -= toDrop;

    return toDrop;
}

bool RingBuffer::waitForProduceAvailable(Timepoint blockUntil) const {
    std::unique_lock<std::mutex> lock(mMutex);
    while (true) {
        if (mAvailableToConsume < mCapacity) {
            return true;
        } else if (mProduceAvailable.wait_until(lock, blockUntil) == std::cv_status::timeout) {
            return false;
        }
    }
}

RingBuffer::ContiniousChunk RingBuffer::getProduceChunk() const {
    std::unique_lock<std::mutex> lock(mMutex);
    const int availableToProduce = mCapacity - mAvailableToConsume;

    ContiniousChunk chunk;

    chunk.data = &mBuffer[mProducePos];
    chunk.size = (mProducePos >= mConsumePos)
        ? std::min(mCapacity - mProducePos, availableToProduce)
        : std::min(mConsumePos - mProducePos, availableToProduce);

    return chunk;
}

size_t RingBuffer::produce(size_t size) {
    std::unique_lock<std::mutex> lock(mMutex);
    const int availableToProduce = mCapacity - mAvailableToConsume;
    size = std::min(size, size_t(availableToProduce));

    mProducePos = (mProducePos + size) % mCapacity;
    mAvailableToConsume += size;

    mConsumeAvailable.notify_one();
    return size;
}

size_t RingBuffer::produce(const void *srcRaw, size_t size) {
    std::unique_lock<std::mutex> lock(mMutex);
    int produceSize = std::min(mCapacity - mAvailableToConsume, int(size));
    size = produceSize;
    const uint8_t *src = static_cast<const uint8_t *>(srcRaw);

    while (produceSize > 0) {
        const int availableToProduce = mCapacity - mAvailableToConsume;
        const int chunkSz = (mProducePos >= mConsumePos)
            ? std::min(mCapacity - mProducePos, availableToProduce)
            : std::min(mConsumePos - mProducePos, availableToProduce);
        void *dst = &mBuffer[mProducePos];

        memcpy(dst, src, chunkSz);
        src += chunkSz;
        mProducePos = (mProducePos + chunkSz) % mCapacity;
        mAvailableToConsume += chunkSz;
        produceSize -= chunkSz;
    }

    mConsumeAvailable.notify_one();
    return size;
}

bool RingBuffer::waitForConsumeAvailable(Timepoint blockUntil) const {
    std::unique_lock<std::mutex> lock(mMutex);
    while (true) {
        if (mAvailableToConsume > 0) {
            return true;
        } else if (mConsumeAvailable.wait_until(lock, blockUntil) == std::cv_status::timeout) {
            return false;
        }
    }
}

RingBuffer::ContiniousLockedChunk RingBuffer::getConsumeChunk() const {
    std::unique_lock<std::mutex> lock(mMutex);

    ContiniousLockedChunk chunk;

    chunk.data = &mBuffer[mConsumePos];
    chunk.size = (mConsumePos >= mProducePos)
        ? std::min(mCapacity - mConsumePos, mAvailableToConsume)
        : std::min(mProducePos - mConsumePos, mAvailableToConsume);
    chunk.lock = std::move(lock);

    return chunk;
}

size_t RingBuffer::consume(const ContiniousLockedChunk &lock, size_t size) {
    (void)lock; // the lock is provided by getConsumeChunk
    size = std::min(size, size_t(mAvailableToConsume));

    mConsumePos = (mConsumePos + size) % mCapacity;
    mAvailableToConsume -= size;

    mProduceAvailable.notify_one();
    return size;
}

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
