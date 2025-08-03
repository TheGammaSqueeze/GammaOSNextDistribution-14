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

#define LOG_TAG "BluetoothMetrics"

#include "os/metrics.h"

#include <statslog_bt.h>

#include "common/audit_log.h"
#include "metrics/metrics_state.h"
#include "common/metric_id_manager.h"
#include "common/strings.h"
#include "hci/hci_packets.h"
#include "os/log.h"

namespace bluetooth {

namespace os {

using bluetooth::common::MetricIdManager;
using bluetooth::hci::Address;
using bluetooth::hci::ErrorCode;
using bluetooth::hci::EventCode;

/**
 * nullptr and size 0 represent missing value for obfuscated_id
 */
static const BytesField byteField(nullptr, 0);

void LogMetricLinkLayerConnectionEvent(
    const Address* address,
    uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction,
    uint16_t link_type,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t hci_ble_event,
    uint16_t cmd_status,
    uint16_t reason_code) {
  int metric_id = 0;
  if (address != nullptr) {
    metric_id = MetricIdManager::GetInstance().AllocateId(*address);
  }
  int ret = stats_write(
      BLUETOOTH_LINK_LAYER_CONNECTION_EVENT,
      byteField,
      connection_handle,
      direction,
      link_type,
      hci_cmd,
      hci_event,
      hci_ble_event,
      cmd_status,
      reason_code,
      metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed to log status %s , reason %s, from cmd %s, event %s,  ble_event %s, for %s, handle %d, type %s, "
        "error %d",
        common::ToHexString(cmd_status).c_str(),
        common::ToHexString(reason_code).c_str(),
        common::ToHexString(hci_cmd).c_str(),
        common::ToHexString(hci_event).c_str(),
        common::ToHexString(hci_ble_event).c_str(),
        address ? ADDRESS_TO_LOGGABLE_CSTR(*address) : "(NULL)",
        connection_handle,
        common::ToHexString(link_type).c_str(),
        ret);
  }
}

void LogMetricHciTimeoutEvent(uint32_t hci_cmd) {
  int ret = stats_write(BLUETOOTH_HCI_TIMEOUT_REPORTED, static_cast<int64_t>(hci_cmd));
  if (ret < 0) {
    LOG_WARN("Failed for opcode %s, error %d", common::ToHexString(hci_cmd).c_str(), ret);
  }
}

void LogMetricRemoteVersionInfo(
    uint16_t handle, uint8_t status, uint8_t version, uint16_t manufacturer_name, uint16_t subversion) {
  int ret = stats_write(BLUETOOTH_REMOTE_VERSION_INFO_REPORTED, handle, status, version, manufacturer_name, subversion);
  if (ret < 0) {
    LOG_WARN(
        "Failed for handle %d, status %s, version %s, manufacturer_name %s, subversion %s, error %d",
        handle,
        common::ToHexString(status).c_str(),
        common::ToHexString(version).c_str(),
        common::ToHexString(manufacturer_name).c_str(),
        common::ToHexString(subversion).c_str(),
        ret);
  }
}

void LogMetricA2dpAudioUnderrunEvent(
    const Address& address, uint64_t encoding_interval_millis, int num_missing_pcm_bytes) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int64_t encoding_interval_nanos = encoding_interval_millis * 1000000;
  int ret = stats_write(
      BLUETOOTH_A2DP_AUDIO_UNDERRUN_REPORTED, byteField, encoding_interval_nanos, num_missing_pcm_bytes, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, encoding_interval_nanos %s, num_missing_pcm_bytes %d, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        std::to_string(encoding_interval_nanos).c_str(),
        num_missing_pcm_bytes,
        ret);
  }
}

