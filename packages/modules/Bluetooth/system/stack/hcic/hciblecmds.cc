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
#include <stddef.h>
#include <string.h>

#include <bitset>

#include "hcidefs.h"
#include "hcimsgs.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_octets.h"
#include "stack/include/bt_types.h"
#include "stack/include/btu_hcif.h"
#include "types/raw_address.h"

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

#define HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_CREATE_SYNC 14
#define HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL 0
#define HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_TERMINATE_SYNC 2
#define HCIC_PARAM_SIZE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST 8
#define HCIC_PARAM_SIZE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST 8
#define HCIC_PARAM_SIZE_CLEAR_PERIODIC_ADVERTISER_LIST 0
#define HCIC_PARAM_SIZE_READ_PERIODIC_ADVERTISER_LIST_SIZE 0
#define HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_RECEIVE_ENABLE 3
#define HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SYNC_TRANSFER 6
#define HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SET_INFO_TRANSFER 5
#define HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS 8
#define HCIC_PARAM_SIZE_SET_DEFAULT_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS 8

void btsnd_hcic_ble_write_adv_params(uint16_t adv_int_min, uint16_t adv_int_max,
                                     uint8_t adv_type,
                                     tBLE_ADDR_TYPE addr_type_own,
                                     tBLE_ADDR_TYPE addr_type_dir,
                                     const RawAddress& direct_bda,
                                     uint8_t channel_map,
                                     uint8_t adv_filter_policy) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_WRITE_ADV_PARAMS;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_WRITE_ADV_PARAMS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_WRITE_ADV_PARAMS);

  UINT16_TO_STREAM(pp, adv_int_min);
  UINT16_TO_STREAM(pp, adv_int_max);
  UINT8_TO_STREAM(pp, adv_type);
  UINT8_TO_STREAM(pp, addr_type_own);
  UINT8_TO_STREAM(pp, addr_type_dir);
  BDADDR_TO_STREAM(pp, direct_bda);
  UINT8_TO_STREAM(pp, channel_map);
  UINT8_TO_STREAM(pp, adv_filter_policy);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}
void btsnd_hcic_ble_read_adv_chnl_tx_power(void) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_READ_CMD;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_READ_ADV_CHNL_TX_POWER);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_READ_CMD);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_adv_data(uint8_t data_len, uint8_t* p_data) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA + 1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_WRITE_ADV_DATA);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA + 1);

  memset(pp, 0, HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA);

  if (p_data != NULL && data_len > 0) {
    if (data_len > HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA)
      data_len = HCIC_PARAM_SIZE_BLE_WRITE_ADV_DATA;

    UINT8_TO_STREAM(pp, data_len);

    ARRAY_TO_STREAM(pp, p_data, data_len);
  }
  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_adv_enable(uint8_t adv_enable) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_ADV_ENABLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_WRITE_ADV_ENABLE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_ADV_ENABLE);

  UINT8_TO_STREAM(pp, adv_enable);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}
