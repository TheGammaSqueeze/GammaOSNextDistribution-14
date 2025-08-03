/******************************************************************************
 *
 *  Copyright 2003-2012 Broadcom Corporation
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
 *  This is the main implementation file for the BTA audio gateway.
 *
 ******************************************************************************/

#include <android_bluetooth_flags.h>
#include <base/logging.h>
#include <bluetooth/log.h>

#include <string>
#include <vector>

#include "bta/ag/bta_ag_int.h"
#include "bta/include/bta_hfp_api.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/alarm.h"
#include "osi/include/compat.h"
#include "osi/include/osi.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btm_api.h"
#include "types/raw_address.h"

using namespace bluetooth;

/*****************************************************************************
 * Constants and types
 ****************************************************************************/
/* state machine states */
enum { BTA_AG_INIT_ST, BTA_AG_OPENING_ST, BTA_AG_OPEN_ST, BTA_AG_CLOSING_ST };

#define CASE_RETURN_STR(const) \
  case const:                  \
    return #const;

static const char* bta_ag_res_str(tBTA_AG_RES result) {
  switch (result) {
    CASE_RETURN_STR(BTA_AG_SPK_RES)
    CASE_RETURN_STR(BTA_AG_MIC_RES)
    CASE_RETURN_STR(BTA_AG_INBAND_RING_RES)
    CASE_RETURN_STR(BTA_AG_CIND_RES)
    CASE_RETURN_STR(BTA_AG_BINP_RES)
    CASE_RETURN_STR(BTA_AG_IND_RES)
    CASE_RETURN_STR(BTA_AG_BVRA_RES)
    CASE_RETURN_STR(BTA_AG_CNUM_RES)
    CASE_RETURN_STR(BTA_AG_BTRH_RES)
    CASE_RETURN_STR(BTA_AG_CLCC_RES)
    CASE_RETURN_STR(BTA_AG_COPS_RES)
    CASE_RETURN_STR(BTA_AG_IN_CALL_RES)
    CASE_RETURN_STR(BTA_AG_IN_CALL_CONN_RES)
    CASE_RETURN_STR(BTA_AG_CALL_WAIT_RES)
    CASE_RETURN_STR(BTA_AG_OUT_CALL_ORIG_RES)
    CASE_RETURN_STR(BTA_AG_OUT_CALL_ALERT_RES)
    CASE_RETURN_STR(BTA_AG_OUT_CALL_CONN_RES)
    CASE_RETURN_STR(BTA_AG_CALL_CANCEL_RES)
    CASE_RETURN_STR(BTA_AG_END_CALL_RES)
    CASE_RETURN_STR(BTA_AG_IN_CALL_HELD_RES)
    CASE_RETURN_STR(BTA_AG_UNAT_RES)
    CASE_RETURN_STR(BTA_AG_MULTI_CALL_RES)
    CASE_RETURN_STR(BTA_AG_BIND_RES)
    CASE_RETURN_STR(BTA_AG_IND_RES_ON_DEMAND)
    default:
      return "Unknown AG Result";
  }
}

static const char* bta_ag_evt_str(uint16_t event) {
  switch (event) {
    CASE_RETURN_STR(BTA_AG_API_REGISTER_EVT)
    CASE_RETURN_STR(BTA_AG_API_DEREGISTER_EVT)
    CASE_RETURN_STR(BTA_AG_API_OPEN_EVT)
    CASE_RETURN_STR(BTA_AG_API_CLOSE_EVT)
    CASE_RETURN_STR(BTA_AG_API_AUDIO_OPEN_EVT)
    CASE_RETURN_STR(BTA_AG_API_AUDIO_CLOSE_EVT)
    CASE_RETURN_STR(BTA_AG_API_RESULT_EVT)
    CASE_RETURN_STR(BTA_AG_API_SETCODEC_EVT)
    CASE_RETURN_STR(BTA_AG_RFC_OPEN_EVT)
    CASE_RETURN_STR(BTA_AG_RFC_CLOSE_EVT)
    CASE_RETURN_STR(BTA_AG_RFC_SRV_CLOSE_EVT)
    CASE_RETURN_STR(BTA_AG_RFC_DATA_EVT)
    CASE_RETURN_STR(BTA_AG_SCO_OPEN_EVT)
    CASE_RETURN_STR(BTA_AG_SCO_CLOSE_EVT)
    CASE_RETURN_STR(BTA_AG_DISC_ACP_RES_EVT)
    CASE_RETURN_STR(BTA_AG_DISC_INT_RES_EVT)
    CASE_RETURN_STR(BTA_AG_DISC_OK_EVT)
    CASE_RETURN_STR(BTA_AG_DISC_FAIL_EVT)
    CASE_RETURN_STR(BTA_AG_RING_TIMEOUT_EVT)
    CASE_RETURN_STR(BTA_AG_SVC_TIMEOUT_EVT)
    CASE_RETURN_STR(BTA_AG_COLLISION_EVT)
    default:
      return "Unknown AG Event";
  }
}

