/*
 * Copyright 2021 The Android Open Source Project
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

#include "main/shim/metrics_api.h"

#include "hci/address.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "metrics/counter_metrics.h"
#include "os/metrics.h"
#include "types/raw_address.h"

using bluetooth::hci::Address;

namespace bluetooth {
namespace shim {
void LogMetricLinkLayerConnectionEvent(
    const RawAddress* raw_address, uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction, uint16_t link_type,
    uint32_t hci_cmd, uint16_t hci_event, uint16_t hci_ble_event,
    uint16_t cmd_status, uint16_t reason_code) {
  Address address = Address::kEmpty;
  if (raw_address != nullptr) {
    address = bluetooth::ToGdAddress(*raw_address);
  }
  bluetooth::os::LogMetricLinkLayerConnectionEvent(
      raw_address == nullptr ? nullptr : &address, connection_handle, direction,
      link_type, hci_cmd, hci_event, hci_ble_event, cmd_status, reason_code);
}

void LogMetricA2dpAudioUnderrunEvent(const RawAddress& raw_address,
                                     uint64_t encoding_interval_millis,
                                     int num_missing_pcm_bytes) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricA2dpAudioUnderrunEvent(
      address, encoding_interval_millis, num_missing_pcm_bytes);
}

void LogMetricA2dpAudioOverrunEvent(const RawAddress& raw_address,
                                    uint64_t encoding_interval_millis,
                                    int num_dropped_buffers,
                                    int num_dropped_encoded_frames,
                                    int num_dropped_encoded_bytes) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricA2dpAudioOverrunEvent(
      address, encoding_interval_millis, num_dropped_buffers,
      num_dropped_encoded_frames, num_dropped_encoded_bytes);
}

void LogMetricA2dpPlaybackEvent(const RawAddress& raw_address,
                                int playback_state, int audio_coding_mode) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricA2dpPlaybackEvent(address, playback_state,
                                            audio_coding_mode);
}

void LogMetricA2dpSessionMetricsEvent(
    const RawAddress& raw_address, int64_t audio_duration_ms,
    int media_timer_min_ms, int media_timer_max_ms, int media_timer_avg_ms,
    int total_scheduling_count, int buffer_overruns_max_count,
    int buffer_overruns_total, float buffer_underruns_average,
    int buffer_underruns_count, int64_t codec_index, bool is_a2dp_offload) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricA2dpSessionMetricsEvent(
      address, audio_duration_ms, media_timer_min_ms, media_timer_max_ms,
      media_timer_avg_ms, total_scheduling_count, buffer_overruns_max_count,
      buffer_overruns_total, buffer_underruns_average, buffer_underruns_count,
      codec_index, is_a2dp_offload);
}

void LogMetricHfpPacketLossStats(const RawAddress& raw_address,
                                 int num_decoded_frames,
                                 double packet_loss_ratio,
                                 uint16_t codec_type) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricHfpPacketLossStats(address, num_decoded_frames,
                                             packet_loss_ratio, codec_type);
}

void LogMetricMmcTranscodeRttStats(int maximum_rtt, double mean_rtt,
                                   int num_requests, int codec_type) {
  bluetooth::os::LogMetricMmcTranscodeRttStats(maximum_rtt, mean_rtt,
                                               num_requests, codec_type);
}

void LogMetricReadRssiResult(const RawAddress& raw_address, uint16_t handle,
                             uint32_t cmd_status, int8_t rssi) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricReadRssiResult(address, handle, cmd_status, rssi);
}

void LogMetricReadFailedContactCounterResult(const RawAddress& raw_address,
                                             uint16_t handle,
                                             uint32_t cmd_status,
                                             int32_t failed_contact_counter) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricReadFailedContactCounterResult(
      address, handle, cmd_status, failed_contact_counter);
}

void LogMetricReadTxPowerLevelResult(const RawAddress& raw_address,
                                     uint16_t handle, uint32_t cmd_status,
                                     int32_t transmit_power_level) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricReadTxPowerLevelResult(address, handle, cmd_status,
                                                 transmit_power_level);
}

void LogMetricSmpPairingEvent(const RawAddress& raw_address, uint16_t smp_cmd,
                              android::bluetooth::DirectionEnum direction,
                              uint16_t smp_fail_reason) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricSmpPairingEvent(address, smp_cmd, direction,
                                          smp_fail_reason);
}

void LogMetricClassicPairingEvent(const RawAddress& raw_address,
                                  uint16_t handle, uint32_t hci_cmd,
                                  uint16_t hci_event, uint16_t cmd_status,
                                  uint16_t reason_code, int64_t event_value) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricClassicPairingEvent(address, handle, hci_cmd,
                                              hci_event, cmd_status,
                                              reason_code, event_value);
}

void LogMetricSdpAttribute(const RawAddress& raw_address,
                           uint16_t protocol_uuid, uint16_t attribute_id,
                           size_t attribute_size, const char* attribute_value) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricSdpAttribute(address, protocol_uuid, attribute_id,
                                       attribute_size, attribute_value);
}

void LogMetricSocketConnectionState(
    const RawAddress& raw_address, int port, int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes, int64_t rx_bytes, int uid, int server_port,
    android::bluetooth::SocketRoleEnum socket_role) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricSocketConnectionState(
      address, port, type, connection_state, tx_bytes, rx_bytes, uid,
      server_port, socket_role);
}

void LogMetricManufacturerInfo(
    const RawAddress& raw_address,
    android::bluetooth::AddressTypeEnum address_type,
    android::bluetooth::DeviceInfoSrcEnum source_type,
    const std::string& source_name, const std::string& manufacturer,
    const std::string& model, const std::string& hardware_version,
    const std::string& software_version) {
  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricManufacturerInfo(address, address_type, source_type,
                                           source_name, manufacturer, model,
                                           hardware_version, software_version);
}

bool CountCounterMetrics(int32_t key, int64_t count) {
  auto counter_metrics = GetCounterMetrics();
  if (counter_metrics == nullptr) {
    return false;
  }
  return counter_metrics->Count(key, count);
}

void LogMetricBluetoothLEConnectionMetricEvent(
    const RawAddress& raw_address,
    android::bluetooth::le::LeConnectionOriginType origin_type,
    android::bluetooth::le::LeConnectionType connection_type,
    android::bluetooth::le::LeConnectionState transaction_state,
    std::vector<std::pair<os::ArgumentType, int>> argument_list) {

  Address address = bluetooth::ToGdAddress(raw_address);
  bluetooth::os::LogMetricBluetoothLEConnectionMetricEvent(address, origin_type, connection_type, transaction_state, argument_list);
}

}  // namespace shim
}  // namespace bluetooth
