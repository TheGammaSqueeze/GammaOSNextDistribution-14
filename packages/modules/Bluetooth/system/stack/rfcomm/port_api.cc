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
 *  this file contains the Serial Port API code
 *
 ******************************************************************************/

#define LOG_TAG "bt_port_api"

#include "stack/include/port_api.h"

#include <base/logging.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "internal_include/bt_target.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/mutex.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/rfcomm/rfc_int.h"
#include "types/raw_address.h"

using namespace bluetooth;

/* Mapping from PORT_* result codes to human readable strings. */
static const char* result_code_strings[] = {"Success",
                                            "Unknown error",
                                            "Already opened",
                                            "Command pending",
                                            "App not registered",
                                            "No memory",
                                            "No resources",
                                            "Bad BD address",
                                            "Unspecified error",
                                            "Bad handle",
                                            "Not opened",
                                            "Line error",
                                            "Start failed",
                                            "Parameter negotiation failed",
                                            "Port negotiation failed",
                                            "Sec failed",
                                            "Peer connection failed",
                                            "Peer failed",
                                            "Peer timeout",
                                            "Closed",
                                            "TX full",
                                            "Local closed",
                                            "Local timeout",
                                            "TX queue disabled",
                                            "Page timeout",
                                            "Invalid SCN",
                                            "Unknown result code"};

/*******************************************************************************
 *
 * Function         RFCOMM_CreateConnectionWithSecurity
 *
 * Description      RFCOMM_CreateConnectionWithSecurity function is used from
 *the application to establish serial port connection to the peer device, or
 *allow RFCOMM to accept a connection from the peer application.
 *
 * Parameters:      scn          - Service Channel Number as registered with
 *                                 the SDP (server) or obtained using SDP from
 *                                 the peer device (client).
 *                  is_server    - true if requesting application is a server
 *                  mtu          - Maximum frame size the application can accept
 *                  bd_addr      - address of the peer (client)
 *                  p_handle     - OUT pointer to the handle.
 *                  p_mgmt_cb    - pointer to callback function to receive
 *                                 connection up/down events.
 *                  sec_mask     - bitmask of BTM_SEC_* values indicating the
 *                                 minimum security requirements for this
 *connection Notes:
 *
 * Server can call this function with the same scn parameter multiple times if
 * it is ready to accept multiple simulteneous connections.
 *
 * DLCI for the connection is (scn * 2 + 1) if client originates connection on
 * existing none initiator multiplexer channel.  Otherwise it is (scn * 2).
 * For the server DLCI can be changed later if client will be calling it using
 * (scn * 2 + 1) dlci.
 *
 ******************************************************************************/
