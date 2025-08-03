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

#include "hci/fuzz/hci_layer_fuzz_client.h"

#include "fuzz/helpers.h"
#include "hci/class_of_device.h"

namespace bluetooth {
namespace hci {
namespace fuzz {
using bluetooth::fuzz::GetArbitraryBytes;
using bluetooth::hci::AclView;

const ModuleFactory HciLayerFuzzClient::Factory = ModuleFactory([]() { return new HciLayerFuzzClient(); });

void HciLayerFuzzClient::Start() {
  hci_ = GetDependency<hci::HciLayer>();
  aclDevNull_ = new os::fuzz::DevNullQueue<AclView>(hci_->GetAclQueueEnd(), GetHandler());
  aclDevNull_->Start();
  aclInject_ = new os::fuzz::FuzzInjectQueue<AclBuilder>(hci_->GetAclQueueEnd(), GetHandler());

  // Can't do security right now, due to the Encryption Change conflict between ACL manager & security
  // security_interface_ = hci_->GetSecurityInterface(common::Bind([](EventView){}), GetHandler());
  le_security_interface_ = hci_->GetLeSecurityInterface(GetHandler()->Bind([](LeMetaEventView) {}));
  acl_connection_interface_ = hci_->GetAclConnectionInterface(
      GetHandler()->Bind([](EventView) {}),
      GetHandler()->Bind([](uint16_t, hci::ErrorCode) {}),
      GetHandler()->Bind([](Address, ClassOfDevice) {}),
      GetHandler()->Bind([](hci::ErrorCode, uint16_t, uint8_t, uint16_t, uint16_t) {}));
  le_acl_connection_interface_ = hci_->GetLeAclConnectionInterface(
      GetHandler()->Bind([](LeMetaEventView) {}),
      GetHandler()->Bind([](uint16_t, hci::ErrorCode) {}),
      GetHandler()->Bind([](hci::ErrorCode, uint16_t, uint8_t, uint16_t, uint16_t) {}));
  le_advertising_interface_ = hci_->GetLeAdvertisingInterface(GetHandler()->Bind([](LeMetaEventView) {}));
  le_scanning_interface_ = hci_->GetLeScanningInterface(GetHandler()->Bind([](LeMetaEventView) {}));
  distance_measurement_interface_ =
      hci_->GetDistanceMeasurementInterface(GetHandler()->Bind([](LeMetaEventView) {}));
}

void HciLayerFuzzClient::Stop() {
  aclDevNull_->Stop();
  delete aclDevNull_;
  delete aclInject_;
}

void HciLayerFuzzClient::injectArbitrary(FuzzedDataProvider& fdp) {
  const uint8_t action = fdp.ConsumeIntegralInRange(0, 8);
  switch (action) {
    case 1:
      injectAclData(GetArbitraryBytes(&fdp));
      break;
    case 2:
      injectHciCommand(GetArbitraryBytes(&fdp));
      break;
    case 3:
      // TODO: injectSecurityCommand(GetArbitraryBytes(&fdp));
      break;
    case 4:
      injectLeSecurityCommand(GetArbitraryBytes(&fdp));
      break;
    case 5:
      injectAclConnectionCommand(GetArbitraryBytes(&fdp));
      break;
    case 6:
      injectLeAclConnectionCommand(GetArbitraryBytes(&fdp));
      break;
    case 7:
      injectLeAdvertisingCommand(GetArbitraryBytes(&fdp));
      break;
    case 8:
      injectLeScanningCommand(GetArbitraryBytes(&fdp));
      break;
  }
}

void HciLayerFuzzClient::injectAclData(std::vector<uint8_t> data) {
  hci::AclView aclPacket = hci::AclView::FromBytes(data);
  if (!aclPacket.IsValid()) {
    return;
  }

  aclInject_->Inject(AclBuilder::FromView(aclPacket));
}

void HciLayerFuzzClient::injectHciCommand(std::vector<uint8_t> data) {
  inject_command<CommandView, CommandBuilder>(data, hci_);
}

void HciLayerFuzzClient::injectSecurityCommand(std::vector<uint8_t> data) {
  inject_command<SecurityCommandView, SecurityCommandBuilder>(data, security_interface_);
}

void HciLayerFuzzClient::injectLeSecurityCommand(std::vector<uint8_t> data) {
  inject_command<LeSecurityCommandView, LeSecurityCommandBuilder>(data, le_security_interface_);
}

void HciLayerFuzzClient::injectAclConnectionCommand(std::vector<uint8_t> data) {
  inject_command<AclCommandView, AclCommandBuilder>(data, acl_connection_interface_);
}

void HciLayerFuzzClient::injectLeAclConnectionCommand(std::vector<uint8_t> data) {
  inject_command<AclCommandView, AclCommandBuilder>(data, le_acl_connection_interface_);
}

void HciLayerFuzzClient::injectLeAdvertisingCommand(std::vector<uint8_t> data) {
  inject_command<LeAdvertisingCommandView, LeAdvertisingCommandBuilder>(data, le_advertising_interface_);
}

void HciLayerFuzzClient::injectLeScanningCommand(std::vector<uint8_t> data) {
  inject_command<LeScanningCommandView, LeScanningCommandBuilder>(data, le_scanning_interface_);
}

}  // namespace fuzz
}  // namespace hci
}  // namespace bluetooth
