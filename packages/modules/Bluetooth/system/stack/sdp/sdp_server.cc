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
 *  This file contains functions that handle the SDP server functions.
 *  This is mainly dealing with client requests
 *
 ******************************************************************************/
#define LOG_TAG "sdp_server"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <string.h>  // memcpy

#include <cstdint>

#include "btif/include/btif_profile_storage.h"
#include "btif/include/btif_storage.h"
#include "common/init_flags.h"
#include "device/include/interop.h"
#include "device/include/interop_config.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/properties.h"
#include "stack/btm/btm_dev.h"
#include "stack/btm/btm_sco_hfp_hal.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdpdefs.h"
#include "stack/sdp/sdpint.h"

/* Maximum number of bytes to reserve out of SDP MTU for response data */
#define SDP_MAX_SERVICE_RSPHDR_LEN 12
#define SDP_MAX_SERVATTR_RSPHDR_LEN 10
#define SDP_MAX_ATTR_RSPHDR_LEN 10
#define PROFILE_VERSION_POSITION 7
#define SDP_PROFILE_DESC_LENGTH 8
#define HFP_PROFILE_MINOR_VERSION_6 0x06
#define HFP_PROFILE_MINOR_VERSION_7 0x07
#define HFP_PROFILE_MINOR_VERSION_9 0x09
#define PBAP_GOEP_L2CAP_PSM_LEN 0x06
#define PBAP_SUPP_FEA_LEN 0x08

#ifndef SDP_ENABLE_PTS_PBAP
#define SDP_ENABLE_PTS_PBAP "bluetooth.pts.pbap"
#endif

#define PBAP_1_2 0x0102
#define PBAP_1_2_BL_LEN 14

using namespace bluetooth;

/* Used to set PBAP local SDP device record for PBAP 1.2 upgrade */
typedef struct {
  int32_t rfcomm_channel_number;
  int32_t l2cap_psm;
  int32_t profile_version;
  uint32_t supported_features;
  uint32_t supported_repositories;
} tSDP_PSE_LOCAL_RECORD;

static tSDP_PSE_LOCAL_RECORD sdpPseLocalRecord;

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/
static void process_service_search(tCONN_CB* p_ccb, uint16_t trans_num,
                                   uint16_t param_len, uint8_t* p_req,
                                   uint8_t* p_req_end);

static void process_service_attr_req(tCONN_CB* p_ccb, uint16_t trans_num,
                                     uint16_t param_len, uint8_t* p_req,
                                     uint8_t* p_req_end);

static void process_service_search_attr_req(tCONN_CB* p_ccb, uint16_t trans_num,
                                            uint16_t param_len, uint8_t* p_req,
                                            uint8_t* p_req_end);
bool sdp_dynamic_change_hfp_version(const tSDP_ATTRIBUTE* p_attr,
                                    const RawAddress& remote_address);
void hfp_fallback(bool& is_hfp_fallback, const tSDP_ATTRIBUTE* p_attr);

static bool is_device_in_allowlist_for_pbap(RawAddress remote_address,
                                            bool check_for_1_2);

static uint16_t sdp_pbap_pse_dynamic_attributes_len_update(
    tCONN_CB* p_ccb, tSDP_ATTR_SEQ* attr_seq, tSDP_UUID_SEQ* uid_seq);

static const tSDP_RECORD* sdp_upgrade_pse_record(const tSDP_RECORD* p_rec,
                                                 RawAddress remote_address);

/******************************************************************************/
/*                E R R O R   T E X T   S T R I N G S                         */
/*                                                                            */
/* The default is to have no text string, but we allow the strings to be      */
/* configured in target.h if people want them.                                */
/******************************************************************************/
#ifndef SDP_TEXT_BAD_HEADER
#define SDP_TEXT_BAD_HEADER NULL
#endif

#ifndef SDP_TEXT_BAD_PDU
#define SDP_TEXT_BAD_PDU NULL
#endif

#ifndef SDP_TEXT_BAD_UUID_LIST
#define SDP_TEXT_BAD_UUID_LIST NULL
#endif

#ifndef SDP_TEXT_BAD_HANDLE
#define SDP_TEXT_BAD_HANDLE NULL
#endif

#ifndef SDP_TEXT_BAD_ATTR_LIST
#define SDP_TEXT_BAD_ATTR_LIST NULL
#endif

#ifndef SDP_TEXT_BAD_CONT_LEN
#define SDP_TEXT_BAD_CONT_LEN NULL
#endif

#ifndef SDP_TEXT_BAD_CONT_INX
#define SDP_TEXT_BAD_CONT_INX NULL
#endif

#ifndef SDP_TEXT_BAD_MAX_RECORDS_LIST
#define SDP_TEXT_BAD_MAX_RECORDS_LIST NULL
#endif

/*************************************************************************************
**
** Function        sdp_dynamic_change_hfp_version
**
** Description     Checks if UUID is AG_HANDSFREE, attribute id
**                 is Profile descriptor list and remote BD address
**                 matches device Allow list, change hfp version to 1.7
**
** Returns         BOOLEAN
**
+***************************************************************************************/
bool sdp_dynamic_change_hfp_version(const tSDP_ATTRIBUTE* p_attr,
                                    const RawAddress& remote_address) {
  if ((p_attr->id != ATTR_ID_BT_PROFILE_DESC_LIST) ||
      (p_attr->len < SDP_PROFILE_DESC_LENGTH)) {
    return false;
  }
  /* As per current DB implementation UUID is condidered as 16 bit */
  if (((p_attr->value_ptr[3] << SDP_PROFILE_DESC_LENGTH) |
       (p_attr->value_ptr[4])) != UUID_SERVCLASS_HF_HANDSFREE) {
    return false;
  }
  bool is_allowlisted_1_7 =
      interop_match_addr_or_name(INTEROP_HFP_1_7_ALLOWLIST, &remote_address,
                                 &btif_storage_get_remote_device_property);
  bool is_allowlisted_1_9 =
      interop_match_addr_or_name(INTEROP_HFP_1_9_ALLOWLIST, &remote_address,
                                 &btif_storage_get_remote_device_property);
  /* For PTS we should update AG's HFP version as 1.7 */
  if (!(is_allowlisted_1_7) && !(is_allowlisted_1_9) &&
      !(osi_property_get_bool("vendor.bt.pts.certification", false))) {
    return false;
  }
  if (hfp_hal_interface::get_swb_supported() && is_allowlisted_1_9) {
    p_attr->value_ptr[PROFILE_VERSION_POSITION] = HFP_PROFILE_MINOR_VERSION_9;
  } else {
    p_attr->value_ptr[PROFILE_VERSION_POSITION] = HFP_PROFILE_MINOR_VERSION_7;
  }
  log::verbose("SDP Change HFP Version = {} for {}",
               p_attr->value_ptr[PROFILE_VERSION_POSITION],
               ADDRESS_TO_LOGGABLE_CSTR(remote_address));
  return true;
}
/******************************************************************************
 *
 * Function         hfp_fallback
 *
 * Description      Update HFP version back to 1.6
 *
 * Returns          void
 *
 *****************************************************************************/
