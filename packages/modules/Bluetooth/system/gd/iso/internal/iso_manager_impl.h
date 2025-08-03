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

#include <list>
#include <vector>

#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"
#include "os/handler.h"

namespace bluetooth {
namespace iso {
using SetCigParametersCallback = common::ContextualOnceCallback<void(std::vector<uint16_t>)>;
using CisEstablishedCallback = common::ContextualCallback<void(uint16_t)>;
using IsoDataCallback = common::ContextualCallback<void(std::unique_ptr<hci::IsoView>)>;

namespace internal {

struct IsochronousConnection {
  uint16_t connection_handle;
  uint8_t cig_id;
  uint8_t cis_id;
};

class IsoManagerImpl {
 public:
  explicit IsoManagerImpl(os::Handler* iso_handler, hci::HciLayer* hci_layer, hci::Controller* controller);
  ~IsoManagerImpl();

  void RegisterIsoEstablishedCallback(CisEstablishedCallback cb) {
    this->cis_established_callback = cb;
  }

  void RegisterIsoDataCallback(IsoDataCallback cb) {
    this->iso_data_callback = cb;
  }

  void OnHciLeEvent(hci::LeMetaEventView event);

  void SetCigParameters(
      uint8_t cig_id,
      uint32_t sdu_interval_m_to_s,
      uint32_t sdu_interval_s_to_m,
      hci::ClockAccuracy peripherals_clock_accuracy,
      hci::Packing packing,
      hci::Enable framing,
      uint16_t max_transport_latency_m_to_s,
      uint16_t max_transport_latency_s_to_m,
      const std::vector<hci::CisParametersConfig>& cis_config,
      SetCigParametersCallback command_complete_callback);
  void SetCigParametersComplete(
      uint8_t cig_id,
      const std::vector<hci::CisParametersConfig>& cis_configs,
      SetCigParametersCallback command_complete_callback,
      hci::CommandCompleteView command_complete);

  void SetCigParametersTest(
      uint8_t cig_id,
      uint32_t sdu_interval_m_to_s,
      uint32_t sdu_interval_s_to_m,
      uint8_t ft_m_to_s,
      uint8_t ft_s_to_m,
      uint16_t iso_interval,
      hci::ClockAccuracy peripherals_clock_accuracy,
      hci::Packing packing,
      hci::Enable framing,
      uint16_t max_transport_latency_m_to_s,
      uint16_t max_transport_latency_s_to_m,
      const std::vector<hci::LeCisParametersTestConfig>& cis_config,
      SetCigParametersCallback command_complete_callback);
  void SetCigParametersTestComplete(
      uint8_t cig_id,
      const std::vector<hci::LeCisParametersTestConfig>& cis_configs,
      SetCigParametersCallback command_complete_callback,
      hci::CommandCompleteView command_complete);

  void LeCreateCis(std::vector<std::pair<uint16_t, uint16_t>> cis_and_acl_handles);

  void RemoveCig(uint8_t cig_id);
  void RemoveCigComplete(hci::CommandCompleteView command_complete);

  void SendIsoPacket(uint16_t cis_handle, std::vector<uint8_t> packet);
  void OnIncomingPacket();

  bool IsKnownCig(uint8_t cig_id) {
    return find_if(iso_connections_.begin(), iso_connections_.end(), [cig_id](const IsochronousConnection& c) {
             return c.cig_id == cig_id;
           }) != iso_connections_.end();
  }

 private:
  os::Handler* iso_handler_;
  hci::HciLayer* hci_layer_;
  hci::LeIsoInterface* hci_le_iso_interface_;
  std::unique_ptr<os::EnqueueBuffer<bluetooth::hci::IsoBuilder>> iso_enqueue_buffer_;
  hci::Controller* controller_ __attribute__((unused));
  std::list<IsochronousConnection> iso_connections_;
  CisEstablishedCallback cis_established_callback;
  IsoDataCallback iso_data_callback;
};
}  // namespace internal
}  // namespace iso
}  // namespace bluetooth
