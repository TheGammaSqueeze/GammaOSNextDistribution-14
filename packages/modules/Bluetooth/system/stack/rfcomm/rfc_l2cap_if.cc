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
 *  This file contains L2CAP interface functions
 *
 ******************************************************************************/

#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstddef>
#include <cstdint>

#include "common/time_util.h"
#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/l2c_api.h"
#include "stack/rfcomm/port_int.h"
#include "stack/rfcomm/rfc_int.h"
#include "types/raw_address.h"

using namespace bluetooth;

/*
 * Define Callback functions to be called by L2CAP
*/
static void RFCOMM_ConnectInd(const RawAddress& bd_addr, uint16_t lcid,
                              uint16_t psm, uint8_t id);
static void RFCOMM_ConnectCnf(uint16_t lcid, uint16_t err);
static void RFCOMM_ConfigInd(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg);
static void RFCOMM_ConfigCnf(uint16_t lcid, uint16_t result,
                             tL2CAP_CFG_INFO* p_cfg);
static void RFCOMM_DisconnectInd(uint16_t lcid, bool is_clear);
static void RFCOMM_BufDataInd(uint16_t lcid, BT_HDR* p_buf);
static void RFCOMM_CongestionStatusInd(uint16_t lcid, bool is_congested);

/*******************************************************************************
 *
 * Function         rfcomm_l2cap_if_init
 *
 * Description      This function is called during the RFCOMM task startup
 *                  to register interface functions with L2CAP.
 *
 ******************************************************************************/
void rfcomm_l2cap_if_init(void) {
  tL2CAP_APPL_INFO* p_l2c = &rfc_cb.rfc.reg_info;

  p_l2c->pL2CA_ConnectInd_Cb = RFCOMM_ConnectInd;
  p_l2c->pL2CA_ConnectCfm_Cb = RFCOMM_ConnectCnf;
  p_l2c->pL2CA_ConfigInd_Cb = RFCOMM_ConfigInd;
  p_l2c->pL2CA_ConfigCfm_Cb = RFCOMM_ConfigCnf;
  p_l2c->pL2CA_DisconnectInd_Cb = RFCOMM_DisconnectInd;
  p_l2c->pL2CA_DataInd_Cb = RFCOMM_BufDataInd;
  p_l2c->pL2CA_CongestionStatus_Cb = RFCOMM_CongestionStatusInd;
  p_l2c->pL2CA_TxComplete_Cb = NULL;
  p_l2c->pL2CA_Error_Cb = rfc_on_l2cap_error;

  L2CA_Register(BT_PSM_RFCOMM, rfc_cb.rfc.reg_info, true /* enable_snoop */,
                nullptr, L2CAP_MTU_SIZE, 0, 0);
}

/*******************************************************************************
 *
 * Function         RFCOMM_ConnectInd
 *
 * Description      This is a callback function called by L2CAP when
 *                  L2CA_ConnectInd received.  Allocate multiplexer control
 *                  block and dispatch the event to it.
 *
 ******************************************************************************/
void RFCOMM_ConnectInd(const RawAddress& bd_addr, uint16_t lcid,
                       UNUSED_ATTR uint16_t psm, uint8_t id) {
  tRFC_MCB* p_mcb = rfc_alloc_multiplexer_channel(bd_addr, false);

  if ((p_mcb) && (p_mcb->state != RFC_MX_STATE_IDLE)) {
    /* if this is collision case */
    if ((p_mcb->is_initiator) && (p_mcb->state == RFC_MX_STATE_WAIT_CONN_CNF)) {
      p_mcb->pending_lcid = lcid;

      /* wait random timeout (2 - 12) to resolve collision */
      /* if peer gives up then local device rejects incoming connection and
       * continues as initiator */
      /* if timeout, local device disconnects outgoing connection and continues
       * as acceptor */
      log::verbose(
          "RFCOMM_ConnectInd start timer for collision, initiator's "
          "LCID(0x{:x}), acceptor's LCID(0x{:x})",
          p_mcb->lcid, p_mcb->pending_lcid);

      rfc_timer_start(
          p_mcb,
          (uint16_t)(bluetooth::common::time_get_os_boottime_ms() % 10 + 2));
      return;
    } else {
      /* we cannot accept connection request from peer at this state */
      /* don't update lcid */
      p_mcb = nullptr;
    }
  } else {
    /* store mcb even if null */
    rfc_save_lcid_mcb(p_mcb, lcid);
  }

  if (p_mcb == nullptr) {
    L2CA_DisconnectReq(lcid);
    return;
  }
  p_mcb->lcid = lcid;

  rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONN_IND, &id);
}

/*******************************************************************************
 *
 * Function         RFCOMM_ConnectCnf
 *
 * Description      This is a callback function called by L2CAP when
 *                  L2CA_ConnectCnf received.  Save L2CAP handle and dispatch
 *                  event to the FSM.
 *
 ******************************************************************************/
