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
 *  This file contains the main SDP functions
 *
 ******************************************************************************/

#define LOG_TAG "sdp"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <string.h>  // memset

#include "common/init_flags.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/btm_sec_api_types.h"
#include "stack/include/l2c_api.h"
#include "stack/sdp/sdpint.h"
#include "types/raw_address.h"

using namespace bluetooth;

/******************************************************************************/
/*                     G L O B A L      S D P       D A T A                   */
/******************************************************************************/
tSDP_CB sdp_cb;

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static void sdp_connect_ind(const RawAddress& bd_addr, uint16_t l2cap_cid,
                            UNUSED_ATTR uint16_t psm, uint8_t l2cap_id);
static void sdp_config_ind(uint16_t l2cap_cid, tL2CAP_CFG_INFO* p_cfg);
static void sdp_config_cfm(uint16_t l2cap_cid, uint16_t result,
                           tL2CAP_CFG_INFO* p_cfg);
static void sdp_disconnect_ind(uint16_t l2cap_cid, bool ack_needed);
static void sdp_data_ind(uint16_t l2cap_cid, BT_HDR* p_msg);

static void sdp_connect_cfm(uint16_t l2cap_cid, uint16_t result);
static void sdp_disconnect_cfm(uint16_t l2cap_cid, uint16_t result);
static void sdp_on_l2cap_error(uint16_t l2cap_cid, uint16_t result);

/*******************************************************************************
 *
 * Function         sdp_init
 *
 * Description      This function initializes the SDP unit.
 *
 * Returns          void
 *
 ******************************************************************************/
void sdp_init(void) {
  /* Clears all structures and local SDP database (if Server is enabled) */
  memset(&sdp_cb, 0, sizeof(tSDP_CB));

  for (int i = 0; i < SDP_MAX_CONNECTIONS; i++) {
    sdp_cb.ccb[i].sdp_conn_timer = alarm_new("sdp.sdp_conn_timer");
  }

  /* Initialize the L2CAP configuration. We only care about MTU */
  sdp_cb.l2cap_my_cfg.mtu_present = true;
  sdp_cb.l2cap_my_cfg.mtu = SDP_MTU_SIZE;

  sdp_cb.max_attr_list_size = SDP_MTU_SIZE - 16;
  sdp_cb.max_recs_per_search = SDP_MAX_DISC_SERVER_RECS;

  sdp_cb.reg_info.pL2CA_ConnectInd_Cb = sdp_connect_ind;
  sdp_cb.reg_info.pL2CA_ConnectCfm_Cb = sdp_connect_cfm;
  sdp_cb.reg_info.pL2CA_ConfigInd_Cb = sdp_config_ind;
  sdp_cb.reg_info.pL2CA_ConfigCfm_Cb = sdp_config_cfm;
  sdp_cb.reg_info.pL2CA_DisconnectInd_Cb = sdp_disconnect_ind;
  sdp_cb.reg_info.pL2CA_DisconnectCfm_Cb = sdp_disconnect_cfm;
  sdp_cb.reg_info.pL2CA_DataInd_Cb = sdp_data_ind;
  sdp_cb.reg_info.pL2CA_Error_Cb = sdp_on_l2cap_error;

  /* Now, register with L2CAP */
  if (!L2CA_Register2(BT_PSM_SDP, sdp_cb.reg_info, true /* enable_snoop */,
                      nullptr, SDP_MTU_SIZE, 0, BTM_SEC_NONE)) {
    log::error("SDP Registration failed");
  }
}

void sdp_free(void) {
  for (int i = 0; i < SDP_MAX_CONNECTIONS; i++) {
    alarm_free(sdp_cb.ccb[i].sdp_conn_timer);
    sdp_cb.ccb[i].sdp_conn_timer = NULL;
  }
}

/*******************************************************************************
 *
 * Function         sdp_connect_ind
 *
 * Description      This function handles an inbound connection indication
 *                  from L2CAP. This is the case where we are acting as a
 *                  server.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_connect_ind(const RawAddress& bd_addr, uint16_t l2cap_cid,
                            UNUSED_ATTR uint16_t psm, uint8_t l2cap_id) {
  tCONN_CB* p_ccb = sdpu_allocate_ccb();
  if (p_ccb == NULL) return;

  /* Transition to the next appropriate state, waiting for config setup. */
  p_ccb->con_state = SDP_STATE_CFG_SETUP;

  /* Save the BD Address and Channel ID. */
  p_ccb->device_address = bd_addr;
  p_ccb->connection_id = l2cap_cid;
}

static void sdp_on_l2cap_error(uint16_t l2cap_cid, uint16_t result) {
  tCONN_CB* p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == nullptr) return;
  sdp_disconnect(p_ccb, SDP_CFG_FAILED);
}

