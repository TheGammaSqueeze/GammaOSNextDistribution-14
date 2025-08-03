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
 *   Functions generated:41
 *
 *  mockcify.pl ver 0.7.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_storage.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings
using bluetooth::Uuid;

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_storage {

// Function state capture and return values, if needed
struct btif_debug_linkkey_type_dump btif_debug_linkkey_type_dump;
struct btif_has_ble_keys btif_has_ble_keys;
struct btif_in_fetch_bonded_ble_device btif_in_fetch_bonded_ble_device;
struct btif_in_fetch_bonded_device btif_in_fetch_bonded_device;
struct btif_split_uuids_string btif_split_uuids_string;
struct btif_storage_add_ble_bonding_key btif_storage_add_ble_bonding_key;
struct btif_storage_add_ble_local_key btif_storage_add_ble_local_key;
struct btif_storage_add_bonded_device btif_storage_add_bonded_device;
struct btif_storage_add_remote_device btif_storage_add_remote_device;
struct btif_storage_get_adapter_prop btif_storage_get_adapter_prop;
struct btif_storage_get_adapter_property btif_storage_get_adapter_property;
struct btif_storage_get_ble_bonding_key btif_storage_get_ble_bonding_key;
struct btif_storage_get_ble_local_key btif_storage_get_ble_local_key;
struct btif_storage_get_gatt_cl_db_hash btif_storage_get_gatt_cl_db_hash;
struct btif_storage_get_gatt_cl_supp_feat btif_storage_get_gatt_cl_supp_feat;
struct btif_storage_get_local_io_caps btif_storage_get_local_io_caps;
struct btif_storage_get_num_bonded_devices btif_storage_get_num_bonded_devices;
struct btif_storage_get_remote_addr_type btif_storage_get_remote_addr_type;
struct btif_storage_get_remote_addr_type2 btif_storage_get_remote_addr_type2;
struct btif_storage_get_remote_device_property
    btif_storage_get_remote_device_property;
struct btif_storage_get_remote_device_type btif_storage_get_remote_device_type;
struct btif_storage_get_remote_prop btif_storage_get_remote_prop;
struct btif_storage_get_sr_supp_feat btif_storage_get_sr_supp_feat;
struct btif_storage_get_stored_remote_name btif_storage_get_stored_remote_name;
struct btif_storage_invoke_addr_type_update
    btif_storage_invoke_addr_type_update;
struct btif_storage_is_restricted_device btif_storage_is_restricted_device;
struct btif_storage_load_bonded_devices btif_storage_load_bonded_devices;
struct btif_storage_load_le_devices btif_storage_load_le_devices;
struct btif_storage_remove_ble_bonding_keys
    btif_storage_remove_ble_bonding_keys;
struct btif_storage_remove_ble_local_keys btif_storage_remove_ble_local_keys;
struct btif_storage_remove_bonded_device btif_storage_remove_bonded_device;
struct btif_storage_remove_gatt_cl_db_hash btif_storage_remove_gatt_cl_db_hash;
struct btif_storage_remove_gatt_cl_supp_feat
    btif_storage_remove_gatt_cl_supp_feat;
struct btif_storage_set_adapter_property btif_storage_set_adapter_property;
struct btif_storage_set_gatt_cl_db_hash btif_storage_set_gatt_cl_db_hash;
struct btif_storage_set_gatt_cl_supp_feat btif_storage_set_gatt_cl_supp_feat;
struct btif_storage_set_gatt_sr_supp_feat btif_storage_set_gatt_sr_supp_feat;
struct btif_storage_set_remote_addr_type btif_storage_set_remote_addr_type;
struct btif_storage_set_remote_addr_type2 btif_storage_set_remote_addr_type2;
struct btif_storage_set_remote_device_property
    btif_storage_set_remote_device_property;
struct btif_storage_set_remote_device_type btif_storage_set_remote_device_type;

}  // namespace btif_storage
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_storage {

bool btif_has_ble_keys::return_value = false;
bt_status_t btif_in_fetch_bonded_ble_device::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_in_fetch_bonded_device::return_value = BT_STATUS_SUCCESS;
size_t btif_split_uuids_string::return_value = 0;
bt_status_t btif_storage_add_ble_bonding_key::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_add_ble_local_key::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_add_bonded_device::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_add_remote_device::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_get_adapter_prop::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_get_adapter_property::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_get_ble_bonding_key::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_get_ble_local_key::return_value = BT_STATUS_SUCCESS;
Octet16 btif_storage_get_gatt_cl_db_hash::return_value = {};
uint8_t btif_storage_get_gatt_cl_supp_feat::return_value = 0;
tBTM_IO_CAP btif_storage_get_local_io_caps::return_value = 0;
int btif_storage_get_num_bonded_devices::return_value = 0;
bt_status_t btif_storage_get_remote_addr_type::return_value = BT_STATUS_SUCCESS;
bool btif_storage_get_remote_addr_type2::return_value = false;
bt_status_t btif_storage_get_remote_device_property::return_value =
    BT_STATUS_SUCCESS;
bool btif_storage_get_remote_device_type::return_value = false;
bt_status_t btif_storage_get_remote_prop::return_value = BT_STATUS_SUCCESS;
uint8_t btif_storage_get_sr_supp_feat::return_value = 0;
bool btif_storage_get_stored_remote_name::return_value = false;
bool btif_storage_is_restricted_device::return_value = false;
bt_status_t btif_storage_load_bonded_devices::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_remove_ble_bonding_keys::return_value =
    BT_STATUS_SUCCESS;
bt_status_t btif_storage_remove_ble_local_keys::return_value =
    BT_STATUS_SUCCESS;
bt_status_t btif_storage_remove_bonded_device::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_set_adapter_property::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_storage_set_remote_device_property::return_value =
    BT_STATUS_SUCCESS;

}  // namespace btif_storage
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btif_debug_linkkey_type_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_debug_linkkey_type_dump(fd);
}
bool btif_has_ble_keys(const std::string& bdstr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_has_ble_keys(bdstr);
}
bt_status_t btif_in_fetch_bonded_ble_device(
    const std::string& remote_bd_addr, int add,
    btif_bonded_devices_t* p_bonded_devices) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_in_fetch_bonded_ble_device(
      remote_bd_addr, add, p_bonded_devices);
}
bt_status_t btif_in_fetch_bonded_device(const std::string& bdstr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_in_fetch_bonded_device(bdstr);
}
size_t btif_split_uuids_string(const char* str, bluetooth::Uuid* p_uuid,
                               size_t max_uuids) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_split_uuids_string(str, p_uuid,
                                                           max_uuids);
}
bt_status_t btif_storage_add_ble_bonding_key(RawAddress* remote_bd_addr,
                                             const uint8_t* key_value,
                                             uint8_t key_type,
                                             uint8_t key_length) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_add_ble_bonding_key(
      remote_bd_addr, key_value, key_type, key_length);
}
bt_status_t btif_storage_add_ble_local_key(const Octet16& key_value,
                                           uint8_t key_type) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_add_ble_local_key(key_value,
                                                                  key_type);
}
bt_status_t btif_storage_add_bonded_device(RawAddress* remote_bd_addr,
                                           LinkKey link_key, uint8_t key_type,
                                           uint8_t pin_length) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_add_bonded_device(
      remote_bd_addr, link_key, key_type, pin_length);
}
bt_status_t btif_storage_add_remote_device(const RawAddress* remote_bd_addr,
                                           uint32_t num_properties,
                                           bt_property_t* properties) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_add_remote_device(
      remote_bd_addr, num_properties, properties);
}
bt_status_t btif_storage_get_adapter_prop(bt_property_type_t type, void* buf,
                                          int size, bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_adapter_prop(
      type, buf, size, property);
}
bt_status_t btif_storage_get_adapter_property(bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_adapter_property(property);
}
bt_status_t btif_storage_get_ble_bonding_key(const RawAddress& remote_bd_addr,
                                             uint8_t key_type,
                                             uint8_t* key_value,
                                             int key_length) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_ble_bonding_key(
      remote_bd_addr, key_type, key_value, key_length);
}
bt_status_t btif_storage_get_ble_local_key(uint8_t key_type,
                                           Octet16* key_value) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_ble_local_key(key_type,
                                                                  key_value);
}
Octet16 btif_storage_get_gatt_cl_db_hash(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_gatt_cl_db_hash(bd_addr);
}
uint8_t btif_storage_get_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_gatt_cl_supp_feat(bd_addr);
}
tBTM_IO_CAP btif_storage_get_local_io_caps() {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_local_io_caps();
}
int btif_storage_get_num_bonded_devices(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_num_bonded_devices();
}
bt_status_t btif_storage_get_remote_addr_type(const RawAddress* remote_bd_addr,
                                              tBLE_ADDR_TYPE* addr_type) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_remote_addr_type(
      remote_bd_addr, addr_type);
}
bool btif_storage_get_remote_addr_type(const RawAddress& remote_bd_addr,
                                       tBLE_ADDR_TYPE& addr_type) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_remote_addr_type2(
      remote_bd_addr, addr_type);
}
bt_status_t btif_storage_get_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_remote_device_property(
      remote_bd_addr, property);
}
bool btif_storage_get_remote_device_type(const RawAddress& remote_bd_addr,
                                         tBT_DEVICE_TYPE& device_type) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_remote_device_type(
      remote_bd_addr, device_type);
}
bt_status_t btif_storage_get_remote_prop(RawAddress* remote_addr,
                                         bt_property_type_t type, void* buf,
                                         int size, bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_remote_prop(
      remote_addr, type, buf, size, property);
}
uint8_t btif_storage_get_sr_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_sr_supp_feat(bd_addr);
}
bool btif_storage_get_stored_remote_name(const RawAddress& bd_addr,
                                         char* name) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_get_stored_remote_name(bd_addr,
                                                                       name);
}
void btif_storage_invoke_addr_type_update(const RawAddress& remote_bd_addr,
                                          const tBLE_ADDR_TYPE& addr_type) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_invoke_addr_type_update(remote_bd_addr,
                                                                 addr_type);
}
bool btif_storage_is_restricted_device(const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_is_restricted_device(
      remote_bd_addr);
}
bt_status_t btif_storage_load_bonded_devices(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_load_bonded_devices();
}
void btif_storage_load_le_devices(void) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_load_le_devices();
}
bt_status_t btif_storage_remove_ble_bonding_keys(
    const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_remove_ble_bonding_keys(
      remote_bd_addr);
}
bt_status_t btif_storage_remove_ble_local_keys(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_remove_ble_local_keys();
}
bt_status_t btif_storage_remove_bonded_device(
    const RawAddress* remote_bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_remove_bonded_device(
      remote_bd_addr);
}
void btif_storage_remove_gatt_cl_db_hash(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_remove_gatt_cl_db_hash(bd_addr);
}
void btif_storage_remove_gatt_cl_supp_feat(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_remove_gatt_cl_supp_feat(bd_addr);
}
bt_status_t btif_storage_set_adapter_property(bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_set_adapter_property(property);
}
void btif_storage_set_gatt_cl_db_hash(const RawAddress& bd_addr, Octet16 hash) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_set_gatt_cl_db_hash(bd_addr, hash);
}
void btif_storage_set_gatt_cl_supp_feat(const RawAddress& bd_addr,
                                        uint8_t feat) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_set_gatt_cl_supp_feat(bd_addr, feat);
}
void btif_storage_set_gatt_sr_supp_feat(const RawAddress& addr, uint8_t feat) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_set_gatt_sr_supp_feat(addr, feat);
}
bt_status_t btif_storage_set_remote_addr_type(const RawAddress* remote_bd_addr,
                                              const tBLE_ADDR_TYPE addr_type) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_set_remote_addr_type(
      remote_bd_addr, addr_type);
}
void btif_storage_set_remote_addr_type(const RawAddress& remote_bd_addr,
                                       const tBLE_ADDR_TYPE& addr_type) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_set_remote_addr_type2(remote_bd_addr,
                                                               addr_type);
}
bt_status_t btif_storage_set_remote_device_property(
    const RawAddress* remote_bd_addr, bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_storage::btif_storage_set_remote_device_property(
      remote_bd_addr, property);
}
void btif_storage_set_remote_device_type(const RawAddress& remote_bd_addr,
                                         const tBT_DEVICE_TYPE& device_type) {
  inc_func_call_count(__func__);
  test::mock::btif_storage::btif_storage_set_remote_device_type(remote_bd_addr,
                                                                device_type);
}
// Mocked functions complete
// END mockcify generation
