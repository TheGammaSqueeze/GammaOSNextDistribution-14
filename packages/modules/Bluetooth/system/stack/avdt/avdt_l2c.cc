/******************************************************************************
 *
 *  Copyright 2002-2012 Broadcom Corporation
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
 *  This AVDTP adaption layer module interfaces to L2CAP
 *
 ******************************************************************************/

#include <bluetooth/log.h>

#include "avdt_int.h"
#include "bta/include/bta_av_api.h"
#include "device/include/interop.h"
#include "l2c_api.h"
#include "l2cdefs.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

using namespace bluetooth;

/* callback function declarations */
void avdt_l2c_connect_ind_cback(const RawAddress& bd_addr, uint16_t lcid,
                                uint16_t psm, uint8_t id);
void avdt_l2c_connect_cfm_cback(uint16_t lcid, uint16_t result);
void avdt_l2c_config_cfm_cback(uint16_t lcid, uint16_t result,
                               tL2CAP_CFG_INFO* p_cfg);
void avdt_l2c_config_ind_cback(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg);
void avdt_l2c_disconnect_ind_cback(uint16_t lcid, bool ack_needed);
void avdt_l2c_congestion_ind_cback(uint16_t lcid, bool is_congested);
void avdt_l2c_data_ind_cback(uint16_t lcid, BT_HDR* p_buf);
static void avdt_on_l2cap_error(uint16_t lcid, uint16_t result);

/* L2CAP callback function structure */
const tL2CAP_APPL_INFO avdt_l2c_appl = {avdt_l2c_connect_ind_cback,
                                        avdt_l2c_connect_cfm_cback,
                                        avdt_l2c_config_ind_cback,
                                        avdt_l2c_config_cfm_cback,
                                        avdt_l2c_disconnect_ind_cback,
                                        NULL,
                                        avdt_l2c_data_ind_cback,
                                        avdt_l2c_congestion_ind_cback,
                                        NULL,
                                        avdt_on_l2cap_error,
                                        NULL,
                                        NULL,
                                        NULL,
                                        NULL};

/*******************************************************************************
 *
 * Function         avdt_sec_check_complete_term
 *
 * Description      The function called when Security Manager finishes
 *                  verification of the service side connection
 *
 * Returns          void
 *
 ******************************************************************************/
static void avdt_sec_check_complete_term(const RawAddress* bd_addr,
                                         tBT_TRANSPORT transport,
                                         void* p_ref_data) {
  AvdtpCcb* p_ccb = NULL;
  AvdtpTransportChannel* p_tbl;

  p_ccb = avdt_ccb_by_bd(*bd_addr);

  p_tbl = avdt_ad_tc_tbl_by_st(AVDT_CHAN_SIG, p_ccb, AVDT_AD_ST_SEC_ACP);
  if (p_tbl == NULL) return;

  /* store idx in LCID table, store LCID in routing table */
  avdtp_cb.ad.lcid_tbl[p_tbl->lcid] = avdt_ad_tc_tbl_to_idx(p_tbl);
  avdtp_cb.ad.rt_tbl[avdt_ccb_to_idx(p_ccb)][p_tbl->tcid].lcid = p_tbl->lcid;

  /* transition to configuration state */
  p_tbl->state = AVDT_AD_ST_CFG;
}

/*******************************************************************************
 *
 * Function         avdt_sec_check_complete_orig
 *
 * Description      The function called when Security Manager finishes
 *                  verification of the service side connection
 *
 * Returns          void
 *
 ******************************************************************************/