void hfp_fallback(bool& is_hfp_fallback, const tSDP_ATTRIBUTE* p_attr) {
  /* Update HFP version back to 1.6 */
  p_attr->value_ptr[PROFILE_VERSION_POSITION] = HFP_PROFILE_MINOR_VERSION_6;
  log::verbose("Restore HFP version to 1.6");
  is_hfp_fallback = false;
}

/*******************************************************************************
 *
 * Function         sdp_server_handle_client_req
 *
 * Description      This is the main dispatcher of the SDP server. It is called
 *                  when any data is received from L2CAP, and dispatches the
 *                  request to the appropriate handler.
 *
 * Returns          void
 *
 ******************************************************************************/
void sdp_server_handle_client_req(tCONN_CB* p_ccb, BT_HDR* p_msg) {
  uint8_t* p_req = (uint8_t*)(p_msg + 1) + p_msg->offset;
  uint8_t* p_req_end = p_req + p_msg->len;
  uint8_t pdu_id;
  uint16_t trans_num, param_len;

  /* Start inactivity timer */
  alarm_set_on_mloop(p_ccb->sdp_conn_timer, SDP_INACT_TIMEOUT_MS,
                     sdp_conn_timer_timeout, p_ccb);

  if (p_req + sizeof(pdu_id) + sizeof(trans_num) > p_req_end) {
    trans_num = 0;
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_HEADER);
    return;
  }

  /* The first byte in the message is the pdu type */
  pdu_id = *p_req++;

  /* Extract the transaction number and parameter length */
  BE_STREAM_TO_UINT16(trans_num, p_req);

  if (p_req + sizeof(param_len) > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_HEADER);
    return;
  }

  BE_STREAM_TO_UINT16(param_len, p_req);

  if ((p_req + param_len) != p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_PDU_SIZE,
                            SDP_TEXT_BAD_HEADER);
    return;
  }

  switch (pdu_id) {
    case SDP_PDU_SERVICE_SEARCH_REQ:
      process_service_search(p_ccb, trans_num, param_len, p_req, p_req_end);
      break;

    case SDP_PDU_SERVICE_ATTR_REQ:
      process_service_attr_req(p_ccb, trans_num, param_len, p_req, p_req_end);
      break;

    case SDP_PDU_SERVICE_SEARCH_ATTR_REQ:
      process_service_search_attr_req(p_ccb, trans_num, param_len, p_req,
                                      p_req_end);
      break;

    default:
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                              SDP_TEXT_BAD_PDU);
      log::warn("SDP - server got unknown PDU: 0x{:x}", pdu_id);
      break;
  }
}

/*******************************************************************************
 *
 * Function         process_service_search
 *
 * Description      This function handles a service search request from the
 *                  client. It builds a reply message with info from the
 *                  database, and sends the reply back to the client.
 *
 * Returns          void
 *
 ******************************************************************************/
static void process_service_search(tCONN_CB* p_ccb, uint16_t trans_num,
                                   uint16_t param_len, uint8_t* p_req,
                                   uint8_t* p_req_end) {
  uint16_t max_replies, cur_handles, rem_handles, cont_offset;
  tSDP_UUID_SEQ uid_seq;
  uint8_t *p_rsp, *p_rsp_start, *p_rsp_param_len;
  uint16_t rsp_param_len, num_rsp_handles, xx;
  uint32_t rsp_handles[SDP_MAX_RECORDS] = {0};
  const tSDP_RECORD* p_rec = NULL;
  bool is_cont = false;

  p_req = sdpu_extract_uid_seq(p_req, param_len, &uid_seq);

  if ((!p_req) || (!uid_seq.num_uids)) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_UUID_LIST);
    return;
  }

  /* Get the max replies we can send. Cap it at our max anyways. */
  if (p_req + sizeof(max_replies) + sizeof(uint8_t) > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_MAX_RECORDS_LIST);
    return;
  }
  BE_STREAM_TO_UINT16(max_replies, p_req);

  if (max_replies > SDP_MAX_RECORDS) max_replies = SDP_MAX_RECORDS;

  /* Get a list of handles that match the UUIDs given to us */
  for (num_rsp_handles = 0; num_rsp_handles < max_replies;) {
    p_rec = sdp_db_service_search(p_rec, &uid_seq);

    if (p_rec)
      rsp_handles[num_rsp_handles++] = p_rec->record_handle;
    else
      break;
  }

  /* Check if this is a continuation request */
  if (p_req + 1 > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                            SDP_TEXT_BAD_CONT_LEN);
    return;
  }
  if (*p_req) {
    if (*p_req++ != SDP_CONTINUATION_LEN ||
        (p_req + sizeof(cont_offset) > p_req_end)) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_LEN);
      return;
    }
    BE_STREAM_TO_UINT16(cont_offset, p_req);

    if (cont_offset != p_ccb->cont_offset || num_rsp_handles < cont_offset) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_INX);
      return;
    }

    rem_handles =
        num_rsp_handles - cont_offset; /* extract the remaining handles */
  } else {
    rem_handles = num_rsp_handles;
    cont_offset = 0;
    p_ccb->cont_offset = 0;
  }

  /* Calculate how many handles will fit in one PDU */
  cur_handles =
      (uint16_t)((p_ccb->rem_mtu_size - SDP_MAX_SERVICE_RSPHDR_LEN) / 4);

  if (rem_handles <= cur_handles)
    cur_handles = rem_handles;
  else /* Continuation is set */
  {
    p_ccb->cont_offset += cur_handles;
    is_cont = true;
  }

  /* Get a buffer to use to build the response */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(SDP_DATA_BUF_SIZE);
  p_buf->offset = L2CAP_MIN_OFFSET;
  p_rsp = p_rsp_start = (uint8_t*)(p_buf + 1) + L2CAP_MIN_OFFSET;

  /* Start building a rsponse */
  UINT8_TO_BE_STREAM(p_rsp, SDP_PDU_SERVICE_SEARCH_RSP);
  UINT16_TO_BE_STREAM(p_rsp, trans_num);

  /* Skip the length, we need to add it at the end */
  p_rsp_param_len = p_rsp;
  p_rsp += 2;

  /* Put in total and current number of handles, and handles themselves */
  UINT16_TO_BE_STREAM(p_rsp, num_rsp_handles);
  UINT16_TO_BE_STREAM(p_rsp, cur_handles);

  /*
    log::verbose("SDP Service Rsp: tothdl {}, curhdlr {}, start {}, end {}, cont
    {}", num_rsp_handles, cur_handles, cont_offset, cont_offset + cur_handles-1,
    is_cont); */
  for (xx = cont_offset; xx < cont_offset + cur_handles; xx++)
    UINT32_TO_BE_STREAM(p_rsp, rsp_handles[xx]);

  if (is_cont) {
    UINT8_TO_BE_STREAM(p_rsp, SDP_CONTINUATION_LEN);
    UINT16_TO_BE_STREAM(p_rsp, p_ccb->cont_offset);
  } else
    UINT8_TO_BE_STREAM(p_rsp, 0);

  /* Go back and put the parameter length into the buffer */
  rsp_param_len = p_rsp - p_rsp_param_len - 2;
  UINT16_TO_BE_STREAM(p_rsp_param_len, rsp_param_len);

  /* Set the length of the SDP data in the buffer */
  p_buf->len = p_rsp - p_rsp_start;

  /* Send the buffer through L2CAP */
  L2CA_DataWrite(p_ccb->connection_id, p_buf);
}

