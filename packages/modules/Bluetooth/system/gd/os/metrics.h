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

#pragma once

#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/hci/enums.pb.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/le/enums.pb.h>

#include <vector>

#include "hci/address.h"

namespace bluetooth {

namespace os {
/**
 * Unknown connection handle for metrics purpose
 */
static const uint32_t kUnknownConnectionHandle = 0xFFFF;

/**
 * Log link layer connection event
 *
 * @param address Stack wide consistent Bluetooth address of this event,
 *                nullptr if unknown
 * @param connection_handle connection handle of this event,
 *                          {@link kUnknownConnectionHandle} if unknown
 * @param direction direction of this connection
 * @param link_type type of the link
 * @param hci_cmd HCI command opecode associated with this event, if any
 * @param hci_event HCI event code associated with this event, if any
 * @param hci_ble_event HCI BLE event code associated with this event, if any
 * @param cmd_status Command status associated with this event, if any
 * @param reason_code Reason code associated with this event, if any
 */
void LogMetricLinkLayerConnectionEvent(
    const hci::Address* address,
    uint32_t connection_handle,
    android::bluetooth::DirectionEnum direction,
    uint16_t link_type,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t hci_ble_event,
    uint16_t cmd_status,
    uint16_t reason_code);

/**
 * Logs when Bluetooth controller failed to reply with command status within
 * a timeout period after receiving an HCI command from the host
 *
 * @param hci_cmd opcode of HCI command that caused this timeout
 */
void LogMetricHciTimeoutEvent(uint32_t hci_cmd);

/**
 * Logs when we receive Bluetooth Read Remote Version Information Complete
 * Event from the remote device, as documented by the Bluetooth Core HCI
 * specification
 *
 * Reference: 5.0 Core Specification, Vol 2, Part E, Page 1118
 *
 * @param handle handle of associated ACL connection
 * @param status HCI command status of this event
 * @param version version code from read remote version complete event
 * @param manufacturer_name manufacturer code from read remote version complete
 *                          event
 * @param subversion subversion code from read remote version complete event
 */
void LogMetricRemoteVersionInfo(
    uint16_t handle, uint8_t status, uint8_t version, uint16_t manufacturer_name, uint16_t subversion);

/**
 * Log A2DP audio buffer underrun event
 *
 * @param address A2DP device associated with this event
 * @param encoding_interval_millis encoding interval in milliseconds
 * @param num_missing_pcm_bytes number of PCM bytes that cannot be read from
 *                              the source
 */
void LogMetricA2dpAudioUnderrunEvent(
    const hci::Address& address, uint64_t encoding_interval_millis, int num_missing_pcm_bytes);

/**
 * Log A2DP audio buffer overrun event
 *
 * @param address A2DP device associated with this event
 * @param encoding_interval_millis encoding interval in milliseconds
 * @param num_dropped_buffers number of encoded buffers dropped from Tx queue
 * @param num_dropped_encoded_frames number of encoded frames dropped from Tx
 *                                   queue
 * @param num_dropped_encoded_bytes number of encoded bytes dropped from Tx
 *                                  queue
 */
void LogMetricA2dpAudioOverrunEvent(
    const hci::Address& address,
    uint64_t encoding_interval_millis,
    int num_dropped_buffers,
    int num_dropped_encoded_frames,
    int num_dropped_encoded_bytes);

/**
 * Log A2DP audio playback state changed event
 *
 * @param address A2DP device associated with this event
 * @param playback_state A2DP audio playback state, on/off
 * @param audio_coding_mode A2DP audio codec encoding mode, hw/sw
 */
void LogMetricA2dpPlaybackEvent(
    const hci::Address& address, int playback_state, int audio_coding_mode);

/**
 * Log A2DP audio session metrics
 *
 * @param address A2DP device associated with this session
 * @param audio_duration_ms duration of the A2DP session
 * @param media_timer_min_ms min time interval for the media timer
 * @param media_timer_max_ms max time interval for the media timer
 * @param media_timer_avg_ms avg time interval for the media timer
 * @param total_scheduling_count total scheduling count
 * @param buffer_overruns_max_count max count of Tx queue messages dropped
                                    caused by buffer overruns
 * @param buffer_overruns_total total count of Tx queue messages dropped
                                caused by buffer overruns
 * @param buffer_underruns_average  avg number of bytes short in buffer
                                    underruns
 * @param buffer_underruns_count count of buffer underruns
 * @param codec_index A2DP codec index (SBC=0, AAC=1, etc...)
 * @param is_a2dp_offload if A2DP is offload
 */
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
    bool is_a2dp_offload);

/**
 * Log HFP audio capture packet loss statistics
 *
 * @param address HFP device associated with this stats
 * @param num_decoded_frames number of decoded frames
 * @param packet_loss_ratio ratio of packet loss frames
 * @param codec_id codec ID of the packet (mSBC=2, LC3=3)
 */
void LogMetricHfpPacketLossStats(
    const hci::Address& address,
    int num_decoded_frames,
    double packet_loss_ratio,
    uint16_t codec_id);

/**
 * Log Mmc transcode round-trip time statistics
 *
 * @param maximum_rtt maximum round-trip time in this session
 * @param mean_rtt the average of round-trip time in this session
 * @param num_requests the number of transcoding requests in the session
 * @param codec_type codec type used in this session
 */
void LogMetricMmcTranscodeRttStats(
    int maximum_rtt, double mean_rtt, int num_requests, int codec_type);

/**
 * Log read RSSI result
 *
 * @param address device associated with this event
 * @param handle connection handle of this event,
 *               {@link kUnknownConnectionHandle} if unknown
 * @param cmd_status command status from read RSSI command
 * @param rssi rssi value in dBm
 */
void LogMetricReadRssiResult(const hci::Address& address, uint16_t handle, uint32_t cmd_status, int8_t rssi);

/**
 * Log failed contact counter report
 *
 * @param address device associated with this event
 * @param handle connection handle of this event,
 *               {@link kUnknownConnectionHandle} if unknown
 * @param cmd_status command status from read failed contact counter command
 * @param failed_contact_counter Number of consecutive failed contacts for a
 *                               connection corresponding to the Handle
 */
void LogMetricReadFailedContactCounterResult(
    const hci::Address& address, uint16_t handle, uint32_t cmd_status, int32_t failed_contact_counter);

/**
 * Log transmit power level for a particular device after read
 *
 * @param address device associated with this event
 * @param handle connection handle of this event,
 *               {@link kUnknownConnectionHandle} if unknown
 * @param cmd_status command status from read failed contact counter command
 * @param transmit_power_level transmit power level for connection to this
 *                             device
 */
void LogMetricReadTxPowerLevelResult(
    const hci::Address& address, uint16_t handle, uint32_t cmd_status, int32_t transmit_power_level);

/**
 * Logs when there is an event related to Bluetooth Security Manager Protocol
 *
 * @param address address of associated device
 * @param smp_cmd SMP command code associated with this event
 * @param direction direction of this SMP command
 * @param smp_fail_reason SMP pairing failure reason code from SMP spec
 */
void LogMetricSmpPairingEvent(
    const hci::Address& address,
    uint16_t smp_cmd,
    android::bluetooth::DirectionEnum direction,
    uint16_t smp_fail_reason);

/**
 * Logs there is an event related Bluetooth classic pairing
 *
 * @param address address of associated device
 * @param handle connection handle of this event,
 *               {@link kUnknownConnectionHandle} if unknown
 * @param hci_cmd HCI command associated with this event
 * @param hci_event HCI event associated with this event
 * @param cmd_status Command status associated with this event
 * @param reason_code Reason code associated with this event
 * @param event_value A status value related to this specific event
 */
void LogMetricClassicPairingEvent(
    const hci::Address& address,
    uint16_t handle,
    uint32_t hci_cmd,
    uint16_t hci_event,
    uint16_t cmd_status,
    uint16_t reason_code,
    int64_t event_value);

/**
 * Logs when certain Bluetooth SDP attributes are discovered
 *
 * @param address address of associated device
 * @param protocol_uuid 16 bit protocol UUID from Bluetooth Assigned Numbers
 * @param attribute_id 16 bit attribute ID from Bluetooth Assigned Numbers
 * @param attribute_size size of this attribute
 * @param attribute_value pointer to the attribute data, must be larger than
 *                        attribute_size
 */
void LogMetricSdpAttribute(
    const hci::Address& address,
    uint16_t protocol_uuid,
    uint16_t attribute_id,
    size_t attribute_size,
    const char* attribute_value);

/**
 * Logs when there is a change in Bluetooth socket connection state
 *
 * @param address address of associated device, empty if this is a server port
 * @param port port of this socket connection
 * @param type type of socket
 * @param connection_state socket connection state
 * @param tx_bytes number of bytes transmitted
 * @param rx_bytes number of bytes received
 * @param server_port server port of this socket, if any. When both
 *        |server_port| and |port| fields are populated, |port| must be spawned
 *        by |server_port|
 * @param socket_role role of this socket, server or connection
 * @param uid socket owner's uid
 */
void LogMetricSocketConnectionState(
    const hci::Address& address,
    int port,
    int type,
    android::bluetooth::SocketConnectionstateEnum connection_state,
    int64_t tx_bytes,
    int64_t rx_bytes,
    int uid,
    int server_port,
    android::bluetooth::SocketRoleEnum socket_role);

/**
 * Logs when a Bluetooth device's manufacturer information is learnt
 *
 * @param address address of associated device
 * @param source_type where is this device info obtained from
 * @param source_name name of the data source, internal or external
 * @param manufacturer name of the manufacturer of this device
 * @param model model of this device
 * @param hardware_version hardware version of this device
 * @param software_version software version of this device
 */
void LogMetricManufacturerInfo(
    const hci::Address& address,
    android::bluetooth::AddressTypeEnum address_type,
    android::bluetooth::DeviceInfoSrcEnum source_type,
    const std::string& source_name,
    const std::string& manufacturer,
    const std::string& model,
    const std::string& hardware_version,
    const std::string& software_version);

/**
 * Logs when received Bluetooth HAL crash reason report.
 *
 * @param address current connected address.
 * @param error_code the crash reason from bluetooth hal
 * @param vendor_error_code the vendor crash reason from bluetooth firmware
 */
void LogMetricBluetoothHalCrashReason(
    const hci::Address& address,
    uint32_t error_code,
    uint32_t vendor_error_code);

void LogMetricBluetoothLocalSupportedFeatures(uint32_t page_num, uint64_t features);

void LogMetricBluetoothLocalVersions(
    uint32_t lmp_manufacturer_name,
    uint8_t lmp_version,
    uint32_t lmp_subversion,
    uint8_t hci_version,
    uint32_t hci_revision);

void LogMetricBluetoothDisconnectionReasonReported(
    uint32_t reason, const hci::Address& address, uint32_t connection_handle);

void LogMetricBluetoothRemoteSupportedFeatures(
    const hci::Address& address, uint32_t page, uint64_t features, uint32_t connection_handle);

void LogMetricBluetoothCodePathCounterMetrics(int32_t key, int64_t count);

using android::bluetooth::le::LeAclConnectionState;
using android::bluetooth::le::LeConnectionOriginType;
using android::bluetooth::le::LeConnectionType;
using android::bluetooth::le::LeConnectionState;
// Adding options
struct LEConnectionSessionOptions {
  // Contains the state of the LE-ACL Connection
  LeAclConnectionState acl_connection_state = LeAclConnectionState::LE_ACL_UNSPECIFIED;
  // Origin of the transaction
  LeConnectionOriginType origin_type = LeConnectionOriginType::ORIGIN_UNSPECIFIED;
  // Connection Type
  LeConnectionType transaction_type = LeConnectionType::CONNECTION_TYPE_UNSPECIFIED;
  // Transaction State
  LeConnectionState transaction_state = LeConnectionState::STATE_UNSPECIFIED;
  // Latency of the entire transaction
  int64_t latency = 0;
  // Address of the remote device
  hci::Address remote_address = hci::Address::kEmpty;
  // UID associated with the device
  int app_uid = 0;
  // Latency of the ACL Transaction
  int64_t acl_latency = 0;
  // Contains the error code associated with the ACL Connection if failed
  android::bluetooth::hci::StatusEnum status = android::bluetooth::hci::StatusEnum::STATUS_UNKNOWN;
  // Cancelled connection
  bool is_cancelled = false;
};

// Argument Type
enum ArgumentType { GATT_IF, L2CAP_PSM, L2CAP_CID, APP_UID, ACL_STATUS_CODE };
void LogMetricBluetoothLEConnectionMetricEvent(
    const hci::Address& address,
    LeConnectionOriginType origin_type,
    LeConnectionType connection_type,
    LeConnectionState transaction_state,
    std::vector<std::pair<os::ArgumentType, int>>& argument_list);

// Upload LE Session
void LogMetricBluetoothLEConnection(os::LEConnectionSessionOptions session_options);

}  // namespace os
   //
}  // namespace bluetooth