static void avdt_sec_check_complete_orig(const RawAddress* bd_addr,
                                         tBT_TRANSPORT trasnport,
                                         UNUSED_ATTR void* p_ref_data,
                                         uint8_t res) {
  AvdtpCcb* p_ccb = NULL;
  AvdtpTransportChannel* p_tbl;

  log::verbose("avdt_sec_check_complete_orig res: {}", res);
  if (bd_addr) p_ccb = avdt_ccb_by_bd(*bd_addr);
  p_tbl = avdt_ad_tc_tbl_by_st(AVDT_CHAN_SIG, p_ccb, AVDT_AD_ST_SEC_INT);
  if (p_tbl == NULL) return;

  if (res == BTM_SUCCESS) {
    /* set channel state */
    p_tbl->state = AVDT_AD_ST_CFG;
  } else {
    avdt_l2c_disconnect(p_tbl->lcid);
    avdt_ad_tc_close_ind(p_tbl);
  }
}
/*******************************************************************************
 *
 * Function         avdt_l2c_connect_ind_cback
 *
 * Description      This is the L2CAP connect indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_connect_ind_cback(const RawAddress& bd_addr, uint16_t lcid,
                                UNUSED_ATTR uint16_t psm, uint8_t id) {
  AvdtpCcb* p_ccb;
  AvdtpTransportChannel* p_tbl = NULL;
  uint16_t result;

  /* do we already have a control channel for this peer? */
  p_ccb = avdt_ccb_by_bd(bd_addr);
  if (p_ccb == NULL) {
    /* no, allocate ccb */
    int channel_index = BTA_AvObtainPeerChannelIndex(bd_addr);
    if (channel_index >= 0) {
      p_ccb = avdt_ccb_alloc_by_channel_index(bd_addr, channel_index);
    }
    if (p_ccb == nullptr) {
      p_ccb = avdt_ccb_alloc(bd_addr);
    }
    if (p_ccb == NULL) {
      /* no ccb available, reject L2CAP connection */
      result = L2CAP_CONN_NO_RESOURCES;
    } else {
      /* allocate and set up entry; first channel is always signaling */
      p_tbl = avdt_ad_tc_tbl_alloc(p_ccb);
      p_tbl->my_mtu = kAvdtpMtu;
      p_tbl->tcid = AVDT_CHAN_SIG;
      p_tbl->lcid = lcid;
      p_tbl->state = AVDT_AD_ST_SEC_ACP;
      p_tbl->cfg_flags = AVDT_L2C_CFG_CONN_ACP;

      if (interop_match_addr(INTEROP_2MBPS_LINK_ONLY, &bd_addr)) {
        // Disable 3DH packets for AVDT ACL to improve sensitivity on HS
        btm_set_packet_types_from_address(
            bd_addr,
            (acl_get_supported_packet_types() | HCI_PKT_TYPES_MASK_NO_3_DH1 |
             HCI_PKT_TYPES_MASK_NO_3_DH3 | HCI_PKT_TYPES_MASK_NO_3_DH5));
      }
      /* Assume security check is complete */
      avdt_sec_check_complete_term(&p_ccb->peer_addr, BT_TRANSPORT_BR_EDR,
                                   nullptr);
      return;
    }
  } else {
    /* deal with simultaneous control channel connect case */
    p_tbl = avdt_ad_tc_tbl_by_st(AVDT_CHAN_SIG, p_ccb, AVDT_AD_ST_CONN);
    if (p_tbl != NULL) {
      /* reject their connection */
      result = L2CAP_CONN_NO_RESOURCES;
    } else {
      /* This must be a traffic channel; are we accepting a traffic channel
       * for this ccb?
       */
      p_tbl = avdt_ad_tc_tbl_by_st(AVDT_CHAN_MEDIA, p_ccb, AVDT_AD_ST_ACP);
      if (p_tbl != NULL) {
        /* yes; proceed with connection */
        result = L2CAP_CONN_OK;
      } else {
        /* this must be a reporting channel; are we accepting a reporting
         * channel for this ccb?
         */
        p_tbl = avdt_ad_tc_tbl_by_st(AVDT_CHAN_REPORT, p_ccb, AVDT_AD_ST_ACP);
        if (p_tbl != NULL) {
          /* yes; proceed with connection */
          result = L2CAP_CONN_OK;
        } else {
          /* else we're not listening for traffic channel; reject */
          result = L2CAP_CONN_NO_PSM;
        }
      }
    }
  }

  /* If we reject the connection, send DisconnectReq */
  if (result != L2CAP_CONN_OK) {
    L2CA_DisconnectReq(lcid);
    return;
  }

  /* if result ok, proceed with connection */
  /* store idx in LCID table, store LCID in routing table */
  avdtp_cb.ad.lcid_tbl[lcid] = avdt_ad_tc_tbl_to_idx(p_tbl);
  avdtp_cb.ad.rt_tbl[avdt_ccb_to_idx(p_ccb)][p_tbl->tcid].lcid = lcid;

  /* transition to configuration state */
  p_tbl->state = AVDT_AD_ST_CFG;
}

static void avdt_on_l2cap_error(uint16_t lcid, uint16_t result) {
  avdt_l2c_disconnect(lcid);
}