int RFCOMM_CreateConnectionWithSecurity(uint16_t uuid, uint8_t scn,
                                        bool is_server, uint16_t mtu,
                                        const RawAddress& bd_addr,
                                        uint16_t* p_handle,
                                        tPORT_CALLBACK* p_mgmt_cb,
                                        uint16_t sec_mask) {
  *p_handle = 0;

  if ((scn == 0) || (scn > RFCOMM_MAX_SCN)) {
    // Server Channel Number (SCN) should be in range [1, 30]
    log::error("Invalid SCN, bd_addr={}, scn={}, is_server={}, mtu={}, uuid={}",
               ADDRESS_TO_LOGGABLE_STR(bd_addr), static_cast<int>(scn),
               is_server, static_cast<int>(mtu), loghex(uuid));
    return (PORT_INVALID_SCN);
  }

  // For client that originates connection on the existing none initiator
  // multiplexer channel, DLCI should be odd.
  uint8_t dlci;
  tRFC_MCB* p_mcb = port_find_mcb(bd_addr);
  if (p_mcb && !p_mcb->is_initiator && !is_server) {
    dlci = static_cast<uint8_t>((scn << 1) + 1);
  } else {
    dlci = (scn << 1);
  }

  // On the client side, do not allow the same (dlci, bd_addr) to be opened
  // twice by application
  tPORT* p_port;
  if (!is_server) {
    p_port = port_find_port(dlci, bd_addr);
    if (p_port != nullptr) {
      // if existing port is also a client port, error out
      if (!p_port->is_server) {
        log::error(
            "already at opened state {}, RFC_state={}, MCB_state={}, "
            "bd_addr={}, scn={}, is_server={}, mtu={}, uuid={}, dlci={}, "
            "p_mcb={}, port={}",
            static_cast<int>(p_port->state),
            static_cast<int>(p_port->rfc.state),
            (p_port->rfc.p_mcb ? p_port->rfc.p_mcb->state : 0),
            ADDRESS_TO_LOGGABLE_STR(bd_addr), scn, is_server, mtu, loghex(uuid),
            dlci, fmt::ptr(p_mcb), p_port->handle);
        *p_handle = p_port->handle;
        return (PORT_ALREADY_OPENED);
      }
    }
  }

  // On the server side, always allocate a new port.
  p_port = port_allocate_port(dlci, bd_addr);
  if (p_port == nullptr) {
    log::error(
        "no resources, bd_addr={}, scn={}, is_server={}, mtu={}, uuid={}, "
        "dlci={}",
        ADDRESS_TO_LOGGABLE_STR(bd_addr), scn, is_server, mtu, loghex(uuid),
        dlci);
    return PORT_NO_RESOURCES;
  }
  p_port->sec_mask = sec_mask;
  *p_handle = p_port->handle;

  // Get default signal state
  switch (uuid) {
    case UUID_PROTOCOL_OBEX:
      p_port->default_signal_state = PORT_OBEX_DEFAULT_SIGNAL_STATE;
      break;
    case UUID_SERVCLASS_SERIAL_PORT:
      p_port->default_signal_state = PORT_SPP_DEFAULT_SIGNAL_STATE;
      break;
    case UUID_SERVCLASS_LAN_ACCESS_USING_PPP:
      p_port->default_signal_state = PORT_PPP_DEFAULT_SIGNAL_STATE;
      break;
    case UUID_SERVCLASS_DIALUP_NETWORKING:
    case UUID_SERVCLASS_FAX:
      p_port->default_signal_state = PORT_DUN_DEFAULT_SIGNAL_STATE;
      break;
    default:
      p_port->default_signal_state =
          (PORT_DTRDSR_ON | PORT_CTSRTS_ON | PORT_DCD_ON);
      break;
  }

  // Assign port specific values
  p_port->state = PORT_CONNECTION_STATE_OPENING;
  p_port->uuid = uuid;
  p_port->is_server = is_server;
  p_port->scn = scn;
  p_port->ev_mask = 0;

  // Find MTU
  // If the MTU is not specified (0), keep MTU decision until the PN frame has
  // to be send at that time connection should be established and we will know
  // for sure our prefered MTU
  uint16_t rfcomm_mtu = L2CAP_MTU_SIZE - RFCOMM_DATA_OVERHEAD;
  if (mtu) {
    p_port->mtu = (mtu < rfcomm_mtu) ? mtu : rfcomm_mtu;
  } else {
    p_port->mtu = rfcomm_mtu;
  }

  // Other states
  // server doesn't need to release port when closing
  if (is_server) {
    p_port->keep_port_handle = true;
    // keep mtu that user asked, p_port->mtu could be updated during param
    // negotiation
    p_port->keep_mtu = p_port->mtu;
  }
  p_port->local_ctrl.modem_signal = p_port->default_signal_state;
  p_port->local_ctrl.fc = false;
  p_port->p_mgmt_callback = p_mgmt_cb;
  p_port->bd_addr = bd_addr;

  log::info(
      "bd_addr={}, scn={}, is_server={}, mtu={}, uuid={}, dlci={}, "
      "signal_state={}, p_port={}",
      ADDRESS_TO_LOGGABLE_STR(bd_addr), scn, is_server, mtu, loghex(uuid), dlci,
      loghex(p_port->default_signal_state), fmt::ptr(p_port));

  // If this is not initiator of the connection need to just wait
  if (p_port->is_server) {
    return (PORT_SUCCESS);
  }

  // Open will be continued after security checks are passed
  return port_open_continue(p_port);
}

/*******************************************************************************
 *
 * Function         RFCOMM_ControlReqFromBTSOCK
 *
 * Description      Send control parameters to the peer.
 *                  So far only for qualification use.
 *                  RFCOMM layer starts the control request only when it is the
 *                  client. This API allows the host to start the control
 *                  request while it works as a RFCOMM server.
 *
 * Parameters:      dlci             - the DLCI to send the MSC command
 *                  bd_addr          - bd_addr of the peer
 *                  modem_signal     - [DTR/DSR | RTS/CTS | RI | DCD]
 *                  break_signal     - 0-3 s in steps of 200 ms
 *                  discard_buffers  - 0 for do not discard, 1 for discard
 *                  break_signal_seq - ASAP or in sequence
 *                  fc               - true when the device is unable to accept
 *                                     frames
 *
 ******************************************************************************/
