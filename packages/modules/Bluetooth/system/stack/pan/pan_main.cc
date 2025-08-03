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
 *  This file contains main functions to support PAN profile
 *  commands and events.
 *
 ******************************************************************************/

#define LOG_TAG "pan"

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>
#include <string.h>  // memset

#include <cstdint>

#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bnep_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "stack/pan/pan_int.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth;
using bluetooth::Uuid;

tPAN_CB pan_cb;

/*******************************************************************************
 *
 * Function         pan_register_with_bnep
 *
 * Description      This function registers PAN profile with BNEP
 *
 * Parameters:      none
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_register_with_bnep(void) {
  tBNEP_REGISTER reg_info;

  memset(&reg_info, 0, sizeof(tBNEP_REGISTER));

  reg_info.p_conn_ind_cb = pan_conn_ind_cb;
  reg_info.p_conn_state_cb = pan_connect_state_cb;
  reg_info.p_data_buf_cb = pan_data_buf_ind_cb;
  reg_info.p_data_ind_cb = NULL;
  reg_info.p_tx_data_flow_cb = pan_tx_data_flow_cb;
  reg_info.p_filter_ind_cb = pan_proto_filt_ind_cb;
  reg_info.p_mfilter_ind_cb = pan_mcast_filt_ind_cb;

  BNEP_Register(&reg_info);
}

/*******************************************************************************
 *
 * Function         pan_conn_ind_cb
 *
 * Description      This function is registered with BNEP as connection
 *                  indication callback. BNEP will call this when there is
 *                  connection request from the peer. PAN should call
 *                  BNEP_ConnectResp to indicate whether to accept the
 *                  connection or reject
 *
 * Parameters:      handle      - handle for the connection
 *                  p_bda       - BD Addr of the peer requesting the connection
 *                  remote_uuid     - UUID of the source role (peer device role)
 *                  local_uuid      - UUID of the destination role (local device
 *                                                                  role)
 *                  is_role_change  - Flag to indicate that it is a role change
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_conn_ind_cb(uint16_t handle, const RawAddress& p_bda,
                     const Uuid& remote_uuid, const Uuid& local_uuid,
                     bool is_role_change) {
  /* If we are in GN or NAP role and have one or more active connections and the
   * received connection is for user role reject it. If we are in user role with
   * one connection active reject the connection. Allocate PCB and store the
   * parameters. Make bridge request to the host system if connection is for NAP
   */

  if (!remote_uuid.Is16Bit()) {
    log::error("PAN Connection failed because of wrong remote UUID");
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_SRC_UUID);
    return;
  }

  if (!local_uuid.Is16Bit()) {
    log::error("PAN Connection failed because of wrong local UUID");
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_DST_UUID);
    return;
  }

  uint16_t remote_uuid16 = remote_uuid.As16Bit();
  uint16_t local_uuid16 = local_uuid.As16Bit();

  log::verbose(
      "handle {}, current role {}, dst uuid 0x{:x}, src uuid 0x{:x}, role "
      "change {}",
      handle, pan_cb.role, local_uuid16, remote_uuid16,
      is_role_change ? "YES" : "NO");

  /* Check if the source UUID is a valid one */
  if (remote_uuid16 != UUID_SERVCLASS_PANU &&
      remote_uuid16 != UUID_SERVCLASS_NAP &&
      remote_uuid16 != UUID_SERVCLASS_GN) {
    log::error("Src UUID 0x{:x} is not valid", remote_uuid16);
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_SRC_UUID);
    return;
  }

  /* Check if the destination UUID is a valid one */
  if (local_uuid16 != UUID_SERVCLASS_PANU &&
      local_uuid16 != UUID_SERVCLASS_NAP && local_uuid16 != UUID_SERVCLASS_GN) {
    log::error("Dst UUID 0x{:x} is not valid", local_uuid16);
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_DST_UUID);
    return;
  }

  /* Check if currently we support the destination role requested */
  if (((!(pan_cb.role & UUID_SERVCLASS_PANU)) &&
       local_uuid16 == UUID_SERVCLASS_PANU) ||
      ((!(pan_cb.role & UUID_SERVCLASS_GN)) &&
       local_uuid16 == UUID_SERVCLASS_GN) ||
      ((!(pan_cb.role & UUID_SERVCLASS_NAP)) &&
       local_uuid16 == UUID_SERVCLASS_NAP)) {
    log::error(
        "PAN Connection failed because of unsupported destination UUID 0x{:x}",
        local_uuid16);
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_DST_UUID);
    return;
  }

  /* Check for valid interactions between the three PAN profile roles */
  /*
   * For reference, see Table 1 in PAN Profile v1.0 spec.
   * Note: the remote is the initiator.
   */
  bool is_valid_interaction = false;
  switch (remote_uuid16) {
    case UUID_SERVCLASS_NAP:
    case UUID_SERVCLASS_GN:
      if (local_uuid16 == UUID_SERVCLASS_PANU) is_valid_interaction = true;
      break;
    case UUID_SERVCLASS_PANU:
      is_valid_interaction = true;
      break;
  }
  /*
   * Explicitly disable connections to the local PANU if the remote is
   * not PANU.
   */
  if ((local_uuid16 == UUID_SERVCLASS_PANU) &&
      (remote_uuid16 != UUID_SERVCLASS_PANU)) {
    is_valid_interaction = false;
  }
  if (!is_valid_interaction) {
    log::error(
        "PAN Connection failed because of invalid PAN profile roles "
        "interaction: Remote UUID 0x{:x} Local UUID 0x{:x}",
        remote_uuid16, local_uuid16);
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED_SRC_UUID);
    return;
  }

  uint8_t req_role;
  /* Requested destination role is */
  if (local_uuid16 == UUID_SERVCLASS_PANU)
    req_role = PAN_ROLE_CLIENT;
  else
    req_role = PAN_ROLE_NAP_SERVER;

  /* If the connection indication is for the existing connection
  ** Check if the new destination role is acceptable
  */
  tPAN_CONN* pcb = pan_get_pcb_by_handle(handle);
  if (pcb) {
    if (pan_cb.num_conns > 1 && local_uuid16 == UUID_SERVCLASS_PANU) {
      /* There are connections other than this one
      ** so we cann't accept PANU role. Reject
      */
      log::error(
          "Dst UUID should be either GN or NAP only because there are other "
          "connections");
      BNEP_ConnectResp(handle, BNEP_CONN_FAILED_DST_UUID);
      return;
    }

    /* If it is already in connected state check for bridging status */
    if (pcb->con_state == PAN_STATE_CONNECTED) {
      log::verbose("PAN Role changing New Src 0x{:x} Dst 0x{:x}", remote_uuid16,
                   local_uuid16);

      pcb->prv_src_uuid = pcb->src_uuid;
      pcb->prv_dst_uuid = pcb->dst_uuid;

      if (pcb->src_uuid == UUID_SERVCLASS_NAP &&
          local_uuid16 != UUID_SERVCLASS_NAP) {
        /* Remove bridging */
        if (pan_cb.pan_bridge_req_cb)
          (*pan_cb.pan_bridge_req_cb)(pcb->rem_bda, false);
      }
    }
    /* Set the latest active PAN role */
    pan_cb.active_role = req_role;
    pcb->src_uuid = local_uuid16;
    pcb->dst_uuid = remote_uuid16;
    BNEP_ConnectResp(handle, BNEP_SUCCESS);
    return;
  } else {
    /* If this a new connection and destination is PANU role and
    ** we already have a connection then reject the request.
    ** If we have a connection in PANU role then reject it
    */
    if (pan_cb.num_conns && (local_uuid16 == UUID_SERVCLASS_PANU ||
                             pan_cb.active_role == PAN_ROLE_CLIENT)) {
      log::error("PAN already have a connection and can't be user");
      BNEP_ConnectResp(handle, BNEP_CONN_FAILED_DST_UUID);
      return;
    }
  }

  /* This is a new connection */
  log::verbose("New connection indication for handle {}", handle);
  pcb = pan_allocate_pcb(p_bda, handle);
  if (!pcb) {
    log::error("PAN no control block for new connection");
    BNEP_ConnectResp(handle, BNEP_CONN_FAILED);
    return;
  }

  log::verbose("PAN connection destination UUID is 0x{:x}", local_uuid16);
  /* Set the latest active PAN role */
  pan_cb.active_role = req_role;
  pcb->src_uuid = local_uuid16;
  pcb->dst_uuid = remote_uuid16;
  pcb->con_state = PAN_STATE_CONN_START;
  pan_cb.num_conns++;

  BNEP_ConnectResp(handle, BNEP_SUCCESS);
  return;
}

