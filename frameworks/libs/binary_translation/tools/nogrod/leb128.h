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

#ifndef __NOGROD_LEB128_DECODER_
#define __NOGROD_LEB128_DECODER_

#include <stddef.h>

#include <cstdint>

#include "berberis/base/macros.h"

namespace nogrod {

class Leb128Decoder {
 public:
  Leb128Decoder(const uint8_t* buffer, size_t size);

  // Returns number of bytes it took do decode the value, 0 if decode has failed.
  size_t Decode(size_t offset, uint64_t* result) const;

 private:
  const uint8_t* buffer_;
  size_t size_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(Leb128Decoder);
};

size_t DecodeLeb128(const uint8_t* buffer, uint64_t size, uint64_t* result);
size_t DecodeSleb128(const uint8_t* buffer, uint64_t size, int64_t* result);

}  // namespace nogrod
#endif  // __NOGROD_LEB128_DECODER_
