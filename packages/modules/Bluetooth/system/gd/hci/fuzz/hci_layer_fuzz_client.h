/*
 * Copyright 2019 The Android Open Source Project
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

#include <fuzzer/FuzzedDataProvider.h>
#include <stddef.h>
#include <stdint.h>

#include <vector>

#include "hci/fuzz/status_vs_complete_commands.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "module.h"
#include "os/fuzz/dev_null_queue.h"
#include "os/fuzz/fuzz_inject_queue.h"

namespace bluetooth {
namespace hci {
namespace fuzz {

class HciLayerFuzzClient : public Module {
 public:
  HciLayerFuzzClient() : Module() {}

  void Start() override;
  void Stop() override;

  void injectArbitrary(FuzzedDataProvider& fdp);

  void ListDependencies(ModuleList* list) const override {
    list->add<hci::HciLayer>();
  }

  static const ModuleFactory Factory;

  std::string ToString() const override {
    return "DevNullHci";
  }

 private:
  void injectAclData(std::vector<uint8_t> data);
  void injectHciCommand(std::vector<uint8_t> data);
  void injectSecurityCommand(std::vector<uint8_t> data);
  void injectLeSecurityCommand(std::vector<uint8_t> data);
  void injectAclConnectionCommand(std::vector<uint8_t> data);
  void injectLeAclConnectionCommand(std::vector<uint8_t> data);
  void injectLeAdvertisingCommand(std::vector<uint8_t> data);
  void injectLeScanningCommand(std::vector<uint8_t> data);

  template <typename TVIEW, typename TBUILDER>
  void inject_command(std::vector<uint8_t> data, CommandInterface<TBUILDER>* interface) {
    TVIEW commandPacket = TVIEW::FromBytes(data);
    if (!commandPacket.IsValid()) {
      return;
    }

    if (uses_command_status(commandPacket.GetOpCode())) {
      interface->EnqueueCommand(
          TBUILDER::FromView(commandPacket),
          GetHandler()->BindOnce([](CommandStatusView /* status */) {}));
    } else {
      interface->EnqueueCommand(
          TBUILDER::FromView(commandPacket),
          GetHandler()->BindOnce([](CommandCompleteView /* status */) {}));
    }
  }

  hci::HciLayer* hci_ = nullptr;
  os::fuzz::DevNullQueue<AclView>* aclDevNull_;
  os::fuzz::FuzzInjectQueue<AclBuilder>* aclInject_;

  SecurityInterface* security_interface_;
  LeSecurityInterface* le_security_interface_;
  AclConnectionInterface* acl_connection_interface_;
  LeAclConnectionInterface* le_acl_connection_interface_;
  LeAdvertisingInterface* le_advertising_interface_;
  LeScanningInterface* le_scanning_interface_;
  DistanceMeasurementInterface* distance_measurement_interface_;
};

}  // namespace fuzz
}  // namespace hci
}  // namespace bluetooth
