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

#include "stack/include/pan_api.h"

#include <base/logging.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <cstring>

#include "bta/sys/bta_sys.h"
#include "internal_include/bt_target.h"
#include "main/shim/dumpsys.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bnep_api.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/sdp_api.h"
#include "stack/pan/pan_int.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth;
using namespace bluetooth::legacy::stack::sdp;

using bluetooth::Uuid;

namespace {
constexpr char kBtmLogTag[] = "PAN";
}

extern std::string user_service_name; /* Service name for PANU role */
extern std::string gn_service_name;   /* Service name for GN role */
extern std::string nap_service_name;  /* Service name for NAP role */

/*******************************************************************************
 *
 * Function         PAN_Register
 *
 * Description      This function is called by the application to register
 *                  its callbacks with PAN profile. The application then
 *                  should set the PAN role explicitly.
 *
 * Parameters:      p_register - contains all callback function pointers
 *
 *
 * Returns          none
 *
 ******************************************************************************/
void PAN_Register(tPAN_REGISTER* p_register) {
  if (!p_register) return;

  pan_register_with_bnep();

  pan_cb.pan_conn_state_cb = p_register->pan_conn_state_cb;
  pan_cb.pan_bridge_req_cb = p_register->pan_bridge_req_cb;
  pan_cb.pan_data_buf_ind_cb = p_register->pan_data_buf_ind_cb;
  pan_cb.pan_data_ind_cb = p_register->pan_data_ind_cb;
  pan_cb.pan_pfilt_ind_cb = p_register->pan_pfilt_ind_cb;
  pan_cb.pan_mfilt_ind_cb = p_register->pan_mfilt_ind_cb;
  pan_cb.pan_tx_data_flow_cb = p_register->pan_tx_data_flow_cb;

  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Registered");
}

/*******************************************************************************
 *
 * Function         PAN_Deregister
 *
 * Description      This function is called by the application to de-register
 *                  its callbacks with PAN profile. This will make the PAN to
 *                  become inactive. This will deregister PAN services from SDP
 *                  and close all active connections
 *
 * Parameters:      none
 *
 *
 * Returns          none
 *
 ******************************************************************************/
void PAN_Deregister(void) {
  pan_cb.pan_bridge_req_cb = NULL;
  pan_cb.pan_data_buf_ind_cb = NULL;
  pan_cb.pan_data_ind_cb = NULL;
  pan_cb.pan_conn_state_cb = NULL;
  pan_cb.pan_pfilt_ind_cb = NULL;
  pan_cb.pan_mfilt_ind_cb = NULL;

  PAN_SetRole(PAN_ROLE_INACTIVE, std::string(), std::string());
  BNEP_Deregister();

  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Unregistered");
}

/*******************************************************************************
 *
 * Function         PAN_SetRole
 *
 * Description      This function is called by the application to set the PAN
 *                  profile role. This should be called after PAN_Register.
 *                  This can be called any time to change the PAN role
 *
 * Parameters:      role        - is bit map of roles to be active
 *                                      PAN_ROLE_CLIENT is for PANU role
 *                                      PAN_ROLE_NAP_SERVER is for NAP role
 *                  p_user_name - Service name for PANU role
 *                  p_nap_name  - Service name for NAP role
 *                                      Can be NULL if user wants the default
 *
 * Returns          PAN_SUCCESS     - if the role is set successfully
 *                  PAN_FAILURE     - if the role is not valid
 *
 ******************************************************************************/
