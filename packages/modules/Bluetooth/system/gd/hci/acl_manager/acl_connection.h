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

#include "common/bidi_queue.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class AclConnection {
 public:
  AclConnection() : queue_up_end_(nullptr), handle_(0){};
  AclConnection(const AclConnection&) = delete;
  AclConnection& operator=(const AclConnection&) = delete;

  virtual ~AclConnection() = default;

  uint16_t GetHandle() const {
    return handle_;
  }

  virtual bool ReadRemoteVersionInformation() = 0;

  using Queue = common::BidiQueue<PacketView<kLittleEndian>, BasePacketBuilder>;
  using QueueUpEnd = common::BidiQueueEnd<BasePacketBuilder, PacketView<kLittleEndian>>;
  using QueueDownEnd = common::BidiQueueEnd<PacketView<kLittleEndian>, BasePacketBuilder>;
  virtual QueueUpEnd* GetAclQueueEnd() const;

  bool locally_initiated_{false};

 protected:
  AclConnection(QueueUpEnd* queue_up_end, uint16_t handle) : queue_up_end_(queue_up_end), handle_(handle) {}
  QueueUpEnd* queue_up_end_;
  uint16_t handle_;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
