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
 *  This file contains state machine and action routines for multiplexer
 *  channel of the RFCOMM unit
 *
 ******************************************************************************/

#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "include/check.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/l2c_api.h"
#include "stack/rfcomm/port_int.h"
#include "stack/rfcomm/rfc_int.h"

#define L2CAP_SUCCESS 0
#define L2CAP_ERROR 1

using namespace bluetooth;

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static void rfc_mx_sm_state_idle(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                 void* p_data);
static void rfc_mx_sm_state_wait_conn_cnf(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                          void* p_data);
static void rfc_mx_sm_state_configure(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                      void* p_data);
static void rfc_mx_sm_sabme_wait_ua(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                    void* p_data);
static void rfc_mx_sm_state_wait_sabme(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                       void* p_data);
static void rfc_mx_sm_state_connected(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                      UNUSED_ATTR void* p_data);
static void rfc_mx_sm_state_disc_wait_ua(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                         void* p_data);

static void rfc_mx_conf_ind(tRFC_MCB* p_mcb, tL2CAP_CFG_INFO* p_cfg);
static void rfc_mx_conf_cnf(tRFC_MCB* p_mcb, uint16_t result);

/*******************************************************************************
 *
 * Function         rfc_mx_sm_execute
 *
 * Description      This function sends multiplexer events through the state
 *                  machine.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_execute(tRFC_MCB* p_mcb, tRFC_MX_EVENT event, void* p_data) {
  CHECK(p_mcb != nullptr) << __func__ << ": NULL mcb for event " << event;

  log::info("RFCOMM peer:{} event:{} state:{}",
            ADDRESS_TO_LOGGABLE_CSTR(p_mcb->bd_addr), event,
            rfcomm_mx_state_text(static_cast<tRFC_MX_STATE>(p_mcb->state)));

  switch (p_mcb->state) {
    case RFC_MX_STATE_IDLE:
      rfc_mx_sm_state_idle(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_WAIT_CONN_CNF:
      rfc_mx_sm_state_wait_conn_cnf(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_CONFIGURE:
      rfc_mx_sm_state_configure(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_SABME_WAIT_UA:
      rfc_mx_sm_sabme_wait_ua(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_WAIT_SABME:
      rfc_mx_sm_state_wait_sabme(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_CONNECTED:
      rfc_mx_sm_state_connected(p_mcb, event, p_data);
      break;

    case RFC_MX_STATE_DISC_WAIT_UA:
      rfc_mx_sm_state_disc_wait_ua(p_mcb, event, p_data);
      break;

    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_idle
 *
 * Description      This function handles events when the multiplexer is in
 *                  IDLE state. This state exists when connection is being
 *                  initially established.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_idle(tRFC_MCB* p_mcb, tRFC_MX_EVENT event, void* p_data) {
  switch (event) {
    case RFC_MX_EVENT_START_REQ: {
      /* Initialize L2CAP MTU */
      p_mcb->peer_l2cap_mtu = L2CAP_DEFAULT_MTU - RFCOMM_MIN_OFFSET - 1;

      uint16_t lcid = L2CA_ConnectReq(BT_PSM_RFCOMM, p_mcb->bd_addr);
      if (lcid == 0) {
        log::error("failed to open L2CAP channel for {}",
                   ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr));
        rfc_save_lcid_mcb(nullptr, p_mcb->lcid);
        p_mcb->lcid = 0;
        PORT_StartCnf(p_mcb, RFCOMM_ERROR);
        return;
      }
      p_mcb->lcid = lcid;
      /* Save entry for quicker access to mcb based on the LCID */
      rfc_save_lcid_mcb(p_mcb, p_mcb->lcid);

      p_mcb->state = RFC_MX_STATE_WAIT_CONN_CNF;
      return;
    }

    case RFC_MX_EVENT_CONN_IND:

      rfc_timer_start(p_mcb, RFCOMM_CONN_TIMEOUT);

      p_mcb->state = RFC_MX_STATE_CONFIGURE;
      return;

    case RFC_MX_EVENT_SABME:
      break;

    case RFC_MX_EVENT_UA:
    case RFC_MX_EVENT_DM:
      return;

    case RFC_MX_EVENT_DISC:
      rfc_send_dm(p_mcb, RFCOMM_MX_DLCI, true);
      return;

    case RFC_MX_EVENT_UIH:
      rfc_send_dm(p_mcb, RFCOMM_MX_DLCI, false);
      return;

    default:
      log::error("Mx error state {} event {}", p_mcb->state, event);
      return;
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_wait_conn_cnf
 *
 * Description      This function handles events when the multiplexer is
 *                  waiting for Connection Confirm from L2CAP.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_wait_conn_cnf(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                   void* p_data) {
  log::verbose("evt {}", event);
  switch (event) {
    case RFC_MX_EVENT_START_REQ:
      log::error("Mx error state {} event {}", p_mcb->state, event);
      return;

    /* There is some new timing so that Config Ind comes before security is
       completed
       so we are still waiting fo the confirmation. */
    case RFC_MX_EVENT_CONF_IND:
      rfc_mx_conf_ind(p_mcb, (tL2CAP_CFG_INFO*)p_data);
      return;

    case RFC_MX_EVENT_CONN_CNF:
      if (*((uint16_t*)p_data) != L2CAP_SUCCESS) {
        p_mcb->state = RFC_MX_STATE_IDLE;

        PORT_StartCnf(p_mcb, *((uint16_t*)p_data));
        return;
      }
      p_mcb->state = RFC_MX_STATE_CONFIGURE;
      return;

    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_TIMEOUT:
      p_mcb->state = RFC_MX_STATE_IDLE;
      L2CA_DisconnectReq(p_mcb->lcid);

      /* we gave up outgoing connection request then try peer's request */
      if (p_mcb->pending_lcid) {
        uint16_t i;
        uint8_t handle;

        log::verbose(
            "RFCOMM MX retry as acceptor in collision case - evt:{} in "
            "state:{}",
            event, p_mcb->state);

        rfc_save_lcid_mcb(NULL, p_mcb->lcid);
        p_mcb->lcid = p_mcb->pending_lcid;
        rfc_save_lcid_mcb(p_mcb, p_mcb->lcid);

        p_mcb->is_initiator = false;

        /* update direction bit */
        for (i = 0; i < RFCOMM_MAX_DLCI; i += 2) {
          handle = p_mcb->port_handles[i];
          if (handle != 0) {
            p_mcb->port_handles[i] = 0;
            p_mcb->port_handles[i + 1] = handle;
            rfc_cb.port.port[handle - 1].dlci += 1;
            log::verbose("RFCOMM MX - DLCI:{} -> {}", i,
                         rfc_cb.port.port[handle - 1].dlci);
          }
        }

        rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONN_IND, nullptr);
      } else {
        PORT_CloseInd(p_mcb);
      }
      return;
    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_configure
 *
 * Description      This function handles events when the multiplexer in the
 *                  configuration state.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_configure(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                               void* p_data) {
  log::verbose("event {}", event);
  switch (event) {
    case RFC_MX_EVENT_START_REQ:
    case RFC_MX_EVENT_CONN_CNF:

      log::error("Mx error state {} event {}", p_mcb->state, event);
      return;

    case RFC_MX_EVENT_CONF_IND:
      rfc_mx_conf_ind(p_mcb, (tL2CAP_CFG_INFO*)p_data);
      return;

    case RFC_MX_EVENT_CONF_CNF:
      rfc_mx_conf_cnf(p_mcb, (uintptr_t)p_data);
      return;

    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_TIMEOUT:
      log::error("L2CAP configuration timeout for {}",
                 ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr));
      p_mcb->state = RFC_MX_STATE_IDLE;
      L2CA_DisconnectReq(p_mcb->lcid);

      PORT_StartCnf(p_mcb, RFCOMM_ERROR);
      return;
    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_sabme_wait_ua
 *
 * Description      This function handles events when the multiplexer sent
 *                  SABME and is waiting for UA reply.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_sabme_wait_ua(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                             UNUSED_ATTR void* p_data) {
  log::verbose("event {}", event);
  switch (event) {
    case RFC_MX_EVENT_START_REQ:
    case RFC_MX_EVENT_CONN_CNF:
      log::error("Mx error state {} event {}", p_mcb->state, event);
      return;

    /* workaround: we don't support reconfig */
    /* commented out until we support reconfig
    case RFC_MX_EVENT_CONF_IND:
        rfc_mx_conf_ind (p_mcb, (tL2CAP_CFG_INFO *)p_data);
        return;

    case RFC_MX_EVENT_CONF_CNF:
        rfc_mx_conf_cnf (p_mcb, (tL2CAP_CFG_INFO *)p_data);
        return;
    */

    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_UA:
      rfc_timer_stop(p_mcb);

      p_mcb->state = RFC_MX_STATE_CONNECTED;
      p_mcb->peer_ready = true;

      PORT_StartCnf(p_mcb, RFCOMM_SUCCESS);
      return;

    case RFC_MX_EVENT_DM:
      rfc_timer_stop(p_mcb);
      FALLTHROUGH_INTENDED; /* FALLTHROUGH */

    case RFC_MX_EVENT_CONF_IND: /* workaround: we don't support reconfig */
    case RFC_MX_EVENT_CONF_CNF: /* workaround: we don't support reconfig */
    case RFC_MX_EVENT_TIMEOUT:
      p_mcb->state = RFC_MX_STATE_IDLE;
      L2CA_DisconnectReq(p_mcb->lcid);

      PORT_StartCnf(p_mcb, RFCOMM_ERROR);
      return;
    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_wait_sabme
 *
 * Description      This function handles events when the multiplexer is
 *                  waiting for SABME on the acceptor side after configuration
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_wait_sabme(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                void* p_data) {
  log::verbose("event {}", event);
  switch (event) {
    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_SABME:
      /* if we gave up outgoing connection request */
      if (p_mcb->pending_lcid) {
        p_mcb->pending_lcid = 0;

        rfc_send_ua(p_mcb, RFCOMM_MX_DLCI);

        rfc_timer_stop(p_mcb);
        p_mcb->state = RFC_MX_STATE_CONNECTED;
        p_mcb->peer_ready = true;

        /* MX channel collision has been resolved, continue to open ports */
        PORT_StartCnf(p_mcb, RFCOMM_SUCCESS);
      } else {
        rfc_timer_stop(p_mcb);
        PORT_StartInd(p_mcb);
      }
      return;

    case RFC_MX_EVENT_START_RSP:
      if (*((uint16_t*)p_data) != RFCOMM_SUCCESS)
        rfc_send_dm(p_mcb, RFCOMM_MX_DLCI, true);
      else {
        rfc_send_ua(p_mcb, RFCOMM_MX_DLCI);

        p_mcb->state = RFC_MX_STATE_CONNECTED;
        p_mcb->peer_ready = true;
        PORT_StartCnf(p_mcb, RFCOMM_SUCCESS);
      }
      return;

    case RFC_MX_EVENT_CONF_IND: /* workaround: we don't support reconfig */
    case RFC_MX_EVENT_CONF_CNF: /* workaround: we don't support reconfig */
    case RFC_MX_EVENT_TIMEOUT:
      p_mcb->state = RFC_MX_STATE_IDLE;
      L2CA_DisconnectReq(p_mcb->lcid);

      PORT_CloseInd(p_mcb);
      return;

    default:
      log::warn("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_connected
 *
 * Description      This function handles events when the multiplexer is
 *                  in the CONNECTED state
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_connected(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                               UNUSED_ATTR void* p_data) {
  log::verbose("event {}", event);

  switch (event) {
    case RFC_MX_EVENT_TIMEOUT:
    case RFC_MX_EVENT_CLOSE_REQ:
      rfc_timer_start(p_mcb, RFC_DISC_TIMEOUT);
      p_mcb->state = RFC_MX_STATE_DISC_WAIT_UA;
      rfc_send_disc(p_mcb, RFCOMM_MX_DLCI);
      return;

    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_DISC:
      /* Reply with UA.  If initiator bring down L2CAP connection */
      /* If server wait for some time if client decide to reinitiate channel */
      rfc_send_ua(p_mcb, RFCOMM_MX_DLCI);
      if (p_mcb->is_initiator) {
        L2CA_DisconnectReq(p_mcb->lcid);
      }
      /* notify all ports that connection is gone */
      PORT_CloseInd(p_mcb);
      return;
    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

/*******************************************************************************
 *
 * Function         rfc_mx_sm_state_disc_wait_ua
 *
 * Description      This function handles events when the multiplexer sent
 *                  DISC and is waiting for UA reply.
 *
 * Returns          void
 *
 ******************************************************************************/
void rfc_mx_sm_state_disc_wait_ua(tRFC_MCB* p_mcb, tRFC_MX_EVENT event,
                                  void* p_data) {
  BT_HDR* p_buf;

  log::verbose("event {}", event);
  switch (event) {
    case RFC_MX_EVENT_UA:
    case RFC_MX_EVENT_DM:
    case RFC_MX_EVENT_TIMEOUT:
      L2CA_DisconnectReq(p_mcb->lcid);

      if (p_mcb->restart_required) {
        /* Start Request was received while disconnecting.  Execute it again */
        uint16_t lcid = L2CA_ConnectReq(BT_PSM_RFCOMM, p_mcb->bd_addr);
        if (lcid == 0) {
          rfc_save_lcid_mcb(NULL, p_mcb->lcid);
          p_mcb->lcid = 0;
          PORT_StartCnf(p_mcb, RFCOMM_ERROR);
          return;
        }
        p_mcb->lcid = lcid;
        /* Save entry for quicker access to mcb based on the LCID */
        rfc_save_lcid_mcb(p_mcb, p_mcb->lcid);

        /* clean up before reuse it */
        while ((p_buf = (BT_HDR*)fixed_queue_try_dequeue(p_mcb->cmd_q)) != NULL)
          osi_free(p_buf);

        rfc_timer_start(p_mcb, RFC_MCB_INIT_INACT_TIMER);

        p_mcb->is_initiator = true;
        p_mcb->restart_required = false;

        p_mcb->state = RFC_MX_STATE_WAIT_CONN_CNF;
        return;
      }
      rfc_release_multiplexer_channel(p_mcb);
      return;

    case RFC_MX_EVENT_DISC:
      rfc_send_ua(p_mcb, RFCOMM_MX_DLCI);
      return;

    case RFC_MX_EVENT_UIH:
      osi_free(p_data);
      rfc_send_dm(p_mcb, RFCOMM_MX_DLCI, false);
      return;

    case RFC_MX_EVENT_START_REQ:
      p_mcb->restart_required = true;
      return;

    case RFC_MX_EVENT_DISC_IND:
      p_mcb->state = RFC_MX_STATE_IDLE;
      PORT_CloseInd(p_mcb);
      return;

    case RFC_MX_EVENT_CLOSE_REQ:
      return;

    case RFC_MX_EVENT_QOS_VIOLATION_IND:
      break;
    default:
      log::error("Received unexpected event:{} in state:{}", event,
                 p_mcb->state);
  }
  log::verbose("RFCOMM MX ignored - evt:{} in state:{}", event, p_mcb->state);
}

void rfc_on_l2cap_error(uint16_t lcid, uint16_t result) {
  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);
  if (p_mcb == nullptr) return;

  if (result & L2CAP_CONN_INTERNAL_MASK) {
    /* if peer rejects our connect request but peer's connect request is pending
     */
    if (p_mcb->pending_lcid) {
      log::verbose(
          "RFCOMM_ConnectCnf retry as acceptor on pending LCID(0x{:x})",
          p_mcb->pending_lcid);

      /* remove mcb from mapping table */
      rfc_save_lcid_mcb(NULL, p_mcb->lcid);

      p_mcb->lcid = p_mcb->pending_lcid;
      p_mcb->is_initiator = false;
      p_mcb->state = RFC_MX_STATE_IDLE;

      /* store mcb into mapping table */
      rfc_save_lcid_mcb(p_mcb, p_mcb->lcid);

      /* update direction bit */
      for (int i = 0; i < RFCOMM_MAX_DLCI; i += 2) {
        uint8_t handle = p_mcb->port_handles[i];
        if (handle != 0) {
          p_mcb->port_handles[i] = 0;
          p_mcb->port_handles[i + 1] = handle;
          rfc_cb.port.port[handle - 1].dlci += 1;
          log::verbose("RFCOMM MX, port_handle={}, DLCI[{}->{}]", handle, i,
                       rfc_cb.port.port[handle - 1].dlci);
        }
      }
      rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONN_IND, nullptr);
      if (p_mcb->pending_configure_complete) {
        log::info("Configuration of the pending connection was completed");
        p_mcb->pending_configure_complete = false;
        uintptr_t result_as_ptr = L2CAP_CFG_OK;
        rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONF_IND,
                          &p_mcb->pending_cfg_info);
        rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONF_CNF, (void*)result_as_ptr);
      }
      return;
    }

    p_mcb->lcid = lcid;
    rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONN_CNF, &result);
  } else if (result == L2CAP_CFG_FAILED_NO_REASON) {
    log::error("failed to configure L2CAP for {}",
               ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr));
    if (p_mcb->is_initiator) {
      log::error("disconnect L2CAP due to config failure for {}",
                 ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr));
      PORT_StartCnf(p_mcb, result);
      L2CA_DisconnectReq(p_mcb->lcid);
    }
    rfc_release_multiplexer_channel(p_mcb);
  }
}

/*******************************************************************************
 *
 * Function         rfc_mx_conf_cnf
 *
 * Description      This function handles L2CA_ConfigCnf message from the
 *                  L2CAP.  If result is not success tell upper layer that
 *                  start has not been accepted.  If initiator send SABME
 *                  on DLCI 0.  T1 is still running.
 *
 ******************************************************************************/
static void rfc_mx_conf_cnf(tRFC_MCB* p_mcb, uint16_t result) {
  if (p_mcb->state == RFC_MX_STATE_CONFIGURE) {
    if (p_mcb->is_initiator) {
      p_mcb->state = RFC_MX_STATE_SABME_WAIT_UA;
      rfc_send_sabme(p_mcb, RFCOMM_MX_DLCI);
      rfc_timer_start(p_mcb, RFC_T1_TIMEOUT);
    } else {
      p_mcb->state = RFC_MX_STATE_WAIT_SABME;
      rfc_timer_start(
          p_mcb, RFCOMM_CONN_TIMEOUT); /* - increased from T2=20 to CONN=120
                             to allow the user more than 10 sec to type in the
                             pin which can be e.g. 16 digits */
    }
  }
}

/*******************************************************************************
 *
 * Function         rfc_mx_conf_ind
 *
 * Description      This function handles L2CA_ConfigInd message from the
 *                  L2CAP.  Send the L2CA_ConfigRsp message.
 *
 ******************************************************************************/
static void rfc_mx_conf_ind(tRFC_MCB* p_mcb, tL2CAP_CFG_INFO* p_cfg) {
  /* Save peer L2CAP MTU if present */
  /* RFCOMM adds 3-4 bytes in the beginning and 1 bytes FCS */
  if (p_cfg->mtu_present) {
    p_mcb->peer_l2cap_mtu = p_cfg->mtu - RFCOMM_MIN_OFFSET - 1;
  } else {
    p_mcb->peer_l2cap_mtu = L2CAP_DEFAULT_MTU - RFCOMM_MIN_OFFSET - 1;
  }
}
