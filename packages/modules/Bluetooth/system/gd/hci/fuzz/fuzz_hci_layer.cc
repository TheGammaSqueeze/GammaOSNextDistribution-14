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

#include "hci/fuzz/fuzz_hci_layer.h"

#include "fuzz/helpers.h"
#include "hci/class_of_device.h"

namespace bluetooth {
namespace hci {
namespace fuzz {

using bluetooth::common::ContextualCallback;
using bluetooth::fuzz::GetArbitraryBytes;
using bluetooth::fuzz::InvokeIfValid;

hci::SecurityInterface* FuzzHciLayer::GetSecurityInterface(
    ContextualCallback<void(hci::EventView)> /* event_handler */) {
  return &security_interface_;
}

hci::LeSecurityInterface* FuzzHciLayer::GetLeSecurityInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */) {
  return &le_security_interface_;
}

hci::AclConnectionInterface* FuzzHciLayer::GetAclConnectionInterface(
    ContextualCallback<void(hci::EventView)> /* event_handler */,
    ContextualCallback<void(uint16_t, hci::ErrorCode)> /* on_disconnect */,
    ContextualCallback<void(Address, ClassOfDevice)> /* on_connection_request */,
    ContextualCallback<void(
        hci::ErrorCode,
        uint16_t,
        uint8_t version,
        uint16_t manufacturer_name,
        uint16_t sub_version)>
    /* on_read_remote_version */) {
  return &acl_connection_interface_;
}

hci::LeAclConnectionInterface* FuzzHciLayer::GetLeAclConnectionInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */,
    ContextualCallback<void(uint16_t, hci::ErrorCode)> /* on_disconnect */,
    ContextualCallback<void(
        hci::ErrorCode,
        uint16_t,
        uint8_t version,
        uint16_t manufacturer_name,
        uint16_t sub_version)>
    /* on_read_remote_version */) {
  return &le_acl_connection_interface_;
}

hci::LeAdvertisingInterface* FuzzHciLayer::GetLeAdvertisingInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */) {
  return &le_advertising_interface_;
}

hci::LeScanningInterface* FuzzHciLayer::GetLeScanningInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */) {
  return &le_scanning_interface_;
}

hci::LeIsoInterface* FuzzHciLayer::GetLeIsoInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */) {
  return &le_iso_interface_;
}

hci::DistanceMeasurementInterface* FuzzHciLayer::GetDistanceMeasurementInterface(
    ContextualCallback<void(hci::LeMetaEventView)> /* event_handler */) {
  return &distance_measurement_interface_;
}

void FuzzHciLayer::Start() {
  acl_dev_null_ = new os::fuzz::DevNullQueue<AclBuilder>(acl_queue_.GetDownEnd(), GetHandler());
  acl_dev_null_->Start();
  acl_inject_ = new os::fuzz::FuzzInjectQueue<AclView>(acl_queue_.GetDownEnd(), GetHandler());
}

void FuzzHciLayer::Stop() {
  acl_dev_null_->Stop();
  delete acl_dev_null_;
  delete acl_inject_;
}

void FuzzHciLayer::injectArbitrary(FuzzedDataProvider& fdp) {
  const uint8_t action = fdp.ConsumeIntegralInRange(0, 13);
  switch (action) {
    case 1:
      injectAclData(GetArbitraryBytes(&fdp));
      break;
    case 2:
      injectCommandComplete(GetArbitraryBytes(&fdp));
      break;
    case 3:
      injectCommandStatus(GetArbitraryBytes(&fdp));
      break;
    case 4:
      injectEvent(fdp);
      break;
    case 5:
      injectLeEvent(fdp);
      break;
    case 6:
      injectSecurityEvent(GetArbitraryBytes(&fdp));
      break;
    case 7:
      injectLeSecurityEvent(GetArbitraryBytes(&fdp));
      break;
    case 8:
      injectAclEvent(GetArbitraryBytes(&fdp));
      break;
    case 9:
      injectAclDisconnect(fdp);
      break;
    case 10:
      injectLeAclEvent(GetArbitraryBytes(&fdp));
      break;
    case 11:
      injectLeAclDisconnect(fdp);
      break;
    case 12:
      injectLeAdvertisingEvent(GetArbitraryBytes(&fdp));
      break;
    case 13:
      injectLeScanningEvent(GetArbitraryBytes(&fdp));
      break;
  }
}

