/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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
 ******************************************************************************/

/******************************************************************************
 *
 *  This file contains function of the HCIC unit to format and send HCI
 *  commands.
 *
 ******************************************************************************/

#include <base/functional/bind.h>
#include <base/functional/callback_forward.h>
#include <string.h>

#include "device/include/esco_parameters.h"
#include "hcidefs.h"
#include "hcimsgs.h"
#include "internal_include/bt_target.h"
#include "main/shim/acl_api.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_dev_class.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_lap.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btu_hcif.h"
#include "types/raw_address.h"

/* Message by message.... */

#define HCIC_PARAM_SIZE_INQUIRY 5

#define HCIC_INQ_INQ_LAP_OFF 0
#define HCIC_INQ_DUR_OFF 3
#define HCIC_INQ_RSP_CNT_OFF 4

/* Periodic Inquiry Mode */
#define HCIC_PARAM_SIZE_PER_INQ_MODE 9

#define HCI_PER_INQ_MAX_INTRVL_OFF 0
#define HCI_PER_INQ_MIN_INTRVL_OFF 2
#define HCI_PER_INQ_INQ_LAP_OFF 4
#define HCI_PER_INQ_DURATION_OFF 7
#define HCI_PER_INQ_RSP_CNT_OFF 8
/* Periodic Inquiry Mode */

/* Exit Periodic Inquiry Mode */
#define HCIC_PARAM_SIZE_EXIT_PER_INQ 0

/* Create Connection */
#define HCIC_PARAM_SIZE_CREATE_CONN 13

#define HCIC_CR_CONN_BD_ADDR_OFF 0
#define HCIC_CR_CONN_PKT_TYPES_OFF 6
#define HCIC_CR_CONN_REP_MODE_OFF 8
#define HCIC_CR_CONN_PAGE_SCAN_MODE_OFF 9
#define HCIC_CR_CONN_CLK_OFF_OFF 10
#define HCIC_CR_CONN_ALLOW_SWITCH_OFF 12
/* Create Connection */

/* Disconnect */
#define HCIC_PARAM_SIZE_DISCONNECT 3

#define HCI_DISC_HANDLE_OFF 0
#define HCI_DISC_REASON_OFF 2
/* Disconnect */

/* Add SCO Connection */
#define HCIC_PARAM_SIZE_ADD_SCO_CONN 4

#define HCI_ADD_SCO_HANDLE_OFF 0
#define HCI_ADD_SCO_PACKET_TYPES_OFF 2
/* Add SCO Connection */

/* Create Connection Cancel */
#define HCIC_PARAM_SIZE_CREATE_CONN_CANCEL 6

#define HCIC_CR_CONN_CANCEL_BD_ADDR_OFF 0
/* Create Connection Cancel */

/* Accept Connection Request */
#define HCIC_PARAM_SIZE_ACCEPT_CONN 7

#define HCI_ACC_CONN_BD_ADDR_OFF 0
#define HCI_ACC_CONN_ROLE_OFF 6
/* Accept Connection Request */

/* Reject Connection Request */
#define HCIC_PARAM_SIZE_REJECT_CONN 7

#define HCI_REJ_CONN_BD_ADDR_OFF 0
#define HCI_REJ_CONN_REASON_OFF 6
/* Reject Connection Request */

/* Link Key Request Reply */
#define HCIC_PARAM_SIZE_LINK_KEY_REQ_REPLY 22

#define HCI_LINK_KEY_REPLY_BD_ADDR_OFF 0
#define HCI_LINK_KEY_REPLY_LINK_KEY_OFF 6
/* Link Key Request Reply  */

/* Link Key Request Neg Reply */
#define HCIC_PARAM_SIZE_LINK_KEY_NEG_REPLY 6

#define HCI_LINK_KEY_NEG_REP_BD_ADR_OFF 0
/* Link Key Request Neg Reply  */

/* PIN Code Request Reply */
#define HCIC_PARAM_SIZE_PIN_CODE_REQ_REPLY 23

#define HCI_PIN_CODE_REPLY_BD_ADDR_OFF 0
#define HCI_PIN_CODE_REPLY_PIN_LEN_OFF 6
#define HCI_PIN_CODE_REPLY_PIN_CODE_OFF 7
/* PIN Code Request Reply  */

/* Link Key Request Neg Reply */
#define HCIC_PARAM_SIZE_PIN_CODE_NEG_REPLY 6

#define HCI_PIN_CODE_NEG_REP_BD_ADR_OFF 0
/* Link Key Request Neg Reply  */

/* Change Connection Type */
#define HCIC_PARAM_SIZE_CHANGE_CONN_TYPE 4

#define HCI_CHNG_PKT_TYPE_HANDLE_OFF 0
#define HCI_CHNG_PKT_TYPE_PKT_TYPE_OFF 2
/* Change Connection Type */

#define HCIC_PARAM_SIZE_CMD_HANDLE 2

#define HCI_CMD_HANDLE_HANDLE_OFF 0

/* Set Connection Encryption */
#define HCIC_PARAM_SIZE_SET_CONN_ENCRYPT 3

#define HCI_SET_ENCRYPT_HANDLE_OFF 0
#define HCI_SET_ENCRYPT_ENABLE_OFF 2
/* Set Connection Encryption */

/* Remote Name Request */
#define HCIC_PARAM_SIZE_RMT_NAME_REQ 10

#define HCI_RMT_NAME_BD_ADDR_OFF 0
#define HCI_RMT_NAME_REP_MODE_OFF 6
#define HCI_RMT_NAME_PAGE_SCAN_MODE_OFF 7
#define HCI_RMT_NAME_CLK_OFF_OFF 8
/* Remote Name Request */

/* Remote Name Request Cancel */
#define HCIC_PARAM_SIZE_RMT_NAME_REQ_CANCEL 6

