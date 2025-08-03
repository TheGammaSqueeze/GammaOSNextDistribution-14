/*
 * Copyright 2021 Google LLC
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
#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "varint.h"

namespace dist_proc {
namespace aggregation {
namespace encoding {

class Encoder {
public:
    // Initialize encoder to encode into "buf"
    Encoder(void* buf, size_t maxn);
    static void AppendToString(const int64_t src, std::string* dst);

    static void SerializeToPackedStringAll(std::vector<int64_t>::const_iterator begin,
                                           std::vector<int64_t>::const_iterator end,
                                           std::string* dst);

private:
    // Max number of bytes needed to encode 64 bits as a varint (= ceil(64 / 7)).
    static const int8_t kMaxLength = 10;
    // buf_ points into the orig_ buffer, just past the last encoded byte.
    unsigned char* buf_ = nullptr;
    // limits_ points just past the last allocated byte in the orig_ buffer.
    unsigned char* limit_ = nullptr;
    // orig_ points to the start of the encoding buffer, whether or not the
    // Encoder owns it.
    unsigned char* orig_ = nullptr;

    void put_varint64(uint64_t v);

    // Return number of bytes encoded so far.
    size_t length() const;

    // Return ptr to start of encoded data.
    const char* base() const {
        return reinterpret_cast<const char*>(orig_);
    }
};

}  // namespace encoding
}  // namespace aggregation
}  // namespace dist_proc
