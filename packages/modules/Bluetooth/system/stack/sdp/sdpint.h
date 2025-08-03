/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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
 *  This file contains internally used SDP definitions
 *
 ******************************************************************************/

#ifndef SDP_INT_H
#define SDP_INT_H

#include <base/strings/stringprintf.h>

#include <cstdint>

#include "internal_include/bt_target.h"
#include "macros.h"
#include "osi/include/alarm.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/l2c_api.h"
#include "stack/include/sdp_callback.h"
#include "stack/sdp/sdp_discovery_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/* Continuation length - we use a 2-byte offset */
#define SDP_CONTINUATION_LEN 2
#define SDP_MAX_CONTINUATION_LEN 16 /* As per the spec */

/* Timeout definitions. */
#define SDP_INACT_TIMEOUT_MS (30 * 1000) /* Inactivity timeout (in ms) */

/* Define the Protocol Data Unit (PDU) types.
 */
#define SDP_PDU_ERROR_RESPONSE 0x01
#define SDP_PDU_SERVICE_SEARCH_REQ 0x02
#define SDP_PDU_SERVICE_SEARCH_RSP 0x03
#define SDP_PDU_SERVICE_ATTR_REQ 0x04
#define SDP_PDU_SERVICE_ATTR_RSP 0x05
#define SDP_PDU_SERVICE_SEARCH_ATTR_REQ 0x06
#define SDP_PDU_SERVICE_SEARCH_ATTR_RSP 0x07

/* Max UUIDs and attributes we support per sequence */
#define MAX_UUIDS_PER_SEQ 16
#define MAX_ATTR_PER_SEQ 16

/* Max length we support for any attribute */
#ifdef SDP_MAX_ATTR_LEN
#define MAX_ATTR_LEN SDP_MAX_ATTR_LEN
#else
#define MAX_ATTR_LEN 256
#endif

/* Internal UUID sequence representation */
typedef struct {
  uint16_t len;
  uint8_t value[bluetooth::Uuid::kNumBytes128];
} tUID_ENT;

typedef struct {
  uint16_t num_uids;
  tUID_ENT uuid_entry[MAX_UUIDS_PER_SEQ];
} tSDP_UUID_SEQ;

/* Internal attribute sequence definitions */
typedef struct {
  uint16_t start;
  uint16_t end;
} tATT_ENT;

typedef struct {
  uint16_t num_attr;
  tATT_ENT attr_entry[MAX_ATTR_PER_SEQ];
} tSDP_ATTR_SEQ;

/* Define the attribute element of the SDP database record */
typedef struct {
  uint32_t len;       /* Number of bytes in the entry */
  uint8_t* value_ptr; /* Points to attr_pad */
  uint16_t id;
  uint8_t type;
} tSDP_ATTRIBUTE;

/* An SDP record consists of a handle, and 1 or more attributes */
typedef struct {
  uint32_t record_handle;
  uint32_t free_pad_ptr;
  uint16_t num_attributes;
  tSDP_ATTRIBUTE attribute[SDP_MAX_REC_ATTR];
  uint8_t attr_pad[SDP_MAX_PAD_LEN];
} tSDP_RECORD;

/* Define the SDP database */
typedef struct {
  uint32_t
      di_primary_handle; /* Device ID Primary record or NULL if nonexistent */
  uint16_t num_records;
  tSDP_RECORD record[SDP_MAX_RECORDS];
} tSDP_DB;

/* Continuation information for the SDP server response */
typedef struct {
  uint16_t next_attr_index;    /* attr index for next continuation response */
  uint16_t next_attr_start_id; /* attr id to start with for the attr index in
                                  next cont. response */
  const tSDP_RECORD* prev_sdp_rec; /* last sdp record that was completely sent
                                in the response */
  bool last_attr_seq_desc_sent; /* whether attr seq length has been sent
                                   previously */
  uint16_t attr_offset; /* offset within the attr to keep trak of partial
                           attributes in the responses */
} tSDP_CONT_INFO;

enum : uint8_t {
  SDP_STATE_IDLE = 0,
  SDP_STATE_CONN_SETUP = 1,
  SDP_STATE_CFG_SETUP = 2,
  SDP_STATE_CONNECTED = 3,
  SDP_STATE_CONN_PEND = 4,
};
typedef uint8_t tSDP_STATE;

inline std::string sdp_state_text(const tSDP_STATE& state) {
  switch (state) {
    CASE_RETURN_TEXT(SDP_STATE_IDLE);
    CASE_RETURN_TEXT(SDP_STATE_CONN_SETUP);
    CASE_RETURN_TEXT(SDP_STATE_CFG_SETUP);
    CASE_RETURN_TEXT(SDP_STATE_CONNECTED);
    CASE_RETURN_TEXT(SDP_STATE_CONN_PEND);
    default:
      return std::string("UNKNOWN[") + std::to_string(state) + std::string("]");
  }
}

