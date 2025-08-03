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

#include "main/shim/acl_legacy_interface.h"

#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/ble_acl_interface.h"
#include "stack/include/sec_hci_link_interface.h"

struct tBTM_ESCO_DATA;
void gatt_notify_phy_updated(tHCI_STATUS status, uint16_t handle,
                             uint8_t tx_phy, uint8_t rx_phy);
void gatt_notify_subrate_change(uint16_t handle, uint16_t subrate_factor,
                                uint16_t latency, uint16_t cont_num,
                                uint16_t timeout, uint8_t status);
void l2cble_process_subrate_change_evt(uint16_t handle, uint8_t status,
                                       uint16_t subrate_factor,
                                       uint16_t peripheral_latency,
                                       uint16_t cont_num, uint16_t timeout);

static void on_le_subrate_change(uint16_t handle, uint16_t subrate_factor,
                                 uint16_t latency, uint16_t cont_num,
                                 uint16_t timeout, uint8_t status) {
  l2cble_process_subrate_change_evt(handle, status, subrate_factor, latency,
                                    cont_num, timeout);
  gatt_notify_subrate_change(handle & 0x0FFF, subrate_factor, latency, cont_num,
                             timeout, status);
}

namespace bluetooth {
namespace shim {
namespace legacy {

const acl_interface_t& GetAclInterface() {
  static acl_interface_t acl_interface{
      .on_send_data_upwards = acl_rcv_acl_data,
      .on_packets_completed = acl_packets_completed,

      .connection.classic.on_connected = on_acl_br_edr_connected,
      .connection.classic.on_connect_request = btm_connection_request,
      .connection.classic.on_failed = on_acl_br_edr_failed,
      .connection.classic.on_disconnected = btm_acl_disconnected,

      .connection.le.on_connected =
          acl_ble_enhanced_connection_complete_from_shim,
      .connection.le.on_failed = acl_ble_connection_fail,
      .connection.le.on_disconnected = btm_acl_disconnected,

      .link.classic.on_authentication_complete = btm_sec_auth_complete,
      .link.classic.on_central_link_key_complete = nullptr,
      .link.classic.on_change_connection_link_key_complete = nullptr,
      .link.classic.on_encryption_change = nullptr,
      .link.classic.on_flow_specification_complete = nullptr,
      .link.classic.on_flush_occurred = nullptr,
      .link.classic.on_mode_change = btm_pm_on_mode_change,
      .link.classic.on_packet_type_changed = nullptr,
      .link.classic.on_qos_setup_complete = nullptr,
      .link.classic.on_read_afh_channel_map_complete = nullptr,
      .link.classic.on_read_automatic_flush_timeout_complete = nullptr,
      .link.classic.on_sniff_subrating = btm_pm_on_sniff_subrating,
      .link.classic.on_read_clock_complete = nullptr,
      .link.classic.on_read_clock_offset_complete = nullptr,
      .link.classic.on_read_failed_contact_counter_complete = nullptr,
      .link.classic.on_read_link_policy_settings_complete = nullptr,
      .link.classic.on_read_link_quality_complete = nullptr,
      .link.classic.on_read_link_supervision_timeout_complete = nullptr,
      .link.classic.on_read_remote_version_information_complete =
          btm_read_remote_version_complete,
      .link.classic.on_read_remote_supported_features_complete =
          acl_process_supported_features,
      .link.classic.on_read_remote_extended_features_complete =
          acl_process_extended_features,
      .link.classic.on_read_rssi_complete = nullptr,
      .link.classic.on_read_transmit_power_level_complete = nullptr,
      .link.classic.on_role_change = btm_acl_role_changed,
      .link.classic.on_role_discovery_complete = nullptr,

      .link.le.on_connection_update = acl_ble_update_event_received,
      .link.le.on_data_length_change = acl_ble_data_length_change_event,
      .link.le.on_read_remote_version_information_complete =
          btm_read_remote_version_complete,
      .link.le.on_phy_update = gatt_notify_phy_updated,
      .link.le.on_le_subrate_change = on_le_subrate_change,
  };
  return acl_interface;
}

}  // namespace legacy
}  // namespace shim
}  // namespace bluetooth
