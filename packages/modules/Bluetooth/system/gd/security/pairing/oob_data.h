/*
 *
 *  Copyright 2020 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License") override;
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#pragma once

#include <array>
#include <cstdint>

namespace bluetooth {
namespace security {
namespace pairing {

using SimplePairingHash = std::array<uint8_t, 16>;
using SimplePairingRandomizer = std::array<uint8_t, 16>;

class OobData {
 public:
  OobData() {}
  OobData(SimplePairingHash C, SimplePairingRandomizer R) : C_(C), R_(R) {}

  SimplePairingHash GetC() {
    return C_;
  }

  SimplePairingRandomizer GetR() {
    return R_;
  }

  bool IsValid() {
    return !std::all_of(C_.begin(), C_.end(), [](uint8_t b) { return b == 0; }) &&
           !std::all_of(R_.begin(), R_.end(), [](uint8_t b) { return b == 0; });
  }

 private:
  SimplePairingHash C_ = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  SimplePairingRandomizer R_ = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
};

}  // namespace pairing
}  // namespace security
}  // namespace bluetooth
