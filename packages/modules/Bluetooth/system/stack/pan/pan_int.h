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
 *  This file contains internally used PAN definitions
 *
 ******************************************************************************/

#ifndef PAN_INT_H
#define PAN_INT_H

#include <bluetooth/log.h>

#include <cstdint>

#include "internal_include/bt_target.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/pan_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/*
 * This role is used to shutdown the profile. Used internally
 * Applications should call PAN_Deregister to shutdown the profile
 */
#define PAN_ROLE_INACTIVE 0

#define PAN_PROFILE_VERSION 0x0100 /* Version 1.00 */

typedef enum : uint8_t {
  PAN_STATE_IDLE = 0,
  PAN_STATE_CONN_START = 1,
  PAN_STATE_CONNECTED = 2,
} tPAN_STATE;

/* Define the PAN Connection Control Block
 */
typedef struct {
  tPAN_STATE con_state;

#define PAN_FLAGS_CONN_COMPLETED 0x01
  uint8_t con_flags;

  uint16_t handle;
  RawAddress rem_bda;

  uint16_t bad_pkts_rcvd;
  uint16_t src_uuid;
  uint16_t dst_uuid;
  uint16_t prv_src_uuid;
  uint16_t prv_dst_uuid;
  uint16_t ip_addr_known;
  uint32_t ip_addr;

  struct {
    size_t octets{0};
    size_t packets{0};
    size_t errors{0};
    size_t drops{0};
  } write, read;

} tPAN_CONN;

/*  The main PAN control block
 */
typedef struct {
  tPAN_ROLE role;
  tPAN_ROLE active_role;
  tPAN_ROLE prv_active_role;
  tPAN_CONN pcb[MAX_PAN_CONNS];

  tPAN_CONN_STATE_CB* pan_conn_state_cb; /* Connection state callback */
  tPAN_BRIDGE_REQ_CB* pan_bridge_req_cb;
  tPAN_DATA_IND_CB* pan_data_ind_cb;
  tPAN_DATA_BUF_IND_CB* pan_data_buf_ind_cb;
  tPAN_FILTER_IND_CB*
      pan_pfilt_ind_cb; /* protocol filter indication callback */
  tPAN_MFILTER_IND_CB*
      pan_mfilt_ind_cb; /* multicast filter indication callback */
  tPAN_TX_DATA_FLOW_CB* pan_tx_data_flow_cb;

  char* user_service_name;
  char* gn_service_name;
  char* nap_service_name;
  uint32_t pan_user_sdp_handle;
  uint32_t pan_gn_sdp_handle;
  uint32_t pan_nap_sdp_handle;
  uint8_t num_conns;
} tPAN_CB;

/* Global PAN data
 */
extern tPAN_CB pan_cb;

/******************************************************************************/
void pan_register_with_bnep(void);
void pan_conn_ind_cb(uint16_t handle, const RawAddress& p_bda,
                     const bluetooth::Uuid& remote_uuid,
                     const bluetooth::Uuid& local_uuid, bool is_role_change);
void pan_connect_state_cb(uint16_t handle, const RawAddress& rem_bda,
                          tBNEP_RESULT result, bool is_role_change);
void pan_data_buf_ind_cb(uint16_t handle, const RawAddress& src,
                         const RawAddress& dst, uint16_t protocol,
                         BT_HDR* p_buf, bool ext);
void pan_tx_data_flow_cb(uint16_t handle, tBNEP_RESULT event);
void pan_proto_filt_ind_cb(uint16_t handle, bool indication,
                           tBNEP_RESULT result, uint16_t num_filters,
                           uint8_t* p_filters);
void pan_mcast_filt_ind_cb(uint16_t handle, bool indication,
                           tBNEP_RESULT result, uint16_t num_filters,
                           uint8_t* p_filters);
uint32_t pan_register_with_sdp(uint16_t uuid, const char* p_name,
                               const char* p_desc);
tPAN_CONN* pan_allocate_pcb(const RawAddress& p_bda, uint16_t handle);
tPAN_CONN* pan_get_pcb_by_handle(uint16_t handle);
tPAN_CONN* pan_get_pcb_by_addr(const RawAddress& p_bda);
void pan_close_all_connections(void);
void pan_release_pcb(tPAN_CONN* p_pcb);
void pan_dump_status(void);

/******************************************************************************/

namespace fmt {
template <>
struct formatter<tPAN_STATE> : enum_formatter<tPAN_STATE> {};
}  // namespace fmt

#endif