void LogMetricA2dpAudioOverrunEvent(
    const Address& address,
    uint64_t encoding_interval_millis,
    int num_dropped_buffers,
    int num_dropped_encoded_frames,
    int num_dropped_encoded_bytes) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }

  int64_t encoding_interval_nanos = encoding_interval_millis * 1000000;
  int ret = stats_write(
      BLUETOOTH_A2DP_AUDIO_OVERRUN_REPORTED,
      byteField,
      encoding_interval_nanos,
      num_dropped_buffers,
      num_dropped_encoded_frames,
      num_dropped_encoded_bytes,
      metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed to log for %s, encoding_interval_nanos %s, num_dropped_buffers %d, "
        "num_dropped_encoded_frames %d, num_dropped_encoded_bytes %d, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        std::to_string(encoding_interval_nanos).c_str(),
        num_dropped_buffers,
        num_dropped_encoded_frames,
        num_dropped_encoded_bytes,
        ret);
  }
}

void LogMetricA2dpPlaybackEvent(const Address& address, int playback_state, int audio_coding_mode) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }

  int ret = stats_write(BLUETOOTH_A2DP_PLAYBACK_STATE_CHANGED, byteField, playback_state, audio_coding_mode, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed to log for %s, playback_state %d, audio_coding_mode %d,error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        playback_state,
        audio_coding_mode,
        ret);
  }
}

void LogMetricA2dpSessionMetricsEvent(
    const hci::Address& /* address */,
    int64_t /* audio_duration_ms */,
    int /* media_timer_min_ms */,
    int /* media_timer_max_ms */,
    int /* media_timer_avg_ms */,
    int /* total_scheduling_count */,
    int /* buffer_overruns_max_count */,
    int /* buffer_overruns_total */,
    float /* buffer_underruns_average */,
    int /* buffer_underruns_count */,
    int64_t /* codec_index */,
    bool /* is_a2dp_offload */) {}

void LogMetricHfpPacketLossStats(
    const Address& /* address */,
    int /* num_decoded_frames */,
    double /* packet_loss_ratio */,
    uint16_t /* codec_type */) {}

void LogMetricReadRssiResult(const Address& address, uint16_t handle, uint32_t cmd_status, int8_t rssi) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(BLUETOOTH_DEVICE_RSSI_REPORTED, byteField, handle, cmd_status, rssi, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, handle %d, status %s, rssi %d dBm, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        handle,
        common::ToHexString(cmd_status).c_str(),
        rssi,
        ret);
  }
}

void LogMetricReadFailedContactCounterResult(
    const Address& address, uint16_t handle, uint32_t cmd_status, int32_t failed_contact_counter) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_DEVICE_FAILED_CONTACT_COUNTER_REPORTED,
      byteField,
      handle,
      cmd_status,
      failed_contact_counter,
      metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, handle %d, status %s, failed_contact_counter %d packets, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        handle,
        common::ToHexString(cmd_status).c_str(),
        failed_contact_counter,
        ret);
  }
}

void LogMetricReadTxPowerLevelResult(
    const Address& address, uint16_t handle, uint32_t cmd_status, int32_t transmit_power_level) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_DEVICE_TX_POWER_LEVEL_REPORTED, byteField, handle, cmd_status, transmit_power_level, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, handle %d, status %s, transmit_power_level %d packets, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        handle,
        common::ToHexString(cmd_status).c_str(),
        transmit_power_level,
        ret);
  }
}

void LogMetricSmpPairingEvent(
    const Address& address, uint16_t smp_cmd, android::bluetooth::DirectionEnum direction, uint16_t smp_fail_reason) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret =
      stats_write(BLUETOOTH_SMP_PAIRING_EVENT_REPORTED, byteField, smp_cmd, direction, smp_fail_reason, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, smp_cmd %s, direction %d, smp_fail_reason %s, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        common::ToHexString(smp_cmd).c_str(),
        direction,
        common::ToHexString(smp_fail_reason).c_str(),
        ret);
  }
}

void LogMetricClassicPairingEvent(
    const Address& address,
    uint16_t handle,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t cmd_status,
    uint16_t reason_code,
    int64_t event_value) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_CLASSIC_PAIRING_EVENT_REPORTED,
      byteField,
      handle,
      hci_cmd,
      hci_event,
      cmd_status,
      reason_code,
      event_value,
      metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, handle %d, hci_cmd %s, hci_event %s, cmd_status %s, "
        "reason %s, event_value %s, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        handle,
        common::ToHexString(hci_cmd).c_str(),
        common::ToHexString(hci_event).c_str(),
        common::ToHexString(cmd_status).c_str(),
        common::ToHexString(reason_code).c_str(),
        std::to_string(event_value).c_str(),
        ret);
  }

  if (static_cast<EventCode>(hci_event) == EventCode::SIMPLE_PAIRING_COMPLETE) {
    common::LogConnectionAdminAuditEvent("Pairing", address, static_cast<ErrorCode>(cmd_status));
  }
}