void FuzzHciLayer::injectAclData(std::vector<uint8_t> data) {
  CONSTRUCT_VALID_UNIQUE_OTHERWISE_BAIL(hci::AclView, packet, data);
  acl_inject_->Inject(std::move(packet));
}

void FuzzHciLayer::injectCommandComplete(std::vector<uint8_t> data) {
  InvokeIfValid<hci::CommandCompleteView>(std::move(on_command_complete_), data);
}

void FuzzHciLayer::injectCommandStatus(std::vector<uint8_t> data) {
  InvokeIfValid<hci::CommandStatusView>(std::move(on_command_status_), data);
}

void FuzzHciLayer::injectEvent(FuzzedDataProvider& fdp) {
  auto handler_pair = event_handlers_.find(static_cast<EventCode>(fdp.ConsumeIntegral<uint8_t>()));
  if (handler_pair != event_handlers_.end()) {
    InvokeIfValid<EventView>(handler_pair->second, GetArbitraryBytes(&fdp));
  }
}

void FuzzHciLayer::injectLeEvent(FuzzedDataProvider& fdp) {
  auto handler_pair = le_event_handlers_.find(static_cast<SubeventCode>(fdp.ConsumeIntegral<uint8_t>()));
  if (handler_pair != le_event_handlers_.end()) {
    InvokeIfValid<LeMetaEventView>(handler_pair->second, GetArbitraryBytes(&fdp));
  }
}

void FuzzHciLayer::injectSecurityEvent(std::vector<uint8_t> data) {
  InvokeIfValid<EventView>(security_event_handler_, data);
}

void FuzzHciLayer::injectLeSecurityEvent(std::vector<uint8_t> data) {
  InvokeIfValid<LeMetaEventView>(le_security_event_handler_, data);
}

void FuzzHciLayer::injectAclEvent(std::vector<uint8_t> data) {
  InvokeIfValid<EventView>(acl_event_handler_, data);
}

void FuzzHciLayer::injectAclDisconnect(FuzzedDataProvider& fdp) {
  acl_on_disconnect_.InvokeIfNotEmpty(fdp.ConsumeIntegral<uint16_t>(),
                                      static_cast<hci::ErrorCode>(fdp.ConsumeIntegral<uint8_t>()));
}

void FuzzHciLayer::injectLeAclEvent(std::vector<uint8_t> data) {
  InvokeIfValid<LeMetaEventView>(le_acl_event_handler_, data);
}

void FuzzHciLayer::injectLeAclDisconnect(FuzzedDataProvider& fdp) {
  le_acl_on_disconnect_.InvokeIfNotEmpty(fdp.ConsumeIntegral<uint16_t>(),
                                         static_cast<hci::ErrorCode>(fdp.ConsumeIntegral<uint8_t>()));
}

void FuzzHciLayer::injectLeAdvertisingEvent(std::vector<uint8_t> data) {
  InvokeIfValid<LeMetaEventView>(le_advertising_event_handler_, data);
}

void FuzzHciLayer::injectLeScanningEvent(std::vector<uint8_t> data) {
  InvokeIfValid<LeMetaEventView>(le_scanning_event_handler_, data);
}

void FuzzHciLayer::injectLeIsoEvent(std::vector<uint8_t> data) {
  InvokeIfValid<LeMetaEventView>(le_iso_event_handler_, data);
}

const ModuleFactory FuzzHciLayer::Factory = ModuleFactory([]() { return new FuzzHciLayer(); });

}  // namespace fuzz
}  // namespace hci
}  // namespace bluetooth
