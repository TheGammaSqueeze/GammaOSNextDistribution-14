/******************************************************************************
 *
 *  Copyright (C) 2023 The Android Open Source Project.
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
 *  This file contains the action functions the NFA_WLC state machine.
 *
 ******************************************************************************/
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <log/log.h>
#include <string.h>

#include "nfa_dm_int.h"
#include "nfa_rw_int.h"
#include "nfa_wlc_int.h"

using android::base::StringPrintf;

/*******************************************************************************
**
** Function         nfa_wlc_enable
**
** Description      Initialize the NFC WLC manager
**
** Returns          TRUE (message buffer to be freed by caller)
**
*******************************************************************************/
bool nfa_wlc_enable(tNFA_WLC_MSG* p_data) {
  LOG(DEBUG) << StringPrintf("%s; nfa_dm_cb.flags=0x%x", __func__,
                             nfa_dm_cb.flags);
  tNFA_WLC_EVT_DATA wlc_cback_data;

  /* Check if NFA is already enabled */
  if ((nfa_dm_cb.flags & NFA_DM_FLAGS_DM_IS_ACTIVE) &&
      !((nfa_dm_cb.flags & NFA_DM_FLAGS_ENABLE_EVT_PEND) ||
        (nfa_dm_cb.flags & NFA_DM_FLAGS_DM_DISABLING_NFC))) {
    /* Store Enable parameters */
    nfa_wlc_cb.p_wlc_cback = p_data->enable.p_wlc_cback;

    wlc_cback_data.status = NFA_STATUS_OK;
  } else {
    LOG(DEBUG) << StringPrintf(
        "%s; DM not active or enable event pending or DM disabling NFC ",
        __func__);
    wlc_cback_data.status = NFA_STATUS_FAILED;
  }
  (*(p_data->enable.p_wlc_cback))(NFA_WLC_ENABLE_RESULT_EVT, &wlc_cback_data);

  return true;
}

/*******************************************************************************
**
** Function         nfa_wlc_start
**
** Description      Start WLC-P Non-Autonomous RF Interface Extension
**                  if conditions are met (extension supported by NFCC,
**                  NFCC in POLL_ACTIVE, correct protocol for activated tag,
**                  DM module in appropriate state...).
**
** Returns          TRUE upon successful start else FALSE
**
*******************************************************************************/
bool nfa_wlc_start(tNFA_WLC_MSG* p_data) {
  LOG(DEBUG) << StringPrintf("%s; ", __func__);

  /* If mode is WLC-P Non-Autonomous mode:
   * Support for WLC-P Non-Autonomous RF Interface Extension in CORE_INIT_RSP
   * mandated Non-Autonomous RF Frame Extension shall be in stopped state NFCC
   * in RFST_POLL_ACTIVE Frame RF or ISO-DEP Interfaces shall be in activated
   * state EP protocol: T2T, T3T, T5T, ISO-DEP DH not waiting for a response
   * from the EP
   */

  if (p_data->start.mode == NFA_WLC_NON_AUTONOMOUS) {
    // TODO: check WLC-P Non-Autonomous RF Interface Extension is enabled in
    // CORE_INIT_RSP

    if (nfa_wlc_cb.flags & NFA_WLC_FLAGS_NON_AUTO_MODE_ENABLED) {
      /* Non-Autonomous RF Frame Extension shall be in stopped state */
      /* return status not stopped to JNI ???*/
      LOG(ERROR) << StringPrintf(
          "%s; WLCP Non-autonomous Extension not "
          "in stopped state",
          __func__);
      return false;
    }

    if (nfa_dm_cb.disc_cb.disc_state != NFA_DM_RFST_POLL_ACTIVE) {
      LOG(ERROR) << StringPrintf(
          "%s; NFCC not in WLCP "
          "RFST_POLL_ACTIVE state",
          __func__);
      return false;
    }

    if (!((nfa_rw_cb.protocol == NFC_PROTOCOL_T2T) ||
          (nfa_rw_cb.protocol == NFC_PROTOCOL_T3T) ||
          (nfa_rw_cb.protocol == NFC_PROTOCOL_T5T) ||
          (nfa_rw_cb.protocol == NFA_PROTOCOL_ISO_DEP))) {
      LOG(ERROR) << StringPrintf("%s; Invalid RF protocol activated", __func__);
      return false;
    }

    if (nfa_rw_cb.flags & NFA_RW_FL_API_BUSY) {
      LOG(ERROR) << StringPrintf("%s; RW API already busy", __func__);
      /* TODO: pending till RW action completes? */
      return false;
    }
    if (nfa_dm_cb.disc_cb.disc_flags &
        (NFA_DM_DISC_FLAGS_W4_RSP | NFA_DM_DISC_FLAGS_W4_NTF |
         NFA_DM_DISC_FLAGS_STOPPING |  /* Stop RF discovery is pending */
         NFA_DM_DISC_FLAGS_DISABLING)) /* Disable NFA is pending */
    {
      // TODO: shall we check other modules busy?
      return false;
    }

    nfa_wlc_cb.wlc_mode = p_data->start.mode;

    // TODO: remove as only for testing, replace by extension activation
    nfa_dm_cb.flags |= NFA_DM_FLAGS_RF_EXT_ACTIVE;
    nfa_dm_cb.flags |= NFA_DM_FLAGS_WLCP_ENABLED;

    tNFA_WLC_EVT_DATA wlc_cback_data;
    wlc_cback_data.status = NFA_STATUS_OK;
    nfa_wlc_event_notify(NFA_WLC_START_RESULT_EVT, &wlc_cback_data);

    return true;

  } else {
    LOG(ERROR) << StringPrintf("%s; Wireless Charging mode not supported",
                               __func__);
    return false;
  }
}

/*******************************************************************************
**
** Function         nfa_wlc_non_auto_start_wpt
**
** Description      Stop timer for presence check
**
** Returns          Nothing
**
*******************************************************************************/
bool nfa_wlc_non_auto_start_wpt(tNFA_WLC_MSG* p_data) {
  LOG(DEBUG) << StringPrintf("%s; power_adj_req=0x%x, wpt_time_int=0x%x",
                             __func__, p_data->non_auto_start_wpt.power_adj_req,
                             p_data->non_auto_start_wpt.wpt_time_int);

  nfa_dm_start_wireless_power_transfer(p_data->non_auto_start_wpt.power_adj_req,
                                       p_data->non_auto_start_wpt.wpt_time_int);

  return true;
}