void btsnd_hcic_ble_set_scan_params(uint8_t scan_type, uint16_t scan_int,
                                    uint16_t scan_win, uint8_t addr_type_own,
                                    uint8_t scan_filter_policy) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_WRITE_SCAN_PARAM;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_WRITE_SCAN_PARAMS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_WRITE_SCAN_PARAM);

  UINT8_TO_STREAM(pp, scan_type);
  UINT16_TO_STREAM(pp, scan_int);
  UINT16_TO_STREAM(pp, scan_win);
  UINT8_TO_STREAM(pp, addr_type_own);
  UINT8_TO_STREAM(pp, scan_filter_policy);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_scan_enable(uint8_t scan_enable, uint8_t duplicate) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_WRITE_SCAN_ENABLE;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_WRITE_SCAN_ENABLE);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_WRITE_SCAN_ENABLE);

  UINT8_TO_STREAM(pp, scan_enable);
  UINT8_TO_STREAM(pp, duplicate);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_upd_ll_conn_params(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout,
                                       uint16_t min_ce_len,
                                       uint16_t max_ce_len) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_UPD_LL_CONN_PARAMS;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_UPD_LL_CONN_PARAMS);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_UPD_LL_CONN_PARAMS);

  UINT16_TO_STREAM(pp, handle);

  UINT16_TO_STREAM(pp, conn_int_min);
  UINT16_TO_STREAM(pp, conn_int_max);
  UINT16_TO_STREAM(pp, conn_latency);
  UINT16_TO_STREAM(pp, conn_timeout);
  UINT16_TO_STREAM(pp, min_ce_len);
  UINT16_TO_STREAM(pp, max_ce_len);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_read_remote_feat(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_READ_REMOTE_FEAT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_READ_REMOTE_FEAT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_READ_REMOTE_FEAT);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_rand(base::Callback<void(BT_OCTET8)> cb) {
  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_BLE_RAND, nullptr, 0,
                            base::Bind(
                                [](base::Callback<void(BT_OCTET8)> cb,
                                   uint8_t* param, uint16_t param_len) {
                                  CHECK(param[0] == 0)
                                      << "LE Rand return status must be zero";
                                  cb.Run(param + 1 /* skip status */);
                                },
                                std::move(cb)));
}

