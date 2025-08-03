// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <stdint.h>

#include <array>

#include "types/ble_address_with_type.h"
namespace bluetooth {
namespace core {

enum class AddressType : uint8_t {
  Public = 0x0,
  Random = 0x1,
};

struct AddressWithType {
  /// Stored in little-endian format
  std::array<uint8_t, 6> address;
  AddressType address_type;
};

inline core::AddressWithType ToRustAddress(tBLE_BD_ADDR address) {
  auto array = address.bda.ToArray();
  std::reverse(std::begin(array), std::end(array));
  return core::AddressWithType{array, address.IsPublic()
                                          ? core::AddressType::Public
                                          : core::AddressType::Random};
}

}  // namespace core
}  // namespace bluetooth