void LogMetricSdpAttribute(
    const Address& address,
    uint16_t protocol_uuid,
    uint16_t attribute_id,
    size_t attribute_size,
    const char* attribute_value) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  BytesField attribute_field(attribute_value, attribute_size);
  int ret =
      stats_write(BLUETOOTH_SDP_ATTRIBUTE_REPORTED, byteField, protocol_uuid, attribute_id, attribute_field, metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, protocol_uuid %s, attribute_id %s, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        common::ToHexString(protocol_uuid).c_str(),
        common::ToHexString(attribute_id).c_str(),
        ret);
  }
}

void LogMetricSocketConnectionState(
    const Address& address,
    int port,
    int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes,
    int64_t rx_bytes,
    int uid,
    int server_port,
    android::bluetooth::SocketRoleEnum socket_role) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_SOCKET_CONNECTION_STATE_CHANGED,
      byteField,
      port,
      type,
      connection_state,
      tx_bytes,
      rx_bytes,
      uid,
      server_port,
      socket_role,
      metric_id);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, port %d, type %d, state %d, tx_bytes %s, rx_bytes %s, uid %d, server_port %d, "
        "socket_role %d, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        port,
        type,
        connection_state,
        std::to_string(tx_bytes).c_str(),
        std::to_string(rx_bytes).c_str(),
        uid,
        server_port,
        socket_role,
        ret);
  }
}

void LogMetricManufacturerInfo(
    const Address& address,
    android::bluetooth::AddressTypeEnum address_type,
    android::bluetooth::DeviceInfoSrcEnum source_type,
    const std::string& source_name,
    const std::string& manufacturer,
    const std::string& model,
    const std::string& hardware_version,
    const std::string& software_version) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_DEVICE_INFO_REPORTED,
      byteField,
      source_type,
      source_name.c_str(),
      manufacturer.c_str(),
      model.c_str(),
      hardware_version.c_str(),
      software_version.c_str(),
      metric_id,
      address_type,
      address.address[5],
      address.address[4],
      address.address[3]);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, source_type %d, source_name %s, manufacturer %s, model %s, "
        "hardware_version %s, "
        "software_version %s, MAC address type %d MAC address prefix %d %d %d, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        source_type,
        source_name.c_str(),
        manufacturer.c_str(),
        model.c_str(),
        hardware_version.c_str(),
        software_version.c_str(),
        address_type,
        address.address[5],
        address.address[4],
        address.address[3],
        ret);
  }
}

void LogMetricBluetoothHalCrashReason(
    const Address& address,
    uint32_t error_code,
    uint32_t vendor_error_code) {
  int ret =
      stats_write(BLUETOOTH_HAL_CRASH_REASON_REPORTED, 0 /* metric_id */, byteField, error_code, vendor_error_code);
  if (ret < 0) {
    LOG_WARN(
        "Failed for %s, error_code %s, vendor_error_code %s, error %d",
        ADDRESS_TO_LOGGABLE_CSTR(address),
        common::ToHexString(error_code).c_str(),
        common::ToHexString(vendor_error_code).c_str(),
        ret);
  }
}

void LogMetricBluetoothLocalSupportedFeatures(uint32_t page_num, uint64_t features) {
  int ret = stats_write(
      BLUETOOTH_LOCAL_SUPPORTED_FEATURES_REPORTED, page_num, static_cast<int64_t>(features));
  if (ret < 0) {
    LOG_WARN(
        "Failed for LogMetricBluetoothLocalSupportedFeatures, "
        "page_num %d, features %s, error %d",
        page_num,
        std::to_string(features).c_str(),
        ret);
  }
}

