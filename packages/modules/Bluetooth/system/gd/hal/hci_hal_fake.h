/*
 * Copyright 2023 The Android Open Source Project
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

#include <future>
#include <list>
#include <optional>

#include "common/blocking_queue.h"
#include "hal/hci_hal.h"
#include "hci/hci_packets.h"
#include "packet/packet_view.h"

namespace bluetooth {
namespace hal {

class TestHciHal : public hal::HciHal {
 public:
  TestHciHal() : hal::HciHal() {}

  ~TestHciHal() {
    if (callbacks != nullptr) {
      LOG_ALWAYS_FATAL("unregisterIncomingPacketCallback() must be called");
    }
  }

  void registerIncomingPacketCallback(hal::HciHalCallbacks* callback) override {
    callbacks = callback;
  }

  void unregisterIncomingPacketCallback() override {
    callbacks = nullptr;
  }

  void sendHciCommand(hal::HciPacket command) override;

  void sendAclData(hal::HciPacket data) override;

  void sendScoData(hal::HciPacket data) override;

  void sendIsoData(hal::HciPacket data) override;

  hal::HciHalCallbacks* callbacks = nullptr;

  packet::PacketView<packet::kLittleEndian> GetPacketView(hal::HciPacket data);

  std::optional<hci::CommandView> GetSentCommand(
      std::chrono::milliseconds timeout = std::chrono::seconds(1));

  std::optional<hci::AclView> GetSentAcl(
      std::chrono::milliseconds timeout = std::chrono::seconds(1));

  std::optional<hci::ScoView> GetSentSco(
      std::chrono::milliseconds timeout = std::chrono::seconds(1));

  std::optional<hci::IsoView> GetSentIso(
      std::chrono::milliseconds timeout = std::chrono::seconds(1));

  void InjectEvent(std::unique_ptr<packet::BasePacketBuilder> event);

  void Start() {}

  void Stop() {}

  void ListDependencies(ModuleList* /* list */) const {}

  std::string ToString() const override {
    return std::string("TestHciHal");
  }

  static const ModuleFactory Factory;

 private:
  common::BlockingQueue<hal::HciPacket> outgoing_commands_;
  common::BlockingQueue<hal::HciPacket> outgoing_acl_;
  common::BlockingQueue<hal::HciPacket> outgoing_sco_;
  common::BlockingQueue<hal::HciPacket> outgoing_iso_;
};

}  // namespace hal
}  // namespace bluetooth
