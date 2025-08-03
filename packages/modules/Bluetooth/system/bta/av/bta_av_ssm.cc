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
 *  This is the stream state machine for the BTA advanced audio/video.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_av"

#include <bluetooth/log.h>

#include "bta/av/bta_av_int.h"
#include "internal_include/bt_target.h"
#include "os/log.h"

using namespace bluetooth;

/*****************************************************************************
 * Constants and types
 ****************************************************************************/

/* state machine states */
enum {
  BTA_AV_INIT_SST,
  BTA_AV_INCOMING_SST,
  BTA_AV_OPENING_SST,
  BTA_AV_OPEN_SST,
  BTA_AV_RCFG_SST,
  BTA_AV_CLOSING_SST
};

static void bta_av_better_stream_state_machine(tBTA_AV_SCB* p_scb,
                                               uint16_t event,
                                               tBTA_AV_DATA* p_data) {
  uint8_t previous_state = p_scb->state;
  tBTA_AV_ACT event_handler1 = nullptr;
  tBTA_AV_ACT event_handler2 = nullptr;
  switch (p_scb->state) {
    case BTA_AV_INIT_SST:
      switch (event) {
        case BTA_AV_API_OPEN_EVT:
          p_scb->state = BTA_AV_OPENING_SST;
          event_handler1 = &bta_av_do_disc_a2dp;
          break;
        case BTA_AV_API_CLOSE_EVT:
          event_handler1 = &bta_av_cleanup;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          p_scb->state = BTA_AV_INCOMING_SST;
          event_handler1 = &bta_av_config_ind;
          break;
        case BTA_AV_ACP_CONNECT_EVT:
          p_scb->state = BTA_AV_INCOMING_SST;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
    case BTA_AV_INCOMING_SST:
      switch (event) {
        case BTA_AV_API_OPEN_EVT:
          event_handler1 = &bta_av_open_at_inc;
          break;
        case BTA_AV_API_CLOSE_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_cco_close;
          event_handler2 = &bta_av_disconnect_req;
          break;
        case BTA_AV_API_PROTECT_REQ_EVT:
          event_handler1 = &bta_av_security_req;
          break;
        case BTA_AV_API_PROTECT_RSP_EVT:
          event_handler1 = &bta_av_security_rsp;
          break;
        case BTA_AV_CI_SETCONFIG_OK_EVT:
          event_handler1 = &bta_av_setconfig_rsp;
          event_handler2 = &bta_av_st_rc_timer;
          break;
        case BTA_AV_CI_SETCONFIG_FAIL_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_setconfig_rej;
          event_handler2 = &bta_av_cleanup;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_STR_DISC_OK_EVT:
          event_handler1 = &bta_av_disc_res_as_acp;
          break;
        case BTA_AV_STR_GETCAP_OK_EVT:
          event_handler1 = &bta_av_save_caps;
          break;
        case BTA_AV_STR_OPEN_OK_EVT:
          p_scb->state = BTA_AV_OPEN_SST;
          event_handler1 = &bta_av_str_opened;
          break;
        case BTA_AV_STR_CLOSE_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_cco_close;
          event_handler2 = &bta_av_cleanup;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          event_handler1 = &bta_av_config_ind;
          break;
        case BTA_AV_STR_SECURITY_IND_EVT:
          event_handler1 = &bta_av_security_ind;
          break;
        case BTA_AV_STR_SECURITY_CFM_EVT:
          event_handler1 = &bta_av_security_cfm;
          break;
        case BTA_AV_AVDT_DISCONNECT_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_cco_close;
          event_handler2 = &bta_av_disconnect_req;
          break;
        case BTA_AV_AVDT_DELAY_RPT_EVT:
          event_handler1 = &bta_av_delay_co;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
    case BTA_AV_OPENING_SST:
      switch (event) {
        case BTA_AV_API_CLOSE_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_do_close;
          break;
        case BTA_AV_API_PROTECT_REQ_EVT:
          event_handler1 = &bta_av_security_req;
          break;
        case BTA_AV_API_PROTECT_RSP_EVT:
          event_handler1 = &bta_av_security_rsp;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          event_handler1 = &bta_av_connect_req;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          event_handler1 = &bta_av_connect_req;
          break;
        case BTA_AV_STR_DISC_OK_EVT:
          event_handler1 = &bta_av_disc_results;
          break;
        case BTA_AV_STR_DISC_FAIL_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_open_failed;
          break;
        case BTA_AV_STR_GETCAP_OK_EVT:
          event_handler1 = &bta_av_getcap_results;
          break;
        case BTA_AV_STR_GETCAP_FAIL_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_open_failed;
          break;
        case BTA_AV_STR_OPEN_OK_EVT:
          p_scb->state = BTA_AV_OPEN_SST;
          event_handler1 = &bta_av_st_rc_timer;
          event_handler2 = &bta_av_str_opened;
          break;
        case BTA_AV_STR_OPEN_FAIL_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_open_failed;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          p_scb->state = BTA_AV_INCOMING_SST;
          event_handler1 = &bta_av_config_ind;
          break;
        case BTA_AV_STR_SECURITY_IND_EVT:
          event_handler1 = &bta_av_security_ind;
          break;
        case BTA_AV_STR_SECURITY_CFM_EVT:
          event_handler1 = &bta_av_security_cfm;
          break;
        case BTA_AV_AVRC_TIMER_EVT:
          event_handler1 = &bta_av_switch_role;
          break;
        case BTA_AV_AVDT_CONNECT_EVT:
          event_handler1 = &bta_av_discover_req;
          break;
        case BTA_AV_AVDT_DISCONNECT_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_conn_failed;
          break;
        case BTA_AV_ROLE_CHANGE_EVT:
          event_handler1 = &bta_av_role_res;
          break;
        case BTA_AV_AVDT_DELAY_RPT_EVT:
          event_handler1 = &bta_av_delay_co;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
    case BTA_AV_OPEN_SST:
      switch (event) {
        case BTA_AV_API_CLOSE_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_do_close;
          break;
        case BTA_AV_AP_START_EVT:
          event_handler1 = &bta_av_do_start;
          break;
        case BTA_AV_AP_STOP_EVT:
          event_handler1 = &bta_av_str_stopped;
          break;
        case BTA_AV_API_RECONFIG_EVT:
          p_scb->state = BTA_AV_RCFG_SST;
          event_handler1 = &bta_av_reconfig;
          break;
        case BTA_AV_API_PROTECT_REQ_EVT:
          event_handler1 = &bta_av_security_req;
          break;
        case BTA_AV_API_PROTECT_RSP_EVT:
          event_handler1 = &bta_av_security_rsp;
          break;
        case BTA_AV_API_RC_OPEN_EVT:
          event_handler1 = &bta_av_set_use_rc;
          break;
        case BTA_AV_SRC_DATA_READY_EVT:
          event_handler1 = &bta_av_data_path;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_STR_GETCAP_OK_EVT:
          event_handler1 = &bta_av_save_caps;
          break;
        case BTA_AV_STR_START_OK_EVT:
          event_handler1 = &bta_av_start_ok;
          break;
        case BTA_AV_STR_START_FAIL_EVT:
          event_handler1 = &bta_av_start_failed;
          break;
        case BTA_AV_STR_CLOSE_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_str_closed;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          event_handler1 = &bta_av_setconfig_rej;
          break;
        case BTA_AV_STR_SECURITY_IND_EVT:
          event_handler1 = &bta_av_security_ind;
          break;
        case BTA_AV_STR_SECURITY_CFM_EVT:
          event_handler1 = &bta_av_security_cfm;
          break;
        case BTA_AV_STR_WRITE_CFM_EVT:
          event_handler1 = &bta_av_clr_cong;
          event_handler2 = &bta_av_data_path;
          break;
        case BTA_AV_STR_SUSPEND_CFM_EVT:
          event_handler1 = &bta_av_suspend_cfm;
          break;
        case BTA_AV_AVRC_TIMER_EVT:
          event_handler1 = &bta_av_open_rc;
          break;
        case BTA_AV_AVDT_DISCONNECT_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_str_closed;
          break;
        case BTA_AV_ROLE_CHANGE_EVT:
          event_handler1 = &bta_av_role_res;
          break;
        case BTA_AV_AVDT_DELAY_RPT_EVT:
          event_handler1 = &bta_av_delay_co;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
    case BTA_AV_RCFG_SST:
      switch (event) {
        case BTA_AV_API_CLOSE_EVT:
          p_scb->state = BTA_AV_CLOSING_SST;
          event_handler1 = &bta_av_disconnect_req;
          break;
        case BTA_AV_API_RECONFIG_EVT:
          event_handler1 = &bta_av_reconfig;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          event_handler1 = &bta_av_free_sdb;
          break;
        case BTA_AV_STR_DISC_OK_EVT:
          event_handler1 = &bta_av_disc_results;
          break;
        case BTA_AV_STR_DISC_FAIL_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_str_closed;
          break;
        case BTA_AV_STR_GETCAP_OK_EVT:
          event_handler1 = &bta_av_getcap_results;
          break;
        case BTA_AV_STR_GETCAP_FAIL_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_str_closed;
          break;
        case BTA_AV_STR_OPEN_OK_EVT:
          p_scb->state = BTA_AV_OPEN_SST;
          event_handler1 = &bta_av_rcfg_str_ok;
          break;
        case BTA_AV_STR_OPEN_FAIL_EVT:
          event_handler1 = &bta_av_rcfg_failed;
          break;
        case BTA_AV_STR_CLOSE_EVT:
          event_handler1 = &bta_av_rcfg_connect;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          event_handler1 = &bta_av_setconfig_rej;
          break;
        case BTA_AV_STR_SUSPEND_CFM_EVT:
          event_handler1 = &bta_av_suspend_cfm;
          event_handler2 = &bta_av_suspend_cont;
          break;
        case BTA_AV_STR_RECONFIG_CFM_EVT:
          event_handler1 = &bta_av_rcfg_cfm;
          break;
        case BTA_AV_AVDT_CONNECT_EVT:
          event_handler1 = &bta_av_rcfg_open;
          break;
        case BTA_AV_AVDT_DISCONNECT_EVT:
          event_handler1 = &bta_av_rcfg_discntd;
          break;
        case BTA_AV_AVDT_DELAY_RPT_EVT:
          event_handler1 = &bta_av_delay_co;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
    case BTA_AV_CLOSING_SST:
      switch (event) {
        case BTA_AV_API_CLOSE_EVT:
          event_handler1 = &bta_av_disconnect_req;
          break;
        case BTA_AV_SDP_DISC_OK_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_sdp_failed;
          break;
        case BTA_AV_SDP_DISC_FAIL_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_sdp_failed;
          break;
        case BTA_AV_STR_OPEN_OK_EVT:
          event_handler1 = &bta_av_do_close;
          break;
        case BTA_AV_STR_OPEN_FAIL_EVT:
          event_handler1 = &bta_av_disconnect_req;
          break;
        case BTA_AV_STR_CLOSE_EVT:
          event_handler1 = &bta_av_disconnect_req;
          break;
        case BTA_AV_STR_CONFIG_IND_EVT:
          event_handler1 = &bta_av_setconfig_rej;
          break;
        case BTA_AV_STR_SECURITY_IND_EVT:
          event_handler1 = &bta_av_security_rej;
          break;
        case BTA_AV_AVDT_DISCONNECT_EVT:
          p_scb->state = BTA_AV_INIT_SST;
          event_handler1 = &bta_av_str_closed;
          break;
        case BTA_AV_API_OFFLOAD_START_EVT:
          event_handler1 = &bta_av_offload_req;
          break;
        case BTA_AV_API_OFFLOAD_START_RSP_EVT:
          event_handler1 = &bta_av_offload_rsp;
          break;
      }
      break;
  }

  if (previous_state != p_scb->state) {
    log::info(
        "peer {} p_scb={:#x}({}) AV event=0x{:x}({}) state={}({}) -> {}({})",
        ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
        fmt::ptr(p_scb), event, bta_av_evt_code(event), previous_state,
        bta_av_sst_code(previous_state), p_scb->state,
        bta_av_sst_code(p_scb->state));

  } else {
    log::verbose("peer {} p_scb={:#x}({}) AV event=0x{:x}({}) state={}({})",
                 ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl,
                 fmt::ptr(p_scb), event, bta_av_evt_code(event), p_scb->state,
                 bta_av_sst_code(p_scb->state));
  }

  if (event_handler1 != nullptr) {
    event_handler1(p_scb, p_data);
  }
  if (event_handler2 != nullptr) {
    event_handler2(p_scb, p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_av_ssm_execute
 *
 * Description      Stream state machine event handling function for AV
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_av_ssm_execute(tBTA_AV_SCB* p_scb, uint16_t event,
                        tBTA_AV_DATA* p_data) {
  if (p_scb == NULL) {
    /* this stream is not registered */
    log::verbose("AV channel not registered");
    return;
  }

  bta_av_better_stream_state_machine(p_scb, event, p_data);
}

/*******************************************************************************
 *
 * Function         bta_av_is_scb_opening
 *
 * Description      Returns true is scb is in opening state.
 *
 *
 * Returns          true if scb is in opening state.
 *
 ******************************************************************************/
bool bta_av_is_scb_opening(tBTA_AV_SCB* p_scb) {
  bool is_opening = false;

  if (p_scb) {
    if (p_scb->state == BTA_AV_OPENING_SST) is_opening = true;
  }

  return is_opening;
}

/*******************************************************************************
 *
 * Function         bta_av_is_scb_incoming
 *
 * Description      Returns true is scb is in incoming state.
 *
 *
 * Returns          true if scb is in incoming state.
 *
 ******************************************************************************/
bool bta_av_is_scb_incoming(tBTA_AV_SCB* p_scb) {
  bool is_incoming = false;

  if (p_scb) {
    if (p_scb->state == BTA_AV_INCOMING_SST) is_incoming = true;
  }

  return is_incoming;
}

/*******************************************************************************
 *
 * Function         bta_av_set_scb_sst_init
 *
 * Description      Set SST state to INIT.
 *                  Use this function to change SST outside of state machine.
 *
 * Returns          None
 *
 ******************************************************************************/
void bta_av_set_scb_sst_init(tBTA_AV_SCB* p_scb) {
  if (p_scb == nullptr) {
    return;
  }

  uint8_t next_state = BTA_AV_INIT_SST;

  log::verbose(
      "peer {} AV (hndl=0x{:x}) state={}({}) next state={}({}) p_scb={}",
      ADDRESS_TO_LOGGABLE_CSTR(p_scb->PeerAddress()), p_scb->hndl, p_scb->state,
      bta_av_sst_code(p_scb->state), next_state, bta_av_sst_code(next_state),
      fmt::ptr(p_scb));

  p_scb->state = next_state;
}

/*******************************************************************************
 *
 * Function         bta_av_is_scb_init
 *
 * Description      Returns true is scb is in init state.
 *
 *
 * Returns          true if scb is in incoming state.
 *
 ******************************************************************************/
bool bta_av_is_scb_init(tBTA_AV_SCB* p_scb) {
  bool is_init = false;

  if (p_scb) {
    if (p_scb->state == BTA_AV_INIT_SST) is_init = true;
  }

  return is_init;
}

/*******************************************************************************
 *
 * Function         bta_av_set_scb_sst_incoming
 *
 * Description      Set SST state to incoming.
 *                  Use this function to change SST outside of state machine.
 *
 * Returns          None
 *
 ******************************************************************************/
void bta_av_set_scb_sst_incoming(tBTA_AV_SCB* p_scb) {
  if (p_scb) {
    p_scb->state = BTA_AV_INCOMING_SST;
  }
}

/*****************************************************************************
 *  Debug Functions
 ****************************************************************************/
/*******************************************************************************
 *
 * Function         bta_av_sst_code
 *
 * Description
 *
 * Returns          char *
 *
 ******************************************************************************/
const char* bta_av_sst_code(uint8_t state) {
  switch (state) {
    case BTA_AV_INIT_SST:
      return "INIT";
    case BTA_AV_INCOMING_SST:
      return "INCOMING";
    case BTA_AV_OPENING_SST:
      return "OPENING";
    case BTA_AV_OPEN_SST:
      return "OPEN";
    case BTA_AV_RCFG_SST:
      return "RCFG";
    case BTA_AV_CLOSING_SST:
      return "CLOSING";
    default:
      return "unknown";
  }
}
