/******************************************************************************
 *
 *  Copyright 2001-2012 Broadcom Corporation
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
 *  This file contains the BNEP API code
 *
 ******************************************************************************/

#include "bnep_api.h"

#include <bluetooth/log.h>
#include <string.h>

#include "bnep_int.h"
#include "bta/include/bta_sec_api.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth;
using bluetooth::Uuid;

/*******************************************************************************
 *
 * Function         BNEP_Init
 *
 * Description      This function initializes the BNEP unit. It should be called
 *                  before accessing any other APIs to initialize the control
 *                  block.
 *
 * Returns          void
 *
 ******************************************************************************/
void BNEP_Init(void) {
  memset(&bnep_cb, 0, sizeof(tBNEP_CB));
}

/*******************************************************************************
 *
 * Function         BNEP_Register
 *
 * Description      This function is called by the upper layer to register
 *                  its callbacks with BNEP
 *
 * Parameters:      p_reg_info - contains all callback function pointers
 *
 *
 * Returns          BNEP_SUCCESS        if registered successfully
 *                  BNEP_FAILURE        if connection state callback is missing
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_Register(tBNEP_REGISTER* p_reg_info) {
  /* There should be connection state call back registered */
  if ((!p_reg_info) || (!(p_reg_info->p_conn_state_cb)))
    return BNEP_SECURITY_FAIL;

  bnep_cb.p_conn_ind_cb = p_reg_info->p_conn_ind_cb;
  bnep_cb.p_conn_state_cb = p_reg_info->p_conn_state_cb;
  bnep_cb.p_data_ind_cb = p_reg_info->p_data_ind_cb;
  bnep_cb.p_data_buf_cb = p_reg_info->p_data_buf_cb;
  bnep_cb.p_filter_ind_cb = p_reg_info->p_filter_ind_cb;
  bnep_cb.p_mfilter_ind_cb = p_reg_info->p_mfilter_ind_cb;
  bnep_cb.p_tx_data_flow_cb = p_reg_info->p_tx_data_flow_cb;

  if (bnep_register_with_l2cap()) return BNEP_SECURITY_FAIL;

  bnep_cb.profile_registered = true;
  return BNEP_SUCCESS;
}

/*******************************************************************************
 *
 * Function         BNEP_Deregister
 *
 * Description      This function is called by the upper layer to de-register
 *                  its callbacks.
 *
 * Parameters:      void
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void BNEP_Deregister(void) {
  /* Clear all the call backs registered */
  bnep_cb.p_conn_ind_cb = NULL;
  bnep_cb.p_conn_state_cb = NULL;
  bnep_cb.p_data_ind_cb = NULL;
  bnep_cb.p_data_buf_cb = NULL;
  bnep_cb.p_filter_ind_cb = NULL;
  bnep_cb.p_mfilter_ind_cb = NULL;

  bnep_cb.profile_registered = false;
  L2CA_Deregister(BT_PSM_BNEP);
}

