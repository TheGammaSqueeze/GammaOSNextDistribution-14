// Copyright 2022 The Android Open Source Project
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

#include "hci/bluetooth_facade.h"

#include <sys/types.h>

#include <array>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <future>
#include <iostream>
#include <memory>
#include <unordered_map>
#include <utility>

#include "hci/address.h"
#include "hci/hci_packet_transport.h"
#include "model/setup/async_manager.h"
#include "model/setup/test_command_handler.h"
#include "model/setup/test_model.h"
#include "netsim-daemon/src/ffi.rs.h"
#include "netsim/config.pb.h"
#include "rust/cxx.h"
#include "util/filesystem.h"
#include "util/log.h"

#ifndef NETSIM_ANDROID_EMULATOR
#include "net/posix/posix_async_socket_server.h"
#endif

namespace rootcanal::log {
void SetLogColorEnable(bool);
}

using netsim::model::State;

namespace netsim::hci::facade {

int8_t SimComputeRssi(int send_id, int recv_id, int8_t tx_power);
void IncrTx(uint32_t send_id, rootcanal::Phy::Type phy_type);
void IncrRx(uint32_t receive_id, rootcanal::Phy::Type phy_type);

using namespace std::literals;
using namespace rootcanal;

using rootcanal::PhyDevice;
using rootcanal::PhyLayer;

class SimPhyLayer : public PhyLayer {
  // for constructor inheritance
  using PhyLayer::PhyLayer;

  // Overrides ComputeRssi in PhyLayerFactory to provide
  // simulated RSSI information using actual spatial
  // device positions.
  int8_t ComputeRssi(PhyDevice::Identifier sender_id,
                     PhyDevice::Identifier receiver_id,
                     int8_t tx_power) override {
    return SimComputeRssi(sender_id, receiver_id, tx_power);
  }

  // Check if the device is present in the phy_devices
  static bool Contains(
      PhyDevice::Identifier device_id,
      const std::list<std::shared_ptr<rootcanal::PhyDevice>> &phy_devices) {
    return std::any_of(
        phy_devices.begin(), phy_devices.end(),
        [device_id](const auto &device) { return device->id == device_id; });
  }

  // Overrides Send in PhyLayerFactory to add Rx/Tx statistics.
  void Send(std::vector<uint8_t> const &packet, int8_t tx_power,
            PhyDevice::Identifier sender_id) override {
    // Skip if the sender's phy is in the "down" state. Prevents all outgoing
    // messages including advertisements occurring when the radio is down.
    if (!Contains(sender_id, phy_devices_)) {
      return;
    }
    IncrTx(sender_id, type);
    for (const auto &device : phy_devices_) {
      if (sender_id != device->id) {
        IncrRx(device->id, type);
        device->Receive(packet, type,
                        ComputeRssi(sender_id, device->id, tx_power));
      }
    }
  }
};

class SimTestModel : public rootcanal::TestModel {
  // for constructor inheritance
  using rootcanal::TestModel::TestModel;

