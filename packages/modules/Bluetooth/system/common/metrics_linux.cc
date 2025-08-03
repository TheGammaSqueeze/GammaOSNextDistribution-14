/******************************************************************************
 *
 *  Copyright 2018 Google, Inc.
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

#include <base/logging.h>

#include "leaky_bonded_queue.h"
#include "metrics.h"
#include "types/raw_address.h"

namespace bluetooth {

namespace common {

void A2dpSessionMetrics::Update(const A2dpSessionMetrics& metrics) {}

bool A2dpSessionMetrics::operator==(const A2dpSessionMetrics& rhs) const {
  LOG(INFO) << "UNIMPLEMENTED";
  return true;
}

struct BluetoothMetricsLogger::impl {
  impl(size_t max_bluetooth_session, size_t max_pair_event,
       size_t max_wake_event, size_t max_scan_event) {}
};

BluetoothMetricsLogger::BluetoothMetricsLogger()
    : pimpl_(new impl(kMaxNumBluetoothSession, kMaxNumPairEvent,
                      kMaxNumWakeEvent, kMaxNumScanEvent)) {}

void BluetoothMetricsLogger::LogPairEvent(uint32_t disconnect_reason,
                                          uint64_t timestamp_ms,
                                          uint32_t device_class,
                                          device_type_t device_type) {}

void BluetoothMetricsLogger::LogWakeEvent(wake_event_type_t type,
                                          const std::string& requestor,
                                          const std::string& name,
                                          uint64_t timestamp_ms) {}

void BluetoothMetricsLogger::LogScanEvent(bool start,
                                          const std::string& initator,
                                          scan_tech_t type, uint32_t results,
                                          uint64_t timestamp_ms) {}

void BluetoothMetricsLogger::LogBluetoothSessionStart(
    connection_tech_t connection_tech_type, uint64_t timestamp_ms) {}

void BluetoothMetricsLogger::LogBluetoothSessionEnd(
    disconnect_reason_t disconnect_reason, uint64_t timestamp_ms) {}

void BluetoothMetricsLogger::LogBluetoothSessionDeviceInfo(
    uint32_t device_class, device_type_t device_type) {}

void BluetoothMetricsLogger::LogA2dpSession(
    const A2dpSessionMetrics& a2dp_session_metrics) {}

void BluetoothMetricsLogger::LogHeadsetProfileRfcConnection(
    tBTA_SERVICE_ID service_id) {}

void BluetoothMetricsLogger::WriteString(std::string* serialized) {}

void BluetoothMetricsLogger::WriteBase64String(std::string* serialized) {}

void BluetoothMetricsLogger::WriteBase64(int fd) {}

void BluetoothMetricsLogger::CutoffSession() {}

void BluetoothMetricsLogger::Build() {}

void BluetoothMetricsLogger::ResetSession() {}

void BluetoothMetricsLogger::ResetLog() {}

void BluetoothMetricsLogger::Reset() {}

void LogClassicPairingEvent(const RawAddress& address, uint16_t handle,
                            uint32_t hci_cmd, uint16_t hci_event,
                            uint16_t cmd_status, uint16_t reason_code,
                            int64_t event_value) {}

void LogSocketConnectionState(
    const RawAddress& address, int port, int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes, int64_t rx_bytes, int uid, int server_port,
    android::bluetooth::SocketRoleEnum socket_role) {}

void LogHciTimeoutEvent(uint32_t hci_cmd) {}

void LogA2dpAudioUnderrunEvent(const RawAddress& address,
                               uint64_t encoding_interval_millis,
                               int num_missing_pcm_bytes) {}

void LogA2dpAudioOverrunEvent(const RawAddress& address,
                              uint64_t encoding_interval_millis,
                              int num_dropped_buffers,
                              int num_dropped_encoded_frames,
                              int num_dropped_encoded_bytes) {}

void LogA2dpPlaybackEvent(const RawAddress& address, int playback_state,
                          int audio_coding_mode) {}

void LogBluetoothHalCrashReason(const RawAddress& address, uint32_t error_code,
                                uint32_t vendor_error_code) {}

void LogReadRssiResult(const RawAddress& address, uint16_t handle,
                       uint32_t cmd_status, int8_t rssi) {}

void LogReadFailedContactCounterResult(const RawAddress& address,
                                       uint16_t handle, uint32_t cmd_status,
                                       int32_t failed_contact_counter) {}

void LogReadTxPowerLevelResult(const RawAddress& address, uint16_t handle,
                               uint32_t cmd_status,
                               int32_t transmit_power_level) {}

void LogRemoteVersionInfo(uint16_t handle, uint8_t status, uint8_t version,
                          uint16_t manufacturer_name, uint16_t subversion) {}

void LogLinkLayerConnectionEvent(const RawAddress* address,
                                 uint32_t connection_handle,
                                 android::bluetooth::DirectionEnum direction,
                                 uint16_t link_type, uint32_t hci_cmd,
                                 uint16_t hci_event, uint16_t hci_ble_event,
                                 uint16_t cmd_status, uint16_t reason_code) {}

void LogManufacturerInfo(const RawAddress& address,
                         android::bluetooth::DeviceInfoSrcEnum source_type,
                         const std::string& source_name,
                         const std::string& manufacturer,
                         const std::string& model,
                         const std::string& hardware_version,
                         const std::string& software_version) {}

void LogSdpAttribute(const RawAddress& address, uint16_t protocol_uuid,
                     uint16_t attribute_id, size_t attribute_size,
                     const char* attribute_value) {}

void LogSmpPairingEvent(const RawAddress& address, uint8_t smp_cmd,
                        android::bluetooth::DirectionEnum direction,
                        uint8_t smp_fail_reason) {}

void LogLeAudioConnectionSessionReported(
    int32_t group_size, int32_t group_metric_id,
    int64_t connection_duration_nanos,
    std::vector<int64_t>& device_connecting_offset_nanos,
    std::vector<int64_t>& device_connected_offset_nanos,
    std::vector<int64_t>& device_connection_duration_nanos,
    std::vector<int32_t>& device_connection_status,
    std::vector<int32_t>& device_disconnection_status,
    std::vector<RawAddress>& device_address,
    std::vector<int64_t>& streaming_offset_nanos,
    std::vector<int64_t>& streaming_duration_nanos,
    std::vector<int32_t>& streaming_context_type) {}

void LogLeAudioBroadcastSessionReported(int64_t duration_nanos) {}

}  // namespace common

}  // namespace bluetooth
