/*
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

#include <bluetooth/log.h>

#include <cstdint>

#include "stack/include/bt_octets.h"
#include "stack/include/btm_api_types.h"  // tBTM_CMPL_CB
#include "stack/include/btm_ble_sec_api_types.h"
#include "stack/include/btm_sec_api_types.h"
#include "types/raw_address.h"

/*
 * Local device configuration
 */
typedef struct {
  tBTM_LOC_BD_NAME bd_name; /* local Bluetooth device name */
  bool pin_type;            /* true if PIN type is fixed */
  uint8_t pin_code_len;     /* Bonding information */
  PIN_CODE pin_code;        /* PIN CODE if pin type is fixed */
} tBTM_CFG;

/* Pairing State */
enum tBTM_PAIRING_STATE : uint8_t {
  BTM_PAIR_STATE_IDLE, /* Idle                                         */
  BTM_PAIR_STATE_GET_REM_NAME, /* Getting the remote name (to check for SM4) */
  BTM_PAIR_STATE_WAIT_PIN_REQ, /* Started authentication, waiting for PIN req
                                  (PIN is pre-fetched) */
  BTM_PAIR_STATE_WAIT_LOCAL_PIN,       /* Waiting for local PIN code */
  BTM_PAIR_STATE_WAIT_NUMERIC_CONFIRM, /* Waiting user 'yes' to numeric
                                          confirmation   */
  BTM_PAIR_STATE_KEY_ENTRY, /* Key entry state (we are a keyboard)          */
  BTM_PAIR_STATE_WAIT_LOCAL_OOB_RSP, /* Waiting for local response to peer OOB
                                        data  */
  BTM_PAIR_STATE_WAIT_LOCAL_IOCAPS, /* Waiting for local IO capabilities and OOB
                                       data */
  BTM_PAIR_STATE_INCOMING_SSP, /* Incoming SSP (got peer IO caps when idle) */
  BTM_PAIR_STATE_WAIT_AUTH_COMPLETE, /* All done, waiting authentication
                                        complete    */
  BTM_PAIR_STATE_WAIT_DISCONNECT     /* Waiting to disconnect the ACL */
};

#define BTM_PAIR_FLAGS_WE_STARTED_DD \
  0x01 /* We want to do dedicated bonding              */
#define BTM_PAIR_FLAGS_PEER_STARTED_DD \
  0x02 /* Peer initiated dedicated bonding             */
#define BTM_PAIR_FLAGS_DISC_WHEN_DONE 0x04 /* Disconnect when done     */
#define BTM_PAIR_FLAGS_PIN_REQD \
  0x08 /* set this bit when pin_callback is called     */
#define BTM_PAIR_FLAGS_PRE_FETCH_PIN \
  0x10 /* set this bit when pre-fetch pin     */
#define BTM_PAIR_FLAGS_REJECTED_CONNECT \
  0x20 /* set this bit when rejected incoming connection  */
#define BTM_PAIR_FLAGS_WE_CANCEL_DD \
  0x40 /* set this bit when cancelling a bonding procedure */
#define BTM_PAIR_FLAGS_LE_ACTIVE \
  0x80 /* use this bit when SMP pairing is active */

typedef struct {
  bool is_mux;
  RawAddress bd_addr;
  uint16_t psm;
  bool is_orig;
  tBTM_SEC_CALLBACK* p_callback;
  tSMP_SIRK_CALLBACK* p_sirk_callback;
  void* p_ref_data;
  uint16_t rfcomm_security_requirement;
  tBT_TRANSPORT transport;
  tBTM_BLE_SEC_ACT sec_act;
} tBTM_SEC_QUEUE_ENTRY;

/* Define the Device Management control structure
 */
typedef struct tBTM_SEC_DEVCB {
  tBTM_CMPL_CB*
      p_stored_link_key_cmpl_cb; /* Read/Write/Delete stored link key    */

  tBTM_BLE_LOCAL_ID_KEYS id_keys;   /* local BLE ID keys */
  Octet16 ble_encryption_key_value; /* BLE encryption key */

  tBTM_IO_CAP loc_io_caps;    /* IO capability of the local device */
  tBTM_AUTH_REQ loc_auth_req; /* the auth_req flag  */
} tBTM_SEC_DEVCB;

/* security action for L2CAP COC channels */
#define BTM_SEC_OK 1
#define BTM_SEC_ENCRYPT 2         /* encrypt the link with current key */
#define BTM_SEC_ENCRYPT_NO_MITM 3 /* unauthenticated encryption or better */
#define BTM_SEC_ENCRYPT_MITM 4    /* authenticated encryption */
#define BTM_SEC_ENC_PENDING 5     /* wait for link encryption pending */

typedef uint8_t tBTM_SEC_ACTION;

namespace fmt {
template <>
struct formatter<tBTM_PAIRING_STATE> : enum_formatter<tBTM_PAIRING_STATE> {};
}  // namespace fmt
