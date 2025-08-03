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

/*****************************************************************************
 *
 *  This file contains main functions to support PAN profile
 *  commands and events.
 *
 *****************************************************************************/

#define LOG_TAG "pan"

#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "internal_include/bt_target.h"
#include "os/log.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/sdp_api.h"
#include "stack/include/sdpdefs.h"
#include "stack/pan/pan_int.h"
#include "types/raw_address.h"

using namespace bluetooth;
using namespace bluetooth::legacy::stack::sdp;

static const uint8_t pan_proto_elem_data[] = {
    0x35, 0x18,       /* data element sequence of length 0x18 bytes */
    0x35, 0x06,       /* data element sequence for L2CAP descriptor */
    0x19, 0x01, 0x00, /* UUID for L2CAP - 0x0100 */
    0x09, 0x00, 0x0F, /* PSM for BNEP - 0x000F */
    0x35, 0x0E,       /* data element seqence for BNEP descriptor */
    0x19, 0x00, 0x0F, /* UUID for BNEP - 0x000F */
    0x09, 0x01,
    0x00, /* BNEP specific parameter 0 -- Version of BNEP = version 1 = 0x0001
           */
    0x35,
    0x06, /* BNEP specific parameter 1 -- Supported network packet type list */
    0x09, 0x08, 0x00, /* network packet type IPv4 = 0x0800 */
    0x09, 0x08, 0x06  /* network packet type ARP  = 0x0806 */
};

/*******************************************************************************
 *
 * Function         pan_register_with_sdp
 *
 * Description
 *
 * Returns
 *
 ******************************************************************************/
uint32_t pan_register_with_sdp(uint16_t uuid, const char* p_name,
                               const char* p_desc) {
  uint32_t sdp_handle;
  uint16_t browse_list = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  uint16_t security = 0;
  uint32_t proto_len = (uint32_t)pan_proto_elem_data[1];

  /* Create a record */
  sdp_handle = get_legacy_stack_sdp_api()->handle.SDP_CreateRecord();

  if (sdp_handle == 0) {
    log::error("PAN_SetRole - could not create SDP record");
    return 0;
  }

  /* Service Class ID List */
  get_legacy_stack_sdp_api()->handle.SDP_AddServiceClassIdList(sdp_handle, 1,
                                                               &uuid);

  /* Add protocol element sequence from the constant string */
  get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_PROTOCOL_DESC_LIST, DATA_ELE_SEQ_DESC_TYPE, proto_len,
      (uint8_t*)(pan_proto_elem_data + 2));

  /* Language base */
  get_legacy_stack_sdp_api()->handle.SDP_AddLanguageBaseAttrIDList(
      sdp_handle, LANG_ID_CODE_ENGLISH, LANG_ID_CHAR_ENCODE_UTF8,
      LANGUAGE_BASE_ID);

  /* Profile descriptor list */
  get_legacy_stack_sdp_api()->handle.SDP_AddProfileDescriptorList(
      sdp_handle, uuid, PAN_PROFILE_VERSION);

  /* Service Name */
  get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SERVICE_NAME, TEXT_STR_DESC_TYPE,
      (uint8_t)(strlen(p_name) + 1), (uint8_t*)p_name);

  /* Service description */
  get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SERVICE_DESCRIPTION, TEXT_STR_DESC_TYPE,
      (uint8_t)(strlen(p_desc) + 1), (uint8_t*)p_desc);

  /* Security description */
  // Only NAP and PANU has service level security; GN has no security
  if (uuid == UUID_SERVCLASS_NAP || uuid == UUID_SERVCLASS_PANU) {
    UINT16_TO_BE_FIELD(&security, 0x0001);
  }
  get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
      sdp_handle, ATTR_ID_SECURITY_DESCRIPTION, UINT_DESC_TYPE, 2,
      (uint8_t*)&security);

  if (uuid == UUID_SERVCLASS_NAP) {
    uint16_t NetAccessType = 0x0005;      /* Ethernet */
    uint32_t NetAccessRate = 0x0001312D0; /* 10Mb/sec */
    uint8_t array[10], *p;

    /* Net access type. */
    p = array;
    UINT16_TO_BE_STREAM(p, NetAccessType);
    get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_NET_ACCESS_TYPE, UINT_DESC_TYPE, 2, array);

    /* Net access rate. */
    p = array;
    UINT32_TO_BE_STREAM(p, NetAccessRate);
    get_legacy_stack_sdp_api()->handle.SDP_AddAttribute(
        sdp_handle, ATTR_ID_MAX_NET_ACCESS_RATE, UINT_DESC_TYPE, 4, array);
  }

  /* Make the service browsable */
  get_legacy_stack_sdp_api()->handle.SDP_AddUuidSequence(
      sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &browse_list);

  return sdp_handle;
}

