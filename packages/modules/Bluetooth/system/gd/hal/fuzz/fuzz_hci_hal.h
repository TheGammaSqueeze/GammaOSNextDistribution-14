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

#include "fuzz/helpers.h"
#include "hal/hci_hal.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hal {
namespace fuzz {

class FuzzHciHal : public HciHal {
 public:
  void registerIncomingPacketCallback(HciHalCallbacks* callbacks) override;
  void unregisterIncomingPacketCallback() override;

  void sendHciCommand(HciPacket command) override;
  void sendAclData(HciPacket /* packet */) override {}
  void sendScoData(HciPacket /* packet */) override {}
  void sendIsoData(HciPacket /* packet */) override {}

  void injectArbitrary(FuzzedDataProvider& fdp);

  std::string ToString() const override {
    return "HciHalFuzz";
  }

  static const ModuleFactory Factory;

 protected:
  void ListDependencies(ModuleList* /* list */) const override {}
  void Start() override {}
  void Stop() override {}

 private:
  void injectAclData(std::vector<uint8_t> data);
  void injectHciEvent(std::vector<uint8_t> data);
  void injectScoData(std::vector<uint8_t> data);
  void injectIsoData(std::vector<uint8_t> data);

  HciHalCallbacks* callbacks_;
  hci::OpCode waiting_opcode_{};
  bool waiting_for_complete_{};
  bool waiting_for_status_{};
};

}  // namespace fuzz
}  // namespace hal
}  // namespace bluetooth
