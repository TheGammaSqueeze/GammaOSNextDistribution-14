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
 *   Functions generated:41
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

#include "btif/include/btif_storage.h"
#include "stack/include/bt_octets.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_storage {

// Shared state between mocked functions and tests
// Name: btif_debug_linkkey_type_dump
// Params: int fd
// Return: void
struct btif_debug_linkkey_type_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct btif_debug_linkkey_type_dump btif_debug_linkkey_type_dump;

// Name: btif_has_ble_keys
// Params: const std::string& bdstr
// Return: bool
struct btif_has_ble_keys {
  static bool return_value;
  std::function<bool(const std::string& bdstr)> body{
      [](const std::string& /* bdstr */) { return return_value; }};
  bool operator()(const std::string& bdstr) { return body(bdstr); };
};
extern struct btif_has_ble_keys btif_has_ble_keys;

// Name: btif_in_fetch_bonded_ble_device
// Params: const std::string& remote_bd_addr, int add, btif_bonded_devices_t*
// p_bonded_devices Return: bt_status_t
struct btif_in_fetch_bonded_ble_device {
  static bt_status_t return_value;
  std::function<bt_status_t(const std::string& remote_bd_addr, int add,
                            btif_bonded_devices_t* p_bonded_devices)>
      body{[](const std::string& /* remote_bd_addr */, int /* add */,
              btif_bonded_devices_t* /* p_bonded_devices */) {
        return return_value;
      }};
  bt_status_t operator()(const std::string& remote_bd_addr, int add,
                         btif_bonded_devices_t* p_bonded_devices) {
    return body(remote_bd_addr, add, p_bonded_devices);
  };
};
extern struct btif_in_fetch_bonded_ble_device btif_in_fetch_bonded_ble_device;

// Name: btif_in_fetch_bonded_device
// Params: const std::string& bdstr
// Return: bt_status_t
struct btif_in_fetch_bonded_device {
  static bt_status_t return_value;
  std::function<bt_status_t(const std::string& bdstr)> body{
      [](const std::string& /* bdstr */) { return return_value; }};
  bt_status_t operator()(const std::string& bdstr) { return body(bdstr); };
};
extern struct btif_in_fetch_bonded_device btif_in_fetch_bonded_device;

// Name: btif_split_uuids_string
// Params: const char* str, bluetooth::Uuid* p_uuid, size_t max_uuids
// Return: size_t
struct btif_split_uuids_string {
  static size_t return_value;
  std::function<size_t(const char* str, bluetooth::Uuid* p_uuid,
                       size_t max_uuids)>
      body{[](const char* /* str */, bluetooth::Uuid* /* p_uuid */,
              size_t /* max_uuids */) { return return_value; }};
  size_t operator()(const char* str, bluetooth::Uuid* p_uuid,
                    size_t max_uuids) {
    return body(str, p_uuid, max_uuids);
  };
};
extern struct btif_split_uuids_string btif_split_uuids_string;

// Name: btif_storage_add_ble_bonding_key
// Params: RawAddress* remote_bd_addr, const uint8_t* key_value, uint8_t
// key_type, uint8_t key_length Return: bt_status_t
struct btif_storage_add_ble_bonding_key {
  static bt_status_t return_value;
  std::function<bt_status_t(RawAddress* remote_bd_addr,
                            const uint8_t* key_value, uint8_t key_type,
                            uint8_t key_length)>
      body{[](RawAddress* /* remote_bd_addr */, const uint8_t* /* key_value */,
              uint8_t /* key_type */,
              uint8_t /* key_length */) { return return_value; }};
  bt_status_t operator()(RawAddress* remote_bd_addr, const uint8_t* key_value,
                         uint8_t key_type, uint8_t key_length) {
    return body(remote_bd_addr, key_value, key_type, key_length);
  };
};
extern struct btif_storage_add_ble_bonding_key btif_storage_add_ble_bonding_key;