int RFCOMM_ControlReqFromBTSOCK(uint8_t dlci, const RawAddress& bd_addr,
                                uint8_t modem_signal, uint8_t break_signal,
                                uint8_t discard_buffers,
                                uint8_t break_signal_seq, bool fc) {
  tRFC_MCB* p_mcb = port_find_mcb(bd_addr);
  if (!p_mcb) {
    return PORT_BAD_BD_ADDR;
  }
  tPORT* p_port = port_find_mcb_dlci_port(p_mcb, dlci);
  if (!p_port) {
    return PORT_NOT_OPENED;
  }
  p_port->local_ctrl.modem_signal = modem_signal;
  p_port->local_ctrl.break_signal = break_signal;
  p_port->local_ctrl.discard_buffers = discard_buffers;
  p_port->local_ctrl.break_signal_seq = break_signal_seq;
  p_port->local_ctrl.fc = fc;
  RFCOMM_ControlReq(p_mcb, dlci, &p_port->local_ctrl);
  return PORT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         RFCOMM_RemoveConnection
 *
 * Description      This function is called to close the specified connection.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *
 ******************************************************************************/
int RFCOMM_RemoveConnection(uint16_t handle) {
  tPORT* p_port;

  log::verbose("RFCOMM_RemoveConnection() handle:{}", handle);

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    log::error("RFCOMM_RemoveConnection() BAD handle:{}", handle);
    return (PORT_BAD_HANDLE);
  }
  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    log::verbose("RFCOMM_RemoveConnection() Not opened:{}", handle);
    return (PORT_SUCCESS);
  }

  p_port->state = PORT_CONNECTION_STATE_CLOSING;

  port_start_close(p_port);

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         RFCOMM_RemoveServer
 *
 * Description      This function is called to close the server port.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *
 ******************************************************************************/
int RFCOMM_RemoveServer(uint16_t handle) {
  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    log::error("bad handle {}", handle);
    return (PORT_BAD_HANDLE);
  }
  tPORT* p_port = &rfc_cb.port.port[handle - 1];

  /* Do not report any events to the client any more. */
  p_port->p_mgmt_callback = nullptr;

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    log::debug("handle {} not opened", handle);
    return (PORT_SUCCESS);
  }
  log::info("handle={}", handle);

  /* this port will be deallocated after closing */
  p_port->keep_port_handle = false;
  p_port->state = PORT_CONNECTION_STATE_CLOSING;

  port_start_close(p_port);

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_SetEventCallback
 *
 * Description      This function is called to provide an address of the
 *                  function which will be called when one of the events
 *                  specified in the mask occures.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_callback - address of the callback function which should
 *                               be called from the RFCOMM when an event
 *                               specified in the mask occures.
 *
 *
 ******************************************************************************/