void LogMetricBluetoothLocalVersions(
    uint32_t lmp_manufacturer_name,
    uint8_t lmp_version,
    uint32_t lmp_subversion,
    uint8_t hci_version,
    uint32_t hci_revision) {
  int ret = stats_write(
      BLUETOOTH_LOCAL_VERSIONS_REPORTED,
      static_cast<int32_t>(lmp_manufacturer_name),
      static_cast<int32_t>(lmp_version),
      static_cast<int32_t>(lmp_subversion),
      static_cast<int32_t>(hci_version),
      static_cast<int32_t>(hci_revision));
  if (ret < 0) {
    LOG_WARN(
        "Failed for LogMetricBluetoothLocalVersions, "
        "lmp_manufacturer_name %d, lmp_version %hhu, lmp_subversion %d, hci_version %hhu, hci_revision %d, error %d",
        lmp_manufacturer_name,
        lmp_version,
        lmp_subversion,
        hci_version,
        hci_revision,
        ret);
  }
}

void LogMetricBluetoothDisconnectionReasonReported(
    uint32_t reason, const Address& address, uint32_t connection_handle) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(BLUETOOTH_DISCONNECTION_REASON_REPORTED, reason, metric_id, connection_handle);
  if (ret < 0) {
    LOG_WARN(
        "Failed for LogMetricBluetoothDisconnectionReasonReported, "
        "reason %d, metric_id %d, connection_handle %d, error %d",
        reason,
        metric_id,
        connection_handle,
        ret);
  }
}

void LogMetricBluetoothRemoteSupportedFeatures(
    const Address& address, uint32_t page, uint64_t features, uint32_t connection_handle) {
  int metric_id = 0;
  if (!address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(address);
  }
  int ret = stats_write(
      BLUETOOTH_REMOTE_SUPPORTED_FEATURES_REPORTED,
      metric_id,
      page,
      static_cast<int64_t>(features),
      connection_handle);
  if (ret < 0) {
    LOG_WARN(
        "Failed for LogMetricBluetoothRemoteSupportedFeatures, "
        "metric_id %d, page %d, features %s, connection_handle %d, error %d",
        metric_id,
        page,
        std::to_string(features).c_str(),
        connection_handle,
        ret);
  }
}

void LogMetricBluetoothCodePathCounterMetrics(int32_t key, int64_t count) {
  int ret = stats_write(BLUETOOTH_CODE_PATH_COUNTER, key, count);
  if (ret < 0) {
    LOG_WARN(
        "Failed counter metrics for %d, count %s, error %d",
        key, std::to_string(count).c_str(), ret);
  }
}

void LogMetricBluetoothLEConnectionMetricEvent(
    const Address& address,
    android::bluetooth::le::LeConnectionOriginType origin_type,
    android::bluetooth::le::LeConnectionType connection_type,
    android::bluetooth::le::LeConnectionState transaction_state,
    std::vector<std::pair<os::ArgumentType, int>>& argument_list) {
  bluetooth::metrics::MetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address, origin_type, connection_type, transaction_state, argument_list);
}

void LogMetricBluetoothLEConnection(os::LEConnectionSessionOptions session_options) {
  int metric_id = 0;
  if (!session_options.remote_address.IsEmpty()) {
    metric_id = MetricIdManager::GetInstance().AllocateId(session_options.remote_address);
  }
  int ret = stats_write(
      BLUETOOTH_LE_SESSION_CONNECTED,
      session_options.acl_connection_state,
      session_options.origin_type,
      session_options.transaction_type,
      session_options.transaction_state,
      session_options.latency,
      metric_id,
      session_options.app_uid,
      session_options.acl_latency,
      session_options.status,
      session_options.is_cancelled);

  if (ret < 0) {
    LOG_WARN(
        "Failed BluetoothLeSessionConnected - Address: %s, ACL Connection State: %s, Origin Type:  "
        "%s",
        ADDRESS_TO_LOGGABLE_CSTR(session_options.remote_address),
        common::ToHexString(session_options.acl_connection_state).c_str(),
        common::ToHexString(session_options.origin_type).c_str());
  }
}

}  // namespace os
}  // namespace bluetooth