void btsnd_hcic_ble_start_enc(uint16_t handle,
                              uint8_t rand[HCIC_BLE_RAND_DI_SIZE],
                              uint16_t ediv, const Octet16& ltk) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_START_ENC;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_START_ENC);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_START_ENC);

  UINT16_TO_STREAM(pp, handle);
  ARRAY_TO_STREAM(pp, rand, HCIC_BLE_RAND_DI_SIZE);
  UINT16_TO_STREAM(pp, ediv);
  ARRAY_TO_STREAM(pp, ltk.data(), HCIC_BLE_ENCRYPT_KEY_SIZE);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_ltk_req_reply(uint16_t handle, const Octet16& ltk) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_LTK_REQ_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_LTK_REQ_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_LTK_REQ_REPLY);

  UINT16_TO_STREAM(pp, handle);
  ARRAY_TO_STREAM(pp, ltk.data(), HCIC_BLE_ENCRYPT_KEY_SIZE);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_ltk_req_neg_reply(uint16_t handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_LTK_REQ_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_LTK_REQ_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_LTK_REQ_NEG_REPLY);

  UINT16_TO_STREAM(pp, handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_receiver_test(uint8_t rx_freq) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM1;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_RECEIVER_TEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM1);

  UINT8_TO_STREAM(pp, rx_freq);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_transmitter_test(uint8_t tx_freq, uint8_t test_data_len,
                                     uint8_t payload) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_WRITE_PARAM3;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_TRANSMITTER_TEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_WRITE_PARAM3);

  UINT8_TO_STREAM(pp, tx_freq);
  UINT8_TO_STREAM(pp, test_data_len);
  UINT8_TO_STREAM(pp, payload);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_test_end(void) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_READ_CMD;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_TEST_END);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_READ_CMD);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_rc_param_req_reply(uint16_t handle, uint16_t conn_int_min,
                                       uint16_t conn_int_max,
                                       uint16_t conn_latency,
                                       uint16_t conn_timeout,
                                       uint16_t min_ce_len,
                                       uint16_t max_ce_len) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_RC_PARAM_REQ_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_REPLY);

  UINT16_TO_STREAM(pp, handle);
  UINT16_TO_STREAM(pp, conn_int_min);
  UINT16_TO_STREAM(pp, conn_int_max);
  UINT16_TO_STREAM(pp, conn_latency);
  UINT16_TO_STREAM(pp, conn_timeout);
  UINT16_TO_STREAM(pp, min_ce_len);
  UINT16_TO_STREAM(pp, max_ce_len);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_rc_param_req_neg_reply(uint16_t handle, uint8_t reason) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_NEG_REPLY;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_RC_PARAM_REQ_NEG_REPLY);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_RC_PARAM_REQ_NEG_REPLY);

  UINT16_TO_STREAM(pp, handle);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_read_resolvable_addr_peer(uint8_t addr_type_peer,
                                              const RawAddress& bda_peer) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_READ_RESOLVABLE_ADDR_PEER;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_READ_RESOLVABLE_ADDR_PEER);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_READ_RESOLVABLE_ADDR_PEER);
  UINT8_TO_STREAM(pp, addr_type_peer);
  BDADDR_TO_STREAM(pp, bda_peer);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_rand_priv_addr_timeout(uint16_t rpa_timout) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_SET_RAND_PRIV_ADDR_TIMOUT;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_SET_RAND_PRIV_ADDR_TIMOUT);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_SET_RAND_PRIV_ADDR_TIMOUT);
  UINT16_TO_STREAM(pp, rpa_timout);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_data_length(uint16_t conn_handle, uint16_t tx_octets,
                                    uint16_t tx_time) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_SET_DATA_LENGTH;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_SET_DATA_LENGTH);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_SET_DATA_LENGTH);

  UINT16_TO_STREAM(pp, conn_handle);
  UINT16_TO_STREAM(pp, tx_octets);
  UINT16_TO_STREAM(pp, tx_time);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_enh_rx_test(uint8_t rx_chan, uint8_t phy,
                                uint8_t mod_index) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_ENH_RX_TEST;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_ENH_RECEIVER_TEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_ENH_RX_TEST);

  UINT8_TO_STREAM(pp, rx_chan);
  UINT8_TO_STREAM(pp, phy);
  UINT8_TO_STREAM(pp, mod_index);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_enh_tx_test(uint8_t tx_chan, uint8_t data_len,
                                uint8_t payload, uint8_t phy) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len = HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_BLE_ENH_TX_TEST;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_ENH_TRANSMITTER_TEST);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_BLE_ENH_TX_TEST);
  UINT8_TO_STREAM(pp, tx_chan);
  UINT8_TO_STREAM(pp, data_len);
  UINT8_TO_STREAM(pp, payload);
  UINT8_TO_STREAM(pp, phy);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_extended_scan_params(uint8_t own_address_type,
                                             uint8_t scanning_filter_policy,
                                             uint8_t scanning_phys,
                                             scanning_phy_cfg* phy_cfg) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  int phy_cnt =
      std::bitset<std::numeric_limits<uint8_t>::digits>(scanning_phys).count();

  uint16_t param_len = 3 + (5 * phy_cnt);
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_SET_EXTENDED_SCAN_PARAMETERS);
  UINT8_TO_STREAM(pp, param_len);

  UINT8_TO_STREAM(pp, own_address_type);
  UINT8_TO_STREAM(pp, scanning_filter_policy);
  UINT8_TO_STREAM(pp, scanning_phys);

  for (int i = 0; i < phy_cnt; i++) {
    UINT8_TO_STREAM(pp, phy_cfg[i].scan_type);
    UINT16_TO_STREAM(pp, phy_cfg[i].scan_int);
    UINT16_TO_STREAM(pp, phy_cfg[i].scan_win);
  }

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_set_extended_scan_enable(uint8_t enable,
                                             uint8_t filter_duplicates,
                                             uint16_t duration,
                                             uint16_t period) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  const int param_len = 6;
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_SET_EXTENDED_SCAN_ENABLE);
  UINT8_TO_STREAM(pp, param_len);

  UINT8_TO_STREAM(pp, enable);
  UINT8_TO_STREAM(pp, filter_duplicates);
  UINT16_TO_STREAM(pp, duration);
  UINT16_TO_STREAM(pp, period);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_set_cig_params(
    uint8_t cig_id, uint32_t sdu_itv_mtos, uint32_t sdu_itv_stom, uint8_t sca,
    uint8_t packing, uint8_t framing, uint16_t max_trans_lat_stom,
    uint16_t max_trans_lat_mtos, uint8_t cis_cnt, const EXT_CIS_CFG* cis_cfg,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 15 + cis_cnt * 9;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT8_TO_STREAM(pp, cig_id);
  UINT24_TO_STREAM(pp, sdu_itv_mtos);
  UINT24_TO_STREAM(pp, sdu_itv_stom);
  UINT8_TO_STREAM(pp, sca);
  UINT8_TO_STREAM(pp, packing);
  UINT8_TO_STREAM(pp, framing);
  UINT16_TO_STREAM(pp, max_trans_lat_mtos);
  UINT16_TO_STREAM(pp, max_trans_lat_stom);
  UINT8_TO_STREAM(pp, cis_cnt);

  for (int i = 0; i < cis_cnt; i++) {
    UINT8_TO_STREAM(pp, cis_cfg[i].cis_id);
    UINT16_TO_STREAM(pp, cis_cfg[i].max_sdu_size_mtos);
    UINT16_TO_STREAM(pp, cis_cfg[i].max_sdu_size_stom);
    UINT8_TO_STREAM(pp, cis_cfg[i].phy_mtos);
    UINT8_TO_STREAM(pp, cis_cfg[i].phy_stom);
    UINT8_TO_STREAM(pp, cis_cfg[i].rtn_mtos);
    UINT8_TO_STREAM(pp, cis_cfg[i].rtn_stom);
  }

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_SET_CIG_PARAMS, param, params_len,
                            std::move(cb));
}