int PORT_SetEventCallback(uint16_t port_handle, tPORT_CALLBACK* p_port_cb) {
  tPORT* p_port;

  /* Check if handle is valid to avoid crashing */
  if ((port_handle == 0) || (port_handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[port_handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  log::verbose("PORT_SetEventCallback() handle:{}", port_handle);

  p_port->p_callback = p_port_cb;

  return (PORT_SUCCESS);
}
/*******************************************************************************
 *
 * Function         PORT_ClearKeepHandleFlag
 *
 * Description      Clear the keep handle flag, which will cause not to keep the
 *                  port handle open when closed
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *
 ******************************************************************************/

int PORT_ClearKeepHandleFlag(uint16_t port_handle) {
  tPORT* p_port;

  /* Check if handle is valid to avoid crashing */
  if ((port_handle == 0) || (port_handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[port_handle - 1];
  p_port->keep_port_handle = 0;
  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_SetCODataCallback
 *
 * Description      This function is when a data packet is received
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_callback - address of the callback function which should
 *                               be called from the RFCOMM when data packet
 *                               is received.
 *
 *
 ******************************************************************************/
int PORT_SetDataCOCallback(uint16_t port_handle,
                           tPORT_DATA_CO_CALLBACK* p_port_cb) {
  tPORT* p_port;

  log::verbose("PORT_SetDataCOCallback() handle:{} cb 0x{}", port_handle,
               fmt::ptr(p_port_cb));

  /* Check if handle is valid to avoid crashing */
  if ((port_handle == 0) || (port_handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[port_handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  p_port->p_data_co_callback = p_port_cb;

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_SetEventMask
 *
 * Description      This function is called to close the specified connection.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  mask   - Bitmask of the events the host is interested in
 *
 ******************************************************************************/
int PORT_SetEventMask(uint16_t port_handle, uint32_t mask) {
  tPORT* p_port;

  log::verbose("PORT_SetEventMask() handle:{} mask:0x{:x}", port_handle, mask);

  /* Check if handle is valid to avoid crashing */
  if ((port_handle == 0) || (port_handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[port_handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  p_port->ev_mask = mask;

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_CheckConnection
 *
 * Description      This function returns PORT_SUCCESS if connection referenced
 *                  by handle is up and running
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  bd_addr    - OUT bd_addr of the peer
 *                  p_lcid     - OUT L2CAP's LCID
 *
 ******************************************************************************/
int PORT_CheckConnection(uint16_t handle, RawAddress* bd_addr,
                         uint16_t* p_lcid) {
  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }
  tPORT* p_port = &rfc_cb.port.port[handle - 1];
  log::verbose(
      "handle={}, in_use={}, port_state={}, p_mcb={}, peer_ready={}, "
      "rfc_state={}",
      handle, p_port->in_use, p_port->state, fmt::ptr(p_port->rfc.p_mcb),
      (p_port->rfc.p_mcb ? p_port->rfc.p_mcb->peer_ready : -1),
      p_port->rfc.state);

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  if (!p_port->rfc.p_mcb || !p_port->rfc.p_mcb->peer_ready ||
      (p_port->rfc.state != RFC_STATE_OPENED)) {
    return (PORT_LINE_ERR);
  }

  *bd_addr = p_port->rfc.p_mcb->bd_addr;
  if (p_lcid) *p_lcid = p_port->rfc.p_mcb->lcid;

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_IsOpening
 *
 * Description      This function returns true if there is any RFCOMM connection
 *                  opening in process.
 *
 * Parameters:      true if any connection opening is found
 *                  bd_addr    - bd_addr of the peer
 *
 ******************************************************************************/
bool PORT_IsOpening(RawAddress* bd_addr) {
  /* Check for any rfc_mcb which is in the middle of opening. */
  for (auto& multiplexer_cb : rfc_cb.port.rfc_mcb) {
    if ((multiplexer_cb.state > RFC_MX_STATE_IDLE) &&
        (multiplexer_cb.state < RFC_MX_STATE_CONNECTED)) {
      *bd_addr = multiplexer_cb.bd_addr;
      log::info(
          "Found a rfc_mcb in the middle of opening a port, returning true");
      return true;
    }

    if (multiplexer_cb.state == RFC_MX_STATE_CONNECTED) {
      bool found_port = false;
      tPORT* p_port = nullptr;

      for (tPORT& port : rfc_cb.port.port) {
        if (port.rfc.p_mcb == &multiplexer_cb) {
          found_port = true;
          p_port = &port;
          break;
        }
      }

      log::info("RFC_MX_STATE_CONNECTED, found_port={}, tRFC_PORT_STATE={}",
                found_port, p_port != nullptr ? p_port->rfc.state : 0);
      if ((!found_port) ||
          (found_port && (p_port->rfc.state < RFC_STATE_OPENED))) {
        /* Port is not established yet. */
        *bd_addr = multiplexer_cb.bd_addr;
        log::info(
            "In RFC_MX_STATE_CONNECTED but port is not established yet, "
            "returning true");
        return true;
      }
    }
  }
  log::info("false");
  return false;
}

/*******************************************************************************
 *
 * Function         PORT_SetState
 *
 * Description      This function configures connection according to the
 *                  specifications in the tPORT_STATE structure.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_settings - Pointer to a tPORT_STATE structure containing
 *                               configuration information for the connection.
 *
 *
 ******************************************************************************/
int PORT_SetState(uint16_t handle, tPORT_STATE* p_settings) {
  tPORT* p_port;
  uint8_t baud_rate;

  log::verbose("PORT_SetState() handle:{}", handle);

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  if (p_port->line_status) {
    return (PORT_LINE_ERR);
  }

  log::verbose("PORT_SetState() handle:{} FC_TYPE:0x{:x}", handle,
               p_settings->fc_type);

  baud_rate = p_port->user_port_pars.baud_rate;
  p_port->user_port_pars = *p_settings;

  /* for now we've been asked to pass only baud rate */
  if (baud_rate != p_settings->baud_rate) {
    port_start_par_neg(p_port);
  }
  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_GetState
 *
 * Description      This function is called to fill tPORT_STATE structure
 *                  with the curremt control settings for the port
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_settings - Pointer to a tPORT_STATE structure in which
 *                               configuration information is returned.
 *
 ******************************************************************************/
int PORT_GetState(uint16_t handle, tPORT_STATE* p_settings) {
  tPORT* p_port;

  log::verbose("PORT_GetState() handle:{}", handle);

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  if (p_port->line_status) {
    return (PORT_LINE_ERR);
  }

  *p_settings = p_port->user_port_pars;
  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_FlowControl_MaxCredit
 *
 * Description      This function directs a specified connection to pass
 *                  flow control message to the peer device.  Enable flag passed
 *                  shows if port can accept more data. It also sends max credit
 *                  when data flow enabled
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  enable     - enables data flow
 *
 ******************************************************************************/

int PORT_FlowControl_MaxCredit(uint16_t handle, bool enable) {
  tPORT* p_port;
  bool old_fc;
  uint32_t events;

  log::verbose("PORT_FlowControl() handle:{} enable: {}", handle, enable);

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  if (!p_port->rfc.p_mcb) {
    return (PORT_NOT_OPENED);
  }

  p_port->rx.user_fc = !enable;

  if (p_port->rfc.p_mcb->flow == PORT_FC_CREDIT) {
    if (!p_port->rx.user_fc) {
      port_flow_control_peer(p_port, true, p_port->credit_rx);
    }
  } else {
    old_fc = p_port->local_ctrl.fc;

    /* FC is set if user is set or peer is set */
    p_port->local_ctrl.fc = (p_port->rx.user_fc | p_port->rx.peer_fc);

    if (p_port->local_ctrl.fc != old_fc) port_start_control(p_port);
  }

  /* Need to take care of the case when we could not deliver events */
  /* to the application because we were flow controlled */
  if (enable && (p_port->rx.queue_size != 0)) {
    events = PORT_EV_RXCHAR;
    if (p_port->rx_flag_ev_pending) {
      p_port->rx_flag_ev_pending = false;
      events |= PORT_EV_RXFLAG;
    }

    events &= p_port->ev_mask;
    if (p_port->p_callback && events) {
      p_port->p_callback(events, p_port->handle);
    }
  }
  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_ReadData
 *
 * Description      Normally not GKI aware application will call this function
 *                  after receiving PORT_EV_RXCHAR event.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_data      - Data area
 *                  max_len     - Byte count requested
 *                  p_len       - Byte count received
 *
 ******************************************************************************/
int PORT_ReadData(uint16_t handle, char* p_data, uint16_t max_len,
                  uint16_t* p_len) {
  tPORT* p_port;
  BT_HDR* p_buf;
  uint16_t count;

  log::verbose("PORT_ReadData() handle:{} max_len:{}", handle, max_len);

  /* Initialize this in case of an error */
  *p_len = 0;

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }

  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    return (PORT_NOT_OPENED);
  }

  if (p_port->state == PORT_CONNECTION_STATE_OPENING) {
    log::warn("Trying to read a port in PORT_CONNECTION_STATE_OPENING state");
  }

  if (p_port->line_status) {
    return (PORT_LINE_ERR);
  }

  if (fixed_queue_is_empty(p_port->rx.queue)) {
    log::warn("Read on empty input queue");
    return (PORT_SUCCESS);
  }

  count = 0;

  while (max_len) {
    p_buf = (BT_HDR*)fixed_queue_try_peek_first(p_port->rx.queue);
    if (p_buf == NULL) break;

    if (p_buf->len > max_len) {
      memcpy(p_data, (uint8_t*)(p_buf + 1) + p_buf->offset, max_len);
      p_buf->offset += max_len;
      p_buf->len -= max_len;

      *p_len += max_len;

      mutex_global_lock();

      p_port->rx.queue_size -= max_len;

      mutex_global_unlock();

      break;
    } else {
      memcpy(p_data, (uint8_t*)(p_buf + 1) + p_buf->offset, p_buf->len);

      *p_len += p_buf->len;
      max_len -= p_buf->len;

      mutex_global_lock();

      p_port->rx.queue_size -= p_buf->len;

      if (max_len) {
        p_data += p_buf->len;
      }

      osi_free(fixed_queue_try_dequeue(p_port->rx.queue));

      mutex_global_unlock();

      count++;
    }
  }

  if (*p_len == 1) {
    log::verbose("PORT_ReadData queue:{} returned:{} {:x}",
                 p_port->rx.queue_size, *p_len, (p_data[0]));
  } else {
    log::verbose("PORT_ReadData queue:{} returned:{}", p_port->rx.queue_size,
                 *p_len);
  }

  /* If rfcomm suspended traffic from the peer based on the rx_queue_size */
  /* check if it can be resumed now */
  port_flow_control_peer(p_port, true, count);

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         port_write
 *
 * Description      This function when a data packet is received from the apper
 *                  layer task.
 *
 * Parameters:      p_port     - pointer to address of port control block
 *                  p_buf      - pointer to address of buffer with data,
 *
 ******************************************************************************/
static int port_write(tPORT* p_port, BT_HDR* p_buf) {
  /* We should not allow to write data in to server port when connection is not
   * opened */
  if (p_port->is_server && (p_port->rfc.state != RFC_STATE_OPENED)) {
    osi_free(p_buf);
    return (PORT_CLOSED);
  }

  /* Keep the data in pending queue if peer does not allow data, or */
  /* Peer is not ready or Port is not yet opened or initial port control */
  /* command has not been sent */
  if (p_port->tx.peer_fc || !p_port->rfc.p_mcb ||
      !p_port->rfc.p_mcb->peer_ready ||
      (p_port->rfc.state != RFC_STATE_OPENED) ||
      ((p_port->port_ctrl & (PORT_CTRL_REQ_SENT | PORT_CTRL_IND_RECEIVED)) !=
       (PORT_CTRL_REQ_SENT | PORT_CTRL_IND_RECEIVED))) {
    if ((p_port->tx.queue_size > PORT_TX_CRITICAL_WM) ||
        (fixed_queue_length(p_port->tx.queue) > PORT_TX_BUF_CRITICAL_WM)) {
      log::warn("PORT_Write: Queue size: {}", p_port->tx.queue_size);

      osi_free(p_buf);

      if ((p_port->p_callback != NULL) && (p_port->ev_mask & PORT_EV_ERR))
        p_port->p_callback(PORT_EV_ERR, p_port->handle);

      return (PORT_TX_FULL);
    }

    log::verbose(
        "PORT_Write : Data is enqued. flow disabled {} peer_ready {} state {} "
        "ctrl_state {:x}",
        p_port->tx.peer_fc,
        (p_port->rfc.p_mcb && p_port->rfc.p_mcb->peer_ready), p_port->rfc.state,
        p_port->port_ctrl);

    fixed_queue_enqueue(p_port->tx.queue, p_buf);
    p_port->tx.queue_size += p_buf->len;

    return (PORT_CMD_PENDING);
  } else {
    log::verbose("PORT_Write : Data is being sent");

    RFCOMM_DataReq(p_port->rfc.p_mcb, p_port->dlci, p_buf);
    return (PORT_SUCCESS);
  }
}

/*******************************************************************************
 *
 * Function         PORT_WriteDataCO
 *
 * Description      Normally not GKI aware application will call this function
 *                  to send data to the port by callout functions
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  fd         - socket fd
 *                  p_len      - Byte count returned
 *
 ******************************************************************************/
int PORT_WriteDataCO(uint16_t handle, int* p_len) {
  tPORT* p_port;
  BT_HDR* p_buf;
  uint32_t event = 0;
  int rc = 0;
  uint16_t length;

  log::verbose("PORT_WriteDataCO() handle:{}", handle);
  *p_len = 0;

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }
  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    log::warn("PORT_WriteDataByFd() no port state:{}", p_port->state);
    return (PORT_NOT_OPENED);
  }

  if (!p_port->peer_mtu) {
    log::error("PORT_WriteDataByFd() peer_mtu:{}", p_port->peer_mtu);
    return (PORT_UNKNOWN_ERROR);
  }
  int available = 0;
  // if(ioctl(fd, FIONREAD, &available) < 0)
  if (!p_port->p_data_co_callback(handle, (uint8_t*)&available,
                                  sizeof(available),
                                  DATA_CO_CALLBACK_TYPE_OUTGOING_SIZE)) {
    log::error(
        "p_data_co_callback DATA_CO_CALLBACK_TYPE_INCOMING_SIZE failed, "
        "available:{}",
        available);
    return (PORT_UNKNOWN_ERROR);
  }
  if (available == 0) return PORT_SUCCESS;
  /* Length for each buffer is the smaller of GKI buffer, peer MTU, or max_len
   */
  length = RFCOMM_DATA_BUF_SIZE -
           (uint16_t)(sizeof(BT_HDR) + L2CAP_MIN_OFFSET + RFCOMM_DATA_OVERHEAD);

  /* If there are buffers scheduled for transmission check if requested */
  /* data fits into the end of the queue */
  mutex_global_lock();

  p_buf = (BT_HDR*)fixed_queue_try_peek_last(p_port->tx.queue);
  if ((p_buf != NULL) &&
      (((int)p_buf->len + available) <= (int)p_port->peer_mtu) &&
      (((int)p_buf->len + available) <= (int)length)) {
    // if(recv(fd, (uint8_t *)(p_buf + 1) + p_buf->offset + p_buf->len,
    // available, 0) != available)
    if (!p_port->p_data_co_callback(
            handle, (uint8_t*)(p_buf + 1) + p_buf->offset + p_buf->len,
            available, DATA_CO_CALLBACK_TYPE_OUTGOING))

    {
      log::error(
          "p_data_co_callback DATA_CO_CALLBACK_TYPE_OUTGOING failed, "
          "available:{}",
          available);
      mutex_global_unlock();
      return (PORT_UNKNOWN_ERROR);
    }
    // memcpy ((uint8_t *)(p_buf + 1) + p_buf->offset + p_buf->len, p_data,
    // max_len);
    p_port->tx.queue_size += (uint16_t)available;

    *p_len = available;
    p_buf->len += (uint16_t)available;

    mutex_global_unlock();

    return (PORT_SUCCESS);
  }

  mutex_global_unlock();

  // int max_read = length < p_port->peer_mtu ? length : p_port->peer_mtu;

  // max_read = available < max_read ? available : max_read;

  while (available) {
    /* if we're over buffer high water mark, we're done */
    if ((p_port->tx.queue_size > PORT_TX_HIGH_WM) ||
        (fixed_queue_length(p_port->tx.queue) > PORT_TX_BUF_HIGH_WM)) {
      port_flow_control_user(p_port);
      event |= PORT_EV_FC;
      log::verbose(
          "tx queue is full,tx.queue_size:{},tx.queue.count:{},available:{}",
          p_port->tx.queue_size, fixed_queue_length(p_port->tx.queue),
          available);
      break;
    }

    /* continue with rfcomm data write */
    p_buf = (BT_HDR*)osi_malloc(RFCOMM_DATA_BUF_SIZE);
    p_buf->offset = L2CAP_MIN_OFFSET + RFCOMM_MIN_OFFSET;
    p_buf->layer_specific = handle;

    if (p_port->peer_mtu < length) length = p_port->peer_mtu;
    if (available < (int)length) length = (uint16_t)available;
    p_buf->len = length;
    p_buf->event = BT_EVT_TO_BTU_SP_DATA;

    // memcpy ((uint8_t *)(p_buf + 1) + p_buf->offset, p_data, length);
    // if(recv(fd, (uint8_t *)(p_buf + 1) + p_buf->offset, (int)length, 0) !=
    // (int)length)
    if (!p_port->p_data_co_callback(handle,
                                    (uint8_t*)(p_buf + 1) + p_buf->offset,
                                    length, DATA_CO_CALLBACK_TYPE_OUTGOING)) {
      log::error(
          "p_data_co_callback DATA_CO_CALLBACK_TYPE_OUTGOING failed, length:{}",
          length);
      return (PORT_UNKNOWN_ERROR);
    }

    log::verbose("PORT_WriteData {} bytes", length);

    rc = port_write(p_port, p_buf);

    /* If queue went below the threashold need to send flow control */
    event |= port_flow_control_user(p_port);

    if (rc == PORT_SUCCESS) event |= PORT_EV_TXCHAR;

    if ((rc != PORT_SUCCESS) && (rc != PORT_CMD_PENDING)) break;

    *p_len += length;
    available -= (int)length;
  }
  if (!available && (rc != PORT_CMD_PENDING) && (rc != PORT_TX_QUEUE_DISABLED))
    event |= PORT_EV_TXEMPTY;

  /* Mask out all events that are not of interest to user */
  event &= p_port->ev_mask;

  /* Send event to the application */
  if (p_port->p_callback && event) (p_port->p_callback)(event, p_port->handle);

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         PORT_WriteData
 *
 * Description      Normally not GKI aware application will call this function
 *                  to send data to the port.
 *
 * Parameters:      handle     - Handle returned in the RFCOMM_CreateConnection
 *                  p_data      - Data area
 *                  max_len     - Byte count requested
 *                  p_len       - Byte count received
 *
 ******************************************************************************/
int PORT_WriteData(uint16_t handle, const char* p_data, uint16_t max_len,
                   uint16_t* p_len) {
  tPORT* p_port;
  BT_HDR* p_buf;
  uint32_t event = 0;
  int rc = 0;
  uint16_t length;

  log::verbose("PORT_WriteData() max_len:{}", max_len);

  *p_len = 0;

  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }
  p_port = &rfc_cb.port.port[handle - 1];

  if (!p_port->in_use || (p_port->state == PORT_CONNECTION_STATE_CLOSED)) {
    log::warn("PORT_WriteData() no port state:{}", p_port->state);
    return (PORT_NOT_OPENED);
  }

  if (p_port->state == PORT_CONNECTION_STATE_OPENING) {
    log::warn("Write data received but port is in OPENING state");
  }

  if (!max_len || !p_port->peer_mtu) {
    log::error("PORT_WriteData() peer_mtu:{}", p_port->peer_mtu);
    return (PORT_UNKNOWN_ERROR);
  }

  /* Length for each buffer is the smaller of GKI buffer, peer MTU, or max_len
   */
  length = RFCOMM_DATA_BUF_SIZE -
           (uint16_t)(sizeof(BT_HDR) + L2CAP_MIN_OFFSET + RFCOMM_DATA_OVERHEAD);

  /* If there are buffers scheduled for transmission check if requested */
  /* data fits into the end of the queue */
  mutex_global_lock();

  p_buf = (BT_HDR*)fixed_queue_try_peek_last(p_port->tx.queue);
  if ((p_buf != NULL) && ((p_buf->len + max_len) <= p_port->peer_mtu) &&
      ((p_buf->len + max_len) <= length)) {
    memcpy((uint8_t*)(p_buf + 1) + p_buf->offset + p_buf->len, p_data, max_len);
    p_port->tx.queue_size += max_len;

    *p_len = max_len;
    p_buf->len += max_len;

    mutex_global_unlock();

    return (PORT_SUCCESS);
  }

  mutex_global_unlock();

  while (max_len) {
    /* if we're over buffer high water mark, we're done */
    if ((p_port->tx.queue_size > PORT_TX_HIGH_WM) ||
        (fixed_queue_length(p_port->tx.queue) > PORT_TX_BUF_HIGH_WM))
      break;

    /* continue with rfcomm data write */
    p_buf = (BT_HDR*)osi_malloc(RFCOMM_DATA_BUF_SIZE);
    p_buf->offset = L2CAP_MIN_OFFSET + RFCOMM_MIN_OFFSET;
    p_buf->layer_specific = handle;

    if (p_port->peer_mtu < length) length = p_port->peer_mtu;
    if (max_len < length) length = max_len;
    p_buf->len = length;
    p_buf->event = BT_EVT_TO_BTU_SP_DATA;

    memcpy((uint8_t*)(p_buf + 1) + p_buf->offset, p_data, length);

    log::verbose("PORT_WriteData {} bytes", length);

    rc = port_write(p_port, p_buf);

    /* If queue went below the threashold need to send flow control */
    event |= port_flow_control_user(p_port);

    if (rc == PORT_SUCCESS) event |= PORT_EV_TXCHAR;

    if ((rc != PORT_SUCCESS) && (rc != PORT_CMD_PENDING)) break;

    *p_len += length;
    max_len -= length;
    p_data += length;
  }
  if (!max_len && (rc != PORT_CMD_PENDING) && (rc != PORT_TX_QUEUE_DISABLED))
    event |= PORT_EV_TXEMPTY;

  /* Mask out all events that are not of interest to user */
  event &= p_port->ev_mask;

  /* Send event to the application */
  if (p_port->p_callback && event) (p_port->p_callback)(event, p_port->handle);

  return (PORT_SUCCESS);
}

/*******************************************************************************
 *
 * Function         RFCOMM_Init
 *
 * Description      This function is called to initialize RFCOMM layer
 *
 ******************************************************************************/
void RFCOMM_Init(void) {
  memset(&rfc_cb, 0, sizeof(tRFC_CB)); /* Init RFCOMM control block */
  rfc_lcid_mcb = {};

  rfc_cb.rfc.last_mux = MAX_BD_CONNECTIONS;

  rfcomm_l2cap_if_init();
}

/*******************************************************************************
 *
 * Function         PORT_GetResultString
 *
 * Description      This function returns the human-readable string for a given
 *                  result code.
 *
 * Returns          a pointer to the human-readable string for the given result.
 *
 ******************************************************************************/
const char* PORT_GetResultString(const uint8_t result_code) {
  if (result_code > PORT_ERR_MAX) {
    return result_code_strings[PORT_ERR_MAX];
  }

  return result_code_strings[result_code];
}

/*******************************************************************************
 *
 * Function         PORT_GetSecurityMask
 *
 * Description      This function returns the security bitmask for a port.
 *
 * Returns          A result code, and writes the bitmask into the output
 *parameter.
 *
 ******************************************************************************/
int PORT_GetSecurityMask(uint16_t handle, uint16_t* sec_mask) {
  /* Check if handle is valid to avoid crashing */
  if ((handle == 0) || (handle > MAX_RFC_PORTS)) {
    return (PORT_BAD_HANDLE);
  }
  tPORT* p_port = &rfc_cb.port.port[handle - 1];
  *sec_mask = p_port->sec_mask;
  return (PORT_SUCCESS);
}
