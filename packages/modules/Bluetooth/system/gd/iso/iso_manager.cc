/*
 *
 *  Copyright 2020 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
#include "iso_manager.h"

#include "os/log.h"

namespace bluetooth {
namespace iso {

void IsoManager::RegisterIsoEstablishedCallback(CisEstablishedCallback cb) {
  iso_handler_->CallOn(iso_manager_impl_, &internal::IsoManagerImpl::RegisterIsoEstablishedCallback, cb);
}

void IsoManager::RegisterIsoDataCallback(IsoDataCallback cb) {
  iso_handler_->CallOn(iso_manager_impl_, &internal::IsoManagerImpl::RegisterIsoDataCallback, cb);
}

void IsoManager::SetCigParameters(
    uint8_t cig_id,
    uint32_t sdu_interval_m_to_s,
    uint32_t sdu_interval_s_to_m,
    hci::ClockAccuracy peripherals_clock_accuracy,
    hci::Packing packing,
    hci::Enable framing,
    uint16_t max_transport_latency_m_to_s,
    uint16_t max_transport_latency_s_to_m,
    std::vector<hci::CisParametersConfig> cis_config,
    SetCigParametersCallback command_complete_callback) {
  iso_handler_->CallOn(
      iso_manager_impl_,
      &internal::IsoManagerImpl::SetCigParameters,
      cig_id,
      sdu_interval_m_to_s,
      sdu_interval_s_to_m,
      peripherals_clock_accuracy,
      packing,
      framing,
      max_transport_latency_m_to_s,
      max_transport_latency_s_to_m,
      cis_config,
      std::move(command_complete_callback));
}

void IsoManager::SetCigParametersTest(
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
    std::vector<hci::LeCisParametersTestConfig> cis_config,
    SetCigParametersCallback command_complete_callback) {
  iso_handler_->CallOn(
      iso_manager_impl_,
      &internal::IsoManagerImpl::SetCigParametersTest,
      cig_id,
      sdu_interval_m_to_s,
      sdu_interval_s_to_m,
      ft_m_to_s,
      ft_s_to_m,
      iso_interval,
      peripherals_clock_accuracy,
      packing,
      framing,
      max_transport_latency_m_to_s,
      max_transport_latency_s_to_m,
      cis_config,
      std::move(command_complete_callback));
}

void IsoManager::LeCreateCis(std::vector<std::pair<uint16_t, uint16_t>> cis_and_acl_handles) {
  iso_handler_->CallOn(iso_manager_impl_, &internal::IsoManagerImpl::LeCreateCis, cis_and_acl_handles);
}

void IsoManager::RemoveCig(uint8_t cig_id) {
  iso_handler_->CallOn(iso_manager_impl_, &internal::IsoManagerImpl::RemoveCig, cig_id);
}

void IsoManager::SendIsoPacket(uint16_t cis_handle, std::vector<uint8_t> packet) {
  iso_handler_->CallOn(iso_manager_impl_, &internal::IsoManagerImpl::SendIsoPacket, cis_handle, packet);
}

}  // namespace iso
}  // namespace bluetooth