enum : uint8_t {
  SDP_FLAGS_IS_ORIG = 0x01,
  SDP_FLAGS_HIS_CFG_DONE = 0x02,
  SDP_FLAGS_MY_CFG_DONE = 0x04,
};
typedef uint8_t tSDP_FLAGS;

inline std::string sdp_flags_text(const tSDP_FLAGS& flags) {
  switch (flags) {
    CASE_RETURN_TEXT(SDP_FLAGS_IS_ORIG);
    CASE_RETURN_TEXT(SDP_FLAGS_HIS_CFG_DONE);
    CASE_RETURN_TEXT(SDP_FLAGS_MY_CFG_DONE);
    default:
      return std::string("UNKNOWN[") + std::to_string(flags) + std::string("]");
  }
}

enum : uint8_t {
  SDP_DISC_WAIT_CONN = 0,
  SDP_DISC_WAIT_HANDLES = 1,
  SDP_DISC_WAIT_ATTR = 2,
  SDP_DISC_WAIT_SEARCH_ATTR = 3,
  SDP_DISC_WAIT_UNUSED4 = 4,
  SDP_DISC_WAIT_CANCEL = 5,
};
typedef uint8_t tSDP_DISC_WAIT;

/* Define the SDP Connection Control Block */
struct tCONN_CB {
  uint8_t con_state;
  uint8_t con_flags;

  RawAddress device_address;
  alarm_t* sdp_conn_timer;
  uint16_t rem_mtu_size;
  uint16_t connection_id;
  uint16_t list_len; /* length of the response in the GKI buffer */
  uint16_t pse_dynamic_attributes_len; /* length of the attributes need to be
                             added in final sdp response len */
  uint8_t* rsp_list; /* pointer to GKI buffer holding response */

  tSDP_DISCOVERY_DB* p_db; /* Database to save info into   */
  tSDP_DISC_CMPL_CB* p_cb; /* Callback for discovery done  */
  tSDP_DISC_CMPL_CB2*
      p_cb2; /* Callback for discovery done piggy back with the user data */
  const void* user_data; /* piggy back user data */
  uint32_t
      handles[SDP_MAX_DISC_SERVER_RECS]; /* Discovered server record handles */
  uint16_t num_handles;                  /* Number of server handles     */
  uint16_t cur_handle;                   /* Current handle being processed */
  uint16_t transaction_id;
  uint16_t disconnect_reason; /* Disconnect reason            */

  uint8_t disc_state;
  bool is_attr_search;

  uint16_t cont_offset;     /* Continuation state data in the server response */
  tSDP_CONT_INFO cont_info; /* structure to hold continuation information for
                               the server response */
  tCONN_CB() = default;

 private:
  tCONN_CB(const tCONN_CB&) = delete;
};

inline std::string sdp_disc_wait_text(const tSDP_DISC_WAIT& state) {
  switch (state) {
    CASE_RETURN_TEXT(SDP_DISC_WAIT_CONN);
    CASE_RETURN_TEXT(SDP_DISC_WAIT_HANDLES);
    CASE_RETURN_TEXT(SDP_DISC_WAIT_ATTR);
    CASE_RETURN_TEXT(SDP_DISC_WAIT_SEARCH_ATTR);
    CASE_RETURN_TEXT(SDP_DISC_WAIT_CANCEL);
    default:
      return base::StringPrintf("UNKNOWN[%d]", state);
  }
}

/*  The main SDP control block */
typedef struct {
  tL2CAP_CFG_INFO l2cap_my_cfg; /* My L2CAP config     */
  tCONN_CB ccb[SDP_MAX_CONNECTIONS];
  tSDP_DB server_db;
  tL2CAP_APPL_INFO reg_info;    /* L2CAP Registration info */
  uint16_t max_attr_list_size;  /* Max attribute list size to use   */
  uint16_t max_recs_per_search; /* Max records we want per seaarch  */
} tSDP_CB;

/* Global SDP data */
extern tSDP_CB sdp_cb;

/* Functions provided by sdp_main.cc */
void sdp_init(void);
void sdp_free(void);
void sdp_disconnect(tCONN_CB* p_ccb, tSDP_REASON reason);

void sdp_conn_timer_timeout(void* data);

tCONN_CB* sdp_conn_originate(const RawAddress& p_bd_addr);

/* Functions provided by sdp_utils.cc
 */
void sdpu_log_attribute_metrics(const RawAddress& bda, tSDP_DISCOVERY_DB* p_db);
tCONN_CB* sdpu_find_ccb_by_cid(uint16_t cid);
tCONN_CB* sdpu_find_ccb_by_db(const tSDP_DISCOVERY_DB* p_db);
tCONN_CB* sdpu_allocate_ccb(void);
void sdpu_release_ccb(tCONN_CB& p_ccb);

