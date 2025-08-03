/******************************************************************************
 *
 *  Copyright 2001-2012 Broadcom Corporation
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
 *  This file contains internally used BNEP definitions
 *
 ******************************************************************************/

#ifndef BNEP_INT_H
#define BNEP_INT_H

#include "bnep_api.h"
#include "internal_include/bt_target.h"
#include "osi/include/alarm.h"
#include "osi/include/fixed_queue.h"
#include "stack/include/bt_hdr.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/* BNEP frame types
*/
#define BNEP_FRAME_GENERAL_ETHERNET 0x00
#define BNEP_FRAME_CONTROL 0x01
#define BNEP_FRAME_COMPRESSED_ETHERNET 0x02
#define BNEP_FRAME_COMPRESSED_ETHERNET_SRC_ONLY 0x03
#define BNEP_FRAME_COMPRESSED_ETHERNET_DEST_ONLY 0x04

/* BNEP filter control message types
*/
#define BNEP_CONTROL_COMMAND_NOT_UNDERSTOOD 0x00
#define BNEP_SETUP_CONNECTION_REQUEST_MSG 0x01
#define BNEP_SETUP_CONNECTION_RESPONSE_MSG 0x02
#define BNEP_FILTER_NET_TYPE_SET_MSG 0x03
#define BNEP_FILTER_NET_TYPE_RESPONSE_MSG 0x04
#define BNEP_FILTER_MULTI_ADDR_SET_MSG 0x05
#define BNEP_FILTER_MULTI_ADDR_RESPONSE_MSG 0x06

/* BNEP header extension types
*/
#define BNEP_EXTENSION_FILTER_CONTROL 0x00

/* BNEP Setup Connection response codes
*/
#define BNEP_SETUP_CONN_OK 0x0000
#define BNEP_SETUP_INVALID_DEST_UUID 0x0001
#define BNEP_SETUP_INVALID_SRC_UUID 0x0002
#define BNEP_SETUP_INVALID_UUID_SIZE 0x0003
#define BNEP_SETUP_CONN_NOT_ALLOWED 0x0004

/* BNEP filter control response codes
*/
#define BNEP_FILTER_CRL_OK 0x0000
#define BNEP_FILTER_CRL_UNSUPPORTED 0x0001
#define BNEP_FILTER_CRL_BAD_RANGE 0x0002
#define BNEP_FILTER_CRL_MAX_REACHED 0x0003
#define BNEP_FILTER_CRL_SECURITY_ERR 0x0004

/* 802.1p protocol packet will have actual protocol field in side the payload */
#define BNEP_802_1_P_PROTOCOL 0x8100

/* Timeout definitions.  */
/* Connection related timeout */
#define BNEP_CONN_TIMEOUT_MS (20 * 1000)
/* host response timeout */
#define BNEP_HOST_TIMEOUT_MS (200 * 1000)
#define BNEP_FILTER_SET_TIMEOUT_MS (10 * 1000)

#define BNEP_MAX_RETRANSMITS 3

/* Define the BNEP Connection Control Block
*/
typedef struct {
#define BNEP_STATE_IDLE 0
#define BNEP_STATE_CONN_START 1
#define BNEP_STATE_CFG_SETUP 2
#define BNEP_STATE_CONN_SETUP 3
#define BNEP_STATE_SEC_CHECKING 4
#define BNEP_STATE_SETUP_RCVD 5
#define BNEP_STATE_CONNECTED 6
  uint8_t con_state;

#define BNEP_FLAGS_IS_ORIG 0x01
#define BNEP_FLAGS_HIS_CFG_DONE 0x02
#define BNEP_FLAGS_MY_CFG_DONE 0x04
#define BNEP_FLAGS_L2CAP_CONGESTED 0x08
#define BNEP_FLAGS_FILTER_RESP_PEND 0x10
#define BNEP_FLAGS_MULTI_RESP_PEND 0x20
#define BNEP_FLAGS_SETUP_RCVD 0x40
#define BNEP_FLAGS_CONN_COMPLETED 0x80
  uint8_t con_flags;
  BT_HDR* p_pending_data;

  uint16_t l2cap_cid;
  RawAddress rem_bda;
  alarm_t* conn_timer;
  fixed_queue_t* xmit_q;

  uint16_t sent_num_filters;
  uint16_t sent_prot_filter_start[BNEP_MAX_PROT_FILTERS];
  uint16_t sent_prot_filter_end[BNEP_MAX_PROT_FILTERS];

  uint16_t sent_mcast_filters;
  RawAddress sent_mcast_filter_start[BNEP_MAX_MULTI_FILTERS];
  RawAddress sent_mcast_filter_end[BNEP_MAX_MULTI_FILTERS];

  uint16_t rcvd_num_filters;
  uint16_t rcvd_prot_filter_start[BNEP_MAX_PROT_FILTERS];
  uint16_t rcvd_prot_filter_end[BNEP_MAX_PROT_FILTERS];

  uint16_t rcvd_mcast_filters;
  RawAddress rcvd_mcast_filter_start[BNEP_MAX_MULTI_FILTERS];
  RawAddress rcvd_mcast_filter_end[BNEP_MAX_MULTI_FILTERS];

  uint16_t bad_pkts_rcvd;
  uint8_t re_transmits;
  uint16_t handle;
  bluetooth::Uuid prv_src_uuid;
  bluetooth::Uuid prv_dst_uuid;
  bluetooth::Uuid src_uuid;
  bluetooth::Uuid dst_uuid;

} tBNEP_CONN;

