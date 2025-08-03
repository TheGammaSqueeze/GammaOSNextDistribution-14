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

#include "RingBuffer.h"

#include <android-base/logging.h>

#include <inttypes.h>  // for PRIu64 and friends

#include <memory>

namespace android {
namespace automotive {
namespace telemetry {

RingBuffer::RingBuffer(int32_t limit) : mSizeLimit(limit) {}

void RingBuffer::push(BufferedCarData&& data) {
    mList.push_back(std::move(data));
    while (mList.size() > mSizeLimit) {
        mList.pop_front();
        mTotalDroppedDataCount += 1;
    }
}

BufferedCarData RingBuffer::popBack() {
    auto result = std::move(mList.back());
    mList.pop_back();
    return result;
}

void RingBuffer::dump(int fd) const {
    dprintf(fd, "    RingBuffer:\n");
    dprintf(fd, "      mSizeLimit=%d\n", mSizeLimit);
    dprintf(fd, "      mList.size=%zu\n", mList.size());
    dprintf(fd, "      mTotalDroppedDataCount=%" PRIu64 "\n", mTotalDroppedDataCount);
}

int32_t RingBuffer::size() const {
    return mList.size();
}

}  // namespace telemetry
}  // namespace automotive
}  // namespace android