#define HCI_RMT_NAME_CANCEL_BD_ADDR_OFF 0
/* Remote Name Request Cancel */

/* Remote Extended Features */
#define HCIC_PARAM_SIZE_RMT_EXT_FEATURES 3

#define HCI_RMT_EXT_FEATURES_HANDLE_OFF 0
#define HCI_RMT_EXT_FEATURES_PAGE_NUM_OFF 2
/* Remote Extended Features */

#define HCIC_PARAM_SIZE_SETUP_ESCO 17

#define HCI_SETUP_ESCO_HANDLE_OFF 0
#define HCI_SETUP_ESCO_TX_BW_OFF 2
#define HCI_SETUP_ESCO_RX_BW_OFF 6
#define HCI_SETUP_ESCO_MAX_LAT_OFF 10
#define HCI_SETUP_ESCO_VOICE_OFF 12
#define HCI_SETUP_ESCO_RETRAN_EFF_OFF 14
#define HCI_SETUP_ESCO_PKT_TYPES_OFF 15

#define HCIC_PARAM_SIZE_ACCEPT_ESCO 21

#define HCI_ACCEPT_ESCO_BDADDR_OFF 0
#define HCI_ACCEPT_ESCO_TX_BW_OFF 6
#define HCI_ACCEPT_ESCO_RX_BW_OFF 10
#define HCI_ACCEPT_ESCO_MAX_LAT_OFF 14
#define HCI_ACCEPT_ESCO_VOICE_OFF 16
#define HCI_ACCEPT_ESCO_RETRAN_EFF_OFF 18
#define HCI_ACCEPT_ESCO_PKT_TYPES_OFF 19

#define HCIC_PARAM_SIZE_REJECT_ESCO 7

#define HCI_REJECT_ESCO_BDADDR_OFF 0
#define HCI_REJECT_ESCO_REASON_OFF 6

/* Hold Mode */
#define HCIC_PARAM_SIZE_HOLD_MODE 6

#define HCI_HOLD_MODE_HANDLE_OFF 0
#define HCI_HOLD_MODE_MAX_PER_OFF 2
#define HCI_HOLD_MODE_MIN_PER_OFF 4
/* Hold Mode */

/* Sniff Mode */
#define HCIC_PARAM_SIZE_SNIFF_MODE 10

#define HCI_SNIFF_MODE_HANDLE_OFF 0
#define HCI_SNIFF_MODE_MAX_PER_OFF 2
#define HCI_SNIFF_MODE_MIN_PER_OFF 4
#define HCI_SNIFF_MODE_ATTEMPT_OFF 6
#define HCI_SNIFF_MODE_TIMEOUT_OFF 8
/* Sniff Mode */

/* Park Mode */
#define HCIC_PARAM_SIZE_PARK_MODE 6

#define HCI_PARK_MODE_HANDLE_OFF 0
#define HCI_PARK_MODE_MAX_PER_OFF 2
#define HCI_PARK_MODE_MIN_PER_OFF 4
/* Park Mode */

/* QoS Setup */
#define HCIC_PARAM_SIZE_QOS_SETUP 20

#define HCI_QOS_HANDLE_OFF 0
#define HCI_QOS_FLAGS_OFF 2
#define HCI_QOS_SERVICE_TYPE_OFF 3
#define HCI_QOS_TOKEN_RATE_OFF 4
#define HCI_QOS_PEAK_BANDWIDTH_OFF 8
#define HCI_QOS_LATENCY_OFF 12
#define HCI_QOS_DELAY_VAR_OFF 16
/* QoS Setup */

#define HCIC_PARAM_SIZE_SWITCH_ROLE 7

#define HCI_SWITCH_BD_ADDR_OFF 0
#define HCI_SWITCH_ROLE_OFF 6
/* Switch Role Request */

/* Write Policy Settings */
#define HCIC_PARAM_SIZE_WRITE_POLICY_SET 4

#define HCI_WRITE_POLICY_HANDLE_OFF 0
#define HCI_WRITE_POLICY_SETTINGS_OFF 2
/* Write Policy Settings */

/* Write Default Policy Settings */
#define HCIC_PARAM_SIZE_WRITE_DEF_POLICY_SET 2

#define HCI_WRITE_DEF_POLICY_SETTINGS_OFF 0
/* Write Default Policy Settings */

#define HCIC_PARAM_SIZE_SNIFF_SUB_RATE 8

#define HCI_SNIFF_SUB_RATE_HANDLE_OFF 0
#define HCI_SNIFF_SUB_RATE_MAX_LAT_OFF 2
#define HCI_SNIFF_SUB_RATE_MIN_REM_LAT_OFF 4
#define HCI_SNIFF_SUB_RATE_MIN_LOC_LAT_OFF 6
/* Sniff Subrating */

/* Extended Inquiry Response */
#define HCIC_PARAM_SIZE_EXT_INQ_RESP 241

#define HCIC_EXT_INQ_RESP_FEC_OFF 0
#define HCIC_EXT_INQ_RESP_RESPONSE 1
/* IO Capabilities Response */
#define HCIC_PARAM_SIZE_IO_CAP_RESP 9

#define HCI_IO_CAP_BD_ADDR_OFF 0
#define HCI_IO_CAPABILITY_OFF 6
#define HCI_IO_CAP_OOB_DATA_OFF 7
#define HCI_IO_CAP_AUTH_REQ_OFF 8

/* IO Capabilities Req Neg Reply */
#define HCIC_PARAM_SIZE_IO_CAP_NEG_REPLY 7

#define HCI_IO_CAP_NR_BD_ADDR_OFF 0
#define HCI_IO_CAP_NR_ERR_CODE 6

/* Read Local OOB Data */
#define HCIC_PARAM_SIZE_R_LOCAL_OOB 0

