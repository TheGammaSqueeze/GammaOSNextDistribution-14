/*
 * Copyright 2023 The Android Open Source Project
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
 *   Functions generated:40
 *
 *  mockcify.pl ver 0.7.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_profile_storage.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings
using bluetooth::Uuid;

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_profile_storage {

// Function state capture and return values, if needed
struct btif_storage_add_groups btif_storage_add_groups;
struct btif_storage_add_hearing_aid btif_storage_add_hearing_aid;
struct btif_storage_add_hid_device_info btif_storage_add_hid_device_info;
struct btif_storage_add_leaudio_has_device btif_storage_add_leaudio_has_device;
struct btif_storage_get_hearing_aid_prop btif_storage_get_hearing_aid_prop;
struct btif_storage_get_le_hid_devices btif_storage_get_le_hid_devices;
struct btif_storage_get_leaudio_has_features
    btif_storage_get_leaudio_has_features;
struct btif_storage_get_leaudio_has_presets
    btif_storage_get_leaudio_has_presets;
struct btif_storage_get_wake_capable_classic_hid_devices
    btif_storage_get_wake_capable_classic_hid_devices;
struct btif_storage_is_pce_version_102 btif_storage_is_pce_version_102;
struct btif_storage_leaudio_clear_service_data
    btif_storage_leaudio_clear_service_data;
struct btif_storage_leaudio_update_ase_bin btif_storage_leaudio_update_ase_bin;
struct btif_storage_leaudio_update_handles_bin
    btif_storage_leaudio_update_handles_bin;
struct btif_storage_leaudio_update_pacs_bin
    btif_storage_leaudio_update_pacs_bin;
struct btif_storage_load_bonded_csis_devices
    btif_storage_load_bonded_csis_devices;
struct btif_storage_load_bonded_groups btif_storage_load_bonded_groups;
struct btif_storage_load_bonded_hearing_aids
    btif_storage_load_bonded_hearing_aids;
struct btif_storage_load_bonded_hid_info btif_storage_load_bonded_hid_info;
struct btif_storage_load_bonded_leaudio btif_storage_load_bonded_leaudio;
struct btif_storage_load_bonded_leaudio_has_devices
    btif_storage_load_bonded_leaudio_has_devices;
struct btif_storage_load_bonded_volume_control_devices
    btif_storage_load_bonded_volume_control_devices;
struct btif_storage_load_hidd btif_storage_load_hidd;
struct btif_storage_remove_csis_device btif_storage_remove_csis_device;
struct btif_storage_remove_groups btif_storage_remove_groups;
struct btif_storage_remove_hearing_aid btif_storage_remove_hearing_aid;
struct btif_storage_remove_hid_info btif_storage_remove_hid_info;
struct btif_storage_remove_hidd btif_storage_remove_hidd;
struct btif_storage_remove_leaudio btif_storage_remove_leaudio;
struct btif_storage_remove_leaudio_has btif_storage_remove_leaudio_has;
struct btif_storage_set_hearing_aid_acceptlist
    btif_storage_set_hearing_aid_acceptlist;
struct btif_storage_set_hidd btif_storage_set_hidd;
struct btif_storage_set_leaudio_audio_location
    btif_storage_set_leaudio_audio_location;
struct btif_storage_set_leaudio_autoconnect
    btif_storage_set_leaudio_autoconnect;
struct btif_storage_set_leaudio_has_acceptlist
    btif_storage_set_leaudio_has_acceptlist;
struct btif_storage_set_leaudio_has_active_preset
    btif_storage_set_leaudio_has_active_preset;
struct btif_storage_set_leaudio_has_features
    btif_storage_set_leaudio_has_features;
struct btif_storage_set_leaudio_has_presets
    btif_storage_set_leaudio_has_presets;
struct btif_storage_set_leaudio_supported_context_types
    btif_storage_set_leaudio_supported_context_types;
struct btif_storage_set_pce_profile_version
    btif_storage_set_pce_profile_version;
struct btif_storage_update_csis_info btif_storage_update_csis_info;

}  // namespace btif_profile_storage
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_profile_storage {

bt_status_t btif_storage_add_hid_device_info::return_value = BT_STATUS_SUCCESS;
bool btif_storage_get_hearing_aid_prop::return_value = false;
std::vector<std::pair<RawAddress, uint8_t>>
    btif_storage_get_le_hid_devices::return_value = {};
bool btif_storage_get_leaudio_has_features::return_value = false;
bool btif_storage_get_leaudio_has_presets::return_value = false;
std::vector<RawAddress>
    btif_storage_get_wake_capable_classic_hid_devices::return_value = {};
bool btif_storage_is_pce_version_102::return_value = false;
bt_status_t btif_storage_load_bonded_hid_info::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_load_hidd::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_remove_hid_info::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_remove_hidd::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_set_hidd::return_value = BT_STATUS_SUCCESS;

}  // namespace btif_profile_storage
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btif_storage_add_groups(const RawAddress& addr) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_add_groups(addr);
}
void btif_storage_add_hearing_aid(const HearingDevice& dev_info) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_add_hearing_aid(dev_info);
}
bt_status_t btif_storage_add_hid_device_info(
    RawAddress* remote_bd_addr, uint16_t attr_mask, uint8_t sub_class,
    uint8_t app_id, uint16_t vendor_id, uint16_t product_id, uint16_t version,
    uint8_t ctry_code, uint16_t ssr_max_latency, uint16_t ssr_min_tout,
    uint16_t dl_len, uint8_t* dsc_list) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_add_hid_device_info(
      remote_bd_addr, attr_mask, sub_class, app_id, vendor_id, product_id,
      version, ctry_code, ssr_max_latency, ssr_min_tout, dl_len, dsc_list);
}
void btif_storage_add_leaudio_has_device(const RawAddress& address,
                                         std::vector<uint8_t> presets_bin,
                                         uint8_t features,
                                         uint8_t active_preset) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_add_leaudio_has_device(
      address, presets_bin, features, active_preset);
}
bool btif_storage_get_hearing_aid_prop(
    const RawAddress& address, uint8_t* capabilities, uint64_t* hi_sync_id,
    uint16_t* render_delay, uint16_t* preparation_delay, uint16_t* codecs) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_get_hearing_aid_prop(
      address, capabilities, hi_sync_id, render_delay, preparation_delay,
      codecs);
}
std::vector<std::pair<RawAddress, uint8_t>> btif_storage_get_le_hid_devices(
    void) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_get_le_hid_devices();
}
bool btif_storage_get_leaudio_has_features(const RawAddress& address,
                                           uint8_t& features) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::
      btif_storage_get_leaudio_has_features(address, features);
}
bool btif_storage_get_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t>& presets_bin,
                                          uint8_t& active_preset) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_get_leaudio_has_presets(
      address, presets_bin, active_preset);
}
std::vector<RawAddress> btif_storage_get_wake_capable_classic_hid_devices(
    void) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::
      btif_storage_get_wake_capable_classic_hid_devices();
}
bool btif_storage_is_pce_version_102(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_is_pce_version_102(
      remote_bd_addr);
}
void btif_storage_leaudio_clear_service_data(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_leaudio_clear_service_data(
      address);
}
void btif_storage_leaudio_update_ase_bin(const RawAddress& addr) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_leaudio_update_ase_bin(addr);
}
void btif_storage_leaudio_update_handles_bin(const RawAddress& addr) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_leaudio_update_handles_bin(
      addr);
}
void btif_storage_leaudio_update_pacs_bin(const RawAddress& addr) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_leaudio_update_pacs_bin(addr);
}
void btif_storage_load_bonded_csis_devices(void) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_load_bonded_csis_devices();
}
void btif_storage_load_bonded_groups(void) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_load_bonded_groups();
}
void btif_storage_load_bonded_hearing_aids() {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_load_bonded_hearing_aids();
}
bt_status_t btif_storage_load_bonded_hid_info(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_load_bonded_hid_info();
}
void btif_storage_load_bonded_leaudio() {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_load_bonded_leaudio();
}
void btif_storage_load_bonded_leaudio_has_devices() {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::
      btif_storage_load_bonded_leaudio_has_devices();
}
void btif_storage_load_bonded_volume_control_devices(void) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::
      btif_storage_load_bonded_volume_control_devices();
}
bt_status_t btif_storage_load_hidd(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_load_hidd();
}
void btif_storage_remove_csis_device(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_remove_csis_device(address);
}
void btif_storage_remove_groups(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_remove_groups(address);
}
void btif_storage_remove_hearing_aid(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_remove_hearing_aid(address);
}
bt_status_t btif_storage_remove_hid_info(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_remove_hid_info(
      remote_bd_addr);
}
bt_status_t btif_storage_remove_hidd(RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_remove_hidd(
      remote_bd_addr);
}
void btif_storage_remove_leaudio(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_remove_leaudio(address);
}
void btif_storage_remove_leaudio_has(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_remove_leaudio_has(address);
}
void btif_storage_set_hearing_aid_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_hearing_aid_acceptlist(
      address, add_to_acceptlist);
}
bt_status_t btif_storage_set_hidd(const RawAddress& remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_storage::btif_storage_set_hidd(
      remote_bd_addr);
}
void btif_storage_set_leaudio_audio_location(const RawAddress& addr,
                                             uint32_t sink_location,
                                             uint32_t source_location) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_audio_location(
      addr, sink_location, source_location);
}
void btif_storage_set_leaudio_autoconnect(const RawAddress& addr,
                                          bool autoconnect) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_autoconnect(
      addr, autoconnect);
}
void btif_storage_set_leaudio_has_acceptlist(const RawAddress& address,
                                             bool add_to_acceptlist) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_has_acceptlist(
      address, add_to_acceptlist);
}
void btif_storage_set_leaudio_has_active_preset(const RawAddress& address,
                                                uint8_t active_preset) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_has_active_preset(
      address, active_preset);
}
void btif_storage_set_leaudio_has_features(const RawAddress& address,
                                           uint8_t features) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_has_features(
      address, features);
}
void btif_storage_set_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t> presets_bin) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_leaudio_has_presets(
      address, presets_bin);
}
void btif_storage_set_leaudio_supported_context_types(
    const RawAddress& addr, uint16_t sink_supported_context_type,
    uint16_t source_supported_context_type) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::
      btif_storage_set_leaudio_supported_context_types(
          addr, sink_supported_context_type, source_supported_context_type);
}
void btif_storage_set_pce_profile_version(const RawAddress& remote_bd_addr,
                                          uint16_t peer_pce_version) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_set_pce_profile_version(
      remote_bd_addr, peer_pce_version);
}
void btif_storage_update_csis_info(const RawAddress& addr) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_storage::btif_storage_update_csis_info(addr);
}
// Mocked functions complete
// END mockcify generation
