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

/*
 * Generated mock file from original source file
 *   Functions generated:12
 *
 *  mockcify.pl ver 0.2
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
// #include <frameworks/proto_logging/stats/enums/bluetooth/le/enums.pb.h>

#include "os/metrics.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace main_shim_metrics_api {

// Shared state between mocked functions and tests
// Name: LogMetricLinkLayerConnectionEvent
// Params: const RawAddress* raw_address, uint32_t connection_handle,
// android::bluetooth::DirectionEnum direction, uint16_t link_type, uint32_t
// hci_cmd, uint16_t hci_event, uint16_t hci_ble_event, uint16_t cmd_status,
// uint16_t reason_code Returns: void
struct LogMetricLinkLayerConnectionEvent {
  std::function<void(const RawAddress* raw_address, uint32_t connection_handle,
                     android::bluetooth::DirectionEnum direction,
                     uint16_t link_type, uint32_t hci_cmd, uint16_t hci_event,
                     uint16_t hci_ble_event, uint16_t cmd_status,
                     uint16_t reason_code)>
      body{[](const RawAddress* /* raw_address */,
              uint32_t /* connection_handle */,
              android::bluetooth::DirectionEnum /* direction */,
              uint16_t /* link_type */, uint32_t /* hci_cmd */,
              uint16_t /* hci_event */, uint16_t /* hci_ble_event */,
              uint16_t /* cmd_status */, uint16_t /* reason_code */) {}};
  void operator()(const RawAddress* raw_address, uint32_t connection_handle,
                  android::bluetooth::DirectionEnum direction,
                  uint16_t link_type, uint32_t hci_cmd, uint16_t hci_event,
                  uint16_t hci_ble_event, uint16_t cmd_status,
                  uint16_t reason_code) {
    body(raw_address, connection_handle, direction, link_type, hci_cmd,
         hci_event, hci_ble_event, cmd_status, reason_code);
  };
};
extern struct LogMetricLinkLayerConnectionEvent
    LogMetricLinkLayerConnectionEvent;