/*******************************************************************************
 *
 * Function         pan_connect_state_cb
 *
 * Description      This function is registered with BNEP as connection state
 *                  change callback. BNEP will call this when the connection
 *                  is established successfully or terminated
 *
 * Parameters:      handle  - handle for the connection given in the connection
 *                            indication callback
 *                  rem_bda - remote device bd addr
 *                  result  - indicates whether the connection is up or down
 *                            BNEP_SUCCESS if the connection is up all other
 *                            values indicate appropriate errors.
 *                  is_role_change - flag to indicate that it is a role change
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_connect_state_cb(uint16_t handle,
                          UNUSED_ATTR const RawAddress& rem_bda,
                          tBNEP_RESULT result, bool is_role_change) {
  tPAN_CONN* pcb;
  uint8_t peer_role;

  log::verbose("pan_connect_state_cb - for handle {}, result {}", handle,
               result);
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN State change indication for wrong handle {}", handle);
    return;
  }

  /* If the connection is getting terminated remove bridging */
  if (result != BNEP_SUCCESS) {
    /* Inform the application that connection is down */
    if (pan_cb.pan_conn_state_cb)
      (*pan_cb.pan_conn_state_cb)(pcb->handle, pcb->rem_bda,
                                  (tPAN_RESULT)result, is_role_change,
                                  PAN_ROLE_INACTIVE, PAN_ROLE_INACTIVE);

    /* Check if this failure is for role change only */
    if (pcb->con_state != PAN_STATE_CONNECTED &&
        (pcb->con_flags & PAN_FLAGS_CONN_COMPLETED)) {
      /* restore the original values */
      log::verbose("restoring the connection state to active");
      pcb->con_state = PAN_STATE_CONNECTED;
      pcb->con_flags &= (~PAN_FLAGS_CONN_COMPLETED);

      pcb->src_uuid = pcb->prv_src_uuid;
      pcb->dst_uuid = pcb->prv_dst_uuid;
      pan_cb.active_role = pan_cb.prv_active_role;

      if ((pcb->src_uuid == UUID_SERVCLASS_NAP) && pan_cb.pan_bridge_req_cb)
        (*pan_cb.pan_bridge_req_cb)(pcb->rem_bda, true);

      return;
    }

    if (pcb->con_state == PAN_STATE_CONNECTED) {
      /* If the connections destination role is NAP remove bridging */
      if ((pcb->src_uuid == UUID_SERVCLASS_NAP) && pan_cb.pan_bridge_req_cb)
        (*pan_cb.pan_bridge_req_cb)(pcb->rem_bda, false);
    }

    pan_cb.num_conns--;
    pan_release_pcb(pcb);
    return;
  }

  /* Requested destination role is */
  if (pcb->src_uuid == UUID_SERVCLASS_PANU)
    pan_cb.active_role = PAN_ROLE_CLIENT;
  else
    pan_cb.active_role = PAN_ROLE_NAP_SERVER;

  if (pcb->dst_uuid == UUID_SERVCLASS_PANU)
    peer_role = PAN_ROLE_CLIENT;
  else
    peer_role = PAN_ROLE_NAP_SERVER;

  pcb->con_state = PAN_STATE_CONNECTED;

  /* Inform the application that connection is down */
  if (pan_cb.pan_conn_state_cb)
    (*pan_cb.pan_conn_state_cb)(pcb->handle, pcb->rem_bda, PAN_SUCCESS,
                                is_role_change, pan_cb.active_role, peer_role);

  /* Create bridge if the destination role is NAP */
  if (pan_cb.pan_bridge_req_cb && pcb->src_uuid == UUID_SERVCLASS_NAP) {
    log::verbose("PAN requesting for bridge");
    (*pan_cb.pan_bridge_req_cb)(pcb->rem_bda, true);
  }
}