static const char* bta_ag_state_str(uint8_t state) {
  switch (state) {
    CASE_RETURN_STR(BTA_AG_INIT_ST)
    CASE_RETURN_STR(BTA_AG_OPENING_ST)
    CASE_RETURN_STR(BTA_AG_OPEN_ST)
    CASE_RETURN_STR(BTA_AG_CLOSING_ST)
    default:
      return "Unknown AG State";
  }
}

/*****************************************************************************
 * Global data
 ****************************************************************************/

/* AG control block */
tBTA_AG_CB bta_ag_cb;
const tBTA_AG_DATA tBTA_AG_DATA::kEmpty = {};

/*******************************************************************************
 *
 * Function         bta_ag_scb_alloc
 *
 * Description      Allocate an AG service control block.
 *
 *
 * Returns          pointer to the scb, or NULL if none could be allocated.
 *
 ******************************************************************************/
static tBTA_AG_SCB* bta_ag_scb_alloc(void) {
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  int i;

  for (i = 0; i < BTA_AG_MAX_NUM_CLIENTS; i++, p_scb++) {
    if (!p_scb->in_use) {
      /* initialize variables */
      p_scb->in_use = true;
      p_scb->sco_idx = BTM_INVALID_SCO_INDEX;
      p_scb->received_at_bac = false;
      p_scb->codec_updated = false;
      p_scb->codec_fallback = false;
      p_scb->retransmission_effort_retries = 0;
      p_scb->peer_codecs = BTM_SCO_CODEC_CVSD;
      p_scb->sco_codec = BTM_SCO_CODEC_CVSD;
      p_scb->peer_version = HFP_HSP_VERSION_UNKNOWN;
      p_scb->hsp_version = HSP_VERSION_1_2;
      p_scb->peer_sdp_features = 0;
      /* set up timers */
      p_scb->ring_timer = alarm_new("bta_ag.scb_ring_timer");
      p_scb->collision_timer = alarm_new("bta_ag.scb_collision_timer");
      p_scb->codec_negotiation_timer =
          alarm_new("bta_ag.scb_codec_negotiation_timer");
      /* set eSCO mSBC setting to T2 as the preferred */
      p_scb->codec_msbc_settings = BTA_AG_SCO_MSBC_SETTINGS_T2;
      p_scb->codec_lc3_settings = BTA_AG_SCO_LC3_SETTINGS_T2;
      /* set eSCO SWB setting to Q0 as the preferred */
      p_scb->codec_aptx_settings = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0;
      p_scb->is_aptx_swb_codec = false;
      log::verbose("bta_ag_scb_alloc {}", bta_ag_scb_to_idx(p_scb));
      break;
    }
  }

  if (i == BTA_AG_MAX_NUM_CLIENTS) {
    /* out of scbs */
    p_scb = nullptr;
    log::warn("Out of scbs");
  }
  return p_scb;
}