#define HCIC_PARAM_SIZE_UCONF_REPLY 6

#define HCI_USER_CONF_BD_ADDR_OFF 0

#define HCIC_PARAM_SIZE_U_PKEY_REPLY 10

#define HCI_USER_PASSKEY_BD_ADDR_OFF 0
#define HCI_USER_PASSKEY_VALUE_OFF 6

#define HCIC_PARAM_SIZE_U_PKEY_NEG_REPLY 6

#define HCI_USER_PASSKEY_NEG_BD_ADDR_OFF 0

/* Remote OOB Data Request Reply */
#define HCIC_PARAM_SIZE_REM_OOB_REPLY 38

#define HCI_REM_OOB_DATA_BD_ADDR_OFF 0
#define HCI_REM_OOB_DATA_C_OFF 6
#define HCI_REM_OOB_DATA_R_OFF 22

/* Remote OOB Data Request Negative Reply */
#define HCIC_PARAM_SIZE_REM_OOB_NEG_REPLY 6

#define HCI_REM_OOB_DATA_NEG_BD_ADDR_OFF 0

/* Read Tx Power Level */
#define HCIC_PARAM_SIZE_R_TX_POWER 0

/* Read Default Erroneous Data Reporting */
#define HCIC_PARAM_SIZE_R_ERR_DATA_RPT 0

#define HCIC_PARAM_SIZE_ENHANCED_FLUSH 3

#define HCIC_PARAM_SIZE_SEND_KEYPRESS_NOTIF 7

#define HCI_SEND_KEYPRESS_NOTIF_BD_ADDR_OFF 0
#define HCI_SEND_KEYPRESS_NOTIF_NOTIF_OFF 6

/**** end of Simple Pairing Commands ****/

#define HCIC_PARAM_SIZE_SET_EVT_FILTER 9

#define HCI_FILT_COND_FILT_TYPE_OFF 0
#define HCI_FILT_COND_COND_TYPE_OFF 1
#define HCI_FILT_COND_FILT_OFF 2
/* Set Event Filter */

/* Delete Stored Key */
#define HCIC_PARAM_SIZE_DELETE_STORED_KEY 7

#define HCI_DELETE_KEY_BD_ADDR_OFF 0
#define HCI_DELETE_KEY_ALL_FLAG_OFF 6
/* Delete Stored Key */

/* Change Local Name */
#define HCIC_PARAM_SIZE_CHANGE_NAME BD_NAME_LEN

#define HCI_CHANGE_NAME_NAME_OFF 0
/* Change Local Name */

#define HCIC_PARAM_SIZE_READ_CMD 0

#define HCIC_PARAM_SIZE_WRITE_PARAM1 1

#define HCIC_WRITE_PARAM1_PARAM_OFF 0

#define HCIC_PARAM_SIZE_WRITE_PARAM2 2

#define HCIC_WRITE_PARAM2_PARAM_OFF 0

#define HCIC_PARAM_SIZE_WRITE_PARAM3 3

#define HCIC_WRITE_PARAM3_PARAM_OFF 0

#define HCIC_PARAM_SIZE_SET_AFH_CHANNELS 10

#define HCIC_PARAM_SIZE_ENH_SET_ESCO_CONN 59
#define HCIC_PARAM_SIZE_ENH_ACC_ESCO_CONN 63

#define HCIC_PARAM_SIZE_WRITE_PAGESCAN_CFG 4

#define HCI_SCAN_CFG_INTERVAL_OFF 0
#define HCI_SCAN_CFG_WINDOW_OFF 2
/* Write Page Scan Activity */

/* Write Inquiry Scan Activity */
#define HCIC_PARAM_SIZE_WRITE_INQSCAN_CFG 4

#define HCI_SCAN_CFG_INTERVAL_OFF 0
#define HCI_SCAN_CFG_WINDOW_OFF 2
/* Write Inquiry Scan Activity */

/* Host Controller to Host flow control */
#define HCI_HOST_FLOW_CTRL_OFF 0
#define HCI_HOST_FLOW_CTRL_ACL_ON 1
#define HCI_HOST_FLOW_CTRL_SCO_ON 2
#define HCI_HOST_FLOW_CTRL_BOTH_ON 3

#define HCIC_PARAM_SIZE_WRITE_AUTOMATIC_FLUSH_TIMEOUT 4

#define HCI_FLUSH_TOUT_HANDLE_OFF 0
#define HCI_FLUSH_TOUT_TOUT_OFF 2

#define HCIC_PARAM_SIZE_READ_TX_POWER 3

#define HCI_READ_TX_POWER_HANDLE_OFF 0
#define HCI_READ_TX_POWER_TYPE_OFF 2

/* Read transmit power level parameter */
#define HCI_READ_CURRENT 0x00
#define HCI_READ_MAXIMUM 0x01

#define HCIC_PARAM_SIZE_NUM_PKTS_DONE_SIZE sizeof(btmsg_hcic_num_pkts_done_t)

#define MAX_DATA_HANDLES 10

#define HCI_PKTS_DONE_NUM_HANDLES_OFF 0
#define HCI_PKTS_DONE_HANDLE_OFF 1
#define HCI_PKTS_DONE_NUM_PKTS_OFF 3

#define HCIC_PARAM_SIZE_WRITE_LINK_SUPER_TOUT 4

#define HCI_LINK_SUPER_TOUT_HANDLE_OFF 0
#define HCI_LINK_SUPER_TOUT_TOUT_OFF 2
/* Write Link Supervision Timeout */

#define MAX_IAC_LAPS 0x40

#define HCI_WRITE_IAC_LAP_NUM_OFF 0
#define HCI_WRITE_IAC_LAP_LAP_OFF 1
/* Write Current IAC LAP */

#define HCIC_PARAM_SIZE_CONFIGURE_DATA_PATH 3