void btsnd_hcic_create_cis(uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 1 + num_cis * 4;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT8_TO_STREAM(pp, num_cis);

  for (int i = 0; i < num_cis; i++) {
    UINT16_TO_STREAM(pp, cis_cfg[i].cis_conn_handle);
    UINT16_TO_STREAM(pp, cis_cfg[i].acl_conn_handle);
  }

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_CREATE_CIS, param, params_len,
                            std::move(cb));
}

void btsnd_hcic_remove_cig(uint8_t cig_id,
                           base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 1;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT8_TO_STREAM(pp, cig_id);

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_REMOVE_CIG, param, params_len,
                            std::move(cb));
}

void btsnd_hcic_accept_cis_req(uint16_t conn_handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  const int param_len = 2;
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_ACCEPT_CIS_REQ);
  UINT8_TO_STREAM(pp, param_len);

  UINT16_TO_STREAM(pp, conn_handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_rej_cis_req(uint16_t conn_handle, uint8_t reason,
                            base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 3;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, conn_handle);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_REJ_CIS_REQ, param, params_len,
                            std::move(cb));
}

void btsnd_hcic_req_peer_sca(uint16_t conn_handle) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  const int param_len = 2;
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_REQ_PEER_SCA);
  UINT8_TO_STREAM(pp, param_len);
  UINT16_TO_STREAM(pp, conn_handle);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_create_big(uint8_t big_handle, uint8_t adv_handle,
                           uint8_t num_bis, uint32_t sdu_itv,
                           uint16_t max_sdu_size, uint16_t transport_latency,
                           uint8_t rtn, uint8_t phy, uint8_t packing,
                           uint8_t framing, uint8_t enc,
                           std::array<uint8_t, 16> bcst_code) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  const int param_len = 31;
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_CREATE_BIG);
  UINT8_TO_STREAM(pp, param_len);

  UINT8_TO_STREAM(pp, big_handle);
  UINT8_TO_STREAM(pp, adv_handle);
  UINT8_TO_STREAM(pp, num_bis);
  UINT24_TO_STREAM(pp, sdu_itv);
  UINT16_TO_STREAM(pp, max_sdu_size);
  UINT16_TO_STREAM(pp, transport_latency);
  UINT8_TO_STREAM(pp, rtn);
  UINT8_TO_STREAM(pp, phy);
  UINT8_TO_STREAM(pp, packing);
  UINT8_TO_STREAM(pp, framing);
  UINT8_TO_STREAM(pp, enc);

  uint8_t* buf_ptr = bcst_code.data();
  ARRAY_TO_STREAM(pp, buf_ptr, 16);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_term_big(uint8_t big_handle, uint8_t reason) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  const int param_len = 2;
  p->len = HCIC_PREAMBLE_SIZE + param_len;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_LE_TERM_BIG);
  UINT8_TO_STREAM(pp, param_len);

  UINT8_TO_STREAM(pp, big_handle);
  UINT8_TO_STREAM(pp, reason);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_setup_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir, uint8_t data_path_id,
    uint8_t codec_id_format, uint16_t codec_id_company,
    uint16_t codec_id_vendor, uint32_t controller_delay,
    std::vector<uint8_t> codec_conf,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 13 + codec_conf.size();
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, iso_handle);
  UINT8_TO_STREAM(pp, data_path_dir);
  UINT8_TO_STREAM(pp, data_path_id);
  UINT8_TO_STREAM(pp, codec_id_format);
  UINT16_TO_STREAM(pp, codec_id_company);
  UINT16_TO_STREAM(pp, codec_id_vendor);
  UINT24_TO_STREAM(pp, controller_delay);
  UINT8_TO_STREAM(pp, codec_conf.size());
  ARRAY_TO_STREAM(pp, codec_conf.data(), static_cast<int>(codec_conf.size()));

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_SETUP_ISO_DATA_PATH, param,
                            params_len, std::move(cb));
}