/*******************************************************************************
 *
 * Function         bta_ag_scb_dealloc
 *
 * Description      Deallocate a service control block.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_scb_dealloc(tBTA_AG_SCB* p_scb) {
  uint8_t idx;
  bool allocated = false;

  log::verbose("bta_ag_scb_dealloc {}", bta_ag_scb_to_idx(p_scb));

  /* stop and free timers */
  alarm_free(p_scb->ring_timer);
  alarm_free(p_scb->codec_negotiation_timer);
  alarm_free(p_scb->collision_timer);

  /* initialize control block */
  *p_scb = {};
  p_scb->sco_idx = BTM_INVALID_SCO_INDEX;

  /* If all scbs are deallocated, callback with disable event */
  if (!bta_sys_is_register(BTA_ID_AG)) {
    for (idx = 0; idx < BTA_AG_MAX_NUM_CLIENTS; idx++) {
      if (bta_ag_cb.scb[idx].in_use) {
        allocated = true;
        break;
      }
    }

    if (!allocated) {
      (*bta_ag_cb.p_cback)(BTA_AG_DISABLE_EVT, nullptr);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_scb_to_idx
 *
 * Description      Given a pointer to an scb, return its index.
 *
 *
 * Returns          Index of scb starting from 1
 *
 ******************************************************************************/
uint16_t bta_ag_scb_to_idx(tBTA_AG_SCB* p_scb) {
  /* use array arithmetic to determine index */
  return static_cast<uint16_t>(p_scb - bta_ag_cb.scb + 1);
}

/*******************************************************************************
 *
 * Function         bta_ag_scb_by_idx
 *
 * Description      Given an scb index return pointer to scb.
 *
 *
 * Returns          Pointer to scb or NULL if not allocated.
 *
 ******************************************************************************/
tBTA_AG_SCB* bta_ag_scb_by_idx(uint16_t idx) {
  tBTA_AG_SCB* p_scb;

  /* verify index */
  if (idx > 0 && idx <= BTA_AG_MAX_NUM_CLIENTS) {
    p_scb = &bta_ag_cb.scb[idx - 1];
    if (!p_scb->in_use) {
      p_scb = nullptr;
      log::warn("ag scb idx {} not allocated", idx);
    }
  } else {
    p_scb = nullptr;
    log::verbose("ag scb idx {} out of range", idx);
  }
  return p_scb;
}

/*******************************************************************************
 *
 * Function         bta_ag_service_to_idx
 *
 * Description      Given a BTA service mask convert to profile index.
 *
 *
 * Returns          Profile ndex of scb.
 *
 ******************************************************************************/
uint8_t bta_ag_service_to_idx(tBTA_SERVICE_MASK services) {
  if (services & BTA_HFP_SERVICE_MASK) {
    return BTA_AG_HFP;
  } else {
    return BTA_AG_HSP;
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_idx_by_bdaddr
 *
 * Description      Find SCB associated with peer BD address.
 *
 *
 * Returns          Index of SCB or zero if none found.
 *
 ******************************************************************************/
uint16_t bta_ag_idx_by_bdaddr(const RawAddress* peer_addr) {
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  if (peer_addr != nullptr) {
    for (uint16_t i = 0; i < BTA_AG_MAX_NUM_CLIENTS; i++, p_scb++) {
      if (p_scb->in_use && *peer_addr == p_scb->peer_addr) {
        return (i + 1);
      }
    }
  }

  /* no scb found */
  log::warn("No ag scb for peer addr");
  return 0;
}

/*******************************************************************************
 *
 * Function         bta_ag_other_scb_open
 *
 * Description      Check whether any other scb is in open state.
 *
 *
 * Returns          true if another scb is in open state, false otherwise.
 *
 ******************************************************************************/
bool bta_ag_other_scb_open(tBTA_AG_SCB* p_curr_scb) {
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  for (int i = 0; i < BTA_AG_MAX_NUM_CLIENTS; i++, p_scb++) {
    if (p_scb->in_use && p_scb != p_curr_scb &&
        p_scb->state == BTA_AG_OPEN_ST) {
      return true;
    }
  }
  /* no other scb found */
  log::debug("No other ag scb open");
  return false;
}

/*******************************************************************************
 *
 * Function         bta_ag_scb_open
 *
 * Description      Check whether given scb is in open state.
 *
 *
 * Returns          true if scb is in open state, false otherwise.
 *
 ******************************************************************************/
bool bta_ag_scb_open(tBTA_AG_SCB* p_curr_scb) {
  return p_curr_scb && p_curr_scb->in_use &&
         p_curr_scb->state == BTA_AG_OPEN_ST;
}

/*******************************************************************************
 *
 * Function         bta_ag_collision_cback
 *
 * Description      Get notified about collision.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_collision_cback(UNUSED_ATTR tBTA_SYS_CONN_STATUS status,
                            tBTA_SYS_ID id, UNUSED_ATTR uint8_t app_id,
                            const RawAddress& peer_addr) {
  /* Check if we have opening scb for the peer device. */
  uint16_t handle = bta_ag_idx_by_bdaddr(&peer_addr);
  tBTA_AG_SCB* p_scb = bta_ag_scb_by_idx(handle);

  if (p_scb && (p_scb->state == BTA_AG_OPENING_ST)) {
    if (id == BTA_ID_SYS) {
      log::warn("AG found collision (ACL) for handle {} device {}",
                unsigned(handle), ADDRESS_TO_LOGGABLE_STR(peer_addr));
    } else if (id == BTA_ID_AG) {
      log::warn("AG found collision (RFCOMM) for handle {} device {}",
                unsigned(handle), ADDRESS_TO_LOGGABLE_STR(peer_addr));
    } else {
      log::warn("AG found collision (UNKNOWN) for handle {} device {}",
                unsigned(handle), ADDRESS_TO_LOGGABLE_STR(peer_addr));
    }
    bta_ag_sm_execute(p_scb, BTA_AG_COLLISION_EVT, tBTA_AG_DATA::kEmpty);
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_resume_open
 *
 * Description      Resume opening process.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_resume_open(tBTA_AG_SCB* p_scb) {
  if (p_scb->state == BTA_AG_INIT_ST) {
    log::info("Resume connection to {}, handle{}",
              ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr),
              bta_ag_scb_to_idx(p_scb));
    tBTA_AG_DATA open_data = {.api_open = {.bd_addr = p_scb->peer_addr}};
    bta_ag_sm_execute(p_scb, BTA_AG_API_OPEN_EVT, open_data);
  } else {
    log::verbose("device {} is already in state {}",
                 ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr), p_scb->state);
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_api_enable
 *
 * Description      Handle an API enable event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_api_enable(tBTA_AG_CBACK* p_cback) {
  /* initialize control block */
  log::info("AG api enable");
  for (tBTA_AG_SCB& scb : bta_ag_cb.scb) {
    alarm_free(scb.ring_timer);
    alarm_free(scb.codec_negotiation_timer);
    alarm_free(scb.collision_timer);
    scb = {};
  }

  /* store callback function */
  bta_ag_cb.p_cback = p_cback;

  /* call init call-out */
  BTM_WriteVoiceSettings(AG_VOICE_SETTINGS);

  bta_sys_collision_register(BTA_ID_AG, bta_ag_collision_cback);

  /* call callback with enable event */
  (*bta_ag_cb.p_cback)(BTA_AG_ENABLE_EVT, nullptr);
}

/*******************************************************************************
 *
 * Function         bta_ag_api_disable
 *
 * Description      Handle an API disable event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_api_disable() {
  /* deregister all scbs in use */
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  bool do_dereg = false;
  int i;

  if (!bta_sys_is_register(BTA_ID_AG)) {
    log::error("BTA AG is already disabled, ignoring ...");
    return;
  }

  /* De-register with BTA system manager */
  bta_sys_deregister(BTA_ID_AG);

  for (i = 0; i < BTA_AG_MAX_NUM_CLIENTS; i++, p_scb++) {
    if (p_scb->in_use) {
      bta_ag_sm_execute(p_scb, BTA_AG_API_DEREGISTER_EVT, tBTA_AG_DATA::kEmpty);
      do_dereg = true;
    }
  }

  if (IS_FLAG_ENABLED(is_sco_managed_by_audio)) {
    // Stop session if not done
    bta_clear_active_device();
  }

  if (!do_dereg) {
    /* Done, send callback evt to app */
    (*bta_ag_cb.p_cback)(BTA_AG_DISABLE_EVT, nullptr);
  }

  bta_sys_collision_register(BTA_ID_AG, nullptr);
}

/*******************************************************************************
 *
 * Function         bta_ag_api_register
 *
 * Description      Handle an API event registers a new service.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_api_register(tBTA_SERVICE_MASK services, tBTA_AG_FEAT features,
                         const std::vector<std::string>& service_names,
                         uint8_t app_id) {
  tBTA_AG_SCB* p_scb = bta_ag_scb_alloc();
  log::debug("bta_ag_api_register: p_scb allocation {}",
             p_scb == nullptr ? "failed" : "success");
  if (p_scb) {
    tBTA_AG_DATA data = {};
    data.api_register.features = features;
    data.api_register.services = services;
    data.api_register.app_id = app_id;
    for (int i = 0; i < BTA_AG_NUM_IDX; i++) {
      if (!service_names[i].empty()) {
        strlcpy(data.api_register.p_name[i], service_names[i].c_str(),
                BTA_SERVICE_NAME_LEN);
      } else {
        data.api_register.p_name[i][0] = 0;
      }
    }
    bta_ag_sm_execute(p_scb, BTA_AG_API_REGISTER_EVT, data);
  } else {
    tBTA_AG bta_ag = {};
    bta_ag.reg.status = BTA_AG_FAIL_RESOURCES;
    (*bta_ag_cb.p_cback)(BTA_AG_REGISTER_EVT, &bta_ag);
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_api_result
 *
 * Description      Handle an API result event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_api_result(uint16_t handle, tBTA_AG_RES result,
                       const tBTA_AG_RES_DATA& result_data) {
  tBTA_AG_DATA event_data = {};
  event_data.api_result.result = result;
  event_data.api_result.data = result_data;
  tBTA_AG_SCB* p_scb;
  if (handle != BTA_AG_HANDLE_ALL) {
    p_scb = bta_ag_scb_by_idx(handle);
    if (p_scb) {
      log::debug("Audio gateway event for one client handle:{} scb:{}", handle,
                 p_scb->ToString());
      bta_ag_sm_execute(p_scb, static_cast<uint16_t>(BTA_AG_API_RESULT_EVT),
                        event_data);
    } else {
      log::warn(
          "Received audio gateway event for unknown AG control block handle:{}",
          handle);
    }
  } else {
    int i;
    for (i = 0, p_scb = &bta_ag_cb.scb[0]; i < BTA_AG_MAX_NUM_CLIENTS;
         i++, p_scb++) {
      if (p_scb->in_use && p_scb->svc_conn) {
        log::debug("Audio gateway event for all clients scb:{}",
                   p_scb->ToString());
        bta_ag_sm_execute(p_scb, static_cast<uint16_t>(BTA_AG_API_RESULT_EVT),
                          event_data);
      }
    }
  }
}

static void bta_ag_better_state_machine(tBTA_AG_SCB* p_scb, uint16_t event,
                                        const tBTA_AG_DATA& data) {
  switch (p_scb->state) {
    case BTA_AG_INIT_ST:
      switch (event) {
        case BTA_AG_API_REGISTER_EVT:
          bta_ag_register(p_scb, data);
          break;
        case BTA_AG_API_DEREGISTER_EVT:
          bta_ag_deregister(p_scb, data);
          break;
        case BTA_AG_API_OPEN_EVT:
          p_scb->state = BTA_AG_OPENING_ST;
          bta_ag_start_open(p_scb, data);
          break;
        case BTA_AG_RFC_OPEN_EVT:
          p_scb->state = BTA_AG_OPEN_ST;
          bta_ag_rfc_acp_open(p_scb, data);
          bta_ag_sco_listen(p_scb, data);
          break;
        case BTA_AG_SCO_OPEN_EVT:
          log::info("Opening sco for EVT BTA_AG_SCO_OPEN_EVT");
          bta_ag_sco_conn_open(p_scb, data);
          break;
        case BTA_AG_SCO_CLOSE_EVT:
          bta_ag_sco_conn_close(p_scb, data);
          break;
        case BTA_AG_DISC_ACP_RES_EVT:
          bta_ag_free_db(p_scb, data);
          break;
        default:
          log::error("unknown event {} at state {}", event, p_scb->state);
          break;
      }
      break;
    case BTA_AG_OPENING_ST:
      switch (event) {
        case BTA_AG_API_DEREGISTER_EVT:
          p_scb->state = BTA_AG_CLOSING_ST;
          bta_ag_rfc_do_close(p_scb, data);
          bta_ag_start_dereg(p_scb, data);
          break;
        case BTA_AG_API_OPEN_EVT:
          bta_ag_open_fail(p_scb, data);
          break;
        case BTA_AG_API_CLOSE_EVT:
          p_scb->state = BTA_AG_CLOSING_ST;
          bta_ag_rfc_do_close(p_scb, data);
          break;
        case BTA_AG_RFC_OPEN_EVT:
          p_scb->state = BTA_AG_OPEN_ST;
          bta_ag_rfc_open(p_scb, data);
          bta_ag_sco_listen(p_scb, data);
          break;
        case BTA_AG_RFC_CLOSE_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_rfc_fail(p_scb, data);
          break;
        case BTA_AG_SCO_OPEN_EVT:
          log::info("Opening sco for EVT BTA_AG_SCO_OPEN_EVT");
          bta_ag_sco_conn_open(p_scb, data);
          break;
        case BTA_AG_SCO_CLOSE_EVT:
          bta_ag_sco_conn_close(p_scb, data);
          break;
        case BTA_AG_DISC_INT_RES_EVT:
          bta_ag_disc_int_res(p_scb, data);
          break;
        case BTA_AG_DISC_OK_EVT:
          bta_ag_rfc_do_open(p_scb, data);
          break;
        case BTA_AG_DISC_FAIL_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_disc_fail(p_scb, data);
          break;
        case BTA_AG_COLLISION_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_handle_collision(p_scb, data);
          break;
        default:
          log::error("unknown event {} at state {}", event, p_scb->state);
          break;
      }
      break;
    case BTA_AG_OPEN_ST:
      switch (event) {
        case BTA_AG_API_DEREGISTER_EVT:
          p_scb->state = BTA_AG_CLOSING_ST;
          bta_ag_start_close(p_scb, data);
          bta_ag_start_dereg(p_scb, data);
          break;
        case BTA_AG_API_OPEN_EVT:
          bta_ag_open_fail(p_scb, data);
          break;
        case BTA_AG_API_CLOSE_EVT:
          p_scb->state = BTA_AG_CLOSING_ST;
          bta_ag_start_close(p_scb, data);
          break;
        case BTA_AG_API_AUDIO_OPEN_EVT:
          bta_ag_sco_open(p_scb, data);
          break;
        case BTA_AG_API_AUDIO_CLOSE_EVT:
          bta_ag_sco_close(p_scb, data);
          break;
        case BTA_AG_API_RESULT_EVT:
          bta_ag_result(p_scb, data);
          break;
        case BTA_AG_API_SETCODEC_EVT:
          bta_ag_setcodec(p_scb, data);
          break;
        case BTA_AG_RFC_CLOSE_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_rfc_close(p_scb, data);
          break;
        case BTA_AG_RFC_DATA_EVT:
          bta_ag_rfc_data(p_scb, data);
          break;
        case BTA_AG_SCO_OPEN_EVT:
          log::info("Opening sco for EVT BTA_AG_SCO_OPEN_EVT");
          bta_ag_sco_conn_open(p_scb, data);
          bta_ag_post_sco_open(p_scb, data);
          break;
        case BTA_AG_SCO_CLOSE_EVT:
          bta_ag_sco_conn_close(p_scb, data);
          bta_ag_post_sco_close(p_scb, data);
          break;
        case BTA_AG_DISC_ACP_RES_EVT:
          bta_ag_disc_acp_res(p_scb, data);
          break;
        case BTA_AG_RING_TIMEOUT_EVT:
          bta_ag_send_ring(p_scb, data);
          break;
        case BTA_AG_SVC_TIMEOUT_EVT:
          p_scb->state = BTA_AG_CLOSING_ST;
          bta_ag_start_close(p_scb, data);
          break;
        default:
          log::error("unknown event {} at state {}", event, p_scb->state);
          break;
      }
      break;
    case BTA_AG_CLOSING_ST:
      switch (event) {
        case BTA_AG_API_DEREGISTER_EVT:
          bta_ag_start_dereg(p_scb, data);
          break;
        case BTA_AG_API_OPEN_EVT:
          bta_ag_open_fail(p_scb, data);
          break;
        case BTA_AG_RFC_CLOSE_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_rfc_close(p_scb, data);
          break;
        case BTA_AG_SCO_OPEN_EVT:
          log::info("Opening sco for EVT BTA_AG_SCO_OPEN_EVT");
          bta_ag_sco_conn_open(p_scb, data);
          break;
        case BTA_AG_SCO_CLOSE_EVT:
          bta_ag_sco_conn_close(p_scb, data);
          bta_ag_post_sco_close(p_scb, data);
          break;
        case BTA_AG_DISC_ACP_RES_EVT:
          bta_ag_free_db(p_scb, data);
          break;
        case BTA_AG_DISC_INT_RES_EVT:
          p_scb->state = BTA_AG_INIT_ST;
          bta_ag_free_db(p_scb, data);
          break;
        default:
          log::error("unknown event {} at state {}", event, p_scb->state);
          break;
      }
      break;
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_sm_execute
 *
 * Description      State machine event handling function for AG
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_sm_execute(tBTA_AG_SCB* p_scb, uint16_t event,
                       const tBTA_AG_DATA& data) {
  uint16_t previous_event = event;
  uint8_t previous_state = p_scb->state;

  log::debug(
      "Execute AG event handle:0x{:04x} bd_addr:{} state:{}[0x{:02x}] "
      "event:{}[0x{:04x}] result:{}[0x{:02x}]",
      bta_ag_scb_to_idx(p_scb), ADDRESS_TO_LOGGABLE_CSTR(p_scb->peer_addr),
      bta_ag_state_str(p_scb->state), p_scb->state, bta_ag_evt_str(event),
      event, bta_ag_res_str(data.api_result.result), data.api_result.result);

  bta_ag_better_state_machine(p_scb, event, data);

  if (p_scb->state != previous_state) {
    log::debug(
        "State changed handle:0x{:04x} bd_addr:{} "
        "state_change:{}[0x{:02x}]->{}[0x{:02x}] event:{}[0x{:04x}] "
        "result:{}[0x{:02x}]",
        bta_ag_scb_to_idx(p_scb), ADDRESS_TO_LOGGABLE_CSTR(p_scb->peer_addr),
        bta_ag_state_str(previous_state), previous_state,
        bta_ag_state_str(p_scb->state), p_scb->state,
        bta_ag_evt_str(previous_event), previous_event,
        bta_ag_res_str(data.api_result.result), data.api_result.result);
  }
}

void bta_ag_sm_execute_by_handle(uint16_t handle, uint16_t event,
                                 const tBTA_AG_DATA& data) {
  tBTA_AG_SCB* p_scb = bta_ag_scb_by_idx(handle);
  if (p_scb) {
    log::debug("AG state machine event:{}[0x{:04x}] handle:0x{:04x}",
               bta_ag_evt_str(event), event, handle);
    bta_ag_sm_execute(p_scb, event, data);
  }
}

/**
 * Handles event from bta_sys_sendmsg(). It is here to support legacy alarm
 * implementation that is mainly for timeouts.
 *
 * @param p_msg event message
 * @return True to free p_msg, or False if p_msg is freed within this function
 */
bool bta_ag_hdl_event(const BT_HDR_RIGID* p_msg) {
  switch (p_msg->event) {
    case BTA_AG_RING_TIMEOUT_EVT:
    case BTA_AG_SVC_TIMEOUT_EVT:
      bta_ag_sm_execute_by_handle(p_msg->layer_specific, p_msg->event,
                                  tBTA_AG_DATA::kEmpty);
      break;
    default:
      log::fatal("bad event {} layer_specific={}", p_msg->event,
                 p_msg->layer_specific);
      break;
  }
  return true;
}