// Name: btif_storage_add_ble_local_key
// Params: const Octet16& key_value, uint8_t key_type
// Return: bt_status_t
struct btif_storage_add_ble_local_key {
  static bt_status_t return_value;
  std::function<bt_status_t(const Octet16& key_value, uint8_t key_type)> body{
      [](const Octet16& /* key_value */, uint8_t /* key_type */) {
        return return_value;
      }};
  bt_status_t operator()(const Octet16& key_value, uint8_t key_type) {
    return body(key_value, key_type);
  };
};
extern struct btif_storage_add_ble_local_key btif_storage_add_ble_local_key;

// Name: btif_storage_add_bonded_device
// Params: RawAddress* remote_bd_addr, LinkKey link_key, uint8_t key_type,
// uint8_t pin_length Return: bt_status_t
struct btif_storage_add_bonded_device {
  static bt_status_t return_value;
  std::function<bt_status_t(RawAddress* remote_bd_addr, LinkKey link_key,
                            uint8_t key_type, uint8_t pin_length)>
      body{[](RawAddress* /* remote_bd_addr */, LinkKey /* link_key */,
              uint8_t /* key_type */,
              uint8_t /* pin_length */) { return return_value; }};
  bt_status_t operator()(RawAddress* remote_bd_addr, LinkKey link_key,
                         uint8_t key_type, uint8_t pin_length) {
    return body(remote_bd_addr, link_key, key_type, pin_length);
  };
};
extern struct btif_storage_add_bonded_device btif_storage_add_bonded_device;

// Name: btif_storage_add_remote_device
// Params: const RawAddress* remote_bd_addr, uint32_t num_properties,
// bt_property_t* properties Return: bt_status_t
struct btif_storage_add_remote_device {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr,
                            uint32_t num_properties, bt_property_t* properties)>
      body{[](const RawAddress* /* remote_bd_addr */,
              uint32_t /* num_properties */,
              bt_property_t* /* properties */) { return return_value; }};
  bt_status_t operator()(const RawAddress* remote_bd_addr,
                         uint32_t num_properties, bt_property_t* properties) {
    return body(remote_bd_addr, num_properties, properties);
  };
};
extern struct btif_storage_add_remote_device btif_storage_add_remote_device;