  std::unique_ptr<rootcanal::PhyLayer> CreatePhyLayer(
      PhyLayer::Identifier id, rootcanal::Phy::Type type) override {
    return std::make_unique<SimPhyLayer>(id, type);
  }
};

size_t phy_low_energy_index_;
size_t phy_classic_index_;

bool gStarted = false;
std::shared_ptr<rootcanal::AsyncManager> gAsyncManager;
rootcanal::AsyncUserId gSocketUserId{};
std::shared_ptr<SimTestModel> gTestModel;
std::shared_ptr<rootcanal::configuration::Controller> controller_proto_;

#ifndef NETSIM_ANDROID_EMULATOR
// test port
std::unique_ptr<rootcanal::TestCommandHandler> gTestChannel;
std::unique_ptr<rootcanal::TestChannelTransport> gTestChannelTransport;
std::shared_ptr<AsyncDataChannelServer> gTestSocketServer;
bool gTestChannelOpen{false};
constexpr int kDefaultTestPort = 7500;
#endif

namespace {
#ifndef NETSIM_ANDROID_EMULATOR

using ::android::net::PosixAsyncSocketServer;

void SetUpTestChannel(uint16_t instance_num) {
  gTestSocketServer = std::make_shared<PosixAsyncSocketServer>(
      kDefaultTestPort + instance_num - 1, gAsyncManager.get());

  gTestChannel = std::make_unique<rootcanal::TestCommandHandler>(*gTestModel);

  gTestChannelTransport = std::make_unique<rootcanal::TestChannelTransport>();
  gTestChannelTransport->RegisterCommandHandler(
      [](const std::string &name, const std::vector<std::string> &args) {
        gAsyncManager->ExecAsync(gSocketUserId, std::chrono::milliseconds(0),
                                 [name, args]() {
                                   std::string args_str = "";
                                   for (auto arg : args) args_str += " " + arg;
                                   if (name == "END_SIMULATION") {
                                   } else {
                                     gTestChannel->HandleCommand(name, args);
                                   }
                                 });
      });

  bool transport_configured = gTestChannelTransport->SetUp(
      gTestSocketServer, [](std::shared_ptr<AsyncDataChannel> conn_fd,
                            AsyncDataChannelServer *server) {
        BtsLogInfo("Test channel connection accepted.");
        server->StartListening();
        if (gTestChannelOpen) {
          BtsLogWarn("Only one connection at a time is supported");
          rootcanal::TestChannelTransport::SendResponse(
              conn_fd, "The connection is broken");
          return false;
        }
        gTestChannelOpen = true;
        gTestChannel->RegisterSendResponse(
            [conn_fd](const std::string &response) {
              rootcanal::TestChannelTransport::SendResponse(conn_fd, response);
            });

        conn_fd->WatchForNonBlockingRead([](AsyncDataChannel *conn_fd) {
          gTestChannelTransport->OnCommandReady(
              conn_fd, []() { gTestChannelOpen = false; });
        });
        return false;
      });

  gTestChannel->SetTimerPeriod({"5"});
  gTestChannel->StartTimer({});

  if (!transport_configured) {
    BtsLogError("Failed to set up test channel.");
    return;
  }

  BtsLogInfo("Set up test channel.");
}
#endif

}  // namespace

// Initialize the rootcanal library.
void Start(const rust::Slice<::std::uint8_t const> proto_bytes,
           uint16_t instance_num) {
  if (gStarted) return;

  // output is to a file, so no color wanted
  rootcanal::log::SetLogColorEnable(false);

  config::Bluetooth config;
  config.ParseFromArray(proto_bytes.data(), proto_bytes.size());
  controller_proto_ = std::make_shared<rootcanal::configuration::Controller>(
      config.properties());

  // When emulators restore from a snapshot the PacketStreamer connection to
  // netsim is recreated with a new (uninitialized) Rootcanal device. However
  // the Android Bluetooth Stack does not re-initialize the controller. Our
  // solution is for Rootcanal to recognize that it is receiving HCI commands
  // before a HCI Reset. The flag below causes a hardware error event that
  // triggers the Reset from the Bluetooth Stack.

  controller_proto_->mutable_quirks()->set_hardware_error_before_reset(true);

  gAsyncManager = std::make_shared<rootcanal::AsyncManager>();
  // Get a user ID for tasks scheduled within the test environment.
  gSocketUserId = gAsyncManager->GetNextUserId();

  gTestModel = std::make_unique<SimTestModel>(
      std::bind(&rootcanal::AsyncManager::GetNextUserId, gAsyncManager),
      std::bind(&rootcanal::AsyncManager::ExecAsync, gAsyncManager,
                std::placeholders::_1, std::placeholders::_2,
                std::placeholders::_3),
      std::bind(&rootcanal::AsyncManager::ExecAsyncPeriodically, gAsyncManager,
                std::placeholders::_1, std::placeholders::_2,
                std::placeholders::_3, std::placeholders::_4),
      std::bind(&rootcanal::AsyncManager::CancelAsyncTasksFromUser,
                gAsyncManager, std::placeholders::_1),
      std::bind(&rootcanal::AsyncManager::CancelAsyncTask, gAsyncManager,
                std::placeholders::_1),
      [](const std::string & /* server */, int /* port */,
         rootcanal::Phy::Type /* phy_type */) { return nullptr; });

  // Disable Address Reuse if '--disable_address_reuse' flag is true
  // Enable Address Reuse if 'address_reuse' is true
  if (config.has_disable_address_reuse()) {
    gTestModel->SetReuseDeviceAddresses(!config.disable_address_reuse());
  }

  // NOTE: 0:BR_EDR, 1:LOW_ENERGY. The order is used by bluetooth CTS.
  phy_classic_index_ = gTestModel->AddPhy(rootcanal::Phy::Type::BR_EDR);
  phy_low_energy_index_ = gTestModel->AddPhy(rootcanal::Phy::Type::LOW_ENERGY);

  // TODO: Remove test channel.
#ifdef NETSIM_ANDROID_EMULATOR
  auto testCommands = rootcanal::TestCommandHandler(*gTestModel);
  testCommands.RegisterSendResponse([](const std::string &) {});
  testCommands.SetTimerPeriod({"5"});
  testCommands.StartTimer({});
#else
  SetUpTestChannel(instance_num);
#endif
  gStarted = true;
};

// Resets the root canal library.
void Stop() {
  // TODO: Fix TestModel::Reset() in test_model.cc.
  // gTestModel->Reset();
  gStarted = false;
}

void AddDeviceToPhy(uint32_t rootcanal_id, bool isLowEnergy) {
  auto phy_index = (isLowEnergy) ? phy_low_energy_index_ : phy_classic_index_;
  gTestModel->AddDeviceToPhy(rootcanal_id, phy_index);
}

void RemoveDeviceFromPhy(uint32_t rootcanal_id, bool isLowEnergy) {
  auto phy_index = (isLowEnergy) ? phy_low_energy_index_ : phy_classic_index_;
  gTestModel->RemoveDeviceFromPhy(rootcanal_id, phy_index);
}

class ChipInfo {
 public:
  uint32_t chip_id;
  std::shared_ptr<model::Chip::Bluetooth> model;
  int le_tx_count = 0;
  int classic_tx_count = 0;
  int le_rx_count = 0;
  int classic_rx_count = 0;
  std::shared_ptr<rootcanal::configuration::Controller> controller_proto;
  std::unique_ptr<rootcanal::ControllerProperties> controller_properties;