/*******************************************************************************
 * BLE Commands
 *      Note: "local_controller_id" is for transport, not counted in HCI
 *             message size
 ******************************************************************************/
#define HCIC_BLE_RAND_DI_SIZE 8
#define HCIC_BLE_IRK_SIZE 16

#define HCIC_PARAM_SIZE_SET_USED_FEAT_CMD 8
#define HCIC_PARAM_SIZE_WRITE_RANDOM_ADDR_CMD 6
#define HCIC_PARAM_SIZE_BLE_WRITE_ADV_PARAMS 15
#define HCIC_PARAM_SIZE_BLE_WRITE_SCAN_RSP 31
#define HCIC_PARAM_SIZE_WRITE_ADV_ENABLE 1
#define HCIC_PARAM_SIZE_BLE_WRITE_SCAN_PARAM 7
#define HCIC_PARAM_SIZE_BLE_WRITE_SCAN_ENABLE 2
#define HCIC_PARAM_SIZE_BLE_CREATE_LL_CONN 25
#define HCIC_PARAM_SIZE_BLE_CREATE_CONN_CANCEL 0
#define HCIC_PARAM_SIZE_CLEAR_ACCEPTLIST 0
#define HCIC_PARAM_SIZE_ADD_ACCEPTLIST 7
#define HCIC_PARAM_SIZE_REMOVE_ACCEPTLIST 7
#define HCIC_PARAM_SIZE_BLE_UPD_LL_CONN_PARAMS 14
#define HCIC_PARAM_SIZE_SET_HOST_CHNL_CLASS 5
#define HCIC_PARAM_SIZE_READ_CHNL_MAP 2
#define HCIC_PARAM_SIZE_BLE_READ_REMOTE_FEAT 2
#define HCIC_PARAM_SIZE_BLE_ENCRYPT 32
#define HCIC_PARAM_SIZE_WRITE_LE_HOST_SUPPORTED 2

#define HCIC_BLE_RAND_DI_SIZE 8
#define HCIC_BLE_ENCRYPT_KEY_SIZE 16
#define HCIC_PARAM_SIZE_BLE_START_ENC \
  (4 + HCIC_BLE_RAND_DI_SIZE + HCIC_BLE_ENCRYPT_KEY_SIZE)
#define HCIC_PARAM_SIZE_LTK_REQ_REPLY (2 + HCIC_BLE_ENCRYPT_KEY_SIZE)
#define HCIC_PARAM_SIZE_LTK_REQ_NEG_REPLY 2
#define HCIC_BLE_CHNL_MAP_SIZE 5
#define HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA 31

#define HCIC_PARAM_SIZE_BLE_ADD_DEV_RESOLVING_LIST (7 + HCIC_BLE_IRK_SIZE * 2)
#define HCIC_PARAM_SIZE_BLE_RM_DEV_RESOLVING_LIST 7
#define HCIC_PARAM_SIZE_BLE_SET_PRIVACY_MODE 8
#define HCIC_PARAM_SIZE_BLE_CLEAR_RESOLVING_LIST 0
#define HCIC_PARAM_SIZE_BLE_READ_RESOLVING_LIST_SIZE 0
#define HCIC_PARAM_SIZE_BLE_READ_RESOLVABLE_ADDR_PEER 7
#define HCIC_PARAM_SIZE_BLE_READ_RESOLVABLE_ADDR_LOCAL 7
#define HCIC_PARAM_SIZE_BLE_SET_ADDR_RESOLUTION_ENABLE 1
#define HCIC_PARAM_SIZE_BLE_SET_RAND_PRIV_ADDR_TIMOUT 2

#define HCIC_PARAM_SIZE_BLE_READ_PHY 2
#define HCIC_PARAM_SIZE_BLE_SET_DEFAULT_PHY 3
#define HCIC_PARAM_SIZE_BLE_SET_PHY 7
#define HCIC_PARAM_SIZE_BLE_ENH_RX_TEST 3
#define HCIC_PARAM_SIZE_BLE_ENH_TX_TEST 4

#define HCIC_PARAM_SIZE_BLE_SET_DATA_LENGTH 6
#define HCIC_PARAM_SIZE_BLE_WRITE_EXTENDED_SCAN_PARAM 11

#define HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_REPLY 14
#define HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_NEG_REPLY 3

static void btsnd_hcic_disconnect(uint16_t handle, uint8_t reason) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_DISCONNECT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_DISCONNECT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_DISCONNECT);
  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_add_SCO_conn(uint16_t handle, uint16_t packet_types) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ADD_SCO_CONN;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ADD_SCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ADD_SCO_CONN);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, packet_types);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_create_conn_cancel(const RawAddress& dest) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CREATE_CONN_CANCEL;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_CREATE_CONNECTION_CANCEL);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CREATE_CONN_CANCEL);

  BDADDR_TO_STREAM(pp, dest);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_accept_conn(const RawAddress& dest, uint8_t role) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ACCEPT_CONN;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ACCEPT_CONNECTION_REQUEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ACCEPT_CONN);
  BDADDR_TO_STREAM(pp, dest);
  UINT8_TO_STREAM(pp, role);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_reject_conn(const RawAddress& dest, uint8_t reason) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_REJECT_CONN;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_REJECT_CONNECTION_REQUEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_REJECT_CONN);

  BDADDR_TO_STREAM(pp, dest);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_link_key_req_reply(const RawAddress& bd_addr,
                                   const LinkKey& link_key) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_LINK_KEY_REQ_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LINK_KEY_REQUEST_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_LINK_KEY_REQ_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);
  ARRAY16_TO_STREAM(pp, link_key.data());

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_link_key_neg_reply(const RawAddress& bd_addr) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_LINK_KEY_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LINK_KEY_REQUEST_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_LINK_KEY_NEG_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_pin_code_req_reply(const RawAddress& bd_addr,
                                   uint8_t pin_code_len, PIN_CODE pin_code) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);
  int i;

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_PIN_CODE_REQ_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_PIN_CODE_REQUEST_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_PIN_CODE_REQ_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, pin_code_len);

  for (i = 0; i < pin_code_len; i++) *pp++ = *pin_code++;

  for (; i < PIN_CODE_LEN; i++) *pp++ = 0;

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_pin_code_neg_reply(const RawAddress& bd_addr) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_PIN_CODE_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_PIN_CODE_REQUEST_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_PIN_CODE_NEG_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_change_conn_type(uint16_t handle, uint16_t packet_types) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CHANGE_CONN_TYPE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_CHANGE_CONN_PACKET_TYPE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CHANGE_CONN_TYPE);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, packet_types);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_auth_request(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_AUTHENTICATION_REQUESTED);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_set_conn_encrypt(uint16_t handle, bool enable) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_SET_CONN_ENCRYPT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SET_CONN_ENCRYPTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_SET_CONN_ENCRYPT);

  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, enable);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_rmt_name_req(const RawAddress& bd_addr,
                             uint8_t page_scan_rep_mode, uint8_t page_scan_mode,
                             uint16_t clock_offset) {
  bluetooth::shim::ACL_RemoteNameRequest(bd_addr, page_scan_rep_mode,
                                         page_scan_mode, clock_offset);
}

