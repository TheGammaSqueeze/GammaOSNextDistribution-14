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
 *  NFA interface for NFC wireless charging
 *
 ******************************************************************************/
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <log/log.h>
#include <string.h>

#include "nfa_api.h"
#include "nfa_wlc_int.h"

using android::base::StringPrintf;

/*****************************************************************************
**  Constants
*****************************************************************************/

/*****************************************************************************
**  APIs
*****************************************************************************/
/*******************************************************************************
**
** Function         NFA_WlcEnable
**
** Description      This function enables WLC module callback. Prior to calling
**                  NFA_WlcEnable, WLC module must be enabled by NFA system
**                  manager (done when NFA_Enable called).
**
**                  When the enabling is completed, an NFA_WLC_ENABLE_RESULT_EVT
**                  is returned to the application using the tNFA_WLC_CBACK.
**
**                  p_wlc_cback: callback to notify later NFCC events
**
** Returns          NFA_STATUS_OK if successfully initiated
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_WlcEnable(tNFA_WLC_CBACK* p_wlc_cback) {
  tNFA_WLC_MSG* p_msg;

  LOG(DEBUG) << __func__;

  /* Validate parameters */
  if (!p_wlc_cback) {
    LOG(ERROR) << StringPrintf("%s; error null callback", __func__);
    return (NFA_STATUS_FAILED);
  }

  p_msg = (tNFA_WLC_MSG*)GKI_getbuf(sizeof(tNFA_WLC_MSG));
  if (p_msg != nullptr) {
    p_msg->enable.hdr.event = NFA_WLC_API_ENABLE_EVT;
    p_msg->enable.p_wlc_cback = p_wlc_cback;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_WlcStart
**
** Description      Perform the WLC start procedure.
**
**                  Upon successful completion of RF Interface Extension start
**                  (according to the NFC Forum NCI2.3 conditions) and upload
**                  of WLC Poller parameters (Non-Autonomous mode only),
**                  an NFA_WLC_START_RESULT_EVT is returned to the application
**                  using the tNFA_WLC_CBACK.
**
**                  mode: WLC-P Non-Autonomous (0) or Semi-Autonomous mode
**
** Returns:
**                  NFA_STATUS_OK if successfully started
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_WlcStart(tNFA_WLC_MODE mode) {
  tNFA_WLC_MSG* p_msg;

  LOG(DEBUG) << __func__;

  if (mode) {
    LOG(ERROR) << StringPrintf("%s; Wireless Charging mode not supported",
                               __func__);
    return (NFA_STATUS_INVALID_PARAM);
  }

  p_msg = (tNFA_WLC_MSG*)GKI_getbuf((uint16_t)sizeof(tNFA_WLC_MSG));
  if (p_msg != nullptr) {
    p_msg->start.hdr.event = NFA_WLC_API_START_EVT;
    p_msg->start.mode = mode;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}

/*******************************************************************************
**
** Function         NFA_WlcStartWPT
**
** Description      Start a wireless power transfer cycle in Non-Autonomous
**                  WLCP mode ([WLC2.0] Technical Specifications state 21
**                  for negotiated or state 6 for static WLC mode).
**
**                  Upon successful completion of WPT start,
**                  an NFA_WLC_START_WPT_RESULT_EVT is returned to the
*application
**                  using the tNFA_WLC_CBACK.
**
**                  When the duration for the power transfer ends or
**                  any error/completion condition occurred, NFCC notifies the
*DH
**                  with an NFA_WLC_CHARGING_RESULT_EVT and end condition value.
**
**                  power_adj_req: POWER_ADUJUST_REQ as defined in [WLC]
**                  wpt_time_int: WPT_INT_TIME as defined in [WLC]
**
** Returns:
**                  NFA_STATUS_OK if successfully started
**                  NFA_STATUS_FAILED otherwise
**
*******************************************************************************/
tNFA_STATUS NFA_WlcStartWPT(uint8_t power_adj_req, uint8_t wpt_time_int) {
  tNFA_WLC_MSG* p_msg;

  LOG(DEBUG) << StringPrintf("%s; power_adj_req: %d, wpt_time_int: %d",
                             __func__, power_adj_req, wpt_time_int);

  /* POWER_ADJ_REQ is in the range [0x00..0x14] for request to increase power
   * POWER_ADJ_REQ is in the range [0xF6..0xFF] for request to decrease power
   */
  if ((power_adj_req > POWER_ADJ_REQ_INC_MAX) &&
      (power_adj_req < POWER_ADJ_REQ_DEC_MIN)) {
    LOG(ERROR) << StringPrintf("%s; Invalid POWER_ADJ_REQ value", __func__);
    return false;
  }

  /* WPT_DURATION_INT is in the range [0x00..0x13]
   * Bits 6 and 7 must be 0b
   */
  if ((wpt_time_int > WPT_DURATION_INT_MAX) ||
      (wpt_time_int & WPT_DURATION_INT_MASK)) {
    LOG(ERROR) << StringPrintf("%s; Invalid WPT_DURATIOM_INT value", __func__);
    return false;
  }

  p_msg = (tNFA_WLC_MSG*)GKI_getbuf((uint16_t)(sizeof(tNFA_WLC_MSG)));
  if (p_msg != nullptr) {
    p_msg->hdr.event = NFA_WLC_API_NON_AUTO_START_WPT_EVT;

    p_msg->non_auto_start_wpt.power_adj_req = power_adj_req;
    p_msg->non_auto_start_wpt.wpt_time_int = wpt_time_int;

    nfa_sys_sendmsg(p_msg);

    return (NFA_STATUS_OK);
  }

  return (NFA_STATUS_FAILED);
}
