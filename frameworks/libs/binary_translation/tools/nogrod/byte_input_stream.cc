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

#include "byte_input_stream.h"

#include "berberis/base/checks.h"

#include "leb128.h"

namespace nogrod {

ByteInputStream::ByteInputStream(const uint8_t* buffer, size_t size)
    : buffer_(buffer), size_(size), offset_(0) {}

bool ByteInputStream::available() const {
  return offset_ < size_;
}

uint64_t ByteInputStream::offset() const {
  return offset_;
}

uint8_t ByteInputStream::ReadUint8() {
  CHECK(offset_ < size_);
  return buffer_[offset_++];
}

uint16_t ByteInputStream::ReadUint16() {
  uint16_t result;
  CHECK(offset_ + sizeof(result) <= size_);
  memcpy(&result, buffer_ + offset_, sizeof(result));
  offset_ += sizeof(result);

  return result;
}

uint32_t ByteInputStream::ReadUint24() {
  uint32_t result = 0;
  CHECK_LE(offset_ + 3, size_);
  memcpy(&result, buffer_ + offset_, 3);
  offset_ += 3;

  return result;
}

uint32_t ByteInputStream::ReadUint32() {
  uint32_t result;
  CHECK(offset_ + sizeof(result) <= size_);
  memcpy(&result, buffer_ + offset_, sizeof(result));
  offset_ += sizeof(result);

  return result;
}

uint64_t ByteInputStream::ReadUint64() {
  uint64_t result;
  CHECK(offset_ + sizeof(result) <= size_);
  memcpy(&result, buffer_ + offset_, sizeof(result));
  offset_ += sizeof(result);

  return result;
}

uint64_t ByteInputStream::ReadLeb128() {
  uint64_t result;
  size_t bytes = DecodeLeb128(buffer_ + offset_, size_ - offset_, &result);
  offset_ += bytes;
  return result;
}

int64_t ByteInputStream::ReadSleb128() {
  int64_t result;
  size_t bytes = DecodeSleb128(buffer_ + offset_, size_ - offset_, &result);
  offset_ += bytes;
  return result;
}

std::vector<uint8_t> ByteInputStream::ReadBytes(uint64_t size) {
  CHECK_LE(offset_ + size, size_);
  // WORKAROUND(http://b/227598583): libc++ on tm-dev branch crashes of you call std::vector c-tor
  // with size = 0;
  if (size == 0) return {};
  // End of workaround
  std::vector<uint8_t> result(size);
  memcpy(result.data(), buffer_ + offset_, size);
  offset_ += size;

  return result;
}

std::string ByteInputStream::ReadString() {
  CHECK_LT(offset_, size_);  // there should be a place for at least one 0

  const char* candidate = reinterpret_cast<const char*>(buffer_ + offset_);
  while (buffer_[offset_++] != 0) {
    CHECK_LT(offset_, size_);
  }

  return std::string{candidate};
}

}  // namespace nogrod