void btsnd_hcic_rmt_name_req_cancel(const RawAddress& bd_addr) {
  bluetooth::shim::ACL_CancelRemoteNameRequest(bd_addr);
}

void btsnd_hcic_rmt_ext_features(uint16_t handle, uint8_t page_num) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_RMT_EXT_FEATURES;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_RMT_EXT_FEATURES);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_RMT_EXT_FEATURES);

  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, page_num);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_rmt_ver_req(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_RMT_VERSION_INFO);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_read_rmt_clk_offset(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_RMT_CLOCK_OFFSET);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_setup_esco_conn(uint16_t handle, uint32_t transmit_bandwidth,
                                uint32_t receive_bandwidth,
                                uint16_t max_latency, uint16_t voice,
                                uint8_t retrans_effort, uint16_t packet_types) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_SETUP_ESCO;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SETUP_ESCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_SETUP_ESCO);

  UINT16_TO_STREAM(pp, handle);
  UINT32_TO_STREAM(pp, transmit_bandwidth);
  UINT32_TO_STREAM(pp, receive_bandwidth);
  UINT16_TO_STREAM(pp, max_latency);
  UINT16_TO_STREAM(pp, voice);
  UINT8_TO_STREAM(pp, retrans_effort);
  UINT16_TO_STREAM(pp, packet_types);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_accept_esco_conn(const RawAddress& bd_addr,
                                 uint32_t transmit_bandwidth,
                                 uint32_t receive_bandwidth,
                                 uint16_t max_latency, uint16_t content_fmt,
                                 uint8_t retrans_effort,
                                 uint16_t packet_types) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ACCEPT_ESCO;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ACCEPT_ESCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ACCEPT_ESCO);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT32_TO_STREAM(pp, transmit_bandwidth);
  UINT32_TO_STREAM(pp, receive_bandwidth);
  UINT16_TO_STREAM(pp, max_latency);
  UINT16_TO_STREAM(pp, content_fmt);
  UINT8_TO_STREAM(pp, retrans_effort);
  UINT16_TO_STREAM(pp, packet_types);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_reject_esco_conn(const RawAddress& bd_addr, uint8_t reason) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_REJECT_ESCO;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_REJECT_ESCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_REJECT_ESCO);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_hold_mode(uint16_t handle, uint16_t max_hold_period,
                          uint16_t min_hold_period) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_HOLD_MODE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_HOLD_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_HOLD_MODE);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, max_hold_period);
  UINT16_TO_STREAM(pp, min_hold_period);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_sniff_mode(uint16_t handle, uint16_t max_sniff_period,
                           uint16_t min_sniff_period, uint16_t sniff_attempt,
                           uint16_t sniff_timeout) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_SNIFF_MODE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SNIFF_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_SNIFF_MODE);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, max_sniff_period);
  UINT16_TO_STREAM(pp, min_sniff_period);
  UINT16_TO_STREAM(pp, sniff_attempt);
  UINT16_TO_STREAM(pp, sniff_timeout);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_exit_sniff_mode(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_EXIT_SNIFF_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_park_mode(uint16_t handle, uint16_t beacon_max_interval,
                          uint16_t beacon_min_interval) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_PARK_MODE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_PARK_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_PARK_MODE);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, beacon_max_interval);
  UINT16_TO_STREAM(pp, beacon_min_interval);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_exit_park_mode(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_EXIT_PARK_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

static void btsnd_hcic_switch_role(const RawAddress& bd_addr, uint8_t role) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_SWITCH_ROLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SWITCH_ROLE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_SWITCH_ROLE);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, role);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_policy_set(uint16_t handle, uint16_t settings) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_POLICY_SET;
  p->offset = 0;
  UINT16_TO_STREAM(pp, HCI_WRITE_POLICY_SETTINGS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_POLICY_SET);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, settings);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_def_policy_set(uint16_t settings) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_DEF_POLICY_SET;
  p->offset = 0;
  UINT16_TO_STREAM(pp, HCI_WRITE_DEF_POLICY_SETTINGS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_DEF_POLICY_SET);

  UINT16_TO_STREAM(pp, settings);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_set_event_filter(uint8_t filt_type, uint8_t filt_cond_type,
                                 uint8_t* filt_cond, uint8_t filt_cond_len) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SET_EVENT_FILTER);

  if (filt_type) {
    p->len = (uint16_t)(HCIC_PREAMBLE_SIZE + 2 + filt_cond_len);
    UINT8_TO_STREAM(pp, (uint8_t)(2 + filt_cond_len));

    UINT8_TO_STREAM(pp, filt_type);
    UINT8_TO_STREAM(pp, filt_cond_type);

    if (filt_cond_type == HCI_FILTER_COND_DEVICE_CLASS) {
      DEVCLASS_TO_STREAM(pp, filt_cond);
      filt_cond += kDevClassLength;
      DEVCLASS_TO_STREAM(pp, filt_cond);
      filt_cond += kDevClassLength;

      filt_cond_len -= (2 * kDevClassLength);
    } else if (filt_cond_type == HCI_FILTER_COND_BD_ADDR) {
      BDADDR_TO_STREAM(pp, *((RawAddress*)filt_cond));
      filt_cond += BD_ADDR_LEN;

      filt_cond_len -= BD_ADDR_LEN;
    }

    if (filt_cond_len) ARRAY_TO_STREAM(pp, filt_cond, filt_cond_len);
  } else {
    p->len = (uint16_t)(HCIC_PREAMBLE_SIZE + 1);
    UINT8_TO_STREAM(pp, 1);

    UINT8_TO_STREAM(pp, filt_type);
  }

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_pin_type(uint8_t type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_PIN_TYPE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_delete_stored_key(const RawAddress& bd_addr,
                                  bool delete_all_flag) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_DELETE_STORED_KEY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_DELETE_STORED_LINK_KEY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_DELETE_STORED_KEY);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, delete_all_flag);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_change_name(BD_NAME name) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);
  uint16_t len = strlen((char*)name) + 1;

  memset(pp, 0, HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CHANGE_NAME);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CHANGE_NAME;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_CHANGE_LOCAL_NAME);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CHANGE_NAME);

  if (len > HCIC_PARAM_SIZE_CHANGE_NAME) len = HCIC_PARAM_SIZE_CHANGE_NAME;

  ARRAY_TO_STREAM(pp, name, len);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_read_name(void) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_READ_CMD;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_LOCAL_NAME);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_READ_CMD);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_page_tout(uint16_t timeout) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM2;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_PAGE_TOUT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM2);

  UINT16_TO_STREAM(pp, timeout);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_scan_enable(uint8_t flag) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_SCAN_ENABLE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, flag);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_pagescan_cfg(uint16_t interval, uint16_t window) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PAGESCAN_CFG;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_PAGESCAN_CFG);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PAGESCAN_CFG);

  UINT16_TO_STREAM(pp, interval);
  UINT16_TO_STREAM(pp, window);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_inqscan_cfg(uint16_t interval, uint16_t window) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_INQSCAN_CFG;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_INQUIRYSCAN_CFG);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_INQSCAN_CFG);

  UINT16_TO_STREAM(pp, interval);
  UINT16_TO_STREAM(pp, window);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_auth_enable(uint8_t flag) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_AUTHENTICATION_ENABLE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, flag);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_dev_class(DEV_CLASS dev_class) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM3;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_CLASS_OF_DEVICE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM3);

  DEVCLASS_TO_STREAM(pp, dev_class);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_voice_settings(uint16_t flags) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM2;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_VOICE_SETTINGS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM2);

  UINT16_TO_STREAM(pp, flags);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_auto_flush_tout(uint16_t handle, uint16_t tout) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_AUTOMATIC_FLUSH_TIMEOUT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_AUTOMATIC_FLUSH_TIMEOUT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_AUTOMATIC_FLUSH_TIMEOUT);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, tout);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_read_tx_power(uint16_t handle, uint8_t type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_READ_TX_POWER;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_TRANSMIT_POWER_LEVEL);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_READ_TX_POWER);

  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_link_super_tout(uint16_t handle, uint16_t timeout) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_LINK_SUPER_TOUT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_LINK_SUPER_TOUT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_LINK_SUPER_TOUT);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, timeout);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_cur_iac_lap(uint8_t num_cur_iac, LAP* const iac_lap) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + 1 + (LAP_LEN * num_cur_iac);
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_CURRENT_IAC_LAP);
  UINT8_TO_STREAM(pp, p->len - HCIC_PREAMBLE_SIZE);

  UINT8_TO_STREAM(pp, num_cur_iac);

  for (int i = 0; i < num_cur_iac; i++) LAP_TO_STREAM(pp, iac_lap[i]);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

