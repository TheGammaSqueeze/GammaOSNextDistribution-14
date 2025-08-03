/******************************************************************************
 *
 *  Copyright 2004-2012 Broadcom Corporation
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
 *  This file contains action functions for advanced audio/video stream
 *  state machine. these functions are shared by both audio and video
 *  streams.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_av"

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <cstring>
#include <vector>

#include "bta/av/bta_av_int.h"
#include "bta/include/bta_ar_api.h"
#include "bta/include/bta_av_co.h"
#include "btif/avrcp/avrcp_service.h"
#include "btif/include/btif_av.h"
#include "btif/include/btif_av_co.h"
#include "btif/include/btif_config.h"
#include "btif/include/btif_storage.h"
#include "device/include/device_iot_config.h"
#include "device/include/interop.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"
#include "stack/include/a2dp_ext.h"
#include "stack/include/a2dp_sbc.h"
#include "stack/include/acl_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/l2c_api.h"
#include "storage/config_keys.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

using namespace bluetooth;

namespace {

constexpr char kBtmLogTag[] = "A2DP";

}

/*****************************************************************************
 *  Constants
 ****************************************************************************/

/* the delay time in milliseconds to start service discovery on AVRCP */
#ifndef BTA_AV_RC_DISC_TIME_VAL
#define BTA_AV_RC_DISC_TIME_VAL 3500
#endif

/* the timer in milliseconds to guard against link busy and AVDT_CloseReq failed
 * to be sent */
#ifndef BTA_AV_CLOSE_REQ_TIME_VAL
#define BTA_AV_CLOSE_REQ_TIME_VAL 4000
#endif

/* number to retry on reconfigure failure - some headsets requirs this number to
 * be more than 1 */
#ifndef BTA_AV_RECONFIG_RETRY
#define BTA_AV_RECONFIG_RETRY 6
#endif

/* ACL quota we are letting FW use for A2DP Offload Tx. */
#define BTA_AV_A2DP_OFFLOAD_XMIT_QUOTA 4

static void bta_av_offload_codec_builder(tBTA_AV_SCB* p_scb,
                                         tBT_A2DP_OFFLOAD* p_a2dp_offload);

/* state machine states */
enum {
  BTA_AV_INIT_SST,
  BTA_AV_INCOMING_SST,
  BTA_AV_OPENING_SST,
  BTA_AV_OPEN_SST,
  BTA_AV_RCFG_SST,
  BTA_AV_CLOSING_SST
};

/* the call out functions for audio stream */
const tBTA_AV_CO_FUNCTS bta_av_a2dp_cos = {bta_av_co_audio_init,
                                           bta_av_co_audio_disc_res,
                                           bta_av_co_audio_getconfig,
                                           bta_av_co_audio_setconfig,
                                           bta_av_co_audio_open,
                                           bta_av_co_audio_close,
                                           bta_av_co_audio_start,
                                           bta_av_co_audio_stop,
                                           bta_av_co_audio_source_data_path,
                                           bta_av_co_audio_delay,
                                           bta_av_co_audio_update_mtu,
                                           bta_av_co_get_scmst_info};

/* these tables translate AVDT events to SSM events */
static const uint16_t bta_av_stream_evt_ok[] = {
    BTA_AV_STR_DISC_OK_EVT,      /* AVDT_DISCOVER_CFM_EVT */
    BTA_AV_STR_GETCAP_OK_EVT,    /* AVDT_GETCAP_CFM_EVT */
    BTA_AV_STR_OPEN_OK_EVT,      /* AVDT_OPEN_CFM_EVT */
    BTA_AV_STR_OPEN_OK_EVT,      /* AVDT_OPEN_IND_EVT */
    BTA_AV_STR_CONFIG_IND_EVT,   /* AVDT_CONFIG_IND_EVT */
    BTA_AV_STR_START_OK_EVT,     /* AVDT_START_CFM_EVT */
    BTA_AV_STR_START_OK_EVT,     /* AVDT_START_IND_EVT */
    BTA_AV_STR_SUSPEND_CFM_EVT,  /* AVDT_SUSPEND_CFM_EVT */
    BTA_AV_STR_SUSPEND_CFM_EVT,  /* AVDT_SUSPEND_IND_EVT */
    BTA_AV_STR_CLOSE_EVT,        /* AVDT_CLOSE_CFM_EVT */
    BTA_AV_STR_CLOSE_EVT,        /* AVDT_CLOSE_IND_EVT */
    BTA_AV_STR_RECONFIG_CFM_EVT, /* AVDT_RECONFIG_CFM_EVT */
    0,                           /* AVDT_RECONFIG_IND_EVT */
    BTA_AV_STR_SECURITY_CFM_EVT, /* AVDT_SECURITY_CFM_EVT */
    BTA_AV_STR_SECURITY_IND_EVT, /* AVDT_SECURITY_IND_EVT */
    BTA_AV_STR_WRITE_CFM_EVT,    /* AVDT_WRITE_CFM_EVT */
    BTA_AV_AVDT_CONNECT_EVT,     /* AVDT_CONNECT_IND_EVT */
    BTA_AV_AVDT_DISCONNECT_EVT,  /* AVDT_DISCONNECT_IND_EVT */
    BTA_AV_AVDT_RPT_CONN_EVT, /* AVDT_REPORT_CONN_EVT */
    BTA_AV_AVDT_RPT_CONN_EVT, /* AVDT_REPORT_DISCONN_EVT */
    BTA_AV_AVDT_DELAY_RPT_EVT, /* AVDT_DELAY_REPORT_EVT */
    BTA_AV_AVDT_DELAY_RPT_CFM_EVT, /* AVDT_DELAY_REPORT_CFM_EVT */
};

static const uint16_t bta_av_stream_evt_fail[] = {
    BTA_AV_STR_DISC_FAIL_EVT,    /* AVDT_DISCOVER_CFM_EVT */
    BTA_AV_STR_GETCAP_FAIL_EVT,  /* AVDT_GETCAP_CFM_EVT */
    BTA_AV_STR_OPEN_FAIL_EVT,    /* AVDT_OPEN_CFM_EVT */
    BTA_AV_STR_OPEN_OK_EVT,      /* AVDT_OPEN_IND_EVT */
    BTA_AV_STR_CONFIG_IND_EVT,   /* AVDT_CONFIG_IND_EVT */
    BTA_AV_STR_START_FAIL_EVT,   /* AVDT_START_CFM_EVT */
    BTA_AV_STR_START_OK_EVT,     /* AVDT_START_IND_EVT */
    BTA_AV_STR_SUSPEND_CFM_EVT,  /* AVDT_SUSPEND_CFM_EVT */
    BTA_AV_STR_SUSPEND_CFM_EVT,  /* AVDT_SUSPEND_IND_EVT */
    BTA_AV_STR_CLOSE_EVT,        /* AVDT_CLOSE_CFM_EVT */
    BTA_AV_STR_CLOSE_EVT,        /* AVDT_CLOSE_IND_EVT */
    BTA_AV_STR_RECONFIG_CFM_EVT, /* AVDT_RECONFIG_CFM_EVT */
    0,                           /* AVDT_RECONFIG_IND_EVT */
    BTA_AV_STR_SECURITY_CFM_EVT, /* AVDT_SECURITY_CFM_EVT */
    BTA_AV_STR_SECURITY_IND_EVT, /* AVDT_SECURITY_IND_EVT */
    BTA_AV_STR_WRITE_CFM_EVT,    /* AVDT_WRITE_CFM_EVT */
    BTA_AV_AVDT_CONNECT_EVT,     /* AVDT_CONNECT_IND_EVT */
    BTA_AV_AVDT_DISCONNECT_EVT,  /* AVDT_DISCONNECT_IND_EVT */
    BTA_AV_AVDT_RPT_CONN_EVT, /* AVDT_REPORT_CONN_EVT */
    BTA_AV_AVDT_RPT_CONN_EVT, /* AVDT_REPORT_DISCONN_EVT */
    BTA_AV_AVDT_DELAY_RPT_EVT, /* AVDT_DELAY_REPORT_EVT */
    BTA_AV_AVDT_DELAY_RPT_CFM_EVT, /* AVDT_DELAY_REPORT_CFM_EVT */
};

/***********************************************
 *
 * Function         bta_get_scb_handle
 *
 * Description      gives the registered AVDT handle.by checking with sep_type.
 *
 *
 * Returns          void
 **********************************************/
static uint8_t bta_av_get_scb_handle(tBTA_AV_SCB* p_scb, uint8_t local_sep) {
  for (int i = 0; i < BTAV_A2DP_CODEC_INDEX_MAX; i++) {
    if ((p_scb->seps[i].tsep == local_sep) &&
        A2DP_CodecTypeEquals(p_scb->seps[i].codec_info,
                             p_scb->cfg.codec_info)) {
      return (p_scb->seps[i].av_handle);
    }
  }
  log::verbose("local sep_type {} not found", local_sep);
  return 0; /* return invalid handle */
}

/***********************************************
 *
 * Function         bta_av_get_scb_sep_type
 *
 * Description      gives the sep type by cross-checking with AVDT handle
 *
 *
 * Returns          void
 **********************************************/
static uint8_t bta_av_get_scb_sep_type(tBTA_AV_SCB* p_scb,
                                       uint8_t tavdt_handle) {
  for (int i = 0; i < BTAV_A2DP_CODEC_INDEX_MAX; i++) {
    if (p_scb->seps[i].av_handle == tavdt_handle) return (p_scb->seps[i].tsep);
  }
  log::verbose("avdt_handle {} not found", tavdt_handle);
  return AVDT_TSEP_INVALID;
}

/*******************************************************************************
 *
 * Function         bta_av_save_addr
 *
 * Description      copy the bd_addr and maybe reset the supported flags
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_av_save_addr(tBTA_AV_SCB* p_scb, const RawAddress& bd_addr) {
  log::verbose("peer={} recfg_sup:{}, suspend_sup:{}",
               ADDRESS_TO_LOGGABLE_CSTR(bd_addr), p_scb->recfg_sup,
               p_scb->suspend_sup);
  if (p_scb->PeerAddress() != bd_addr) {
    log::info("reset flags old_addr={} new_addr={}",
              ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
              ADDRESS_TO_LOGGABLE_CSTR(bd_addr));
    /* a new addr, reset the supported flags */
    p_scb->recfg_sup = true;
    p_scb->suspend_sup = true;
  }

  /* do this copy anyway, just in case the first addr matches
   * the control block one by accident */
  p_scb->OnConnected(bd_addr);
}

/*******************************************************************************
 *
 * Function         notify_start_failed
 *
 * Description      notify up-layer AV start failed
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void notify_start_failed(tBTA_AV_SCB* p_scb) {
  log::error("peer {} role:0x{:x} bta_channel:{} bta_handle:0x{:x}",
             ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->role,
             p_scb->chnl, p_scb->hndl);
  tBTA_AV bta_av_data = {
      .start =
          {
              .chnl = p_scb->chnl,
              .hndl = p_scb->hndl,
              .status = BTA_AV_FAIL,
              .initiator = true,
              .suspending = false,
          },
  };

  /* if start failed, clear role */
  p_scb->role &= ~BTA_AV_ROLE_START_INT;

  (*bta_av_cb.p_cback)(BTA_AV_START_EVT, &bta_av_data);
}

