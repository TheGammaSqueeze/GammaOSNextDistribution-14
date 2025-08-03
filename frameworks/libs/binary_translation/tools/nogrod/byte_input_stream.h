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

#ifndef __NOGROD_BYTE_INPUT_STREAM_
#define __NOGROD_BYTE_INPUT_STREAM_

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace nogrod {

class ByteInputStream {
 public:
  ByteInputStream(const uint8_t* buffer, size_t size);

  [[nodiscard]] bool available() const;
  [[nodiscard]] uint64_t offset() const;

  [[nodiscard]] uint8_t ReadUint8();
  [[nodiscard]] uint16_t ReadUint16();
  [[nodiscard]] uint32_t ReadUint24();
  [[nodiscard]] uint32_t ReadUint32();
  [[nodiscard]] uint64_t ReadUint64();
  [[nodiscard]] uint64_t ReadLeb128();
  [[nodiscard]] int64_t ReadSleb128();
  [[nodiscard]] std::string ReadString();
  [[nodiscard]] std::vector<uint8_t> ReadBytes(uint64_t size);

 private:
  const uint8_t* buffer_;
  uint64_t size_;
  uint64_t offset_;
};

};  // namespace nogrod

#endif  // __NOGROD_BYTE_INPUT_STREAM_
