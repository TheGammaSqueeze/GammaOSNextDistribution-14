/*
 *
 * Copyright 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#pragma once

#include <cstdint>
#include "stack/include/bt_octets.h"
#include "stack/include/btm_sec_api_types.h"

//////////////////////////////////////////////////////////
////// from btm_ble_api_types.h
/////////////////////////////////////////////////////////
/* BLE encryption keys */
typedef struct {
  Octet16 ltk;
  BT_OCTET8 rand;
  uint16_t ediv;
  uint8_t sec_level;
  uint8_t key_size;
} tBTM_LE_PENC_KEYS;

/* BLE CSRK keys */
typedef struct {
  uint32_t counter;
  Octet16 csrk;
  uint8_t sec_level;
} tBTM_LE_PCSRK_KEYS;

/* BLE Encryption reproduction keys */
typedef struct {
  Octet16 ltk;
  uint16_t div;
  uint8_t key_size;
  uint8_t sec_level;
} tBTM_LE_LENC_KEYS;

/* BLE SRK keys */
typedef struct {
  uint32_t counter;
  uint16_t div;
  uint8_t sec_level;
  Octet16 csrk;
} tBTM_LE_LCSRK_KEYS;

typedef struct {
  Octet16 irk;
  tBLE_ADDR_TYPE identity_addr_type;
  RawAddress identity_addr;
} tBTM_LE_PID_KEYS;

typedef union {
  tBTM_LE_PENC_KEYS penc_key;   /* received peer encryption key */
  tBTM_LE_PCSRK_KEYS pcsrk_key; /* received peer device SRK */
  tBTM_LE_PID_KEYS pid_key;     /* peer device ID key */
  tBTM_LE_LENC_KEYS lenc_key;   /* local encryption reproduction keys
                                 * LTK = = d1(ER,DIV,0) */
  tBTM_LE_LCSRK_KEYS lcsrk_key; /* local device CSRK = d1(ER,DIV,1)*/
} tBTM_LE_KEY_VALUE;

typedef struct {
  tBTM_LE_KEY_TYPE key_type;
  tBTM_LE_KEY_VALUE* p_key_value;
} tBTM_LE_KEY;

typedef union {
  tBTM_LE_IO_REQ io_req; /* BTM_LE_IO_REQ_EVT      */
  uint32_t key_notif;    /* BTM_LE_KEY_NOTIF_EVT   */
                         /* BTM_LE_NC_REQ_EVT */
                         /* no callback data for
                          * BTM_LE_KEY_REQ_EVT
                          * and BTM_LE_OOB_REQ_EVT  */
  tBTM_LE_COMPLT complt; /* BTM_LE_COMPLT_EVT      */
  tSMP_OOB_DATA_TYPE req_oob_type;
  tBTM_LE_KEY key;
  tSMP_LOC_OOB_DATA local_oob_data;
  RawAddress id_addr;
} tBTM_LE_EVT_DATA;

/* Simple Pairing Events.  Called by the stack when Simple Pairing related
 * events occur.
 */
typedef uint8_t(tBTM_LE_CALLBACK)(tBTM_LE_EVT event, const RawAddress& bda,
                                  tBTM_LE_EVT_DATA* p_data);

#define BTM_BLE_KEY_TYPE_ID 1
#define BTM_BLE_KEY_TYPE_ER 2
#define BTM_BLE_KEY_TYPE_COUNTER 3  // tobe obsolete

typedef struct {
  Octet16 ir;
  Octet16 irk;
  Octet16 dhk;

} tBTM_BLE_LOCAL_ID_KEYS;

typedef union {
  tBTM_BLE_LOCAL_ID_KEYS id_keys;
  Octet16 er;
} tBTM_BLE_LOCAL_KEYS;

/* New LE identity key for local device.
 */
typedef void(tBTM_LE_KEY_CALLBACK)(uint8_t key_type,
                                   tBTM_BLE_LOCAL_KEYS* p_key);