/*******************************************************************************
 *
 * Function         sdp_connect_cfm
 *
 * Description      This function handles the connect confirm events
 *                  from L2CAP. This is the case when we are acting as a
 *                  client and have sent a connect request.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_connect_cfm(uint16_t l2cap_cid, uint16_t result) {
  tCONN_CB* p_ccb;

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == NULL) {
    log::warn("SDP - Rcvd conn cnf for unknown CID 0x{:x}", l2cap_cid);
    return;
  }

  /* If the connection response contains success status, then */
  /* Transition to the next state and startup the timer.      */
  if ((result == L2CAP_CONN_OK) && (p_ccb->con_state == SDP_STATE_CONN_SETUP)) {
    p_ccb->con_state = SDP_STATE_CFG_SETUP;
  } else {
    log::error("invoked with non OK status");
  }
}

/*******************************************************************************
 *
 * Function         sdp_config_ind
 *
 * Description      This function processes the L2CAP configuration indication
 *                  event.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_config_ind(uint16_t l2cap_cid, tL2CAP_CFG_INFO* p_cfg) {
  tCONN_CB* p_ccb;

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == NULL) {
    log::warn("SDP - Rcvd L2CAP cfg ind, unknown CID: 0x{:x}", l2cap_cid);
    return;
  }

  /* Remember the remote MTU size */
  if (!p_cfg->mtu_present) {
    /* use min(L2CAP_DEFAULT_MTU,SDP_MTU_SIZE) for GKI buffer size reasons */
    p_ccb->rem_mtu_size =
        (L2CAP_DEFAULT_MTU > SDP_MTU_SIZE) ? SDP_MTU_SIZE : L2CAP_DEFAULT_MTU;
  } else {
    if (p_cfg->mtu > SDP_MTU_SIZE)
      p_ccb->rem_mtu_size = SDP_MTU_SIZE;
    else
      p_ccb->rem_mtu_size = p_cfg->mtu;
  }

  log::verbose("SDP - Rcvd cfg ind, sent cfg cfm, CID: 0x{:x}", l2cap_cid);
}

/*******************************************************************************
 *
 * Function         sdp_config_cfm
 *
 * Description      This function processes the L2CAP configuration confirmation
 *                  event.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_config_cfm(uint16_t l2cap_cid, uint16_t initiator,
                           tL2CAP_CFG_INFO* p_cfg) {
  sdp_config_ind(l2cap_cid, p_cfg);

  tCONN_CB* p_ccb;

  log::verbose("SDP - Rcvd cfg cfm, CID: 0x{:x}", l2cap_cid);

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == NULL) {
    log::warn("SDP - Rcvd L2CAP cfg ind, unknown CID: 0x{:x}", l2cap_cid);
    return;
  }

  /* For now, always accept configuration from the other side */
  p_ccb->con_state = SDP_STATE_CONNECTED;

  if (p_ccb->con_flags & SDP_FLAGS_IS_ORIG) {
    sdp_disc_connected(p_ccb);
  } else {
    /* Start inactivity timer */
    alarm_set_on_mloop(p_ccb->sdp_conn_timer, SDP_INACT_TIMEOUT_MS,
                       sdp_conn_timer_timeout, p_ccb);
  }
}

/*******************************************************************************
 *
 * Function         sdp_disconnect_ind
 *
 * Description      This function handles a disconnect event from L2CAP. If
 *                  requested to, we ack the disconnect before dropping the CCB
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_disconnect_ind(uint16_t l2cap_cid, bool ack_needed) {
  tCONN_CB* p_ccb;

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == NULL) {
    log::warn("SDP - Rcvd L2CAP disc, unknown CID: 0x{:x}", l2cap_cid);
    return;
  }
  tCONN_CB& ccb = *p_ccb;

  const tSDP_REASON reason =
      (ccb.con_state == SDP_STATE_CONNECTED) ? SDP_SUCCESS : SDP_CONN_FAILED;
  sdpu_callback(ccb, reason);

  if (ack_needed) {
    log::warn("SDP - Rcvd L2CAP disc, process pend sdp ccb: 0x{:x}", l2cap_cid);
    sdpu_process_pend_ccb_new_cid(ccb);
  } else {
    log::warn("SDP - Rcvd L2CAP disc, clear pend sdp ccb: 0x{:x}", l2cap_cid);
    sdpu_clear_pend_ccb(ccb);
  }

  sdpu_release_ccb(ccb);
}

/*******************************************************************************
 *
 * Function         sdp_data_ind
 *
 * Description      This function is called when data is received from L2CAP.
 *                  if we are the originator of the connection, we are the SDP
 *                  client, and the received message is queued for the client.
 *
 *                  If we are the destination of the connection, we are the SDP
 *                  server, so the message is passed to the server processing
 *                  function.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_data_ind(uint16_t l2cap_cid, BT_HDR* p_msg) {
  tCONN_CB* p_ccb;

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb != NULL) {
    if (p_ccb->con_state == SDP_STATE_CONNECTED) {
      if (p_ccb->con_flags & SDP_FLAGS_IS_ORIG)
        sdp_disc_server_rsp(p_ccb, p_msg);
      else
        sdp_server_handle_client_req(p_ccb, p_msg);
    } else {
      log::warn("SDP - Ignored L2CAP data while in state: {}, CID: 0x{:x}",
                p_ccb->con_state, l2cap_cid);
    }
  } else {
    log::warn("SDP - Rcvd L2CAP data, unknown CID: 0x{:x}", l2cap_cid);
  }

  osi_free(p_msg);
}

/*******************************************************************************
 *
 * Function         sdp_conn_originate
 *
 * Description      This function is called from the API to originate a
 *                  connection.
 *
 * Returns          void
 *
 ******************************************************************************/