// Name: LogMetricA2dpAudioUnderrunEvent
// Params: const RawAddress& raw_address, uint64_t encoding_interval_millis, int
// num_missing_pcm_bytes Returns: void
struct LogMetricA2dpAudioUnderrunEvent {
  std::function<void(const RawAddress& raw_address,
                     uint64_t encoding_interval_millis,
                     int num_missing_pcm_bytes)>
      body{[](const RawAddress& /* raw_address */,
              uint64_t /* encoding_interval_millis */,
              int /* num_missing_pcm_bytes */) {}};
  void operator()(const RawAddress& raw_address,
                  uint64_t encoding_interval_millis,
                  int num_missing_pcm_bytes) {
    body(raw_address, encoding_interval_millis, num_missing_pcm_bytes);
  };
};
extern struct LogMetricA2dpAudioUnderrunEvent LogMetricA2dpAudioUnderrunEvent;
// Name: LogMetricA2dpAudioOverrunEvent
// Params: const RawAddress& raw_address, uint64_t encoding_interval_millis, int
// num_dropped_buffers, int num_dropped_encoded_frames, int
// num_dropped_encoded_bytes Returns: void
struct LogMetricA2dpAudioOverrunEvent {
  std::function<void(const RawAddress& raw_address,
                     uint64_t encoding_interval_millis, int num_dropped_buffers,
                     int num_dropped_encoded_frames,
                     int num_dropped_encoded_bytes)>
      body{[](const RawAddress& /* raw_address */,
              uint64_t /* encoding_interval_millis */,
              int /* num_dropped_buffers */,
              int /* num_dropped_encoded_frames */,
              int /* num_dropped_encoded_bytes */) {}};
  void operator()(const RawAddress& raw_address,
                  uint64_t encoding_interval_millis, int num_dropped_buffers,
                  int num_dropped_encoded_frames,
                  int num_dropped_encoded_bytes) {
    body(raw_address, encoding_interval_millis, num_dropped_buffers,
         num_dropped_encoded_frames, num_dropped_encoded_bytes);
  };
};
extern struct LogMetricA2dpAudioOverrunEvent LogMetricA2dpAudioOverrunEvent;
// Name: LogMetricA2dpPlaybackEvent
// Params: const RawAddress& raw_address, int playback_state, int
// audio_coding_mode Returns: void
struct LogMetricA2dpPlaybackEvent {
  std::function<void(const RawAddress& raw_address, int playback_state,
                     int audio_coding_mode)>
      body{[](const RawAddress& /* raw_address */, int /* playback_state */,
              int /* audio_coding_mode */) {}};
  void operator()(const RawAddress& raw_address, int playback_state,
                  int audio_coding_mode) {
    body(raw_address, playback_state, audio_coding_mode);
  };
};
extern struct LogMetricA2dpSessionMetricsEvent LogMetricA2dpSessionMetricsEvent;
// Name: LogMetricA2dpSessionMetricsEvent
// Params: const RawAddress& raw_address, int playback_state, int
// audio_coding_mode Returns: void
struct LogMetricA2dpSessionMetricsEvent {
  std::function<void(const RawAddress& raw_address, int64_t audio_duration_ms,
                     int media_timer_min_ms, int media_timer_max_ms,
                     int media_timer_avg_ms, int total_scheduling_count,
                     int buffer_overruns_max_count, int buffer_overruns_total,
                     float buffer_underruns_average, int buffer_underruns_count,
                     int64_t codec_index, bool is_a2dp_offload)>
      body{[](const RawAddress& /* raw_address */,
              int64_t /* audio_duration_ms */, int /* media_timer_min_ms */,
              int /* media_timer_max_ms */, int /* media_timer_avg_ms */,
              int /* total_scheduling_count */,
              int /* buffer_overruns_max_count */,
              int /* buffer_overruns_total */,
              float /* buffer_underruns_average */,
              int /* buffer_underruns_count */, int64_t /* codec_index */,
              bool /* is_a2dp_offload */) {}};
  void operator()(const RawAddress& raw_address, int64_t audio_duration_ms,
                  int media_timer_min_ms, int media_timer_max_ms,
                  int /* media_timer_avg_ms */, int total_scheduling_count,
                  int buffer_overruns_max_count, int buffer_overruns_total,
                  float buffer_underruns_average, int buffer_underruns_count,
                  int64_t codec_index, bool is_a2dp_offload) {
    body(raw_address, audio_duration_ms, media_timer_min_ms, media_timer_max_ms,
         audio_duration_ms, total_scheduling_count, buffer_overruns_max_count,
         buffer_overruns_total, buffer_underruns_average,
         buffer_underruns_count, codec_index, is_a2dp_offload);
  };
};
extern struct LogMetricA2dpPlaybackEvent LogMetricA2dpPlaybackEvent;
// Name: LogMetricHfpPacketLossStats
// Params: const RawAddress& raw_address, int num_decoded_frames, double
// packet_loss_ratio, uint16_t codec_type Returns: void
struct LogMetricHfpPacketLossStats {
  std::function<void(const RawAddress& raw_address, int num_decoded_frames,
                     double packet_loss_ratio, uint16_t codec_type)>
      body{[](const RawAddress& /* raw_address */, int /* num_decoded_frames */,
              double /* packet_loss_ratio */, uint16_t /* codec_type */) {}};
  void operator()(const RawAddress& raw_address, int num_decoded_frames,
                  double packet_loss_ratio, uint16_t codec_type) {
    body(raw_address, num_decoded_frames, packet_loss_ratio, codec_type);
  };
};
extern struct LogMetricHfpPacketLossStats LogMetricHfpPacketLossStats;
// Name: LogMetricMmcTranscodeRttStats
// Params: int maximum_rtt, double mean_rtt, int num_requests, int codec_type
// Return: void
struct LogMetricMmcTranscodeRttStats {
  std::function<void(int maximum_rtt, double mean_rtt, int num_requests,
                     int codec_type)>
      body{[](int /* maximum_rtt */, double /* mean_rtt */,
              int /* num_requests */, int /* codec_type */) {}};
  void operator()(int maximum_rtt, double mean_rtt, int num_requests,
                  int codec_type) {
    body(maximum_rtt, mean_rtt, num_requests, codec_type);
  };
};
extern struct LogMetricMmcTranscodeRttStats LogMetricMmcTranscodeRttStats;
// Name: LogMetricReadRssiResult
// Params: const RawAddress& raw_address, uint16_t handle, uint32_t cmd_status,
// int8_t rssi Returns: void
struct LogMetricReadRssiResult {
  std::function<void(const RawAddress& raw_address, uint16_t handle,
                     uint32_t cmd_status, int8_t rssi)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* handle */,
              uint32_t /* cmd_status */, int8_t /* rssi */) {}};
  void operator()(const RawAddress& raw_address, uint16_t handle,
                  uint32_t cmd_status, int8_t rssi) {
    body(raw_address, handle, cmd_status, rssi);
  };
};
extern struct LogMetricReadRssiResult LogMetricReadRssiResult;
// Name: LogMetricReadFailedContactCounterResult
// Params: const RawAddress& raw_address, uint16_t handle, uint32_t cmd_status,
// int32_t failed_contact_counter Returns: void
struct LogMetricReadFailedContactCounterResult {
  std::function<void(const RawAddress& raw_address, uint16_t handle,
                     uint32_t cmd_status, int32_t failed_contact_counter)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* handle */,
              uint32_t /* cmd_status */,
              int32_t /* failed_contact_counter */) {}};
  void operator()(const RawAddress& raw_address, uint16_t handle,
                  uint32_t cmd_status, int32_t failed_contact_counter) {
    body(raw_address, handle, cmd_status, failed_contact_counter);
  };
};
extern struct LogMetricReadFailedContactCounterResult
    LogMetricReadFailedContactCounterResult;
