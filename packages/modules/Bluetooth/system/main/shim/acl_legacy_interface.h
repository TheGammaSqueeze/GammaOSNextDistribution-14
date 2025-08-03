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

#pragma once

#include <cstdint>

#include "hci/class_of_device.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hci_mode.h"
#include "types/ble_address_with_type.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {
namespace legacy {

typedef struct {
  void (*on_connected)(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                       bool locally_initiated);
  void (*on_connect_request)(const RawAddress& bda, const hci::ClassOfDevice&);
  void (*on_failed)(const RawAddress& bda, tHCI_STATUS status,
                    bool locally_initiated);
  void (*on_disconnected)(tHCI_STATUS status, uint16_t handle,
                          tHCI_STATUS reason);
} acl_classic_connection_interface_t;

typedef struct {
  void (*on_connected)(const tBLE_BD_ADDR& address_with_type, uint16_t handle,
                       tHCI_ROLE role, uint16_t conn_interval,
                       uint16_t conn_latency, uint16_t conn_timeout,
                       const RawAddress& local_rpa, const RawAddress& peer_rpa,
                       tBLE_ADDR_TYPE peer_addr_type,
                       bool can_read_discoverable_characteristics);
  void (*on_failed)(const tBLE_BD_ADDR& address_with_type, uint16_t handle,
                    bool enhanced, tHCI_STATUS status);
  void (*on_disconnected)(tHCI_STATUS status, uint16_t handle,
                          tHCI_STATUS reason);
} acl_le_connection_interface_t;

typedef struct {
  void (*on_authentication_complete)(uint16_t handle, tHCI_STATUS status);
  void (*on_change_connection_link_key_complete)();
  void (*on_encryption_change)(bool enabled);
  void (*on_flow_specification_complete)(uint16_t flow_direction,
                                         uint16_t service_type,
                                         uint32_t token_rate,
                                         uint32_t token_bucket_size,
                                         uint32_t peak_bandwidth,
                                         uint32_t access_latency);
  void (*on_flush_occurred)();
  void (*on_central_link_key_complete)(uint8_t key_flag);
  void (*on_mode_change)(tHCI_STATUS status, uint16_t handle,
                         tHCI_MODE current_mode, uint16_t interval);
  void (*on_sniff_subrating)(tHCI_STATUS status, uint16_t handle,
                             uint16_t maximum_transmit_latency,
                             uint16_t maximum_receive_latency,
                             uint16_t minimum_remote_timeout,
                             uint16_t minimum_local_timeout);
  void (*on_packet_type_changed)(uint16_t packet_type);
  void (*on_qos_setup_complete)(uint16_t service_type, uint32_t token_rate,
                                uint32_t peak_bandwidth, uint32_t latency,
                                uint32_t delay_variation);
  void (*on_read_afh_channel_map_complete)(uint8_t afh_mode,
                                           uint8_t afh_channel_map[]);
  void (*on_read_automatic_flush_timeout_complete)(uint16_t flush_timeout);
  void (*on_read_clock_complete)(uint32_t clock, uint16_t accuracy);
  void (*on_read_clock_offset_complete)(uint16_t clock_offset);
  void (*on_read_failed_contact_counter_complete)(
      uint16_t failed_contact_counter);
  void (*on_read_link_policy_settings_complete)(uint16_t link_policy_settings);
  void (*on_read_link_quality_complete)(uint8_t link_quality);
  void (*on_read_link_supervision_timeout_complete)(
      uint16_t link_supervision_timeout);
  void (*on_read_remote_supported_features_complete)(uint16_t handle,
                                                     uint64_t features);
  void (*on_read_remote_extended_features_complete)(uint16_t handle,
                                                    uint8_t current_page_number,
                                                    uint8_t max_page_number,
                                                    uint64_t features);
  void (*on_read_remote_version_information_complete)(
      tHCI_STATUS status, uint16_t handle, uint8_t lmp_version,
      uint16_t manufacturer_name, uint16_t sub_version);
  void (*on_read_rssi_complete)(uint8_t rssi);
  void (*on_read_transmit_power_level_complete)(uint8_t transmit_power_level);
  void (*on_role_change)(tHCI_STATUS status, const RawAddress& bd_addr,
                         tHCI_ROLE new_role);
  void (*on_role_discovery_complete)(tHCI_ROLE current_role);
} acl_classic_link_interface_t;

typedef struct {
  void (*on_connection_update)(tHCI_STATUS status, uint16_t handle,
                               uint16_t connection_interval,
                               uint16_t connection_latency,
                               uint16_t supervision_timeout);
  void (*on_data_length_change)(uint16_t handle, uint16_t max_tx_octets,
                                uint16_t max_tx_time, uint16_t max_rx_octets,
                                uint16_t max_rx_time);
  void (*on_read_remote_version_information_complete)(
      tHCI_STATUS status, uint16_t handle, uint8_t lmp_version,
      uint16_t manufacturer_name, uint16_t sub_version);
  void (*on_phy_update)(tHCI_STATUS status, uint16_t handle, uint8_t tx_phy,
                        uint8_t rx_phy);

  void (*on_le_subrate_change)(uint16_t handle, uint16_t subrate_factor,
                               uint16_t latency, uint16_t cont_num,
                               uint16_t timeout, uint8_t status);
} acl_le_link_interface_t;

typedef struct {
  acl_classic_connection_interface_t classic;
  acl_le_connection_interface_t le;
} acl_connection_interface_t;

typedef struct {
  acl_classic_link_interface_t classic;
  acl_le_link_interface_t le;
} acl_link_interface_t;

typedef struct {
  void (*on_send_data_upwards)(BT_HDR*);
  void (*on_packets_completed)(uint16_t handle, uint16_t num_packets);
  acl_connection_interface_t connection;
  acl_link_interface_t link;
} acl_interface_t;

const acl_interface_t& GetAclInterface();

}  // namespace legacy
}  // namespace shim
}  // namespace bluetooth
