/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <string>

#include "bta/include/bta_api.h"
#include "bta/sys/bta_sys.h"
#include "macros.h"
#include "stack/btm/neighbor_inquiry.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/sdp_status.h"
#include "stack/sdp/sdp_discovery_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

#define BTA_SERVICE_ID_TO_SERVICE_MASK(id) (1 << (id))

/* DM search events */
typedef enum : uint16_t {
  /* DM search API events */
  BTA_DM_API_SEARCH_EVT = BTA_SYS_EVT_START(BTA_ID_DM_SEARCH),
  BTA_DM_API_SEARCH_CANCEL_EVT,
  BTA_DM_API_DISCOVER_EVT,
  BTA_DM_INQUIRY_CMPL_EVT,
  BTA_DM_REMT_NAME_EVT,
  BTA_DM_SDP_RESULT_EVT,
  BTA_DM_SEARCH_CMPL_EVT,
  BTA_DM_DISCOVERY_RESULT_EVT,
  BTA_DM_DISC_CLOSE_TOUT_EVT,
} tBTA_DM_EVT;

inline std::string bta_dm_event_text(const tBTA_DM_EVT& event) {
  switch (event) {
    CASE_RETURN_TEXT(BTA_DM_API_SEARCH_EVT);
    CASE_RETURN_TEXT(BTA_DM_API_SEARCH_CANCEL_EVT);
    CASE_RETURN_TEXT(BTA_DM_API_DISCOVER_EVT);
    CASE_RETURN_TEXT(BTA_DM_INQUIRY_CMPL_EVT);
    CASE_RETURN_TEXT(BTA_DM_REMT_NAME_EVT);
    CASE_RETURN_TEXT(BTA_DM_SDP_RESULT_EVT);
    CASE_RETURN_TEXT(BTA_DM_SEARCH_CMPL_EVT);
    CASE_RETURN_TEXT(BTA_DM_DISCOVERY_RESULT_EVT);
    CASE_RETURN_TEXT(BTA_DM_DISC_CLOSE_TOUT_EVT);
    default:
      return base::StringPrintf("UNKNOWN[0x%04x]", event);
  }
}

/* data type for BTA_DM_API_SEARCH_EVT */
typedef struct {
  BT_HDR_RIGID hdr;
  tBTA_SERVICE_MASK services;
  tBTA_DM_SEARCH_CBACK* p_cback;
} tBTA_DM_API_SEARCH;

/* data type for BTA_DM_API_DISCOVER_EVT */
typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress bd_addr;
  tBTA_DM_SEARCH_CBACK* p_cback;
  tBT_TRANSPORT transport;
} tBTA_DM_API_DISCOVER;

typedef struct {
  BT_HDR_RIGID hdr;
} tBTA_DM_API_DISCOVERY_CANCEL;

typedef struct {
  BT_HDR_RIGID hdr;
  RawAddress bd_addr;
  BD_NAME bd_name; /* Name of peer device. */
  tHCI_STATUS hci_status;
} tBTA_DM_REMOTE_NAME;

/* data type for tBTA_DM_DISC_RESULT */
typedef struct {
  BT_HDR_RIGID hdr;
  tBTA_DM_SEARCH result;
} tBTA_DM_DISC_RESULT;

/* data type for BTA_DM_INQUIRY_CMPL_EVT */
typedef struct {
  BT_HDR_RIGID hdr;
  uint8_t num;
} tBTA_DM_INQUIRY_CMPL;

/* data type for BTA_DM_SDP_RESULT_EVT */
typedef struct {
  BT_HDR_RIGID hdr;
  tSDP_RESULT sdp_result;
} tBTA_DM_SDP_RESULT;

typedef struct {
  BT_HDR_RIGID hdr;
  bool enable;
} tBTA_DM_API_BLE_FEATURE;

/* union of all data types */
typedef union {
  /* GKI event buffer header */
  BT_HDR_RIGID hdr;

  tBTA_DM_API_SEARCH search;

  tBTA_DM_API_DISCOVER discover;

  tBTA_DM_REMOTE_NAME remote_name_msg;

  tBTA_DM_DISC_RESULT disc_result;

  tBTA_DM_INQUIRY_CMPL inq_cmpl;

  tBTA_DM_SDP_RESULT sdp_event;

} tBTA_DM_MSG;

/* DM search state */
typedef enum {

  BTA_DM_SEARCH_IDLE,
  BTA_DM_SEARCH_ACTIVE,
  BTA_DM_SEARCH_CANCELLING,
  BTA_DM_DISCOVER_ACTIVE

} tBTA_DM_STATE;

inline std::string bta_dm_state_text(const tBTA_DM_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(BTA_DM_SEARCH_IDLE);
    CASE_RETURN_TEXT(BTA_DM_SEARCH_ACTIVE);
    CASE_RETURN_TEXT(BTA_DM_SEARCH_CANCELLING);
    CASE_RETURN_TEXT(BTA_DM_DISCOVER_ACTIVE);
    default:
      return base::StringPrintf("UNKNOWN[%d]", state);
  }
}

/* DM search control block */
typedef struct {
  tBTA_DM_SEARCH_CBACK* p_search_cback;
  tBTM_INQ_INFO* p_btm_inq_info;
  tBTA_SERVICE_MASK services;
  tBTA_SERVICE_MASK services_to_search;
  tBTA_SERVICE_MASK services_found;
  tSDP_DISCOVERY_DB* p_sdp_db;
  tBTA_DM_STATE state;
  RawAddress peer_bdaddr;
  bool name_discover_done;
  BD_NAME peer_name;
  alarm_t* search_timer;
  uint8_t service_index;
  tBTA_DM_MSG* p_pending_search;
  fixed_queue_t* pending_discovery_queue;
  bool wait_disc;
  bool sdp_results;
  bluetooth::Uuid uuid;
  uint8_t peer_scn;
  tBT_TRANSPORT transport;
  tBTA_DM_SEARCH_CBACK* p_scan_cback;
  tBTA_DM_SEARCH_CBACK* p_csis_scan_cback;
  tGATT_IF client_if;
  uint8_t uuid_to_search;
  bool gatt_disc_active;
  uint16_t conn_id;
  alarm_t* gatt_close_timer;    /* GATT channel close delay timer */
  RawAddress pending_close_bda; /* pending GATT channel remote device address */

} tBTA_DM_SEARCH_CB;

extern const uint32_t bta_service_id_to_btm_srv_id_lkup_tbl[];
extern const uint16_t bta_service_id_to_uuid_lkup_tbl[];

namespace fmt {
template <>
struct formatter<tBTA_DM_EVT> : enum_formatter<tBTA_DM_EVT> {};
template <>
struct formatter<tBTA_DM_STATE> : enum_formatter<tBTA_DM_STATE> {};
}  // namespace fmt