// Name: LogMetricReadTxPowerLevelResult
// Params: const RawAddress& raw_address, uint16_t handle, uint32_t cmd_status,
// int32_t transmit_power_level Returns: void
struct LogMetricReadTxPowerLevelResult {
  std::function<void(const RawAddress& raw_address, uint16_t handle,
                     uint32_t cmd_status, int32_t transmit_power_level)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* handle */,
              uint32_t /* cmd_status */,
              int32_t /* transmit_power_level */) {}};
  void operator()(const RawAddress& raw_address, uint16_t handle,
                  uint32_t cmd_status, int32_t transmit_power_level) {
    body(raw_address, handle, cmd_status, transmit_power_level);
  };
};
extern struct LogMetricReadTxPowerLevelResult LogMetricReadTxPowerLevelResult;
// Name: LogMetricSmpPairingEvent
// Params: const RawAddress& raw_address, uint16_t smp_cmd,
// android::bluetooth::DirectionEnum direction, uint8_t smp_fail_reason Returns:
// void
struct LogMetricSmpPairingEvent {
  std::function<void(const RawAddress& raw_address, uint16_t smp_cmd,
                     android::bluetooth::DirectionEnum direction,
                     uint16_t smp_fail_reason)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* smp_cmd */,
              android::bluetooth::DirectionEnum /* direction */,
              uint16_t /* smp_fail_reason */) {}};
  void operator()(const RawAddress& raw_address, uint16_t smp_cmd,
                  android::bluetooth::DirectionEnum direction,
                  uint16_t smp_fail_reason) {
    body(raw_address, smp_cmd, direction, smp_fail_reason);
  };
};
extern struct LogMetricSmpPairingEvent LogMetricSmpPairingEvent;
// Name: LogMetricClassicPairingEvent
// Params: const RawAddress& raw_address, uint16_t handle, uint32_t hci_cmd,
// uint16_t hci_event, uint16_t cmd_status, uint16_t reason_code, int64_t
// event_value Returns: void
struct LogMetricClassicPairingEvent {
  std::function<void(const RawAddress& raw_address, uint16_t handle,
                     uint32_t hci_cmd, uint16_t hci_event, uint16_t cmd_status,
                     uint16_t reason_code, int64_t event_value)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* handle */,
              uint32_t /* hci_cmd */, uint16_t /* hci_event */,
              uint16_t /* cmd_status */, uint16_t /* reason_code */,
              int64_t /* event_value */) {}};
  void operator()(const RawAddress& raw_address, uint16_t handle,
                  uint32_t hci_cmd, uint16_t hci_event, uint16_t cmd_status,
                  uint16_t reason_code, int64_t event_value) {
    body(raw_address, handle, hci_cmd, hci_event, cmd_status, reason_code,
         event_value);
  };
};
extern struct LogMetricClassicPairingEvent LogMetricClassicPairingEvent;
// Name: LogMetricSdpAttribute
// Params: const RawAddress& raw_address, uint16_t protocol_uuid, uint16_t
// attribute_id, size_t attribute_size, const char* attribute_value Returns:
// void
struct LogMetricSdpAttribute {
  std::function<void(const RawAddress& raw_address, uint16_t protocol_uuid,
                     uint16_t attribute_id, size_t attribute_size,
                     const char* attribute_value)>
      body{[](const RawAddress& /* raw_address */, uint16_t /* protocol_uuid */,
              uint16_t /* attribute_id */, size_t /* attribute_size */,
              const char* /* attribute_value */) {}};
  void operator()(const RawAddress& raw_address, uint16_t protocol_uuid,
                  uint16_t attribute_id, size_t attribute_size,
                  const char* attribute_value) {
    body(raw_address, protocol_uuid, attribute_id, attribute_size,
         attribute_value);
  };
};
extern struct LogMetricSdpAttribute LogMetricSdpAttribute;
// Name: LogMetricSocketConnectionState
// Params: const RawAddress& raw_address, int port, int type,
// android::bluetooth::SocketConnectionstateEnum connection_state, int64_t
// tx_bytes, int64_t rx_bytes, int uid, int server_port,
// android::bluetooth::SocketRoleEnum socket_role Returns: void
struct LogMetricSocketConnectionState {
  std::function<void(
      const RawAddress& raw_address, int port, int type,
      android::bluetooth::SocketConnectionstateEnum connection_state,
      int64_t tx_bytes, int64_t rx_bytes, int uid, int server_port,
      android::bluetooth::SocketRoleEnum socket_role)>
      body{[](const RawAddress& /* raw_address */, int /* port */,
              int /* type */,
              android::bluetooth::
                  SocketConnectionstateEnum /* connection_state */,
              int64_t /* tx_bytes */, int64_t /* rx_bytes */, int /* uid */,
              int /* server_port */,
              android::bluetooth::SocketRoleEnum /* socket_role */) {}};
  void operator()(
      const RawAddress& raw_address, int port, int type,
      android::bluetooth::SocketConnectionstateEnum connection_state,
      int64_t tx_bytes, int64_t rx_bytes, int uid, int server_port,
      android::bluetooth::SocketRoleEnum socket_role) {
    body(raw_address, port, type, connection_state, tx_bytes, rx_bytes, uid,
         server_port, socket_role);
  };
};
extern struct LogMetricSocketConnectionState LogMetricSocketConnectionState;
// Name: LogMetricManufacturerInfo
// Params: const RawAddress& raw_address, android::bluetooth::DeviceInfoSrcEnum
// source_type, const std::string& source_name, const std::string& manufacturer,
// const std::string& model, const std::string& hardware_version, const
// std::string& software_version Returns: void
struct LogMetricManufacturerInfo {
  std::function<void(const RawAddress& raw_address,
                     android::bluetooth::AddressTypeEnum address_type,
                     android::bluetooth::DeviceInfoSrcEnum source_type,
                     const std::string& source_name,
                     const std::string& manufacturer, const std::string& model,
                     const std::string& hardware_version,
                     const std::string& software_version)>
      body{[](const RawAddress& /* raw_address */,
              android::bluetooth::AddressTypeEnum /* address_type */,
              android::bluetooth::DeviceInfoSrcEnum /* source_type */,
              const std::string& /* source_name */,
              const std::string& /* manufacturer */,
              const std::string& /* model */,
              const std::string& /* hardware_version */,
              const std::string& /* software_version */) {}};
  void operator()(const RawAddress& raw_address,
                  android::bluetooth::AddressTypeEnum address_type,
                  android::bluetooth::DeviceInfoSrcEnum source_type,
                  const std::string& source_name,
                  const std::string& manufacturer, const std::string& model,
                  const std::string& hardware_version,
                  const std::string& software_version) {
    body(raw_address, address_type, source_type, source_name, manufacturer,
         model, hardware_version, software_version);
  };
};
extern struct LogMetricManufacturerInfo LogMetricManufacturerInfo;

