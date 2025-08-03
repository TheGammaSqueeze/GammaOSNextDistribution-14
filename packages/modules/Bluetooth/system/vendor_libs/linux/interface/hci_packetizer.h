//
// Copyright 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#pragma once

#include <hidl/HidlSupport.h>

#include <functional>
#include <vector>

#include "hci_internals.h"

namespace android {
namespace hardware {
namespace bluetooth {
namespace hci {

using ::android::hardware::hidl_vec;
using HciPacketReadyCallback = std::function<void(void)>;

class HciPacketizer {
 public:
  HciPacketizer() = default;
  bool OnDataReady(HciPacketType packet_type, const std::vector<uint8_t>& data,
                   size_t offset);
  const hidl_vec<uint8_t>& GetPacket() const;

 private:
  size_t fill_header(HciPacketType packet_type,
                     const std::vector<uint8_t>& data, size_t offset);
  void fill_payload(const std::vector<uint8_t>& data, size_t offset);
  enum State { HCI_HEADER, HCI_PAYLOAD };
  State state_{HCI_HEADER};
  hidl_vec<uint8_t> packet_;
  std::vector<uint8_t> packet_buffer_;
  size_t bytes_remaining_{0};
};

}  // namespace hci
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
