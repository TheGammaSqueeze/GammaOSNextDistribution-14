/*
 * Copyright 2022 The Android Open Source Project
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

#include "internal_include/stack_config.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdarg>
#include <cstring>

const std::string kSmpOptions("mock smp options");
const std::string kBroadcastAudioConfigOptions(
    "mock broadcast audio config options");
bool get_pts_avrcp_test(void) { return false; }
bool get_pts_secure_only_mode(void) { return false; }
bool get_pts_conn_updates_disabled(void) { return false; }
bool get_pts_crosskey_sdp_disable(void) { return false; }
const std::string* get_pts_smp_options(void) { return &kSmpOptions; }
int get_pts_smp_failure_case(void) { return 123; }
bool get_pts_force_eatt_for_notifications(void) { return false; }
bool get_pts_connect_eatt_unconditionally(void) { return false; }
bool get_pts_connect_eatt_before_encryption(void) { return false; }
bool get_pts_unencrypt_broadcast(void) { return false; }
bool get_pts_eatt_peripheral_collision_support(void) { return false; }
bool get_pts_use_eatt_for_all_services(void) { return false; }
bool get_pts_force_le_audio_multiple_contexts_metadata(void) { return false; }
bool get_pts_l2cap_ecoc_upper_tester(void) { return false; }
int get_pts_l2cap_ecoc_min_key_size(void) { return -1; }
int get_pts_l2cap_ecoc_initial_chan_cnt(void) { return -1; }
bool get_pts_l2cap_ecoc_connect_remaining(void) { return false; }
int get_pts_l2cap_ecoc_send_num_of_sdu(void) { return -1; }
bool get_pts_l2cap_ecoc_reconfigure(void) { return false; }
const std::string* get_pts_broadcast_audio_config_options(void) {
  return &kBroadcastAudioConfigOptions;
}
bool get_pts_le_audio_disable_ases_before_stopping(void) { return false; }
struct config_t;
config_t* get_all(void) { return nullptr; }
struct packet_fragmenter_t;
const packet_fragmenter_t* packet_fragmenter_get_interface() { return nullptr; }

stack_config_t mock_stack_config{
    .get_pts_avrcp_test = get_pts_avrcp_test,
    .get_pts_secure_only_mode = get_pts_secure_only_mode,
    .get_pts_conn_updates_disabled = get_pts_conn_updates_disabled,
    .get_pts_crosskey_sdp_disable = get_pts_crosskey_sdp_disable,
    .get_pts_smp_options = get_pts_smp_options,
    .get_pts_smp_failure_case = get_pts_smp_failure_case,
    .get_pts_force_eatt_for_notifications =
        get_pts_force_eatt_for_notifications,
    .get_pts_connect_eatt_unconditionally =
        get_pts_connect_eatt_unconditionally,
    .get_pts_connect_eatt_before_encryption =
        get_pts_connect_eatt_before_encryption,
    .get_pts_unencrypt_broadcast = get_pts_unencrypt_broadcast,
    .get_pts_eatt_peripheral_collision_support =
        get_pts_eatt_peripheral_collision_support,
    .get_pts_use_eatt_for_all_services = get_pts_use_eatt_for_all_services,
    .get_pts_force_le_audio_multiple_contexts_metadata =
        get_pts_force_le_audio_multiple_contexts_metadata,
    .get_pts_l2cap_ecoc_upper_tester = get_pts_l2cap_ecoc_upper_tester,
    .get_pts_l2cap_ecoc_min_key_size = get_pts_l2cap_ecoc_min_key_size,
    .get_pts_l2cap_ecoc_initial_chan_cnt = get_pts_l2cap_ecoc_initial_chan_cnt,
    .get_pts_l2cap_ecoc_connect_remaining =
        get_pts_l2cap_ecoc_connect_remaining,
    .get_pts_l2cap_ecoc_send_num_of_sdu = get_pts_l2cap_ecoc_send_num_of_sdu,
    .get_pts_l2cap_ecoc_reconfigure = get_pts_l2cap_ecoc_reconfigure,
    .get_pts_broadcast_audio_config_options =
        get_pts_broadcast_audio_config_options,
    .get_pts_le_audio_disable_ases_before_stopping =
        get_pts_le_audio_disable_ases_before_stopping,
    .get_all = get_all,
};

const stack_config_t* stack_config_get_interface(void) {
  return &mock_stack_config;
}