/*  The main BNEP control block
*/
typedef struct {
  tL2CAP_CFG_INFO l2cap_my_cfg; /* My L2CAP config     */
  tBNEP_CONN bcb[BNEP_MAX_CONNECTIONS];

  tBNEP_CONNECT_IND_CB* p_conn_ind_cb;
  tBNEP_CONN_STATE_CB* p_conn_state_cb;
  tBNEP_DATA_IND_CB* p_data_ind_cb;
  tBNEP_DATA_BUF_CB* p_data_buf_cb;
  tBNEP_FILTER_IND_CB* p_filter_ind_cb;
  tBNEP_MFILTER_IND_CB* p_mfilter_ind_cb;
  tBNEP_TX_DATA_FLOW_CB* p_tx_data_flow_cb;

  tL2CAP_APPL_INFO reg_info;

  bool profile_registered; /* true when we got our BD addr */

} tBNEP_CB;

/* Global BNEP data
*/
extern tBNEP_CB bnep_cb;

/* Functions provided by bnep_main.cc
*/
tBNEP_RESULT bnep_register_with_l2cap(void);
void bnep_disconnect(tBNEP_CONN* p_bcb, uint16_t reason);
tBNEP_CONN* bnep_conn_originate(uint8_t* p_bd_addr);
void bnep_conn_timer_timeout(void* data);
void bnep_connected(tBNEP_CONN* p_bcb);

/* Functions provided by bnep_utils.cc
*/
tBNEP_CONN* bnepu_find_bcb_by_cid(uint16_t cid);
tBNEP_CONN* bnepu_find_bcb_by_bd_addr(const RawAddress& p_bda);
tBNEP_CONN* bnepu_allocate_bcb(const RawAddress& p_rem_bda);
void bnepu_release_bcb(tBNEP_CONN* p_bcb);
void bnepu_send_peer_our_filters(tBNEP_CONN* p_bcb);
void bnepu_send_peer_our_multi_filters(tBNEP_CONN* p_bcb);
bool bnepu_does_dest_support_prot(tBNEP_CONN* p_bcb, uint16_t protocol);
void bnepu_build_bnep_hdr(tBNEP_CONN* p_bcb, BT_HDR* p_buf, uint16_t protocol,
                          const RawAddress& src_addr,
                          const RawAddress& dest_addr, bool ext_bit);
void test_bnepu_build_bnep_hdr(tBNEP_CONN* p_bcb, BT_HDR* p_buf,
                               uint16_t protocol, uint8_t* p_src_addr,
                               uint8_t* p_dest_addr, uint8_t type);

tBNEP_CONN* bnepu_get_route_to_dest(uint8_t* p_bda);
void bnepu_check_send_packet(tBNEP_CONN* p_bcb, BT_HDR* p_buf);
void bnep_send_command_not_understood(tBNEP_CONN* p_bcb, uint8_t cmd_code);
void bnepu_process_peer_filter_set(tBNEP_CONN* p_bcb, uint8_t* p_filters,
                                   uint16_t len);
void bnepu_process_peer_filter_rsp(tBNEP_CONN* p_bcb, uint8_t* p_data);
void bnepu_process_multicast_filter_rsp(tBNEP_CONN* p_bcb, uint8_t* p_data);
void bnep_send_conn_req(tBNEP_CONN* p_bcb);
void bnep_send_conn_response(tBNEP_CONN* p_bcb, uint16_t resp_code);
void bnep_process_setup_conn_req(tBNEP_CONN* p_bcb, uint8_t* p_setup,
                                 uint8_t len);
void bnep_process_setup_conn_responce(tBNEP_CONN* p_bcb, uint8_t* p_setup);
uint8_t* bnep_process_control_packet(tBNEP_CONN* p_bcb, uint8_t* p,
                                     uint16_t* len, bool is_ext);
void bnep_sec_check_complete(const RawAddress* bd_addr, tBT_TRANSPORT trasnport,
                             void* p_ref_data);
tBNEP_RESULT bnep_is_packet_allowed(tBNEP_CONN* p_bcb,
                                    const RawAddress& dest_addr,
                                    uint16_t protocol, bool fw_ext_present,
                                    uint8_t* p_data, uint16_t org_len);

#endif
