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

#include "async_fd_watcher.h"
#include "hci_internals.h"
#include "hci_packetizer.h"

namespace android {
namespace hardware {
namespace bluetooth {
namespace hci {

using ::android::hardware::hidl_vec;
using PacketReadCallback = std::function<void(const hidl_vec<uint8_t>&)>;
using OnDisconnectCallback = std::function<void()>;

class H4Protocol {
 public:
  H4Protocol(int fd, PacketReadCallback event_cb, PacketReadCallback acl_cb,
             PacketReadCallback sco_cb, PacketReadCallback iso_cb,
             OnDisconnectCallback disconnect_cb);

  virtual ~H4Protocol() {}

  size_t Send(uint8_t type, const uint8_t* data, size_t length);

  void OnDataReady(int fd);

 private:
  int uart_fd_;
  bool disconnected_{false};

  size_t on_packet_ready(const hidl_vec<uint8_t>& packet);
  void send_data_to_packetizer(uint8_t* buffer, size_t length);

  PacketReadCallback event_cb_;
  PacketReadCallback acl_cb_;
  PacketReadCallback sco_cb_;
  PacketReadCallback iso_cb_;
  OnDisconnectCallback disconnect_cb_;

  HciPacketType hci_packet_type_{HCI_PACKET_TYPE_UNKNOWN};
  HciPacketizer hci_packetizer_;

  /**
   * Question : Why read in single chunk rather than multiple reads?
   * Answer: Using multiple reads does not work with some BT USB dongles.
   * Reading in single shot gives expected response.
   * ACL max length is 2 bytes, so using 64K as the buffer length.
   */
  static constexpr size_t kMaxPacketLength = 64 * 1024;
};

}  // namespace hci
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