/******************************************
 *    Lisbon Features
 ******************************************/
void btsnd_hcic_sniff_sub_rate(uint16_t handle, uint16_t max_lat,
                               uint16_t min_remote_lat,
                               uint16_t min_local_lat) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_SNIFF_SUB_RATE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_SNIFF_SUB_RATE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_SNIFF_SUB_RATE);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, max_lat);
  UINT16_TO_STREAM(pp, min_remote_lat);
  UINT16_TO_STREAM(pp, min_local_lat);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

/**** Extended Inquiry Response Commands ****/
void btsnd_hcic_write_ext_inquiry_response(void* buffer, uint8_t fec_req) {
  BT_HDR* p = (BT_HDR*)buffer;
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_EXT_INQ_RESP;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_EXT_INQ_RESPONSE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_EXT_INQ_RESP);

  UINT8_TO_STREAM(pp, fec_req);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_io_cap_req_reply(const RawAddress& bd_addr, uint8_t capability,
                                 uint8_t oob_present, uint8_t auth_req) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_IO_CAP_RESP;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_IO_CAPABILITY_REQUEST_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_IO_CAP_RESP);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, capability);
  UINT8_TO_STREAM(pp, oob_present);
  UINT8_TO_STREAM(pp, auth_req);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_enhanced_set_up_synchronous_connection(
    uint16_t conn_handle, enh_esco_params_t* p_params) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ENH_SET_ESCO_CONN;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ENH_SETUP_ESCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ENH_SET_ESCO_CONN);

  UINT16_TO_STREAM(pp, conn_handle);
  UINT32_TO_STREAM(pp, p_params->transmit_bandwidth);
  UINT32_TO_STREAM(pp, p_params->receive_bandwidth);
  UINT8_TO_STREAM(pp, p_params->transmit_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->transmit_coding_format.company_id);
  UINT16_TO_STREAM(pp,
                   p_params->transmit_coding_format.vendor_specific_codec_id);
  UINT8_TO_STREAM(pp, p_params->receive_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->receive_coding_format.company_id);
  UINT16_TO_STREAM(pp,
                   p_params->receive_coding_format.vendor_specific_codec_id);
  UINT16_TO_STREAM(pp, p_params->transmit_codec_frame_size);
  UINT16_TO_STREAM(pp, p_params->receive_codec_frame_size);
  UINT32_TO_STREAM(pp, p_params->input_bandwidth);
  UINT32_TO_STREAM(pp, p_params->output_bandwidth);
  UINT8_TO_STREAM(pp, p_params->input_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->input_coding_format.company_id);
  UINT16_TO_STREAM(pp, p_params->input_coding_format.vendor_specific_codec_id);
  UINT8_TO_STREAM(pp, p_params->output_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->output_coding_format.company_id);
  UINT16_TO_STREAM(pp, p_params->output_coding_format.vendor_specific_codec_id);
  UINT16_TO_STREAM(pp, p_params->input_coded_data_size);
  UINT16_TO_STREAM(pp, p_params->output_coded_data_size);
  UINT8_TO_STREAM(pp, p_params->input_pcm_data_format);
  UINT8_TO_STREAM(pp, p_params->output_pcm_data_format);
  UINT8_TO_STREAM(pp, p_params->input_pcm_payload_msb_position);
  UINT8_TO_STREAM(pp, p_params->output_pcm_payload_msb_position);
  UINT8_TO_STREAM(pp, p_params->input_data_path);
  UINT8_TO_STREAM(pp, p_params->output_data_path);
  UINT8_TO_STREAM(pp, p_params->input_transport_unit_size);
  UINT8_TO_STREAM(pp, p_params->output_transport_unit_size);
  UINT16_TO_STREAM(pp, p_params->max_latency_ms);
  UINT16_TO_STREAM(pp, p_params->packet_types);
  UINT8_TO_STREAM(pp, p_params->retransmission_effort);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_enhanced_accept_synchronous_connection(
    const RawAddress& bd_addr, enh_esco_params_t* p_params) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ENH_ACC_ESCO_CONN;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ENH_ACCEPT_ESCO_CONNECTION);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ENH_ACC_ESCO_CONN);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT32_TO_STREAM(pp, p_params->transmit_bandwidth);
  UINT32_TO_STREAM(pp, p_params->receive_bandwidth);
  UINT8_TO_STREAM(pp, p_params->transmit_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->transmit_coding_format.company_id);
  UINT16_TO_STREAM(pp,
                   p_params->transmit_coding_format.vendor_specific_codec_id);
  UINT8_TO_STREAM(pp, p_params->receive_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->receive_coding_format.company_id);
  UINT16_TO_STREAM(pp,
                   p_params->receive_coding_format.vendor_specific_codec_id);
  UINT16_TO_STREAM(pp, p_params->transmit_codec_frame_size);
  UINT16_TO_STREAM(pp, p_params->receive_codec_frame_size);
  UINT32_TO_STREAM(pp, p_params->input_bandwidth);
  UINT32_TO_STREAM(pp, p_params->output_bandwidth);
  UINT8_TO_STREAM(pp, p_params->input_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->input_coding_format.company_id);
  UINT16_TO_STREAM(pp, p_params->input_coding_format.vendor_specific_codec_id);
  UINT8_TO_STREAM(pp, p_params->output_coding_format.coding_format);
  UINT16_TO_STREAM(pp, p_params->output_coding_format.company_id);
  UINT16_TO_STREAM(pp, p_params->output_coding_format.vendor_specific_codec_id);
  UINT16_TO_STREAM(pp, p_params->input_coded_data_size);
  UINT16_TO_STREAM(pp, p_params->output_coded_data_size);
  UINT8_TO_STREAM(pp, p_params->input_pcm_data_format);
  UINT8_TO_STREAM(pp, p_params->output_pcm_data_format);
  UINT8_TO_STREAM(pp, p_params->input_pcm_payload_msb_position);
  UINT8_TO_STREAM(pp, p_params->output_pcm_payload_msb_position);
  UINT8_TO_STREAM(pp, p_params->input_data_path);
  UINT8_TO_STREAM(pp, p_params->output_data_path);
  UINT8_TO_STREAM(pp, p_params->input_transport_unit_size);
  UINT8_TO_STREAM(pp, p_params->output_transport_unit_size);
  UINT16_TO_STREAM(pp, p_params->max_latency_ms);
  UINT16_TO_STREAM(pp, p_params->packet_types);
  UINT8_TO_STREAM(pp, p_params->retransmission_effort);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_io_cap_req_neg_reply(const RawAddress& bd_addr,
                                     uint8_t err_code) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_IO_CAP_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_IO_CAP_REQ_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_IO_CAP_NEG_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT8_TO_STREAM(pp, err_code);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_read_local_oob_data(void) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_R_LOCAL_OOB;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_LOCAL_OOB_DATA);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_R_LOCAL_OOB);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_user_conf_reply(const RawAddress& bd_addr, bool is_yes) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_UCONF_REPLY;
  p->offset = 0;

  if (!is_yes) {
    /* Negative reply */
    UINT16_TO_STREAM(pp, HCI_USER_CONF_VALUE_NEG_REPLY);
  } else {
    /* Confirmation */
    UINT16_TO_STREAM(pp, HCI_USER_CONF_REQUEST_REPLY);
  }

  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_UCONF_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_user_passkey_reply(const RawAddress& bd_addr, uint32_t value) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_U_PKEY_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_USER_PASSKEY_REQ_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_U_PKEY_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);
  UINT32_TO_STREAM(pp, value);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_user_passkey_neg_reply(const RawAddress& bd_addr) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_U_PKEY_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_USER_PASSKEY_REQ_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_U_PKEY_NEG_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_rem_oob_reply(const RawAddress& bd_addr, const Octet16& c,
                              const Octet16& r) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_REM_OOB_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_REM_OOB_DATA_REQ_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_REM_OOB_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);
  ARRAY16_TO_STREAM(pp, c.data());
  ARRAY16_TO_STREAM(pp, r.data());

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_rem_oob_neg_reply(const RawAddress& bd_addr) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_REM_OOB_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_REM_OOB_DATA_REQ_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_REM_OOB_NEG_REPLY);

  BDADDR_TO_STREAM(pp, bd_addr);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

