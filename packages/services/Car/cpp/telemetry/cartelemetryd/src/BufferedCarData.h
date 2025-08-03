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

#ifndef CPP_TELEMETRY_CARTELEMETRYD_SRC_BUFFEREDCARDATA_H_
#define CPP_TELEMETRY_CARTELEMETRYD_SRC_BUFFEREDCARDATA_H_

#include <stdint.h>

#include <tuple>
#include <vector>

namespace android {
namespace automotive {
namespace telemetry {

// Internally stored `CarData` with some extras.
struct BufferedCarData {
    BufferedCarData(BufferedCarData&& other) = default;
    BufferedCarData(const BufferedCarData&) = default;
    BufferedCarData& operator=(BufferedCarData&& other) = delete;
    BufferedCarData& operator=(const BufferedCarData&) = delete;

    inline bool operator==(const BufferedCarData& rhs) const {
        return std::tie(mId, mContent, mPublisherUid) ==
                std::tie(rhs.mId, rhs.mContent, rhs.mPublisherUid);
    }

    // Returns the size of the stored data. Note that it's not the exact size of the struct.
    int32_t contentSizeInBytes() const { return mContent.size(); }

    const int32_t mId;
    const std::vector<uint8_t> mContent;

    // The uid of the logging client.
    const uid_t mPublisherUid;
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_SRC_BUFFEREDCARDATA_H_