// Name: LogMetricBluetoothLEConnectionMetricEvent
// Params:     const RawAddress& raw_address,
//    android::bluetooth::le::LeConnectionOriginType origin_type,
//    android::bluetooth::le::LeConnectionType connection_type,
//    android::bluetooth::le::LeConnectionState transaction_state,
//    std::vector<std::pair<bluetooth::metrics::ArgumentType, int>>
//    argument_list
struct LogMetricBluetoothLEConnectionMetricEvent {
  std::function<void(
      const RawAddress& raw_address,
      android::bluetooth::le::LeConnectionOriginType origin_type,
      android::bluetooth::le::LeConnectionType connection_type,
      android::bluetooth::le::LeConnectionState transaction_state,
      std::vector<std::pair<bluetooth::os::ArgumentType, int>> argument_list)>
      body{[](const RawAddress& /* raw_address */,
              android::bluetooth::le::LeConnectionOriginType /* origin_type */,
              android::bluetooth::le::LeConnectionType /* connection_type */,
              android::bluetooth::le::LeConnectionState
              /* transaction_state */,
              std::vector<std::pair<bluetooth::os::ArgumentType, int>>
              /* argument_list */) {}};
  void operator()(
      const RawAddress& raw_address,
      android::bluetooth::le::LeConnectionOriginType origin_type,
      android::bluetooth::le::LeConnectionType connection_type,
      android::bluetooth::le::LeConnectionState transaction_state,
      std::vector<std::pair<bluetooth::os::ArgumentType, int>>
          argument_list) {
    body(raw_address, origin_type, connection_type, transaction_state,
         argument_list);
  };
};

}  // namespace main_shim_metrics_api
}  // namespace mock
}  // namespace test

// END mockcify generation