/*******************************************************************************
 *
 * Function         process_service_attr_req
 *
 * Description      This function handles an attribute request from the client.
 *                  It builds a reply message with info from the database,
 *                  and sends the reply back to the client.
 *
 * Returns          void
 *
 ******************************************************************************/
static void process_service_attr_req(tCONN_CB* p_ccb, uint16_t trans_num,
                                     uint16_t param_len, uint8_t* p_req,
                                     uint8_t* p_req_end) {
  uint16_t max_list_len, len_to_send, cont_offset;
  int16_t rem_len;
  tSDP_ATTR_SEQ attr_seq, attr_seq_sav;
  uint8_t *p_rsp, *p_rsp_start, *p_rsp_param_len;
  uint16_t rsp_param_len, xx;
  uint32_t rec_handle;
  const tSDP_RECORD* p_rec;
  const tSDP_ATTRIBUTE* p_attr;
  bool is_cont = false;
  bool is_hfp_fallback = false;
  uint16_t attr_len;

  if (p_req + sizeof(rec_handle) + sizeof(max_list_len) > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_SERV_REC_HDL,
                            SDP_TEXT_BAD_HANDLE);
    return;
  }

  /* Extract the record handle */
  BE_STREAM_TO_UINT32(rec_handle, p_req);
  param_len -= sizeof(rec_handle);

  /* Get the max list length we can send. Cap it at MTU size minus overhead */
  BE_STREAM_TO_UINT16(max_list_len, p_req);
  param_len -= sizeof(max_list_len);

  if (max_list_len > (p_ccb->rem_mtu_size - SDP_MAX_ATTR_RSPHDR_LEN))
    max_list_len = p_ccb->rem_mtu_size - SDP_MAX_ATTR_RSPHDR_LEN;

  p_req = sdpu_extract_attr_seq(p_req, param_len, &attr_seq);

  if ((!p_req) || (!attr_seq.num_attr) ||
      (p_req + sizeof(uint8_t) > p_req_end)) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_ATTR_LIST);
    return;
  }

  memcpy(&attr_seq_sav, &attr_seq, sizeof(tSDP_ATTR_SEQ));

  /* Find a record with the record handle */
  p_rec = sdp_db_find_record(rec_handle);
  if (!p_rec) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_SERV_REC_HDL,
                            SDP_TEXT_BAD_HANDLE);
    return;
  }

  if (max_list_len < 4) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_ILLEGAL_PARAMETER, NULL);
    return;
  }

  if (bluetooth::common::init_flags::
          pbap_pse_dynamic_version_upgrade_is_enabled()) {
    p_rec = sdp_upgrade_pse_record(p_rec, p_ccb->device_address);
  } else {
    log::warn("PBAP PSE dynamic version upgrade is not enabled");
  }

  /* Free and reallocate buffer */
  osi_free(p_ccb->rsp_list);
  p_ccb->rsp_list = (uint8_t*)osi_malloc(max_list_len);

  /* Check if this is a continuation request */
  if (p_req + 1 > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                            SDP_TEXT_BAD_CONT_LEN);
    return;
  }
  if (*p_req) {
    if (*p_req++ != SDP_CONTINUATION_LEN ||
        (p_req + sizeof(cont_offset) > p_req_end)) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_LEN);
      return;
    }
    BE_STREAM_TO_UINT16(cont_offset, p_req);

    if (cont_offset != p_ccb->cont_offset) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_INX);
      return;
    }
    is_cont = true;

    /* Initialise for continuation response */
    p_rsp = &p_ccb->rsp_list[0];
    attr_seq.attr_entry[p_ccb->cont_info.next_attr_index].start =
        p_ccb->cont_info.next_attr_start_id;
  } else {
    p_ccb->cont_offset = 0;
    p_rsp = &p_ccb->rsp_list[3]; /* Leave space for data elem descr */

    /* Reset continuation parameters in p_ccb */
    p_ccb->cont_info.prev_sdp_rec = NULL;
    p_ccb->cont_info.next_attr_index = 0;
    p_ccb->cont_info.attr_offset = 0;
  }

  bool is_service_avrc_target = false;
  const tSDP_ATTRIBUTE* p_attr_service_id;
  const tSDP_ATTRIBUTE* p_attr_profile_desc_list_id;
  uint16_t avrc_sdp_version = 0;
  p_attr_service_id = sdp_db_find_attr_in_rec(
      p_rec, ATTR_ID_SERVICE_CLASS_ID_LIST, ATTR_ID_SERVICE_CLASS_ID_LIST);
  p_attr_profile_desc_list_id = sdp_db_find_attr_in_rec(
      p_rec, ATTR_ID_BT_PROFILE_DESC_LIST, ATTR_ID_BT_PROFILE_DESC_LIST);
  if (p_attr_service_id) {
    is_service_avrc_target = sdpu_is_service_id_avrc_target(p_attr_service_id);
  }
  /* Search for attributes that match the list given to us */
  for (xx = p_ccb->cont_info.next_attr_index; xx < attr_seq.num_attr; xx++) {
    p_attr = sdp_db_find_attr_in_rec(p_rec, attr_seq.attr_entry[xx].start,
                                     attr_seq.attr_entry[xx].end);
    if (p_attr) {
      if (is_service_avrc_target) {
        sdpu_set_avrc_target_version(p_attr, &(p_ccb->device_address));
        if (p_attr->id == ATTR_ID_SUPPORTED_FEATURES &&
            bluetooth::common::init_flags::
                dynamic_avrcp_version_enhancement_is_enabled()) {
          avrc_sdp_version = sdpu_is_avrcp_profile_description_list(
              p_attr_profile_desc_list_id);
          log::error("avrc_sdp_version in SDP records {:x}", avrc_sdp_version);
          sdpu_set_avrc_target_features(p_attr, &(p_ccb->device_address),
                                        avrc_sdp_version);
        }
      }
      if (bluetooth::common::init_flags::hfp_dynamic_version_is_enabled()) {
        is_hfp_fallback =
            sdp_dynamic_change_hfp_version(p_attr, p_ccb->device_address);
      }
      /* Check if attribute fits. Assume 3-byte value type/length */
      rem_len = max_list_len - (int16_t)(p_rsp - &p_ccb->rsp_list[0]);

      /* just in case */
      if (rem_len <= 0) {
        p_ccb->cont_info.next_attr_index = xx;
        p_ccb->cont_info.next_attr_start_id = p_attr->id;
        break;
      }

      attr_len = sdpu_get_attrib_entry_len(p_attr);
      /* if there is a partial attribute pending to be sent */
      if (p_ccb->cont_info.attr_offset) {
        if (attr_len < p_ccb->cont_info.attr_offset) {
          log::error("offset is bigger than attribute length");
          sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                                  SDP_TEXT_BAD_CONT_LEN);
          return;
        }
        p_rsp = sdpu_build_partial_attrib_entry(p_rsp, p_attr, rem_len,
                                                &p_ccb->cont_info.attr_offset);

        /* If the partial attrib could not been fully added yet */
        if (p_ccb->cont_info.attr_offset != attr_len)
          break;
        else /* If the partial attrib has been added in full by now */
          p_ccb->cont_info.attr_offset = 0; /* reset attr_offset */
      } else if (rem_len <
                 attr_len) /* Not enough space for attr... so add partially */
      {
        if (attr_len >= SDP_MAX_ATTR_LEN) {
          log::error("SDP attr too big: max_list_len={},attr_len={}",
                     max_list_len, attr_len);
          sdpu_build_n_send_error(p_ccb, trans_num, SDP_NO_RESOURCES, NULL);
          return;
        }

        /* add the partial attribute if possible */
        p_rsp = sdpu_build_partial_attrib_entry(
            p_rsp, p_attr, (uint16_t)rem_len, &p_ccb->cont_info.attr_offset);

        p_ccb->cont_info.next_attr_index = xx;
        p_ccb->cont_info.next_attr_start_id = p_attr->id;
        break;
      } else /* build the whole attribute */
        p_rsp = sdpu_build_attrib_entry(p_rsp, p_attr);

      /* If doing a range, stick with this one till no more attributes found */
      if (attr_seq.attr_entry[xx].start != attr_seq.attr_entry[xx].end) {
        /* Update for next time through */
        attr_seq.attr_entry[xx].start = p_attr->id + 1;

        xx--;
      }
      if (is_hfp_fallback) {
        hfp_fallback(is_hfp_fallback, p_attr);
      }
    }
  }
  if (is_hfp_fallback) {
    hfp_fallback(is_hfp_fallback, p_attr);
  }
  /* If all the attributes have been accomodated in p_rsp,
     reset next_attr_index */
  if (xx == attr_seq.num_attr) p_ccb->cont_info.next_attr_index = 0;

  len_to_send = (uint16_t)(p_rsp - &p_ccb->rsp_list[0]);
  cont_offset = 0;

  if (!is_cont) {
    p_ccb->list_len = sdpu_get_attrib_seq_len(p_rec, &attr_seq_sav) + 3;
    /* Put in the sequence header (2 or 3 bytes) */
    if (p_ccb->list_len > 255) {
      p_ccb->rsp_list[0] =
          (uint8_t)((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_WORD);
      p_ccb->rsp_list[1] = (uint8_t)((p_ccb->list_len - 3) >> 8);
      p_ccb->rsp_list[2] = (uint8_t)(p_ccb->list_len - 3);
    } else {
      cont_offset = 1;

      p_ccb->rsp_list[1] =
          (uint8_t)((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_BYTE);
      p_ccb->rsp_list[2] = (uint8_t)(p_ccb->list_len - 3);

      p_ccb->list_len--;
      len_to_send--;
    }
  }

  /* Get a buffer to use to build the response */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(SDP_DATA_BUF_SIZE);
  p_buf->offset = L2CAP_MIN_OFFSET;
  p_rsp = p_rsp_start = (uint8_t*)(p_buf + 1) + L2CAP_MIN_OFFSET;

  /* Start building a rsponse */
  UINT8_TO_BE_STREAM(p_rsp, SDP_PDU_SERVICE_ATTR_RSP);
  UINT16_TO_BE_STREAM(p_rsp, trans_num);

  /* Skip the parameter length, add it when we know the length */
  p_rsp_param_len = p_rsp;
  p_rsp += 2;

  UINT16_TO_BE_STREAM(p_rsp, len_to_send);

  memcpy(p_rsp, &p_ccb->rsp_list[cont_offset], len_to_send);
  p_rsp += len_to_send;

  p_ccb->cont_offset += len_to_send;

  /* If anything left to send, continuation needed */
  if (p_ccb->cont_offset < p_ccb->list_len) {
    is_cont = true;

    UINT8_TO_BE_STREAM(p_rsp, SDP_CONTINUATION_LEN);
    UINT16_TO_BE_STREAM(p_rsp, p_ccb->cont_offset);
  } else
    UINT8_TO_BE_STREAM(p_rsp, 0);

  /* Go back and put the parameter length into the buffer */
  rsp_param_len = p_rsp - p_rsp_param_len - 2;
  UINT16_TO_BE_STREAM(p_rsp_param_len, rsp_param_len);

  /* Set the length of the SDP data in the buffer */
  p_buf->len = p_rsp - p_rsp_start;

  /* Send the buffer through L2CAP */
  L2CA_DataWrite(p_ccb->connection_id, p_buf);
}

/*******************************************************************************
 *
 * Function         process_service_search_attr_req
 *
 * Description      This function handles a combined service search and
 *                  attribute read request from the client. It builds a reply
 *                  message with info from the database, and sends the reply
 *                  back to the client.
 *
 * Returns          void
 *
 ******************************************************************************/
static void process_service_search_attr_req(tCONN_CB* p_ccb, uint16_t trans_num,
                                            uint16_t param_len, uint8_t* p_req,
                                            uint8_t* p_req_end) {
  uint16_t max_list_len;
  int16_t rem_len;
  uint16_t len_to_send, cont_offset;
  tSDP_UUID_SEQ uid_seq;
  uint8_t *p_rsp, *p_rsp_start, *p_rsp_param_len;
  uint16_t rsp_param_len, xx;
  const tSDP_RECORD* p_rec;
  tSDP_RECORD* p_prev_rec;
  tSDP_ATTR_SEQ attr_seq, attr_seq_sav;
  const tSDP_ATTRIBUTE* p_attr;
  bool maxxed_out = false, is_cont = false;
  uint8_t* p_seq_start;
  bool is_hfp_fallback = false;
  uint16_t seq_len, attr_len;

  /* Extract the UUID sequence to search for */
  p_req = sdpu_extract_uid_seq(p_req, param_len, &uid_seq);

  if ((!p_req) || (!uid_seq.num_uids) ||
      (p_req + sizeof(uint16_t) > p_req_end)) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_UUID_LIST);
    return;
  }

  /* Get the max list length we can send. Cap it at our max list length. */
  BE_STREAM_TO_UINT16(max_list_len, p_req);

  if (max_list_len > (p_ccb->rem_mtu_size - SDP_MAX_SERVATTR_RSPHDR_LEN))
    max_list_len = p_ccb->rem_mtu_size - SDP_MAX_SERVATTR_RSPHDR_LEN;

  param_len = static_cast<uint16_t>(p_req_end - p_req);
  p_req = sdpu_extract_attr_seq(p_req, param_len, &attr_seq);

  if ((!p_req) || (!attr_seq.num_attr) ||
      (p_req + sizeof(uint8_t) > p_req_end)) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_REQ_SYNTAX,
                            SDP_TEXT_BAD_ATTR_LIST);
    return;
  }

  memcpy(&attr_seq_sav, &attr_seq, sizeof(tSDP_ATTR_SEQ));

  if (max_list_len < 4) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_ILLEGAL_PARAMETER, NULL);
    return;
  }

  /* Free and reallocate buffer */
  osi_free(p_ccb->rsp_list);
  p_ccb->rsp_list = (uint8_t*)osi_malloc(max_list_len);

  /* Check if this is a continuation request */
  if (p_req + 1 > p_req_end) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                            SDP_TEXT_BAD_CONT_LEN);
    return;
  }
  if (*p_req) {
    if (*p_req++ != SDP_CONTINUATION_LEN ||
        (p_req + sizeof(uint16_t) > p_req_end)) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_LEN);
      return;
    }
    BE_STREAM_TO_UINT16(cont_offset, p_req);

    if (cont_offset != p_ccb->cont_offset) {
      sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                              SDP_TEXT_BAD_CONT_INX);
      return;
    }
    is_cont = true;

    /* Initialise for continuation response */
    p_rsp = &p_ccb->rsp_list[0];
    attr_seq.attr_entry[p_ccb->cont_info.next_attr_index].start =
        p_ccb->cont_info.next_attr_start_id;
  } else {
    p_ccb->cont_offset = 0;
    p_rsp = &p_ccb->rsp_list[3]; /* Leave space for data elem descr */

    /* Reset continuation parameters in p_ccb */
    p_ccb->cont_info.prev_sdp_rec = NULL;
    p_ccb->cont_info.next_attr_index = 0;
    p_ccb->cont_info.last_attr_seq_desc_sent = false;
    p_ccb->cont_info.attr_offset = 0;
  }

  /* Get a list of handles that match the UUIDs given to us */
  for (p_rec = sdp_db_service_search(p_ccb->cont_info.prev_sdp_rec, &uid_seq);
       p_rec; p_rec = sdp_db_service_search(p_rec, &uid_seq)) {
    /* Store the actual record pointer which would be reused later */
    p_prev_rec = (tSDP_RECORD*)p_rec;
    if (bluetooth::common::init_flags::
            pbap_pse_dynamic_version_upgrade_is_enabled()) {
      p_rec = sdp_upgrade_pse_record(p_rec, p_ccb->device_address);
    } else {
      log::warn("PBAP PSE dynamic version upgrade is not enabled");
    }
    /* Allow space for attribute sequence type and length */
    p_seq_start = p_rsp;
    if (!p_ccb->cont_info.last_attr_seq_desc_sent) {
      /* See if there is enough room to include a new service in the current
       * response */
      rem_len = max_list_len - (int16_t)(p_rsp - &p_ccb->rsp_list[0]);
      if (rem_len < 3) {
        /* Not enough room. Update continuation info for next response */
        p_ccb->cont_info.next_attr_index = 0;
        p_ccb->cont_info.next_attr_start_id = attr_seq.attr_entry[0].start;
        break;
      }
      p_rsp += 3;
    }

    bool is_service_avrc_target = false;
    const tSDP_ATTRIBUTE* p_attr_service_id;
    const tSDP_ATTRIBUTE* p_attr_profile_desc_list_id;
    uint16_t avrc_sdp_version = 0;
    p_attr_service_id = sdp_db_find_attr_in_rec(
        p_rec, ATTR_ID_SERVICE_CLASS_ID_LIST, ATTR_ID_SERVICE_CLASS_ID_LIST);
    p_attr_profile_desc_list_id = sdp_db_find_attr_in_rec(
        p_rec, ATTR_ID_BT_PROFILE_DESC_LIST, ATTR_ID_BT_PROFILE_DESC_LIST);
    if (p_attr_service_id) {
      is_service_avrc_target =
          sdpu_is_service_id_avrc_target(p_attr_service_id);
    }
    /* Get a list of handles that match the UUIDs given to us */
    for (xx = p_ccb->cont_info.next_attr_index; xx < attr_seq.num_attr; xx++) {
      p_attr = sdp_db_find_attr_in_rec(p_rec, attr_seq.attr_entry[xx].start,
                                       attr_seq.attr_entry[xx].end);

      if (p_attr) {
        if (is_service_avrc_target) {
          sdpu_set_avrc_target_version(p_attr, &(p_ccb->device_address));
          if (p_attr->id == ATTR_ID_SUPPORTED_FEATURES &&
              bluetooth::common::init_flags::
                  dynamic_avrcp_version_enhancement_is_enabled() &&
                  p_attr_profile_desc_list_id != nullptr) {
            avrc_sdp_version = sdpu_is_avrcp_profile_description_list(
                p_attr_profile_desc_list_id);
            log::error("avrc_sdp_version in SDP records {:x}",
                       avrc_sdp_version);
            sdpu_set_avrc_target_features(p_attr, &(p_ccb->device_address),
                                          avrc_sdp_version);
          }
        }
        if (bluetooth::common::init_flags::hfp_dynamic_version_is_enabled()) {
          is_hfp_fallback =
              sdp_dynamic_change_hfp_version(p_attr, p_ccb->device_address);
        }
        /* Check if attribute fits. Assume 3-byte value type/length */
        rem_len = max_list_len - (int16_t)(p_rsp - &p_ccb->rsp_list[0]);

        /* just in case */
        if (rem_len <= 0) {
          p_ccb->cont_info.next_attr_index = xx;
          p_ccb->cont_info.next_attr_start_id = p_attr->id;
          maxxed_out = true;
          break;
        }

        attr_len = sdpu_get_attrib_entry_len(p_attr);
        /* if there is a partial attribute pending to be sent */
        if (p_ccb->cont_info.attr_offset) {
          if (attr_len < p_ccb->cont_info.attr_offset) {
            log::error("offset is bigger than attribute length");
            sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE,
                                    SDP_TEXT_BAD_CONT_LEN);
            return;
          }
          p_rsp = sdpu_build_partial_attrib_entry(
              p_rsp, p_attr, rem_len, &p_ccb->cont_info.attr_offset);

          /* If the partial attrib could not been fully added yet */
          if (p_ccb->cont_info.attr_offset != attr_len) {
            maxxed_out = true;
            break;
          } else /* If the partial attrib has been added in full by now */
            p_ccb->cont_info.attr_offset = 0; /* reset attr_offset */
        } else if (rem_len <
                   attr_len) /* Not enough space for attr... so add partially */
        {
          if (attr_len >= SDP_MAX_ATTR_LEN) {
            log::error("SDP attr too big: max_list_len={},attr_len={}",
                       max_list_len, attr_len);
            sdpu_build_n_send_error(p_ccb, trans_num, SDP_NO_RESOURCES, NULL);
            return;
          }

          /* add the partial attribute if possible */
          p_rsp = sdpu_build_partial_attrib_entry(
              p_rsp, p_attr, (uint16_t)rem_len, &p_ccb->cont_info.attr_offset);

          p_ccb->cont_info.next_attr_index = xx;
          p_ccb->cont_info.next_attr_start_id = p_attr->id;
          maxxed_out = true;
          break;
        } else /* build the whole attribute */
          p_rsp = sdpu_build_attrib_entry(p_rsp, p_attr);

        /* If doing a range, stick with this one till no more attributes found
         */
        if (attr_seq.attr_entry[xx].start != attr_seq.attr_entry[xx].end) {
          /* Update for next time through */
          attr_seq.attr_entry[xx].start = p_attr->id + 1;

          xx--;
        }
        if (is_hfp_fallback) {
          hfp_fallback(is_hfp_fallback, p_attr);
        }
      }
    }
    if (is_hfp_fallback) {
      hfp_fallback(is_hfp_fallback, p_attr);
    }

    /* Go back and put the type and length into the buffer */
    if (!p_ccb->cont_info.last_attr_seq_desc_sent) {
      seq_len = sdpu_get_attrib_seq_len(p_rec, &attr_seq_sav);
      if (seq_len != 0) {
        UINT8_TO_BE_STREAM(p_seq_start,
                           (DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_WORD);
        UINT16_TO_BE_STREAM(p_seq_start, seq_len);

        if (maxxed_out) p_ccb->cont_info.last_attr_seq_desc_sent = true;
      } else
        p_rsp = p_seq_start;
    }

    if (maxxed_out) break;

    /* Restore the attr_seq to look for in the next sdp record */
    memcpy(&attr_seq, &attr_seq_sav, sizeof(tSDP_ATTR_SEQ));

    /* Reset the next attr index */
    p_ccb->cont_info.next_attr_index = 0;
    /* restore the record pointer.*/
    p_rec = p_prev_rec;
    p_ccb->cont_info.prev_sdp_rec = p_rec;
    p_ccb->cont_info.last_attr_seq_desc_sent = false;
  }

  /* response length */
  len_to_send = (uint16_t)(p_rsp - &p_ccb->rsp_list[0]);
  cont_offset = 0;

  // The current SDP server design has a critical flaw where it can run into
  // an infinite request/response loop with the client. Here's the scenario:
  // - client makes SDP request
  // - server returns the first fragment of the response with a continuation
  //   token
  // - an SDP record is deleted from the server
  // - client issues another request with previous continuation token
  // - server has nothing to send back because the record is unavailable but
  //   in the first fragment, it had specified more response bytes than are
  //   now available
  // - server sends back no additional response bytes and returns the same
  //   continuation token
  // - client issues another request with the continuation token, and the
  //   process repeats
  //
  // We work around this design flaw here by checking if we will make forward
  // progress (i.e. we will send > 0 response bytes) on a continued request.
  // If not, we must have run into the above situation and we tell the peer an
  // error occurred.
  //
  // TODO(sharvil): rewrite SDP server.
  if (is_cont && len_to_send == 0) {
    sdpu_build_n_send_error(p_ccb, trans_num, SDP_INVALID_CONT_STATE, NULL);
    return;
  }

  /* If first response, insert sequence header */
  if (!is_cont) {
    /* Get the total list length for requested uid and attribute sequence */
    p_ccb->list_len = sdpu_get_list_len(&uid_seq, &attr_seq_sav) + 3;

    /* Get the length of denylisted attributes to be updated if device is
     * denylisted */
    if (bluetooth::common::init_flags::
            pbap_pse_dynamic_version_upgrade_is_enabled()) {
      p_ccb->pse_dynamic_attributes_len =
          sdp_pbap_pse_dynamic_attributes_len_update(p_ccb, &attr_seq_sav,
                                                     &uid_seq);
    } else {
      log::warn("PBAP PSE dynamic version upgrade is not enabled");
      p_ccb->pse_dynamic_attributes_len = 0;
    }

    log::verbose("p_ccb->list_len = {} pse_dynamic_attributes_len = {}",
                 p_ccb->list_len, p_ccb->pse_dynamic_attributes_len);

    /* Put in the sequence header (2 or 3 bytes) */
    if (p_ccb->list_len > 255) {
      p_ccb->rsp_list[0] =
          (uint8_t)((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_WORD);
      p_ccb->rsp_list[1] =
          (uint8_t)((p_ccb->list_len - 3 + p_ccb->pse_dynamic_attributes_len) >>
                    8);
      p_ccb->rsp_list[2] =
          (uint8_t)(p_ccb->list_len - 3 + p_ccb->pse_dynamic_attributes_len);
    } else {
      cont_offset = 1;

      p_ccb->rsp_list[1] =
          (uint8_t)((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_BYTE);
      p_ccb->rsp_list[2] =
          (uint8_t)(p_ccb->list_len - 3 + p_ccb->pse_dynamic_attributes_len);

      p_ccb->list_len--;
      len_to_send--;
    }
  }

  /* Get a buffer to use to build the response */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(SDP_DATA_BUF_SIZE);
  p_buf->offset = L2CAP_MIN_OFFSET;
  p_rsp = p_rsp_start = (uint8_t*)(p_buf + 1) + L2CAP_MIN_OFFSET;

  /* Start building a rsponse */
  UINT8_TO_BE_STREAM(p_rsp, SDP_PDU_SERVICE_SEARCH_ATTR_RSP);
  UINT16_TO_BE_STREAM(p_rsp, trans_num);

  /* Skip the parameter length, add it when we know the length */
  p_rsp_param_len = p_rsp;
  p_rsp += 2;

  /* Stream the list length to send */
  UINT16_TO_BE_STREAM(p_rsp, len_to_send);

  /* copy from rsp_list to the actual buffer to be sent */
  memcpy(p_rsp, &p_ccb->rsp_list[cont_offset], len_to_send);
  p_rsp += len_to_send;

  p_ccb->cont_offset += len_to_send;

  log::verbose(
      "p_ccb->pse_dynamic_attributes_len {}, cont_offset = {}, p_ccb->list_len "
      "= {}",
      p_ccb->pse_dynamic_attributes_len, p_ccb->cont_offset,
      p_ccb->list_len + p_ccb->pse_dynamic_attributes_len);
  /* If anything left to send, continuation needed */
  if (p_ccb->cont_offset <
      (p_ccb->list_len + p_ccb->pse_dynamic_attributes_len)) {
    is_cont = true;
    UINT8_TO_BE_STREAM(p_rsp, SDP_CONTINUATION_LEN);
    UINT16_TO_BE_STREAM(p_rsp, p_ccb->cont_offset);
  } else {
    UINT8_TO_BE_STREAM(p_rsp, 0);
    if (p_ccb->pse_dynamic_attributes_len) {
      p_ccb->pse_dynamic_attributes_len = 0;
    }
  }

  /* Go back and put the parameter length into the buffer */
  rsp_param_len = p_rsp - p_rsp_param_len - 2;
  UINT16_TO_BE_STREAM(p_rsp_param_len, rsp_param_len);

  /* Set the length of the SDP data in the buffer */
  p_buf->len = p_rsp - p_rsp_start;

  /* Send the buffer through L2CAP */
  L2CA_DataWrite(p_ccb->connection_id, p_buf);
}

/*************************************************************************************
**
** Function        is_device_in_allowlist_for_pbap
**
** Description     Checks if given PBAP device is in allowlist for PBAP PSE.
**
** Returns         BOOLEAN
**
***************************************************************************************/
static bool is_device_in_allowlist_for_pbap(RawAddress remote_address,
                                            bool check_for_1_2) {
  if (!check_for_1_2 &&
      interop_match_addr_or_name(INTEROP_ADV_PBAP_VER_1_1, &remote_address,
                                 &btif_storage_get_remote_device_property)) {
    log::verbose("device is in allowlist for pbap version < 1.2");
    return true;
  }
  if (check_for_1_2) {
    if (btm_sec_is_a_bonded_dev(remote_address)) {
      if (interop_match_addr_or_name(
              INTEROP_ADV_PBAP_VER_1_2, &remote_address,
              &btif_storage_get_remote_device_property)) {
        log::verbose("device is in allowlist for pbap version 1.2");
        return true;
      }
    } else {
      const char* p_name = BTM_SecReadDevName(remote_address);
      if ((p_name != NULL) &&
          interop_match_name(INTEROP_ADV_PBAP_VER_1_2, p_name)) {
        log::verbose(
            "device is not paired & in allowlist for pbap version 1.2");
        return true;
      }
    }
  }
  return false;
}

/*************************************************************************************
**
** Function        sdp_pbap_pse_dynamic_attributes_len_update
**
** Description      length of the attributes need to be added in final sdp
*response len
**
** Returns         returns the length of denylisted attributes.
**
***************************************************************************************/
static uint16_t sdp_pbap_pse_dynamic_attributes_len_update(
    tCONN_CB* p_ccb, tSDP_ATTR_SEQ* attr_seq, tSDP_UUID_SEQ* uid_seq) {
  if (!p_ccb || !attr_seq || !uid_seq) return 0;
  const tSDP_RECORD* p_rec;

  p_ccb->pse_dynamic_attributes_len = 0;

  // Check to validate if 1.2 record is getting sent
  bool is_pbap_102_supported =
      btif_storage_is_pce_version_102(p_ccb->device_address);
  bool is_pbap_101_allowlisted =
      is_device_in_allowlist_for_pbap(p_ccb->device_address, false);
  bool is_pbap_102_allowlisted =
      is_device_in_allowlist_for_pbap(p_ccb->device_address, true);
  bool running_pts = osi_property_get_bool(SDP_ENABLE_PTS_PBAP, false);

  log::verbose(
      "remote BD Addr : {} is_pbap_102_supported = {} is_pbap_101_allowlisted "
      "= {} is_pbap_102_allowlisted = {} running_pts = {}",
      ADDRESS_TO_LOGGABLE_CSTR(p_ccb->device_address), is_pbap_102_supported,
      is_pbap_101_allowlisted, is_pbap_102_allowlisted, running_pts);

  if (is_pbap_101_allowlisted ||
      (!is_pbap_102_supported && !is_pbap_102_allowlisted && !running_pts)) {
    // Send Length without any update
    return p_ccb->pse_dynamic_attributes_len;
  }

  int xx;
  tSDP_ATTRIBUTE attr;
  for (p_rec = (tSDP_RECORD*)sdp_db_service_search(NULL, uid_seq); p_rec;
       p_rec = (tSDP_RECORD*)sdp_db_service_search(p_rec, uid_seq)) {
    attr = p_rec->attribute[1];
    if ((attr.id == ATTR_ID_SERVICE_CLASS_ID_LIST) &&
        (((attr.value_ptr[1] << 8) | (attr.value_ptr[2])) ==
         UUID_SERVCLASS_PBAP_PSE)) {
      // PBAP PSE Record
      p_rec = sdp_upgrade_pse_record(p_rec, p_ccb->device_address);
      log::verbose("response has PBAP PSE record for allowlist device");

      int att_index;
      bool l2cap_psm_len_included = false, supp_attr_len_included = false;
      for (xx = p_ccb->cont_info.next_attr_index; xx < attr_seq->num_attr;
           xx++) {
        log::verbose(
            "xx = {} attr_seq->num_attr = {}, attr_seq->attr_entry[xx].start = "
            "{} , attr_seq->attr_entry[xx].end = {}",
            xx, attr_seq->num_attr, attr_seq->attr_entry[xx].start,
            attr_seq->attr_entry[xx].end);

        for (att_index = 0; att_index < p_rec->num_attributes; att_index++) {
          tSDP_ATTRIBUTE cur_attr = p_rec->attribute[att_index];
          if (cur_attr.id == ATTR_ID_GOEP_L2CAP_PSM &&
              !l2cap_psm_len_included &&
              cur_attr.id >= attr_seq->attr_entry[xx].start &&
              cur_attr.id <= attr_seq->attr_entry[xx].end) {
            l2cap_psm_len_included = true;
            p_ccb->pse_dynamic_attributes_len += PBAP_GOEP_L2CAP_PSM_LEN;
            log::error(
                "ATTR_ID_GOEP_L2CAP_PSM requested, need to change length by {}",
                p_ccb->pse_dynamic_attributes_len);
          } else if (cur_attr.id == ATTR_ID_PBAP_SUPPORTED_FEATURES &&
                     !supp_attr_len_included &&
                     cur_attr.id >= attr_seq->attr_entry[xx].start &&
                     cur_attr.id <= attr_seq->attr_entry[xx].end) {
            supp_attr_len_included = true;
            p_ccb->pse_dynamic_attributes_len += PBAP_SUPP_FEA_LEN;
            log::verbose(
                "ATTR_ID_PBAP_SUPPORTED_FEATURES requested, need to change "
                "length by {}",
                p_ccb->pse_dynamic_attributes_len);
          }
        }
        if (p_ccb->pse_dynamic_attributes_len == PBAP_1_2_BL_LEN) break;
      }
      break;
    }
  }
  log::verbose("pse_dynamic_attributes_len = {}",
               p_ccb->pse_dynamic_attributes_len);
  return p_ccb->pse_dynamic_attributes_len;
}

/*************************************************************************************
**
** Function        sdp_upgrade_pbap_pse_record
**
** Description     updates pbap record to pbap 1.2 record if remote supports
*pbap 1.2
**
** Returns         the address of updated record
**
***************************************************************************************/
static const tSDP_RECORD* sdp_upgrade_pse_record(const tSDP_RECORD* p_rec,
                                                 RawAddress remote_address) {
  static bool is_pbap_102_supported = FALSE;
  tSDP_ATTRIBUTE attr = p_rec->attribute[1];
  if (!((attr.id == ATTR_ID_SERVICE_CLASS_ID_LIST) &&
        (((attr.value_ptr[1] << 8) | (attr.value_ptr[2])) ==
         UUID_SERVCLASS_PBAP_PSE))) {
    // Not a PBAP PSE Record
    return p_rec;
  }

  /* Check if remote supports PBAP 1.2 */
  is_pbap_102_supported = btif_storage_is_pce_version_102(remote_address);
  bool is_pbap_101_allowlisted =
      is_device_in_allowlist_for_pbap(remote_address, false);
  bool is_pbap_102_allowlisted =
      is_device_in_allowlist_for_pbap(remote_address, true);
  bool running_pts = osi_property_get_bool(SDP_ENABLE_PTS_PBAP, false);

  log::verbose(
      "remote BD Addr : {} is_pbap_102_supported : {} is_pbap_101_allowlisted "
      "= {} is_pbap_102_allowlisted = {} running_pts = {}",
      ADDRESS_TO_LOGGABLE_CSTR(remote_address), is_pbap_102_supported,
      is_pbap_101_allowlisted, is_pbap_102_allowlisted, running_pts);

  if (is_pbap_101_allowlisted ||
      (!is_pbap_102_supported && !is_pbap_102_allowlisted && !running_pts)) {
    // Send 1.1 SDP Record
    return p_rec;
  }

  static tSDP_RECORD pbap_102_sdp_rec = {};
  const tSDP_ATTRIBUTE* p_attr = &p_rec->attribute[0];
  uint8_t temp[4], j;
  uint8_t* p_temp = temp;
  bool status = true;

  /* Copying contents of the PBAP 1.1 PSE record to a new 1.2 record */
  for (j = 0; j < p_rec->num_attributes; j++, p_attr++) {
    SDP_AddAttributeToRecord(&pbap_102_sdp_rec, p_attr->id, p_attr->type,
                             p_attr->len, p_attr->value_ptr);
  }

  /* Add supported repositories 1 byte */
  status &= SDP_AddAttributeToRecord(
      &pbap_102_sdp_rec, ATTR_ID_SUPPORTED_REPOSITORIES, UINT_DESC_TYPE,
      (uint32_t)1, (uint8_t*)&sdpPseLocalRecord.supported_repositories);

  /* Add in the Bluetooth Profile Descriptor List */
  status &= SDP_AddProfileDescriptorListToRecord(
      &pbap_102_sdp_rec, UUID_SERVCLASS_PHONE_ACCESS,
      sdpPseLocalRecord.profile_version);

  /* Add PBAP 1.2 supported features 4 */
  UINT32_TO_BE_STREAM(p_temp, sdpPseLocalRecord.supported_features);
  status &= SDP_AddAttributeToRecord(&pbap_102_sdp_rec,
                                     ATTR_ID_PBAP_SUPPORTED_FEATURES,
                                     UINT_DESC_TYPE, (uint32_t)4, temp);

  /* Add the L2CAP PSM */
  p_temp = temp;  // The macro modifies p_temp, hence rewind.
  UINT16_TO_BE_STREAM(p_temp, sdpPseLocalRecord.l2cap_psm);
  status &= SDP_AddAttributeToRecord(&pbap_102_sdp_rec, ATTR_ID_GOEP_L2CAP_PSM,
                                     UINT_DESC_TYPE, (uint32_t)2, temp);

  if (!status) {
    log::error("FAILED");
    return p_rec;
  }
  return &pbap_102_sdp_rec;
}

/*************************************************************************************
**
** Function        update_pce_entry_to_interop_database
**
** Description     Update PCE 1.2 entry to dynamic interop database
**
***************************************************************************************/
void update_pce_entry_to_interop_database(RawAddress remote_addr) {
  if (!interop_match_addr_or_name(INTEROP_ADV_PBAP_VER_1_2, &remote_addr,
                                  &btif_storage_get_remote_device_property)) {
    interop_database_add_addr(INTEROP_ADV_PBAP_VER_1_2, &remote_addr, 3);
    log::verbose("device: {} is added into interop list",
                 ADDRESS_TO_LOGGABLE_CSTR(remote_addr));
  } else {
    log::warn("device: {} is already found on interop list",
              ADDRESS_TO_LOGGABLE_CSTR(remote_addr));
  }
}

/*************************************************************************************
**
** Function        is_sdp_pbap_pce_disabled
**
** Description     Checks if given PBAP record is for PBAP PSE and SDP
*denylisted
**
** Returns         BOOLEAN
**
***************************************************************************************/
bool is_sdp_pbap_pce_disabled(RawAddress remote_address) {
  if (interop_match_addr_or_name(INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING,
                                 &remote_address,
                                 &btif_storage_get_remote_device_property)) {
    log::verbose("device is denylisted for PCE SDP");
    return true;
  } else {
    return false;
  }
}

/*************************************************************************************
**
** Function        sdp_save_local_pse_record_attributes_val
**
** Description     Save pbap 1.2 sdp record attributes values, which would be
*used for dynamic version upgrade.
**
** Returns         BOOLEAN
**
***************************************************************************************/
void sdp_save_local_pse_record_attributes(int32_t rfcomm_channel_number,
                                          int32_t l2cap_psm,
                                          int32_t profile_version,
                                          uint32_t supported_features,
                                          uint32_t supported_repositories) {
  log::warn(
      "rfcomm_channel_number: 0x{:x}, l2cap_psm: 0x{:x} profile_version: "
      "0x{:x}supported_features: 0x{:x} supported_repositories:  0x{:x}",
      rfcomm_channel_number, l2cap_psm, profile_version, supported_features,
      supported_repositories);
  sdpPseLocalRecord.rfcomm_channel_number = rfcomm_channel_number;
  sdpPseLocalRecord.l2cap_psm = l2cap_psm;
  sdpPseLocalRecord.profile_version = profile_version;
  sdpPseLocalRecord.supported_features = supported_features;
  sdpPseLocalRecord.supported_repositories = supported_repositories;
}