  ChipInfo(uint32_t chip_id, std::shared_ptr<model::Chip::Bluetooth> model)
      : chip_id(chip_id), model(model) {}
  ChipInfo(
      uint32_t chip_id, std::shared_ptr<model::Chip::Bluetooth> model,
      std::shared_ptr<rootcanal::configuration::Controller> controller_proto,
      std::unique_ptr<rootcanal::ControllerProperties> controller_properties)
      : chip_id(chip_id),
        model(model),
        controller_proto(std::move(controller_proto)),
        controller_properties(std::move(controller_properties)) {}
};

std::unordered_map<uint32_t, std::shared_ptr<ChipInfo>> id_to_chip_info_;

model::Chip::Bluetooth Get(uint32_t id) {
  model::Chip::Bluetooth model;
  if (id_to_chip_info_.find(id) != id_to_chip_info_.end()) {
    model.CopyFrom(*id_to_chip_info_[id]->model.get());
    auto chip_info = id_to_chip_info_[id];
    model.mutable_classic()->set_tx_count(chip_info->classic_tx_count);
    model.mutable_classic()->set_rx_count(chip_info->classic_rx_count);
    model.mutable_low_energy()->set_tx_count(chip_info->le_tx_count);
    model.mutable_low_energy()->set_rx_count(chip_info->le_rx_count);
    if (chip_info->controller_proto) {
      model.mutable_bt_properties()->CopyFrom(*chip_info->controller_proto);
    }
  }
  return model;
}

void Reset(uint32_t id) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto chip_info = it->second;
    chip_info->le_tx_count = 0;
    chip_info->le_rx_count = 0;
    chip_info->classic_tx_count = 0;
    chip_info->classic_rx_count = 0;
  }
  // First remove LOW_ENERGY and BR_EDR Phy
  RemoveDeviceFromPhy(id, true);
  RemoveDeviceFromPhy(id, false);
  // Add to LOW_ENERGY and BR_EDR Phy
  AddDeviceToPhy(id, true);
  AddDeviceToPhy(id, false);
}

