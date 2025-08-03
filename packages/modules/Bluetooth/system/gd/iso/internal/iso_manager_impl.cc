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
#include "iso_manager_impl.h"

#include "common/bind.h"
#include "hci/hci_packets.h"
#include "iso/iso_manager.h"
#include "os/handler.h"
#include "os/log.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace iso {
namespace internal {

using bluetooth::hci::IsoBuilder;

IsoManagerImpl::IsoManagerImpl(os::Handler* iso_handler, hci::HciLayer* hci_layer, hci::Controller* controller)
    : iso_handler_(iso_handler),
      hci_layer_(hci_layer),
      hci_le_iso_interface_(hci_layer->GetLeIsoInterface(iso_handler_->BindOn(this, &IsoManagerImpl::OnHciLeEvent))),
      controller_(controller) {
  hci_layer_->GetIsoQueueEnd()->RegisterDequeue(
      iso_handler_, common::Bind(&IsoManagerImpl::OnIncomingPacket, common::Unretained(this)));
  iso_enqueue_buffer_ = std::make_unique<os::EnqueueBuffer<IsoBuilder>>(hci_layer_->GetIsoQueueEnd());
}

IsoManagerImpl::~IsoManagerImpl() {
  hci_layer_->GetIsoQueueEnd()->UnregisterDequeue();
  iso_enqueue_buffer_ = nullptr;
}

void IsoManagerImpl::OnHciLeEvent(hci::LeMetaEventView event) {
  hci::SubeventCode code = event.GetSubeventCode();

  if (code == hci::SubeventCode::CIS_ESTABLISHED) {
    hci::LeCisEstablishedView le_cis_established_view = hci::LeCisEstablishedView::Create(event);
    if (!le_cis_established_view.IsValid()) {
      LOG_ERROR("Invalid LeCisEstablishedView packet received");
      return;
    }

    cis_established_callback.Invoke(le_cis_established_view.GetConnectionHandle());
    return;
  } else if (code == hci::SubeventCode::CIS_REQUEST) {
    hci::LeCisRequestView le_cis_request_view = hci::LeCisRequestView::Create(event);
    if (!le_cis_request_view.IsValid()) {
      LOG_ERROR("Invalid LeCisRequestView packet received");
      return;
    }

    hci_le_iso_interface_->EnqueueCommand(
        hci::LeAcceptCisRequestBuilder::Create(le_cis_request_view.GetCisConnectionHandle()),
        iso_handler_->BindOnce([](hci::CommandStatusView command_status) {
          LOG_INFO("command_status=%hhu ", command_status.GetStatus());
        }));

    return;
  }

  LOG_ERROR("Unhandled HCI LE ISO event, code %s", hci::SubeventCodeText(code).c_str());
  ASSERT_LOG(false, "Unhandled HCI LE ISO event");
}

void IsoManagerImpl::SetCigParameters(
    uint8_t cig_id,
    uint32_t sdu_interval_m_to_s,
    uint32_t sdu_interval_s_to_m,
    hci::ClockAccuracy peripherals_clock_accuracy,
    hci::Packing packing,
    hci::Enable framing,
    uint16_t max_transport_latency_m_to_s,
    uint16_t max_transport_latency_s_to_m,
    const std::vector<hci::CisParametersConfig>& cis_configs,
    SetCigParametersCallback command_complete_callback) {
  hci_le_iso_interface_->EnqueueCommand(
      hci::LeSetCigParametersBuilder::Create(
          cig_id,
          sdu_interval_m_to_s,
          sdu_interval_s_to_m,
          peripherals_clock_accuracy,
          packing,
          framing,
          max_transport_latency_m_to_s,
          max_transport_latency_s_to_m,
          cis_configs),
      iso_handler_->BindOnce(
          &IsoManagerImpl::SetCigParametersComplete,
          common::Unretained(this),
          cig_id,
          cis_configs,
          std::move(command_complete_callback)));
}

void IsoManagerImpl::SetCigParametersComplete(
    uint8_t cig_id,
    const std::vector<hci::CisParametersConfig>& cis_configs,
    SetCigParametersCallback command_complete_callback,
    hci::CommandCompleteView command_complete) {
  ASSERT(command_complete.IsValid());

  hci::LeSetCigParametersCompleteView setCigParamsComplete =
      hci::LeSetCigParametersCompleteView::Create(command_complete);
  ASSERT(setCigParamsComplete.IsValid());

  if (setCigParamsComplete.GetStatus() == hci::ErrorCode::SUCCESS) {
    uint8_t cig_id_back_from_ctrl = setCigParamsComplete.GetCigId();
    auto conn_handles = setCigParamsComplete.GetConnectionHandle();

    ASSERT(cig_id_back_from_ctrl == cig_id);
    ASSERT(conn_handles.size() == cis_configs.size());

    auto cis_it = cis_configs.begin();
    auto handle_it = conn_handles.begin();

    std::vector<uint16_t> handles;
    while (cis_it != cis_configs.end()) {
      iso_connections_.push_back({
          .connection_handle = *handle_it,
          .cig_id = cig_id,
          .cis_id = cis_it->cis_id_,
      });

      handles.push_back(*handle_it);

      cis_it++;
      handle_it++;
    }

    command_complete_callback.Invoke(handles);
  }
}

void IsoManagerImpl::SetCigParametersTest(
    uint8_t cig_id,
    uint32_t sdu_interval_m_to_s,
    uint32_t sdu_interval_s_to_m,
    uint8_t ft_m_to_s,
    uint8_t ft_s_to_m,
    uint16_t iso_interval,
    hci::ClockAccuracy peripherals_clock_accuracy,
    hci::Packing packing,
    hci::Enable framing,
    uint16_t /* max_transport_latency_m_to_s */,
    uint16_t /* max_transport_latency_s_to_m */,
    const std::vector<hci::LeCisParametersTestConfig>& cis_test_configs,
    SetCigParametersCallback command_complete_callback) {
  hci_le_iso_interface_->EnqueueCommand(
      hci::LeSetCigParametersTestBuilder::Create(
          cig_id,
          sdu_interval_m_to_s,
          sdu_interval_s_to_m,
          ft_m_to_s,
          ft_s_to_m,
          iso_interval,
          peripherals_clock_accuracy,
          packing,
          framing,
          cis_test_configs),
      iso_handler_->BindOnce(
          &IsoManagerImpl::SetCigParametersTestComplete,
          common::Unretained(this),
          cig_id,
          cis_test_configs,
          std::move(command_complete_callback)));
}

void IsoManagerImpl::SetCigParametersTestComplete(
    uint8_t cig_id,
    const std::vector<hci::LeCisParametersTestConfig>& cis_configs,
    SetCigParametersCallback command_complete_callback,
    hci::CommandCompleteView command_complete) {
  ASSERT(command_complete.IsValid());

  hci::LeSetCigParametersTestCompleteView setCigParamsComplete =
      hci::LeSetCigParametersTestCompleteView::Create(command_complete);
  ASSERT(setCigParamsComplete.IsValid());

  if (setCigParamsComplete.GetStatus() == hci::ErrorCode::SUCCESS) {
    uint8_t cig_id_back_from_ctrl = setCigParamsComplete.GetCigId();
    auto conn_handles = setCigParamsComplete.GetConnectionHandle();

    ASSERT(cig_id_back_from_ctrl == cig_id);
    ASSERT(conn_handles.size() == cis_configs.size());

    auto cis_it = cis_configs.begin();
    auto handle_it = conn_handles.begin();

    std::vector<uint16_t> handles;
    while (cis_it != cis_configs.end()) {
      iso_connections_.push_back({
          .connection_handle = *handle_it,
          .cig_id = cig_id,
          .cis_id = cis_it->cis_id_,
      });

      handles.push_back(*handle_it);

      cis_it++;
      handle_it++;
    }

    command_complete_callback.Invoke(handles);
  }
}

void IsoManagerImpl::LeCreateCis(std::vector<std::pair<uint16_t, uint16_t>> cis_and_acl_handles) {
  std::vector<hci::CreateCisConfig> cis_configs;

  for (const auto& handle_pair : cis_and_acl_handles) {
    hci::CreateCisConfig config;
    config.cis_connection_handle_ = handle_pair.first;
    config.acl_connection_handle_ = handle_pair.second;
    cis_configs.push_back(config);
  }

  hci_le_iso_interface_->EnqueueCommand(
      hci::LeCreateCisBuilder::Create(cis_configs), iso_handler_->BindOnce([](hci::CommandStatusView command_status) {
        LOG_INFO("command_status=%hhu ", command_status.GetStatus());
      }));
}

void IsoManagerImpl::RemoveCig(uint8_t cig_id) {
  ASSERT(IsKnownCig(cig_id));

  hci_le_iso_interface_->EnqueueCommand(
      hci::LeRemoveCigBuilder::Create(cig_id),
      iso_handler_->BindOnce(&IsoManagerImpl::RemoveCigComplete, common::Unretained(this)));
}

void IsoManagerImpl::RemoveCigComplete(hci::CommandCompleteView command_complete) {
  ASSERT(command_complete.IsValid());

  hci::LeRemoveCigCompleteView removeCigComplete = hci::LeRemoveCigCompleteView::Create(command_complete);
  ASSERT(removeCigComplete.IsValid());
}

void IsoManagerImpl::SendIsoPacket(uint16_t cis_handle, std::vector<uint8_t> packet) {
  auto builder = hci::IsoWithoutTimestampBuilder::Create(
      cis_handle,
      hci::IsoPacketBoundaryFlag::COMPLETE_SDU,
      0 /* sequence_number */,
      packet.size() /* iso_sdu_length */,
      hci::IsoPacketStatusFlag::VALID,
      std::make_unique<bluetooth::packet::RawBuilder>(packet));
  LOG_INFO("%c%c", packet[0], packet[1]);
  iso_enqueue_buffer_->Enqueue(std::move(builder), iso_handler_);
}

void IsoManagerImpl::OnIncomingPacket() {
  std::unique_ptr<hci::IsoView> packet = hci_layer_->GetIsoQueueEnd()->TryDequeue();
  iso_data_callback.Invoke(std::move(packet));
}

}  // namespace internal
}  // namespace iso
}  // namespace bluetooth