/*******************************************************************************
 *
 * Function         BNEP_Connect
 *
 * Description      This function creates a BNEP connection to a remote
 *                  device.
 *
 * Parameters:      p_rem_addr  - BD_ADDR of the peer
 *                  src_uuid    - source uuid for the connection
 *                  dst_uuid    - destination uuid for the connection
 *                  p_handle    - pointer to return the handle for the
 *                                connection
 *
 * Returns          BNEP_SUCCESS                if connection started
 *                  BNEP_NO_RESOURCES           if no resources
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_Connect(const RawAddress& p_rem_bda, const Uuid& src_uuid,
                          const Uuid& dst_uuid, uint16_t* p_handle,
                          uint32_t mx_chan_id) {
  uint16_t cid;
  tBNEP_CONN* p_bcb = bnepu_find_bcb_by_bd_addr(p_rem_bda);

  log::verbose("BDA:{}", ADDRESS_TO_LOGGABLE_STR(p_rem_bda));

  if (!bnep_cb.profile_registered) return BNEP_WRONG_STATE;

  if (!p_bcb) {
    p_bcb = bnepu_allocate_bcb(p_rem_bda);
    if (p_bcb == NULL) return (BNEP_NO_RESOURCES);
  } else if (p_bcb->con_state != BNEP_STATE_CONNECTED)
    return BNEP_WRONG_STATE;
  else {
    /* Backup current UUID values to restore if role change fails */
    p_bcb->prv_src_uuid = p_bcb->src_uuid;
    p_bcb->prv_dst_uuid = p_bcb->dst_uuid;
  }

  /* We are the originator of this connection */
  p_bcb->con_flags |= BNEP_FLAGS_IS_ORIG;

  p_bcb->src_uuid = src_uuid;
  p_bcb->dst_uuid = dst_uuid;

  if (p_bcb->con_state == BNEP_STATE_CONNECTED) {
    /* Transition to the next appropriate state, waiting for connection confirm.
     */
    p_bcb->con_state = BNEP_STATE_SEC_CHECKING;

    log::verbose("BNEP initiating security procedures for src uuid {}",
                 p_bcb->src_uuid.ToString().c_str());

    bnep_sec_check_complete(&p_bcb->rem_bda, BT_TRANSPORT_BR_EDR, p_bcb);
  } else {
    /* Transition to the next appropriate state, waiting for connection confirm.
     */
    p_bcb->con_state = BNEP_STATE_CONN_START;

    cid = L2CA_ConnectReq2(BT_PSM_BNEP, p_bcb->rem_bda,
                           BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT);
    if (cid != 0) {
      p_bcb->l2cap_cid = cid;

    } else {
      log::error("BNEP - Originate failed");
      if (bnep_cb.p_conn_state_cb)
        (*bnep_cb.p_conn_state_cb)(p_bcb->handle, p_bcb->rem_bda,
                                   BNEP_CONN_FAILED, false);
      bnepu_release_bcb(p_bcb);
      return BNEP_CONN_FAILED;
    }

    /* Start timer waiting for connect */
    alarm_set_on_mloop(p_bcb->conn_timer, BNEP_CONN_TIMEOUT_MS,
                       bnep_conn_timer_timeout, p_bcb);
  }

  *p_handle = p_bcb->handle;
  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_ConnectResp
 *
 * Description      This function is called in responce to connection indication
 *
 *
 * Parameters:      handle  - handle given in the connection indication
 *                  resp    - responce for the connection indication
 *
 * Returns          BNEP_SUCCESS                if connection started
 *                  BNEP_WRONG_HANDLE           if the connection is not found
 *                  BNEP_WRONG_STATE            if the responce is not expected
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_ConnectResp(uint16_t handle, tBNEP_RESULT resp) {
  tBNEP_CONN* p_bcb;
  uint16_t resp_code = BNEP_SETUP_CONN_OK;

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) return (BNEP_WRONG_HANDLE);

  p_bcb = &(bnep_cb.bcb[handle - 1]);

  if (p_bcb->con_state != BNEP_STATE_CONN_SETUP ||
      (!(p_bcb->con_flags & BNEP_FLAGS_SETUP_RCVD)))
    return (BNEP_WRONG_STATE);

  log::debug("handle {}, responce {}", handle, resp);

  /* Form appropriate responce based on profile responce */
  if (resp == BNEP_CONN_FAILED_SRC_UUID)
    resp_code = BNEP_SETUP_INVALID_SRC_UUID;
  else if (resp == BNEP_CONN_FAILED_DST_UUID)
    resp_code = BNEP_SETUP_INVALID_DEST_UUID;
  else if (resp == BNEP_CONN_FAILED_UUID_SIZE)
    resp_code = BNEP_SETUP_INVALID_UUID_SIZE;
  else if (resp == BNEP_SUCCESS)
    resp_code = BNEP_SETUP_CONN_OK;
  else
    resp_code = BNEP_SETUP_CONN_NOT_ALLOWED;

  bnep_send_conn_response(p_bcb, resp_code);
  p_bcb->con_flags &= (~BNEP_FLAGS_SETUP_RCVD);

  if (resp == BNEP_SUCCESS)
    bnep_connected(p_bcb);
  else if (p_bcb->con_flags & BNEP_FLAGS_CONN_COMPLETED) {
    /* Restore the original parameters */
    p_bcb->con_state = BNEP_STATE_CONNECTED;
    p_bcb->con_flags &= (~BNEP_FLAGS_SETUP_RCVD);

    p_bcb->src_uuid = p_bcb->prv_src_uuid;
    p_bcb->dst_uuid = p_bcb->prv_dst_uuid;
  }

  /* Process remaining part of the setup message (extension headers) */
  if (p_bcb->p_pending_data) {
    uint8_t extension_present = true, *p, ext_type;
    uint16_t rem_len;

    rem_len = p_bcb->p_pending_data->len;
    p = (uint8_t*)(p_bcb->p_pending_data + 1) + p_bcb->p_pending_data->offset;
    while (extension_present && p && rem_len) {
      ext_type = *p++;
      rem_len--;
      extension_present = ext_type >> 7;
      ext_type &= 0x7F;

      /* if unknown extension present stop processing */
      if (ext_type) break;

      p = bnep_process_control_packet(p_bcb, p, &rem_len, true);
    }

    osi_free_and_reset((void**)&p_bcb->p_pending_data);
  }
  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_Disconnect
 *
 * Description      This function is called to close the specified connection.
 *
 * Parameters:      handle   - handle of the connection
 *
 * Returns          BNEP_SUCCESS                if connection is disconnected
 *                  BNEP_WRONG_HANDLE           if no connection is not found
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_Disconnect(uint16_t handle) {
  tBNEP_CONN* p_bcb;

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) return (BNEP_WRONG_HANDLE);

  p_bcb = &(bnep_cb.bcb[handle - 1]);

  if (p_bcb->con_state == BNEP_STATE_IDLE) return (BNEP_WRONG_HANDLE);

  log::verbose("BNEP_Disconnect()  for handle {}", handle);

  L2CA_DisconnectReq(p_bcb->l2cap_cid);

  bnepu_release_bcb(p_bcb);

  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_WriteBuf
 *
 * Description      This function sends data in a GKI buffer on BNEP connection
 *
 * Parameters:      handle       - handle of the connection to write
 *                  p_dest_addr  - BD_ADDR/Ethernet addr of the destination
 *                  p_buf        - pointer to address of buffer with data
 *                  protocol     - protocol type of the packet
 *                  p_src_addr   - (optional) BD_ADDR/ethernet address of the
 *                                 source
 *                                 (should be NULL if it is local BD Addr)
 *                  fw_ext_present - forwarded extensions present
 *
 * Returns:         BNEP_WRONG_HANDLE       - if passed handle is not valid
 *                  BNEP_MTU_EXCEDED        - If the data length is greater than
 *                                            the MTU
 *                  BNEP_IGNORE_CMD         - If the packet is filtered out
 *                  BNEP_Q_SIZE_EXCEEDED    - If the Tx Q is full
 *                  BNEP_SUCCESS            - If written successfully
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_WriteBuf(uint16_t handle, const RawAddress& dest_addr,
                           BT_HDR* p_buf, uint16_t protocol,
                           const RawAddress& src_addr, bool fw_ext_present) {
  tBNEP_CONN* p_bcb;
  uint8_t* p_data;

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) {
    osi_free(p_buf);
    return (BNEP_WRONG_HANDLE);
  }

  p_bcb = &(bnep_cb.bcb[handle - 1]);
  /* Check MTU size */
  if (p_buf->len > BNEP_MTU_SIZE) {
    log::error("length {} exceeded MTU {}", p_buf->len, BNEP_MTU_SIZE);
    osi_free(p_buf);
    return (BNEP_MTU_EXCEDED);
  }

  /* Check if the packet should be filtered out */
  p_data = (uint8_t*)(p_buf + 1) + p_buf->offset;
  if (bnep_is_packet_allowed(p_bcb, dest_addr, protocol, fw_ext_present, p_data,
                             p_buf->len) != BNEP_SUCCESS) {
    /*
    ** If packet is filtered and ext headers are present
    ** drop the data and forward the ext headers
    */
    if (fw_ext_present) {
      uint8_t ext, length;
      uint16_t org_len, new_len;
      /* parse the extension headers and findout the new packet len */
      org_len = p_buf->len;
      new_len = 0;
      do {
        if ((new_len + 2) > org_len) {
          osi_free(p_buf);
          return BNEP_IGNORE_CMD;
        }

        ext = *p_data++;
        length = *p_data++;
        p_data += length;

        new_len += (length + 2);

        if (new_len > org_len) {
          osi_free(p_buf);
          return BNEP_IGNORE_CMD;
        }

      } while (ext & 0x80);

      if (protocol != BNEP_802_1_P_PROTOCOL)
        protocol = 0;
      else {
        new_len += 4;
        if (new_len > org_len) {
          osi_free(p_buf);
          return BNEP_IGNORE_CMD;
        }
        p_data[2] = 0;
        p_data[3] = 0;
      }
      p_buf->len = new_len;
    } else {
      osi_free(p_buf);
      return BNEP_IGNORE_CMD;
    }
  }

  /* Check transmit queue */
  if (fixed_queue_length(p_bcb->xmit_q) >= BNEP_MAX_XMITQ_DEPTH) {
    osi_free(p_buf);
    return (BNEP_Q_SIZE_EXCEEDED);
  }

  /* Build the BNEP header */
  bnepu_build_bnep_hdr(p_bcb, p_buf, protocol, src_addr, dest_addr,
                       fw_ext_present);

  /* Send the data or queue it up */
  bnepu_check_send_packet(p_bcb, p_buf);

  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_Write
 *
 * Description      This function sends data over a BNEP connection
 *
 * Parameters:      handle       - handle of the connection to write
 *                  dest_addr    - BD_ADDR/Ethernet addr of the destination
 *                  p_data       - pointer to data start
 *                  protocol     - protocol type of the packet
 *                  src_addr     - (optional) BD_ADDR/ethernet address of the
 *                                 source
 *                                 (should be kEmpty if it is local BD Addr)
 *                  fw_ext_present - forwarded extensions present
 *
 * Returns:         BNEP_WRONG_HANDLE       - if passed handle is not valid
 *                  BNEP_MTU_EXCEDED        - If the data length is greater than
 *                                            the MTU
 *                  BNEP_IGNORE_CMD         - If the packet is filtered out
 *                  BNEP_Q_SIZE_EXCEEDED    - If the Tx Q is full
 *                  BNEP_NO_RESOURCES       - If not able to allocate a buffer
 *                  BNEP_SUCCESS            - If written successfully
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_Write(uint16_t handle, const RawAddress& dest_addr,
                        uint8_t* p_data, uint16_t len, uint16_t protocol,
                        const RawAddress& src_addr, bool fw_ext_present) {
  tBNEP_CONN* p_bcb;
  uint8_t* p;

  /* Check MTU size. Consider the possibility of having extension headers */
  if (len > BNEP_MTU_SIZE) {
    log::error("length {} exceeded MTU {}", len, BNEP_MTU_SIZE);
    return (BNEP_MTU_EXCEDED);
  }

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) return (BNEP_WRONG_HANDLE);

  p_bcb = &(bnep_cb.bcb[handle - 1]);

  /* Check if the packet should be filtered out */
  if (bnep_is_packet_allowed(p_bcb, dest_addr, protocol, fw_ext_present, p_data,
                             len) != BNEP_SUCCESS) {
    /*
    ** If packet is filtered and ext headers are present
    ** drop the data and forward the ext headers
    */
    if (fw_ext_present) {
      uint8_t ext, length;
      uint16_t org_len, new_len;
      /* parse the extension headers and findout the new packet len */
      org_len = len;
      new_len = 0;
      p = p_data;
      do {
        if ((new_len + 2) > org_len) {
          return BNEP_IGNORE_CMD;
        }

        ext = *p_data++;
        length = *p_data++;
        p_data += length;

        new_len += (length + 2);

        if (new_len > org_len) return BNEP_IGNORE_CMD;

      } while (ext & 0x80);

      if (protocol != BNEP_802_1_P_PROTOCOL)
        protocol = 0;
      else {
        new_len += 4;
        if (new_len > org_len) return BNEP_IGNORE_CMD;
        p_data[2] = 0;
        p_data[3] = 0;
      }
      len = new_len;
      p_data = p;
    } else
      return BNEP_IGNORE_CMD;
  }

  /* Check transmit queue */
  if (fixed_queue_length(p_bcb->xmit_q) >= BNEP_MAX_XMITQ_DEPTH)
    return (BNEP_Q_SIZE_EXCEEDED);

  /* Get a buffer to copy the data into */
  BT_HDR* p_buf = (BT_HDR*)osi_malloc(BNEP_BUF_SIZE);

  p_buf->len = len;
  p_buf->offset = BNEP_MINIMUM_OFFSET;
  p = (uint8_t*)(p_buf + 1) + BNEP_MINIMUM_OFFSET;

  memcpy(p, p_data, len);

  /* Build the BNEP header */
  bnepu_build_bnep_hdr(p_bcb, p_buf, protocol, src_addr, dest_addr,
                       fw_ext_present);

  /* Send the data or queue it up */
  bnepu_check_send_packet(p_bcb, p_buf);

  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_SetProtocolFilters
 *
 * Description      This function sets the protocol filters on peer device
 *
 * Parameters:      handle        - Handle for the connection
 *                  num_filters   - total number of filter ranges
 *                  p_start_array - Array of beginings of all protocol ranges
 *                  p_end_array   - Array of ends of all protocol ranges
 *
 * Returns          BNEP_WRONG_HANDLE           - if the connection handle is
 *                                                not valid
 *                  BNEP_SET_FILTER_FAIL        - if the connection is in wrong
 *                                                state
 *                  BNEP_TOO_MANY_FILTERS       - if too many filters
 *                  BNEP_SUCCESS                - if request sent successfully
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_SetProtocolFilters(uint16_t handle, uint16_t num_filters,
                                     uint16_t* p_start_array,
                                     uint16_t* p_end_array) {
  uint16_t xx;
  tBNEP_CONN* p_bcb;

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) return (BNEP_WRONG_HANDLE);

  p_bcb = &(bnep_cb.bcb[handle - 1]);

  /* Check the connection state */
  if ((p_bcb->con_state != BNEP_STATE_CONNECTED) &&
      (!(p_bcb->con_flags & BNEP_FLAGS_CONN_COMPLETED)))
    return (BNEP_WRONG_STATE);

  /* Validate the parameters */
  if (num_filters && (!p_start_array || !p_end_array))
    return (BNEP_SET_FILTER_FAIL);

  if (num_filters > BNEP_MAX_PROT_FILTERS) return (BNEP_TOO_MANY_FILTERS);

  /* Fill the filter values in connnection block */
  for (xx = 0; xx < num_filters; xx++) {
    p_bcb->sent_prot_filter_start[xx] = *p_start_array++;
    p_bcb->sent_prot_filter_end[xx] = *p_end_array++;
  }

  p_bcb->sent_num_filters = num_filters;

  bnepu_send_peer_our_filters(p_bcb);

  return (BNEP_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BNEP_SetMulticastFilters
 *
 * Description      This function sets the filters for multicast addresses for
 *                  BNEP.
 *
 * Parameters:      handle        - Handle for the connection
 *                  num_filters   - total number of filter ranges
 *                  p_start_array - Pointer to sequence of beginings of all
 *                                         multicast address ranges
 *                  p_end_array   - Pointer to sequence of ends of all
 *                                         multicast address ranges
 *
 * Returns          BNEP_WRONG_HANDLE           - if the connection handle is
 *                                                not valid
 *                  BNEP_SET_FILTER_FAIL        - if the connection is in wrong
 *                                                state
 *                  BNEP_TOO_MANY_FILTERS       - if too many filters
 *                  BNEP_SUCCESS                - if request sent successfully
 *
 ******************************************************************************/
tBNEP_RESULT BNEP_SetMulticastFilters(uint16_t handle, uint16_t num_filters,
                                      uint8_t* p_start_array,
                                      uint8_t* p_end_array) {
  uint16_t xx;
  tBNEP_CONN* p_bcb;

  if ((!handle) || (handle > BNEP_MAX_CONNECTIONS)) return (BNEP_WRONG_HANDLE);

  p_bcb = &(bnep_cb.bcb[handle - 1]);

  /* Check the connection state */
  if ((p_bcb->con_state != BNEP_STATE_CONNECTED) &&
      (!(p_bcb->con_flags & BNEP_FLAGS_CONN_COMPLETED)))
    return (BNEP_WRONG_STATE);

  /* Validate the parameters */
  if (num_filters && (!p_start_array || !p_end_array))
    return (BNEP_SET_FILTER_FAIL);

  if (num_filters > BNEP_MAX_MULTI_FILTERS) return (BNEP_TOO_MANY_FILTERS);

  /* Fill the multicast filter values in connnection block */
  for (xx = 0; xx < num_filters; xx++) {
    memcpy(p_bcb->sent_mcast_filter_start[xx].address, p_start_array,
           BD_ADDR_LEN);
    memcpy(p_bcb->sent_mcast_filter_end[xx].address, p_end_array, BD_ADDR_LEN);

    p_start_array += BD_ADDR_LEN;
    p_end_array += BD_ADDR_LEN;
  }

  p_bcb->sent_mcast_filters = num_filters;

  bnepu_send_peer_our_multi_filters(p_bcb);

  return (BNEP_SUCCESS);
}
