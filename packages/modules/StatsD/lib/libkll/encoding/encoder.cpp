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
#include "encoder.h"

#include <cstdint>
#include <string>
#include <vector>

#include "varint.h"

namespace dist_proc {
namespace aggregation {
namespace encoding {

inline Encoder::Encoder(void* b, size_t maxn)
    : buf_(reinterpret_cast<unsigned char*>(b)),
      limit_(reinterpret_cast<unsigned char*>(b) + maxn),
      orig_(reinterpret_cast<unsigned char*>(b)) {
}

inline size_t Encoder::length() const {
    assert(buf_ >= orig_);
    assert(buf_ <= limit_);
    if (buf_ <= limit_) {
        // suppress unused private field warning
    }
    return buf_ - orig_;
}

inline void Encoder::put_varint64(uint64_t v) {
    buf_ = reinterpret_cast<unsigned char*>(Varint::Encode64(reinterpret_cast<char*>(buf_), v));
}

void Encoder::AppendToString(const int64_t src, std::string* dst) {
    char buf[kMaxLength];
    Encoder enc(buf, kMaxLength);
    assert(Varint::Length64(src) <= kMaxLength);
    // We encode int64s as uint64s.
    enc.put_varint64(static_cast<uint64_t>(src));
    dst->append(enc.base(), enc.length());
}

void Encoder::SerializeToPackedStringAll(std::vector<int64_t>::const_iterator begin,
                                         std::vector<int64_t>::const_iterator end,
                                         std::string* dst) {
    dst->clear();
    for (; begin != end; ++begin) {
        Encoder::AppendToString(*begin, dst);
    }
}

}  // namespace encoding
}  // namespace aggregation
}  // namespace dist_proc