void Remove(uint32_t id) {
  BtsLogInfo("Removing HCI chip rootcanal_id: %d.", id);
  id_to_chip_info_.erase(id);
  // Call the transport close callback. This invokes HciDevice::Close and
  // TestModel close callback.
  gAsyncManager->ExecAsync(gSocketUserId, std::chrono::milliseconds(0), [id]() {
    // rootcanal will call HciPacketTransport::Close().
    HciPacketTransport::Remove(id);
  });
}

// Rename AddChip(model::Chip, device, transport)

uint32_t Add(uint32_t chip_id, const std::string &address_string,
             const rust::Slice<::std::uint8_t const> controller_proto_bytes) {
  auto transport = std::make_shared<HciPacketTransport>(chip_id, gAsyncManager);

  std::shared_ptr<rootcanal::configuration::Controller> controller_proto =
      controller_proto_;
  // If the Bluetooth Controller protobuf is provided, we use the provided
  if (controller_proto_bytes.size() != 0) {
    rootcanal::configuration::Controller custom_proto;
    custom_proto.ParseFromArray(controller_proto_bytes.data(),
                                controller_proto_bytes.size());
    BtsLogInfo("chip_id: %d has rootcanal Controller configuration: %s",
               chip_id, custom_proto.ShortDebugString().c_str());

    // When emulators restore from a snapshot the PacketStreamer connection to
    // netsim is recreated with a new (uninitialized) Rootcanal device. However
    // the Android Bluetooth Stack does not re-initialize the controller. Our
    // solution is for Rootcanal to recognize that it is receiving HCI commands
    // before a HCI Reset. The flag below causes a hardware error event that
    // triggers the Reset from the Bluetooth Stack.
    custom_proto.mutable_quirks()->set_hardware_error_before_reset(true);

    controller_proto =
        std::make_shared<rootcanal::configuration::Controller>(custom_proto);
  }
  std::unique_ptr<rootcanal::ControllerProperties> controller_properties =
      std::make_unique<rootcanal::ControllerProperties>(*controller_proto);

  auto hci_device =
      std::make_shared<rootcanal::HciDevice>(transport, *controller_properties);

  // Pass netsim::hci::facade::ReportInvalidPacket signature into hci_device
  hci_device->RegisterInvalidPacketHandler(
      [](uint32_t rootcanal_id, rootcanal::InvalidPacketReason reason,
         std::string description, const std::vector<uint8_t> &packet) {
        netsim::hci::facade::ReportInvalidPacket(
            rootcanal_id, static_cast<int32_t>(reason), description, packet);
      });

  // Use the `AsyncManager` to ensure that the `AddHciConnection` method is
  // invoked atomically, preventing data races.
  std::promise<uint32_t> rootcanal_id_promise;
  auto rootcanal_id_future = rootcanal_id_promise.get_future();

  std::optional<Address> address_option;
  if (address_string != "") {
    address_option = rootcanal::Address::FromString(address_string);
  }
  gAsyncManager->ExecAsync(
      gSocketUserId, std::chrono::milliseconds(0),
      [hci_device, &rootcanal_id_promise, address_option]() {
        rootcanal_id_promise.set_value(
            gTestModel->AddHciConnection(hci_device, address_option));
      });
  auto rootcanal_id = rootcanal_id_future.get();

  HciPacketTransport::Add(rootcanal_id, transport);
  BtsLogInfo("Creating HCI rootcanal_id: %d for chip_id: %d", rootcanal_id,
             chip_id);

  auto model = std::make_shared<model::Chip::Bluetooth>();
  model->mutable_classic()->set_state(model::State::ON);
  model->mutable_low_energy()->set_state(model::State::ON);

  id_to_chip_info_.emplace(rootcanal_id, std::make_shared<ChipInfo>(
                                             chip_id, model, controller_proto,
                                             std::move(controller_properties)));
  return rootcanal_id;
}

