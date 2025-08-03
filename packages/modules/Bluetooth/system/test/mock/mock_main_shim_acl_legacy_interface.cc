/*
 * Copyright 2024 The Android Open Source Project
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

namespace bluetooth {
namespace shim {
namespace legacy {

acl_interface_t acl_interface_ = {
    .on_send_data_upwards = nullptr,
    .on_packets_completed = nullptr,

    .connection.classic.on_connected = nullptr,
    .connection.classic.on_connect_request = nullptr,
    .connection.classic.on_failed = nullptr,
    .connection.classic.on_disconnected = nullptr,

    .connection.le.on_connected = nullptr,
    .connection.le.on_failed = nullptr,
    .connection.le.on_disconnected = nullptr,

    .link.classic.on_authentication_complete = nullptr,
    .link.classic.on_central_link_key_complete = nullptr,
    .link.classic.on_change_connection_link_key_complete = nullptr,
    .link.classic.on_encryption_change = nullptr,
    .link.classic.on_flow_specification_complete = nullptr,
    .link.classic.on_flush_occurred = nullptr,
    .link.classic.on_mode_change = nullptr,
    .link.classic.on_packet_type_changed = nullptr,
    .link.classic.on_qos_setup_complete = nullptr,
    .link.classic.on_read_afh_channel_map_complete = nullptr,
    .link.classic.on_read_automatic_flush_timeout_complete = nullptr,
    .link.classic.on_sniff_subrating = nullptr,
    .link.classic.on_read_clock_complete = nullptr,
    .link.classic.on_read_clock_offset_complete = nullptr,
    .link.classic.on_read_failed_contact_counter_complete = nullptr,
    .link.classic.on_read_link_policy_settings_complete = nullptr,
    .link.classic.on_read_link_quality_complete = nullptr,
    .link.classic.on_read_link_supervision_timeout_complete = nullptr,
    .link.classic.on_read_remote_version_information_complete = nullptr,
    .link.classic.on_read_remote_supported_features_complete = nullptr,
    .link.classic.on_read_remote_extended_features_complete = nullptr,
    .link.classic.on_read_rssi_complete = nullptr,
    .link.classic.on_read_transmit_power_level_complete = nullptr,
    .link.classic.on_role_change = nullptr,
    .link.classic.on_role_discovery_complete = nullptr,

    .link.le.on_connection_update = nullptr,
    .link.le.on_data_length_change = nullptr,
    .link.le.on_read_remote_version_information_complete = nullptr,
    .link.le.on_phy_update = nullptr,
    .link.le.on_le_subrate_change = nullptr,
};

const acl_interface_t& GetAclInterface() { return acl_interface_; }

}  // namespace legacy
}  // namespace shim
}  // namespace bluetooth