void RFCOMM_ConnectCnf(uint16_t lcid, uint16_t result) {
  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);

  if (!p_mcb) {
    log::error("RFCOMM_ConnectCnf LCID:0x{:x}", lcid);
    return;
  }

  if (p_mcb->pending_lcid) {
    /* if peer rejects our connect request but peer's connect request is pending
     */
    if (result != L2CAP_CONN_OK) {
      return;
    } else {
      log::verbose("RFCOMM_ConnectCnf peer gave up pending LCID(0x{:x})",
                   p_mcb->pending_lcid);

      /* Peer gave up its connection request, make sure cleaning up L2CAP
       * channel */
      L2CA_DisconnectReq(p_mcb->pending_lcid);

      p_mcb->pending_lcid = 0;
    }
  }

  /* Save LCID to be used in all consecutive calls to L2CAP */
  p_mcb->lcid = lcid;

  rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONN_CNF, &result);
}

/*******************************************************************************
 *
 * Function         RFCOMM_ConfigInd
 *
 * Description      This is a callback function called by L2CAP when
 *                  L2CA_ConfigInd received.  Save parameters in the control
 *                  block and dispatch event to the FSM.
 *
 ******************************************************************************/
void RFCOMM_ConfigInd(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg) {
  if (p_cfg == nullptr) {
    log::error("Received l2cap configuration info with nullptr");
    return;
  }

  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);

  if (!p_mcb) {
    log::error("RFCOMM_ConfigInd LCID:0x{:x}", lcid);
    for (auto& [cid, mcb] : rfc_lcid_mcb) {
      if (mcb != nullptr && mcb->pending_lcid == lcid) {
        tL2CAP_CFG_INFO l2cap_cfg_info(*p_cfg);
        mcb->pending_configure_complete = true;
        mcb->pending_cfg_info = l2cap_cfg_info;
        return;
      }
    }
    return;
  }

  rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONF_IND, (void*)p_cfg);
}

/*******************************************************************************
 *
 * Function         RFCOMM_ConfigCnf
 *
 * Description      This is a callback function called by L2CAP when
 *                  L2CA_ConfigCnf received.  Save L2CAP handle and dispatch
 *                  event to the FSM.
 *
 ******************************************************************************/
void RFCOMM_ConfigCnf(uint16_t lcid, UNUSED_ATTR uint16_t initiator,
                      tL2CAP_CFG_INFO* p_cfg) {
  RFCOMM_ConfigInd(lcid, p_cfg);

  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);

  if (!p_mcb) {
    log::error("RFCOMM_ConfigCnf no MCB LCID:0x{:x}", lcid);
    return;
  }
  uintptr_t result_as_ptr = L2CAP_CFG_OK;
  rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_CONF_CNF, (void*)result_as_ptr);
}

/*******************************************************************************
 *
 * Function         RFCOMM_DisconnectInd
 *
 * Description      This is a callback function called by L2CAP when
 *                  L2CA_DisconnectInd received.  Dispatch event to the FSM.
 *
 ******************************************************************************/
void RFCOMM_DisconnectInd(uint16_t lcid, bool is_conf_needed) {
  log::verbose("lcid:{}, is_conf_needed:{}", loghex(lcid), is_conf_needed);
  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);
  if (!p_mcb) {
    log::warn("no mcb for lcid {}", loghex(lcid));
    return;
  }
  rfc_mx_sm_execute(p_mcb, RFC_MX_EVENT_DISC_IND, nullptr);
}

/*******************************************************************************
 *
 * Function         RFCOMM_BufDataInd
 *
 * Description      This is a callback function called by L2CAP when
 *                  data RFCOMM frame is received.  Parse the frames, check
 *                  the checksum and dispatch event to multiplexer or port
 *                  state machine depending on the frame destination.
 *
 ******************************************************************************/