/*******************************************************************************
 *
 * Function         pan_allocate_pcb
 *
 * Description
 *
 * Returns
 *
 ******************************************************************************/
tPAN_CONN* pan_allocate_pcb(const RawAddress& p_bda, uint16_t handle) {
  uint16_t i;

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state != PAN_STATE_IDLE &&
        pan_cb.pcb[i].handle == handle)
      return NULL;
  }

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state != PAN_STATE_IDLE &&
        pan_cb.pcb[i].rem_bda == p_bda)
      return NULL;
  }

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state == PAN_STATE_IDLE) {
      memset(&(pan_cb.pcb[i]), 0, sizeof(tPAN_CONN));
      pan_cb.pcb[i].rem_bda = p_bda;
      pan_cb.pcb[i].handle = handle;
      return &(pan_cb.pcb[i]);
    }
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         pan_get_pcb_by_handle
 *
 * Description
 *
 * Returns
 *
 ******************************************************************************/
tPAN_CONN* pan_get_pcb_by_handle(uint16_t handle) {
  uint16_t i;

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state != PAN_STATE_IDLE &&
        pan_cb.pcb[i].handle == handle)
      return &(pan_cb.pcb[i]);
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         pan_get_pcb_by_addr
 *
 * Description
 *
 * Returns
 *
 ******************************************************************************/
tPAN_CONN* pan_get_pcb_by_addr(const RawAddress& p_bda) {
  uint16_t i;

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state == PAN_STATE_IDLE) continue;

    if (pan_cb.pcb[i].rem_bda == p_bda) return &(pan_cb.pcb[i]);

    /*
    if (pan_cb.pcb[i].mfilter_present &&
        p_bda == pan_cb.pcb[i].multi_cast_bridge)
        return &(pan_cb.pcb[i]);
    */
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         pan_close_all_connections
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void pan_close_all_connections(void) {
  uint16_t i;

  for (i = 0; i < MAX_PAN_CONNS; i++) {
    if (pan_cb.pcb[i].con_state != PAN_STATE_IDLE) {
      BNEP_Disconnect(pan_cb.pcb[i].handle);
      pan_cb.pcb[i].con_state = PAN_STATE_IDLE;
    }
  }

  pan_cb.active_role = PAN_ROLE_INACTIVE;
  pan_cb.num_conns = 0;
  return;
}

/*******************************************************************************
 *
 * Function         pan_release_pcb
 *
 * Description      This function releases a PCB.
 *
 * Returns          void
 *
 ******************************************************************************/
void pan_release_pcb(tPAN_CONN* p_pcb) {
  /* Drop any response pointer we may be holding */
  memset(p_pcb, 0, sizeof(tPAN_CONN));
  p_pcb->con_state = PAN_STATE_IDLE;
}

/*******************************************************************************
 *
 * Function         pan_dump_status
 *
 * Description      This function dumps the pan control block and connection
 *                  blocks information
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_dump_status(void) {
#if (PAN_SUPPORTS_DEBUG_DUMP == TRUE)
  uint16_t i;
  tPAN_CONN* p_pcb;

  log::verbose("PAN role {:x}, active role {}, num_conns {}", pan_cb.role,
               pan_cb.active_role, pan_cb.num_conns);

  for (i = 0, p_pcb = pan_cb.pcb; i < MAX_PAN_CONNS; i++, p_pcb++) {
    log::verbose("{} state:{}, handle:{}, src{}, BD:{}", i, p_pcb->con_state,
                 p_pcb->handle, p_pcb->src_uuid,
                 ADDRESS_TO_LOGGABLE_STR(p_pcb->rem_bda));
  }
#endif
}