void btsnd_hcic_remove_iso_data_path(
    uint16_t iso_handle, uint8_t data_path_dir,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 3;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, iso_handle);
  UINT8_TO_STREAM(pp, data_path_dir);

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_REMOVE_ISO_DATA_PATH, param,
                            params_len, std::move(cb));
}

void btsnd_hcic_read_iso_link_quality(
    uint16_t iso_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  const int params_len = 2;
  uint8_t param[params_len];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, iso_handle);

  btu_hcif_send_cmd_with_cb(FROM_HERE, HCI_LE_READ_ISO_LINK_QUALITY, param,
                            params_len, std::move(cb));
}

void btsnd_hcic_ble_periodic_advertising_create_sync(
    uint8_t options, uint8_t adv_sid, uint8_t adv_addr_type,
    const RawAddress& adv_addr, uint16_t skip_num, uint16_t sync_timeout,
    uint8_t sync_cte_type) {
  BT_HDR* p = (BT_HDR*)osi_malloc(HCI_CMD_BUF_SIZE);
  uint8_t* pp = (uint8_t*)(p + 1);

  p->len =
      HCIC_PREAMBLE_SIZE + HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_CREATE_SYNC;
  p->offset = 0;

  UINT16_TO_STREAM(pp, HCI_BLE_PERIODIC_ADVERTISING_CREATE_SYNC);
  UINT8_TO_STREAM(pp, HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_CREATE_SYNC);
  UINT8_TO_STREAM(pp, options);
  UINT8_TO_STREAM(pp, adv_sid);
  UINT8_TO_STREAM(pp, adv_addr_type);
  BDADDR_TO_STREAM(pp, adv_addr);
  UINT16_TO_STREAM(pp, skip_num);
  UINT16_TO_STREAM(pp, sync_timeout);
  UINT8_TO_STREAM(pp, sync_cte_type);

  btu_hcif_send_cmd(LOCAL_BR_EDR_CONTROLLER_ID, p);
}

void btsnd_hcic_ble_periodic_advertising_create_sync_cancel(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_BLE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL, nullptr,
      HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL, std::move(cb));
}

