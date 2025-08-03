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

#include <stdint.h>

#include "common/bidi_queue.h"
#include "common/multi_priority_queue.h"
#include "hci/acl_manager/acl_connection.h"
#include "hci/controller.h"
#include "hci/hci_packets.h"
#include "os/handler.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

class RoundRobinScheduler {
 public:
  RoundRobinScheduler(
      os::Handler* handler, Controller* controller, common::BidiQueueEnd<AclBuilder, AclView>* hci_queue_end);
  ~RoundRobinScheduler();

  enum ConnectionType { CLASSIC, LE };

  struct acl_queue_handler {
    ConnectionType connection_type_;
    std::shared_ptr<acl_manager::AclConnection::Queue> queue_;
    bool dequeue_is_registered_ = false;
    uint16_t number_of_sent_packets_ = 0;  // Track credits
    bool high_priority_ = false;           // For A2dp use
  };

  void Register(ConnectionType connection_type, uint16_t handle,
                std::shared_ptr<acl_manager::AclConnection::Queue> queue);
  void Unregister(uint16_t handle);
  void SetLinkPriority(uint16_t handle, bool high_priority);
  uint16_t GetCredits();
  uint16_t GetLeCredits();

 private:
  void start_round_robin();
  void buffer_packet(uint16_t acl_handle);
  void unregister_all_connections();
  void send_next_fragment();
  std::unique_ptr<AclBuilder> handle_enqueue_next_fragment();
  void incoming_acl_credits(uint16_t handle, uint16_t credits);

  os::Handler* handler_ = nullptr;
  Controller* controller_ = nullptr;
  std::map<uint16_t, acl_queue_handler> acl_queue_handlers_;
  common::MultiPriorityQueue<std::pair<ConnectionType, std::unique_ptr<AclBuilder>>, 2> fragments_to_send_;
  uint16_t max_acl_packet_credits_ = 0;
  uint16_t acl_packet_credits_ = 0;
  uint16_t le_max_acl_packet_credits_ = 0;
  uint16_t le_acl_packet_credits_ = 0;
  size_t hci_mtu_{0};
  size_t le_hci_mtu_{0};
  std::atomic_bool enqueue_registered_ = false;
  common::BidiQueueEnd<AclBuilder, AclView>* hci_queue_end_ = nullptr;
  // first register queue end for the Round-robin schedule
  std::map<uint16_t, acl_queue_handler>::iterator starting_point_;
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