void RFCOMM_BufDataInd(uint16_t lcid, BT_HDR* p_buf) {
  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);

  if (!p_mcb) {
    log::warn("Cannot find RFCOMM multiplexer for lcid {}", loghex(lcid));
    osi_free(p_buf);
    return;
  }

  tRFC_EVENT event = rfc_parse_data(p_mcb, &rfc_cb.rfc.rx_frame, p_buf);

  /* If the frame did not pass validation just ignore it */
  if (event == RFC_EVENT_BAD_FRAME) {
    log::warn("Bad RFCOMM frame from lcid={}, bd_addr={}, p_mcb={}",
              loghex(lcid), ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr),
              fmt::ptr(p_mcb));
    osi_free(p_buf);
    return;
  }

  if (rfc_cb.rfc.rx_frame.dlci == RFCOMM_MX_DLCI) {
    log::verbose("handle multiplexer event {}, p_mcb={}", event,
                 fmt::ptr(p_mcb));
    /* Take special care of the Multiplexer Control Messages */
    if (event == RFC_EVENT_UIH) {
      rfc_process_mx_message(p_mcb, p_buf);
      return;
    }

    /* Other multiplexer events go to state machine */
    rfc_mx_sm_execute(p_mcb, static_cast<tRFC_MX_EVENT>(event), nullptr);
    osi_free(p_buf);
    return;
  }

  /* The frame was received on the data channel DLCI, verify that DLC exists */
  tPORT* p_port = port_find_mcb_dlci_port(p_mcb, rfc_cb.rfc.rx_frame.dlci);
  if (p_port == nullptr || !p_port->rfc.p_mcb) {
    /* If this is a SABME on new port, check if any app is waiting for it */
    if (event != RFC_EVENT_SABME) {
      log::warn("no for none-SABME event, lcid={}, bd_addr={}, p_mcb={}",
                loghex(lcid), ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr),
                fmt::ptr(p_mcb));
      if ((p_mcb->is_initiator && !rfc_cb.rfc.rx_frame.cr) ||
          (!p_mcb->is_initiator && rfc_cb.rfc.rx_frame.cr)) {
        log::error("Disconnecting RFCOMM, lcid={}, bd_addr={}, p_mcb={}",
                   loghex(lcid), ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr),
                   fmt::ptr(p_mcb));
        rfc_send_dm(p_mcb, rfc_cb.rfc.rx_frame.dlci, rfc_cb.rfc.rx_frame.pf);
      }
      osi_free(p_buf);
      return;
    }

    p_port = port_find_dlci_port(rfc_cb.rfc.rx_frame.dlci);
    if (p_port == nullptr) {
      log::error(
          "Disconnecting RFCOMM, no port for dlci {}, lcid={}, bd_addr={}, "
          "p_mcb={}",
          rfc_cb.rfc.rx_frame.dlci, loghex(lcid),
          ADDRESS_TO_LOGGABLE_STR(p_mcb->bd_addr), fmt::ptr(p_mcb));
      rfc_send_dm(p_mcb, rfc_cb.rfc.rx_frame.dlci, true);
      osi_free(p_buf);
      return;
    }
    log::verbose("port_handles[dlci={}]:{}->{}, p_mcb={}",
                 rfc_cb.rfc.rx_frame.dlci,
                 p_mcb->port_handles[rfc_cb.rfc.rx_frame.dlci], p_port->handle,
                 fmt::ptr(p_mcb));
    p_mcb->port_handles[rfc_cb.rfc.rx_frame.dlci] = p_port->handle;
    p_port->rfc.p_mcb = p_mcb;
  }

  if (event == RFC_EVENT_UIH) {
    log::verbose("Handling UIH event, buf_len={}, credit={}", p_buf->len,
                 rfc_cb.rfc.rx_frame.credit);
    if (p_buf->len > 0) {
      rfc_port_sm_execute(p_port, static_cast<tRFC_PORT_EVENT>(event), p_buf);
    } else {
      osi_free(p_buf);
    }

    if (rfc_cb.rfc.rx_frame.credit != 0) {
      rfc_inc_credit(p_port, rfc_cb.rfc.rx_frame.credit);
    }

    return;
  }
  rfc_port_sm_execute(p_port, static_cast<tRFC_PORT_EVENT>(event), nullptr);
  osi_free(p_buf);
}

/*******************************************************************************
 *
 * Function         RFCOMM_CongestionStatusInd
 *
 * Description      This is a callback function called by L2CAP when
 *                  data RFCOMM L2CAP congestion status changes
 *
 ******************************************************************************/
void RFCOMM_CongestionStatusInd(uint16_t lcid, bool is_congested) {
  tRFC_MCB* p_mcb = rfc_find_lcid_mcb(lcid);

  if (!p_mcb) {
    log::error("RFCOMM_CongestionStatusInd dropped LCID:0x{:x}", lcid);
    return;
  } else {
    log::verbose("RFCOMM_CongestionStatusInd LCID:0x{:x}", lcid);
  }
  rfc_process_l2cap_congestion(p_mcb, is_congested);
}

/*******************************************************************************
 *
 * Function         rfc_find_lcid_mcb
 *
 * Description      This function returns MCB block supporting local cid
 *
 ******************************************************************************/
tRFC_MCB* rfc_find_lcid_mcb(uint16_t lcid) {
  tRFC_MCB* p_mcb = rfc_lcid_mcb[lcid];
  if (p_mcb != nullptr) {
    if (p_mcb->lcid != lcid) {
      log::warn("LCID reused lcid=:{}, current_lcid={}", loghex(lcid),
                loghex(p_mcb->lcid));
      return nullptr;
    }
  }
  return p_mcb;
}

/*******************************************************************************
 *
 * Function         rfc_save_lcid_mcb
 *
 * Description      This function returns MCB block supporting local cid
 *
 ******************************************************************************/
void rfc_save_lcid_mcb(tRFC_MCB* p_mcb, uint16_t lcid) {
  auto mcb_index = static_cast<size_t>(lcid);
  rfc_lcid_mcb[mcb_index] = p_mcb;
}
