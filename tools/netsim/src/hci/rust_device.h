// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include <cstdint>
#include <memory>
#include <string>

#include "model/devices/device.h"
#include "packets/link_layer_packets.h"
#include "phy.h"
#include "rust/cxx.h"

namespace netsim::hci::facade {
// Use forward declaration instead of including "netsim-cxx/src/lib.rs.h".
struct DynRustBluetoothChipCallbacks;
class RustBluetoothChip;

class RustDevice : public rootcanal::Device {
 public:
  RustDevice(rust::Box<DynRustBluetoothChipCallbacks> callbacks,
             const std::string &type, const std::string &address)
      : callbacks_(std::move(callbacks)), TYPE(type) {
    rootcanal::Address::FromString(address, address_);
    this->SetAddress(address_);
  }

  void Tick() override;
  std::string GetTypeString() const override { return TYPE; }
  std::string ToString() const override { return TYPE; }
  void Close() override {}
  void ReceiveLinkLayerPacket(::model::packets::LinkLayerPacketView packet,
                              rootcanal::Phy::Type type, int8_t rssi) override;
  void SetRustBluetoothChip(std::unique_ptr<RustBluetoothChip>);

 private:
  rust::Box<DynRustBluetoothChipCallbacks> callbacks_;
  const std::string TYPE;
};

/// Delegation class for RustDevice to be used in Rust.
class RustBluetoothChip {
 public:
  RustBluetoothChip(std::shared_ptr<RustDevice> rust_device)
      : rust_device(std::move(rust_device)) {}

  void SendLinkLayerLePacket(const rust::Slice<const uint8_t> packet,
                             int8_t tx_power) const;

 private:
  std::shared_ptr<RustDevice> rust_device;
};
}  // namespace netsim::hci::facade