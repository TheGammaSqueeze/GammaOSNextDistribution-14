/*
 * Copyright 2023 The Android Open Source Project
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
#define LOG_TAG "BTAudioHfpAIDL"

#include "hfp_client_interface_aidl.h"

#include <map>

#include "aidl/android/hardware/bluetooth/audio/AudioConfiguration.h"
#include "aidl/transport_instance.h"
#include "bta/ag/bta_ag_int.h"
#include "btif/include/btif_hf.h"
#include "btm_api_types.h"
#include "hardware/bluetooth.h"
#include "hardware/bluetooth_headset_interface.h"
#include "provider_info.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace audio {
namespace aidl {
namespace hfp {

std::map<bt_status_t, BluetoothAudioCtrlAck> status_to_ack_map = {
    {BT_STATUS_SUCCESS, BluetoothAudioCtrlAck::SUCCESS_FINISHED},
    {BT_STATUS_DONE, BluetoothAudioCtrlAck::SUCCESS_FINISHED},
    {BT_STATUS_FAIL, BluetoothAudioCtrlAck::FAILURE},
    {BT_STATUS_NOT_READY, BluetoothAudioCtrlAck::FAILURE_BUSY},
    {BT_STATUS_BUSY, BluetoothAudioCtrlAck::FAILURE_BUSY},
    {BT_STATUS_UNSUPPORTED, BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED},
};

tBTA_AG_SCB* get_hfp_active_device_callback() {
  const RawAddress& addr = bta_ag_get_active_device();
  if (addr.IsEmpty()) {
    LOG(ERROR) << __func__ << ": No active device found";
    return nullptr;
  }
  auto idx = bta_ag_idx_by_bdaddr(&addr);
  if (idx == 0) {
    LOG(ERROR) << __func__ << ": No index found for active device";
    return nullptr;
  }
  auto cb = bta_ag_scb_by_idx(idx);
  if (cb == nullptr) {
    LOG(ERROR) << __func__ << ": No callback for the active device";
    return nullptr;
  }
  return cb;
}

std::unordered_map<int, ::hfp::sco_config> HfpTransport::GetHfpScoConfig(
    SessionType sessionType) {
  auto providerInfo =
      ::bluetooth::audio::aidl::ProviderInfo::GetProviderInfo(sessionType);
  return providerInfo->GetHfpScoConfig();
}

HfpTransport::HfpTransport() { hfp_pending_cmd_ = HFP_CTRL_CMD_NONE; }

BluetoothAudioCtrlAck HfpTransport::StartRequest() {
  if (hfp_pending_cmd_ == HFP_CTRL_CMD_START) {
    LOG(INFO) << __func__ << ": HFP_CTRL_CMD_START in progress";
    return BluetoothAudioCtrlAck::PENDING;
  } else if (hfp_pending_cmd_ != HFP_CTRL_CMD_NONE) {
    LOG(WARNING) << __func__ << ": busy in pending_cmd=" << hfp_pending_cmd_;
    return BluetoothAudioCtrlAck::FAILURE_BUSY;
  }

  auto cb = get_hfp_active_device_callback();
  if (cb == nullptr) return BluetoothAudioCtrlAck::FAILURE;

  if (bta_ag_sco_is_open(cb)) {
    // Already started, ACK back immediately.
    return BluetoothAudioCtrlAck::SUCCESS_FINISHED;
  }

  /* Post start SCO event and wait for sco to open */
  hfp_pending_cmd_ = HFP_CTRL_CMD_START;
  // as ConnectAudio only queues the command into main thread, keep PENDING
  // status
  auto status =
      bluetooth::headset::GetInterface()->ConnectAudio(&cb->peer_addr, 0);
  LOG(INFO) << __func__ << ": ConnectAudio status = " << status << " - "
            << bt_status_text(status).c_str();
  auto ctrl_ack = status_to_ack_map.find(status);
  if (ctrl_ack == status_to_ack_map.end()) {
    LOG_WARN("Unmapped status=%d", status);
    return BluetoothAudioCtrlAck::FAILURE;
  }
  if (ctrl_ack->second != BluetoothAudioCtrlAck::SUCCESS_FINISHED) {
    return ctrl_ack->second;
  }
  return BluetoothAudioCtrlAck::PENDING;
}

void HfpTransport::StopRequest() {
  LOG(INFO) << __func__ << ": handling";
  RawAddress addr = bta_ag_get_active_device();
  if (addr.IsEmpty()) {
    LOG(ERROR) << __func__ << ": No active device found";
    return;
  }
  hfp_pending_cmd_ = HFP_CTRL_CMD_STOP;
  auto status = bluetooth::headset::GetInterface()->DisconnectAudio(&addr);
  LOG(INFO) << __func__ << ": DisconnectAudio status = " << status << " - "
            << bt_status_text(status).c_str();
  hfp_pending_cmd_ = HFP_CTRL_CMD_NONE;
  return;
}