/**** end of Simple Pairing Commands ****/

void btsnd_hcic_enhanced_flush(uint16_t handle, uint8_t packet_type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_ENHANCED_FLUSH;
  p->offset = 0;
  UINT16_TO_STREAM(pp, HCI_ENHANCED_FLUSH);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_ENHANCED_FLUSH);

  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, packet_type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

/*************************
 * End of Lisbon Commands
 *************************/

void btsnd_hcic_read_rssi(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_RSSI);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

static void read_encryption_key_size_complete(ReadEncKeySizeCb cb, uint8_t* return_parameters,
                                              uint16_t return_parameters_length) {
  uint8_t status;
  uint16_t handle;
  uint8_t key_size;
  STREAM_TO_UINT8(status, return_parameters);
  STREAM_TO_UINT16(handle, return_parameters);
  STREAM_TO_UINT8(key_size, return_parameters);

  std::move(cb).Run(status, handle, key_size);
}

void btsnd_hcic_read_encryption_key_size(uint16_t handle, ReadEncKeySizeCb cb) {
  constexpr uint8_t len = 2;
  uint8_t param[len];
  memset(param, 0, len);

  uint8_t* p = param;
  UINT16_TO_STREAM(p, handle);

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_READ_ENCR_KEY_SIZE, param, len,
                            base::Bind(&read_encryption_key_size_complete, base::Passed(&cb)));
}

