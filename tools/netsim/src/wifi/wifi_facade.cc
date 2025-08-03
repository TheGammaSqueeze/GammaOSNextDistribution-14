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

#include "wifi/wifi_facade.h"

#include <memory>
#include <optional>

#include "netsim-daemon/src/ffi.rs.h"
#include "netsim/config.pb.h"
#include "netsim/hci_packet.pb.h"
#include "rust/cxx.h"
#include "util/log.h"
#ifdef NETSIM_ANDROID_EMULATOR
#include "android-qemu2-glue/emulation/WifiService.h"
#endif

namespace netsim::wifi {
namespace {

class ChipInfo {
 public:
  std::shared_ptr<model::Chip::Radio> model;

  ChipInfo(std::shared_ptr<model::Chip::Radio> model)
      : model(std::move(model)) {}
};

std::unordered_map<uint32_t, std::shared_ptr<ChipInfo>> id_to_chip_info_;
#ifdef NETSIM_ANDROID_EMULATOR
std::shared_ptr<android::qemu2::WifiService> wifi_service;
#endif
;

bool ChangedState(model::State a, model::State b) {
  return (b != model::State::UNKNOWN && a != b);
}

std::optional<model::State> GetState(uint32_t id) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto &model = it->second->model;
    return model->state();
  }
  BtsLogWarn("Failed to get WiFi state with unknown chip_id %d", id);
  return std::nullopt;
}

void IncrTx(uint32_t id) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto &model = it->second->model;
    model->set_tx_count(model->tx_count() + 1);
  }
}

void IncrRx(uint32_t id) {
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto &model = it->second->model;
    model->set_rx_count(model->rx_count() + 1);
  }
}

}  // namespace

namespace facade {

void Reset(uint32_t id) {
  BtsLog("wifi::facade::Reset(%d)", id);
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    auto chip_info = it->second;
    chip_info->model->set_state(model::State::ON);
    chip_info->model->set_tx_count(0);
    chip_info->model->set_rx_count(0);
  }
}
void Remove(uint32_t id) {
  BtsLog("wifi::facade::Remove(%d)", id);
  id_to_chip_info_.erase(id);
}

void Patch(uint32_t id, const model::Chip::Radio &request) {
  BtsLog("wifi::facade::Patch(%d)", id);
  auto it = id_to_chip_info_.find(id);
  if (it == id_to_chip_info_.end()) {
    BtsLogWarn("Patch an unknown chip_id: %d", id);
    return;
  }
  auto &model = it->second->model;
  if (ChangedState(model->state(), request.state())) {
    model->set_state(request.state());
  }
}

model::Chip::Radio Get(uint32_t id) {
  BtsLog("wifi::facade::Get(%d)", id);
  model::Chip::Radio radio;
  if (auto it = id_to_chip_info_.find(id); it != id_to_chip_info_.end()) {
    radio.CopyFrom(*it->second->model);
  }
  return radio;
}

void PatchCxx(uint32_t id,
              const rust::Slice<::std::uint8_t const> proto_bytes) {
  model::Chip::Radio radio;
  radio.ParseFromArray(proto_bytes.data(), proto_bytes.size());
  Patch(id, radio);
}

rust::Vec<uint8_t> GetCxx(uint32_t id) {
  auto radio = Get(id);
  std::vector<uint8_t> proto_bytes(radio.ByteSizeLong());
  radio.SerializeToArray(proto_bytes.data(), proto_bytes.size());
  rust::Vec<uint8_t> proto_rust_bytes;
  std::copy(proto_bytes.begin(), proto_bytes.end(),
            std::back_inserter(proto_rust_bytes));
  return proto_rust_bytes;
}

void Add(uint32_t chip_id) {
  BtsLog("wifi::facade::Add(%d)", chip_id);

  auto model = std::make_shared<model::Chip::Radio>();
  model->set_state(model::State::ON);
  id_to_chip_info_.emplace(chip_id, std::make_shared<ChipInfo>(model));
}

size_t HandleWifiCallback(const uint8_t *buf, size_t size) {
  //  Broadcast the response to all WiFi chips.
  std::vector<uint8_t> packet(buf, buf + size);
  for (auto [chip_id, chip_info] : id_to_chip_info_) {
    if (auto state = chip_info->model->state();
        !state || state == model::State::OFF) {
      continue;
    }
    netsim::wifi::IncrRx(chip_id);
    echip::HandleResponse(chip_id, packet,
                          packet::HCIPacket::HCI_PACKET_UNSPECIFIED);
  }
  return size;
}

void Start(const rust::Slice<::std::uint8_t const> proto_bytes) {
#ifdef NETSIM_ANDROID_EMULATOR
  // Initialize hostapd and slirp inside WiFi Service.
  config::WiFi config;
  config.ParseFromArray(proto_bytes.data(), proto_bytes.size());

  android::qemu2::HostapdOptions hostapd = {
      .disabled = config.hostapd_options().disabled(),
      .ssid = config.hostapd_options().ssid(),
      .passwd = config.hostapd_options().passwd()};

  android::qemu2::SlirpOptions slirpOpts = {
      .disabled = config.slirp_options().disabled(),
      .ipv4 = (config.slirp_options().has_ipv4() ? config.slirp_options().ipv4()
                                                 : true),
      .restricted = config.slirp_options().restricted(),
      .vnet = config.slirp_options().vnet(),
      .vhost = config.slirp_options().vhost(),
      .vmask = config.slirp_options().vmask(),
      .ipv6 = (config.slirp_options().has_ipv6() ? config.slirp_options().ipv6()
                                                 : true),
      .vprefix6 = config.slirp_options().vprefix6(),
      .vprefixLen = (uint8_t)config.slirp_options().vprefixlen(),
      .vhost6 = config.slirp_options().vhost6(),
      .vhostname = config.slirp_options().vhostname(),
      .tftpath = config.slirp_options().tftpath(),
      .bootfile = config.slirp_options().bootfile(),
      .dhcpstart = config.slirp_options().dhcpstart(),
      .dns = config.slirp_options().dns(),
      .dns6 = config.slirp_options().dns6(),
  };

  auto builder = android::qemu2::WifiService::Builder()
                     .withHostapd(hostapd)
                     .withSlirp(slirpOpts)
                     .withOnReceiveCallback(HandleWifiCallback)
                     .withVerboseLogging(true);
  wifi_service = builder.build();
  if (!wifi_service->init()) {
    BtsLogWarn("Failed to initialize wifi service");
  }
#endif
}
void Stop() {
#ifdef NETSIM_ANDROID_EMULATOR
  wifi_service->stop();
#endif
}

}  // namespace facade

void HandleWifiRequest(uint32_t chip_id,
                       const std::shared_ptr<std::vector<uint8_t>> &packet) {
#ifdef NETSIM_ANDROID_EMULATOR
  if (auto state = GetState(chip_id); !state || state == model::State::OFF) {
    return;
  }
  netsim::wifi::IncrTx(chip_id);
  // Send the packet to the WiFi service.
  struct iovec iov[1];
  iov[0].iov_base = packet->data();
  iov[0].iov_len = packet->size();
  wifi_service->send(android::base::IOVector(iov, iov + 1));
#endif
}

void HandleWifiRequestCxx(uint32_t chip_id, const rust::Vec<uint8_t> &packet) {
  std::vector<uint8_t> buffer(packet.begin(), packet.end());
  auto packet_ptr = std::make_shared<std::vector<uint8_t>>(buffer);
  HandleWifiRequest(chip_id, packet_ptr);
}

}  // namespace netsim::wifi