tPAN_RESULT PAN_SetRole(uint8_t role, std::string p_user_name,
                        std::string p_nap_name) {
  /* Check if it is a shutdown request */
  if (role == PAN_ROLE_INACTIVE) {
    pan_close_all_connections();
    pan_cb.role = role;
    user_service_name.clear();
    nap_service_name.clear();
    return PAN_SUCCESS;
  }

  const char* p_desc;

  /* If the role is not a valid combination reject it */
  if ((!(role & (PAN_ROLE_CLIENT | PAN_ROLE_NAP_SERVER))) &&
      role != PAN_ROLE_INACTIVE) {
    log::error("PAN role {} is invalid", role);
    return PAN_FAILURE;
  }

  /* If the current active role is same as the role being set do nothing */
  if (pan_cb.role == role) {
    log::verbose("PAN role already was set to: {}", role);
    return PAN_SUCCESS;
  }

  /* Register all the roles with SDP */
  log::verbose("PAN_SetRole() called with role 0x{:x}", role);
  if (role & PAN_ROLE_NAP_SERVER) {
    /* Check the service name */
    if (p_nap_name.empty())
      p_nap_name = std::string(PAN_NAP_DEFAULT_SERVICE_NAME);

    /* Registering for NAP service with SDP */
    p_desc = PAN_NAP_DEFAULT_DESCRIPTION;

    if (pan_cb.pan_nap_sdp_handle != 0)
      get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
          pan_cb.pan_nap_sdp_handle);

    pan_cb.pan_nap_sdp_handle =
        pan_register_with_sdp(UUID_SERVCLASS_NAP, p_nap_name.c_str(), p_desc);
    bta_sys_add_uuid(UUID_SERVCLASS_NAP);
    nap_service_name = p_nap_name;
  }
  /* If the NAP role is already active and now being cleared delete the record
   */
  else if (pan_cb.role & PAN_ROLE_NAP_SERVER) {
    if (pan_cb.pan_nap_sdp_handle != 0) {
      get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
          pan_cb.pan_nap_sdp_handle);
      pan_cb.pan_nap_sdp_handle = 0;
      bta_sys_remove_uuid(UUID_SERVCLASS_NAP);
      nap_service_name.clear();
    }
  }

  if (role & PAN_ROLE_CLIENT) {
    /* Check the service name */
    if (p_user_name.empty()) p_user_name = PAN_PANU_DEFAULT_SERVICE_NAME;

    /* Registering for PANU service with SDP */
    p_desc = PAN_PANU_DEFAULT_DESCRIPTION;
    if (pan_cb.pan_user_sdp_handle != 0)
      get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
          pan_cb.pan_user_sdp_handle);

    pan_cb.pan_user_sdp_handle =
        pan_register_with_sdp(UUID_SERVCLASS_PANU, p_user_name.c_str(), p_desc);
    bta_sys_add_uuid(UUID_SERVCLASS_PANU);
    user_service_name = p_user_name;
  }
  /* If the PANU role is already active and now being cleared delete the record
   */
  else if (pan_cb.role & PAN_ROLE_CLIENT) {
    if (pan_cb.pan_user_sdp_handle != 0) {
      get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(
          pan_cb.pan_user_sdp_handle);
      pan_cb.pan_user_sdp_handle = 0;
      bta_sys_remove_uuid(UUID_SERVCLASS_PANU);
      user_service_name.clear();
    }
  }

  pan_cb.role = role;
  log::verbose("PAN role set to: {}", role);

  BTM_LogHistory(kBtmLogTag, RawAddress::kEmpty, "Role change",
                 base::StringPrintf("role:0x%x", role));
  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_Connect
 *
 * Description      This function is called by the application to initiate a
 *                  connection to the remote device
 *
 * Parameters:      rem_bda     - BD Addr of the remote device
 *                  src_role    - Role of the local device for the connection
 *                  dst_role    - Role of the remote device for the connection
 *                                      PAN_ROLE_CLIENT is for PANU role
 *                                      PAN_ROLE_NAP_SERVER is for NAP role
 *                  *handle     - Pointer for returning Handle to the connection
 *
 * Returns          PAN_SUCCESS      - if the connection is initiated
 *                                     successfully
 *                  PAN_NO_RESOURCES - resources are not sufficent
 *                  PAN_FAILURE      - if the connection cannot be initiated
 *                                     this can be because of the combination of
 *                                     src and dst roles may not be valid or
 *                                     allowed at that point of time
 *
 ******************************************************************************/