void RemoveRustDevice(uint32_t rootcanal_id) {
  gTestModel->RemoveDevice(rootcanal_id);
}

rust::Box<AddRustDeviceResult> AddRustDevice(
    uint32_t chip_id, rust::Box<DynRustBluetoothChipCallbacks> callbacks,
    const std::string &type, const std::string &address) {
  auto rust_device =
      std::make_shared<RustDevice>(std::move(callbacks), type, address);

  // TODO: Use the `AsyncManager` to ensure that the `AddDevice` and
  // `AddDeviceToPhy` methods are invoked atomically, preventing data races.
  // For unknown reason, use `AsyncManager` hangs.
  auto rootcanal_id = gTestModel->AddDevice(rust_device);
  gTestModel->AddDeviceToPhy(rootcanal_id, phy_low_energy_index_);

  auto model = std::make_shared<model::Chip::Bluetooth>();
  // Only enable ble for beacon.
  model->mutable_low_energy()->set_state(model::State::ON);
  id_to_chip_info_.emplace(rootcanal_id,
                           std::make_shared<ChipInfo>(chip_id, model));
  return CreateAddRustDeviceResult(
      rootcanal_id, std::make_unique<RustBluetoothChip>(rust_device));
}

void SetRustDeviceAddress(
    uint32_t rootcanal_id,
    std::array<uint8_t, rootcanal::Address::kLength> address) {
  uint8_t addr[rootcanal::Address::kLength];
  std::memcpy(addr, address.data(), rootcanal::Address::kLength);
  gTestModel->SetDeviceAddress(rootcanal_id, rootcanal::Address(addr));
}

void IncrTx(uint32_t id, rootcanal::Phy::Type phy_type) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto chip_info = it->second;
    if (phy_type == rootcanal::Phy::Type::LOW_ENERGY) {
      chip_info->le_tx_count++;
    } else {
      chip_info->classic_tx_count++;
    }
  }
}

void IncrRx(uint32_t id, rootcanal::Phy::Type phy_type) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto chip_info = it->second;
    if (phy_type == rootcanal::Phy::Type::LOW_ENERGY) {
      chip_info->le_rx_count++;
    } else {
      chip_info->classic_rx_count++;
    }
  }
}

// TODO: Make SimComputeRssi invoke netsim::device::GetDistanceRust with dev
// flag
int8_t SimComputeRssi(int send_id, int recv_id, int8_t tx_power) {
  if (id_to_chip_info_.find(send_id) == id_to_chip_info_.end() ||
      id_to_chip_info_.find(recv_id) == id_to_chip_info_.end()) {
#ifdef NETSIM_ANDROID_EMULATOR
    // NOTE: Ignore log messages in Cuttlefish for beacon devices created by
    // test channel.
    BtsLogWarn("Missing chip_info");
#endif
    return tx_power;
  }
  auto a = id_to_chip_info_[send_id]->chip_id;
  auto b = id_to_chip_info_[recv_id]->chip_id;
  auto distance = netsim::device::GetDistanceCxx(a, b);
  return netsim::DistanceToRssi(tx_power, distance);
}

rust::Vec<::std::uint8_t> GetCxx(uint32_t id) {
  auto bluetooth = Get(id);
  std::vector<uint8_t> proto_bytes(bluetooth.ByteSizeLong());
  bluetooth.SerializeToArray(proto_bytes.data(), proto_bytes.size());
  rust::Vec<uint8_t> proto_rust_bytes;
  std::copy(proto_bytes.begin(), proto_bytes.end(),
            std::back_inserter(proto_rust_bytes));
  return proto_rust_bytes;
}

}  // namespace netsim::hci::facade
