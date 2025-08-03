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
 *  NFA wireless charging API functions
 *
 ******************************************************************************/
#ifndef NFA_WLC_API_H
#define NFA_WLC_API_H

#include "nfa_api.h"
#include "nfc_target.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/
enum {
  NFA_WLC_NON_AUTONOMOUS = 0, /* Default behavior: DH handles WLC protocol */
  NFA_WLC_SEMI_AUTONOMOUS,    /* WLC protocol split between DH and NFCC    */
  NFA_WLC_AUTONOMOUS          /* NFCC handles WLC protocol                 */
};
typedef uint8_t tNFA_WLC_MODE;

/*****************************************************************************
**  NFA WLC Constants and definitions
*****************************************************************************/

/* Union of all WLC callback structures */
typedef union {
  tNFA_STATUS status; /* NFA_WLC_..._EVT        */
  uint8_t wpt_end_cdt;

} tNFA_WLC_EVT_DATA;

/* NFA WLC callback events */
typedef enum {
  NFA_WLC_ENABLE_RESULT_EVT,    /* The status for NFA_WlcEnable () */
  NFA_WLC_START_RESULT_EVT,     /* The status for NFA_WlcStart () */
  NFA_WLC_START_WPT_RESULT_EVT, /* The status for NFA_WlcStartWPT () */
  NFA_WLC_CHARGING_RESULT_EVT,  /* Notification of WPT_START completion */
} tNFA_WLC_EVT;

/* NFA WLC Callback */
typedef void(tNFA_WLC_CBACK)(tNFA_WLC_EVT event, tNFA_WLC_EVT_DATA* p_data);

/*****************************************************************************
**  External Function Declarations
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
extern tNFA_STATUS NFA_WlcEnable(tNFA_WLC_CBACK* p_wlc_cback);

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
extern tNFA_STATUS NFA_WlcStart(tNFA_WLC_MODE mode);

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
extern tNFA_STATUS NFA_WlcStartWPT(uint8_t power_adj_req, uint8_t wpt_time_int);

#endif /* NFA_WLC_API_H */
