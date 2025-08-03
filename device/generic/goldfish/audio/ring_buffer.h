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

#pragma once
#include <memory>
#include <chrono>
#include <utility>
#include <condition_variable>
#include <mutex>
#include <stdint.h>

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

// A one-producer-one-consumer ring buffer.
struct RingBuffer {
    typedef std::chrono::time_point<std::chrono::high_resolution_clock> Timepoint;

    RingBuffer(size_t capacity);

    size_t capacity() const { return mCapacity; }
    size_t availableToProduce() const;
    size_t availableToConsume() const;

    struct ContiniousChunk {
        void *data;
        size_t size;
        std::unique_lock<std::mutex> lock;
    };

    struct ContiniousLockedChunk : public ContiniousChunk{
        std::unique_lock<std::mutex> lock;
    };

    size_t makeRoomForProduce(size_t atLeast);

    bool waitForProduceAvailable(Timepoint blockUntil) const;

    // `getProduceChunk` is a non-blocking function which returns a pointer
    // (`result.data`) inside RingBuffer's buffer, `result.size` is the
    // size of the continious chunk (can be smaller than availableToProduce()).
    ContiniousChunk getProduceChunk() const;

    // Tries to move the `produce` cursor by `size`, returns the actual size
    // moved.
    size_t produce(size_t size);

    // Tried to write `size` bytes into the internal buffer from `data`,
    // returns the actual size written.
    size_t produce(const void *data, size_t size);

    bool waitForConsumeAvailable(Timepoint blockUntil) const;

    // `getConsumeChunk` is a non-blocking function which a pointer
    // (`result.data`) inside RingBuffer's buffer, `result.size` is the
    //  size of the continious chunk (can be smaller than availableToConsume()).
    // IMPORTANT: do not call long running functions while have an instance of
    //            ContiniousLockedChunk, it might block the producer because
    //            it need to drop the stale audio data to replace it with
    //            more recent one.
    // NOTE: ContiniousLockedChunk holds a lock inside, it might deadlock
    //       if you calling other RingBuffer's functions that assume it is
    //       unlocked, in this case you want to introduce a function like
    //       `consume` below.
    ContiniousLockedChunk getConsumeChunk() const;

    // Tries to move the `consume` cursor by `size`, returns the actual size
    // moved.
    size_t consume(const ContiniousLockedChunk&, size_t size);

private:
    std::unique_ptr<uint8_t[]> mBuffer;
    mutable std::condition_variable mProduceAvailable;
    mutable std::condition_variable mConsumeAvailable;
    mutable std::mutex mMutex;
    const int mCapacity;
    int mAvailableToConsume = 0;
    int mProducePos = 0;
    int mConsumePos = 0;
};

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
