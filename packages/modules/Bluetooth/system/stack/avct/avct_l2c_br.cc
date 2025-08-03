/******************************************************************************
 *
 *  Copyright 2008-2016 Broadcom Corporation
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

/*****************************************************************************
 *
 *  Name:           avct_l2c_br.cc
 *
 *  Description:    This AVCTP module interfaces to L2CAP
 *
 *****************************************************************************/

#define LOG_TAG "avctp"

#include <bluetooth/log.h>

#include "avct_api.h"
#include "avct_int.h"
#include "internal_include/bt_target.h"
#include "l2c_api.h"
#include "l2cdefs.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

using namespace bluetooth;

/* callback function declarations */
void avct_l2c_br_connect_ind_cback(const RawAddress& bd_addr, uint16_t lcid,
                                   uint16_t psm, uint8_t id);
void avct_l2c_br_connect_cfm_cback(uint16_t lcid, uint16_t result);
void avct_l2c_br_config_cfm_cback(uint16_t lcid, uint16_t result,
                                  tL2CAP_CFG_INFO* p_cfg);
void avct_l2c_br_config_ind_cback(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg);
void avct_l2c_br_disconnect_ind_cback(uint16_t lcid, bool ack_needed);
void avct_l2c_br_congestion_ind_cback(uint16_t lcid, bool is_congested);
void avct_l2c_br_data_ind_cback(uint16_t lcid, BT_HDR* p_buf);
void avct_br_on_l2cap_error(uint16_t lcid, uint16_t result);

/* L2CAP callback function structure */
const tL2CAP_APPL_INFO avct_l2c_br_appl = {avct_l2c_br_connect_ind_cback,
                                           avct_l2c_br_connect_cfm_cback,
                                           avct_l2c_br_config_ind_cback,
                                           avct_l2c_br_config_cfm_cback,
                                           avct_l2c_br_disconnect_ind_cback,
                                           NULL,
                                           avct_l2c_br_data_ind_cback,
                                           avct_l2c_br_congestion_ind_cback,
                                           NULL,
                                           avct_br_on_l2cap_error,
                                           NULL,
                                           NULL,
                                           NULL,
                                           NULL};

/*******************************************************************************
 *
 * Function         avct_l2c_br_is_passive
 *
 * Description      check is the CCB associated with the given BCB was created
 *                  as passive
 *
 * Returns          true, if the given CCB is created as AVCT_PASSIVE
 *
 ******************************************************************************/