void btsnd_hcic_ble_periodic_advertising_terminate_sync(
    uint16_t sync_handle, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_TERMINATE_SYNC];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, sync_handle);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_BLE_PERIODIC_ADVERTISING_TERMINATE_SYNC, param,
      HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_TERMINATE_SYNC, std::move(cb));
}

void btsnd_hci_ble_add_device_to_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST];
  uint8_t* pp = param;

  UINT8_TO_STREAM(pp, adv_addr_type);
  BDADDR_TO_STREAM(pp, adv_addr);
  UINT8_TO_STREAM(pp, adv_sid);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_BLE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST, param,
      HCIC_PARAM_SIZE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST, std::move(cb));
}

void btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
    uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_sid,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST];
  uint8_t* pp = param;

  UINT8_TO_STREAM(pp, adv_addr_type);
  BDADDR_TO_STREAM(pp, adv_addr);
  UINT8_TO_STREAM(pp, adv_sid);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_BLE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST, param,
      HCIC_PARAM_SIZE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST,
      std::move(cb));
}

void btsnd_hci_ble_clear_periodic_advertiser_list(
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_BLE_CLEAR_PERIODIC_ADVERTISER_LIST, nullptr,
      HCIC_PARAM_SIZE_CLEAR_PERIODIC_ADVERTISER_LIST, std::move(cb));
}

void btsnd_hcic_ble_set_periodic_advertising_receive_enable(
    uint16_t sync_handle, bool enable,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_RECEIVE_ENABLE];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, sync_handle);
  UINT8_TO_STREAM(pp, (enable ? 0x01 : 0x00));

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_LE_SET_PERIODIC_ADVERTISING_RECEIVE_ENABLE, param,
      HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_RECEIVE_ENABLE, std::move(cb));
}

void btsnd_hcic_ble_periodic_advertising_sync_transfer(
    uint16_t conn_handle, uint16_t service_data, uint16_t sync_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SYNC_TRANSFER];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, conn_handle);
  UINT16_TO_STREAM(pp, service_data);
  UINT16_TO_STREAM(pp, sync_handle);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_LE_PERIODIC_ADVERTISING_SYNC_TRANSFER, param,
      HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SYNC_TRANSFER, std::move(cb));
}

void btsnd_hcic_ble_periodic_advertising_set_info_transfer(
    uint16_t conn_handle, uint16_t service_data, uint8_t adv_handle,
    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SET_INFO_TRANSFER];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, conn_handle);
  UINT16_TO_STREAM(pp, service_data);
  UINT8_TO_STREAM(pp, adv_handle);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_LE_PERIODIC_ADVERTISING_SET_INFO_TRANSFER, param,
      HCIC_PARAM_SIZE_PERIODIC_ADVERTISING_SET_INFO_TRANSFER, std::move(cb));
}

void btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param[HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, conn_handle);
  UINT8_TO_STREAM(pp, mode);
  UINT16_TO_STREAM(pp, skip);
  UINT16_TO_STREAM(pp, sync_timeout);
  UINT8_TO_STREAM(pp, cte_type);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_LE_SET_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAM, param,
      HCIC_PARAM_SIZE_SET_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS,
      std::move(cb));
}

void btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
    uint16_t conn_handle, uint8_t mode, uint16_t skip, uint16_t sync_timeout,
    uint8_t cte_type, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
  uint8_t param
      [HCIC_PARAM_SIZE_SET_DEFAULT_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS];
  uint8_t* pp = param;

  UINT16_TO_STREAM(pp, conn_handle);
  UINT8_TO_STREAM(pp, mode);
  UINT16_TO_STREAM(pp, skip);
  UINT16_TO_STREAM(pp, sync_timeout);
  UINT8_TO_STREAM(pp, cte_type);

  btu_hcif_send_cmd_with_cb(
      FROM_HERE, HCI_LE_SET_DEFAULT_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAM,
      param,
      HCIC_PARAM_SIZE_SET_DEFAULT_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAMS,
      std::move(cb));
}
