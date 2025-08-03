/******************************************************************************
 *
 *  Copyright 2021 Google, Inc.
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
 ******************************************************************************/

#include "os/metrics.h"

#include <metrics/structured_events.h>

#include "metrics/utils.h"
#include "os/log.h"

namespace bluetooth {
namespace os {

using bluetooth::hci::Address;

void LogMetricClassicPairingEvent(
    const Address& address,
    uint16_t handle,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t cmd_status,
    uint16_t reason_code,
    int64_t event_value) {}

void LogMetricSocketConnectionState(
    const Address& address,
    int port,
    int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes,
    int64_t rx_bytes,
    int uid,
    int server_port,
    android::bluetooth::SocketRoleEnum socket_role) {}

void LogMetricHciTimeoutEvent(uint32_t hci_cmd) {}

void LogMetricA2dpAudioUnderrunEvent(
    const Address& address, uint64_t encoding_interval_millis, int num_missing_pcm_bytes) {}

void LogMetricA2dpAudioOverrunEvent(
    const Address& address,
    uint64_t encoding_interval_millis,
    int num_dropped_buffers,
    int num_dropped_encoded_frames,
    int num_dropped_encoded_bytes) {
  std::string boot_id;
  std::string addr_string;

  if (!metrics::GetBootId(&boot_id)) return;

  addr_string = address.ToString();

  LOG_DEBUG(
      "A2dpAudioOverrun: %s, %s, %llu, %d, %d, %d",
      boot_id.c_str(),
      addr_string.c_str(),
      (long long unsigned)encoding_interval_millis,
      num_dropped_buffers,
      num_dropped_encoded_bytes,
      num_dropped_encoded_bytes);

  ::metrics::structured::events::bluetooth::BluetoothA2dpAudioOverrun()
      .SetBootId(boot_id)
      .SetDeviceId(addr_string)
      .SetEncodingInterval(encoding_interval_millis)
      .SetDroppedBuffers(num_dropped_buffers)
      .SetDroppedFrames(num_dropped_encoded_frames)
      .SetDroppedBytes(num_dropped_encoded_bytes)
      .Record();
}

void LogMetricHfpPacketLossStats(
    const Address& address, int num_decoded_frames, double packet_loss_ratio, uint16_t codec_type) {
  std::string boot_id;
  std::string addr_string;

  if (!metrics::GetBootId(&boot_id)) return;

  addr_string = address.ToString();

  LOG_DEBUG(
      "HfpPacketLoss: %s, %s, %d, %f, %u",
      boot_id.c_str(),
      addr_string.c_str(),
      num_decoded_frames,
      packet_loss_ratio,
      codec_type);

  ::metrics::structured::events::bluetooth::BluetoothHfpPacketLoss()
      .SetBootId(boot_id)
      .SetDeviceId(addr_string)
      .SetDecodedFrames(num_decoded_frames)
      .SetPacketLossRatio(packet_loss_ratio)
      .SetCodecType(codec_type)
      .Record();
}

void LogMetricMmcTranscodeRttStats(
    int maximum_rtt, double mean_rtt, int num_requests, int codec_type) {
  std::string boot_id;

  if (!metrics::GetBootId(&boot_id)) return;

  LOG_DEBUG(
      "MmcTranscodeRttStats: %s, %d, %f, %d, %d",
      boot_id.c_str(),
      maximum_rtt,
      mean_rtt,
      num_requests,
      codec_type);

  ::metrics::structured::events::bluetooth::BluetoothMmcTranscodeRtt()
      .SetBootId(boot_id)
      .SetMaximumRtt(maximum_rtt)
      .SetMeanRtt(mean_rtt)
      .SetNumRequests(num_requests)
      .SetCodecType(codec_type)
      .Record();
}

void LogMetricReadRssiResult(
    const Address& address, uint16_t handle, uint32_t cmd_status, int8_t rssi) {}

void LogMetricReadFailedContactCounterResult(
    const Address& address, uint16_t handle, uint32_t cmd_status, int32_t failed_contact_counter) {}

void LogMetricReadTxPowerLevelResult(
    const Address& address, uint16_t handle, uint32_t cmd_status, int32_t transmit_power_level) {}

void LogMetricRemoteVersionInfo(
    uint16_t handle,
    uint8_t status,
    uint8_t version,
    uint16_t manufacturer_name,
    uint16_t subversion) {}

void LogMetricLinkLayerConnectionEvent(
    const Address* address,
    uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction,
    uint16_t link_type,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t hci_ble_event,
    uint16_t cmd_status,
    uint16_t reason_code) {}

void LogMetricManufacturerInfo(
    const Address& address,
    android::bluetooth::AddressTypeEnum address_type,
    android::bluetooth::DeviceInfoSrcEnum source_type,
    const std::string& source_name,
    const std::string& manufacturer,
    const std::string& model,
    const std::string& hardware_version,
    const std::string& software_version) {}

void LogMetricSdpAttribute(
    const Address& address,
    uint16_t protocol_uuid,
    uint16_t attribute_id,
    size_t attribute_size,
    const char* attribute_value) {}

void LogMetricSmpPairingEvent(
    const Address& address,
    uint16_t smp_cmd,
    android::bluetooth::DirectionEnum direction,
    uint16_t smp_fail_reason) {}

void LogMetricA2dpPlaybackEvent(const Address& address, int playback_state, int audio_coding_mode) {
}

void LogMetricA2dpSessionMetricsEvent(
    const hci::Address& address,
    int64_t audio_duration_ms,
    int media_timer_min_ms,
    int media_timer_max_ms,
    int media_timer_avg_ms,
    int total_scheduling_count,
    int buffer_overruns_max_count,
    int buffer_overruns_total,
    float buffer_underruns_average,
    int buffer_underruns_count,
    int64_t codec_index,
    bool is_a2dp_offload) {
  std::string boot_id;
  std::string addr_string;

  if (!metrics::GetBootId(&boot_id)) return;

  addr_string = address.ToString();

  LOG_DEBUG(
      "A2dpSessionMetrics: %s, %s, %lld, %d, %d, %d, %d, %d, %d, %f, %d, %lld, %d",
      boot_id.c_str(),
      addr_string.c_str(),
      (long long int)audio_duration_ms,
      media_timer_min_ms,
      media_timer_max_ms,
      media_timer_avg_ms,
      total_scheduling_count,
      buffer_overruns_max_count,
      buffer_overruns_total,
      buffer_underruns_average,
      buffer_underruns_count,
      codec_index,
      is_a2dp_offload);

  ::metrics::structured::events::bluetooth::BluetoothA2dpSession()
      .SetBootId(boot_id)
      .SetDeviceId(addr_string)
      .SetAudioDuration(audio_duration_ms)
      .SetMediaTimerMin(media_timer_min_ms)
      .SetMediaTimerMax(media_timer_max_ms)
      .SetMediaTimerAvg(media_timer_avg_ms)
      .SetTotalSchedulingCount(total_scheduling_count)
      .SetBufferOverrunsMaxCount(buffer_overruns_max_count)
      .SetBufferOverrunsTotal(buffer_overruns_total)
      .SetBufferUnderrunsAvg(buffer_underruns_average)
      .SetBufferUnderrunsCount(buffer_underruns_count)
      .SetCodecIndex(codec_index)
      .SetIsA2dpOffload(is_a2dp_offload)
      .Record();
}

void LogMetricBluetoothHalCrashReason(
    const Address& address, uint32_t error_code, uint32_t vendor_error_code) {}

void LogMetricBluetoothLocalSupportedFeatures(uint32_t page_num, uint64_t features) {}

void LogMetricBluetoothLocalVersions(
    uint32_t lmp_manufacturer_name,
    uint8_t lmp_version,
    uint32_t lmp_subversion,
    uint8_t hci_version,
    uint32_t hci_revision) {}

void LogMetricBluetoothDisconnectionReasonReported(
    uint32_t reason, const Address& address, uint32_t connection_handle) {}

void LogMetricBluetoothRemoteSupportedFeatures(
    const Address& address, uint32_t page, uint64_t features, uint32_t connection_handle) {}

void LogMetricBluetoothCodePathCounterMetrics(int32_t key, int64_t count) {}

void LogMetricBluetoothLEConnectionMetricEvent(
    const Address& raw_address,
    android::bluetooth::le::LeConnectionOriginType origin_type,
    android::bluetooth::le::LeConnectionType connection_type,
    android::bluetooth::le::LeConnectionState transaction_state,
    std::vector<std::pair<os::ArgumentType, int>>& argument_list) {}

void LogMetricBluetoothLEConnection(os::LEConnectionSessionOptions /* session_options */) {}
}  // namespace os
}  // namespace bluetooth