uint8_t* sdpu_build_attrib_seq(uint8_t* p_out, uint16_t* p_attr,
                               uint16_t num_attrs);
uint8_t* sdpu_build_attrib_entry(uint8_t* p_out, const tSDP_ATTRIBUTE* p_attr);
void sdpu_build_n_send_error(tCONN_CB* p_ccb, uint16_t trans_num,
                             uint16_t error_code, char* p_error_text);

uint8_t* sdpu_extract_attr_seq(uint8_t* p, uint16_t param_len,
                               tSDP_ATTR_SEQ* p_seq);
uint8_t* sdpu_extract_uid_seq(uint8_t* p, uint16_t param_len,
                              tSDP_UUID_SEQ* p_seq);

uint8_t* sdpu_get_len_from_type(uint8_t* p, uint8_t* p_end, uint8_t type,
                                uint32_t* p_len);
bool sdpu_is_base_uuid(uint8_t* p_uuid);
bool sdpu_compare_uuid_arrays(const uint8_t* p_uuid1, uint32_t len1,
                              const uint8_t* p_uuid2, uint16_t len2);
bool sdpu_compare_uuid_with_attr(const bluetooth::Uuid& uuid,
                                 tSDP_DISC_ATTR* p_attr);

void sdpu_sort_attr_list(uint16_t num_attr, tSDP_DISCOVERY_DB* p_db);
uint16_t sdpu_get_list_len(tSDP_UUID_SEQ* uid_seq, tSDP_ATTR_SEQ* attr_seq);
uint16_t sdpu_get_attrib_seq_len(const tSDP_RECORD* p_rec,
                                 const tSDP_ATTR_SEQ* attr_seq);
uint16_t sdpu_get_attrib_entry_len(const tSDP_ATTRIBUTE* p_attr);
uint8_t* sdpu_build_partial_attrib_entry(uint8_t* p_out,
                                         const tSDP_ATTRIBUTE* p_attr,
                                         uint16_t len, uint16_t* offset);
bool SDP_AddAttributeToRecord(tSDP_RECORD* p_rec, uint16_t attr_id,
                              uint8_t attr_type, uint32_t attr_len,
                              uint8_t* p_val);
bool SDP_AddProfileDescriptorListToRecord(tSDP_RECORD* p_rec,
                                          uint16_t profile_uuid,
                                          uint16_t version);
bool SDP_DeleteAttributeFromRecord(tSDP_RECORD* p_rec, uint16_t attr_id);
uint16_t sdpu_is_avrcp_profile_description_list(const tSDP_ATTRIBUTE* p_attr);
bool sdpu_is_service_id_avrc_target(const tSDP_ATTRIBUTE* p_attr);
bool spdu_is_avrcp_version_valid(const uint16_t version);
void sdpu_set_avrc_target_version(const tSDP_ATTRIBUTE* p_attr,
                                  const RawAddress* bdaddr);
void sdpu_set_avrc_target_features(const tSDP_ATTRIBUTE* p_attr,
                                   const RawAddress* bdaddr,
                                   uint16_t profile_version);
uint16_t sdpu_get_active_ccb_cid(const RawAddress& remote_bd_addr);
bool sdpu_process_pend_ccb_same_cid(tCONN_CB& ccb);
bool sdpu_process_pend_ccb_new_cid(tCONN_CB& ccb);
void sdpu_clear_pend_ccb(tCONN_CB& ccb);
void sdpu_callback(tCONN_CB& ccb, tSDP_REASON reason);

/* Functions provided by sdp_db.cc
 */
const tSDP_RECORD* sdp_db_service_search(const tSDP_RECORD* p_rec,
                                         const tSDP_UUID_SEQ* p_seq);
tSDP_RECORD* sdp_db_find_record(uint32_t handle);
const tSDP_ATTRIBUTE* sdp_db_find_attr_in_rec(const tSDP_RECORD* p_rec,
                                              uint16_t start_attr,
                                              uint16_t end_attr);

/* Functions provided by sdp_server.cc
 */
void sdp_server_handle_client_req(tCONN_CB* p_ccb, BT_HDR* p_msg);

/* Functions provided by sdp_discovery.cc
 */
void sdp_disc_connected(tCONN_CB* p_ccb);
void sdp_disc_server_rsp(tCONN_CB* p_ccb, BT_HDR* p_msg);

void update_pce_entry_to_interop_database(RawAddress remote_addr);
bool is_sdp_pbap_pce_disabled(RawAddress remote_addr);
void sdp_save_local_pse_record_attributes(int32_t rfcomm_channel_number,
                                          int32_t l2cap_psm,
                                          int32_t profile_version,
                                          uint32_t supported_features,
                                          uint32_t supported_repositories);

#endif