static bool avct_l2c_br_is_passive(tAVCT_BCB* p_bcb) {
  bool is_passive = false;
  tAVCT_LCB* p_lcb;
  tAVCT_CCB* p_ccb = &avct_cb.ccb[0];
  p_lcb = avct_lcb_by_bcb(p_bcb);
  int i;

  for (i = 0; i < AVCT_NUM_CONN; i++, p_ccb++) {
    if (p_ccb->allocated && (p_ccb->p_lcb == p_lcb)) {
      log::verbose("Is bcb associated ccb control passive :0x{:x}",
                   p_ccb->cc.control);
      if (p_ccb->cc.control & AVCT_PASSIVE) {
        is_passive = true;
        break;
      }
    }
  }
  return is_passive;
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_connect_ind_cback
 *
 * Description      This is the L2CAP connect indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_connect_ind_cback(const RawAddress& bd_addr, uint16_t lcid,
                                   UNUSED_ATTR uint16_t psm, uint8_t id) {
  tAVCT_LCB* p_lcb;
  uint16_t result = L2CAP_CONN_NO_RESOURCES;
  tAVCT_BCB* p_bcb;
  tL2CAP_ERTM_INFO ertm_info;

  p_lcb = avct_lcb_by_bd(bd_addr);
  if (p_lcb != NULL) {
    /* control channel exists */
    p_bcb = avct_bcb_by_lcb(p_lcb);
    p_bcb->peer_addr = bd_addr;

    if (p_bcb->allocated == 0) {
      /* browsing channel does not exist yet and the browsing channel is
       * registered
       * - accept connection */
      p_bcb->allocated = p_lcb->allocated; /* copy the index from lcb */

      result = L2CAP_CONN_OK;
    } else {
      if (!avct_l2c_br_is_passive(p_bcb) || (p_bcb->ch_state == AVCT_CH_OPEN)) {
        /* this BCB included CT role - reject */
        result = L2CAP_CONN_NO_RESOURCES;
      } else {
        /* add channel ID to conflict ID */
        p_bcb->conflict_lcid = p_bcb->ch_lcid;
        result = L2CAP_CONN_OK;
        log::verbose("Detected conflict_lcid:0x{:x}", p_bcb->conflict_lcid);
      }
    }
  }
  /* else no control channel yet, reject */

  /* Set the FCR options: Browsing channel mandates ERTM */
  ertm_info.preferred_mode = L2CAP_FCR_ERTM_MODE;

  /* If we reject the connection, send DisconnectReq */
  if (result != L2CAP_CONN_OK) {
    log::verbose("Connection rejected to lcid:0x{:x}", lcid);
    L2CA_DisconnectReq(lcid);
  }

  /* if result ok, proceed with connection */
  if (result == L2CAP_CONN_OK) {
    /* store LCID */
    p_bcb->ch_lcid = lcid;

    /* transition to configuration state */
    p_bcb->ch_state = AVCT_CH_CFG;
  }
}

void avct_br_on_l2cap_error(uint16_t lcid, uint16_t result) {
  tAVCT_BCB* p_bcb = avct_bcb_by_lcid(lcid);
  if (p_bcb == nullptr) return;

  if (p_bcb->ch_state == AVCT_CH_CONN && p_bcb->conflict_lcid == lcid) {
    log::verbose("Reset conflict_lcid:0x{:x}", p_bcb->conflict_lcid);
    p_bcb->conflict_lcid = 0;
    return;
  }
  /* store result value */
  p_bcb->ch_result = result;

  /* Send L2CAP disconnect req */
  avct_l2c_br_disconnect(lcid, 0);
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_connect_cfm_cback
 *
 * Description      This is the L2CAP connect confirm callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_connect_cfm_cback(uint16_t lcid, uint16_t result) {
  tAVCT_BCB* p_bcb;

  /* look up bcb for this channel */
  p_bcb = avct_bcb_by_lcid(lcid);

  if (p_bcb == NULL) {
    return;
  }
  /* if in correct state */
  if (p_bcb->ch_state == AVCT_CH_CONN) {
    /* if result successful */
    if (result == L2CAP_CONN_OK) {
      /* set channel state */
      p_bcb->ch_state = AVCT_CH_CFG;
    }
    /* else failure */
    else {
      log::error("Invoked with non OK status");
    }
  } else if (p_bcb->conflict_lcid == lcid) {
    /* we must be in AVCT_CH_CFG state for the ch_lcid channel */
    if (result == L2CAP_CONN_OK) {
      /* just in case the peer also accepts our connection - Send L2CAP
       * disconnect req */
      log::verbose("Disconnect conflict_lcid:0x{:x}", p_bcb->conflict_lcid);
      L2CA_DisconnectReq(lcid);
    }
    p_bcb->conflict_lcid = 0;
  }
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_config_cfm_cback
 *
 * Description      This is the L2CAP config confirm callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_config_cfm_cback(uint16_t lcid, uint16_t initiator,
                                  tL2CAP_CFG_INFO* p_cfg) {
  avct_l2c_br_config_ind_cback(lcid, p_cfg);

  tAVCT_BCB* p_lcb;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if ((p_lcb == NULL) || (p_lcb->ch_state != AVCT_CH_CFG)) return;

  p_lcb->ch_state = AVCT_CH_OPEN;
  avct_bcb_event(p_lcb, AVCT_LCB_LL_OPEN_EVT, NULL);
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_config_ind_cback
 *
 * Description      This is the L2CAP config indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_config_ind_cback(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg) {
  tAVCT_BCB* p_lcb;
  uint16_t max_mtu = BT_DEFAULT_BUFFER_SIZE - L2CAP_MIN_OFFSET - BT_HDR_SIZE;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if (p_lcb == NULL) return;

  /* store the mtu in tbl */
  p_lcb->peer_mtu = L2CAP_DEFAULT_MTU;
  if (p_cfg->mtu_present) {
    p_lcb->peer_mtu = p_cfg->mtu;
  }

  if (p_lcb->peer_mtu > max_mtu) {
    p_lcb->peer_mtu = max_mtu;
  }

  log::verbose("peer_mtu:{} use:{}", p_lcb->peer_mtu, max_mtu);
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_disconnect_ind_cback
 *
 * Description      This is the L2CAP disconnect indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_disconnect_ind_cback(uint16_t lcid, bool ack_needed) {
  tAVCT_BCB* p_lcb;
  uint16_t result = AVCT_RESULT_FAIL;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if (p_lcb == NULL) return;

  tAVCT_LCB_EVT avct_lcb_evt;
  avct_lcb_evt.result = result;
  avct_bcb_event(p_lcb, AVCT_LCB_LL_CLOSE_EVT, &avct_lcb_evt);
}

void avct_l2c_br_disconnect(uint16_t lcid, uint16_t result) {
  L2CA_DisconnectReq(lcid);

  tAVCT_BCB* p_lcb;
  uint16_t res;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if (p_lcb == NULL) return;

  /* result value may be previously stored */
  res = (p_lcb->ch_result != 0) ? p_lcb->ch_result : result;
  p_lcb->ch_result = 0;

  tAVCT_LCB_EVT avct_lcb_evt;
  avct_lcb_evt.result = res;
  avct_bcb_event(p_lcb, AVCT_LCB_LL_CLOSE_EVT, &avct_lcb_evt);
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_congestion_ind_cback
 *
 * Description      This is the L2CAP congestion indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_congestion_ind_cback(uint16_t lcid, bool is_congested) {
  tAVCT_BCB* p_lcb;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if (p_lcb == NULL) return;

  tAVCT_LCB_EVT avct_lcb_evt;
  avct_lcb_evt.cong = is_congested;
  avct_bcb_event(p_lcb, AVCT_LCB_LL_CONG_EVT, &avct_lcb_evt);
}

/*******************************************************************************
 *
 * Function         avct_l2c_br_data_ind_cback
 *
 * Description      This is the L2CAP data indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avct_l2c_br_data_ind_cback(uint16_t lcid, BT_HDR* p_buf) {
  tAVCT_BCB* p_lcb;
  tAVCT_LCB_EVT evt_data;

  /* look up lcb for this channel */
  p_lcb = avct_bcb_by_lcid(lcid);
  if (p_lcb == NULL) {
    /* prevent buffer leak */
    osi_free(p_buf);
    return;
  }

  evt_data.p_buf = p_buf;
  avct_bcb_event(p_lcb, AVCT_LCB_LL_MSG_EVT, &evt_data);
}