void btsnd_hcic_read_failed_contact_counter(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CMD_HANDLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_READ_FAILED_CONTACT_COUNTER);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CMD_HANDLE);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_enable_test_mode(void) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_READ_CMD;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_ENABLE_DEV_UNDER_TEST_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_READ_CMD);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_inqscan_type(uint8_t type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_INQSCAN_TYPE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_inquiry_mode(uint8_t mode) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_INQUIRY_MODE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, mode);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_write_pagescan_type(uint8_t type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_WRITE_PAGESCAN_TYPE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

static void btsnd_hcic_vendor_spec_complete(tBTM_VSC_CMPL_CB* p_vsc_cplt_cback,
                                            uint16_t opcode, uint8_t* data,
                                            uint16_t len) {
  /* If there was a callback address for vcs complete, call it */
  if (p_vsc_cplt_cback) {
    tBTM_VSC_CMPL vcs_cplt_params;
    vcs_cplt_params.opcode = opcode;
    vcs_cplt_params.param_len = len;
    vcs_cplt_params.p_param_buf = data;
    /* Call the VSC complete callback function */
    (*p_vsc_cplt_cback)(&vcs_cplt_params);
  }
}

void btsnd_hcic_vendor_spec_cmd(uint16_t opcode, uint8_t len, uint8_t* p_data,
                                tBTM_VSC_CMPL_CB* p_cmd_cplt_cback) {
  uint16_t v_opcode = HCI_GRP_VENDOR_SPECIFIC | opcode;

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, v_opcode, p_data, len,
      base::BindOnce(&btsnd_hcic_vendor_spec_complete,
                     base::Unretained(p_cmd_cplt_cback), v_opcode));
}

void btsnd_hcic_configure_data_path(hci_data_direction_t data_path_direction,
                                    uint8_t data_path_id,
                                    std::vector<uint8_t> vendor_config) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);
  uint8_t size = static_cast<uint8_t>(vendor_config.size());
  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_CONFIGURE_DATA_PATH + size;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_CONFIGURE_DATA_PATH);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_CONFIGURE_DATA_PATH + size);
  UINT8_TO_STREAM(pp, data_path_direction);
  UINT8_TO_STREAM(pp, data_path_id);
  UINT8_TO_STREAM(pp, vendor_config.size());
  if (size != 0) {
    ARRAY_TO_STREAM(pp, vendor_config.data(), size);
  }

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

namespace bluetooth::legacy::hci {
class InterfaceImpl : public Interface {
  void Disconnect(uint16_t handle, uint8_t reason) const override {
    btsnd_hcic_disconnect(handle, reason);
  }
  void ChangeConnectionPacketType(uint16_t handle,
                                  uint16_t packet_types) const override {
    btsnd_hcic_change_conn_type(handle, packet_types);
  }
  void StartRoleSwitch(const RawAddress& bd_addr, uint8_t role) const override {
    btsnd_hcic_switch_role(bd_addr, role);
  }
  void ConfigureDataPath(hci_data_direction_t data_path_direction,
                         uint8_t data_path_id,
                         std::vector<uint8_t> vendor_config) const override {
    btsnd_hcic_configure_data_path(data_path_direction, data_path_id,
                                   vendor_config);
  }
};

namespace {
const InterfaceImpl interface_;
}

const Interface& GetInterface() { return interface_; }
}  // namespace bluetooth::legacy::hci