tPAN_RESULT PAN_Connect(const RawAddress& rem_bda, tPAN_ROLE src_role,
                        tPAN_ROLE dst_role, uint16_t* handle) {
  uint32_t mx_chan_id;

  /*
  ** Initialize the handle so that in case of failure return values
  ** the profile will not get confused
  */
  *handle = BNEP_INVALID_HANDLE;

  /* Check if PAN is active or not */
  if (!(pan_cb.role & src_role)) {
    log::error("PAN is not active for the role {}", src_role);
    return PAN_FAILURE;
  }

  /* Validate the parameters before proceeding */
  if ((src_role != PAN_ROLE_CLIENT && src_role != PAN_ROLE_NAP_SERVER) ||
      (dst_role != PAN_ROLE_CLIENT && dst_role != PAN_ROLE_NAP_SERVER)) {
    log::error("Either source {} or destination role {} is invalid", src_role,
               dst_role);
    return PAN_FAILURE;
  }

  /* Check if connection exists for this remote device */
  tPAN_CONN* pcb = pan_get_pcb_by_addr(rem_bda);

  uint16_t src_uuid, dst_uuid;
  /* If we are PANU for this role validate destination role */
  if (src_role == PAN_ROLE_CLIENT) {
    if ((pan_cb.num_conns > 1) || (pan_cb.num_conns && (!pcb))) {
      /*
      ** If the request is not for existing connection reject it
      ** because if there is already a connection we cannot accept
      ** another connection in PANU role
      */
      log::error(
          "Cannot make PANU connections when there are more than one "
          "connection");
      return PAN_INVALID_SRC_ROLE;
    }

    src_uuid = UUID_SERVCLASS_PANU;
    if (dst_role == PAN_ROLE_CLIENT) {
      dst_uuid = UUID_SERVCLASS_PANU;
    } else {
      dst_uuid = UUID_SERVCLASS_NAP;
    }
    mx_chan_id = dst_uuid;
  }
  /* If destination is PANU role validate source role */
  else if (dst_role == PAN_ROLE_CLIENT) {
    if (pan_cb.num_conns && pan_cb.active_role == PAN_ROLE_CLIENT && !pcb) {
      log::error("Device already have a connection in PANU role");
      return PAN_INVALID_SRC_ROLE;
    }

    dst_uuid = UUID_SERVCLASS_PANU;
    src_uuid = UUID_SERVCLASS_NAP;
    mx_chan_id = src_uuid;
  }
  /* The role combination is not valid */
  else {
    log::error("Source {} and Destination roles {} are not valid combination",
               src_role, dst_role);
    return PAN_FAILURE;
  }

  /* Allocate control block and initiate connection */
  if (!pcb) pcb = pan_allocate_pcb(rem_bda, BNEP_INVALID_HANDLE);
  if (!pcb) {
    log::error("PAN Connection failed because of no resources");
    return PAN_NO_RESOURCES;
  }

  log::verbose("for BD Addr: {}", ADDRESS_TO_LOGGABLE_STR(rem_bda));
  if (pcb->con_state == PAN_STATE_IDLE) {
    pan_cb.num_conns++;
  } else if (pcb->con_state == PAN_STATE_CONNECTED) {
    pcb->con_flags |= PAN_FLAGS_CONN_COMPLETED;
  } else
    /* PAN connection is still in progress */
    return PAN_WRONG_STATE;

  pcb->con_state = PAN_STATE_CONN_START;
  pcb->prv_src_uuid = pcb->src_uuid;
  pcb->prv_dst_uuid = pcb->dst_uuid;

  pcb->src_uuid = src_uuid;
  pcb->dst_uuid = dst_uuid;

  tBNEP_RESULT ret =
      BNEP_Connect(rem_bda, Uuid::From16Bit(src_uuid),
                   Uuid::From16Bit(dst_uuid), &(pcb->handle), mx_chan_id);
  if (ret != BNEP_SUCCESS) {
    pan_release_pcb(pcb);
    return (tPAN_RESULT)ret;
  }

  log::verbose("PAN_Connect() current active role set to {}", src_role);
  pan_cb.prv_active_role = pan_cb.active_role;
  pan_cb.active_role = src_role;
  *handle = pcb->handle;

  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_Disconnect
 *
 * Description      This is used to disconnect the connection
 *
 * Parameters:      handle           - handle for the connection
 *
 * Returns          PAN_SUCCESS      - if the connection is closed successfully
 *                  PAN_FAILURE      - if the connection is not found or
 *                                           there is an error in disconnecting
 *
 ******************************************************************************/
tPAN_RESULT PAN_Disconnect(uint16_t handle) {
  tPAN_CONN* pcb;
  tBNEP_RESULT result;

  /* Check if the connection exists */
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN connection not found for the handle {}", handle);
    return PAN_FAILURE;
  }

  result = BNEP_Disconnect(pcb->handle);
  if (pcb->con_state != PAN_STATE_IDLE) pan_cb.num_conns--;

  if (pan_cb.pan_bridge_req_cb && pcb->src_uuid == UUID_SERVCLASS_NAP)
    (*pan_cb.pan_bridge_req_cb)(pcb->rem_bda, false);

  BTM_LogHistory(kBtmLogTag, pcb->rem_bda, "Disconnect");

  pan_release_pcb(pcb);

  if (result != BNEP_SUCCESS) {
    log::verbose("Error in closing PAN connection");
    return PAN_FAILURE;
  }

  log::verbose("PAN connection closed");
  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_Write
 *
 * Description      This sends data over the PAN connections. If this is called
 *                  on GN or NAP side and the packet is multicast or broadcast
 *                  it will be sent on all the links. Otherwise the correct link
 *                  is found based on the destination address and forwarded on
 *                  it.
 *
 * Parameters:      handle   - handle for the connection
 *                  dst      - MAC or BD Addr of the destination device
 *                  src      - MAC or BD Addr of the source who sent this packet
 *                  protocol - protocol of the ethernet packet like IP or ARP
 *                  p_data   - pointer to the data
 *                  len      - length of the data
 *                  ext      - to indicate that extension headers present
 *
 * Returns          PAN_SUCCESS       - if the data is sent successfully
 *                  PAN_FAILURE       - if the connection is not found or
 *                                           there is an error in sending data
 *
 ******************************************************************************/
tPAN_RESULT PAN_Write(uint16_t handle, const RawAddress& dst,
                      const RawAddress& src, uint16_t protocol, uint8_t* p_data,
                      uint16_t len, bool ext) {
  if (pan_cb.role == PAN_ROLE_INACTIVE || !pan_cb.num_conns) {
    log::error("PAN is not active, data write failed.");
    return PAN_FAILURE;
  }

  // If the packet is broadcast or multicast, we're going to have to create
  // a copy of the packet for each connection. We can save one extra copy
  // by fast-pathing here and calling BNEP_Write instead of placing the packet
  // in a BT_HDR buffer, calling BNEP_Write, and then freeing the buffer.
  if (dst.address[0] & 0x01) {
    int i;
    for (i = 0; i < MAX_PAN_CONNS; ++i) {
      if (pan_cb.pcb[i].con_state == PAN_STATE_CONNECTED)
        BNEP_Write(pan_cb.pcb[i].handle, dst, p_data, len, protocol, src, ext);
    }
    return PAN_SUCCESS;
  }

  BT_HDR* buffer = (BT_HDR*)osi_malloc(PAN_BUF_SIZE);
  buffer->len = len;
  buffer->offset = PAN_MINIMUM_OFFSET;
  memcpy((uint8_t*)buffer + sizeof(BT_HDR) + buffer->offset, p_data,
         buffer->len);

  return PAN_WriteBuf(handle, dst, src, protocol, buffer, ext);
}

/*******************************************************************************
 *
 * Function         PAN_WriteBuf
 *
 * Description      This sends data over the PAN connections. If this is called
 *                  on GN or NAP side and the packet is multicast or broadcast
 *                  it will be sent on all the links. Otherwise the correct link
 *                  is found based on the destination address and forwarded on
 *                  it. If the return value is not PAN_SUCCESS, the application
 *                  should take care of releasing the message buffer.
 *
 * Parameters:      handle   - handle for the connection
 *                  dst      - MAC or BD Addr of the destination device
 *                  src      - MAC or BD Addr of the source who sent this packet
 *                  protocol - protocol of the ethernet packet like IP or ARP
 *                  p_buf    - pointer to the data buffer
 *                  ext      - to indicate that extension headers present
 *
 * Returns          PAN_SUCCESS       - if the data is sent successfully
 *                  PAN_FAILURE       - if the connection is not found or
 *                                           there is an error in sending data
 *
 ******************************************************************************/
tPAN_RESULT PAN_WriteBuf(uint16_t handle, const RawAddress& dst,
                         const RawAddress& src, uint16_t protocol,
                         BT_HDR* p_buf, bool ext) {
  tPAN_CONN* pcb;
  uint16_t i;
  tBNEP_RESULT result;

  if (pan_cb.role == PAN_ROLE_INACTIVE || (!(pan_cb.num_conns))) {
    log::error("PAN is not active Data write failed");
    osi_free(p_buf);
    return PAN_FAILURE;
  }

  /* Check if it is broadcast or multicast packet */
  if (dst.address[0] & 0x01) {
    uint8_t* data = (uint8_t*)p_buf + sizeof(BT_HDR) + p_buf->offset;
    for (i = 0; i < MAX_PAN_CONNS; ++i) {
      if (pan_cb.pcb[i].con_state == PAN_STATE_CONNECTED)
        BNEP_Write(pan_cb.pcb[i].handle, dst, data, p_buf->len, protocol, src,
                   ext);
    }
    osi_free(p_buf);
    return PAN_SUCCESS;
  }

  /* Check if the data write is on PANU side */
  if (pan_cb.active_role == PAN_ROLE_CLIENT) {
    /* Data write is on PANU connection */
    for (i = 0; i < MAX_PAN_CONNS; i++) {
      if (pan_cb.pcb[i].con_state == PAN_STATE_CONNECTED &&
          pan_cb.pcb[i].src_uuid == UUID_SERVCLASS_PANU)
        break;
    }

    if (i == MAX_PAN_CONNS) {
      log::error("PAN Don't have any user connections");
      osi_free(p_buf);
      return PAN_FAILURE;
    }

    result =
        BNEP_WriteBuf(pan_cb.pcb[i].handle, dst, p_buf, protocol, src, ext);
    if (result == BNEP_IGNORE_CMD) {
      log::verbose("PAN ignored data write for PANU connection");
      return (tPAN_RESULT)result;
    } else if (result != BNEP_SUCCESS) {
      log::error("PAN failed to write data for the PANU connection");
      return (tPAN_RESULT)result;
    }

    pan_cb.pcb[i].write.octets += p_buf->len;
    pan_cb.pcb[i].write.packets++;

    log::verbose("PAN successfully wrote data for the PANU connection");
    return PAN_SUCCESS;
  }

  /* findout to which connection the data is meant for */
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN Buf write for wrong handle");
    osi_free(p_buf);
    return PAN_FAILURE;
  }

  if (pcb->con_state != PAN_STATE_CONNECTED) {
    log::error("PAN Buf write when conn is not active");
    pcb->write.drops++;
    osi_free(p_buf);
    return PAN_FAILURE;
  }

  uint16_t len = p_buf->len;
  result = BNEP_WriteBuf(pcb->handle, dst, p_buf, protocol, src, ext);
  if (result == BNEP_IGNORE_CMD) {
    log::verbose("PAN ignored data buf write to PANU");
    pcb->write.errors++;
    return PAN_IGNORE_CMD;
  } else if (result != BNEP_SUCCESS) {
    log::error("PAN failed to send data buf to the PANU");
    pcb->write.errors++;
    return (tPAN_RESULT)result;
  }

  pcb->write.octets += len;
  pcb->write.packets++;

  log::verbose("PAN successfully sent data buf to the PANU");

  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_SetProtocolFilters
 *
 * Description      This function is used to set protocol filters on the peer
 *
 * Parameters:      handle      - handle for the connection
 *                  num_filters - number of protocol filter ranges
 *                  start       - array of starting protocol numbers
 *                  end         - array of ending protocol numbers
 *
 *
 * Returns          PAN_SUCCESS    if protocol filters are set successfully
 *                  PAN_FAILURE    if connection not found or error in setting
 *
 ******************************************************************************/
tPAN_RESULT PAN_SetProtocolFilters(uint16_t handle, uint16_t num_filters,
                                   uint16_t* p_start_array,
                                   uint16_t* p_end_array) {
  tPAN_CONN* pcb;

  /* Check if the connection exists */
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN connection not found for the handle {}", handle);
    return PAN_FAILURE;
  }

  tBNEP_RESULT result = BNEP_SetProtocolFilters(pcb->handle, num_filters,
                                                p_start_array, p_end_array);
  if (result != BNEP_SUCCESS) {
    log::error("PAN failed to set protocol filters for handle {}", handle);
    return (tPAN_RESULT)result;
  }

  log::verbose("PAN successfully sent protocol filters for handle {}", handle);
  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_SetMulticastFilters
 *
 * Description      This function is used to set multicast filters on the peer
 *
 * Parameters:      handle      - handle for the connection
 *                  num_filters - number of multicast filter ranges
 *                  start       - array of starting multicast filter addresses
 *                  end         - array of ending multicast filter addresses
 *
 *
 * Returns          PAN_SUCCESS    if multicast filters are set successfully
 *                  PAN_FAILURE    if connection not found or error in setting
 *
 ******************************************************************************/
tPAN_RESULT PAN_SetMulticastFilters(uint16_t handle, uint16_t num_mcast_filters,
                                    uint8_t* p_start_array,
                                    uint8_t* p_end_array) {
  tPAN_CONN* pcb;

  /* Check if the connection exists */
  pcb = pan_get_pcb_by_handle(handle);
  if (!pcb) {
    log::error("PAN connection not found for the handle {}", handle);
    return PAN_FAILURE;
  }

  tBNEP_RESULT result = BNEP_SetMulticastFilters(pcb->handle, num_mcast_filters,
                                                 p_start_array, p_end_array);
  if (result != BNEP_SUCCESS) {
    log::error("PAN failed to set multicast filters for handle {}", handle);
    return (tPAN_RESULT)result;
  }

  log::verbose("PAN successfully sent multicast filters for handle {}", handle);
  return PAN_SUCCESS;
}

/*******************************************************************************
 *
 * Function         PAN_Init
 *
 * Description      This function initializes the PAN module variables
 *
 * Parameters:      none
 *
 * Returns          none
 *
 ******************************************************************************/
void PAN_Init(void) {
  memset(&pan_cb, 0, sizeof(tPAN_CB));
}

#define DUMPSYS_TAG "shim::legacy::pan"
void PAN_Dumpsys(int fd) {
  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);

  LOG_DUMPSYS(fd, "Connections:%hhu roles configured:%s current:%s previous:%s",
              pan_cb.num_conns, pan_role_to_text(pan_cb.role).c_str(),
              pan_role_to_text(pan_cb.active_role).c_str(),
              pan_role_to_text(pan_cb.prv_active_role).c_str());

  if (!user_service_name.empty())
    LOG_DUMPSYS(fd, "service_name_user:\"%s\"", user_service_name.c_str());
  if (!gn_service_name.empty())
    LOG_DUMPSYS(fd, "service_name_gn:\"%s\"", gn_service_name.c_str());
  if (!nap_service_name.empty())
    LOG_DUMPSYS(fd, "service_name_nap:\"%s\"", nap_service_name.c_str());

  const tPAN_CONN* pcb = &pan_cb.pcb[0];
  for (int i = 0; i < MAX_PAN_CONNS; i++, pcb++) {
    if (pcb->con_state == PAN_STATE_IDLE) continue;
    LOG_DUMPSYS(fd, "  Id:%d peer:%s", i, ADDRESS_TO_LOGGABLE_CSTR(pcb->rem_bda));
    LOG_DUMPSYS(
        fd,
        "    rx_packets:%-5lu rx_octets:%-8lu rx_errors:%-5lu rx_drops:%-5lu",
        (unsigned long)pcb->read.packets, (unsigned long)pcb->read.octets,
        (unsigned long)pcb->read.errors, (unsigned long)pcb->read.drops);
    LOG_DUMPSYS(
        fd,
        "    tx_packets:%-5lu tx_octets:%-8lu tx_errors:%-5lu tx_drops:%-5lu",
        (unsigned long)pcb->write.packets, (unsigned long)pcb->write.octets,
        (unsigned long)pcb->write.errors, (unsigned long)pcb->write.drops);
    LOG_DUMPSYS(fd,
                "    src_uuid:0x%04x[prev:0x%04x] dst_uuid:0x%04x[prev:0x%04x] "
                "bad_pkts:%hu",
                pcb->src_uuid, pcb->dst_uuid, pcb->prv_src_uuid,
                pcb->prv_dst_uuid, pcb->bad_pkts_rcvd);
  }
}
#undef DUMPSYS_TAG