// Name: btif_storage_get_adapter_prop
// Params: bt_property_type_t type, void* buf, int size, bt_property_t* property
// Return: bt_status_t
struct btif_storage_get_adapter_prop {
  static bt_status_t return_value;
  std::function<bt_status_t(bt_property_type_t type, void* buf, int size,
                            bt_property_t* property)>
      body{[](bt_property_type_t /* type */, void* /* buf */, int /* size */,
              bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(bt_property_type_t type, void* buf, int size,
                         bt_property_t* property) {
    return body(type, buf, size, property);
  };
};
extern struct btif_storage_get_adapter_prop btif_storage_get_adapter_prop;

// Name: btif_storage_get_adapter_property
// Params: bt_property_t* property
// Return: bt_status_t
struct btif_storage_get_adapter_property {
  static bt_status_t return_value;
  std::function<bt_status_t(bt_property_t* property)> body{
      [](bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(bt_property_t* property) { return body(property); };
};
extern struct btif_storage_get_adapter_property
    btif_storage_get_adapter_property;

// Name: btif_storage_get_ble_bonding_key
// Params: const RawAddress& remote_bd_addr, uint8_t key_type, uint8_t*
// key_value, int key_length Return: bt_status_t
struct btif_storage_get_ble_bonding_key {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress& remote_bd_addr, uint8_t key_type,
                            uint8_t* key_value, int key_length)>
      body{[](const RawAddress& /* remote_bd_addr */, uint8_t /* key_type */,
              uint8_t* /* key_value */,
              int /* key_length */) { return return_value; }};
  bt_status_t operator()(const RawAddress& remote_bd_addr, uint8_t key_type,
                         uint8_t* key_value, int key_length) {
    return body(remote_bd_addr, key_type, key_value, key_length);
  };
};
extern struct btif_storage_get_ble_bonding_key btif_storage_get_ble_bonding_key;

// Name: btif_storage_get_ble_local_key
// Params: uint8_t key_type, Octet16* key_value
// Return: bt_status_t
struct btif_storage_get_ble_local_key {
  static bt_status_t return_value;
  std::function<bt_status_t(uint8_t key_type, Octet16* key_value)> body{
      [](uint8_t /* key_type */, Octet16* /* key_value */) {
        return return_value;
      }};
  bt_status_t operator()(uint8_t key_type, Octet16* key_value) {
    return body(key_type, key_value);
  };
};
extern struct btif_storage_get_ble_local_key btif_storage_get_ble_local_key;

// Name: btif_storage_get_gatt_cl_db_hash
// Params: const RawAddress& bd_addr
// Return: Octet16
struct btif_storage_get_gatt_cl_db_hash {
  static Octet16 return_value;
  std::function<Octet16(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  Octet16 operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct btif_storage_get_gatt_cl_db_hash btif_storage_get_gatt_cl_db_hash;

// Name: btif_storage_get_gatt_cl_supp_feat
// Params: const RawAddress& bd_addr
// Return: uint8_t
struct btif_storage_get_gatt_cl_supp_feat {
  static uint8_t return_value;
  std::function<uint8_t(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  uint8_t operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct btif_storage_get_gatt_cl_supp_feat
    btif_storage_get_gatt_cl_supp_feat;

// Name: btif_storage_get_local_io_caps
// Params:
// Return: tBTM_IO_CAP
struct btif_storage_get_local_io_caps {
  static tBTM_IO_CAP return_value;
  std::function<tBTM_IO_CAP()> body{[]() { return return_value; }};
  tBTM_IO_CAP operator()() { return body(); };
};
extern struct btif_storage_get_local_io_caps btif_storage_get_local_io_caps;

// Name: btif_storage_get_num_bonded_devices
// Params: void
// Return: int
struct btif_storage_get_num_bonded_devices {
  static int return_value;
  std::function<int(void)> body{[](void) { return return_value; }};
  int operator()(void) { return body(); };
};
extern struct btif_storage_get_num_bonded_devices
    btif_storage_get_num_bonded_devices;

// Name: btif_storage_get_remote_addr_type
// Params: const RawAddress* remote_bd_addr, tBLE_ADDR_TYPE addr_type
// Return: bt_status_t
struct btif_storage_get_remote_addr_type {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr,
                            tBLE_ADDR_TYPE* addr_type)>
      body{[](const RawAddress* /* remote_bd_addr */,
              tBLE_ADDR_TYPE* /* addr_type */) -> bt_status_t {
        return return_value;
      }};
  bt_status_t operator()(const RawAddress* remote_bd_addr,
                         tBLE_ADDR_TYPE* addr_type) {
    return body(remote_bd_addr, addr_type);
  };
};
extern struct btif_storage_get_remote_addr_type
    btif_storage_get_remote_addr_type;

// Name: btif_storage_get_remote_addr_type2
// Params: const RawAddress& remote_bd_addr, tBLE_ADDR_TYPE& addr_type
// Return: bool
struct btif_storage_get_remote_addr_type2 {
  static bool return_value;
  std::function<bool(const RawAddress& remote_bd_addr,
                     tBLE_ADDR_TYPE& addr_type)>
      body{[](const RawAddress& /* remote_bd_addr */,
              tBLE_ADDR_TYPE& /* addr_type */) { return return_value; }};
  bool operator()(const RawAddress& remote_bd_addr, tBLE_ADDR_TYPE& addr_type) {
    return body(remote_bd_addr, addr_type);
  };
};
extern struct btif_storage_get_remote_addr_type2
    btif_storage_get_remote_addr_type2;

// Name: btif_storage_get_remote_device_property
// Params: const RawAddress* remote_bd_addr, bt_property_t* property
// Return: bt_status_t
struct btif_storage_get_remote_device_property {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr,
                            bt_property_t* property)>
      body{[](const RawAddress* /* remote_bd_addr */,
              bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(const RawAddress* remote_bd_addr,
                         bt_property_t* property) {
    return body(remote_bd_addr, property);
  };
};
extern struct btif_storage_get_remote_device_property
    btif_storage_get_remote_device_property;

// Name: btif_storage_get_remote_device_type
// Params: const RawAddress& remote_bd_addr, tBT_DEVICE_TYPE& device_type
// Return: bool
struct btif_storage_get_remote_device_type {
  static bool return_value;
  std::function<bool(const RawAddress& remote_bd_addr,
                     tBT_DEVICE_TYPE& device_type)>
      body{[](const RawAddress& /* remote_bd_addr */,
              tBT_DEVICE_TYPE& /* device_type */) { return return_value; }};
  bool operator()(const RawAddress& remote_bd_addr,
                  tBT_DEVICE_TYPE& device_type) {
    return body(remote_bd_addr, device_type);
  };
};
extern struct btif_storage_get_remote_device_type
    btif_storage_get_remote_device_type;

// Name: btif_storage_get_remote_prop
// Params: RawAddress* remote_addr, bt_property_type_t type, void* buf, int
// size, bt_property_t* property Return: bt_status_t
struct btif_storage_get_remote_prop {
  static bt_status_t return_value;
  std::function<bt_status_t(RawAddress* remote_addr, bt_property_type_t type,
                            void* buf, int size, bt_property_t* property)>
      body{[](RawAddress* /* remote_addr */, bt_property_type_t /* type */,
              void* /* buf */, int /* size */,
              bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(RawAddress* remote_addr, bt_property_type_t type,
                         void* buf, int size, bt_property_t* property) {
    return body(remote_addr, type, buf, size, property);
  };
};
extern struct btif_storage_get_remote_prop btif_storage_get_remote_prop;

// Name: btif_storage_get_sr_supp_feat
// Params: const RawAddress& bd_addr
// Return: uint8_t
struct btif_storage_get_sr_supp_feat {
  static uint8_t return_value;
  std::function<uint8_t(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return return_value; }};
  uint8_t operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct btif_storage_get_sr_supp_feat btif_storage_get_sr_supp_feat;

// Name: btif_storage_get_stored_remote_name
// Params: const RawAddress& bd_addr, char* name
// Return: bool
struct btif_storage_get_stored_remote_name {
  static bool return_value;
  std::function<bool(const RawAddress& bd_addr, char* name)> body{
      [](const RawAddress& /* bd_addr */, char* /* name */) {
        return return_value;
      }};
  bool operator()(const RawAddress& bd_addr, char* name) {
    return body(bd_addr, name);
  };
};
extern struct btif_storage_get_stored_remote_name
    btif_storage_get_stored_remote_name;

// Name: btif_storage_invoke_addr_type_update
// Params: const RawAddress& remote_bd_addr, const tBLE_ADDR_TYPE& addr_type
// Return: void
struct btif_storage_invoke_addr_type_update {
  std::function<void(const RawAddress& remote_bd_addr,
                     const tBLE_ADDR_TYPE& addr_type)>
      body{[](const RawAddress& /* remote_bd_addr */,
              const tBLE_ADDR_TYPE& /* addr_type */) {}};
  void operator()(const RawAddress& remote_bd_addr,
                  const tBLE_ADDR_TYPE& addr_type) {
    body(remote_bd_addr, addr_type);
  };
};
extern struct btif_storage_invoke_addr_type_update
    btif_storage_invoke_addr_type_update;

// Name: btif_storage_is_restricted_device
// Params: const RawAddress* remote_bd_addr
// Return: bool
struct btif_storage_is_restricted_device {
  static bool return_value;
  std::function<bool(const RawAddress* remote_bd_addr)> body{
      [](const RawAddress* /* remote_bd_addr */) { return return_value; }};
  bool operator()(const RawAddress* remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_is_restricted_device
    btif_storage_is_restricted_device;

// Name: btif_storage_load_bonded_devices
// Params: void
// Return: bt_status_t
struct btif_storage_load_bonded_devices {
  static bt_status_t return_value;
  std::function<bt_status_t(void)> body{[](void) { return return_value; }};
  bt_status_t operator()(void) { return body(); };
};
extern struct btif_storage_load_bonded_devices btif_storage_load_bonded_devices;

// Name: btif_storage_load_le_devices
// Params: void
// Return: void
struct btif_storage_load_le_devices {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_storage_load_le_devices btif_storage_load_le_devices;

// Name: btif_storage_remove_ble_bonding_keys
// Params: const RawAddress* remote_bd_addr
// Return: bt_status_t
struct btif_storage_remove_ble_bonding_keys {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr)> body{
      [](const RawAddress* /* remote_bd_addr */) { return return_value; }};
  bt_status_t operator()(const RawAddress* remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_remove_ble_bonding_keys
    btif_storage_remove_ble_bonding_keys;

// Name: btif_storage_remove_ble_local_keys
// Params: void
// Return: bt_status_t
struct btif_storage_remove_ble_local_keys {
  static bt_status_t return_value;
  std::function<bt_status_t(void)> body{[](void) { return return_value; }};
  bt_status_t operator()(void) { return body(); };
};
extern struct btif_storage_remove_ble_local_keys
    btif_storage_remove_ble_local_keys;

// Name: btif_storage_remove_bonded_device
// Params: const RawAddress* remote_bd_addr
// Return: bt_status_t
struct btif_storage_remove_bonded_device {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr)> body{
      [](const RawAddress* /* remote_bd_addr */) { return return_value; }};
  bt_status_t operator()(const RawAddress* remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct btif_storage_remove_bonded_device
    btif_storage_remove_bonded_device;

// Name: btif_storage_remove_gatt_cl_db_hash
// Params: const RawAddress& bd_addr
// Return: void
struct btif_storage_remove_gatt_cl_db_hash {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btif_storage_remove_gatt_cl_db_hash
    btif_storage_remove_gatt_cl_db_hash;

// Name: btif_storage_remove_gatt_cl_supp_feat
// Params: const RawAddress& bd_addr
// Return: void
struct btif_storage_remove_gatt_cl_supp_feat {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btif_storage_remove_gatt_cl_supp_feat
    btif_storage_remove_gatt_cl_supp_feat;

// Name: btif_storage_set_adapter_property
// Params: bt_property_t* property
// Return: bt_status_t
struct btif_storage_set_adapter_property {
  static bt_status_t return_value;
  std::function<bt_status_t(bt_property_t* property)> body{
      [](bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(bt_property_t* property) { return body(property); };
};
extern struct btif_storage_set_adapter_property
    btif_storage_set_adapter_property;

// Name: btif_storage_set_gatt_cl_db_hash
// Params: const RawAddress& bd_addr, Octet16 hash
// Return: void
struct btif_storage_set_gatt_cl_db_hash {
  std::function<void(const RawAddress& bd_addr, Octet16 hash)> body{
      [](const RawAddress& /* bd_addr */, Octet16 /* hash */) {}};
  void operator()(const RawAddress& bd_addr, Octet16 hash) {
    body(bd_addr, hash);
  };
};
extern struct btif_storage_set_gatt_cl_db_hash btif_storage_set_gatt_cl_db_hash;

// Name: btif_storage_set_gatt_cl_supp_feat
// Params: const RawAddress& bd_addr, uint8_t feat
// Return: void
struct btif_storage_set_gatt_cl_supp_feat {
  std::function<void(const RawAddress& bd_addr, uint8_t feat)> body{
      [](const RawAddress& /* bd_addr */, uint8_t /* feat */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t feat) {
    body(bd_addr, feat);
  };
};
extern struct btif_storage_set_gatt_cl_supp_feat
    btif_storage_set_gatt_cl_supp_feat;

// Name: btif_storage_set_gatt_sr_supp_feat
// Params: const RawAddress& addr, uint8_t feat
// Return: void
struct btif_storage_set_gatt_sr_supp_feat {
  std::function<void(const RawAddress& addr, uint8_t feat)> body{
      [](const RawAddress& /* addr */, uint8_t /* feat */) {}};
  void operator()(const RawAddress& addr, uint8_t feat) { body(addr, feat); };
};
extern struct btif_storage_set_gatt_sr_supp_feat
    btif_storage_set_gatt_sr_supp_feat;

// Name: btif_storage_set_remote_addr_type
// Params: const RawAddress& remote_bd_addr, const tBLE_ADDR_TYPE& addr_type
// Return: void
struct btif_storage_set_remote_addr_type {
  std::function<bt_status_t(const RawAddress* remote_bd_addr,
                            const tBLE_ADDR_TYPE addr_type)>
      body{[](const RawAddress* /* remote_bd_addr */,
              const tBLE_ADDR_TYPE /* addr_type */) {
        return BT_STATUS_SUCCESS;
      }};
  bt_status_t operator()(const RawAddress* remote_bd_addr,
                         const tBLE_ADDR_TYPE addr_type) {
    return body(remote_bd_addr, addr_type);
  };
};
extern struct btif_storage_set_remote_addr_type
    btif_storage_set_remote_addr_type;

// Name: btif_storage_set_remote_addr_type
// Params: const RawAddress& remote_bd_addr, const tBLE_ADDR_TYPE& addr_type
// Return: void
struct btif_storage_set_remote_addr_type2 {
  std::function<void(const RawAddress& remote_bd_addr,
                     const tBLE_ADDR_TYPE& addr_type)>
      body{[](const RawAddress& /* remote_bd_addr */,
              const tBLE_ADDR_TYPE& /* addr_type */) {}};
  void operator()(const RawAddress& remote_bd_addr,
                  const tBLE_ADDR_TYPE& addr_type) {
    body(remote_bd_addr, addr_type);
  };
};
extern struct btif_storage_set_remote_addr_type2
    btif_storage_set_remote_addr_type2;

// Name: btif_storage_set_remote_device_property
// Params: const RawAddress* remote_bd_addr, bt_property_t* property
// Return: bt_status_t
struct btif_storage_set_remote_device_property {
  static bt_status_t return_value;
  std::function<bt_status_t(const RawAddress* remote_bd_addr,
                            bt_property_t* property)>
      body{[](const RawAddress* /* remote_bd_addr */,
              bt_property_t* /* property */) { return return_value; }};
  bt_status_t operator()(const RawAddress* remote_bd_addr,
                         bt_property_t* property) {
    return body(remote_bd_addr, property);
  };
};
extern struct btif_storage_set_remote_device_property
    btif_storage_set_remote_device_property;

// Name: btif_storage_set_remote_device_type
// Params: const RawAddress& remote_bd_addr, const tBT_DEVICE_TYPE& device_type
// Return: void
struct btif_storage_set_remote_device_type {
  std::function<void(const RawAddress& remote_bd_addr,
                     const tBT_DEVICE_TYPE& device_type)>
      body{[](const RawAddress& /* remote_bd_addr */,
              const tBT_DEVICE_TYPE& /* device_type */) {}};
  void operator()(const RawAddress& remote_bd_addr,
                  const tBT_DEVICE_TYPE& device_type) {
    body(remote_bd_addr, device_type);
  };
};
extern struct btif_storage_set_remote_device_type
    btif_storage_set_remote_device_type;

}  // namespace btif_storage
}  // namespace mock
}  // namespace test

// END mockcify generation
