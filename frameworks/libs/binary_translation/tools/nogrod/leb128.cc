/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "leb128.h"

#include <cstring>
#include <inttypes.h>
#include <limits.h>

#include <berberis/base/checks.h>

namespace nogrod {

Leb128Decoder::Leb128Decoder(const uint8_t* buffer, size_t size) : buffer_(buffer), size_(size) {}

// Returns number of bytes it took do decode the value, 0 if decode has failed.
size_t Leb128Decoder::Decode(size_t offset, uint64_t* result) const {
  return DecodeLeb128(buffer_ + offset, size_ - offset, result);
}

size_t DecodeLeb128(const uint8_t* buf, uint64_t buf_size, uint64_t* result) {
  uint64_t value = 0;
  size_t shift = 0;
  size_t size = 0;

  uint8_t byte;

  do {
    if (size >= buf_size) {
      FATAL("leb128: ran out of bounds while reading value at offset=%zd (buf_size=%" PRId64 ")",
            size,
            buf_size);
    }

    if (shift >= CHAR_BIT * sizeof(uint64_t)) {
      FATAL("leb128: the value at offset %zd is too big (does not fit into uint64_t), shift=%zd",
            size,
            shift);
    }

    byte = buf[size++];

    value += static_cast<uint64_t>(byte & 0x7f) << shift;

    shift += 7;
  } while ((byte & 0x80) != 0);

  *result = value;
  return size;
}

size_t DecodeSleb128(const uint8_t* buf, uint64_t buf_size, int64_t* result) {
  uint64_t value = 0;
  size_t shift = 0;
  size_t size = 0;

  uint8_t byte;

  do {
    if (size >= buf_size) {
      FATAL("sleb128: ran out of bounds while reading value at offset=%zd (buf_size=%" PRId64 ")",
            size,
            buf_size);
    }

    if (shift >= CHAR_BIT * sizeof(uint64_t)) {
      FATAL("sleb128: the value at offset %zd is too big (does not fit into uint64_t), shift=%zd",
            size,
            shift);
    }

    byte = buf[size++];

    value += static_cast<uint64_t>(byte & 0x7f) << shift;

    shift += 7;
  } while ((byte & 0x80) != 0);

  // sign extent is not applicable if shift is out of bounds of uint64_t
  if (shift < (sizeof(uint64_t) * CHAR_BIT) && (byte & 0x40) != 0) {
    value |= -(static_cast<uint64_t>(1) << shift);
  }

  memcpy(result, &value, sizeof(*result));
  return size;
}

}  // namespace nogrod