void HfpTransport::ResetPendingCmd() { hfp_pending_cmd_ = HFP_CTRL_CMD_NONE; }

uint8_t HfpTransport::GetPendingCmd() const { return hfp_pending_cmd_; }

// Unimplemented functions
void HfpTransport::LogBytesProcessed(size_t bytes_read) {}

BluetoothAudioCtrlAck HfpTransport::SuspendRequest() {
  return BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED;
}

void HfpTransport::SetLatencyMode(LatencyMode latency_mode) {}

void HfpTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {}

void HfpTransport::SinkMetadataChanged(const sink_metadata_v7_t&) {}

void HfpTransport::ResetPresentationPosition() {}

bool HfpTransport::GetPresentationPosition(uint64_t* remote_delay_report_ns,
                                           uint64_t* total_bytes_read,
                                           timespec* data_position) {
  return false;
}

// Source / sink functions
HfpDecodingTransport::HfpDecodingTransport(SessionType session_type)
    : IBluetoothSinkTransportInstance(session_type, (AudioConfiguration){}) {
  transport_ = new HfpTransport();
};

HfpDecodingTransport::~HfpDecodingTransport() { delete transport_; }

BluetoothAudioCtrlAck HfpDecodingTransport::StartRequest(bool is_low_latency) {
  return transport_->StartRequest();
}

BluetoothAudioCtrlAck HfpDecodingTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void HfpDecodingTransport::SetLatencyMode(LatencyMode latency_mode) {
  transport_->SetLatencyMode(latency_mode);
}

bool HfpDecodingTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_written,
    timespec* data_position) {
  return transport_->GetPresentationPosition(
      remote_delay_report_ns, total_bytes_written, data_position);
}

void HfpDecodingTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {
  transport_->SourceMetadataChanged(source_metadata);
}

void HfpDecodingTransport::SinkMetadataChanged(
    const sink_metadata_v7_t& sink_metadata) {
  transport_->SinkMetadataChanged(sink_metadata);
}

void HfpDecodingTransport::ResetPresentationPosition() {
  transport_->ResetPresentationPosition();
}

void HfpDecodingTransport::LogBytesRead(size_t bytes_written) {
  transport_->LogBytesProcessed(bytes_written);
}

uint8_t HfpDecodingTransport::GetPendingCmd() const {
  return transport_->GetPendingCmd();
}

void HfpDecodingTransport::ResetPendingCmd() { transport_->ResetPendingCmd(); }

void HfpDecodingTransport::StopRequest() { transport_->StopRequest(); }

HfpEncodingTransport::HfpEncodingTransport(SessionType session_type)
    : IBluetoothSourceTransportInstance(session_type, (AudioConfiguration){}) {
  transport_ = new HfpTransport();
};

HfpEncodingTransport::~HfpEncodingTransport() { delete transport_; }

BluetoothAudioCtrlAck HfpEncodingTransport::StartRequest(bool is_low_latency) {
  return transport_->StartRequest();
}

BluetoothAudioCtrlAck HfpEncodingTransport::SuspendRequest() {
  return transport_->SuspendRequest();
}

void HfpEncodingTransport::StopRequest() { transport_->StopRequest(); }

void HfpEncodingTransport::SetLatencyMode(LatencyMode latency_mode) {
  transport_->SetLatencyMode(latency_mode);
}

bool HfpEncodingTransport::GetPresentationPosition(
    uint64_t* remote_delay_report_ns, uint64_t* total_bytes_written,
    timespec* data_position) {
  return transport_->GetPresentationPosition(
      remote_delay_report_ns, total_bytes_written, data_position);
}

void HfpEncodingTransport::SourceMetadataChanged(
    const source_metadata_v7_t& source_metadata) {
  transport_->SourceMetadataChanged(source_metadata);
}

void HfpEncodingTransport::SinkMetadataChanged(
    const sink_metadata_v7_t& sink_metadata) {
  transport_->SinkMetadataChanged(sink_metadata);
}

void HfpEncodingTransport::ResetPresentationPosition() {
  transport_->ResetPresentationPosition();
}

void HfpEncodingTransport::LogBytesWritten(size_t bytes_written) {
  transport_->LogBytesProcessed(bytes_written);
}

uint8_t HfpEncodingTransport::GetPendingCmd() const {
  return transport_->GetPendingCmd();
}

void HfpEncodingTransport::ResetPendingCmd() { transport_->ResetPendingCmd(); }

}  // namespace hfp
}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