/*******************************************************************************
 *
 * Function         pan_data_buf_ind_cb
 *
 * Description      This function is registered with BNEP as data buffer
 *                  indication callback. BNEP will call this when the peer sends
 *                  any data on this connection. PAN is responsible to release
 *                  the buffer
 *
 * Parameters:      handle      - handle for the connection
 *                  src         - source BD Addr
 *                  dst         - destination BD Addr
 *                  protocol    - Network protocol of the Eth packet
 *                  p_buf       - pointer to the data buffer
 *                  ext         - to indicate whether the data contains any
 *                                         extension headers before the payload
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_data_buf_ind_cb(uint16_t handle, const RawAddress& src,
                         const RawAddress& dst, uint16_t protocol,
                         BT_HDR* p_buf, bool ext) {
  tPAN_CONN *pcb, *dst_pcb;
  tBNEP_RESULT result;
  uint16_t i, len;
  uint8_t* p_data;
  bool forward = false;

  /* Check if the connection is in right state */
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN Data buffer indication for wrong handle {}", handle);
    osi_free(p_buf);
    return;
  }

  if (pcb->con_state != PAN_STATE_CONNECTED) {
    log::error("PAN Data indication in wrong state {} for handle {}",
               pcb->con_state, handle);
    pcb->read.drops++;
    osi_free(p_buf);
    return;
  }

  p_data = (uint8_t*)(p_buf + 1) + p_buf->offset;
  len = p_buf->len;

  pcb->read.octets += len;
  pcb->read.packets++;

  log::verbose(
      "pan_data_buf_ind_cb - for handle {}, protocol 0x{:x}, length {}, ext {}",
      handle, protocol, len, ext);

  if (pcb->src_uuid == UUID_SERVCLASS_NAP)
    forward = true;
  else
    forward = false;

  /* Check if it is broadcast or multicast packet */
  if (pcb->src_uuid != UUID_SERVCLASS_PANU) {
    if (dst.address[0] & 0x01) {
      log::verbose(
          "PAN received broadcast packet on handle {}, src uuid 0x{:x}", handle,
          pcb->src_uuid);
      for (i = 0; i < MAX_PAN_CONNS; i++) {
        if (pan_cb.pcb[i].con_state == PAN_STATE_CONNECTED &&
            pan_cb.pcb[i].handle != handle &&
            pcb->src_uuid == pan_cb.pcb[i].src_uuid) {
          BNEP_Write(pan_cb.pcb[i].handle, dst, p_data, len, protocol, src,
                     ext);
        }
      }

      if (pan_cb.pan_data_buf_ind_cb)
        (*pan_cb.pan_data_buf_ind_cb)(pcb->handle, src, dst, protocol, p_buf,
                                      ext, forward);
      else if (pan_cb.pan_data_ind_cb)
        (*pan_cb.pan_data_ind_cb)(pcb->handle, src, dst, protocol, p_data, len,
                                  ext, forward);

      osi_free(p_buf);
      return;
    }

    /* Check if it is for any other PAN connection */
    dst_pcb = pan_get_pcb_by_addr(dst);
    if (dst_pcb) {
      log::verbose(
          "destination PANU found on handle {} and sending data, len: {}",
          dst_pcb->handle, len);

      result =
          BNEP_Write(dst_pcb->handle, dst, p_data, len, protocol, src, ext);
      if (result != BNEP_SUCCESS && result != BNEP_IGNORE_CMD)
        log::error("Failed to write data for PAN connection handle {}",
                   dst_pcb->handle);
      pcb->read.errors++;
      osi_free(p_buf);
      return;
    }
  }

  /* Send it over the LAN or give it to host software */
  if (pan_cb.pan_data_buf_ind_cb)
    (*pan_cb.pan_data_buf_ind_cb)(pcb->handle, src, dst, protocol, p_buf, ext,
                                  forward);
  else if (pan_cb.pan_data_ind_cb)
    (*pan_cb.pan_data_ind_cb)(pcb->handle, src, dst, protocol, p_data, len, ext,
                              forward);
  osi_free(p_buf);
  return;
}

