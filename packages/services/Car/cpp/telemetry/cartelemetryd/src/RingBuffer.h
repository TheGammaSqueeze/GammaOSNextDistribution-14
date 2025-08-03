/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_TELEMETRY_CARTELEMETRYD_SRC_RINGBUFFER_H_
#define CPP_TELEMETRY_CARTELEMETRYD_SRC_RINGBUFFER_H_

#include "BufferedCarData.h"

#include <list>

namespace android {
namespace automotive {
namespace telemetry {

// A ring buffer that holds BufferedCarData. It drops old data if it's full.
// Not thread-safe.
class RingBuffer {
public:
    // RingBuffer limits the number of elements in the buffer to the given param `sizeLimit`.
    // Doesn't pre-allocate the memory.
    explicit RingBuffer(int32_t sizeLimit);

    // Not copyable or movable
    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;
    RingBuffer(RingBuffer&&) = delete;
    RingBuffer& operator=(RingBuffer&&) = delete;

    // Pushes the data to the buffer. If the buffer is full, it removes the oldest data.
    // Supports moving the data to the RingBuffer.
    void push(BufferedCarData&& data);

    // Returns the newest element from the ring buffer and removes it from the buffer.
    BufferedCarData popBack();

    // Dumps the current state for dumpsys.
    void dump(int fd) const;

    // Returns the number of elements in the buffer.
    int32_t size() const;

private:
    const int32_t mSizeLimit;

    // TODO(b/174608802): Improve dropped CarData handling, see ag/13818937 for details.
    int64_t mTotalDroppedDataCount;

    // Linked list that holds all the data and allows deleting old data when the buffer is full.
    std::list<BufferedCarData> mList;
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_SRC_RINGBUFFER_H_
