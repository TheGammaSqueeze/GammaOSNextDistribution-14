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

#include "hal/fuzz/fuzz_hci_hal.h"

#include "fuzz/helpers.h"
#include "hci/fuzz/status_vs_complete_commands.h"

namespace bluetooth {
namespace hal {
namespace fuzz {
using bluetooth::fuzz::GetArbitraryBytes;

void FuzzHciHal::registerIncomingPacketCallback(HciHalCallbacks* callbacks) {
  callbacks_ = callbacks;
}

void FuzzHciHal::unregisterIncomingPacketCallback() {
  callbacks_ = nullptr;
}

void FuzzHciHal::injectArbitrary(FuzzedDataProvider& fdp) {
  const uint8_t action = fdp.ConsumeIntegralInRange(0, 4);
  switch (action) {
    case 1:
      injectAclData(GetArbitraryBytes(&fdp));
      break;
    case 2:
      injectHciEvent(GetArbitraryBytes(&fdp));
      break;
    case 3:
      injectScoData(GetArbitraryBytes(&fdp));
      break;
    case 4:
      injectIsoData(GetArbitraryBytes(&fdp));
      break;
  }
}

void FuzzHciHal::sendHciCommand(HciPacket packet) {
  hci::CommandView command = hci::CommandView::FromBytes(packet);
  if (!command.IsValid()) {
    return;
  }

  waiting_opcode_ = command.GetOpCode();
  waiting_for_status_ = hci::fuzz::uses_command_status(waiting_opcode_);
  waiting_for_complete_ = !waiting_for_status_;
}

void FuzzHciHal::injectHciEvent(std::vector<uint8_t> data) {
  hci::EventView event = hci::EventView::FromBytes(data);
  if (!event.IsValid()) {
    return;
  }

  hci::CommandCompleteView complete = hci::CommandCompleteView::Create(event);
  if (complete.IsValid()) {
    if (!waiting_for_complete_ || complete.GetCommandOpCode() != waiting_opcode_) {
      return;
    }
    waiting_for_complete_ = false;
  }

  hci::CommandStatusView status = hci::CommandStatusView::Create(event);
  if (status.IsValid()) {
    if (!waiting_for_status_ || status.GetCommandOpCode() != waiting_opcode_) {
      return;
    }
    waiting_for_status_ = false;
  }

  callbacks_->hciEventReceived(data);
}

void FuzzHciHal::injectAclData(std::vector<uint8_t> data) {
  hci::AclView aclPacket = hci::AclView::FromBytes(data);
  if (!aclPacket.IsValid()) {
    return;
  }

  callbacks_->aclDataReceived(data);
}

void FuzzHciHal::injectScoData(std::vector<uint8_t> data) {
  hci::ScoView scoPacket = hci::ScoView::FromBytes(data);
  if (!scoPacket.IsValid()) {
    return;
  }

  callbacks_->scoDataReceived(data);
}

void FuzzHciHal::injectIsoData(std::vector<uint8_t> data) {
  hci::IsoView isoPacket = hci::IsoView::FromBytes(data);
  if (!isoPacket.IsValid()) {
    return;
  }

  callbacks_->isoDataReceived(data);
}

const ModuleFactory FuzzHciHal::Factory = ModuleFactory([]() { return new FuzzHciHal(); });

}  // namespace fuzz
}  // namespace hal
}  // namespace bluetooth