/*******************************************************************************
 *
 * Function         bta_av_st_rc_timer
 *
 * Description      start the AVRC timer if no RC connection & CT is supported &
 *                  RC is used or
 *                  as ACP (we do not really know if we want AVRC)
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_st_rc_timer(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("rc_handle:{}, use_rc: {}", p_scb->rc_handle, p_scb->use_rc);
  /* for outgoing RC connection as INT/CT */
  if ((p_scb->rc_handle == BTA_AV_RC_HANDLE_NONE) &&
      /* (bta_av_cb.features & BTA_AV_FEAT_RCCT) && */
      (p_scb->use_rc || (p_scb->role & BTA_AV_ROLE_AD_ACP))) {
    if ((p_scb->wait & BTA_AV_WAIT_ROLE_SW_BITS) == 0) {
      bta_sys_start_timer(p_scb->avrc_ct_timer, BTA_AV_RC_DISC_TIME_VAL,
                          BTA_AV_AVRC_TIMER_EVT, p_scb->hndl);
    } else {
      p_scb->wait |= BTA_AV_WAIT_CHECK_RC;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_next_getcap
 *
 * Description      The function gets the capabilities of the next available
 *                  stream found in the discovery results.
 *
 * Returns          true if we sent request to AVDT, false otherwise.
 *
 ******************************************************************************/
static bool bta_av_next_getcap(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  int i;
  bool sent_cmd = false;
  uint16_t uuid_int = p_scb->uuid_int;
  uint8_t sep_requested = 0;

  if (uuid_int == UUID_SERVCLASS_AUDIO_SOURCE)
    sep_requested = AVDT_TSEP_SNK;
  else if (uuid_int == UUID_SERVCLASS_AUDIO_SINK)
    sep_requested = AVDT_TSEP_SRC;

  for (i = p_scb->sep_info_idx; i < p_scb->num_seps; i++) {
    /* steam not in use, is a sink, and is the right media type (audio/video) */
    if ((!p_scb->sep_info[i].in_use) &&
        (p_scb->sep_info[i].tsep == sep_requested) &&
        (p_scb->sep_info[i].media_type == p_scb->media_type)) {
      p_scb->sep_info_idx = i;

      /* we got a stream; get its capabilities */
      bool get_all_cap = (p_scb->AvdtpVersion() >= AVDT_VERSION_1_3) &&
                         (A2DP_GetAvdtpVersion() >= AVDT_VERSION_1_3);
      AVDT_GetCapReq(p_scb->PeerAddress(), p_scb->hdi, p_scb->sep_info[i].seid,
                     &p_scb->peer_cap, &bta_av_proc_stream_evt, get_all_cap);
      sent_cmd = true;
      break;
    }
  }

  /* if no streams available then stream open fails */
  if (!sent_cmd) {
    log::error("BTA_AV_STR_GETCAP_FAIL_EVT: peer_addr={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
    bta_av_ssm_execute(p_scb, BTA_AV_STR_GETCAP_FAIL_EVT, p_data);
  }

  return sent_cmd;
}

/*******************************************************************************
 *
 * Function         bta_av_proc_stream_evt
 *
 * Description      Utility function to compose stream events.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_proc_stream_evt(uint8_t handle, const RawAddress& bd_addr,
                            uint8_t event, tAVDT_CTRL* p_data,
                            uint8_t scb_index) {
  CHECK_LT(scb_index, BTA_AV_NUM_STRS);
  tBTA_AV_SCB* p_scb = bta_av_cb.p_scb[scb_index];
  uint16_t sec_len = 0;

  log::verbose(
      "peer_address: {} avdt_handle: {} event=0x{:x} scb_index={} p_scb={}",
      ADDRESS_TO_LOGGABLE_CSTR(bd_addr), handle, event, scb_index,
      fmt::ptr(p_scb));

  if (p_data) {
    if (event == AVDT_SECURITY_IND_EVT) {
      sec_len = (p_data->security_ind.len < BTA_AV_SECURITY_MAX_LEN)
                    ? p_data->security_ind.len
                    : BTA_AV_SECURITY_MAX_LEN;
    } else if (event == AVDT_SECURITY_CFM_EVT && p_data->hdr.err_code == 0) {
      sec_len = (p_data->security_cfm.len < BTA_AV_SECURITY_MAX_LEN)
                    ? p_data->security_cfm.len
                    : BTA_AV_SECURITY_MAX_LEN;
    }
  }

  if (p_scb) {
    tBTA_AV_STR_MSG* p_msg =
        (tBTA_AV_STR_MSG*)osi_malloc(sizeof(tBTA_AV_STR_MSG) + sec_len);

    /* copy event data, bd addr, and handle to event message buffer */
    p_msg->hdr.offset = 0;

    p_msg->bd_addr = bd_addr;
    p_msg->scb_index = scb_index;
    log::verbose("stream event bd_addr: {} scb_index: {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_msg->bd_addr), scb_index);

    if (p_data != NULL) {
      memcpy(&p_msg->msg, p_data, sizeof(tAVDT_CTRL));
      /* copy config params to event message buffer */
      switch (event) {
        case AVDT_CONFIG_IND_EVT:
          p_msg->cfg = *p_data->config_ind.p_cfg;
          break;

        case AVDT_SECURITY_IND_EVT:
          p_msg->msg.security_ind.p_data = (uint8_t*)(p_msg + 1);
          memcpy(p_msg->msg.security_ind.p_data, p_data->security_ind.p_data,
                 sec_len);
          break;

        case AVDT_SECURITY_CFM_EVT:
          p_msg->msg.security_cfm.p_data = (uint8_t*)(p_msg + 1);
          if (p_data->hdr.err_code == 0) {
            memcpy(p_msg->msg.security_cfm.p_data, p_data->security_cfm.p_data,
                   sec_len);
          }
          break;

        case AVDT_SUSPEND_IND_EVT:
          p_msg->msg.hdr.err_code = 0;
          break;

        case AVDT_CONNECT_IND_EVT:
          p_scb->recfg_sup = true;
          p_scb->suspend_sup = true;
          break;

        default:
          break;
      }
    } else {
      p_msg->msg.hdr.err_code = 0;
    }

    /* look up application event */
    if ((p_data == NULL) || (p_data->hdr.err_code == 0)) {
      p_msg->hdr.event = bta_av_stream_evt_ok[event];
    } else {
      p_msg->hdr.event = bta_av_stream_evt_fail[event];
    }

    p_msg->initiator = false;
    if (event == AVDT_SUSPEND_CFM_EVT) p_msg->initiator = true;

    log::verbose("bta_handle:0x{:x} avdt_handle:{}", p_scb->hndl, handle);
    p_msg->hdr.layer_specific = p_scb->hndl;
    p_msg->handle = handle;
    p_msg->avdt_event = event;
    bta_sys_sendmsg(p_msg);
  }

  if (p_data) {
    bta_av_conn_cback(handle, bd_addr, event, p_data, scb_index);
  } else {
    log::error("p_data is null");
  }
}

/*******************************************************************************
 *
 * Function         bta_av_sink_data_cback
 *
 * Description      This is the AVDTP callback function for sink stream events.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_sink_data_cback(uint8_t handle, BT_HDR* p_pkt, uint32_t time_stamp,
                            uint8_t m_pt) {
  int index = 0;
  tBTA_AV_SCB* p_scb;
  log::verbose(
      "avdt_handle: {} pkt_len=0x{:x}  offset = 0x{:x} number of frames 0x{:x} "
      "sequence number 0x{:x}",
      handle, p_pkt->len, p_pkt->offset,
      *((uint8_t*)(p_pkt + 1) + p_pkt->offset), p_pkt->layer_specific);
  /* Get SCB and correct sep type */
  for (index = 0; index < BTA_AV_NUM_STRS; index++) {
    p_scb = bta_av_cb.p_scb[index];
    if ((p_scb->avdt_handle == handle) &&
        (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SNK)) {
      break;
    }
  }
  if (index == BTA_AV_NUM_STRS) {
    /* cannot find correct handler */
    osi_free(p_pkt);
    return;
  }
  p_pkt->event = BTA_AV_SINK_MEDIA_DATA_EVT;
  p_scb->seps[p_scb->sep_idx].p_app_sink_data_cback(
      p_scb->PeerAddress(), BTA_AV_SINK_MEDIA_DATA_EVT, (tBTA_AV_MEDIA*)p_pkt);
  /* Free the buffer: a copy of the packet has been delivered */
  osi_free(p_pkt);
}

/*******************************************************************************
 *
 * Function         bta_av_a2dp_sdp_cback
 *
 * Description      A2DP service discovery callback.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_av_a2dp_sdp_cback(bool found, tA2DP_Service* p_service,
                                  const RawAddress& peer_address) {
  log::verbose("peer {} : found={}", ADDRESS_TO_LOGGABLE_CSTR(peer_address),
               (found) ? "true" : "false");

  tBTA_AV_SCB* p_scb = NULL;
  if (peer_address != RawAddress::kEmpty) {
    p_scb = bta_av_addr_to_scb(peer_address);
  }
  if (p_scb == NULL) {
    p_scb = bta_av_hndl_to_scb(bta_av_cb.handle);
  }
  if (p_scb == NULL) {
    log::error("no scb found for SDP handle(0x{:x})", bta_av_cb.handle);
    return;
  }
  if (bta_av_cb.handle != p_scb->hndl) {
    log::warn("SDP bta_handle expected=0x{:x} processing=0x{:x}",
              bta_av_cb.handle, p_scb->hndl);
  }

  if (!found) {
    log::error("peer {} A2DP service discovery failed",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
  }
  log::verbose("peer {} found={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
               (found) ? "true" : "false");

  tBTA_AV_SDP_RES* p_msg =
      (tBTA_AV_SDP_RES*)osi_malloc(sizeof(tBTA_AV_SDP_RES));
  if (found) {
    p_msg->hdr.event = BTA_AV_SDP_DISC_OK_EVT;
  } else {
    p_msg->hdr.event = BTA_AV_SDP_DISC_FAIL_EVT;
    log::error("BTA_AV_SDP_DISC_FAIL_EVT: peer_addr={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
  }
  if (found && (p_service != NULL)) {
    p_scb->SetAvdtpVersion(p_service->avdt_version);
    DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(
        p_scb->PeerAddress(), IOT_CONF_KEY_A2DP_VERSION,
        p_service->avdt_version, IOT_CONF_BYTE_NUM_2);

    if (p_service->avdt_version != 0) {
      if (btif_config_set_bin(p_scb->PeerAddress().ToString(),
                              BTIF_STORAGE_KEY_AVDTP_VERSION,
                              (const uint8_t*)&p_service->avdt_version,
                              sizeof(p_service->avdt_version))) {
      } else {
        log::warn("Failed to store peer AVDTP version for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
      }
    }
  } else {
    p_scb->SetAvdtpVersion(0);
  }
  p_msg->hdr.layer_specific = p_scb->hndl;

  bta_sys_sendmsg(p_msg);
}

/*******************************************************************************
 *
 * Function         bta_av_adjust_seps_idx
 *
 * Description      adjust the sep_idx
 *
 * Returns
 *
 ******************************************************************************/
static void bta_av_adjust_seps_idx(tBTA_AV_SCB* p_scb, uint8_t avdt_handle) {
  log::verbose("codec: {}", A2DP_CodecName(p_scb->cfg.codec_info));
  for (int i = 0; i < BTAV_A2DP_CODEC_INDEX_MAX; i++) {
    log::verbose("avdt_handle: {} codec: {}", p_scb->seps[i].av_handle,
                 A2DP_CodecName(p_scb->seps[i].codec_info));
    if (p_scb->seps[i].av_handle && (p_scb->seps[i].av_handle == avdt_handle) &&
        A2DP_CodecTypeEquals(p_scb->seps[i].codec_info,
                             p_scb->cfg.codec_info)) {
      p_scb->sep_idx = i;
      p_scb->avdt_handle = p_scb->seps[i].av_handle;
      break;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_switch_role
 *
 * Description      Switch role was not started and a timer was started.
 *                  another attempt to switch role now - still opening.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_switch_role(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  tBTA_AV_RS_RES switch_res = BTA_AV_RS_NONE;
  tBTA_AV_API_OPEN* p_buf = &p_scb->q_info.open;

  log::verbose("peer {} wait:0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->wait);
  if (p_scb->wait & BTA_AV_WAIT_ROLE_SW_RES_START)
    p_scb->wait |= BTA_AV_WAIT_ROLE_SW_RETRY;

  /* clear the masks set when the timer is started */
  p_scb->wait &=
      ~(BTA_AV_WAIT_ROLE_SW_RES_OPEN | BTA_AV_WAIT_ROLE_SW_RES_START);

  if (p_scb->q_tag == BTA_AV_Q_TAG_OPEN) {
    if (bta_av_switch_if_needed(p_scb) ||
        !bta_av_link_role_ok(p_scb, A2DP_SET_MULTL_BIT)) {
      p_scb->wait |= BTA_AV_WAIT_ROLE_SW_RES_OPEN;
    } else {
      /* this should not happen in theory. Just in case...
       * continue to do_disc_a2dp */
      switch_res = BTA_AV_RS_DONE;
    }
  } else {
    /* report failure on OPEN */
    log::error("peer {} role switch failed (wait=0x{:x})",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->wait);
    switch_res = BTA_AV_RS_FAIL;
  }

  if (switch_res != BTA_AV_RS_NONE) {
    if (bta_av_cb.rs_idx == (p_scb->hdi + 1)) {
      bta_av_cb.rs_idx = 0;
    }
    p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_RETRY;
    p_scb->q_tag = 0;
    p_buf->switch_res = switch_res;
    bta_av_do_disc_a2dp(p_scb, (tBTA_AV_DATA*)p_buf);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_role_res
 *
 * Description      Handle the role changed event
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_role_res(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  bool initiator = false;

  log::verbose("peer {} q_tag:{}, wait:0x{:x}, role:0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->q_tag,
               p_scb->wait, p_scb->role);
  if (p_scb->role & BTA_AV_ROLE_START_INT) initiator = true;

  if (p_scb->q_tag == BTA_AV_Q_TAG_START) {
    if (p_scb->wait & BTA_AV_WAIT_ROLE_SW_STARTED) {
      p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;
      if (p_data->role_res.hci_status != HCI_SUCCESS) {
        p_scb->role &= ~BTA_AV_ROLE_START_INT;
        bta_sys_idle(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
        /* start failed because of role switch. */
        tBTA_AV bta_av_data = {
            .start =
                {
                    .chnl = p_scb->chnl,
                    .hndl = p_scb->hndl,
                    .status = BTA_AV_FAIL_ROLE,
                    .initiator = initiator,
                    .suspending = false,
                },
        };
        (*bta_av_cb.p_cback)(BTA_AV_START_EVT, &bta_av_data);
      } else {
        bta_av_start_ok(p_scb, p_data);
      }
    } else if (p_scb->wait & BTA_AV_WAIT_ROLE_SW_RES_START)
      p_scb->wait |= BTA_AV_WAIT_ROLE_SW_FAILED;
  } else if (p_scb->q_tag == BTA_AV_Q_TAG_OPEN) {
    if (p_scb->wait & BTA_AV_WAIT_ROLE_SW_RES_OPEN) {
      p_scb->role &= ~BTA_AV_ROLE_START_INT;
      p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;

      if (p_data->role_res.hci_status != HCI_SUCCESS) {
        /* Open failed because of role switch. */
        tBTA_AV bta_av_data = {
            .open =
                {
                    .chnl = p_scb->chnl,
                    .hndl = p_scb->hndl,
                    .bd_addr = p_scb->PeerAddress(),
                    .status = BTA_AV_FAIL_ROLE,
                    .starting = false,
                    .edr = 0,
                    .sep = AVDT_TSEP_INVALID,
                },
        };
        if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC) {
          bta_av_data.open.sep = AVDT_TSEP_SNK;
        } else if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SNK) {
          bta_av_data.open.sep = AVDT_TSEP_SRC;
        }
        (*bta_av_cb.p_cback)(BTA_AV_OPEN_EVT, &bta_av_data);
      } else {
        /* Continue av open process */
        p_scb->q_info.open.switch_res = BTA_AV_RS_DONE;
        bta_av_do_disc_a2dp(p_scb, (tBTA_AV_DATA*)&(p_scb->q_info.open));
      }
    } else {
      log::warn(
          "peer {} unexpected role switch event: q_tag = {} wait = 0x{:x}",
          ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->q_tag,
          p_scb->wait);
    }
  }

  log::verbose("peer {} wait:0x{:x}, role:0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->wait,
               p_scb->role);
}

/*******************************************************************************
 *
 * Function         bta_av_delay_co
 *
 * Description      Call the delay call-out function to report the delay report
 *                  from SNK
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_delay_co(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::verbose("peer {} bta_handle:0x{:x} delay:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               p_data->str_msg.msg.delay_rpt_cmd.delay);
  p_scb->p_cos->delay(p_scb->hndl, p_scb->PeerAddress(),
                      p_data->str_msg.msg.delay_rpt_cmd.delay);
}

/*******************************************************************************
 *
 * Function         bta_av_do_disc_a2dp
 *
 * Description      Do service discovery for A2DP.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_do_disc_a2dp(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  bool ok_continue = false;
  tA2DP_SDP_DB_PARAMS db_params;
  uint16_t attr_list[] = {ATTR_ID_SERVICE_CLASS_ID_LIST,
                          ATTR_ID_PROTOCOL_DESC_LIST,
                          ATTR_ID_BT_PROFILE_DESC_LIST};
  uint16_t sdp_uuid = 0; /* UUID for which SDP has to be done */

  log::verbose("peer_addr: {} use_rc: {} switch_res:{}, oc:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_data->api_open.bd_addr),
               p_data->api_open.use_rc, p_data->api_open.switch_res,
               bta_av_cb.audio_open_cnt);

  memcpy(&(p_scb->open_api), &(p_data->api_open), sizeof(tBTA_AV_API_OPEN));

  switch (p_data->api_open.switch_res) {
    case BTA_AV_RS_NONE:
      if (bta_av_switch_if_needed(p_scb) ||
          !bta_av_link_role_ok(p_scb, A2DP_SET_MULTL_BIT)) {
        /* waiting for role switch result. save the api to control block */
        memcpy(&p_scb->q_info.open, &p_data->api_open,
               sizeof(tBTA_AV_API_OPEN));
        p_scb->wait |= BTA_AV_WAIT_ROLE_SW_RES_OPEN;
        p_scb->q_tag = BTA_AV_Q_TAG_OPEN;
      } else {
        ok_continue = true;
      }
      break;

    case BTA_AV_RS_FAIL:
      /* report a new failure event  */
      p_scb->open_status = BTA_AV_FAIL_ROLE;
      log::error("BTA_AV_SDP_DISC_FAIL_EVT: peer_addr={}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
      bta_av_ssm_execute(p_scb, BTA_AV_SDP_DISC_FAIL_EVT, NULL);
      break;

    case BTA_AV_RS_OK:
      p_data = (tBTA_AV_DATA*)&p_scb->q_info.open;
      /* continue to open if link role is ok */
      if (bta_av_link_role_ok(p_scb, A2DP_SET_MULTL_BIT)) {
        ok_continue = true;
      } else {
        p_scb->wait |= BTA_AV_WAIT_ROLE_SW_RES_OPEN;
      }
      break;

    case BTA_AV_RS_DONE:
      ok_continue = true;
      break;
  }

  log::verbose("ok_continue: {} wait:0x{:x}, q_tag: {}", ok_continue,
               p_scb->wait, p_scb->q_tag);
  if (!ok_continue) return;

  /* clear the role switch bits */
  p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;

  if (p_scb->wait & BTA_AV_WAIT_CHECK_RC) {
    p_scb->wait &= ~BTA_AV_WAIT_CHECK_RC;
    bta_sys_start_timer(p_scb->avrc_ct_timer, BTA_AV_RC_DISC_TIME_VAL,
                        BTA_AV_AVRC_TIMER_EVT, p_scb->hndl);
  }

  /* store peer addr other parameters */
  bta_av_save_addr(p_scb, p_data->api_open.bd_addr);
  p_scb->use_rc = p_data->api_open.use_rc;

  bta_sys_app_open(BTA_ID_AV, p_scb->app_id, p_scb->PeerAddress());

  /* set up parameters */
  db_params.db_len = BTA_AV_DISC_BUF_SIZE;
  db_params.num_attr = 3;
  db_params.p_attrs = attr_list;
  p_scb->uuid_int = p_data->api_open.uuid;
  p_scb->sdp_discovery_started = true;
  if (p_scb->uuid_int == UUID_SERVCLASS_AUDIO_SINK)
    sdp_uuid = UUID_SERVCLASS_AUDIO_SOURCE;
  else if (p_scb->uuid_int == UUID_SERVCLASS_AUDIO_SOURCE)
    sdp_uuid = UUID_SERVCLASS_AUDIO_SINK;

  log::verbose(
      "Initiate SDP discovery for peer {} : uuid_int=0x{:x} sdp_uuid=0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->uuid_int,
      sdp_uuid);
  tA2DP_STATUS find_service_status = A2DP_FindService(
      sdp_uuid, p_scb->PeerAddress(), &db_params, bta_av_a2dp_sdp_cback);
  if (find_service_status != A2DP_SUCCESS) {
    log::error(
        "A2DP_FindService() failed for peer {} uuid_int=0x{:x} sdp_uuid=0x{:x} "
        ": status={}",
        ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->uuid_int,
        sdp_uuid, find_service_status);
    bta_av_a2dp_sdp_cback(false, nullptr, p_scb->PeerAddress());
  } else {
    /* only one A2DP find service is active at a time */
    bta_av_cb.handle = p_scb->hndl;
  }
}

/*******************************************************************************
 *
 * Function         bta_av_cleanup
 *
 * Description      cleanup AV stream control block.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_cleanup(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  tBTA_AV_CONN_CHG msg;
  uint8_t role = BTA_AV_ROLE_AD_INT;

  log::info("peer {}", ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));

  /* free any buffers */
  p_scb->sdp_discovery_started = false;
  p_scb->SetAvdtpVersion(0);

  /* initialize some control block variables */
  p_scb->open_status = BTA_AV_SUCCESS;

  /* if de-registering shut everything down */
  msg.hdr.layer_specific = p_scb->hndl;
  p_scb->started = false;
  p_scb->use_rtp_header_marker_bit = false;
  p_scb->cong = false;
  p_scb->role = role;
  p_scb->cur_psc_mask = 0;
  p_scb->wait = 0;
  p_scb->num_disc_snks = 0;
  p_scb->coll_mask = 0;
  p_scb->uuid_int = 0;
  alarm_cancel(p_scb->avrc_ct_timer);
  alarm_cancel(p_scb->link_signalling_timer);
  alarm_cancel(p_scb->accept_signalling_timer);

  /* TODO(eisenbach): RE-IMPLEMENT USING VSC OR HAL EXTENSION
    vendor_get_interface()->send_command(
        (vendor_opcode_t)BT_VND_OP_A2DP_OFFLOAD_STOP, (void*)&p_scb->l2c_cid);
    if (p_scb->offload_start_pending) {
      tBTA_AV_STATUS status = BTA_AV_FAIL_STREAM;
      tBTA_AV bta_av_data;
      bta_av_data.status = status;
      (*bta_av_cb.p_cback)(BTA_AV_OFFLOAD_START_RSP_EVT, &bta_av_data);
    }
  */

  if (p_scb->deregistering) {
    /* remove stream */
    for (int i = 0; i < BTAV_A2DP_CODEC_INDEX_MAX; i++) {
      if (p_scb->seps[i].av_handle) AVDT_RemoveStream(p_scb->seps[i].av_handle);
      p_scb->seps[i].av_handle = 0;
    }

    bta_av_dereg_comp((tBTA_AV_DATA*)&msg);
  } else {
    /* report stream closed to main SM */
    msg.is_up = false;
    msg.peer_addr = p_scb->PeerAddress();
    bta_av_conn_chg((tBTA_AV_DATA*)&msg);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_free_sdb
 *
 * Description      Free service discovery db buffer.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_free_sdb(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  p_scb->sdp_discovery_started = false;
}

/*******************************************************************************
 *
 * Function         bta_av_config_ind
 *
 * Description      Handle a stream configuration indication from the peer.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_config_ind(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV_CI_SETCONFIG setconfig{};
  tAVDT_SEP_INFO* p_info;
  const AvdtpSepConfig* p_evt_cfg = &p_data->str_msg.cfg;
  uint8_t psc_mask = (p_evt_cfg->psc_mask | p_scb->cfg.psc_mask);
  uint8_t
      local_sep; /* sep type of local handle on which connection was received */
  tBTA_AV_STR_MSG* p_msg = (tBTA_AV_STR_MSG*)p_data;

  local_sep = bta_av_get_scb_sep_type(p_scb, p_msg->handle);
  p_scb->avdt_label = p_data->str_msg.msg.hdr.label;

  log::verbose("peer {} bta_handle:0x{:x} local_sep:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               local_sep);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_evt_cfg->codec_info));

  memcpy(p_scb->cfg.codec_info, p_evt_cfg->codec_info, AVDT_CODEC_SIZE);
  bta_av_save_addr(p_scb, p_data->str_msg.bd_addr);

  /* Clear collision mask */
  p_scb->coll_mask = 0;
  alarm_cancel(p_scb->accept_signalling_timer);

  /* if no codec parameters in configuration, fail */
  if ((p_evt_cfg->num_codec == 0) ||
      /* or the peer requests for a service we do not support */
      ((psc_mask != p_scb->cfg.psc_mask) &&
       (psc_mask != (p_scb->cfg.psc_mask & ~AVDT_PSC_DELAY_RPT)))) {
    setconfig.hndl = p_scb->hndl; /* we may not need this */
    setconfig.err_code = AVDT_ERR_UNSUP_CFG;
    bta_av_ssm_execute(p_scb, BTA_AV_CI_SETCONFIG_FAIL_EVT,
                       (tBTA_AV_DATA*)&setconfig);
  } else {
    p_info = &p_scb->sep_info[0];
    p_info->in_use = 0;
    p_info->media_type = p_scb->media_type;
    p_info->seid = p_data->str_msg.msg.config_ind.int_seid;

    /* Sep type of Peer will be oppsite role to our local sep */
    if (local_sep == AVDT_TSEP_SRC) {
      p_info->tsep = AVDT_TSEP_SNK;
      DEVICE_IOT_CONFIG_ADDR_SET_INT(p_scb->PeerAddress(),
                                     IOT_CONF_KEY_A2DP_ROLE,
                                     IOT_CONF_VAL_A2DP_ROLE_SINK);
    } else if (local_sep == AVDT_TSEP_SNK) {
      p_info->tsep = AVDT_TSEP_SRC;
      DEVICE_IOT_CONFIG_ADDR_SET_INT(p_scb->PeerAddress(),
                                     IOT_CONF_KEY_A2DP_ROLE,
                                     IOT_CONF_VAL_A2DP_ROLE_SOURCE);
    }

    p_scb->role |= BTA_AV_ROLE_AD_ACP;
    p_scb->cur_psc_mask = p_evt_cfg->psc_mask;
    if (bta_av_cb.features & BTA_AV_FEAT_RCTG)
      p_scb->use_rc = true;
    else
      p_scb->use_rc = false;

    p_scb->num_seps = 1;
    p_scb->sep_info_idx = 0;
    log::verbose("SEID: {} use_rc: {} cur_psc_mask:0x{:x}", p_info->seid,
                 p_scb->use_rc, p_scb->cur_psc_mask);
    /*  in case of A2DP SINK this is the first time peer data is being sent to
     * co functions */
    if (local_sep == AVDT_TSEP_SNK) {
      p_scb->p_cos->setcfg(p_scb->hndl, p_scb->PeerAddress(),
                           p_evt_cfg->codec_info, p_info->seid,
                           p_evt_cfg->num_protect, p_evt_cfg->protect_info,
                           AVDT_TSEP_SNK, p_msg->handle);
    } else {
      p_scb->p_cos->setcfg(p_scb->hndl, p_scb->PeerAddress(),
                           p_evt_cfg->codec_info, p_info->seid,
                           p_evt_cfg->num_protect, p_evt_cfg->protect_info,
                           AVDT_TSEP_SRC, p_msg->handle);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_disconnect_req
 *
 * Description      Disconnect AVDTP connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_disconnect_req(tBTA_AV_SCB* p_scb,
                           UNUSED_ATTR tBTA_AV_DATA* p_data) {
  tBTA_AV_RCB* p_rcb;

  log::verbose("conn_lcb: 0x{:x} peer_addr: {}", bta_av_cb.conn_lcb,
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));

  alarm_cancel(p_scb->link_signalling_timer);
  alarm_cancel(p_scb->accept_signalling_timer);
  alarm_cancel(p_scb->avrc_ct_timer);

  // conn_lcb is the index bitmask of all used LCBs, and since LCB and SCB use
  // the same index, it should be safe to use SCB index here.
  if ((bta_av_cb.conn_lcb & (1 << p_scb->hdi)) != 0) {
    p_rcb = bta_av_get_rcb_by_shdl((uint8_t)(p_scb->hdi + 1));
    if (p_rcb && p_scb->rc_handle != BTA_AV_RC_HANDLE_NONE) {
      bta_av_del_rc(p_rcb);
    }
    AVDT_DisconnectReq(p_scb->PeerAddress(), &bta_av_proc_stream_evt);
  } else {
    log::warn("conn_lcb=0x{:x} bta_handle=0x{:x} (hdi={}) no link",
              bta_av_cb.conn_lcb, p_scb->hndl, p_scb->hdi);
    bta_av_ssm_execute(p_scb, BTA_AV_AVDT_DISCONNECT_EVT, NULL);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_security_req
 *
 * Description      Send an AVDTP security request.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_security_req(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  if (bta_av_cb.features & BTA_AV_FEAT_PROTECT) {
    AVDT_SecurityReq(p_scb->avdt_handle, p_data->api_protect_req.p_data,
                     p_data->api_protect_req.len);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_security_rsp
 *
 * Description      Send an AVDTP security response.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_security_rsp(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  if (bta_av_cb.features & BTA_AV_FEAT_PROTECT) {
    AVDT_SecurityRsp(p_scb->avdt_handle, p_scb->avdt_label,
                     p_data->api_protect_rsp.error_code,
                     p_data->api_protect_rsp.p_data,
                     p_data->api_protect_rsp.len);
  } else {
    AVDT_SecurityRsp(p_scb->avdt_handle, p_scb->avdt_label, AVDT_ERR_NSC, NULL,
                     0);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_setconfig_rsp
 *
 * Description      setconfig is OK
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_setconfig_rsp(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t num = p_data->ci_setconfig.num_seid + 1;
  uint8_t avdt_handle = p_data->ci_setconfig.avdt_handle;
  uint8_t* p_seid = p_data->ci_setconfig.p_seid;
  int i;
  uint8_t local_sep;

  /* we like this codec_type. find the sep_idx */
  local_sep = bta_av_get_scb_sep_type(p_scb, avdt_handle);
  bta_av_adjust_seps_idx(p_scb, avdt_handle);
  log::info(
      "peer {} bta_handle=0x{:x} avdt_handle={} sep_idx={} cur_psc_mask:0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
      p_scb->avdt_handle, p_scb->sep_idx, p_scb->cur_psc_mask);

  if ((AVDT_TSEP_SNK == local_sep) &&
      (p_data->ci_setconfig.err_code == AVDT_SUCCESS) &&
      (p_scb->seps[p_scb->sep_idx].p_app_sink_data_cback != NULL)) {
    tBTA_AV_MEDIA av_sink_codec_info;
    av_sink_codec_info.avk_config.bd_addr = p_scb->PeerAddress();
    av_sink_codec_info.avk_config.codec_info = p_scb->cfg.codec_info;
    p_scb->seps[p_scb->sep_idx].p_app_sink_data_cback(
        p_scb->PeerAddress(), BTA_AV_SINK_MEDIA_CFG_EVT, &av_sink_codec_info);
  }

  AVDT_ConfigRsp(p_scb->avdt_handle, p_scb->avdt_label,
                 p_data->ci_setconfig.err_code, p_data->ci_setconfig.category);

  alarm_cancel(p_scb->link_signalling_timer);

  if (p_data->ci_setconfig.err_code == AVDT_SUCCESS) {
    p_scb->wait = BTA_AV_WAIT_ACP_CAPS_ON;
    if (p_data->ci_setconfig.recfg_needed)
      p_scb->role |= BTA_AV_ROLE_SUSPEND_OPT;
    log::verbose("recfg_needed:{} role:0x{:x} num:{}",
                 p_data->ci_setconfig.recfg_needed, p_scb->role, num);
    /* callout module tells BTA the number of "good" SEPs and their SEIDs.
     * getcap on these SEID */
    p_scb->num_seps = num;

    if (p_scb->cur_psc_mask & AVDT_PSC_DELAY_RPT)
      p_scb->SetAvdtpVersion(AVDT_VERSION_1_3);

    if (A2DP_GetCodecType(p_scb->cfg.codec_info) == A2DP_MEDIA_CT_SBC ||
        num > 1) {
      /* if SBC is used by the SNK as INT, discover req is not sent in
       * bta_av_config_ind.
       * call disc_res now */
      /* this is called in A2DP SRC path only, In case of SINK we don't need it
       */
      if (local_sep == AVDT_TSEP_SRC)
        p_scb->p_cos->disc_res(p_scb->hndl, p_scb->PeerAddress(), num, num, 0,
                               UUID_SERVCLASS_AUDIO_SOURCE);
    } else {
      /* we do not know the peer device and it is using non-SBC codec
       * we need to know all the SEPs on SNK */
      if (p_scb->uuid_int == 0) p_scb->uuid_int = p_scb->open_api.uuid;
      bta_av_discover_req(p_scb, NULL);
      return;
    }

    for (i = 1; i < num; i++) {
      log::verbose("sep_info[{}] SEID: {}", i, p_seid[i - 1]);
      /* initialize the sep_info[] to get capabilities */
      p_scb->sep_info[i].in_use = false;
      p_scb->sep_info[i].tsep = AVDT_TSEP_SNK;
      p_scb->sep_info[i].media_type = p_scb->media_type;
      p_scb->sep_info[i].seid = p_seid[i - 1];
    }

    /* only in case of local sep as SRC we need to look for other SEPs, In case
     * of SINK we don't */
    if (btif_av_src_sink_coexist_enabled()) {
      if (local_sep == AVDT_TSEP_SRC) {
        /* Make sure UUID has been initialized... */
        /* if local sep is source, uuid_int should be source */
        p_scb->uuid_int = UUID_SERVCLASS_AUDIO_SOURCE;
        bta_av_next_getcap(p_scb, p_data);
      } else {
        p_scb->uuid_int = UUID_SERVCLASS_AUDIO_SINK;
      }
    } else {
      if (local_sep == AVDT_TSEP_SRC) {
        /* Make sure UUID has been initialized... */
        if (p_scb->uuid_int == 0) {
          p_scb->uuid_int = p_scb->open_api.uuid;
        }
        bta_av_next_getcap(p_scb, p_data);
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_str_opened
 *
 * Description      Stream opened OK (incoming/outgoing).
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_str_opened(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV_CONN_CHG msg;
  char remote_name[BTM_MAX_REM_BD_NAME_LEN] = "";
  uint8_t* p;

  log::verbose("peer {} bta_handle: 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl);

  msg.hdr.layer_specific = p_scb->hndl;
  msg.is_up = true;
  msg.peer_addr = p_scb->PeerAddress();
  p_scb->l2c_cid = AVDT_GetL2CapChannel(p_scb->avdt_handle);
  bta_av_conn_chg((tBTA_AV_DATA*)&msg);
  /* set the congestion flag, so AV would not send media packets by accident */
  p_scb->cong = true;
  // Don't use AVDTP SUSPEND for restrict listed devices
  btif_storage_get_stored_remote_name(p_scb->PeerAddress(), remote_name);
  if (interop_match_name(INTEROP_DISABLE_AVDTP_SUSPEND, remote_name) ||
      interop_match_addr(INTEROP_DISABLE_AVDTP_SUSPEND,
                         &p_scb->PeerAddress())) {
    log::info("disable AVDTP SUSPEND: interop matched name {} address {}",
              remote_name, ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
    p_scb->suspend_sup = false;
  }

  p_scb->stream_mtu =
      p_data->str_msg.msg.open_ind.peer_mtu - AVDT_MEDIA_HDR_SIZE;
  log::verbose("l2c_cid: 0x{:x} stream_mtu: {}", p_scb->l2c_cid,
               p_scb->stream_mtu);

  /* Set the media channel as high priority */
  L2CA_SetTxPriority(p_scb->l2c_cid, L2CAP_CHNL_PRIORITY_HIGH);
  L2CA_SetChnlFlushability(p_scb->l2c_cid, true);

  bta_sys_conn_open(BTA_ID_AV, p_scb->app_id, p_scb->PeerAddress());
  memset(&p_scb->q_info, 0, sizeof(tBTA_AV_Q_INFO));

  p_scb->l2c_bufs = 0;
  p_scb->p_cos->open(p_scb->hndl, p_scb->PeerAddress(), p_scb->stream_mtu);

  {
    /* TODO check if other audio channel is open.
     * If yes, check if reconfig is needed
     * Rigt now we do not do this kind of checking.
     * BTA-AV is INT for 2nd audio connection.
     * The application needs to make sure the current codec_info is proper.
     * If one audio connection is open and another SNK attempts to connect to
     * AV,
     * the connection will be rejected.
     */
    /* check if other audio channel is started. If yes, start */
    tBTA_AV_OPEN open = {
        .chnl = p_scb->chnl,
        .hndl = p_scb->hndl,
        .bd_addr = p_scb->PeerAddress(),
        .status = BTA_AV_SUCCESS,
        .starting = false,
        .edr = 0,
        .sep = AVDT_TSEP_INVALID,
    };

    L2CA_SetMediaStreamChannel(p_scb->l2c_cid, true);

    p = BTM_ReadRemoteFeatures(p_scb->PeerAddress());
    if (p != NULL) {
      if (HCI_EDR_ACL_2MPS_SUPPORTED(p)) open.edr |= BTA_AV_EDR_2MBPS;
      if (HCI_EDR_ACL_3MPS_SUPPORTED(p)) {
        if (!interop_match_addr(INTEROP_2MBPS_LINK_ONLY,
                                &p_scb->PeerAddress())) {
          open.edr |= BTA_AV_EDR_3MBPS;
        }
      }
    }
    bta_ar_avdt_conn(BTA_ID_AV, open.bd_addr, p_scb->hdi);
    if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC) {
      open.starting = false;
      open.sep = AVDT_TSEP_SNK;
    } else if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SNK) {
      open.starting = bta_av_chk_start(p_scb);
      open.sep = AVDT_TSEP_SRC;
    }

    tBTA_AV bta_av_data;
    bta_av_data.open = open;
    (*bta_av_cb.p_cback)(BTA_AV_OPEN_EVT, &bta_av_data);
    if (open.starting) {
      bta_av_ssm_execute(p_scb, BTA_AV_AP_START_EVT, NULL);
    }
  }

  // This code is used to pass PTS TC for AVDTP ABORT
  char value[PROPERTY_VALUE_MAX] = {0};
  if ((osi_property_get("bluetooth.pts.force_a2dp_abort", value, "false")) &&
      (!strcmp(value, "true"))) {
    log::error("Calling AVDT_AbortReq");
    AVDT_AbortReq(p_scb->avdt_handle);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_security_ind
 *
 * Description      Handle an AVDTP security indication.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_security_ind(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  p_scb->avdt_label = p_data->str_msg.msg.hdr.label;

  if (bta_av_cb.features & BTA_AV_FEAT_PROTECT) {
    tBTA_AV bta_av_data = {
        .protect_req =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .p_data = p_data->str_msg.msg.security_ind.p_data,
                .len = p_data->str_msg.msg.security_ind.len,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_PROTECT_REQ_EVT, &bta_av_data);
  }
  /* app doesn't support security indication; respond with failure */
  else {
    AVDT_SecurityRsp(p_scb->avdt_handle, p_scb->avdt_label, AVDT_ERR_NSC, NULL,
                     0);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_security_cfm
 *
 * Description      Handle an AVDTP security confirm.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_security_cfm(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  if (bta_av_cb.features & BTA_AV_FEAT_PROTECT) {
    tBTA_AV bta_av_data = {
        .protect_rsp =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .p_data = p_data->str_msg.msg.security_cfm.p_data,
                .len = p_data->str_msg.msg.security_cfm.len,
                .err_code = p_data->str_msg.msg.hdr.err_code,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_PROTECT_RSP_EVT, &bta_av_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_do_close
 *
 * Description      Close stream.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_do_close(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("p_scb->co_started={}", p_scb->co_started);

  /* stop stream if started */
  if (p_scb->co_started) {
    bta_av_str_stopped(p_scb, NULL);
  }
  alarm_cancel(p_scb->link_signalling_timer);

  /* close stream */
  p_scb->started = false;
  p_scb->use_rtp_header_marker_bit = false;

  /* drop the buffers queued in L2CAP */
  L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_ALL);

  AVDT_CloseReq(p_scb->avdt_handle);
  /* just in case that the link is congested, link is flow controled by peer or
   * for whatever reason the the close request can not be sent in time.
   * when this timer expires, AVDT_DisconnectReq will be called to disconnect
   * the link
   */
  bta_sys_start_timer(p_scb->avrc_ct_timer, BTA_AV_CLOSE_REQ_TIME_VAL,
                      BTA_AV_API_CLOSE_EVT, p_scb->hndl);
}

/*******************************************************************************
 *
 * Function         bta_av_connect_req
 *
 * Description      Connect AVDTP connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_connect_req(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("peer {} coll_mask=0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
               p_scb->coll_mask);
  p_scb->sdp_discovery_started = false;
  if (p_scb->coll_mask & BTA_AV_COLL_INC_TMR) {
    /* SNK initiated L2C connection while SRC was doing SDP.    */
    /* Wait until timeout to check if SNK starts signalling.    */
    log::warn("coll_mask=0x{:02x} incoming timer is up", p_scb->coll_mask);
    p_scb->coll_mask |= BTA_AV_COLL_API_CALLED;
    log::verbose("updated coll_mask=0x{:02x}", p_scb->coll_mask);
    return;
  }

  AVDT_ConnectReq(p_scb->PeerAddress(), p_scb->hdi, &bta_av_proc_stream_evt);
}

/*******************************************************************************
 *
 * Function         bta_av_sdp_failed
 *
 * Description      Service discovery failed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_sdp_failed(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::error("peer_addr={} open_status={}",
             ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
             p_scb->open_status);

  if (p_scb->open_status == BTA_AV_SUCCESS) {
    p_scb->open_status = BTA_AV_FAIL_SDP;
  }

  p_scb->sdp_discovery_started = false;
  bta_av_str_closed(p_scb, p_data);
}

/*******************************************************************************
 *
 * Function         bta_av_disc_results
 *
 * Description      Handle the AVDTP discover results.  Search through the
 *                  results and find the first available stream, and get
 *                  its capabilities.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_disc_results(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t num_snks = 0, num_srcs = 0, i;
  /* our uuid in case we initiate connection */
  uint16_t uuid_int = p_scb->uuid_int;

  log::verbose("peer {} bta_handle: 0x{:x} initiator UUID 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               uuid_int);

  /* store number of stream endpoints returned */
  p_scb->num_seps = p_data->str_msg.msg.discover_cfm.num_seps;

  if (btif_av_src_sink_coexist_enabled()) {
    for (i = 0; i < p_scb->num_seps; i++) {
      /* steam not in use, is a sink, and is audio */
      if ((!p_scb->sep_info[i].in_use) &&
          (p_scb->sep_info[i].media_type == p_scb->media_type)) {
        if (p_scb->sep_info[i].tsep == AVDT_TSEP_SNK) num_snks++;

        if (p_scb->sep_info[i].tsep == AVDT_TSEP_SRC) num_srcs++;
      }
    }
    log::verbose("both_enable={}, uuid_int=0x{:x}, incoming={}",
                 btif_av_both_enable(), uuid_int, p_scb->open_api.incoming);
    if (btif_av_both_enable() && p_scb->open_api.incoming) {
      if (uuid_int == UUID_SERVCLASS_AUDIO_SOURCE && num_snks == 0 &&
          num_srcs > 0) {
        p_scb->uuid_int = UUID_SERVCLASS_AUDIO_SINK;
        log::verbose("change UUID to 0x{:x}, num_snks={}, num_srcs={}",
                     p_scb->uuid_int, num_snks, num_srcs);
      } else if (uuid_int == UUID_SERVCLASS_AUDIO_SINK && num_srcs == 0 &&
                 num_snks > 0) {
        p_scb->uuid_int = UUID_SERVCLASS_AUDIO_SOURCE;
        log::verbose("change UUID to 0x{:x}, num_snks={}, num_srcs={}",
                     p_scb->uuid_int, num_snks, num_srcs);
      }
      uuid_int = p_scb->uuid_int;
    }
  } else {
    for (i = 0; i < p_scb->num_seps; i++) {
      /* steam not in use, is a sink, and is audio */
      if ((!p_scb->sep_info[i].in_use) &&
          (p_scb->sep_info[i].media_type == p_scb->media_type)) {
        if ((p_scb->sep_info[i].tsep == AVDT_TSEP_SNK) &&
            (uuid_int == UUID_SERVCLASS_AUDIO_SOURCE))
          num_snks++;

        if ((p_scb->sep_info[i].tsep == AVDT_TSEP_SRC) &&
            (uuid_int == UUID_SERVCLASS_AUDIO_SINK))
          num_srcs++;
      }
    }
  }

  p_scb->p_cos->disc_res(p_scb->hndl, p_scb->PeerAddress(), p_scb->num_seps,
                         num_snks, num_srcs, uuid_int);
  p_scb->num_disc_snks = num_snks;
  p_scb->num_disc_srcs = num_srcs;

  /* if we got any */
  if (p_scb->num_seps > 0) {
    /* initialize index into discovery results */
    p_scb->sep_info_idx = 0;

    /* get the capabilities of the first available stream */
    bta_av_next_getcap(p_scb, p_data);
  }
  /* else we got discover response but with no streams; we're done */
  else {
    log::error("BTA_AV_STR_DISC_FAIL_EVT: peer_addr={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
    bta_av_ssm_execute(p_scb, BTA_AV_STR_DISC_FAIL_EVT, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_disc_res_as_acp
 *
 * Description      Handle the AVDTP discover results.  Search through the
 *                  results and find the first available stream, and get
 *                  its capabilities.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_disc_res_as_acp(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t num_snks = 0, i;

  log::verbose("peer {} bta_handle: 0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl);

  /* store number of stream endpoints returned */
  p_scb->num_seps = p_data->str_msg.msg.discover_cfm.num_seps;

  for (i = 0; i < p_scb->num_seps; i++) {
    /* steam is a sink, and is audio */
    if ((p_scb->sep_info[i].tsep == AVDT_TSEP_SNK) &&
        (p_scb->sep_info[i].media_type == p_scb->media_type)) {
      p_scb->sep_info[i].in_use = false;
      num_snks++;
    }
  }
  p_scb->p_cos->disc_res(p_scb->hndl, p_scb->PeerAddress(), p_scb->num_seps,
                         num_snks, 0, UUID_SERVCLASS_AUDIO_SOURCE);
  p_scb->num_disc_snks = num_snks;
  p_scb->num_disc_srcs = 0;

  /* if we got any */
  if (p_scb->num_seps > 0) {
    /* initialize index into discovery results */
    p_scb->sep_info_idx = 0;

    /* get the capabilities of the first available stream */
    bta_av_next_getcap(p_scb, p_data);
  }
  /* else we got discover response but with no streams; we're done */
  else {
    log::error("BTA_AV_STR_DISC_FAIL_EVT: peer_addr={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
    bta_av_ssm_execute(p_scb, BTA_AV_STR_DISC_FAIL_EVT, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_save_caps
 *
 * Description      report the SNK SEP capabilities to application
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_save_caps(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  AvdtpSepConfig cfg;
  tAVDT_SEP_INFO* p_info = &p_scb->sep_info[p_scb->sep_info_idx];
  uint8_t old_wait = p_scb->wait;
  bool getcap_done = false;

  log::verbose(
      "peer {} bta_handle:0x{:x} num_seps:{} sep_info_idx:{} wait:0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
      p_scb->num_seps, p_scb->sep_info_idx, p_scb->wait);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->peer_cap.codec_info));

  cfg = p_scb->peer_cap;
  /* let application know the capability of the SNK */
  if (p_scb->p_cos->getcfg(p_scb->hndl, p_scb->PeerAddress(), cfg.codec_info,
                           &p_scb->sep_info_idx, p_info->seid, &cfg.num_protect,
                           cfg.protect_info) != A2DP_SUCCESS) {
    p_scb->sep_info_idx++;
    log::verbose("result: next sep_info_idx:{}", p_scb->sep_info_idx);
  } else {
    // All capabilities found
    getcap_done = true;
    log::verbose("result: done sep_info_idx:{}", p_scb->sep_info_idx);
  }
  log::verbose("codec: {}", A2DP_CodecInfoString(cfg.codec_info));

  if (p_scb->num_seps > p_scb->sep_info_idx && !getcap_done) {
    /* Some devices have seps at the end of the discover list, which is not */
    /* matching media type(video not audio).                                */
    /* In this case, we are done with getcap without sending another        */
    /* request to AVDT.                                                     */
    if (!bta_av_next_getcap(p_scb, p_data)) getcap_done = true;
  } else {
    getcap_done = true;
  }

  if (getcap_done) {
    log::verbose("getcap_done: num_seps:{} sep_info_idx:{} wait:0x{:x}",
                 p_scb->num_seps, p_scb->sep_info_idx, p_scb->wait);
    p_scb->wait &= ~(BTA_AV_WAIT_ACP_CAPS_ON | BTA_AV_WAIT_ACP_CAPS_STARTED);
    if (old_wait & BTA_AV_WAIT_ACP_CAPS_STARTED) {
      bta_av_start_ok(p_scb, NULL);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_set_use_rc
 *
 * Description      set to use AVRC for this stream control block.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_set_use_rc(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  p_scb->use_rc = true;
}

/*******************************************************************************
 *
 * Function         bta_av_cco_close
 *
 * Description      call close call-out function.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_cco_close(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("peer {} bta_handle:0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl);
  p_scb->p_cos->close(p_scb->hndl, p_scb->PeerAddress());
}

/*******************************************************************************
 *
 * Function         bta_av_open_failed
 *
 * Description      Failed to open an AVDT stream
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_open_failed(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  bool is_av_opened = false;
  tBTA_AV_SCB* p_opened_scb = NULL;
  uint8_t idx;

  log::error("peer_addr={}", ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
  p_scb->open_status = BTA_AV_FAIL_STREAM;
  bta_av_cco_close(p_scb, p_data);

  /* check whether there is already an opened audio or video connection with the
   * same device */
  for (idx = 0; (idx < BTA_AV_NUM_STRS) && (!is_av_opened); idx++) {
    p_opened_scb = bta_av_cb.p_scb[idx];
    if (p_opened_scb && (p_opened_scb->state == BTA_AV_OPEN_SST) &&
        (p_opened_scb->PeerAddress() == p_scb->PeerAddress()))
      is_av_opened = true;
  }

  /* if there is already an active AV connnection with the same bd_addr,
     don't send disconnect req, just report the open event with
     BTA_AV_FAIL_GET_CAP status */
  if (is_av_opened) {
    tBTA_AV bta_av_data = {.open = {
                               .chnl = p_scb->chnl,
                               .hndl = p_scb->hndl,
                               .bd_addr = p_scb->PeerAddress(),
                               .status = BTA_AV_FAIL_GET_CAP,
                               .starting = bta_av_chk_start(p_scb),
                               .edr = 0,
                               .sep = AVDT_TSEP_INVALID,
                           }};
    /* set the state back to initial state */
    bta_av_set_scb_sst_init(p_scb);

    if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC) {
      bta_av_data.open.sep = AVDT_TSEP_SNK;
    } else if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SNK) {
      bta_av_data.open.sep = AVDT_TSEP_SRC;
    }

    log::error(
        "there is already an active connection: peer_addr={} chnl={} "
        "hndl=0x{:x} status={} starting={} edr={}",
        ADDRESS_TO_LOGGABLE_CSTR(bta_av_data.open.bd_addr),
        bta_av_data.open.chnl, bta_av_data.open.hndl, bta_av_data.open.status,
        bta_av_data.open.starting, bta_av_data.open.edr);

    (*bta_av_cb.p_cback)(BTA_AV_OPEN_EVT, &bta_av_data);
  } else {
    AVDT_DisconnectReq(p_scb->PeerAddress(), &bta_av_proc_stream_evt);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_getcap_results
 *
 * Description      Handle the AVDTP get capabilities results.  Check the codec
 *                  type and see if it matches ours.  If it does not, get the
 *                  capabilities of the next stream, if any.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_getcap_results(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  AvdtpSepConfig cfg = p_scb->cfg;
  uint8_t media_type = A2DP_GetMediaType(p_scb->peer_cap.codec_info);
  tAVDT_SEP_INFO* p_info = &p_scb->sep_info[p_scb->sep_info_idx];

  cfg.num_codec = 1;
  cfg.num_protect = p_scb->peer_cap.num_protect;
  memcpy(cfg.codec_info, p_scb->peer_cap.codec_info, AVDT_CODEC_SIZE);
  memcpy(cfg.protect_info, p_scb->peer_cap.protect_info, AVDT_PROTECT_SIZE);

  log::verbose("peer {} bta_handle:0x{:x} num_codec:{} psc_mask=0x{:x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               p_scb->peer_cap.num_codec, p_scb->cfg.psc_mask);
  log::verbose("media type 0x{:x}, 0x{:x}", media_type, p_scb->media_type);
  log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));

  /* if codec present and we get a codec configuration */
  if ((p_scb->peer_cap.num_codec != 0) && (media_type == p_scb->media_type) &&
      (p_scb->p_cos->getcfg(p_scb->hndl, p_scb->PeerAddress(), cfg.codec_info,
                            &p_scb->sep_info_idx, p_info->seid,
                            &cfg.num_protect,
                            cfg.protect_info) == A2DP_SUCCESS)) {
    /* UUID for which connection was initiatied */
    uint16_t uuid_int = p_scb->uuid_int;

    /* save copy of codec configuration */
    p_scb->cfg = cfg;

    log::verbose("result: sep_info_idx={}", p_scb->sep_info_idx);
    log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));

    log::verbose("initiator UUID = 0x{:x}", uuid_int);
    if (uuid_int == UUID_SERVCLASS_AUDIO_SOURCE)
      bta_av_adjust_seps_idx(p_scb,
                             bta_av_get_scb_handle(p_scb, AVDT_TSEP_SRC));
    else if (uuid_int == UUID_SERVCLASS_AUDIO_SINK)
      bta_av_adjust_seps_idx(p_scb,
                             bta_av_get_scb_handle(p_scb, AVDT_TSEP_SNK));
    log::info("sep_idx={} avdt_handle={} bta_handle=0x{:x}", p_scb->sep_idx,
              p_scb->avdt_handle, p_scb->hndl);

    /* use only the services peer supports */
    cfg.psc_mask &= p_scb->peer_cap.psc_mask;
    p_scb->cur_psc_mask = cfg.psc_mask;
    log::verbose(
        "peer {} bta_handle:0x{:x} sep_idx:{} sep_info_idx:{} "
        "cur_psc_mask:0x{:x}",
        ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
        p_scb->sep_idx, p_scb->sep_info_idx, p_scb->cur_psc_mask);

    if ((uuid_int == UUID_SERVCLASS_AUDIO_SINK) &&
        (p_scb->seps[p_scb->sep_idx].p_app_sink_data_cback != NULL)) {
      log::verbose("configure decoder for Sink connection");
      tBTA_AV_MEDIA av_sink_codec_info = {
          .avk_config =
              {
                  .codec_info = p_scb->cfg.codec_info,
                  .bd_addr = p_scb->PeerAddress(),
              },
      };
      p_scb->seps[p_scb->sep_idx].p_app_sink_data_cback(
          p_scb->PeerAddress(), BTA_AV_SINK_MEDIA_CFG_EVT, &av_sink_codec_info);
    }

    if (uuid_int == UUID_SERVCLASS_AUDIO_SOURCE) {
      A2DP_AdjustCodec(cfg.codec_info);
    }

    /* open the stream */
    AVDT_OpenReq(p_scb->seps[p_scb->sep_idx].av_handle, p_scb->PeerAddress(),
                 p_scb->hdi, p_scb->sep_info[p_scb->sep_info_idx].seid, &cfg);
  } else {
    /* try the next stream, if any */
    p_scb->sep_info_idx++;
    bta_av_next_getcap(p_scb, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_setconfig_rej
 *
 * Description      Send AVDTP set config reject.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_setconfig_rej(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t avdt_handle = p_data->ci_setconfig.avdt_handle;

  bta_av_adjust_seps_idx(p_scb, avdt_handle);
  log::info("sep_idx={} avdt_handle={} bta_handle=0x{:x}", p_scb->sep_idx,
            p_scb->avdt_handle, p_scb->hndl);
  AVDT_ConfigRsp(p_scb->avdt_handle, p_scb->avdt_label, AVDT_ERR_UNSUP_CFG, 0);

  tBTA_AV bta_av_data = {
      .reject =
          {
              .bd_addr = p_scb->PeerAddress(),
              .hndl = p_scb->hndl,
          },
  };

  (*bta_av_cb.p_cback)(BTA_AV_REJECT_EVT, &bta_av_data);
}

/*******************************************************************************
 *
 * Function         bta_av_discover_req
 *
 * Description      Send an AVDTP discover request to the peer.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_discover_req(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  /* send avdtp discover request */

  AVDT_DiscoverReq(p_scb->PeerAddress(), p_scb->hdi, p_scb->sep_info,
                   BTA_AV_NUM_SEPS, &bta_av_proc_stream_evt);
}

/*******************************************************************************
 *
 * Function         bta_av_conn_failed
 *
 * Description      AVDTP connection failed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_conn_failed(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::error("peer_addr={} open_status={}",
             ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
             p_scb->open_status);

  p_scb->open_status = BTA_AV_FAIL_STREAM;
  bta_av_str_closed(p_scb, p_data);
}

/*******************************************************************************
 *
 * Function         bta_av_do_start
 *
 * Description      Start stream.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_do_start(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::info(
      "A2dp stream start peer:{} sco_occupied:{} av_role:0x{:x} started:{} "
      "wait:0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
      logbool(bta_av_cb.sco_occupied), p_scb->role, logbool(p_scb->started),
      p_scb->wait);
  if (bta_av_cb.sco_occupied) {
    log::warn("A2dp stream start failed");
    bta_av_start_failed(p_scb, p_data);
    return;
  }

  if (p_scb->started) {
    p_scb->role |= BTA_AV_ROLE_START_INT;
    if (p_scb->wait != 0) {
      log::warn(
          "peer {} start stream request ignored: already waiting: "
          "sco_occupied:{} role:0x{:x} started:{} wait:0x{:x}",
          ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
          logbool(bta_av_cb.sco_occupied), p_scb->role, logbool(p_scb->started),
          p_scb->wait);
      return;
    }
    if (p_scb->role & BTA_AV_ROLE_SUSPEND) {
      notify_start_failed(p_scb);
    } else {
      if (p_data) {
        bta_av_set_use_latency_mode(p_scb, p_data->do_start.use_latency_mode);
      }
      bta_av_start_ok(p_scb, NULL);
    }
    return;
  }

  if ((p_scb->role & BTA_AV_ROLE_START_INT) != 0) {
    log::warn(
        "peer {} start stream request ignored: already initiated: "
        "sco_occupied:{} role:0x{:x} started:{} wait:0x{:x}",
        ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
        logbool(bta_av_cb.sco_occupied), p_scb->role, logbool(p_scb->started),
        p_scb->wait);
    return;
  }

  p_scb->role |= BTA_AV_ROLE_START_INT;
  bta_sys_busy(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
  /* disallow role switch during streaming, only if we are the central role
   * i.e. allow role switch, if we are peripheral.
   * It would not hurt us, if the peer device wants us to be central
   * disable sniff mode unconditionally during streaming */
  tHCI_ROLE cur_role;
  if ((BTM_GetRole(p_scb->PeerAddress(), &cur_role) == BTM_SUCCESS) &&
      (cur_role == HCI_ROLE_CENTRAL)) {
    BTM_block_role_switch_and_sniff_mode_for(p_scb->PeerAddress());
  } else {
    BTM_block_sniff_mode_for(p_scb->PeerAddress());
  }

  uint16_t result = AVDT_StartReq(&p_scb->avdt_handle, 1);
  if (result != AVDT_SUCCESS) {
    log::error("AVDT_StartReq failed for peer {} result:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), result);
    bta_av_start_failed(p_scb, p_data);
  } else if (p_data) {
    bta_av_set_use_latency_mode(p_scb, p_data->do_start.use_latency_mode);
  }
  log::info(
      "peer {} start requested: sco_occupied:{} role:0x{:x} started:{} "
      "wait:0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
      logbool(bta_av_cb.sco_occupied), p_scb->role, logbool(p_scb->started),
      p_scb->wait);
}

/*******************************************************************************
 *
 * Function         bta_av_str_stopped
 *
 * Description      Stream stopped.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_str_stopped(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t start = p_scb->started;
  bool sus_evt = true;
  BT_HDR* p_buf;

  log::info("peer {} bta_handle:0x{:x} audio_open_cnt:{}, p_data {} start:{}",
            ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
            bta_av_cb.audio_open_cnt, fmt::ptr(p_data), start);

  bta_sys_idle(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
  BTM_unblock_role_switch_and_sniff_mode_for(p_scb->PeerAddress());

  if (p_scb->co_started) {
    if (bta_av_cb.offload_started_hndl == p_scb->hndl) {
      bta_av_vendor_offload_stop();
      bta_av_cb.offload_started_hndl = BTA_AV_INVALID_HANDLE;
    } else if (bta_av_cb.offload_start_pending_hndl == p_scb->hndl) {
      log::warn("Stop pending offload start command");
      bta_av_vendor_offload_stop();
      bta_av_cb.offload_start_pending_hndl = BTA_AV_INVALID_HANDLE;
    }

    bta_av_stream_chg(p_scb, false);
    p_scb->co_started = false;

    p_scb->p_cos->stop(p_scb->hndl, p_scb->PeerAddress());
  }

  /* if q_info.a2dp_list is not empty, drop it now */
  if (BTA_AV_CHNL_AUDIO == p_scb->chnl) {
    while (!list_is_empty(p_scb->a2dp_list)) {
      p_buf = (BT_HDR*)list_front(p_scb->a2dp_list);
      list_remove(p_scb->a2dp_list, p_buf);
      osi_free(p_buf);
    }

    /* drop the audio buffers queued in L2CAP */
    if (p_data && p_data->api_stop.flush)
      L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_ALL);
  }

  tBTA_AV_SUSPEND suspend_rsp = {};
  suspend_rsp.chnl = p_scb->chnl;
  suspend_rsp.hndl = p_scb->hndl;

  if (p_data && p_data->api_stop.suspend) {
    log::verbose("peer {} suspending: {}, sup:{}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), start,
                 p_scb->suspend_sup);
    if ((start) && (p_scb->suspend_sup)) {
      sus_evt = false;
      p_scb->l2c_bufs = 0;
      AVDT_SuspendReq(&p_scb->avdt_handle, 1);
    }

    /* send SUSPEND_EVT event only if not in reconfiguring state and sus_evt is
     * true*/
    if ((sus_evt) && (p_scb->state != BTA_AV_RCFG_SST)) {
      suspend_rsp.status = BTA_AV_SUCCESS;
      suspend_rsp.initiator = true;
      tBTA_AV bta_av_data;
      bta_av_data.suspend = suspend_rsp;
      (*bta_av_cb.p_cback)(BTA_AV_SUSPEND_EVT, &bta_av_data);
    }
  } else {
    suspend_rsp.status = BTA_AV_SUCCESS;
    suspend_rsp.initiator = true;
    log::verbose("status {}", suspend_rsp.status);

    // Send STOP_EVT event only if not in reconfiguring state.
    // However, we should send STOP_EVT if we are reconfiguring when taking
    // the Close->Configure->Open->Start path.
    if (p_scb->state != BTA_AV_RCFG_SST ||
        (p_data && p_data->api_stop.reconfig_stop)) {
      tBTA_AV bta_av_data;
      bta_av_data.suspend = suspend_rsp;
      (*bta_av_cb.p_cback)(BTA_AV_STOP_EVT, &bta_av_data);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_reconfig
 *
 * Description      process the reconfigure request.
 *                  save the parameter in control block and
 *                  suspend, reconfigure or close the stream
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_reconfig(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  AvdtpSepConfig* p_cfg;
  tBTA_AV_API_STOP stop = {};
  tBTA_AV_API_RCFG* p_rcfg = &p_data->api_reconfig;

  log::verbose("r:{}, s:{} idx: {} (o:{})", p_scb->recfg_sup,
               p_scb->suspend_sup, p_scb->rcfg_idx, p_scb->sep_info_idx);

  p_scb->num_recfg = 0;
  /* store the new configuration in control block */
  p_cfg = &p_scb->cfg;

  alarm_cancel(p_scb->avrc_ct_timer);

  log::debug(
      "p_scb->sep_info_idx={} p_scb->rcfg_idx={} p_rcfg->sep_info_idx={}",
      p_scb->sep_info_idx, p_scb->rcfg_idx, p_rcfg->sep_info_idx);
  log::debug("Peer capable codec: {}",
             A2DP_CodecInfoString(p_scb->peer_cap.codec_info));
  log::debug("Current codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));
  log::debug("Reconfig codec: {}", A2DP_CodecInfoString(p_rcfg->codec_info));

  BTM_LogHistory(
      kBtmLogTag, p_scb->PeerAddress(), "Codec reconfig",
      base::StringPrintf("%s => %s", A2DP_CodecName(p_scb->cfg.codec_info),
                         A2DP_CodecName(p_rcfg->codec_info)));

  p_cfg->num_protect = p_rcfg->num_protect;
  memcpy(p_cfg->codec_info, p_rcfg->codec_info, AVDT_CODEC_SIZE);
  memcpy(p_cfg->protect_info, p_rcfg->p_protect_info, p_rcfg->num_protect);
  p_scb->rcfg_idx = p_rcfg->sep_info_idx;
  p_cfg->psc_mask = p_scb->cur_psc_mask;

  // If the requested SEP index is same as the current one, then we
  // can Suspend->Reconfigure->Start.
  // Otherwise, we have to Close->Configure->Open->Start or
  // Close->Configure->Open for streams that are / are not started.
  if ((p_scb->rcfg_idx == p_scb->sep_info_idx) && p_rcfg->suspend &&
      p_scb->recfg_sup && p_scb->suspend_sup) {
    if (p_scb->started) {
      // Suspend->Reconfigure->Start
      stop.flush = false;
      stop.suspend = true;
      stop.reconfig_stop = false;
      bta_av_str_stopped(p_scb, (tBTA_AV_DATA*)&stop);
    } else {
      // Reconfigure
      log::verbose("reconfig");
      log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));
      AVDT_ReconfigReq(p_scb->avdt_handle, &p_scb->cfg);
      p_scb->cfg.psc_mask = p_scb->cur_psc_mask;
    }
  } else {
    // Close the stream first, and then Configure it
    log::verbose("Close/Open started: {} state: {} num_protect: {}",
                 p_scb->started, p_scb->state, p_cfg->num_protect);
    if (p_scb->started) {
      // Close->Configure->Open->Start
      if ((p_scb->rcfg_idx != p_scb->sep_info_idx) && p_scb->recfg_sup) {
        // Make sure we trigger STOP_EVT when taking the longer road to
        // reconfiguration, otherwise we don't call Start.
        stop.flush = false;
        stop.suspend = false;
        stop.reconfig_stop = true;
        bta_av_str_stopped(p_scb, (tBTA_AV_DATA*)&stop);
      } else {
        bta_av_str_stopped(p_scb, NULL);
      }
      p_scb->started = false;
    } else {
      // Close->Configure->Open
      bta_av_str_stopped(p_scb, NULL);
    }
    // Drop the buffers queued in L2CAP
    L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_ALL);
    AVDT_CloseReq(p_scb->avdt_handle);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_data_path
 *
 * Description      Handle stream data path.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_data_path(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  BT_HDR* p_buf = NULL;
  uint32_t timestamp;
  bool new_buf = false;
  uint8_t m_pt = 0x60;
  tAVDT_DATA_OPT_MASK opt;

  if (!p_scb->started) return;

  if (p_scb->cong) return;

  if (p_scb->use_rtp_header_marker_bit) {
    m_pt |= AVDT_MARKER_SET;
  }

  // Always get the current number of bufs que'd up
  p_scb->l2c_bufs =
      (uint8_t)L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_GET);

  if (!list_is_empty(p_scb->a2dp_list)) {
    p_buf = (BT_HDR*)list_front(p_scb->a2dp_list);
    list_remove(p_scb->a2dp_list, p_buf);
    /* use q_info.a2dp data, read the timestamp */
    timestamp = *(uint32_t*)(p_buf + 1);
  } else {
    new_buf = true;
    /* A2DP_list empty, call co_data, dup data to other channels */
    p_buf = p_scb->p_cos->data(p_scb->cfg.codec_info, &timestamp);

    if (p_buf) {
      /* use the offset area for the time stamp */
      *(uint32_t*)(p_buf + 1) = timestamp;

      /* dup the data to other channels */
      bta_av_dup_audio_buf(p_scb, p_buf);
    }
  }

  if (p_buf) {
    if (p_scb->l2c_bufs < (BTA_AV_QUEUE_DATA_CHK_NUM)) {
      /* There's a buffer, just queue it to L2CAP.
       * There's no need to increment it here, it is always read from
       * L2CAP (see above).
       */

      /* opt is a bit mask, it could have several options set */
      opt = AVDT_DATA_OPT_NONE;
      if (p_scb->no_rtp_header) {
        opt |= AVDT_DATA_OPT_NO_RTP;
      }

      //
      // Fragment the payload if larger than the MTU.
      // NOTE: The fragmentation is RTP-compatibie.
      //
      size_t extra_fragments_n = 0;
      if (p_buf->len > 0) {
        extra_fragments_n = (p_buf->len / p_scb->stream_mtu) +
                            ((p_buf->len % p_scb->stream_mtu) ? 1 : 0) - 1;
      }
      std::vector<BT_HDR*> extra_fragments;
      extra_fragments.reserve(extra_fragments_n);

      uint8_t* data_begin = (uint8_t*)(p_buf + 1) + p_buf->offset;
      uint8_t* data_end = (uint8_t*)(p_buf + 1) + p_buf->offset + p_buf->len;
      while (extra_fragments_n-- > 0) {
        data_begin += p_scb->stream_mtu;
        size_t fragment_len = data_end - data_begin;
        if (fragment_len > p_scb->stream_mtu) fragment_len = p_scb->stream_mtu;

        BT_HDR* p_buf2 = (BT_HDR*)osi_malloc(BT_DEFAULT_BUFFER_SIZE);
        p_buf2->offset = p_buf->offset;
        p_buf2->len = 0;
        p_buf2->layer_specific = 0;
        uint8_t* packet2 =
            (uint8_t*)(p_buf2 + 1) + p_buf2->offset + p_buf2->len;
        memcpy(packet2, data_begin, fragment_len);
        p_buf2->len += fragment_len;
        extra_fragments.push_back(p_buf2);
        p_buf->len -= fragment_len;
      }

      if (!extra_fragments.empty()) {
        // Reset the RTP Marker bit for all fragments except the last one
        m_pt &= ~AVDT_MARKER_SET;
      }
      AVDT_WriteReqOpt(p_scb->avdt_handle, p_buf, timestamp, m_pt, opt);
      for (size_t i = 0; i < extra_fragments.size(); i++) {
        if (i + 1 == extra_fragments.size()) {
          // Set the RTP Marker bit for the last fragment
          m_pt |= AVDT_MARKER_SET;
        }
        BT_HDR* p_buf2 = extra_fragments[i];
        AVDT_WriteReqOpt(p_scb->avdt_handle, p_buf2, timestamp, m_pt, opt);
      }
      p_scb->cong = true;
    } else {
      /* there's a buffer, but L2CAP does not seem to be moving data */
      if (new_buf) {
        /* just got this buffer from co_data,
         * put it in queue */
        list_append(p_scb->a2dp_list, p_buf);
      } else {
        /* just dequeue it from the a2dp_list */
        if (list_length(p_scb->a2dp_list) < 3) {
          /* put it back to the queue */
          list_prepend(p_scb->a2dp_list, p_buf);
        } else {
          /* too many buffers in a2dp_list, drop it. */
          bta_av_co_audio_drop(p_scb->hndl, p_scb->PeerAddress());
          osi_free(p_buf);
        }
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_start_ok
 *
 * Description      Stream started.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_start_ok(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  bool initiator = false;
  bool suspend = false;
  uint8_t new_role = p_scb->role;
  BT_HDR_RIGID hdr;
  tHCI_ROLE cur_role;
  uint8_t local_tsep = p_scb->seps[p_scb->sep_idx].tsep;

  log::info("peer {} bta_handle:0x{:x} wait:0x{:x} role:0x{:x} local_tsep:{}",
            ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
            p_scb->wait, p_scb->role, local_tsep);

  p_scb->started = true;

  if (local_tsep == AVDT_TSEP_SRC) {
    // The RTP Header marker bit for the A2DP Source encoder
    A2dpCodecConfig* codec_config =
        bta_av_get_a2dp_peer_current_codec(p_scb->PeerAddress());
    CHECK(codec_config != nullptr);
    p_scb->use_rtp_header_marker_bit = codec_config->useRtpHeaderMarkerBit();
  }

  if (p_scb->sco_suspend) {
    p_scb->sco_suspend = false;
  }

  if (new_role & BTA_AV_ROLE_START_INT) initiator = true;

  /* for A2DP SINK we do not send get_caps */
  if ((p_scb->avdt_handle == p_scb->seps[p_scb->sep_idx].av_handle) &&
      (local_tsep == AVDT_TSEP_SNK)) {
    p_scb->wait &= ~(BTA_AV_WAIT_ACP_CAPS_ON);
    log::verbose("local SEP type is SNK new wait is 0x{:x}", p_scb->wait);
  }
  if (p_scb->wait & BTA_AV_WAIT_ROLE_SW_FAILED) {
    /* role switch has failed */
    log::error(
        "peer {} role switch failed: bta_handle:0x{:x} wait:0x{:x}, "
        "role:0x{:x}",
        ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
        p_scb->wait, p_scb->role);
    p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_FAILED;
    p_data = (tBTA_AV_DATA*)&hdr;
    hdr.offset = BTA_AV_RS_FAIL;
  }
  log::verbose("peer {} wait:0x{:x} use_rtp_header_marker_bit:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->wait,
               (p_scb->use_rtp_header_marker_bit) ? "true" : "false");

  if (p_data && (p_data->hdr.offset != BTA_AV_RS_NONE)) {
    p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;
    if (p_data->hdr.offset == BTA_AV_RS_FAIL) {
      bta_sys_idle(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
      tBTA_AV bta_av_data = {
          .start =
              {
                  .chnl = p_scb->chnl,
                  .hndl = p_scb->hndl,
                  .status = BTA_AV_FAIL_ROLE,
                  .initiator = initiator,
                  .suspending = false,
              },
      };
      (*bta_av_cb.p_cback)(BTA_AV_START_EVT, &bta_av_data);
      return;
    }
  }

  if (!bta_av_link_role_ok(p_scb, A2DP_SET_ONE_BIT))
    p_scb->q_tag = BTA_AV_Q_TAG_START;
  else {
    /* The wait flag may be set here while we are already central on the link */
    /* this could happen if a role switch complete event occurred during
     * reconfig */
    /* if we are now central on the link, there is no need to wait for the role
     * switch, */
    /* complete anymore so we can clear the wait for role switch flag */
    p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;
  }

  if (p_scb->wait &
      (BTA_AV_WAIT_ROLE_SW_RES_OPEN | BTA_AV_WAIT_ROLE_SW_RES_START)) {
    p_scb->wait |= BTA_AV_WAIT_ROLE_SW_STARTED;
    p_scb->q_tag = BTA_AV_Q_TAG_START;
  }

  if (p_scb->wait) {
    log::error("peer {} wait:0x{:x} q_tag:{} not started",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->wait,
               p_scb->q_tag);
    /* Clear first bit of p_scb->wait and not to return from this point else
     * HAL layer gets blocked. And if there is delay in Get Capability response
     * as
     * first bit of p_scb->wait is cleared hence it ensures bt_av_start_ok is
     * not called
     * again from bta_av_save_caps.
     */
    p_scb->wait &= ~BTA_AV_WAIT_ACP_CAPS_ON;
  }

  /* tell role manager to check M/S role */
  bta_sys_conn_open(BTA_ID_AV, p_scb->app_id, p_scb->PeerAddress());

  bta_sys_busy(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());

  if (p_scb->media_type == AVDT_MEDIA_TYPE_AUDIO) {
    /* in normal logic, conns should be bta_av_cb.audio_count - 1,
     * However, bta_av_stream_chg is not called to increase
     * bta_av_cb.audio_count yet.
     * If the code were to be re-arranged for some reasons, this number may need
     * to be changed
     */
    p_scb->co_started = bta_av_cb.audio_open_cnt;
  }

  /* clear the congestion flag */
  p_scb->cong = false;

  if (new_role & BTA_AV_ROLE_START_INT) {
    new_role &= ~BTA_AV_ROLE_START_INT;
  } else if ((new_role & BTA_AV_ROLE_AD_ACP) &&
             (new_role & BTA_AV_ROLE_SUSPEND_OPT)) {
    suspend = true;
  }

  if (!suspend) {
    p_scb->q_tag = BTA_AV_Q_TAG_STREAM;
    bta_av_stream_chg(p_scb, true);
  }

  {
    /* If sink starts stream, disable sniff mode here */
    if (!initiator) {
      /* If source is the central role, disable role switch during streaming.
       * Otherwise allow role switch, if source is peripheral.
       * Because it would not hurt source, if the peer device wants source to be
       * central.
       * disable sniff mode unconditionally during streaming */
      if ((BTM_GetRole(p_scb->PeerAddress(), &cur_role) == BTM_SUCCESS) &&
          (cur_role == HCI_ROLE_CENTRAL)) {
        BTM_block_role_switch_and_sniff_mode_for(p_scb->PeerAddress());
      } else {
        BTM_block_sniff_mode_for(p_scb->PeerAddress());
      }
    }

    p_scb->role = new_role;
    p_scb->role &= ~BTA_AV_ROLE_AD_ACP;
    p_scb->role &= ~BTA_AV_ROLE_SUSPEND_OPT;

    p_scb->no_rtp_header = false;
    p_scb->p_cos->start(p_scb->hndl, p_scb->PeerAddress(),
                        p_scb->cfg.codec_info, &p_scb->no_rtp_header);
    p_scb->co_started = true;

    log::verbose("peer {} suspending: {}, role:0x{:x}, init {}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), suspend,
                 p_scb->role, initiator);

    tBTA_AV bta_av_data = {
        .start =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .status = BTA_AV_SUCCESS,
                .initiator = initiator,
                .suspending = suspend,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_START_EVT, &bta_av_data);

    if (suspend) {
      p_scb->role |= BTA_AV_ROLE_SUSPEND;
      p_scb->cong = true; /* do not allow the media data to go through */
      /* do not duplicate the media packets to this channel */
      p_scb->p_cos->stop(p_scb->hndl, p_scb->PeerAddress());
      p_scb->co_started = false;
      tBTA_AV_API_STOP stop = {
          .hdr = {},
          .suspend = true,
          .flush = false,
          .reconfig_stop = false,
      };
      bta_av_ssm_execute(p_scb, BTA_AV_AP_STOP_EVT, (tBTA_AV_DATA*)&stop);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_start_failed
 *
 * Description      Stream start failed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_start_failed(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::error(
      "peer {} bta_handle:0x{:x} audio_open_cnt:{} started:{} co_started:{}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
      bta_av_cb.audio_open_cnt, logbool(p_scb->started), p_scb->co_started);

  if (!p_scb->started && !p_scb->co_started) {
    bta_sys_idle(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
    notify_start_failed(p_scb);
  }

  BTM_unblock_role_switch_and_sniff_mode_for(p_scb->PeerAddress());
  p_scb->sco_suspend = false;
}

/*******************************************************************************
 *
 * Function         bta_av_str_closed
 *
 * Description      Stream closed.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_str_closed(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV data = {};
  tBTA_AV_EVT event = {};

  log::warn("peer {} bta_handle:0x{:x} open_status:{} chnl:{} co_started:{}",
            ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
            p_scb->open_status, p_scb->chnl, p_scb->co_started);

  BTM_unblock_role_switch_and_sniff_mode_for(p_scb->PeerAddress());
  if (bta_av_cb.audio_open_cnt <= 1) {
    BTM_default_unblock_role_switch();
  }

  L2CA_SetMediaStreamChannel(p_scb->l2c_cid, false);

  if (p_scb->open_status != BTA_AV_SUCCESS) {
    /* must be failure when opening the stream */
    data.open.bd_addr = p_scb->PeerAddress();
    data.open.status = p_scb->open_status;
    data.open.chnl = p_scb->chnl;
    data.open.hndl = p_scb->hndl;

    if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC)
      data.open.sep = AVDT_TSEP_SNK;
    else if (p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SNK)
      data.open.sep = AVDT_TSEP_SRC;

    event = BTA_AV_OPEN_EVT;
    p_scb->open_status = BTA_AV_SUCCESS;

    bta_sys_conn_close(BTA_ID_AV, p_scb->app_id, p_scb->PeerAddress());
    bta_av_cleanup(p_scb, p_data);
    (*bta_av_cb.p_cback)(event, &data);
  } else {
    /* do stop if we were started */
    if (p_scb->co_started) {
      bta_av_str_stopped(p_scb, NULL);
    }

    {
      p_scb->p_cos->close(p_scb->hndl, p_scb->PeerAddress());
      data.close.chnl = p_scb->chnl;
      data.close.hndl = p_scb->hndl;
      event = BTA_AV_CLOSE_EVT;

      bta_sys_conn_close(BTA_ID_AV, p_scb->app_id, p_scb->PeerAddress());
      bta_av_cleanup(p_scb, p_data);
      (*bta_av_cb.p_cback)(event, &data);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_clr_cong
 *
 * Description      Clear stream congestion flag.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_clr_cong(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("");
  if (p_scb->co_started) {
    p_scb->cong = false;
  }
}

/*******************************************************************************
 *
 * Function         bta_av_suspend_cfm
 *
 * Description      process the suspend response
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_suspend_cfm(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV_SUSPEND suspend_rsp = {};
  uint8_t err_code = p_data->str_msg.msg.hdr.err_code;

  log::verbose("peer {} bta_handle:0x{:x} audio_open_cnt:{} err_code:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               bta_av_cb.audio_open_cnt, err_code);

  if (!p_scb->started) {
    /* handle the condition where there is a collision of SUSPEND req from
     *either side
     ** Second SUSPEND req could be rejected. Do not treat this as a failure
     */
    log::warn("already suspended, ignore, err_code {}", err_code);
    return;
  }

  suspend_rsp.status = BTA_AV_SUCCESS;
  if (err_code && (err_code != AVDT_ERR_BAD_STATE)) {
    suspend_rsp.status = BTA_AV_FAIL;

    log::error("suspend failed, closing connection");

    /* SUSPEND failed. Close connection. */
    bta_av_ssm_execute(p_scb, BTA_AV_API_CLOSE_EVT, NULL);
  } else {
    /* only set started to false when suspend is successful */
    p_scb->started = false;
  }

  if (p_scb->role & BTA_AV_ROLE_SUSPEND) {
    p_scb->role &= ~BTA_AV_ROLE_SUSPEND;
    p_scb->cong = false;
  }

  bta_sys_idle(BTA_ID_AV, bta_av_cb.audio_open_cnt, p_scb->PeerAddress());
  BTM_unblock_role_switch_and_sniff_mode_for(p_scb->PeerAddress());

  /* in case that we received suspend_ind, we may need to call co_stop here */
  if (p_scb->co_started) {
    if (bta_av_cb.offload_started_hndl == p_scb->hndl) {
      bta_av_vendor_offload_stop();
      bta_av_cb.offload_started_hndl = BTA_AV_INVALID_HANDLE;
    } else if (bta_av_cb.offload_start_pending_hndl == p_scb->hndl) {
      log::warn("Stop pending offload start command");
      bta_av_vendor_offload_stop();
      bta_av_cb.offload_start_pending_hndl = BTA_AV_INVALID_HANDLE;
    }
    bta_av_stream_chg(p_scb, false);

    {
      p_scb->co_started = false;
      p_scb->p_cos->stop(p_scb->hndl, p_scb->PeerAddress());
    }
  }

  {
    suspend_rsp.chnl = p_scb->chnl;
    suspend_rsp.hndl = p_scb->hndl;
    suspend_rsp.initiator = p_data->str_msg.initiator;
    tBTA_AV bta_av_data;
    bta_av_data.suspend = suspend_rsp;
    (*bta_av_cb.p_cback)(BTA_AV_SUSPEND_EVT, &bta_av_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_str_ok
 *
 * Description      report reconfigure successful
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_str_ok(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  p_scb->l2c_cid = AVDT_GetL2CapChannel(p_scb->avdt_handle);
  log::verbose("peer {} bta_handle:0x{:x} l2c_cid:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               p_scb->l2c_cid);

  if (p_data != NULL) {
    // p_data could be NULL if the reconfig was triggered by the local device
    p_scb->stream_mtu =
        p_data->str_msg.msg.open_ind.peer_mtu - AVDT_MEDIA_HDR_SIZE;
    log::verbose("l2c_cid: 0x{:x} stream_mtu: {}", p_scb->l2c_cid,
                 p_scb->stream_mtu);
    p_scb->p_cos->update_mtu(p_scb->hndl, p_scb->PeerAddress(),
                             p_scb->stream_mtu);
  }

  /* rc listen */
  bta_av_st_rc_timer(p_scb, NULL);

  /* No need to keep the role bits once reconfig is done. */
  p_scb->role &= ~BTA_AV_ROLE_AD_ACP;
  p_scb->role &= ~BTA_AV_ROLE_SUSPEND_OPT;
  p_scb->role &= ~BTA_AV_ROLE_START_INT;

  {
    /* reconfigure success  */
    tBTA_AV bta_av_data = {
        .reconfig =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .status = BTA_AV_SUCCESS,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_RECONFIG_EVT, &bta_av_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_failed
 *
 * Description      process reconfigure failed
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_failed(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::error("num_recfg={} conn_lcb=0x{:x} peer_addr={}", p_scb->num_recfg,
             bta_av_cb.conn_lcb,
             ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));

  if (p_scb->num_recfg > BTA_AV_RECONFIG_RETRY) {
    bta_av_cco_close(p_scb, p_data);
    /* report failure */
    tBTA_AV bta_av_data = {
        .reconfig =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .status = BTA_AV_FAIL_STREAM,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_RECONFIG_EVT, &bta_av_data);
    /* go to closing state */
    bta_av_ssm_execute(p_scb, BTA_AV_API_CLOSE_EVT, NULL);
  } else {
    /* open failed. try again */
    p_scb->num_recfg++;
    // conn_lcb is the index bitmask of all used LCBs, and since LCB and SCB use
    // the same index, it should be safe to use SCB index here.
    if ((bta_av_cb.conn_lcb & (1 << p_scb->hdi)) != 0) {
      AVDT_DisconnectReq(p_scb->PeerAddress(), &bta_av_proc_stream_evt);
    } else {
      log::warn("conn_lcb=0x{:x} bta_handle=0x{:x} (hdi={}) no link",
                bta_av_cb.conn_lcb, p_scb->hndl, p_scb->hdi);
      bta_av_connect_req(p_scb, NULL);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_connect
 *
 * Description      stream closed. reconnect the stream
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_connect(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("");

  p_scb->cong = false;
  p_scb->num_recfg++;
  log::verbose("num_recfg: {}", p_scb->num_recfg);
  if (p_scb->num_recfg > BTA_AV_RECONFIG_RETRY) {
    /* let bta_av_rcfg_failed report fail */
    bta_av_rcfg_failed(p_scb, NULL);
  } else {
    AVDT_ConnectReq(p_scb->PeerAddress(), p_scb->hdi, &bta_av_proc_stream_evt);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_discntd
 *
 * Description      AVDT disconnected. reconnect the stream
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_discntd(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::error("num_recfg={} conn_lcb=0x{:x} peer_addr={}", p_scb->num_recfg,
             bta_av_cb.conn_lcb,
             ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));

  p_scb->num_recfg++;
  if (p_scb->num_recfg > BTA_AV_RECONFIG_RETRY) {
    /* report failure */
    tBTA_AV bta_av_data = {
        .reconfig =
            {
                .chnl = p_scb->chnl,
                .hndl = p_scb->hndl,
                .status = BTA_AV_FAIL_STREAM,
            },
    };
    (*bta_av_cb.p_cback)(BTA_AV_RECONFIG_EVT, &bta_av_data);
    /* report close event & go to init state */
    bta_av_ssm_execute(p_scb, BTA_AV_STR_DISC_FAIL_EVT, NULL);
  } else {
    AVDT_ConnectReq(p_scb->PeerAddress(), p_scb->hdi, &bta_av_proc_stream_evt);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_suspend_cont
 *
 * Description      received the suspend response.
 *                  continue to reconfigure the stream
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_suspend_cont(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t err_code = p_data->str_msg.msg.hdr.err_code;

  log::verbose("err_code={}", err_code);

  p_scb->started = false;
  p_scb->cong = false;
  if (err_code) {
    if (AVDT_ERR_CONNECT == err_code) {
      /* report failure */
      tBTA_AV bta_av_data = {
          .reconfig =
              {
                  .chnl = p_scb->chnl,
                  .hndl = p_scb->hndl,
                  .status = BTA_AV_FAIL,
              },
      };
      (*bta_av_cb.p_cback)(BTA_AV_RECONFIG_EVT, &bta_av_data);
      log::error("BTA_AV_STR_DISC_FAIL_EVT: peer_addr={}",
                 ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
      bta_av_ssm_execute(p_scb, BTA_AV_STR_DISC_FAIL_EVT, NULL);
    } else {
      log::error("suspend rejected, try close");
      /* drop the buffers queued in L2CAP */
      L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_ALL);

      AVDT_CloseReq(p_scb->avdt_handle);
    }
  } else {
    log::verbose("calling AVDT_ReconfigReq");
    /* reconfig the stream */

    log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));
    AVDT_ReconfigReq(p_scb->avdt_handle, &p_scb->cfg);
    p_scb->cfg.psc_mask = p_scb->cur_psc_mask;
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_cfm
 *
 * Description      if reconfigure is successful, report the event
 *                  otherwise, close the stream.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_cfm(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  uint8_t err_code = p_data->str_msg.msg.hdr.err_code;

  log::verbose("err_code = {}", err_code);

  // Disable AVDTP RECONFIGURE for rejectlisted devices
  bool disable_avdtp_reconfigure = false;
  {
    char remote_name[BTM_MAX_REM_BD_NAME_LEN] = "";
    if (btif_storage_get_stored_remote_name(p_scb->PeerAddress(),
                                            remote_name)) {
      if (interop_match_name(INTEROP_DISABLE_AVDTP_RECONFIGURE, remote_name) ||
          interop_match_addr(INTEROP_DISABLE_AVDTP_RECONFIGURE,
                             (const RawAddress*)&p_scb->PeerAddress())) {
        log::info(
            "disable AVDTP RECONFIGURE: interop matched name {} address {}",
            remote_name, ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()));
        disable_avdtp_reconfigure = true;
      }
    }
  }

  if ((err_code != 0) || disable_avdtp_reconfigure) {
    log::error("reconfig rejected, try close");
    /* Disable reconfiguration feature only with explicit rejection(not with
     * timeout) */
    if ((err_code != AVDT_ERR_TIMEOUT) || disable_avdtp_reconfigure) {
      p_scb->recfg_sup = false;
    }
    /* started flag is false when reconfigure command is sent */
    /* drop the buffers queued in L2CAP */
    L2CA_FlushChannel(p_scb->l2c_cid, L2CAP_FLUSH_CHANS_ALL);
    AVDT_CloseReq(p_scb->avdt_handle);
  } else {
    /* update the codec info after rcfg cfm */
    log::verbose(
        "updating from codec {} to codec {}",
        A2DP_CodecName(p_scb->cfg.codec_info),
        A2DP_CodecName(p_data->str_msg.msg.reconfig_cfm.p_cfg->codec_info));
    memcpy(p_scb->cfg.codec_info,
           p_data->str_msg.msg.reconfig_cfm.p_cfg->codec_info, AVDT_CODEC_SIZE);
    /* take the SSM back to OPEN state */
    bta_av_ssm_execute(p_scb, BTA_AV_STR_OPEN_OK_EVT, NULL);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_rcfg_open
 *
 * Description      AVDT is connected. open the stream with the new
 *                  configuration
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_rcfg_open(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::verbose("peer {} bta_handle:0x{:x} num_disc_snks:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
               p_scb->num_disc_snks);

  if (p_scb->num_disc_snks == 0) {
    /* Need to update call-out module so that it will be ready for discover */
    p_scb->p_cos->stop(p_scb->hndl, p_scb->PeerAddress());

    /* send avdtp discover request */
    AVDT_DiscoverReq(p_scb->PeerAddress(), p_scb->hdi, p_scb->sep_info,
                     BTA_AV_NUM_SEPS, &bta_av_proc_stream_evt);
  } else {
    log::verbose("calling AVDT_OpenReq()");
    log::verbose("codec: {}", A2DP_CodecInfoString(p_scb->cfg.codec_info));

    /* we may choose to use a different SEP at reconfig.
     * adjust the sep_idx now */
    bta_av_adjust_seps_idx(p_scb, bta_av_get_scb_handle(p_scb, AVDT_TSEP_SRC));
    log::info("sep_idx={} avdt_handle={} bta_handle=0x{:x}", p_scb->sep_idx,
              p_scb->avdt_handle, p_scb->hndl);

    /* open the stream with the new config */
    p_scb->sep_info_idx = p_scb->rcfg_idx;
    AVDT_OpenReq(p_scb->avdt_handle, p_scb->PeerAddress(), p_scb->hdi,
                 p_scb->sep_info[p_scb->sep_info_idx].seid, &p_scb->cfg);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_security_rej
 *
 * Description      Send an AVDTP security reject.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_security_rej(tBTA_AV_SCB* p_scb, UNUSED_ATTR tBTA_AV_DATA* p_data) {
  AVDT_SecurityRsp(p_scb->avdt_handle, p_scb->avdt_label, AVDT_ERR_BAD_STATE,
                   NULL, 0);
}

/*******************************************************************************
 *
 * Function         bta_av_chk_2nd_start
 *
 * Description      check if this is 2nd stream and if it needs to be started.
 *                  This function needs to be kept very similar to
 *                  bta_av_chk_start
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_chk_2nd_start(tBTA_AV_SCB* p_scb,
                          UNUSED_ATTR tBTA_AV_DATA* p_data) {
  log::info(
      "peer {} channel:{} bta_av_cb.audio_open_cnt:{} role:0x{:x} "
      "features:0x{:x}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->chnl,
      bta_av_cb.audio_open_cnt, p_scb->role, bta_av_cb.features);

  if ((p_scb->chnl == BTA_AV_CHNL_AUDIO) && (bta_av_cb.audio_open_cnt >= 2) &&
      (((p_scb->role & BTA_AV_ROLE_AD_ACP) == 0) ||  // Outgoing connection or
       (bta_av_cb.features & BTA_AV_FEAT_ACP_START))) {  // Auto-starting option
    // More than one audio channel is connected.
    if (!(p_scb->role & BTA_AV_ROLE_SUSPEND_OPT)) {
      // This channel does not need to be reconfigured.
      // If there is other channel streaming, start the stream now.
      bool new_started = false;
      for (int i = 0; i < BTA_AV_NUM_STRS; i++) {
        tBTA_AV_SCB* p_scbi = bta_av_cb.p_scb[i];
        if (p_scb == p_scbi) {
          continue;
        }
        if (p_scbi && p_scbi->chnl == BTA_AV_CHNL_AUDIO && p_scbi->co_started) {
          if (!new_started) {
            // Start the new stream
            new_started = true;
            log::info(
                "starting new stream for peer {} because peer {} already "
                "started",
                ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
                ADDRESS_TO_LOGGABLE_CSTR(p_scbi->PeerAddress()));
            bta_av_ssm_execute(p_scb, BTA_AV_AP_START_EVT, NULL);
          }
          // May need to update the flush timeout of this already started stream
          if (p_scbi->co_started != bta_av_cb.audio_open_cnt) {
            p_scbi->co_started = bta_av_cb.audio_open_cnt;
          }
        }
      }
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_open_rc
 *
 * Description      Send a message to main SM to open RC channel.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_open_rc(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  log::verbose("use_rc: {}, wait: 0x{:x} role: 0x{:x}", p_scb->use_rc,
               p_scb->wait, p_scb->role);
  if ((p_scb->wait & BTA_AV_WAIT_ROLE_SW_BITS) &&
      (p_scb->q_tag == BTA_AV_Q_TAG_START)) {
    /* waiting for role switch for some reason & the timer expires */
    if (!bta_av_link_role_ok(p_scb, A2DP_SET_ONE_BIT)) {
      log::error("failed to start streaming for role management reasons!!");
      alarm_cancel(p_scb->avrc_ct_timer);

      tBTA_AV bta_av_data = {
          .start =
              {
                  .chnl = p_scb->chnl,
                  .hndl = p_scb->hndl,
                  .status = BTA_AV_FAIL_ROLE,
                  .initiator = true,
                  .suspending = false,
              },
      };
      p_scb->wait &= ~BTA_AV_WAIT_ROLE_SW_BITS;
      bta_av_cb.rs_idx = 0;
      (*bta_av_cb.p_cback)(BTA_AV_START_EVT, &bta_av_data);
    } else {
      /* role switch is done. continue to start streaming */
      bta_av_cb.rs_idx = 0;
      p_data->hdr.offset = BTA_AV_RS_OK;
      bta_av_start_ok(p_scb, p_data);
    }
    return;
  }

  if (p_scb->use_rc || (p_scb->role & BTA_AV_ROLE_AD_ACP)) {
    if (bta_av_cb.disc) {
      /* AVRC discover db is in use */
      if (p_scb->rc_handle == BTA_AV_RC_HANDLE_NONE) {
        /* AVRC channel is not connected. delay a little bit */
        if ((p_scb->wait & BTA_AV_WAIT_ROLE_SW_BITS) == 0) {
          bta_sys_start_timer(p_scb->avrc_ct_timer, BTA_AV_RC_DISC_TIME_VAL,
                              BTA_AV_AVRC_TIMER_EVT, p_scb->hndl);
        } else {
          p_scb->wait |= BTA_AV_WAIT_CHECK_RC;
        }
      }
    } else {
      /* use main SM for AVRC SDP activities */
      if (btif_av_both_enable()) {
        /* if peer is sink, it should run new avrcp */
        if ((p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC) &&
            is_new_avrcp_enabled()) {
          log::warn("local src Using the new AVRCP Profile");
          if (bluetooth::avrcp::AvrcpService::Get() != nullptr) {
            bluetooth::avrcp::AvrcpService::Get()->ConnectDevice(
                p_scb->PeerAddress());
            return;
          }
        }

        log::warn("local sink Using the legacy AVRCP Profile");
        bta_av_rc_disc((uint8_t)(p_scb->hdi + 1));

        return;
      }
      if (btif_av_is_source_enabled() && is_new_avrcp_enabled()) {
        log::warn("Using the new AVRCP Profile");
        bluetooth::avrcp::AvrcpService::Get()->ConnectDevice(
            p_scb->PeerAddress());
      } else {
        bta_av_rc_disc((uint8_t)(p_scb->hdi + 1));
      }
    }
  } else {
    if (BTA_AV_RC_HANDLE_NONE != p_scb->rc_handle) {
      /* the open API said that this handle does not want a RC connection.
       * disconnect it now */
      AVRC_Close(p_scb->rc_handle);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_av_open_at_inc
 *
 * Description      This function is called if API open is called by application
 *                  while state-machine is at incoming state.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_open_at_inc(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  memcpy(&(p_scb->open_api), &(p_data->api_open), sizeof(tBTA_AV_API_OPEN));

  log::verbose("peer {} coll_mask=0x{:02x}",
               ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()),
               p_scb->coll_mask);

  if (p_scb->coll_mask & BTA_AV_COLL_INC_TMR) {
    p_scb->coll_mask |= BTA_AV_COLL_API_CALLED;

    /* API open will be handled at timeout if SNK did not start signalling. */
    /* API open will be ignored if SNK starts signalling.                   */
  } else {
    /* SNK did not start signalling, API was called N seconds timeout. */
    /* We need to switch to INIT state and start opening connection. */
    p_scb->coll_mask = 0;
    bta_av_set_scb_sst_init(p_scb);

    tBTA_AV_API_OPEN* p_buf =
        (tBTA_AV_API_OPEN*)osi_malloc(sizeof(tBTA_AV_API_OPEN));
    memcpy(p_buf, &(p_scb->open_api), sizeof(tBTA_AV_API_OPEN));
    bta_sys_sendmsg(p_buf);
  }
}

void offload_vendor_callback(tBTM_VSC_CMPL* param) {
  tBTA_AV value{0};
  uint8_t sub_opcode = 0;
  if (param->param_len) {
    log::verbose("param_len = {} status = {}", param->param_len,
                 param->p_param_buf[0]);
    value.status = static_cast<tBTA_AV_STATUS>(param->p_param_buf[0]);
  }
  if (value.status == 0) {
    sub_opcode = param->p_param_buf[1];
    log::verbose("subopcode = {}", sub_opcode);
    switch (sub_opcode) {
      case VS_HCI_A2DP_OFFLOAD_STOP:
      case VS_HCI_A2DP_OFFLOAD_STOP_V2:
        log::verbose("VS_HCI_STOP_A2DP_MEDIA successful");
        break;
      case VS_HCI_A2DP_OFFLOAD_START:
      case VS_HCI_A2DP_OFFLOAD_START_V2:
        if (bta_av_cb.offload_start_pending_hndl) {
          log::verbose("VS_HCI_START_A2DP_MEDIA successful");
          bta_av_cb.offload_started_hndl = bta_av_cb.offload_start_pending_hndl;
          bta_av_cb.offload_start_pending_hndl = BTA_AV_INVALID_HANDLE;
        } else {
          log::info(
              "No pending start command due to AVDTP suspend immediately");
        }
        (*bta_av_cb.p_cback)(BTA_AV_OFFLOAD_START_RSP_EVT, &value);
        break;
      default:
        break;
    }
  } else {
    log::verbose("Offload failed for subopcode= {}", sub_opcode);
    if (param->opcode != VS_HCI_A2DP_OFFLOAD_STOP &&
        param->opcode != VS_HCI_A2DP_OFFLOAD_STOP_V2) {
      bta_av_cb.offload_start_pending_hndl = BTA_AV_INVALID_HANDLE;
      (*bta_av_cb.p_cback)(BTA_AV_OFFLOAD_START_RSP_EVT, &value);
    }
  }
}

void bta_av_vendor_offload_start(tBTA_AV_SCB* p_scb,
                                 tBT_A2DP_OFFLOAD* offload_start) {
  uint8_t param[sizeof(tBT_A2DP_OFFLOAD)];
  log::verbose("");

  uint8_t* p_param = param;
  *p_param++ = VS_HCI_A2DP_OFFLOAD_START;

  UINT32_TO_STREAM(p_param, offload_start->codec_type);
  UINT16_TO_STREAM(p_param, offload_start->max_latency);
  ARRAY_TO_STREAM(p_param, offload_start->scms_t_enable,
                  static_cast<int>(offload_start->scms_t_enable.size()));
  UINT32_TO_STREAM(p_param, offload_start->sample_rate);
  UINT8_TO_STREAM(p_param, offload_start->bits_per_sample);
  UINT8_TO_STREAM(p_param, offload_start->ch_mode);
  UINT32_TO_STREAM(p_param, offload_start->encoded_audio_bitrate);
  UINT16_TO_STREAM(p_param, offload_start->acl_hdl);
  UINT16_TO_STREAM(p_param, offload_start->l2c_rcid);
  UINT16_TO_STREAM(p_param, offload_start->mtu);
  ARRAY_TO_STREAM(p_param, offload_start->codec_info,
                  (int8_t)sizeof(offload_start->codec_info));
  bta_av_cb.offload_start_pending_hndl = p_scb->hndl;
  bta_av_cb.offload_start_v2 = false;
  log::info(
      "codec: {:#x}, sample rate: {:#x}, bit depth: {:#x}, channel: {:#x}, "
      "bitrate: {:#x}, ACL: {:#x}, L2CAP: {:#x}, MTU: {:#x}",
      offload_start->codec_type, offload_start->sample_rate,
      offload_start->bits_per_sample, offload_start->ch_mode,
      offload_start->encoded_audio_bitrate, offload_start->acl_hdl,
      offload_start->l2c_rcid, offload_start->mtu);
  BTM_VendorSpecificCommand(HCI_CONTROLLER_A2DP, p_param - param, param,
                            offload_vendor_callback);
}

void bta_av_vendor_offload_start_v2(tBTA_AV_SCB* p_scb,
                                    A2dpCodecConfigExt* offload_codec) {
  log::verbose("");

  uint16_t connection_handle =
      get_btm_client_interface().lifecycle.BTM_GetHCIConnHandle(
          p_scb->PeerAddress(), BT_TRANSPORT_BR_EDR);
  btav_a2dp_scmst_info_t scmst_info =
      p_scb->p_cos->get_scmst_info(p_scb->PeerAddress());
  uint16_t mtu = p_scb->stream_mtu;
  uint16_t l2cap_channel_handle = 0;

  if (mtu > MAX_3MBPS_AVDTP_MTU) {
    mtu = MAX_3MBPS_AVDTP_MTU;
  }
  if (L2CA_GetRemoteCid(p_scb->l2c_cid, &l2cap_channel_handle) == false) {
    log::error("Failed to fetch l2c rcid");
  }

  uint8_t param[255];
  uint8_t* p_param = param;
  *p_param++ = VS_HCI_A2DP_OFFLOAD_START_V2;

  // Connection_Handle: 2 bytes
  UINT16_TO_STREAM(p_param, connection_handle);
  // L2CAP_Channel_ID: 2 bytes
  UINT16_TO_STREAM(p_param, l2cap_channel_handle);
  // Data_Path_Direction: 1 byte
  // TODO(b/305779580): Sink offload
  UINT8_TO_STREAM(p_param, 0x0);
  // Peer_MTU: 2 bytes
  UINT16_TO_STREAM(p_param, mtu);
  // CP_Enable_SCMS_T: 1 byte
  UINT8_TO_STREAM(p_param, scmst_info.enable_status);
  // CP_Header_SCMS_T: 1 byte
  UINT8_TO_STREAM(p_param, scmst_info.cp_header);
  // Vendor_Specific_Parameters_Len: 1 byte
  // Vendor_Specific_Parameters: N bytes
  auto const& vendor_specific_parameters =
      offload_codec->getVendorCodecParameters();
  UINT8_TO_STREAM(p_param,
                  static_cast<uint8_t>(vendor_specific_parameters.size()));
  ARRAY_TO_STREAM(p_param, vendor_specific_parameters.data(),
                  static_cast<uint8_t>(vendor_specific_parameters.size()));

  // Update the pending state.
  bta_av_cb.offload_start_pending_hndl = p_scb->hndl;
  bta_av_cb.offload_start_v2 = true;

  BTM_VendorSpecificCommand(HCI_CONTROLLER_A2DP, p_param - param,
                            param, offload_vendor_callback);
}

void bta_av_vendor_offload_stop() {
  uint8_t param[255];
  uint8_t* p_param = param;

  log::verbose("");

  if (bta_av_cb.offload_start_v2) {
    tBTA_AV_SCB* p_scb =
        bta_av_hndl_to_scb(bta_av_cb.offload_start_pending_hndl);
    if (p_scb == nullptr) {
      return;
    }
    uint16_t connection_handle =
        get_btm_client_interface().lifecycle.BTM_GetHCIConnHandle(
            p_scb->PeerAddress(), BT_TRANSPORT_BR_EDR);
    uint16_t l2cap_channel_handle = 0;

    if (L2CA_GetRemoteCid(p_scb->l2c_cid, &l2cap_channel_handle) == false) {
      log::error("Failed to fetch l2c rcid");
    }

    *p_param++ = VS_HCI_A2DP_OFFLOAD_STOP_V2;
    // Connection_Handle: 2 bytes
    UINT16_TO_STREAM(p_param, connection_handle);
    // L2CAP_Channel_ID: 2 bytes
    UINT16_TO_STREAM(p_param, l2cap_channel_handle);
    // Data_Path_Direction: 1 byte
    // TODO(b/305779580): Sink offload
    UINT8_TO_STREAM(p_param, 0x0);
  } else {
    *p_param++ = VS_HCI_A2DP_OFFLOAD_STOP;
  }

  BTM_VendorSpecificCommand(HCI_CONTROLLER_A2DP, p_param - param, param,
                            offload_vendor_callback);
}

/*******************************************************************************
 *
 * Function         bta_av_offload_req
 *
 * Description      This function is called if application requests offload of
 *                  a2dp audio.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_offload_req(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV_STATUS status = BTA_AV_FAIL_RESOURCES;

  tBT_A2DP_OFFLOAD offload_start;
  log::verbose("stream {}, audio channels open {}",
               p_scb->started ? "STARTED" : "STOPPED",
               bta_av_cb.audio_open_cnt);

  A2dpCodecConfig* codec_config = bta_av_get_a2dp_current_codec();
  ASSERT(codec_config != nullptr);

  /* Check if stream has already been started. */
  /* Support offload if only one audio source stream is open. */
  if (p_scb->started != true) {
    status = BTA_AV_FAIL_STREAM;
  } else if (bta_av_cb.offload_start_pending_hndl ||
             bta_av_cb.offload_started_hndl) {
    log::warn("offload already started, ignore request");
    return;
  } else if (::bluetooth::audio::a2dp::provider::supports_codec(
                 codec_config->codecIndex())) {
    bta_av_vendor_offload_start_v2(
        p_scb, static_cast<A2dpCodecConfigExt*>(codec_config));
  } else {
    bta_av_offload_codec_builder(p_scb, &offload_start);
    bta_av_vendor_offload_start(p_scb, &offload_start);
    return;
  }
  if (status != BTA_AV_SUCCESS) {
    tBTA_AV bta_av_data;
    bta_av_data.status = status;
    (*bta_av_cb.p_cback)(BTA_AV_OFFLOAD_START_RSP_EVT, &bta_av_data);
  }
  /* TODO(eisenbach): RE-IMPLEMENT USING VSC OR HAL EXTENSION
  else if (bta_av_cb.audio_open_cnt == 1 &&
             p_scb->seps[p_scb->sep_idx].tsep == AVDT_TSEP_SRC &&
             p_scb->chnl == BTA_AV_CHNL_AUDIO) {
    bt_vendor_op_a2dp_offload_t a2dp_offload_start;

    if (L2CA_GetConnectionConfig(
            p_scb->l2c_cid, &a2dp_offload_start.acl_data_size,
            &a2dp_offload_start.remote_cid, &a2dp_offload_start.lm_handle)) {
      log::verbose("l2cmtu {} lcid 0x{:02X} rcid 0x{:02X} lm_handle 0x{:02X}",
  a2dp_offload_start.acl_data_size, p_scb->l2c_cid,
  a2dp_offload_start.remote_cid, a2dp_offload_start.lm_handle);

      a2dp_offload_start.bta_av_handle = p_scb->hndl;
      a2dp_offload_start.xmit_quota = BTA_AV_A2DP_OFFLOAD_XMIT_QUOTA;
      a2dp_offload_start.stream_mtu = p_scb->stream_mtu;
      a2dp_offload_start.local_cid = p_scb->l2c_cid;
      a2dp_offload_start.is_flushable = true;
      a2dp_offload_start.stream_source =
          ((uint32_t)(p_scb->cfg.codec_info[1] | p_scb->cfg.codec_info[2]));

      memcpy(a2dp_offload_start.codec_info, p_scb->cfg.codec_info,
             sizeof(a2dp_offload_start.codec_info));

      if (!vendor_get_interface()->send_command(
              (vendor_opcode_t)BT_VND_OP_A2DP_OFFLOAD_START,
              &a2dp_offload_start)) {
        status = BTA_AV_SUCCESS;
        p_scb->offload_start_pending = true;
      }
    }
  }
 */
}

/*******************************************************************************
 *
 * Function         bta_av_offload_rsp
 *
 * Description      This function is called when the vendor lib responds to
 *                  BT_VND_OP_A2DP_OFFLOAD_START.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_offload_rsp(tBTA_AV_SCB* p_scb, tBTA_AV_DATA* p_data) {
  tBTA_AV_STATUS status = p_data->api_status_rsp.status;

  log::verbose("stream {} status {}", p_scb->started ? "STARTED" : "STOPPED",
               status ? "FAIL" : "SUCCESS");

  /* Check if stream has already been started. */
  if (status == BTA_AV_SUCCESS && p_scb->started != true) {
    status = BTA_AV_FAIL_STREAM;
  }

  bta_av_cb.offload_start_pending_hndl = BTA_AV_INVALID_HANDLE;
  tBTA_AV bta_av_data;
  bta_av_data.status = status;
  (*bta_av_cb.p_cback)(BTA_AV_OFFLOAD_START_RSP_EVT, &bta_av_data);
}

static void bta_av_offload_codec_builder(tBTA_AV_SCB* p_scb,
                                         tBT_A2DP_OFFLOAD* p_a2dp_offload) {
  A2dpCodecConfig* CodecConfig = bta_av_get_a2dp_current_codec();
  btav_a2dp_codec_index_t codec_index =
      A2DP_SourceCodecIndex(p_scb->cfg.codec_info);
  uint32_t codec_type = 0;
  uint16_t mtu = p_scb->stream_mtu;
  log::verbose("codec_index = {}", codec_index);
  switch (codec_index) {
    case BTAV_A2DP_CODEC_INDEX_SOURCE_SBC:
      codec_type = BTA_AV_CODEC_TYPE_SBC;
      if (A2DP_GetMaxBitpoolSbc(p_scb->cfg.codec_info) <=
          A2DP_SBC_BITPOOL_MIDDLE_QUALITY) {
        log::warn("Restricting streaming MTU size for MQ Bitpool");
        mtu = MAX_2MBPS_AVDTP_MTU;
      }
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_AAC:
      codec_type = BTA_AV_CODEC_TYPE_AAC;
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX:
      codec_type = BTA_AV_CODEC_TYPE_APTX;
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD:
      codec_type = BTA_AV_CODEC_TYPE_APTXHD;
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC:
      codec_type = BTA_AV_CODEC_TYPE_LDAC;
      break;
    case BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS:
      codec_type = BTA_AV_CODEC_TYPE_OPUS;
      break;
    default:
      log::error("Unknown Codec type");
      return;
  }
  if (mtu > MAX_3MBPS_AVDTP_MTU) mtu = MAX_3MBPS_AVDTP_MTU;
  p_a2dp_offload->codec_type = codec_type;
  p_a2dp_offload->max_latency = 0;
  p_a2dp_offload->mtu = mtu;
  p_a2dp_offload->acl_hdl =
      get_btm_client_interface().lifecycle.BTM_GetHCIConnHandle(
          p_scb->PeerAddress(), BT_TRANSPORT_BR_EDR);
  btav_a2dp_scmst_info_t scmst_info =
      p_scb->p_cos->get_scmst_info(p_scb->PeerAddress());
  p_a2dp_offload->scms_t_enable[0] = scmst_info.enable_status;
  p_a2dp_offload->scms_t_enable[1] = scmst_info.cp_header;
  log::verbose(
      "SCMS-T_enable status: {}, SCMS-T header (if it's enabled): 0x{:02x}",
      scmst_info.enable_status, scmst_info.cp_header);

  switch (A2DP_GetTrackSampleRate(p_scb->cfg.codec_info)) {
    case 44100:
      p_a2dp_offload->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_44100;
      break;
    case 48000:
      p_a2dp_offload->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_48000;
      break;
    case 88200:
      p_a2dp_offload->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_88200;
      break;
    case 96000:
      p_a2dp_offload->sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_96000;
      break;
  }
  if (L2CA_GetRemoteCid(p_scb->l2c_cid, &p_a2dp_offload->l2c_rcid) == false) {
    log::error("Failed to fetch l2c rcid");
    return;
  }
  switch (CodecConfig->getAudioBitsPerSample()) {
    case 16:
      p_a2dp_offload->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16;
      break;
    case 24:
      p_a2dp_offload->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24;
      break;
    case 32:
      p_a2dp_offload->bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32;
      break;
  }
  p_a2dp_offload->ch_mode = A2DP_GetTrackChannelCount(p_scb->cfg.codec_info);
  p_a2dp_offload->encoded_audio_bitrate = CodecConfig->getTrackBitRate();
  if (!CodecConfig->getCodecSpecificConfig(p_a2dp_offload)) {
    log::error("not a valid codec info");
  }
}
void bta_av_api_set_peer_sep(tBTA_AV_DATA* p_data) {
  log::verbose("bd_addr={}, sep:{}",
               ADDRESS_TO_LOGGABLE_CSTR(p_data->peer_sep.addr),
               p_data->peer_sep.sep);
  const tBTA_AV_SCB* p_scb = bta_av_addr_to_scb(p_data->peer_sep.addr);
  if (!p_scb) {
    log::warn("scb not found");
    return;
  }
  log::verbose("rc_handle:{}", p_scb->rc_handle);
  if (btif_av_both_enable()) {
    if (p_data->peer_sep.sep == AVDT_TSEP_SNK) {
      // src close legacy cback
      log::warn("current dut is src");
      AVRC_UpdateCcb(&p_data->peer_sep.addr, AVRC_CO_METADATA);
    } else if (p_data->peer_sep.sep == AVDT_TSEP_SRC) {
      // sink close new cback
      log::warn("current dut is sink");
      AVRC_UpdateCcb(&p_data->peer_sep.addr, AVRC_CO_GOOGLE);
    }
  }
}
