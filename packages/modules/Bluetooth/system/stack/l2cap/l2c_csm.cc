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
 *  This file contains the L2CAP channel state machine
 *
 ******************************************************************************/
#define LOG_TAG "l2c_csm"

#include <base/functional/callback.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>

#include <string>

#include "hal/snoop_logger.h"
#include "internal_include/bt_target.h"
#include "main/shim/entry.h"
#include "main/shim/metrics_api.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/stack_power_telemetry.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/l2cdefs.h"
#include "stack/l2cap/l2c_int.h"

using namespace bluetooth;

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static void l2c_csm_closed(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data);
static void l2c_csm_orig_w4_sec_comp(tL2C_CCB* p_ccb, tL2CEVT event,
                                     void* p_data);
static void l2c_csm_term_w4_sec_comp(tL2C_CCB* p_ccb, tL2CEVT event,
                                     void* p_data);
static void l2c_csm_w4_l2cap_connect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                         void* p_data);
static void l2c_csm_w4_l2ca_connect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                        void* p_data);
static void l2c_csm_config(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data);
static void l2c_csm_open(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data);
static void l2c_csm_w4_l2cap_disconnect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                            void* p_data);
static void l2c_csm_w4_l2ca_disconnect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                           void* p_data);

static const char* l2c_csm_get_event_name(tL2CEVT event);

// Send a connect response with result OK and adjust the state machine
static void l2c_csm_send_connect_rsp(tL2C_CCB* p_ccb) {
  l2c_csm_execute(p_ccb, L2CEVT_L2CA_CONNECT_RSP, NULL);
}

// Send a config request and adjust the state machine
static void l2c_csm_send_config_req(tL2C_CCB* p_ccb) {
  tL2CAP_CFG_INFO config{};
  config.mtu_present = true;
  config.mtu = p_ccb->p_rcb->my_mtu;
  p_ccb->max_rx_mtu = config.mtu;
  if (p_ccb->p_rcb->ertm_info.preferred_mode != L2CAP_FCR_BASIC_MODE) {
    config.fcr_present = true;
    config.fcr = kDefaultErtmOptions;
  }
  p_ccb->our_cfg = config;
  l2c_csm_execute(p_ccb, L2CEVT_L2CA_CONFIG_REQ, &config);
}

// Send a config response with result OK and adjust the state machine
static void l2c_csm_send_config_rsp_ok(tL2C_CCB* p_ccb, bool cbit) {
  tL2CAP_CFG_INFO config{};
  config.result = L2CAP_CFG_OK;
  if (cbit) {
    config.flags = L2CAP_CFG_FLAGS_MASK_CONT;
  }
  l2c_csm_execute(p_ccb, L2CEVT_L2CA_CONFIG_RSP, &config);
}

static void l2c_csm_send_disconnect_rsp(tL2C_CCB* p_ccb) {
  l2c_csm_execute(p_ccb, L2CEVT_L2CA_DISCONNECT_RSP, NULL);
}

static void l2c_csm_indicate_connection_open(tL2C_CCB* p_ccb) {
  if (p_ccb->connection_initiator == L2CAP_INITIATOR_LOCAL) {
    (*p_ccb->p_rcb->api.pL2CA_ConnectCfm_Cb)(p_ccb->local_cid, L2CAP_CONN_OK);
  } else {
    if (*p_ccb->p_rcb->api.pL2CA_ConnectInd_Cb) {
      (*p_ccb->p_rcb->api.pL2CA_ConnectInd_Cb)(
          p_ccb->p_lcb->remote_bd_addr, p_ccb->local_cid, p_ccb->p_rcb->psm,
          p_ccb->remote_id);
    } else {
      log::warn("pL2CA_ConnectInd_Cb is null");
    }
  }
  if (p_ccb->chnl_state == CST_OPEN && !p_ccb->p_lcb->is_transport_ble()) {
    (*p_ccb->p_rcb->api.pL2CA_ConfigCfm_Cb)(
        p_ccb->local_cid, p_ccb->connection_initiator, &p_ccb->peer_cfg);
  }
  power_telemetry::GetInstance().LogChannelConnected(
      p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
      p_ccb->p_lcb->remote_bd_addr);
}

