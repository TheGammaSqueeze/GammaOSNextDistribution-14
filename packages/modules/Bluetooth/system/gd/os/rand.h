/*
 * Copyright 2020 The Android Open Source Project
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

#include <openssl/rand.h>
#include <stddef.h>

#include <array>
#include <cstdint>

#include "os/log.h"

namespace bluetooth {
namespace os {

template <size_t SIZE>
std::array<uint8_t, SIZE> GenerateRandom() {
  std::array<uint8_t, SIZE> ret;
  ASSERT(RAND_bytes(ret.data(), ret.size()) == 1);
  return ret;
}

inline uint32_t GenerateRandom() {
  uint32_t ret{};
  ASSERT(RAND_bytes((uint8_t*)(&ret), sizeof(uint32_t)) == 1);
  return ret;
}

}  // namespace os
}  // namespace bluetooth