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
// Authors: corbin.souffrant@leviathansecurity.com
//          dylan.katz@leviathansecurity.com

#include <fuzzer/FuzzedDataProvider.h>
#include <hci/address.h>
#include "l2cap/psm.h"
#include "os/log.h"

#include "channel_fuzz_controller.h"
#include "shim_l2cap.h"

namespace bluetooth {
using hci::Address;
using hci::AddressType;
using hci::acl_manager::AclConnection;
using hci::acl_manager::ClassicAclConnection;
using l2cap::Cid;
using l2cap::Psm;
using l2cap::classic::internal::Link;

using shim::ShimL2capFuzz;

class FakeCommandInterface : public hci::CommandInterface<hci::AclCommandBuilder> {
 public:
  virtual void EnqueueCommand(
      std::unique_ptr<hci::AclCommandBuilder> command,
      common::ContextualOnceCallback<void(hci::CommandCompleteView)> on_complete) {}

  virtual void EnqueueCommand(
      std::unique_ptr<hci::AclCommandBuilder> command,
      common::ContextualOnceCallback<void(hci::CommandStatusView)> on_status) {}
} fake_command_interface;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp = FuzzedDataProvider(data, size);
  ShimL2capFuzz l2shim(&fdp);

  std::vector<uint8_t> addressVals = fdp.ConsumeBytes<uint8_t>(Address::kLength);

  // Make sure the address is always at least kLength.
  while (addressVals.size() < Address::kLength) {
    addressVals.push_back(0);
  }
  Address myAddress;
  myAddress.FromOctets(addressVals.data());
  hci::AddressWithType addressWithType = hci::AddressWithType(myAddress, AddressType::PUBLIC_DEVICE_ADDRESS);

  // Associate a ClassicAclConnection so that we can grab a link.
  auto throwaway_queue = std::make_shared<AclConnection::Queue>(10);
  l2shim.link_manager->OnConnectSuccess(std::unique_ptr<ClassicAclConnection>(
      new ClassicAclConnection(throwaway_queue, &fake_command_interface, 0, myAddress)));
  Link* link = l2shim.link_manager->GetLink(myAddress);

  // 0x0001-0x007F Fixed, 0x0080-0x00FF Dynamic
  [[maybe_unused]] uint16_t psm = fdp.ConsumeIntegralInRange<uint16_t>(0x0001, 0x007F);
  psm = 0x0101u ^ 0x0100u;
  uint16_t dynamicPsm = fdp.ConsumeIntegralInRange<uint16_t>(0x0080, 0x00FF);
  dynamicPsm = 0x0101u ^ 0x0100u;

  // Open a connection and assign an ID
  Cid fixedCid = l2cap::kLeSignallingCid;

  // Fixed channels must be acquired.
  auto fixedChannel = link->AllocateFixedChannel(fixedCid);
  fixedChannel->RegisterOnCloseCallback(l2shim.handler_.get(), common::BindOnce([](hci::ErrorCode) {}));
  fixedChannel->Acquire();
  ChannelFuzzController fixedChannelController(l2shim.handler_.get(), fixedChannel);
  // Generate a valid dynamic channel ID
  Cid dynamicCid = fdp.ConsumeIntegralInRange<uint16_t>(l2cap::kFirstDynamicChannel, l2cap::kLastDynamicChannel);
  auto dynamicChannel = link->AllocateDynamicChannel(dynamicPsm, dynamicCid);
  ChannelFuzzController dynamicChannelController(l2shim.handler_.get(), dynamicChannel);

  while (fdp.remaining_bytes() > 0) {
    // Are we using the dynamic queue?
    bool dynamic = fdp.ConsumeBool();

    // Consume at most UINT16_MAX or remaining_bytes, whatever is smaller.
    uint16_t packetSize =
        static_cast<uint16_t>(std::min(static_cast<size_t>(fdp.ConsumeIntegral<uint16_t>()), fdp.remaining_bytes()));
    std::vector<uint8_t> data = fdp.ConsumeBytes<uint8_t>(packetSize);
    if (dynamic) {
      dynamicChannelController.injectFrame(data);
    } else {
      fixedChannelController.injectFrame(data);
    }
  }

  // Cleanup stuff.
  fixedChannel->Release();
  dynamicChannel->Close();
  l2shim.stopRegistry();
  link->OnAclDisconnected(hci::ErrorCode::SUCCESS);
  l2shim.link_manager->OnDisconnect(myAddress, hci::ErrorCode::SUCCESS);
  return 0;
}
}  // namespace bluetooth