/*******************************************************************************
 *
 * Function         pan_proto_filt_ind_cb
 *
 * Description      This function is registered with BNEP to receive tx data
 *          flow status
 *
 * Parameters:      handle      - handle for the connection
 *          event       - flow status
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_tx_data_flow_cb(uint16_t handle, tBNEP_RESULT result) {
  if (pan_cb.pan_tx_data_flow_cb)
    (*pan_cb.pan_tx_data_flow_cb)(handle, (tPAN_RESULT)result);

  return;
}

/*******************************************************************************
 *
 * Function         pan_proto_filt_ind_cb
 *
 * Description      This function is registered with BNEP as proto filter
 *                  indication callback. BNEP will call this when the peer sends
 *                  any protocol filter set for the connection or to indicate
 *                  the result of the protocol filter set by the local device
 *
 * Parameters:      handle      - handle for the connection
 *                  indication  - true if this is indication
 *                                false if it is called to give the result of
 *                                      local device protocol filter set
 *                  result      - This gives the result of the filter set
 *                                      operation
 *                  num_filters - number of filters set by the peer device
 *                  p_filters   - pointer to the filters set by the peer device
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_proto_filt_ind_cb(uint16_t handle, bool indication,
                           tBNEP_RESULT result, uint16_t num_filters,
                           uint8_t* p_filters) {
  log::verbose(
      "pan_proto_filt_ind_cb - called for handle {} with ind {}, result {}, "
      "num {}",
      handle, indication, result, num_filters);

  if (pan_cb.pan_pfilt_ind_cb)
    (*pan_cb.pan_pfilt_ind_cb)(handle, indication, (tPAN_RESULT)result,
                               num_filters, p_filters);
}

/*******************************************************************************
 *
 * Function         pan_mcast_filt_ind_cb
 *
 * Description      This function is registered with BNEP as mcast filter
 *                  indication callback. BNEP will call this when the peer sends
 *                  any multicast filter set for the connection or to indicate
 *                  the result of the multicast filter set by the local device
 *
 * Parameters:      handle      - handle for the connection
 *                  indication  - true if this is indication
 *                                false if it is called to give the result of
 *                                      local device multicast filter set
 *                  result      - This gives the result of the filter set
 *                                operation
 *                  num_filters - number of filters set by the peer device
 *                  p_filters   - pointer to the filters set by the peer device
 *
 * Returns          none
 *
 ******************************************************************************/
void pan_mcast_filt_ind_cb(uint16_t handle, bool indication,
                           tBNEP_RESULT result, uint16_t num_filters,
                           uint8_t* p_filters) {
  log::verbose(
      "pan_mcast_filt_ind_cb - called for handle {} with ind {}, result {}, "
      "num {}",
      handle, indication, result, num_filters);

  if (pan_cb.pan_mfilt_ind_cb)
    (*pan_cb.pan_mfilt_ind_cb)(handle, indication, (tPAN_RESULT)result,
                               num_filters, p_filters);
}
