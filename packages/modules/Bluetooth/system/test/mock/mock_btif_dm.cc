/*
 * Copyright 2021 The Android Open Source Project
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
 *   Functions generated:51
 */

#include <cstdint>

#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "include/hardware/bluetooth.h"
#include "internal_include/bte_appl.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/bt_dev_class.h"
#include "test/common/mock_functions.h"
#include "types/bt_transport.h"
#include "types/raw_address.h"

struct uid_set_t;

bool btif_dm_pairing_is_busy() {
  inc_func_call_count(__func__);
  return false;
}
bool check_cod(const RawAddress* /* remote_bdaddr */, uint32_t /* cod */) {
  inc_func_call_count(__func__);
  return false;
}
bool check_cod_hid(const RawAddress* /* remote_bdaddr */) {
  inc_func_call_count(__func__);
  return false;
}
bool check_cod_hid(const RawAddress& /* remote_bdaddr */) {
  inc_func_call_count(__func__);
  return false;
}
bool is_device_le_audio_capable(const RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
  return false;
}
uint16_t btif_dm_get_connection_state(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return 0;
}
void BTIF_dm_disable() { inc_func_call_count(__func__); }
void BTIF_dm_enable() { inc_func_call_count(__func__); }
void BTIF_dm_report_inquiry_status_change(tBTM_INQUIRY_STATE /* state */) {
  inc_func_call_count(__func__);
}
void btif_dm_sec_evt(tBTA_DM_SEC_EVT /* event */, tBTA_DM_SEC* /* p_data */) {
  inc_func_call_count(__func__);
}
void btif_ble_receiver_test(uint8_t /* rx_freq */) {
  inc_func_call_count(__func__);
}
void btif_ble_test_end() { inc_func_call_count(__func__); }
void btif_ble_transmitter_test(uint8_t /* tx_freq */,
                               uint8_t /* test_data_len */,
                               uint8_t /* packet_payload */) {
  inc_func_call_count(__func__);
}
void btif_debug_bond_event_dump(int /* fd */) { inc_func_call_count(__func__); }
void btif_dm_ble_sec_req_evt(tBTA_DM_BLE_SEC_REQ* /* p_ble_req */,
                             bool /* is_consent */) {
  inc_func_call_count(__func__);
}
void btif_dm_cancel_bond(const RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
}
void btif_dm_cancel_discovery(void) { inc_func_call_count(__func__); }
void btif_dm_cleanup(void) { inc_func_call_count(__func__); }
void btif_dm_create_bond(const RawAddress /* bd_addr */, int /* transport */) {
  inc_func_call_count(__func__);
}
void btif_dm_create_bond_le(const RawAddress /* bd_addr */,
                            tBLE_ADDR_TYPE /* addr_type */) {
  inc_func_call_count(__func__);
}
void btif_dm_create_bond_out_of_band(const RawAddress /* bd_addr */,
                                     int /* transport */,
                                     const bt_oob_data_t /* p192_data */,
                                     const bt_oob_data_t /* p256_data */) {
  inc_func_call_count(__func__);
}
void btif_dm_enable_service(tBTA_SERVICE_ID /* service_id */,
                            bool /* enable */) {
  inc_func_call_count(__func__);
}
void btif_dm_get_ble_local_keys(tBTA_DM_BLE_LOCAL_KEY_MASK* /* p_key_mask */,
                                Octet16* /* p_er */,
                                tBTA_BLE_LOCAL_ID_KEYS* /* p_id_keys */) {
  inc_func_call_count(__func__);
}
void btif_dm_get_remote_services(RawAddress /* remote_addr */,
                                 const int /* transport */) {
  inc_func_call_count(__func__);
}
void btif_dm_hh_open_failed(RawAddress* /* bdaddr */) {
  inc_func_call_count(__func__);
}
void btif_dm_init(uid_set_t* /* set */) { inc_func_call_count(__func__); }
DEV_CLASS btif_dm_get_local_class_of_device() {
  inc_func_call_count(__func__);
  return kDevClassUnclassified;
}
void btif_dm_load_ble_local_keys(void) { inc_func_call_count(__func__); }
void btif_dm_on_disable() { inc_func_call_count(__func__); }
void btif_dm_pin_reply(const RawAddress /* bd_addr */, uint8_t /* accept */,
                       uint8_t /* pin_len */, bt_pin_code_t /* pin_code */) {
  inc_func_call_count(__func__);
}
void btif_dm_proc_io_req(tBTM_AUTH_REQ* /* p_auth_req */, bool /* is_orig */) {
  inc_func_call_count(__func__);
}
void btif_dm_proc_io_rsp(const RawAddress& /* bd_addr */,
                         tBTM_IO_CAP /* io_cap */, tBTM_OOB_DATA /* oob_data */,
                         tBTM_AUTH_REQ /* auth_req */) {
  inc_func_call_count(__func__);
}
void btif_dm_read_energy_info() { inc_func_call_count(__func__); }
void btif_dm_remove_ble_bonding_keys(void) { inc_func_call_count(__func__); }
void btif_dm_remove_bond(const RawAddress /* bd_addr */) {
  inc_func_call_count(__func__);
}
void btif_dm_set_oob_for_io_req(tBTM_OOB_DATA* /* p_has_oob_data */) {
  inc_func_call_count(__func__);
}
void btif_dm_set_oob_for_le_io_req(const RawAddress& /* bd_addr */,
                                   tBTM_OOB_DATA* /* p_has_oob_data */,
                                   tBTM_LE_AUTH_REQ* /* p_auth_req */) {
  inc_func_call_count(__func__);
}
void btif_dm_ssp_reply(const RawAddress /* bd_addr */,
                       bt_ssp_variant_t /* variant */, uint8_t /* accept */) {
  inc_func_call_count(__func__);
}
void btif_dm_start_discovery(void) { inc_func_call_count(__func__); }
void btif_dm_update_ble_remote_properties(const RawAddress& /* bd_addr */,
                                          BD_NAME /* bd_name */,
                                          DEV_CLASS /* dev_class */,
                                          tBT_DEVICE_TYPE /* dev_type */) {
  inc_func_call_count(__func__);
}

bool btif_dm_get_smp_config(tBTE_APPL_CFG* /* p_cfg */) {
  inc_func_call_count(__func__);
  return true;
}

bool btif_dm_proc_rmt_oob(const RawAddress& /* bd_addr */, Octet16* /* p_c */,
                          Octet16* /* p_r */) {
  inc_func_call_count(__func__);
  return false;
}

void btif_dm_proc_loc_oob(tBT_TRANSPORT /* transport */, bool /* is_valid */,
                          const Octet16& /* c */, const Octet16& /* r */) {
  inc_func_call_count(__func__);
}
bool btif_get_device_type(const RawAddress& /* bda */,
                          int* /* p_device_type */) {
  inc_func_call_count(__func__);
  return false;
}
bool btif_get_address_type(const RawAddress& /* bda */,
                           tBLE_ADDR_TYPE* /* p_addr_type */) {
  inc_func_call_count(__func__);
  return false;
}