/*******************************************************************************
 *
 * Function         l2c_csm_execute
 *
 * Description      This function executes the state machine.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_csm_execute(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data) {
  if (p_ccb == nullptr) {
    log::warn("CCB is null for event ({})", event);
    return;
  }

  if (!l2cu_is_ccb_active(p_ccb)) {
    log::warn("CCB not in use, event ({}) cannot be processed", event);
    return;
  }

  log::verbose("Entry chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);

  switch (p_ccb->chnl_state) {
    case CST_CLOSED:
      l2c_csm_closed(p_ccb, event, p_data);
      break;

    case CST_ORIG_W4_SEC_COMP:
      l2c_csm_orig_w4_sec_comp(p_ccb, event, p_data);
      break;

    case CST_TERM_W4_SEC_COMP:
      l2c_csm_term_w4_sec_comp(p_ccb, event, p_data);
      break;

    case CST_W4_L2CAP_CONNECT_RSP:
      l2c_csm_w4_l2cap_connect_rsp(p_ccb, event, p_data);
      break;

    case CST_W4_L2CA_CONNECT_RSP:
      l2c_csm_w4_l2ca_connect_rsp(p_ccb, event, p_data);
      break;

    case CST_CONFIG:
      l2c_csm_config(p_ccb, event, p_data);
      break;

    case CST_OPEN:
      l2c_csm_open(p_ccb, event, p_data);
      break;

    case CST_W4_L2CAP_DISCONNECT_RSP:
      l2c_csm_w4_l2cap_disconnect_rsp(p_ccb, event, p_data);
      break;

    case CST_W4_L2CA_DISCONNECT_RSP:
      l2c_csm_w4_l2ca_disconnect_rsp(p_ccb, event, p_data);
      break;

    default:
      log::error("Unhandled state {}, event {}", p_ccb->chnl_state, event);
      break;
  }
}

/*******************************************************************************
 *
 * Function         l2c_csm_closed
 *
 * Description      This function handles events when the channel is in
 *                  CLOSED state. This state exists only when the link is
 *                  being initially established.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_closed(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data) {
  tL2C_CONN_INFO* p_ci = (tL2C_CONN_INFO*)p_data;
  uint16_t local_cid = p_ccb->local_cid;
  tL2CA_DISCONNECT_IND_CB* disconnect_ind;

  if (p_ccb->p_rcb == NULL) {
    log::error("LCID: 0x{:04x}  st: CLOSED  evt: {} p_rcb == NULL",
               p_ccb->local_cid, l2c_csm_get_event_name(event));
    return;
  }

  disconnect_ind = p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;

  log::debug("LCID: 0x{:04x}  st: CLOSED  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_LP_CONNECT_CFM: /* Link came up         */
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        p_ccb->chnl_state = CST_ORIG_W4_SEC_COMP;
        l2ble_sec_access_req(p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm,
                             true, &l2c_link_sec_comp, p_ccb);
      } else {
        p_ccb->chnl_state = CST_ORIG_W4_SEC_COMP;
        btm_sec_l2cap_access_req(p_ccb->p_lcb->remote_bd_addr,
                                 p_ccb->p_rcb->psm, true, &l2c_link_sec_comp,
                                 p_ccb);
      }
      break;

    case L2CEVT_LP_CONNECT_CFM_NEG: /* Link failed          */
      if (p_ci->status == HCI_ERR_CONNECTION_EXISTS) {
        btm_acl_notif_conn_collision(p_ccb->p_lcb->remote_bd_addr);
      } else {
        l2cu_release_ccb(p_ccb);
        (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid,
                                            L2CAP_CONN_ACL_CONNECTION_FAILED);
        bluetooth::shim::CountCounterMetrics(
            android::bluetooth::CodePathCounterKeyEnum::
                L2CAP_CONNECT_CONFIRM_NEG,
            1);
      }
      break;

    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_REQ: /* API connect request  */
    case L2CEVT_L2CA_CONNECT_REQ:
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        p_ccb->chnl_state = CST_ORIG_W4_SEC_COMP;
        l2ble_sec_access_req(p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm,
                             true, &l2c_link_sec_comp, p_ccb);
      } else {
        if (!BTM_SetLinkPolicyActiveMode(p_ccb->p_lcb->remote_bd_addr)) {
          log::warn("Unable to set link policy active");
        }
        /* If sec access does not result in started SEC_COM or COMP_NEG are
         * already processed */
        if (btm_sec_l2cap_access_req(
                p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm, true,
                &l2c_link_sec_comp, p_ccb) == BTM_CMD_STARTED) {
          p_ccb->chnl_state = CST_ORIG_W4_SEC_COMP;
        }
      }
      break;

    case L2CEVT_SEC_COMP:
      p_ccb->chnl_state = CST_W4_L2CAP_CONNECT_RSP;

      /* Wait for the info resp in this state before sending connect req (if
       * needed) */
      if (!p_ccb->p_lcb->w4_info_rsp) {
        /* Need to have at least one compatible channel to continue */
        if (!l2c_fcr_chk_chan_modes(p_ccb)) {
          l2cu_release_ccb(p_ccb);
          (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid,
                                              L2CAP_CONN_OTHER_ERROR);
          bluetooth::shim::CountCounterMetrics(
              android::bluetooth::CodePathCounterKeyEnum::
                  L2CAP_NO_COMPATIBLE_CHANNEL_AT_CSM_CLOSED,
              1);
        } else {
          l2cu_send_peer_connect_req(p_ccb);
          alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                             L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                             l2c_ccb_timer_timeout, p_ccb);
        }
      }
      break;

    case L2CEVT_SEC_COMP_NEG: /* something is really bad with security */
      l2cu_release_ccb(p_ccb);
      (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(
          local_cid, L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED);
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::
              L2CAP_SECURITY_NEG_AT_CSM_CLOSED,
          1);
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_REQ: /* Peer connect request */
    case L2CEVT_L2CAP_CONNECT_REQ:
      /* stop link timer to avoid race condition between A2MP, Security, and
       * L2CAP */
      alarm_cancel(p_ccb->p_lcb->l2c_lcb_timer);

      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        p_ccb->chnl_state = CST_TERM_W4_SEC_COMP;
        tL2CAP_LE_RESULT_CODE result = l2ble_sec_access_req(
            p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm, false,
            &l2c_link_sec_comp, p_ccb);

        switch (result) {
          case L2CAP_LE_RESULT_INSUFFICIENT_AUTHORIZATION:
          case L2CAP_LE_RESULT_UNACCEPTABLE_PARAMETERS:
          case L2CAP_LE_RESULT_INVALID_PARAMETERS:
          case L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION:
          case L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP_KEY_SIZE:
          case L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP:
            l2cu_reject_ble_connection(p_ccb, p_ccb->remote_id, result);
            l2cu_release_ccb(p_ccb);
            break;
          case L2CAP_LE_RESULT_CONN_OK:
          case L2CAP_LE_RESULT_NO_PSM:
          case L2CAP_LE_RESULT_NO_RESOURCES:
          case L2CAP_LE_RESULT_INVALID_SOURCE_CID:
          case L2CAP_LE_RESULT_SOURCE_CID_ALREADY_ALLOCATED:
            break;
        }
      } else {
        if (!BTM_SetLinkPolicyActiveMode(p_ccb->p_lcb->remote_bd_addr)) {
          log::warn("Unable to set link policy active");
        }
        p_ccb->chnl_state = CST_TERM_W4_SEC_COMP;
        auto status = btm_sec_l2cap_access_req(p_ccb->p_lcb->remote_bd_addr,
                                               p_ccb->p_rcb->psm, false,
                                               &l2c_link_sec_comp, p_ccb);
        if (status == BTM_CMD_STARTED) {
          // started the security process, tell the peer to set a longer timer
          l2cu_send_peer_connect_rsp(p_ccb, L2CAP_CONN_PENDING, 0);
        } else {
          log::info("Check security for psm 0x{:04x}, status {}",
                    p_ccb->p_rcb->psm, status);
        }
      }
      break;

    case L2CEVT_TIMEOUT:
      l2cu_release_ccb(p_ccb);
      (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid, L2CAP_CONN_OTHER_ERROR);
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::
              L2CAP_TIMEOUT_AT_CSM_CLOSED,
          1);
      break;

    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
      osi_free(p_data);
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      l2cu_release_ccb(p_ccb);
      break;

    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_orig_w4_sec_comp
 *
 * Description      This function handles events when the channel is in
 *                  CST_ORIG_W4_SEC_COMP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_orig_w4_sec_comp(tL2C_CCB* p_ccb, tL2CEVT event,
                                     void* p_data) {
  tL2CA_DISCONNECT_IND_CB* disconnect_ind =
      p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;
  uint16_t local_cid = p_ccb->local_cid;

  log::debug("{} - LCID: 0x{:04x}  st: ORIG_W4_SEC_COMP  evt: {}",
             ((p_ccb->p_lcb) && (p_ccb->p_lcb->transport == BT_TRANSPORT_LE))
                 ? "LE "
                 : "",
             p_ccb->local_cid, l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_SEC_RE_SEND_CMD: /* BTM has enough info to proceed */
    case L2CEVT_LP_CONNECT_CFM:  /* Link came up         */
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        l2ble_sec_access_req(p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm,
                             false, &l2c_link_sec_comp, p_ccb);
      } else {
        btm_sec_l2cap_access_req(p_ccb->p_lcb->remote_bd_addr,
                                 p_ccb->p_rcb->psm, true, &l2c_link_sec_comp,
                                 p_ccb);
      }
      break;

    case L2CEVT_SEC_COMP: /* Security completed success */
      /* Wait for the info resp in this state before sending connect req (if
       * needed) */
      p_ccb->chnl_state = CST_W4_L2CAP_CONNECT_RSP;
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
        l2cble_credit_based_conn_req(p_ccb); /* Start Connection     */
      } else {
        if (!p_ccb->p_lcb->w4_info_rsp) {
          /* Need to have at least one compatible channel to continue */
          if (!l2c_fcr_chk_chan_modes(p_ccb)) {
            l2cu_release_ccb(p_ccb);
            (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid,
                                                L2CAP_CONN_OTHER_ERROR);
            bluetooth::shim::CountCounterMetrics(
                android::bluetooth::CodePathCounterKeyEnum::
                    L2CAP_NO_COMPATIBLE_CHANNEL_AT_W4_SEC,
                1);
          } else {
            alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                               L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                               l2c_ccb_timer_timeout, p_ccb);
            l2cu_send_peer_connect_req(p_ccb); /* Start Connection     */
          }
        }
      }
      break;

    case L2CEVT_SEC_COMP_NEG:
      /* If last channel immediately disconnect the ACL for better security.
         Also prevents a race condition between BTM and L2CAP */
      if ((p_ccb == p_ccb->p_lcb->ccb_queue.p_first_ccb) &&
          (p_ccb == p_ccb->p_lcb->ccb_queue.p_last_ccb)) {
        p_ccb->p_lcb->idle_timeout = 0;
      }

      l2cu_release_ccb(p_ccb);
      (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(
          local_cid, L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED);
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::
              L2CAP_SECURITY_NEG_AT_W4_SEC,
          1);
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
      osi_free(p_data);
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      /* Tell security manager to abort */
      btm_sec_abort_access_req(p_ccb->p_lcb->remote_bd_addr);

      l2cu_release_ccb(p_ccb);
      break;

    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_term_w4_sec_comp
 *
 * Description      This function handles events when the channel is in
 *                  CST_TERM_W4_SEC_COMP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_term_w4_sec_comp(tL2C_CCB* p_ccb, tL2CEVT event,
                                     void* p_data) {
  log::debug("LCID: 0x{:04x}  st: TERM_W4_SEC_COMP  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      /* Tell security manager to abort */
      btm_sec_abort_access_req(p_ccb->p_lcb->remote_bd_addr);

      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_SEC_COMP:
      p_ccb->chnl_state = CST_W4_L2CA_CONNECT_RSP;

      /* Wait for the info resp in next state before sending connect ind (if
       * needed) */
      if (!p_ccb->p_lcb->w4_info_rsp) {
        log::debug("Not waiting for info response, sending connect response");
        /* Don't need to get info from peer or already retrieved so continue */
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);

        if (p_ccb->p_lcb->transport != BT_TRANSPORT_LE) {
          log::debug("Not LE connection, sending configure request");
          l2c_csm_send_connect_rsp(p_ccb);
          l2c_csm_send_config_req(p_ccb);
        } else {
          if (p_ccb->ecoc) {
            /* Handle Credit Based Connection */
            log::debug("Calling CreditBasedConnect_Ind_Cb(), num of cids: {}",
                       p_ccb->p_lcb->pending_ecoc_conn_cnt);

            std::vector<uint16_t> pending_cids;
            for (int i = 0; i < p_ccb->p_lcb->pending_ecoc_conn_cnt; i++) {
              uint16_t cid = p_ccb->p_lcb->pending_ecoc_connection_cids[i];
              if (cid != 0) pending_cids.push_back(cid);
            }

            (*p_ccb->p_rcb->api.pL2CA_CreditBasedConnectInd_Cb)(
                p_ccb->p_lcb->remote_bd_addr, pending_cids, p_ccb->p_rcb->psm,
                p_ccb->peer_conn_cfg.mtu, p_ccb->remote_id);
          } else {
            /* Handle BLE CoC */
            log::debug("Calling Connect_Ind_Cb(), CID: 0x{:04x}",
                       p_ccb->local_cid);
            l2c_csm_send_connect_rsp(p_ccb);
            l2c_csm_indicate_connection_open(p_ccb);
          }
        }
      } else {
        /*
        ** L2CAP Connect Response will be sent out by 3 sec timer expiration
        ** because Bluesoleil doesn't respond to L2CAP Information Request.
        ** Bluesoleil seems to disconnect ACL link as failure case, because
        ** it takes too long (4~7secs) to get response.
        ** product version : Bluesoleil 2.1.1.0 EDR Release 060123
        ** stack version   : 05.04.11.20060119
        */

        /* Cancel ccb timer as security complete. waiting for w4_info_rsp
        ** once info rsp received, connection rsp timer will be started
        ** while sending connection ind to profiles
        */
        alarm_cancel(p_ccb->l2c_ccb_timer);

        /* Waiting for the info resp, tell the peer to set a longer timer */
        log::debug("Waiting for info response, sending connect pending");
        l2cu_send_peer_connect_rsp(p_ccb, L2CAP_CONN_PENDING, 0);
      }
      break;

    case L2CEVT_SEC_COMP_NEG:
      if (((tL2C_CONN_INFO*)p_data)->status == BTM_DELAY_CHECK) {
        /* start a timer - encryption change not received before L2CAP connect
         * req */
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                           L2CAP_DELAY_CHECK_SM4_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
      } else {
        if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE)
          l2cu_reject_ble_connection(
              p_ccb, p_ccb->remote_id,
              L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION);
        else
          l2cu_send_peer_connect_rsp(p_ccb, L2CAP_CONN_SECURITY_BLOCK, 0);
        l2cu_release_ccb(p_ccb);
      }
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
      osi_free(p_data);
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_L2CAP_DISCONNECT_REQ: /* Peer disconnected request */
      l2cu_send_peer_disc_rsp(p_ccb->p_lcb, p_ccb->remote_id, p_ccb->local_cid,
                              p_ccb->remote_cid);

      /* Tell security manager to abort */
      btm_sec_abort_access_req(p_ccb->p_lcb->remote_bd_addr);

      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_TIMEOUT:
      /* SM4 related. */
      acl_disconnect_from_handle(
          p_ccb->p_lcb->Handle(), HCI_ERR_AUTH_FAILURE,
          "stack::l2cap::l2c_csm::l2c_csm_term_w4_sec_comp Event timeout");
      break;

    case L2CEVT_SEC_RE_SEND_CMD: /* BTM has enough info to proceed */
      btm_sec_l2cap_access_req(p_ccb->p_lcb->remote_bd_addr, p_ccb->p_rcb->psm,
                               false, &l2c_link_sec_comp, p_ccb);
      break;

    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_w4_l2cap_connect_rsp
 *
 * Description      This function handles events when the channel is in
 *                  CST_W4_L2CAP_CONNECT_RSP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_w4_l2cap_connect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                         void* p_data) {
  tL2C_CONN_INFO* p_ci = (tL2C_CONN_INFO*)p_data;
  tL2CA_DISCONNECT_IND_CB* disconnect_ind =
      p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;
  tL2CA_CREDIT_BASED_CONNECT_CFM_CB* credit_based_connect_cfm =
      p_ccb->p_rcb->api.pL2CA_CreditBasedConnectCfm_Cb;
  uint16_t local_cid = p_ccb->local_cid;
  tL2C_LCB* p_lcb = p_ccb->p_lcb;

  log::debug("LCID: 0x{:04x}  st: W4_L2CAP_CON_RSP  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      /* Send disc indication unless peer to peer race condition AND normal
       * disconnect */
      /* *((uint8_t *)p_data) != HCI_ERR_PEER_USER happens when peer device try
       * to disconnect for normal reason */
      p_ccb->chnl_state = CST_CLOSED;
      if ((p_ccb->flags & CCB_FLAG_NO_RETRY) || !p_data ||
          (*((uint8_t*)p_data) != HCI_ERR_PEER_USER)) {
        log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                   p_ccb->local_cid);
        l2cu_release_ccb(p_ccb);
        (*disconnect_ind)(local_cid, false);
      }
      p_ccb->flags |= CCB_FLAG_NO_RETRY;
      break;

    case L2CEVT_L2CAP_CONNECT_RSP: /* Got peer connect confirm */
      p_ccb->remote_cid = p_ci->remote_cid;
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        /* Connection is completed */
        alarm_cancel(p_ccb->l2c_ccb_timer);
        p_ccb->chnl_state = CST_OPEN;
        l2c_csm_indicate_connection_open(p_ccb);
        p_ccb->local_conn_cfg = p_ccb->p_rcb->coc_cfg;
        p_ccb->remote_credit_count = p_ccb->p_rcb->coc_cfg.credits;
        l2c_csm_execute(p_ccb, L2CEVT_L2CA_CONNECT_RSP, NULL);
      } else {
        p_ccb->chnl_state = CST_CONFIG;
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
      }
      log::debug("Calling Connect_Cfm_Cb(), CID: 0x{:04x}, Success",
                 p_ccb->local_cid);

      l2c_csm_send_config_req(p_ccb);
      break;

    case L2CEVT_L2CAP_CONNECT_RSP_PND: /* Got peer connect pending */
      p_ccb->remote_cid = p_ci->remote_cid;
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                         L2CAP_CHNL_CONNECT_EXT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP:
      alarm_cancel(p_ccb->l2c_ccb_timer);
      p_ccb->chnl_state = CST_OPEN;
      log::debug("Calling credit_based_connect_cfm(),cid {}, result 0x{:04x}",
                 p_ccb->local_cid, L2CAP_CONN_OK);

      (*credit_based_connect_cfm)(p_lcb->remote_bd_addr, p_ccb->local_cid,
                                  p_ci->peer_mtu, L2CAP_CONN_OK);
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP_NEG:
      log::debug("Calling pL2CA_Error_Cb(),cid {}, result 0x{:04x}", local_cid,
                 p_ci->l2cap_result);
      (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid, p_ci->l2cap_result);
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::
              L2CAP_CREDIT_BASED_CONNECT_RSP_NEG,
          1);

      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_L2CAP_CONNECT_RSP_NEG: /* Peer rejected connection */
      log::warn("L2CAP connection rejected, lcid={}, reason={}",
                loghex(p_ccb->local_cid), loghex(p_ci->l2cap_result));
      l2cu_release_ccb(p_ccb);
      if (p_lcb->transport == BT_TRANSPORT_LE) {
        (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(
            local_cid, le_result_to_l2c_conn(p_ci->l2cap_result));
      } else {
        (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid, L2CAP_CONN_OTHER_ERROR);
      }
      bluetooth::shim::CountCounterMetrics(
          android::bluetooth::CodePathCounterKeyEnum::L2CAP_CONNECT_RSP_NEG, 1);
      break;

    case L2CEVT_TIMEOUT:
      log::warn("L2CAP connection timeout");

      if (p_ccb->ecoc) {
        for (int i = 0; i < p_lcb->pending_ecoc_conn_cnt; i++) {
          uint16_t cid = p_lcb->pending_ecoc_connection_cids[i];
          tL2C_CCB* temp_p_ccb = l2cu_find_ccb_by_cid(p_lcb, cid);
          log::warn("lcid= {}", loghex(cid));
          (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(p_ccb->local_cid,
                                              L2CAP_CONN_TIMEOUT);
          bluetooth::shim::CountCounterMetrics(
              android::bluetooth::CodePathCounterKeyEnum::
                  L2CAP_TIMEOUT_AT_CONNECT_RSP,
              1);
          l2cu_release_ccb(temp_p_ccb);
        }
        p_lcb->pending_ecoc_conn_cnt = 0;
        memset(p_lcb->pending_ecoc_connection_cids, 0,
               L2CAP_CREDIT_BASED_MAX_CIDS);

      } else {
        log::warn("lcid= {}", loghex(p_ccb->local_cid));
        l2cu_release_ccb(p_ccb);
        (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid, L2CAP_CONN_OTHER_ERROR);
        bluetooth::shim::CountCounterMetrics(
            android::bluetooth::CodePathCounterKeyEnum::
                L2CAP_CONN_OTHER_ERROR_AT_CONNECT_RSP,
            1);
      }
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      /* If we know peer CID from connect pending, we can send disconnect */
      if (p_ccb->remote_cid != 0) {
        l2cu_send_peer_disc_req(p_ccb);
        p_ccb->chnl_state = CST_W4_L2CAP_DISCONNECT_RSP;
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                           L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
      } else {
        tL2CA_DISCONNECT_CFM_CB* disconnect_cfm =
            p_ccb->p_rcb->api.pL2CA_DisconnectCfm_Cb;
        l2cu_release_ccb(p_ccb);
        if (disconnect_cfm != nullptr) {
          (*disconnect_cfm)(local_cid, L2CAP_CONN_NO_LINK);
        }
      }
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
      osi_free(p_data);
      break;

    case L2CEVT_L2CAP_INFO_RSP:
      /* Need to have at least one compatible channel to continue */
      if (!l2c_fcr_chk_chan_modes(p_ccb)) {
        l2cu_release_ccb(p_ccb);
        (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(local_cid, L2CAP_CONN_OTHER_ERROR);
        bluetooth::shim::CountCounterMetrics(
            android::bluetooth::CodePathCounterKeyEnum::
                L2CAP_INFO_NO_COMPATIBLE_CHANNEL_AT_RSP,
            1);
      } else {
        /* We have feature info, so now send peer connect request */
        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
        l2cu_send_peer_connect_req(p_ccb); /* Start Connection     */
      }
      break;

    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_w4_l2ca_connect_rsp
 *
 * Description      This function handles events when the channel is in
 *                  CST_W4_L2CA_CONNECT_RSP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_w4_l2ca_connect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                        void* p_data) {
  tL2C_CONN_INFO* p_ci;
  tL2C_LCB* p_lcb = p_ccb->p_lcb;
  tL2CA_DISCONNECT_IND_CB* disconnect_ind =
      p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;
  uint16_t local_cid = p_ccb->local_cid;

  log::debug("LCID: 0x{:04x}  st: W4_L2CA_CON_RSP  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP:
      p_ci = (tL2C_CONN_INFO*)p_data;
      if ((p_lcb == nullptr) || (p_lcb && p_lcb->transport != BT_TRANSPORT_LE)) {
        log::warn("LE link doesn't exist");
        return;
      }
      l2cu_send_peer_credit_based_conn_res(p_ccb, p_ci->lcids,
                                           p_ci->l2cap_result);
      alarm_cancel(p_ccb->l2c_ccb_timer);

      for (int i = 0; i < p_lcb->pending_ecoc_conn_cnt; i++) {
        uint16_t cid = p_lcb->pending_ecoc_connection_cids[i];
        if (cid == 0) {
          log::warn("pending_ecoc_connection_cids[{}] is {}", i, cid);
          continue;
        }

        tL2C_CCB* temp_p_ccb = l2cu_find_ccb_by_cid(p_lcb, cid);
        if (temp_p_ccb) {
          auto it = std::find(p_ci->lcids.begin(), p_ci->lcids.end(), cid);
          if (it != p_ci->lcids.end()) {
            temp_p_ccb->chnl_state = CST_OPEN;
          } else {
            l2cu_release_ccb(temp_p_ccb);
          }
        }
        else {
          log::warn(
              "temp_p_ccb is NULL, pending_ecoc_connection_cids[{}] is {}", i,
              cid);
        }
      }
      p_lcb->pending_ecoc_conn_cnt = 0;
      memset(p_lcb->pending_ecoc_connection_cids, 0,
             L2CAP_CREDIT_BASED_MAX_CIDS);

      break;
    case L2CEVT_L2CA_CONNECT_RSP:
      p_ci = (tL2C_CONN_INFO*)p_data;
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE) {
        /* Result should be OK or Reject */
        if ((!p_ci) || (p_ci->l2cap_result == L2CAP_CONN_OK)) {
          l2cble_credit_based_conn_res(p_ccb, L2CAP_CONN_OK);
          p_ccb->chnl_state = CST_OPEN;
          alarm_cancel(p_ccb->l2c_ccb_timer);
        } else {
          l2cble_credit_based_conn_res(p_ccb, p_ci->l2cap_result);
          l2cu_release_ccb(p_ccb);
        }
      } else {
        /* Result should be OK or PENDING */
        if ((!p_ci) || (p_ci->l2cap_result == L2CAP_CONN_OK)) {
          log::debug("Sending connection ok for BR_EDR");
          l2cu_send_peer_connect_rsp(p_ccb, L2CAP_CONN_OK, 0);
          p_ccb->chnl_state = CST_CONFIG;
          alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                             l2c_ccb_timer_timeout, p_ccb);
        } else {
          /* If pending, stay in same state and start extended timer */
          log::debug("Sending connection result {} and status {}",
                     p_ci->l2cap_result, p_ci->l2cap_status);
          l2cu_send_peer_connect_rsp(p_ccb, p_ci->l2cap_result,
                                     p_ci->l2cap_status);
          alarm_set_on_mloop(p_ccb->l2c_ccb_timer,
                             L2CAP_CHNL_CONNECT_EXT_TIMEOUT_MS,
                             l2c_ccb_timer_timeout, p_ccb);
        }
      }
      break;

    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP_NEG:
      p_ci = (tL2C_CONN_INFO*)p_data;
      alarm_cancel(p_ccb->l2c_ccb_timer);
      if (p_lcb != nullptr) {
        if (p_lcb->transport == BT_TRANSPORT_LE) {
          l2cu_send_peer_credit_based_conn_res(p_ccb, p_ci->lcids,
                                               p_ci->l2cap_result);
        }
        for (int i = 0; i < p_lcb->pending_ecoc_conn_cnt; i++) {
          uint16_t cid = p_lcb->pending_ecoc_connection_cids[i];
          tL2C_CCB* temp_p_ccb = l2cu_find_ccb_by_cid(p_lcb, cid);
          l2cu_release_ccb(temp_p_ccb);
        }

        p_lcb->pending_ecoc_conn_cnt = 0;
        memset(p_lcb->pending_ecoc_connection_cids, 0,
               L2CAP_CREDIT_BASED_MAX_CIDS);
      }
      break;
    case L2CEVT_L2CA_CONNECT_RSP_NEG:
      p_ci = (tL2C_CONN_INFO*)p_data;
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE)
        l2cble_credit_based_conn_res(p_ccb, p_ci->l2cap_result);
      else
        l2cu_send_peer_connect_rsp(p_ccb, p_ci->l2cap_result,
                                   p_ci->l2cap_status);
      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_TIMEOUT:
      l2cu_send_peer_connect_rsp(p_ccb, L2CAP_CONN_NO_PSM, 0);
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
      osi_free(p_data);
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      l2cu_send_peer_disc_req(p_ccb);
      p_ccb->chnl_state = CST_W4_L2CAP_DISCONNECT_RSP;
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;

    case L2CEVT_L2CAP_INFO_RSP:
      /* We have feature info, so now give the upper layer connect IND */
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      log::debug("Calling Connect_Ind_Cb(), CID: 0x{:04x}", p_ccb->local_cid);

      l2c_csm_send_connect_rsp(p_ccb);
      l2c_csm_send_config_req(p_ccb);
      break;
    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_config
 *
 * Description      This function handles events when the channel is in
 *                  CONFIG state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_config(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data) {
  tL2CAP_CFG_INFO* p_cfg = (tL2CAP_CFG_INFO*)p_data;
  tL2CA_DISCONNECT_IND_CB* disconnect_ind =
      p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;
  uint16_t local_cid = p_ccb->local_cid;
  uint8_t cfg_result;
  tL2C_LCB* p_lcb = p_ccb->p_lcb;
  tL2C_CCB* temp_p_ccb;
  tL2CAP_LE_CFG_INFO* p_le_cfg = (tL2CAP_LE_CFG_INFO*)p_data;

  log::debug("LCID: 0x{:04x}  st: CONFIG  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_REQ:
      /* For ecoc reconfig is handled below in l2c_ble. In case of success
       * let us notify upper layer about the reconfig
       */
      log::debug("Calling LeReconfigCompleted_Cb(), CID: 0x{:04x}",
                 p_ccb->local_cid);

      (*p_ccb->p_rcb->api.pL2CA_CreditBasedReconfigCompleted_Cb)(
          p_lcb->remote_bd_addr, p_ccb->local_cid, false, p_le_cfg);
      break;
    case L2CEVT_L2CAP_CONFIG_REQ: /* Peer config request   */
      cfg_result = l2cu_process_peer_cfg_req(p_ccb, p_cfg);
      if (cfg_result == L2CAP_PEER_CFG_OK) {
        log::debug("Calling Config_Req_Cb(), CID: 0x{:04x}, C-bit {}",
                   p_ccb->local_cid,
                   (p_cfg->flags & L2CAP_CFG_FLAGS_MASK_CONT));
        l2c_csm_send_config_rsp_ok(p_ccb,
                                   p_cfg->flags & L2CAP_CFG_FLAGS_MASK_CONT);
        if (p_ccb->config_done & OB_CFG_DONE) {
          if (p_ccb->remote_config_rsp_result == L2CAP_CFG_OK) {
            l2c_csm_indicate_connection_open(p_ccb);
          } else {
            if (p_ccb->connection_initiator == L2CAP_INITIATOR_LOCAL) {
              (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(p_ccb->local_cid,
                                                  L2CAP_CFG_FAILED_NO_REASON);
              bluetooth::shim::CountCounterMetrics(
                  android::bluetooth::CodePathCounterKeyEnum::
                      L2CAP_CONFIG_REQ_FAILURE,
                  1);
            }
          }
        }
      } else if (cfg_result == L2CAP_PEER_CFG_DISCONNECT) {
        /* Disconnect if channels are incompatible */
        log::debug("incompatible configurations disconnect");
        l2cu_disconnect_chnl(p_ccb);
      } else /* Return error to peer so it can renegotiate if possible */
      {
        log::debug("incompatible configurations trying reconfig");
        l2cu_send_peer_config_rsp(p_ccb, p_cfg);
      }
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_RSP:
      p_ccb->config_done |= OB_CFG_DONE;
      p_ccb->config_done |= RECONFIG_FLAG;
      p_ccb->chnl_state = CST_OPEN;
      alarm_cancel(p_ccb->l2c_ccb_timer);

      log::debug("Calling Config_Rsp_Cb(), CID: 0x{:04x}", p_ccb->local_cid);

      p_ccb->p_rcb->api.pL2CA_CreditBasedReconfigCompleted_Cb(
          p_lcb->remote_bd_addr, p_ccb->local_cid, true, p_le_cfg);

      break;
    case L2CEVT_L2CAP_CONFIG_RSP: /* Peer config response  */
      l2cu_process_peer_cfg_rsp(p_ccb, p_cfg);

      /* TBD: When config options grow beyong minimum MTU (48 bytes)
       *      logic needs to be added to handle responses with
       *      continuation bit set in flags field.
       *       1. Send additional config request out until C-bit is cleared in
       * response
       */
      p_ccb->config_done |= OB_CFG_DONE;

      if (p_ccb->config_done & IB_CFG_DONE) {
        /* Verify two sides are in compatible modes before continuing */
        if (p_ccb->our_cfg.fcr.mode != p_ccb->peer_cfg.fcr.mode) {
          l2cu_send_peer_disc_req(p_ccb);
          log::warn(
              "Calling Disconnect_Ind_Cb(Incompatible CFG), CID: 0x{:04x}  No "
              "Conf Needed",
              p_ccb->local_cid);
          l2cu_release_ccb(p_ccb);
          (*disconnect_ind)(local_cid, false);
          break;
        }

        p_ccb->config_done |= RECONFIG_FLAG;
        p_ccb->chnl_state = CST_OPEN;
        l2c_link_adjust_chnl_allocation();
        alarm_cancel(p_ccb->l2c_ccb_timer);

        /* If using eRTM and waiting for an ACK, restart the ACK timer */
        if (p_ccb->fcrb.wait_ack) l2c_fcr_start_timer(p_ccb);

        /*
         ** check p_ccb->our_cfg.fcr.mon_tout and
         *p_ccb->our_cfg.fcr.rtrans_tout
         ** we may set them to zero when sending config request during
         *renegotiation
         */
        if ((p_ccb->our_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE) &&
            ((p_ccb->our_cfg.fcr.mon_tout == 0) ||
             (p_ccb->our_cfg.fcr.rtrans_tout))) {
          l2c_fcr_adj_monitor_retran_timeout(p_ccb);
        }

        /* See if we can forward anything on the hold queue */
        if (!fixed_queue_is_empty(p_ccb->xmit_hold_q)) {
          l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);
        }
      }

      if (p_ccb->config_done & RECONFIG_FLAG) {
        // Notify only once
        bluetooth::shim::GetSnoopLogger()->SetL2capChannelOpen(
            p_ccb->p_lcb->Handle(), p_ccb->local_cid, p_ccb->remote_cid,
            p_ccb->p_rcb->psm,
            p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE);
      }

      log::debug("Calling Config_Rsp_Cb(), CID: 0x{:04x}", p_ccb->local_cid);
      p_ccb->remote_config_rsp_result = p_cfg->result;
      if (p_ccb->config_done & IB_CFG_DONE) {
        l2c_csm_indicate_connection_open(p_ccb);
      }
      break;

    case L2CEVT_L2CAP_CONFIG_RSP_NEG: /* Peer config error rsp */
                                      /* Disable the Timer */
      alarm_cancel(p_ccb->l2c_ccb_timer);

      /* If failure was channel mode try to renegotiate */
      if (!l2c_fcr_renegotiate_chan(p_ccb, p_cfg)) {
        log::debug("Calling Config_Rsp_Cb(), CID: 0x{:04x}, Failure: {}",
                   p_ccb->local_cid, p_cfg->result);
        if (p_ccb->connection_initiator == L2CAP_INITIATOR_LOCAL) {
          (*p_ccb->p_rcb->api.pL2CA_Error_Cb)(p_ccb->local_cid,
                                              L2CAP_CFG_FAILED_NO_REASON);
          bluetooth::shim::CountCounterMetrics(
              android::bluetooth::CodePathCounterKeyEnum::L2CAP_CONFIG_RSP_NEG,
              1);
        }
      }
      break;

    case L2CEVT_L2CAP_DISCONNECT_REQ: /* Peer disconnected request */
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      p_ccb->chnl_state = CST_W4_L2CA_DISCONNECT_RSP;
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  Conf Needed",
                 p_ccb->local_cid);
      (*p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb)(p_ccb->local_cid, true);
      l2c_csm_send_disconnect_rsp(p_ccb);
      break;

    case L2CEVT_L2CA_CREDIT_BASED_RECONFIG_REQ:
      l2cu_send_credit_based_reconfig_req(p_ccb, (tL2CAP_LE_CFG_INFO*)p_data);
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;
    case L2CEVT_L2CA_CONFIG_REQ: /* Upper layer config req   */
      l2cu_process_our_cfg_req(p_ccb, p_cfg);
      l2cu_send_peer_config_req(p_ccb, p_cfg);
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;

    case L2CEVT_L2CA_CONFIG_RSP: /* Upper layer config rsp   */
      l2cu_process_our_cfg_rsp(p_ccb, p_cfg);

      p_ccb->config_done |= IB_CFG_DONE;

      if (p_ccb->config_done & OB_CFG_DONE) {
        /* Verify two sides are in compatible modes before continuing */
        if (p_ccb->our_cfg.fcr.mode != p_ccb->peer_cfg.fcr.mode) {
          l2cu_send_peer_disc_req(p_ccb);
          log::warn(
              "Calling Disconnect_Ind_Cb(Incompatible CFG), CID: 0x{:04x}  No "
              "Conf Needed",
              p_ccb->local_cid);
          l2cu_release_ccb(p_ccb);
          (*disconnect_ind)(local_cid, false);
          break;
        }

        p_ccb->config_done |= RECONFIG_FLAG;
        p_ccb->chnl_state = CST_OPEN;
        l2c_link_adjust_chnl_allocation();
        alarm_cancel(p_ccb->l2c_ccb_timer);
      }

      l2cu_send_peer_config_rsp(p_ccb, p_cfg);

      /* If using eRTM and waiting for an ACK, restart the ACK timer */
      if (p_ccb->fcrb.wait_ack) l2c_fcr_start_timer(p_ccb);

      if (p_ccb->config_done & RECONFIG_FLAG) {
        // Notify only once
        bluetooth::shim::GetSnoopLogger()->SetL2capChannelOpen(
            p_ccb->p_lcb->Handle(), p_ccb->local_cid, p_ccb->remote_cid,
            p_ccb->p_rcb->psm,
            p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE);
      }

      /* See if we can forward anything on the hold queue */
      if ((p_ccb->chnl_state == CST_OPEN) &&
          (!fixed_queue_is_empty(p_ccb->xmit_hold_q))) {
        l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);
      }
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      l2cu_send_peer_disc_req(p_ccb);
      p_ccb->chnl_state = CST_W4_L2CAP_DISCONNECT_RSP;
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;

    case L2CEVT_L2CAP_DATA: /* Peer data packet rcvd    */
      log::debug("Calling DataInd_Cb(), CID: 0x{:04x}", p_ccb->local_cid);
      if (p_ccb->local_cid >= L2CAP_FIRST_FIXED_CHNL &&
          p_ccb->local_cid <= L2CAP_LAST_FIXED_CHNL) {
        if (p_ccb->local_cid < L2CAP_BASE_APPL_CID) {
          if (l2cb.fixed_reg[p_ccb->local_cid - L2CAP_FIRST_FIXED_CHNL]
                  .pL2CA_FixedData_Cb != nullptr) {
            p_ccb->metrics.rx(static_cast<BT_HDR*>(p_data)->len);
            (*l2cb.fixed_reg[p_ccb->local_cid - L2CAP_FIRST_FIXED_CHNL]
                  .pL2CA_FixedData_Cb)(p_ccb->local_cid,
                                       p_ccb->p_lcb->remote_bd_addr,
                                       (BT_HDR*)p_data);
          } else {
            if (p_data != nullptr) osi_free_and_reset(&p_data);
          }
          break;
        }
      }
      if (p_data) p_ccb->metrics.rx(static_cast<BT_HDR*>(p_data)->len);
      (*p_ccb->p_rcb->api.pL2CA_DataInd_Cb)(p_ccb->local_cid, (BT_HDR*)p_data);
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
      if (p_ccb->config_done & OB_CFG_DONE)
        l2c_enqueue_peer_data(p_ccb, (BT_HDR*)p_data);
      else
        osi_free(p_data);
      break;

    case L2CEVT_TIMEOUT:
      if (p_ccb->ecoc) {
        for (temp_p_ccb = p_lcb->ccb_queue.p_first_ccb; temp_p_ccb;
             temp_p_ccb = temp_p_ccb->p_next_ccb) {
          if ((temp_p_ccb->in_use) && (temp_p_ccb->reconfig_started)) {
            (*temp_p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb)(
                temp_p_ccb->local_cid, false);
            l2cu_release_ccb(temp_p_ccb);
          }
        }

        acl_disconnect_from_handle(
            p_ccb->p_lcb->Handle(), HCI_ERR_CONN_CAUSE_LOCAL_HOST,
            "stack::l2cap::l2c_csm::l2c_csm_config timeout");
        return;
      }

      l2cu_send_peer_disc_req(p_ccb);
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;
    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_open
 *
 * Description      This function handles events when the channel is in
 *                  OPEN state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_open(tL2C_CCB* p_ccb, tL2CEVT event, void* p_data) {
  uint16_t local_cid = p_ccb->local_cid;
  tL2CAP_CFG_INFO* p_cfg;
  tL2C_CHNL_STATE tempstate;
  uint8_t tempcfgdone;
  uint8_t cfg_result = L2CAP_PEER_CFG_DISCONNECT;
  uint16_t credit = 0;
  tL2CAP_LE_CFG_INFO* p_le_cfg = (tL2CAP_LE_CFG_INFO*)p_data;

  log::verbose("LCID: 0x{:04x}  st: OPEN  evt: {}", p_ccb->local_cid,
               l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      power_telemetry::GetInstance().LogChannelDisconnected(
          p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
          p_ccb->p_lcb->remote_bd_addr);
      l2cu_release_ccb(p_ccb);
      if (p_ccb->p_rcb)
        (*p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb)(local_cid, false);
      break;

    case L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_REQ:
      /* For ecoc reconfig is handled below in l2c_ble. In case of success
       * let us notify upper layer about the reconfig
       */
      if (p_le_cfg) {
        log::debug("Calling LeReconfigCompleted_Cb(), CID: 0x{:04x}",
                   p_ccb->local_cid);
        (*p_ccb->p_rcb->api.pL2CA_CreditBasedReconfigCompleted_Cb)(
            p_ccb->p_lcb->remote_bd_addr, p_ccb->local_cid, false, p_le_cfg);
      }
      break;

    case L2CEVT_L2CAP_CONFIG_REQ: /* Peer config request   */
      p_cfg = (tL2CAP_CFG_INFO*)p_data;

      tempstate = p_ccb->chnl_state;
      tempcfgdone = p_ccb->config_done;
      p_ccb->chnl_state = CST_CONFIG;
      // clear cached configuration in case reconfig takes place later
      p_ccb->peer_cfg.mtu_present = false;
      p_ccb->peer_cfg.flush_to_present = false;
      p_ccb->peer_cfg.qos_present = false;
      p_ccb->config_done &= ~IB_CFG_DONE;

      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      if (p_cfg) {
        cfg_result = l2cu_process_peer_cfg_req(p_ccb, p_cfg);
      }
      if (cfg_result == L2CAP_PEER_CFG_OK) {
        (*p_ccb->p_rcb->api.pL2CA_ConfigInd_Cb)(p_ccb->local_cid, p_cfg);
        l2c_csm_send_config_rsp_ok(p_ccb,
                                   p_cfg->flags & L2CAP_CFG_FLAGS_MASK_CONT);
      }

      /* Error in config parameters: reset state and config flag */
      else if (cfg_result == L2CAP_PEER_CFG_UNACCEPTABLE) {
        alarm_cancel(p_ccb->l2c_ccb_timer);
        p_ccb->chnl_state = tempstate;
        p_ccb->config_done = tempcfgdone;
        l2cu_send_peer_config_rsp(p_ccb, p_cfg);
      } else /* L2CAP_PEER_CFG_DISCONNECT */
      {
        /* Disconnect if channels are incompatible
         * Note this should not occur if reconfigure
         * since this should have never passed original config.
         */
        l2cu_disconnect_chnl(p_ccb);
      }
      break;

    case L2CEVT_L2CAP_DISCONNECT_REQ: /* Peer disconnected request */
      if (p_ccb->p_lcb->transport != BT_TRANSPORT_LE) {
        if (!BTM_SetLinkPolicyActiveMode(p_ccb->p_lcb->remote_bd_addr)) {
          log::warn("Unable to set link policy active");
        }
      }

      p_ccb->chnl_state = CST_W4_L2CA_DISCONNECT_RSP;
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  Conf Needed",
                 p_ccb->local_cid);
      power_telemetry::GetInstance().LogChannelDisconnected(
          p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
          p_ccb->p_lcb->remote_bd_addr);
      (*p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb)(p_ccb->local_cid, true);
      l2c_csm_send_disconnect_rsp(p_ccb);
      break;

    case L2CEVT_L2CAP_DATA: /* Peer data packet rcvd    */
      if (p_data && (p_ccb->p_rcb)) {
        uint16_t package_len = ((BT_HDR*)p_data)->len;
        if (p_ccb->p_rcb->api.pL2CA_DataInd_Cb) {
          p_ccb->metrics.rx(static_cast<BT_HDR*>(p_data)->len);
          (*p_ccb->p_rcb->api.pL2CA_DataInd_Cb)(p_ccb->local_cid,
                                                (BT_HDR*)p_data);
        }

        power_telemetry::GetInstance().LogRxBytes(
            p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
            p_ccb->p_lcb->remote_bd_addr, package_len);
      }
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper wants to disconnect */
      if (p_ccb->p_lcb->transport != BT_TRANSPORT_LE) {
        /* Make sure we are not in sniff mode */
        if (!BTM_SetLinkPolicyActiveMode(p_ccb->p_lcb->remote_bd_addr)) {
          log::warn("Unable to set link policy active");
        }
      }
      power_telemetry::GetInstance().LogChannelDisconnected(
          p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
          p_ccb->p_lcb->remote_bd_addr);
      if (p_ccb->p_lcb->transport == BT_TRANSPORT_LE)
        l2cble_send_peer_disc_req(p_ccb);
      else
        l2cu_send_peer_disc_req(p_ccb);

      p_ccb->chnl_state = CST_W4_L2CAP_DISCONNECT_RSP;
      alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_DISCONNECT_TIMEOUT_MS,
                         l2c_ccb_timer_timeout, p_ccb);
      break;

    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
      if (p_data) {
        uint16_t package_len = ((BT_HDR*)p_data)->len;
        l2c_enqueue_peer_data(p_ccb, (BT_HDR*)p_data);
        l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);
        power_telemetry::GetInstance().LogTxBytes(
            p_ccb->p_rcb->psm, p_ccb->local_cid, p_ccb->remote_id,
            p_ccb->p_lcb->remote_bd_addr, package_len);
      }
      break;

    case L2CEVT_L2CA_CREDIT_BASED_RECONFIG_REQ:
      p_ccb->chnl_state = CST_CONFIG;
      p_ccb->config_done &= ~OB_CFG_DONE;

      if (p_data) {
        l2cu_send_credit_based_reconfig_req(p_ccb, (tL2CAP_LE_CFG_INFO*)p_data);

        alarm_set_on_mloop(p_ccb->l2c_ccb_timer, L2CAP_CHNL_CFG_TIMEOUT_MS,
                           l2c_ccb_timer_timeout, p_ccb);
      }
      break;

    case L2CEVT_L2CA_CONFIG_REQ: /* Upper layer config req   */
      log::error(
          "Dropping L2CAP re-config request because there is no usage and "
          "should not be invoked");
      break;

    case L2CEVT_TIMEOUT:
      /* Process the monitor/retransmission time-outs in flow control/retrans
       * mode */
      if (p_ccb->peer_cfg.fcr.mode == L2CAP_FCR_ERTM_MODE)
        l2c_fcr_proc_tout(p_ccb);
      break;

    case L2CEVT_ACK_TIMEOUT:
      l2c_fcr_proc_ack_tout(p_ccb);
      break;

    case L2CEVT_L2CA_SEND_FLOW_CONTROL_CREDIT:
      if (p_data) {
        log::debug("Sending credit");
        credit = *(uint16_t*)p_data;
        l2cble_send_flow_control_credit(p_ccb, credit);
      }
      break;

    case L2CEVT_L2CAP_RECV_FLOW_CONTROL_CREDIT:
      if (p_data) {
        credit = *(uint16_t*)p_data;
        log::debug("Credits received {}", credit);
        if ((p_ccb->peer_conn_cfg.credits + credit) > L2CAP_LE_CREDIT_MAX) {
          /* we have received credits more than max coc credits,
           * so disconnecting the Le Coc Channel
           */
          l2cble_send_peer_disc_req(p_ccb);
        } else {
          p_ccb->peer_conn_cfg.credits += credit;
          l2c_link_check_send_pkts(p_ccb->p_lcb, 0, NULL);
        }
      }
      break;
    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_w4_l2cap_disconnect_rsp
 *
 * Description      This function handles events when the channel is in
 *                  CST_W4_L2CAP_DISCONNECT_RSP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_w4_l2cap_disconnect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                            void* p_data) {
  tL2CA_DISCONNECT_CFM_CB* disconnect_cfm =
      p_ccb->p_rcb->api.pL2CA_DisconnectCfm_Cb;
  uint16_t local_cid = p_ccb->local_cid;

  log::debug("LCID: 0x{:04x}  st: W4_L2CAP_DISC_RSP  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_L2CAP_DISCONNECT_RSP: /* Peer disconnect response */
      l2cu_release_ccb(p_ccb);
      if (disconnect_cfm != nullptr) {
        (*disconnect_cfm)(local_cid, L2CAP_DISC_OK);
      }
      break;

    case L2CEVT_L2CAP_DISCONNECT_REQ: /* Peer disconnect request  */
      l2cu_send_peer_disc_rsp(p_ccb->p_lcb, p_ccb->remote_id, p_ccb->local_cid,
                              p_ccb->remote_cid);
      l2cu_release_ccb(p_ccb);
      if (disconnect_cfm != nullptr) {
        (*disconnect_cfm)(local_cid, L2CAP_DISC_OK);
      }
      break;

    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
    case L2CEVT_TIMEOUT:           /* Timeout */
      l2cu_release_ccb(p_ccb);
      if (disconnect_cfm != nullptr) {
        (*disconnect_cfm)(local_cid, L2CAP_DISC_TIMEOUT);
      }

      break;

    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
      osi_free(p_data);
      break;
    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_w4_l2ca_disconnect_rsp
 *
 * Description      This function handles events when the channel is in
 *                  CST_W4_L2CA_DISCONNECT_RSP state.
 *
 * Returns          void
 *
 ******************************************************************************/
static void l2c_csm_w4_l2ca_disconnect_rsp(tL2C_CCB* p_ccb, tL2CEVT event,
                                           void* p_data) {
  tL2CA_DISCONNECT_IND_CB* disconnect_ind =
      p_ccb->p_rcb->api.pL2CA_DisconnectInd_Cb;
  uint16_t local_cid = p_ccb->local_cid;

  log::debug("LCID: 0x{:04x}  st: W4_L2CA_DISC_RSP  evt: {}", p_ccb->local_cid,
             l2c_csm_get_event_name(event));

  switch (event) {
    case L2CEVT_LP_DISCONNECT_IND: /* Link was disconnected */
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_TIMEOUT:
      l2cu_send_peer_disc_rsp(p_ccb->p_lcb, p_ccb->remote_id, p_ccb->local_cid,
                              p_ccb->remote_cid);
      log::debug("Calling Disconnect_Ind_Cb(), CID: 0x{:04x}  No Conf Needed",
                 p_ccb->local_cid);
      l2cu_release_ccb(p_ccb);
      (*disconnect_ind)(local_cid, false);
      break;

    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper disconnect request */
    case L2CEVT_L2CA_DISCONNECT_RSP: /* Upper disconnect response */
      l2cu_send_peer_disc_rsp(p_ccb->p_lcb, p_ccb->remote_id, p_ccb->local_cid,
                              p_ccb->remote_cid);
      l2cu_release_ccb(p_ccb);
      break;

    case L2CEVT_L2CAP_DATA:      /* Peer data packet rcvd    */
    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data to send */
      osi_free(p_data);
      break;
    default:
      log::error("Handling unexpected event:{}", l2c_csm_get_event_name(event));
  }
  log::verbose("Exit chnl_state={} [{}], event={} [{}]",
               channel_state_text(p_ccb->chnl_state), p_ccb->chnl_state,
               l2c_csm_get_event_name(event), event);
}

/*******************************************************************************
 *
 * Function         l2c_csm_get_event_name
 *
 * Description      This function returns the event name.
 *
 * NOTE             conditionally compiled to save memory.
 *
 * Returns          pointer to the name
 *
 ******************************************************************************/
static const char* l2c_csm_get_event_name(tL2CEVT event) {
  switch (event) {
    case L2CEVT_LP_CONNECT_CFM: /* Lower layer connect confirm          */
      return ("LOWER_LAYER_CONNECT_CFM");
    case L2CEVT_LP_CONNECT_CFM_NEG: /* Lower layer connect confirm (failed) */
      return ("LOWER_LAYER_CONNECT_CFM_NEG");
    case L2CEVT_LP_CONNECT_IND: /* Lower layer connect indication       */
      return ("LOWER_LAYER_CONNECT_IND");
    case L2CEVT_LP_DISCONNECT_IND: /* Lower layer disconnect indication    */
      return ("LOWER_LAYER_DISCONNECT_IND");

    case L2CEVT_SEC_COMP: /* Security cleared successfully        */
      return ("SECURITY_COMPLETE");
    case L2CEVT_SEC_COMP_NEG: /* Security procedure failed            */
      return ("SECURITY_COMPLETE_NEG");

    case L2CEVT_L2CAP_CONNECT_REQ: /* Peer connection request              */
      return ("PEER_CONNECT_REQ");
    case L2CEVT_L2CAP_CONNECT_RSP: /* Peer connection response             */
      return ("PEER_CONNECT_RSP");
    case L2CEVT_L2CAP_CONNECT_RSP_PND: /* Peer connection response pending */
      return ("PEER_CONNECT_RSP_PND");
    case L2CEVT_L2CAP_CONNECT_RSP_NEG: /* Peer connection response (failed) */
      return ("PEER_CONNECT_RSP_NEG");
    case L2CEVT_L2CAP_CONFIG_REQ: /* Peer configuration request           */
      return ("PEER_CONFIG_REQ");
    case L2CEVT_L2CAP_CONFIG_RSP: /* Peer configuration response          */
      return ("PEER_CONFIG_RSP");
    case L2CEVT_L2CAP_CONFIG_RSP_NEG: /* Peer configuration response (failed) */
      return ("PEER_CONFIG_RSP_NEG");
    case L2CEVT_L2CAP_DISCONNECT_REQ: /* Peer disconnect request              */
      return ("PEER_DISCONNECT_REQ");
    case L2CEVT_L2CAP_DISCONNECT_RSP: /* Peer disconnect response             */
      return ("PEER_DISCONNECT_RSP");
    case L2CEVT_L2CAP_DATA: /* Peer data                            */
      return ("PEER_DATA");

    case L2CEVT_L2CA_CONNECT_REQ: /* Upper layer connect request          */
      return ("UPPER_LAYER_CONNECT_REQ");
    case L2CEVT_L2CA_CONNECT_RSP: /* Upper layer connect response         */
      return ("UPPER_LAYER_CONNECT_RSP");
    case L2CEVT_L2CA_CONNECT_RSP_NEG: /* Upper layer connect response (failed)*/
      return ("UPPER_LAYER_CONNECT_RSP_NEG");
    case L2CEVT_L2CA_CONFIG_REQ: /* Upper layer config request           */
      return ("UPPER_LAYER_CONFIG_REQ");
    case L2CEVT_L2CA_CONFIG_RSP: /* Upper layer config response          */
      return ("UPPER_LAYER_CONFIG_RSP");
    case L2CEVT_L2CA_DISCONNECT_REQ: /* Upper layer disconnect request       */
      return ("UPPER_LAYER_DISCONNECT_REQ");
    case L2CEVT_L2CA_DISCONNECT_RSP: /* Upper layer disconnect response      */
      return ("UPPER_LAYER_DISCONNECT_RSP");
    case L2CEVT_L2CA_DATA_READ: /* Upper layer data read                */
      return ("UPPER_LAYER_DATA_READ");
    case L2CEVT_L2CA_DATA_WRITE: /* Upper layer data write               */
      return ("UPPER_LAYER_DATA_WRITE");
    case L2CEVT_TIMEOUT: /* Timeout                              */
      return ("TIMEOUT");
    case L2CEVT_SEC_RE_SEND_CMD:
      return ("SEC_RE_SEND_CMD");
    case L2CEVT_L2CAP_INFO_RSP: /* Peer information response            */
      return ("L2CEVT_L2CAP_INFO_RSP");
    case L2CEVT_ACK_TIMEOUT:
      return ("L2CEVT_ACK_TIMEOUT");
    case L2CEVT_L2CA_SEND_FLOW_CONTROL_CREDIT: /* Upper layer send credit packet
                                                */
      return ("SEND_FLOW_CONTROL_CREDIT");
    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_REQ: /* Upper layer credit based
                                                  connect request */
      return ("SEND_CREDIT_BASED_CONNECT_REQ");
    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP: /* Upper layer credit based
                                                  connect response */
      return ("SEND_CREDIT_BASED_CONNECT_RSP");
    case L2CEVT_L2CA_CREDIT_BASED_CONNECT_RSP_NEG: /* Upper layer credit based
                                                      connect response
                                                      (failed)*/
      return ("SEND_CREDIT_BASED_CONNECT_RSP_NEG");
    case L2CEVT_L2CA_CREDIT_BASED_RECONFIG_REQ: /* Upper layer credit based
                                                   reconfig request */
      return ("SEND_CREDIT_BASED_RECONFIG_REQ");
    case L2CEVT_L2CAP_RECV_FLOW_CONTROL_CREDIT: /* Peer send credit packet */
      return ("RECV_FLOW_CONTROL_CREDIT");
    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_REQ: /* Peer send credit based
                                                   connect request */
      return ("RECV_CREDIT_BASED_CONNECT_REQ");
    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP: /* Peer send credit based
                                                   connect response */
      return ("RECV_CREDIT_BASED_CONNECT_RSP");
    case L2CEVT_L2CAP_CREDIT_BASED_CONNECT_RSP_NEG: /* Peer send reject credit
                                                       based connect response */
      return ("RECV_CREDIT_BASED_CONNECT_RSP_NEG");
    case L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_REQ: /* Peer send credit based
                                                    reconfig request */
      return ("RECV_CREDIT_BASED_RECONFIG_REQ");
    case L2CEVT_L2CAP_CREDIT_BASED_RECONFIG_RSP: /* Peer send credit based
                                                    reconfig response */
      return ("RECV_CREDIT_BASED_RECONFIG_RSP");
    default:
      return ("???? UNKNOWN EVENT");
  }
}

/*******************************************************************************
 *
 * Function         l2c_enqueue_peer_data
 *
 * Description      Enqueues data destined for the peer in the ccb. Handles
 *                  FCR segmentation and checks for congestion.
 *
 * Returns          void
 *
 ******************************************************************************/
void l2c_enqueue_peer_data(tL2C_CCB* p_ccb, BT_HDR* p_buf) {
  CHECK(p_ccb != nullptr);

  p_ccb->metrics.tx(p_buf->len);

  uint8_t* p;

  if (p_ccb->peer_cfg.fcr.mode != L2CAP_FCR_BASIC_MODE) {
    p_buf->event = 0;
  } else {
    /* Save the channel ID for faster counting */
    p_buf->event = p_ccb->local_cid;

    /* Step back to add the L2CAP header */
    p_buf->offset -= L2CAP_PKT_OVERHEAD;
    p_buf->len += L2CAP_PKT_OVERHEAD;

    /* Set the pointer to the beginning of the data */
    p = (uint8_t*)(p_buf + 1) + p_buf->offset;

    /* Now the L2CAP header */
    UINT16_TO_STREAM(p, p_buf->len - L2CAP_PKT_OVERHEAD);
    UINT16_TO_STREAM(p, p_ccb->remote_cid);
  }

  if (p_ccb->xmit_hold_q == NULL) {
    log::error(
        "empty queue: p_ccb = {} p_ccb->in_use = {} p_ccb->chnl_state = {} "
        "p_ccb->local_cid = {} p_ccb->remote_cid = {}",
        fmt::ptr(p_ccb), p_ccb->in_use, p_ccb->chnl_state, p_ccb->local_cid,
        p_ccb->remote_cid);
  } else {
    fixed_queue_enqueue(p_ccb->xmit_hold_q, p_buf);
  }

  l2cu_check_channel_congestion(p_ccb);

  /* if new packet is higher priority than serving ccb and it is not overrun */
  if ((p_ccb->p_lcb->rr_pri > p_ccb->ccb_priority) &&
      (p_ccb->p_lcb->rr_serv[p_ccb->ccb_priority].quota > 0)) {
    /* send out higher priority packet */
    p_ccb->p_lcb->rr_pri = p_ccb->ccb_priority;
  }

  /* if we are doing a round robin scheduling, set the flag */
  if (p_ccb->p_lcb->link_xmit_quota == 0) l2cb.check_round_robin = true;
}
