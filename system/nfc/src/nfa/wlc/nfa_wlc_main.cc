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
 *  This is the main implementation file for the NFA_WLC
 *
 ******************************************************************************/
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <string.h>

#include "nfa_wlc_int.h"

using android::base::StringPrintf;

/* NFA_WLC control block */
tNFA_WLC_CB nfa_wlc_cb;

bool nfa_wlc_handle_event(NFC_HDR* p_msg);
void nfa_wlc_sys_disable(void);

/*****************************************************************************
** Constants and types
*****************************************************************************/
static const tNFA_SYS_REG nfa_wlc_sys_reg = {nullptr, nfa_wlc_handle_event,
                                             nfa_wlc_sys_disable, nullptr};

/* NFA_WLC actions */
const tNFA_WLC_ACTION nfa_wlc_action_tbl[] = {
    nfa_wlc_enable,             /* NFA_WLC_API_ENABLE_EVT            */
    nfa_wlc_start,              /* NFA_WLC_API_START_EVT              */
    nfa_wlc_non_auto_start_wpt, /* NFA_WLC_API_NON_AUTO_START_WPT_EVT */
};

#define NFA_WLC_ACTION_TBL_SIZE \
  (sizeof(nfa_wlc_action_tbl) / sizeof(tNFA_WLC_ACTION))

/*****************************************************************************
** Local function prototypes
*****************************************************************************/
static std::string nfa_wlc_evt_2_str(uint16_t event);

/*******************************************************************************
**
** Function         nfa_wlc_init
**
** Description      Initialize NFA WLC
**
** Returns          none
**
*******************************************************************************/
void nfa_wlc_init(void) {
  LOG(DEBUG) << __func__;

  /* initialize control block */
  memset(&nfa_wlc_cb, 0, sizeof(tNFA_WLC_CB));

  /* register message handler on NFA SYS */
  nfa_sys_register(NFA_ID_WLC, &nfa_wlc_sys_reg);
}

/*******************************************************************************
**
** Function         nfa_wlc_sys_disable
**
** Description      Clean up rw sub-system
**
**
** Returns          none
**
*******************************************************************************/
void nfa_wlc_sys_disable(void) {
  LOG(DEBUG) << __func__;

  nfa_sys_deregister(NFA_ID_WLC);
}

/*******************************************************************************
**
** Function         nfa_wlc_event_notify
**
** Description      Called by nfa_dm to handle WLC dedicated events
**
** Returns          none
**
*******************************************************************************/
void nfa_wlc_event_notify(tNFA_WLC_EVT event, tNFA_WLC_EVT_DATA* p_data) {
  LOG(DEBUG) << __func__;

  if (nfa_wlc_cb.p_wlc_cback) {
    (*nfa_wlc_cb.p_wlc_cback)(event, p_data);
  } else {
    LOG(DEBUG) << StringPrintf("%s; callback pointer null", __func__);
  }
}

/*******************************************************************************
**
** Function         nfa_wlc_handle_event
**
** Description      nfa wlc main event handling function.
**
** Returns          TRUE if caller should free p_msg buffer
**
*******************************************************************************/
bool nfa_wlc_handle_event(NFC_HDR* p_msg) {
  uint16_t act_idx;

  LOG(DEBUG) << StringPrintf("%s; event: %s (0x%02x), flags: %08x", __func__,
                             nfa_wlc_evt_2_str(p_msg->event).c_str(),
                             p_msg->event, nfa_wlc_cb.flags);

  /* Get NFA_WLC sub-event */
  act_idx = (p_msg->event & 0x00FF);
  if (act_idx < (NFA_WLC_ACTION_TBL_SIZE)) {
    return (*nfa_wlc_action_tbl[act_idx])((tNFA_WLC_MSG*)p_msg);
  } else {
    LOG(ERROR) << StringPrintf("%s; unhandled event 0x%02X", __func__,
                               p_msg->event);
    return true;
  }
}

/*******************************************************************************
**
** Function         nfa_wlc_evt_2_str
**
** Description      convert nfa_wlc evt to string
**
*******************************************************************************/
static std::string nfa_wlc_evt_2_str(uint16_t event) {
  switch (event) {
    case NFA_WLC_API_ENABLE_EVT:
      return "NFA_WLC_API_ENABLE_EVT";
    case NFA_WLC_API_START_EVT:
      return "NFA_WLC_API_START_EVT";
    case NFA_WLC_API_NON_AUTO_START_WPT_EVT:
      return "NFA_WLC_API_NON_AUTO_START_WPT_EVT";
    default:
      return "Unknown";
  }
}
