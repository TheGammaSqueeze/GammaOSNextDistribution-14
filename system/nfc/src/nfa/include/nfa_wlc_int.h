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
 *  This is the private interface file for NFA_WLC
 *
 ******************************************************************************/
#ifndef NFA_WLC_INT_H
#define NFA_WLC_INT_H

#include "nfa_api.h"
#include "nfa_sys.h"
#include "nfa_wlc_api.h"
#include "nfc_api.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/
#define WPT_DURATION_INT_MASK 0xC0
#define WPT_DURATION_INT_MAX 0x13

#define POWER_ADJ_REQ_INC_MAX 0x14
#define POWER_ADJ_REQ_DEC_MIN 0xF6

#define NCI_WPT_START_CMD_SIZE 6

/* NFA_WLC flags */
/* WLC is enabled                                                        */
#define NFA_WLC_FLAGS_NON_AUTO_MODE_ENABLED 0x00000001
/* Waiting for end of power transfer phase                               */
#define NFA_WLC_FLAGS_WPT_NTF_PENDING 0x00000002

/* WLC events */
enum {
  /* device manager local device API events */
  NFA_WLC_API_ENABLE_EVT = NFA_SYS_EVT_START(NFA_ID_WLC),
  NFA_WLC_API_START_EVT,
  // NFA_WLC_API_STOP_EVT,
  NFA_WLC_API_NON_AUTO_START_WPT_EVT,
  NFA_WLC_API_REMOVE_EP_EVT,
};

/* WLC control block */
typedef struct {
  uint32_t flags; /* NFA_WLC flags (see definitions for NFA_WLC_FLAGS_*)    */
  tNFA_WLC_CBACK* p_wlc_cback; /* NFA WLC callback */

  /* NFCC power mode */
  uint8_t wlc_mode;
} tNFA_WLC_CB;
extern tNFA_WLC_CB nfa_wlc_cb;

/* data type for NFA_DM_API_ENABLE_EVT */
typedef struct {
  NFC_HDR hdr;
  // tNFA_DM_CBACK* p_dm_cback;
  tNFA_WLC_CBACK* p_wlc_cback;
} tNFA_WLC_API_ENABLE;

/* data type for NFA_WLC_API_START_EVT */
typedef struct {
  NFC_HDR hdr;
  tNFA_WLC_MODE mode;
} tNFA_WLC_API_START;

/* data type for NFA_WLC_API_NON_AUTO_START_WPT_EVT */
typedef struct {
  NFC_HDR hdr;
  uint8_t power_adj_req;
  uint8_t wpt_time_int;
} tNFA_WLC_API_NON_AUTO_START_WPT;

/* union of all data types */
typedef union {
  /* GKI event buffer header */
  NFC_HDR hdr; /* For NFA_WLC_API_STOP_EVT */
  tNFA_WLC_API_ENABLE enable;
  tNFA_WLC_API_START start;
  tNFA_WLC_API_NON_AUTO_START_WPT non_auto_start_wpt;
} tNFA_WLC_MSG;

/* type definition for action functions */
typedef bool (*tNFA_WLC_ACTION)(tNFA_WLC_MSG* p_data);

/* Action function prototypes */
extern bool nfa_wlc_enable(tNFA_WLC_MSG* p_data);
extern bool nfa_wlc_start(tNFA_WLC_MSG* p_data);
extern bool nfa_wlc_non_auto_start_wpt(tNFA_WLC_MSG* p_data);

extern void nfa_wlc_init(void);
extern void nfa_wlc_event_notify(tNFA_WLC_EVT event, tNFA_WLC_EVT_DATA* p_data);

#endif /* NFA_WLC_INT_H */
