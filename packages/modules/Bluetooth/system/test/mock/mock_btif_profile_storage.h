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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:40
 *
 *  mockcify.pl ver 0.7.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include <alloca.h>
#include <base/logging.h>
#include <stdlib.h>

#include <vector>

#include "bta/include/bta_hearing_aid_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Original usings
using bluetooth::Uuid;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_profile_storage {

// Shared state between mocked functions and tests
// Name: btif_storage_add_groups
// Params: const RawAddress& addr
// Return: void
struct btif_storage_add_groups {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct btif_storage_add_groups btif_storage_add_groups;

// Name: btif_storage_add_hearing_aid
// Params: const HearingDevice& dev_info
// Return: void
struct btif_storage_add_hearing_aid {
  std::function<void(const HearingDevice& dev_info)> body{
      [](const HearingDevice& /* dev_info */) {}};
  void operator()(const HearingDevice& dev_info) { body(dev_info); };
};
extern struct btif_storage_add_hearing_aid btif_storage_add_hearing_aid;

// Name: btif_storage_add_hid_device_info
// Params: RawAddress* remote_bd_addr, uint16_t attr_mask, uint8_t sub_class,
// uint8_t app_id, uint16_t vendor_id, uint16_t product_id, uint16_t version,
// uint8_t ctry_code, uint16_t ssr_max_latency, uint16_t ssr_min_tout, uint16_t
// dl_len, uint8_t* dsc_list Return: bt_status_t
struct btif_storage_add_hid_device_info {
  static bt_status_t return_value;
  std::function<bt_status_t(
      RawAddress* remote_bd_addr, uint16_t attr_mask, uint8_t sub_class,
      uint8_t app_id, uint16_t vendor_id, uint16_t product_id, uint16_t version,
      uint8_t ctry_code, uint16_t ssr_max_latency, uint16_t ssr_min_tout,
      uint16_t dl_len, uint8_t* dsc_list)>
      body{[](RawAddress* /* remote_bd_addr */, uint16_t /* attr_mask */,
              uint8_t /* sub_class */, uint8_t /* app_id */,
              uint16_t /* vendor_id */, uint16_t /* product_id */,
              uint16_t /* version */, uint8_t /* ctry_code */,
              uint16_t /* ssr_max_latency */, uint16_t /* ssr_min_tout */,
              uint16_t /* dl_len */,
              uint8_t* /* dsc_list */) { return return_value; }};
  bt_status_t operator()(RawAddress* remote_bd_addr, uint16_t attr_mask,
                         uint8_t sub_class, uint8_t app_id, uint16_t vendor_id,
                         uint16_t product_id, uint16_t version,
                         uint8_t ctry_code, uint16_t ssr_max_latency,
                         uint16_t ssr_min_tout, uint16_t dl_len,
                         uint8_t* dsc_list) {
    return body(remote_bd_addr, attr_mask, sub_class, app_id, vendor_id,
                product_id, version, ctry_code, ssr_max_latency, ssr_min_tout,
                dl_len, dsc_list);
  };
};
extern struct btif_storage_add_hid_device_info btif_storage_add_hid_device_info;

// Name: btif_storage_add_leaudio_has_device
// Params: const RawAddress& address, std::vector<uint8_t> presets_bin, uint8_t
// features, uint8_t active_preset Return: void
struct btif_storage_add_leaudio_has_device {
  std::function<void(const RawAddress& address,
                     std::vector<uint8_t> presets_bin, uint8_t features,
                     uint8_t active_preset)>
      body{[](const RawAddress& /* address */,
              std::vector<uint8_t> /* presets_bin */, uint8_t /* features */,
              uint8_t /* active_preset */) {}};
  void operator()(const RawAddress& address, std::vector<uint8_t> presets_bin,
                  uint8_t features, uint8_t active_preset) {
    body(address, presets_bin, features, active_preset);
  };
};
extern struct btif_storage_add_leaudio_has_device
    btif_storage_add_leaudio_has_device;

// Name: btif_storage_get_hearing_aid_prop
// Params: const RawAddress& address, uint8_t* capabilities, uint64_t*
// hi_sync_id, uint16_t* render_delay, uint16_t* preparation_delay, uint16_t*
// codecs Return: bool
struct btif_storage_get_hearing_aid_prop {
  static bool return_value;
  std::function<bool(const RawAddress& address, uint8_t* capabilities,
                     uint64_t* hi_sync_id, uint16_t* render_delay,
                     uint16_t* preparation_delay, uint16_t* codecs)>
      body{[](const RawAddress& /* address */, uint8_t* /* capabilities */,
              uint64_t* /* hi_sync_id */, uint16_t* /* render_delay */,
              uint16_t* /* preparation_delay */,
              uint16_t* /* codecs */) { return return_value; }};
  bool operator()(const RawAddress& address, uint8_t* capabilities,
                  uint64_t* hi_sync_id, uint16_t* render_delay,
                  uint16_t* preparation_delay, uint16_t* codecs) {
    return body(address, capabilities, hi_sync_id, render_delay,
                preparation_delay, codecs);
  };
};
extern struct btif_storage_get_hearing_aid_prop
    btif_storage_get_hearing_aid_prop;

// Name: btif_storage_get_le_hid_devices
// Params: void
// Return: std::vector<std::pair<RawAddress, uint8_t>>
struct btif_storage_get_le_hid_devices {
  static std::vector<std::pair<RawAddress, uint8_t>> return_value;
  std::function<std::vector<std::pair<RawAddress, uint8_t>>(void)> body{
      [](void) { return return_value; }};
  std::vector<std::pair<RawAddress, uint8_t>> operator()(void) {
    return body();
  };
};
extern struct btif_storage_get_le_hid_devices btif_storage_get_le_hid_devices;

// Name: btif_storage_get_leaudio_has_features
// Params: const RawAddress& address, uint8_t& features
// Return: bool
struct btif_storage_get_leaudio_has_features {
  static bool return_value;
  std::function<bool(const RawAddress& address, uint8_t& features)> body{
      [](const RawAddress& /* address */, uint8_t& /* features */) {
        return return_value;
      }};
  bool operator()(const RawAddress& address, uint8_t& features) {
    return body(address, features);
  };
};
extern struct btif_storage_get_leaudio_has_features
    btif_storage_get_leaudio_has_features;

// Name: btif_storage_get_leaudio_has_presets
// Params: const RawAddress& address, std::vector<uint8_t>& presets_bin,
// uint8_t& active_preset Return: bool
struct btif_storage_get_leaudio_has_presets {
  static bool return_value;
  std::function<bool(const RawAddress& address,
                     std::vector<uint8_t>& presets_bin, uint8_t& active_preset)>
      body{[](const RawAddress& /* address */,
              std::vector<uint8_t>& /* presets_bin */,
              uint8_t& /* active_preset */) { return return_value; }};
  bool operator()(const RawAddress& address, std::vector<uint8_t>& presets_bin,
                  uint8_t& active_preset) {
    return body(address, presets_bin, active_preset);
  };
};
extern struct btif_storage_get_leaudio_has_presets
    btif_storage_get_leaudio_has_presets;

// Name: btif_storage_get_wake_capable_classic_hid_devices
// Params: void
// Return: std::vector<RawAddress>
struct btif_storage_get_wake_capable_classic_hid_devices {
  static std::vector<RawAddress> return_value;
  std::function<std::vector<RawAddress>(void)> body{
      [](void) { return return_value; }};
  std::vector<RawAddress> operator()(void) { return body(); };
};
extern struct btif_storage_get_wake_capable_classic_hid_devices
    btif_storage_get_wake_capable_classic_hid_devices;

// Name: btif_storage_is_pce_version_102
// Params: const RawAddress& remote_bd_addr
// Return: bool
struct btif_storage_is_pce_version_102 {
  static bool return_value;
  std::function<bool(const RawAddress& remote_bd_addr)> body{
      [](const RawAddress& /* remote_bd_addr */) { return return_value; }};
  bool operator()(const RawAddress& remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_is_pce_version_102 btif_storage_is_pce_version_102;

// Name: btif_storage_leaudio_clear_service_data
// Params: const RawAddress& address
// Return: void
struct btif_storage_leaudio_clear_service_data {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_leaudio_clear_service_data
    btif_storage_leaudio_clear_service_data;

// Name: btif_storage_leaudio_update_ase_bin
// Params: const RawAddress& addr
// Return: void
struct btif_storage_leaudio_update_ase_bin {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct btif_storage_leaudio_update_ase_bin
    btif_storage_leaudio_update_ase_bin;

// Name: btif_storage_leaudio_update_handles_bin
// Params: const RawAddress& addr
// Return: void
struct btif_storage_leaudio_update_handles_bin {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct btif_storage_leaudio_update_handles_bin
    btif_storage_leaudio_update_handles_bin;

// Name: btif_storage_leaudio_update_pacs_bin
// Params: const RawAddress& addr
// Return: void
struct btif_storage_leaudio_update_pacs_bin {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct btif_storage_leaudio_update_pacs_bin
    btif_storage_leaudio_update_pacs_bin;

// Name: btif_storage_load_bonded_csis_devices
// Params: void
// Return: void
struct btif_storage_load_bonded_csis_devices {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_storage_load_bonded_csis_devices
    btif_storage_load_bonded_csis_devices;

// Name: btif_storage_load_bonded_groups
// Params: void
// Return: void
struct btif_storage_load_bonded_groups {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_storage_load_bonded_groups btif_storage_load_bonded_groups;

// Name: btif_storage_load_bonded_hearing_aids
// Params:
// Return: void
struct btif_storage_load_bonded_hearing_aids {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct btif_storage_load_bonded_hearing_aids
    btif_storage_load_bonded_hearing_aids;

// Name: btif_storage_load_bonded_hid_info
// Params: void
// Return: bt_status_t
struct btif_storage_load_bonded_hid_info {
  static bt_status_t return_value;
  std::function<bt_status_t(void)> body{[](void) { return return_value; }};
  bt_status_t operator()(void) { return body(); };
};
extern struct btif_storage_load_bonded_hid_info
    btif_storage_load_bonded_hid_info;

// Name: btif_storage_load_bonded_leaudio
// Params:
// Return: void
struct btif_storage_load_bonded_leaudio {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct btif_storage_load_bonded_leaudio btif_storage_load_bonded_leaudio;

// Name: btif_storage_load_bonded_leaudio_has_devices
// Params:
// Return: void
struct btif_storage_load_bonded_leaudio_has_devices {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct btif_storage_load_bonded_leaudio_has_devices
    btif_storage_load_bonded_leaudio_has_devices;

// Name: btif_storage_load_bonded_volume_control_devices
// Params: void
// Return: void
struct btif_storage_load_bonded_volume_control_devices {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_storage_load_bonded_volume_control_devices
    btif_storage_load_bonded_volume_control_devices;

// Name: btif_storage_load_hidd
// Params: void
// Return: bt_status_t
struct btif_storage_load_hidd {
  static bt_status_t return_value;
  std::function<bt_status_t(void)> body{[](void) { return return_value; }};
  bt_status_t operator()(void) { return body(); };
};
extern struct btif_storage_load_hidd btif_storage_load_hidd;

// Name: btif_storage_remove_csis_device
// Params: const RawAddress& address
// Return: void
struct btif_storage_remove_csis_device {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_remove_csis_device btif_storage_remove_csis_device;

// Name: btif_storage_remove_groups
// Params: const RawAddress& address
// Return: void
struct btif_storage_remove_groups {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_remove_groups btif_storage_remove_groups;

// Name: btif_storage_remove_hearing_aid
// Params: const RawAddress& address
// Return: void
struct btif_storage_remove_hearing_aid {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_remove_hearing_aid btif_storage_remove_hearing_aid;

// Name: btif_storage_remove_hid_info
// Params: const RawAddress& remote_bd_addr
// Return: bt_status_t
struct btif_storage_remove_hid_info {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress& remote_bd_addr)> body{
      [](const RawAddress& /* remote_bd_addr */) { return return_value; }};
  bt_status_t operator()(const RawAddress& remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_remove_hid_info btif_storage_remove_hid_info;

// Name: btif_storage_remove_hidd
// Params: RawAddress* remote_bd_addr
// Return: bt_status_t
struct btif_storage_remove_hidd {
  static bt_status_t return_value;
  std::function<bt_status_t(RawAddress* remote_bd_addr)> body{
      [](RawAddress* /* remote_bd_addr */) { return return_value; }};
  bt_status_t operator()(RawAddress* remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_remove_hidd btif_storage_remove_hidd;

// Name: btif_storage_remove_leaudio
// Params: const RawAddress& address
// Return: void
struct btif_storage_remove_leaudio {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_remove_leaudio btif_storage_remove_leaudio;

// Name: btif_storage_remove_leaudio_has
// Params: const RawAddress& address
// Return: void
struct btif_storage_remove_leaudio_has {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btif_storage_remove_leaudio_has btif_storage_remove_leaudio_has;

// Name: btif_storage_set_hearing_aid_acceptlist
// Params: const RawAddress& address, bool add_to_acceptlist
// Return: void
struct btif_storage_set_hearing_aid_acceptlist {
  std::function<void(const RawAddress& address, bool add_to_acceptlist)> body{
      [](const RawAddress& /* address */, bool /* add_to_acceptlist */) {}};
  void operator()(const RawAddress& address, bool add_to_acceptlist) {
    body(address, add_to_acceptlist);
  };
};
extern struct btif_storage_set_hearing_aid_acceptlist
    btif_storage_set_hearing_aid_acceptlist;

// Name: btif_storage_set_hidd
// Params: const RawAddress& remote_bd_addr
// Return: bt_status_t
struct btif_storage_set_hidd {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress& remote_bd_addr)> body{
      [](const RawAddress& /* remote_bd_addr */) { return return_value; }};
  bt_status_t operator()(const RawAddress& remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_set_hidd btif_storage_set_hidd;

// Name: btif_storage_set_leaudio_audio_location
// Params: const RawAddress& addr, uint32_t sink_location, uint32_t
// source_location Return: void
struct btif_storage_set_leaudio_audio_location {
  std::function<void(const RawAddress& addr, uint32_t sink_location,
                     uint32_t source_location)>
      body{[](const RawAddress& /* addr */, uint32_t /* sink_location */,
              uint32_t /* source_location */) {}};
  void operator()(const RawAddress& addr, uint32_t sink_location,
                  uint32_t source_location) {
    body(addr, sink_location, source_location);
  };
};
extern struct btif_storage_set_leaudio_audio_location
    btif_storage_set_leaudio_audio_location;

// Name: btif_storage_set_leaudio_autoconnect
// Params: const RawAddress& addr, bool autoconnect
// Return: void
struct btif_storage_set_leaudio_autoconnect {
  std::function<void(const RawAddress& addr, bool autoconnect)> body{
      [](const RawAddress& /* addr */, bool /* autoconnect */) {}};
  void operator()(const RawAddress& addr, bool autoconnect) {
    body(addr, autoconnect);
  };
};
extern struct btif_storage_set_leaudio_autoconnect
    btif_storage_set_leaudio_autoconnect;

// Name: btif_storage_set_leaudio_has_acceptlist
// Params: const RawAddress& address, bool add_to_acceptlist
// Return: void
struct btif_storage_set_leaudio_has_acceptlist {
  std::function<void(const RawAddress& address, bool add_to_acceptlist)> body{
      [](const RawAddress& /* address */, bool /* add_to_acceptlist */) {}};
  void operator()(const RawAddress& address, bool add_to_acceptlist) {
    body(address, add_to_acceptlist);
  };
};
extern struct btif_storage_set_leaudio_has_acceptlist
    btif_storage_set_leaudio_has_acceptlist;

// Name: btif_storage_set_leaudio_has_active_preset
// Params: const RawAddress& address, uint8_t active_preset
// Return: void
struct btif_storage_set_leaudio_has_active_preset {
  std::function<void(const RawAddress& address, uint8_t active_preset)> body{
      [](const RawAddress& /* address */, uint8_t /* active_preset */) {}};
  void operator()(const RawAddress& address, uint8_t active_preset) {
    body(address, active_preset);
  };
};
extern struct btif_storage_set_leaudio_has_active_preset
    btif_storage_set_leaudio_has_active_preset;

// Name: btif_storage_set_leaudio_has_features
// Params: const RawAddress& address, uint8_t features
// Return: void
struct btif_storage_set_leaudio_has_features {
  std::function<void(const RawAddress& address, uint8_t features)> body{
      [](const RawAddress& /* address */, uint8_t /* features */) {}};
  void operator()(const RawAddress& address, uint8_t features) {
    body(address, features);
  };
};
extern struct btif_storage_set_leaudio_has_features
    btif_storage_set_leaudio_has_features;

// Name: btif_storage_set_leaudio_has_presets
// Params: const RawAddress& address, std::vector<uint8_t> presets_bin
// Return: void
struct btif_storage_set_leaudio_has_presets {
  std::function<void(const RawAddress& address,
                     std::vector<uint8_t> presets_bin)>
      body{[](const RawAddress& /* address */,
              std::vector<uint8_t> /* presets_bin */) {}};
  void operator()(const RawAddress& address, std::vector<uint8_t> presets_bin) {
    body(address, presets_bin);
  };
};
extern struct btif_storage_set_leaudio_has_presets
    btif_storage_set_leaudio_has_presets;

// Name: btif_storage_set_leaudio_supported_context_types
// Params: const RawAddress& addr, uint16_t sink_supported_context_type,
// uint16_t source_supported_context_type Return: void
struct btif_storage_set_leaudio_supported_context_types {
  std::function<void(const RawAddress& addr,
                     uint16_t sink_supported_context_type,
                     uint16_t source_supported_context_type)>
      body{[](const RawAddress& /* addr */,
              uint16_t /* sink_supported_context_type */,
              uint16_t /* source_supported_context_type */) {}};
  void operator()(const RawAddress& addr, uint16_t sink_supported_context_type,
                  uint16_t source_supported_context_type) {
    body(addr, sink_supported_context_type, source_supported_context_type);
  };
};
extern struct btif_storage_set_leaudio_supported_context_types
    btif_storage_set_leaudio_supported_context_types;

// Name: btif_storage_set_pce_profile_version
// Params: const RawAddress& remote_bd_addr, uint16_t peer_pce_version
// Return: void
struct btif_storage_set_pce_profile_version {
  std::function<void(const RawAddress& remote_bd_addr,
                     uint16_t peer_pce_version)>
      body{[](const RawAddress& /* remote_bd_addr */,
              uint16_t /* peer_pce_version */) {}};
  void operator()(const RawAddress& remote_bd_addr, uint16_t peer_pce_version) {
    body(remote_bd_addr, peer_pce_version);
  };
};
extern struct btif_storage_set_pce_profile_version
    btif_storage_set_pce_profile_version;

// Name: btif_storage_update_csis_info
// Params: const RawAddress& addr
// Return: void
struct btif_storage_update_csis_info {
  std::function<void(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) {}};
  void operator()(const RawAddress& addr) { body(addr); };
};
extern struct btif_storage_update_csis_info btif_storage_update_csis_info;

}  // namespace btif_profile_storage
}  // namespace mock
}  // namespace test

// END mockcify generation