tCONN_CB* sdp_conn_originate(const RawAddress& p_bd_addr) {
  tCONN_CB* p_ccb;
  uint16_t cid;

  /* Allocate a new CCB. Return if none available. */
  p_ccb = sdpu_allocate_ccb();
  if (p_ccb == NULL) {
    log::warn("no spare CCB for peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_bd_addr));
    return (NULL);
  }

  log::verbose("SDP - Originate started for peer {}",
               ADDRESS_TO_LOGGABLE_CSTR(p_bd_addr));

  /* Look for any active sdp connection on the remote device */
  cid = sdpu_get_active_ccb_cid(p_bd_addr);

  /* We are the originator of this connection */
  p_ccb->con_flags |= SDP_FLAGS_IS_ORIG;

  /* Save the BD Address */
  p_ccb->device_address = p_bd_addr;

  /* Transition to the next appropriate state, waiting for connection confirm */
  if (!bluetooth::common::init_flags::sdp_serialization_is_enabled() ||
      cid == 0) {
    p_ccb->con_state = SDP_STATE_CONN_SETUP;
    cid = L2CA_ConnectReq2(BT_PSM_SDP, p_bd_addr, BTM_SEC_NONE);
  } else {
    p_ccb->con_state = SDP_STATE_CONN_PEND;
    log::warn("SDP already active for peer {}. cid={:#0x}",
              ADDRESS_TO_LOGGABLE_CSTR(p_bd_addr), cid);
  }

  /* Check if L2CAP started the connection process */
  if (cid == 0) {
    log::warn("SDP - Originate failed for peer {}",
              ADDRESS_TO_LOGGABLE_CSTR(p_bd_addr));
    sdpu_release_ccb(*p_ccb);
    return (NULL);
  }
  p_ccb->connection_id = cid;
  return (p_ccb);
}

/*******************************************************************************
 *
 * Function         sdp_disconnect
 *
 * Description      This function disconnects a connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void sdp_disconnect(tCONN_CB* p_ccb, tSDP_REASON reason) {
  tCONN_CB& ccb = *p_ccb;
  log::verbose("SDP - disconnect  CID: 0x{:x}", ccb.connection_id);

  /* Check if we have a connection ID */
  if (ccb.connection_id != 0) {
    ccb.disconnect_reason = reason;
    if (SDP_SUCCESS == reason && sdpu_process_pend_ccb_same_cid(*p_ccb)) {
      sdpu_callback(ccb, reason);
      sdpu_release_ccb(ccb);
      return;
    } else {
      L2CA_DisconnectReq(ccb.connection_id);
    }
  }

  /* If at setup state, we may not get callback ind from L2CAP */
  /* Call user callback immediately */
  if (ccb.con_state == SDP_STATE_CONN_SETUP) {
    sdpu_callback(ccb, reason);
    sdpu_clear_pend_ccb(ccb);
    sdpu_release_ccb(ccb);
  }
}

/*******************************************************************************
 *
 * Function         sdp_disconnect_cfm
 *
 * Description      This function handles a disconnect confirm event from L2CAP.
 *
 * Returns          void
 *
 ******************************************************************************/
static void sdp_disconnect_cfm(uint16_t l2cap_cid,
                               UNUSED_ATTR uint16_t result) {
  tCONN_CB* p_ccb;

  /* Find CCB based on CID */
  p_ccb = sdpu_find_ccb_by_cid(l2cap_cid);
  if (p_ccb == NULL) {
    log::warn("SDP - Rcvd L2CAP disc cfm, unknown CID: 0x{:x}", l2cap_cid);
    return;
  }
  tCONN_CB& ccb = *p_ccb;

  log::verbose("SDP - Rcvd L2CAP disc cfm, CID: 0x{:x}", l2cap_cid);

  sdpu_callback(ccb, static_cast<tSDP_STATUS>(ccb.disconnect_reason));
  sdpu_process_pend_ccb_new_cid(ccb);
  sdpu_release_ccb(ccb);
}

/*******************************************************************************
 *
 * Function         sdp_conn_timer_timeout
 *
 * Description      This function processes a timeout. Currently, we simply send
 *                  a disconnect request to L2CAP.
 *
 * Returns          void
 *
 ******************************************************************************/
void sdp_conn_timer_timeout(void* data) {
  tCONN_CB& ccb = *(tCONN_CB*)data;

  log::verbose("SDP - CCB timeout in state: {}  CID: 0x{:x}", ccb.con_state,
               ccb.connection_id);

  L2CA_DisconnectReq(ccb.connection_id);

  sdpu_callback(ccb, SDP_CONN_FAILED);
  sdpu_clear_pend_ccb(ccb);
  sdpu_release_ccb(ccb);
}
