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

#include <memory>

#include "bta/include/bta_api.h"
#include "bta/include/bta_sec_api.h"
#include "osi/include/osi.h"  // UNUSED_ATTR

typedef struct {
  RawAddress bd_addr;
  bool accept;
  uint8_t pin_len;
  uint8_t p_pin[PIN_CODE_LEN];
} tBTA_DM_API_PIN_REPLY;

typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress bd_addr;
  tBTM_IO_CAP io_cap;
  tBTM_OOB_DATA oob_data;
  tBTM_AUTH_REQ auth_req;
} tBTA_DM_CI_IO_REQ;

typedef struct {
  RawAddress bd_addr;
  Octet16 c;
  Octet16 r;
  bool accept;
} tBTA_DM_CI_RMT_OOB;

typedef struct {
  RawAddress bd_addr;
  DEV_CLASS dc;
  LinkKey link_key;
  uint8_t key_type;
  bool link_key_known;
  bool dc_known;
  BD_NAME bd_name;
  uint8_t pin_length;
} tBTA_DM_API_ADD_DEVICE;

typedef struct {
  tBTA_DM_SEC_CBACK* p_sec_cback;
  tBTA_DM_SEC_CBACK* p_sec_sirk_cback;
/* Storage for pin code request parameters */
  RawAddress pin_bd_addr;
  DEV_CLASS pin_dev_class;
  tBTA_DM_SEC_EVT pin_evt;
  tBTM_IO_CAP loc_io_caps;    /* IO Capabilities of local device */
  tBTM_IO_CAP rmt_io_caps;    /* IO Capabilities of remote device */
  tBTM_AUTH_REQ loc_auth_req; /* Authentication required for local device */
  tBTM_AUTH_REQ rmt_auth_req;
  uint32_t num_val; /* the numeric value for comparison. If just_works, do not
                       show this number to UI */
  bool just_works;  /* true, if "Just Works" association model */
} tBTA_DM_SEC_CB;

extern tBTA_DM_SEC_CB bta_dm_sec_cb;

void bta_dm_sec_enable(tBTA_DM_SEC_CBACK* p_sec_cback);
void btm_sec_on_hw_on();

void bta_dm_add_ble_device(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                           tBT_DEVICE_TYPE dev_type);
void bta_dm_add_blekey(const RawAddress& bd_addr, tBTA_LE_KEY_VALUE blekey,
                       tBTM_LE_KEY_TYPE key_type);
void bta_dm_add_device(std::unique_ptr<tBTA_DM_API_ADD_DEVICE> msg);
void bta_dm_ble_config_local_privacy(bool privacy_enable);
void bta_dm_ble_confirm_reply(const RawAddress& bd_addr, bool accept);
void bta_dm_ble_passkey_reply(const RawAddress& bd_addr, bool accept,
                              uint32_t passkey);
void bta_dm_ble_sirk_confirm_device_reply(const RawAddress& bd_addr,
                                          bool accept);
void bta_dm_ble_sirk_sec_cb_register(tBTA_DM_SEC_CBACK* p_cback);
void bta_dm_bond(const RawAddress& bd_addr, tBLE_ADDR_TYPE addr_type,
                 tBT_TRANSPORT transport, tBT_DEVICE_TYPE device_type);
void bta_dm_bond_cancel(const RawAddress& bd_addr);
void bta_dm_remove_device(const RawAddress& bd_addr);
void bta_dm_ci_rmt_oob_act(std::unique_ptr<tBTA_DM_CI_RMT_OOB> msg);
void bta_dm_confirm(const RawAddress& bd_addr, bool accept);
void bta_dm_consolidate(const RawAddress& identity_addr, const RawAddress& rpa);
void bta_dm_enable(tBTA_DM_SEC_CBACK* p_sec_cback);
void bta_dm_encrypt_cback(const RawAddress* bd_addr, tBT_TRANSPORT transport,
                          UNUSED_ATTR void* p_ref_data, tBTM_STATUS result);
void bta_dm_pin_reply(std::unique_ptr<tBTA_DM_API_PIN_REPLY> msg);
void bta_dm_set_encryption(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                           tBTA_DM_ENCRYPT_CBACK* p_callback,
                           tBTM_BLE_SEC_ACT sec_act);
void btm_dm_sec_init();