/*******************************************************************************
 *
 * Function         avdt_l2c_connect_cfm_cback
 *
 * Description      This is the L2CAP connect confirm callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_connect_cfm_cback(uint16_t lcid, uint16_t result) {
  AvdtpTransportChannel* p_tbl;
  AvdtpCcb* p_ccb;

  log::verbose("avdt_l2c_connect_cfm_cback lcid: {}, result: {}", lcid, result);
  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    /* if in correct state */
    if (p_tbl->state == AVDT_AD_ST_CONN) {
      /* if result successful */
      if (result == L2CAP_CONN_OK) {
        if (p_tbl->tcid != AVDT_CHAN_SIG) {
          /* set channel state */
          p_tbl->state = AVDT_AD_ST_CFG;
        } else {
          p_ccb = avdt_ccb_by_idx(p_tbl->ccb_idx);
          if (p_ccb == NULL) {
            result = L2CAP_CONN_NO_RESOURCES;
          } else {
            /* set channel state */
            p_tbl->state = AVDT_AD_ST_SEC_INT;
            p_tbl->lcid = lcid;
            p_tbl->cfg_flags = AVDT_L2C_CFG_CONN_INT;

            if (interop_match_addr(INTEROP_2MBPS_LINK_ONLY,
                                   (const RawAddress*)&p_ccb->peer_addr)) {
              // Disable 3DH packets for AVDT ACL to improve sensitivity on HS
              btm_set_packet_types_from_address(
                  p_ccb->peer_addr,
                  (acl_get_supported_packet_types() |
                   HCI_PKT_TYPES_MASK_NO_3_DH1 | HCI_PKT_TYPES_MASK_NO_3_DH3 |
                   HCI_PKT_TYPES_MASK_NO_3_DH5));
            }

            /* Assume security check is complete */
            avdt_sec_check_complete_orig(&p_ccb->peer_addr, BT_TRANSPORT_BR_EDR,
                                         nullptr, BTM_SUCCESS);
          }
        }
      }

      /* failure; notify adaption that channel closed */
      if (result != L2CAP_CONN_OK) {
        log::error("invoked with non OK status");
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         avdt_l2c_config_cfm_cback
 *
 * Description      This is the L2CAP config confirm callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_config_cfm_cback(uint16_t lcid, uint16_t initiator,
                               tL2CAP_CFG_INFO* p_cfg) {
  avdt_l2c_config_ind_cback(lcid, p_cfg);

  AvdtpTransportChannel* p_tbl;

  log::verbose("lcid: {}", lcid);

  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    p_tbl->lcid = lcid;

    /* if in correct state */
    if (p_tbl->state == AVDT_AD_ST_CFG) {
      avdt_ad_tc_open_ind(p_tbl);
    }
  }
}

/*******************************************************************************
 *
 * Function         avdt_l2c_config_ind_cback
 *
 * Description      This is the L2CAP config indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_config_ind_cback(uint16_t lcid, tL2CAP_CFG_INFO* p_cfg) {
  AvdtpTransportChannel* p_tbl;

  log::verbose("lcid: {}", lcid);

  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    /* store the mtu in tbl */
    if (p_cfg->mtu_present) {
      p_tbl->peer_mtu = p_cfg->mtu;
    } else {
      p_tbl->peer_mtu = L2CAP_DEFAULT_MTU;
    }
    log::verbose("peer_mtu: {}, lcid: {}", p_tbl->peer_mtu, lcid);
  }
}

/*******************************************************************************
 *
 * Function         avdt_l2c_disconnect_ind_cback
 *
 * Description      This is the L2CAP disconnect indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_disconnect_ind_cback(uint16_t lcid, bool ack_needed) {
  AvdtpTransportChannel* p_tbl;

  log::verbose("avdt_l2c_disconnect_ind_cback lcid: {}, ack_needed: {}", lcid,
               ack_needed);
  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    avdt_ad_tc_close_ind(p_tbl);
  }
}

void avdt_l2c_disconnect(uint16_t lcid) {
  L2CA_DisconnectReq(lcid);
  AvdtpTransportChannel* p_tbl;

  log::verbose("avdt_l2c_disconnect_cfm_cback lcid: {}", lcid);
  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    avdt_ad_tc_close_ind(p_tbl);
  }
}

/*******************************************************************************
 *
 * Function         avdt_l2c_congestion_ind_cback
 *
 * Description      This is the L2CAP congestion indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_congestion_ind_cback(uint16_t lcid, bool is_congested) {
  AvdtpTransportChannel* p_tbl;

  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    avdt_ad_tc_cong_ind(p_tbl, is_congested);
  }
}

/*******************************************************************************
 *
 * Function         avdt_l2c_data_ind_cback
 *
 * Description      This is the L2CAP data indication callback function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void avdt_l2c_data_ind_cback(uint16_t lcid, BT_HDR* p_buf) {
  AvdtpTransportChannel* p_tbl;

  /* look up info for this channel */
  p_tbl = avdt_ad_tc_tbl_by_lcid(lcid);
  if (p_tbl != NULL) {
    avdt_ad_tc_data_ind(p_tbl, p_buf);
  } else /* prevent buffer leak */
    osi_free(p_buf);
}
