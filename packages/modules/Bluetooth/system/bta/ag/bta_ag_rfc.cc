/******************************************************************************
 *
 *  Copyright 2004-2012 Broadcom Corporation
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
 *  This file contains the audio gateway functions controlling the RFCOMM
 *  connections.
 *
 ******************************************************************************/

#include <base/functional/bind.h>
#include <base/logging.h>
#include <bluetooth/log.h>

#include "bta/ag/bta_ag_int.h"
#include "bta/include/bta_sec_api.h"
#include "internal_include/bt_trace.h"
#include "osi/include/osi.h"
#include "stack/include/main_thread.h"
#include "stack/include/port_api.h"
#include "types/raw_address.h"

/* Event mask for RfCOMM port callback */
#define BTA_AG_PORT_EV_MASK PORT_EV_RXCHAR

using namespace bluetooth;

/* each scb has its own rfcomm callbacks */
void bta_ag_port_cback_1(uint32_t code, uint16_t port_handle);
void bta_ag_port_cback_2(uint32_t code, uint16_t port_handle);
void bta_ag_port_cback_3(uint32_t code, uint16_t port_handle);
void bta_ag_port_cback_4(uint32_t code, uint16_t port_handle);
void bta_ag_port_cback_5(uint32_t code, uint16_t port_handle);
void bta_ag_port_cback_6(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_1(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_2(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_3(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_4(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_5(uint32_t code, uint16_t port_handle);
void bta_ag_mgmt_cback_6(uint32_t code, uint16_t port_handle);

/* rfcomm callback function tables */
typedef tPORT_CALLBACK* tBTA_AG_PORT_CBACK;
const tBTA_AG_PORT_CBACK bta_ag_port_cback_tbl[] = {
    bta_ag_port_cback_1, bta_ag_port_cback_2, bta_ag_port_cback_3,
    bta_ag_port_cback_4, bta_ag_port_cback_5, bta_ag_port_cback_6};

const tBTA_AG_PORT_CBACK bta_ag_mgmt_cback_tbl[] = {
    bta_ag_mgmt_cback_1, bta_ag_mgmt_cback_2, bta_ag_mgmt_cback_3,
    bta_ag_mgmt_cback_4, bta_ag_mgmt_cback_5, bta_ag_mgmt_cback_6};

/*******************************************************************************
 *
 * Function         bta_ag_port_cback
 *
 * Description      RFCOMM Port callback
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_ag_port_cback(UNUSED_ATTR uint32_t code, uint16_t port_handle,
                              uint16_t handle) {
  tBTA_AG_SCB* p_scb = bta_ag_scb_by_idx(handle);
  if (p_scb != nullptr) {
    /* ignore port events for port handles other than connected handle */
    if (port_handle != p_scb->conn_handle) {
      log::error(
          "ag_port_cback ignoring handle:{} conn_handle = {} other handle = {}",
          port_handle, p_scb->conn_handle, handle);
      return;
    }
    if (!bta_ag_scb_open(p_scb)) {
      log::error(
          "rfcomm data on an unopened control block {} peer_addr {} state {}",
          handle, ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr), p_scb->state);
    }
    do_in_main_thread(
        FROM_HERE, base::BindOnce(&bta_ag_sm_execute_by_handle, handle,
                                  BTA_AG_RFC_DATA_EVT, tBTA_AG_DATA::kEmpty));
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_mgmt_cback
 *
 * Description      RFCOMM management callback
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_ag_mgmt_cback(uint32_t code, uint16_t port_handle,
                              uint16_t handle) {
  tBTA_AG_SCB* p_scb = bta_ag_scb_by_idx(handle);
  log::verbose("code={}, port_handle={}, scb_handle={}, p_scb=0x{}", code,
               port_handle, handle, fmt::ptr(p_scb));
  if (p_scb == nullptr) {
    log::warn("cannot find scb, code={}, port_handle={}, handle={}", code,
              port_handle, handle);
    return;
  }
  /* ignore close event for port handles other than connected handle */
  if ((code != PORT_SUCCESS) && (port_handle != p_scb->conn_handle)) {
    log::warn("ignore open failure for unmatched port_handle {}, scb_handle={}",
              port_handle, handle);
    return;
  }
  uint16_t event;
  if (code == PORT_SUCCESS) {
    bool found_handle = false;
    if (p_scb->conn_handle) {
      /* Outgoing connection */
      if (port_handle == p_scb->conn_handle) {
        found_handle = true;
      }
    } else {
      /* Incoming connection */
      for (uint16_t service_port_handle : p_scb->serv_handle) {
        if (port_handle == service_port_handle) {
          found_handle = true;
          break;
        }
      }
    }
    if (!found_handle) {
      log::error(
          "port opened successfully, but port_handle {} is unknown, "
          "scb_handle={}",
          port_handle, handle);
      return;
    }
    event = BTA_AG_RFC_OPEN_EVT;
  } else if (port_handle == p_scb->conn_handle) {
    /* distinguish server close events */
    event = BTA_AG_RFC_CLOSE_EVT;
  } else {
    event = BTA_AG_RFC_SRV_CLOSE_EVT;
  }

  tBTA_AG_DATA data = {};
  data.rfc.port_handle = port_handle;
  do_in_main_thread(FROM_HERE, base::BindOnce(&bta_ag_sm_execute_by_handle,
                                              handle, event, data));
}

/*******************************************************************************
 *
 * Function         bta_ag_port_cback_1 to 6
 *                  bta_ag_mgmt_cback_1 to 6
 *
 * Description      RFCOMM callback functions.  This is an easy way to
 *                  distinguish scb from the callback.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_mgmt_cback_1(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 1);
}
void bta_ag_mgmt_cback_2(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 2);
}
void bta_ag_mgmt_cback_3(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 3);
}
void bta_ag_mgmt_cback_4(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 4);
}
void bta_ag_mgmt_cback_5(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 5);
}
void bta_ag_mgmt_cback_6(uint32_t code, uint16_t port_handle) {
  bta_ag_mgmt_cback(code, port_handle, 6);
}
void bta_ag_port_cback_1(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 1);
}
void bta_ag_port_cback_2(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 2);
}
void bta_ag_port_cback_3(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 3);
}
void bta_ag_port_cback_4(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 4);
}
void bta_ag_port_cback_5(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 5);
}
void bta_ag_port_cback_6(uint32_t code, uint16_t port_handle) {
  bta_ag_port_cback(code, port_handle, 6);
}

/*******************************************************************************
 *
 * Function         bta_ag_setup_port
 *
 * Description      Setup RFCOMM port for use by AG.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_setup_port(tBTA_AG_SCB* p_scb, uint16_t handle) {
  int port_callback_index = bta_ag_scb_to_idx(p_scb) - 1;
  CHECK_GE(port_callback_index, 0)
      << "invalid callback index, handle=" << handle << ", bd_addr"
      << p_scb->peer_addr;
  CHECK_LT(port_callback_index,
           static_cast<int>(sizeof(bta_ag_port_cback_tbl) /
                            sizeof(bta_ag_port_cback_tbl[0])))
      << "callback index out of bound, handle=" << handle << ", bd_addr"
      << p_scb->peer_addr;
  PORT_SetEventMask(handle, BTA_AG_PORT_EV_MASK);
  PORT_SetEventCallback(handle, bta_ag_port_cback_tbl[port_callback_index]);
}

/*******************************************************************************
 *
 * Function         bta_ag_start_servers
 *
 * Description      Setup RFCOMM servers for use by AG.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_start_servers(tBTA_AG_SCB* p_scb, tBTA_SERVICE_MASK services) {
  services >>= BTA_HSP_SERVICE_ID;
  for (int i = 0; i < BTA_AG_NUM_IDX && services != 0; i++, services >>= 1) {
    /* if service is set in mask */
    if (services & 1) {
      int management_callback_index = bta_ag_scb_to_idx(p_scb) - 1;
      CHECK_GE(management_callback_index, 0)
          << "invalid callback index, services=" << loghex(services)
          << ", bd_addr=" << p_scb->peer_addr;
      CHECK_LT(management_callback_index,
               static_cast<int>(sizeof(bta_ag_mgmt_cback_tbl) /
                                sizeof(bta_ag_mgmt_cback_tbl[0])))
          << "callback index out of bound, services=" << loghex(services)
          << ", bd_addr" << p_scb->peer_addr;
      int status = RFCOMM_CreateConnectionWithSecurity(
          bta_ag_uuid[i], bta_ag_cb.profile[i].scn, true, BTA_AG_MTU,
          RawAddress::kAny, &(p_scb->serv_handle[i]),
          bta_ag_mgmt_cback_tbl[management_callback_index],
          BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT);
      if (status == PORT_SUCCESS) {
        bta_ag_setup_port(p_scb, p_scb->serv_handle[i]);
      } else {
        /* TODO: CR#137125 to handle to error properly */
        log::error(
            "RFCOMM_CreateConnectionWithSecurity ERROR {}, p_scb={}, "
            "services={}, mgmt_cback_index={}",
            status, fmt::ptr(p_scb), loghex(services),
            management_callback_index);
      }
      log::verbose("p_scb=0x{}, services=0x{:04x}, mgmt_cback_index={}",
                   fmt::ptr(p_scb), services, management_callback_index);
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_close_servers
 *
 * Description      Close RFCOMM servers port for use by AG.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_close_servers(tBTA_AG_SCB* p_scb, tBTA_SERVICE_MASK services) {
  services >>= BTA_HSP_SERVICE_ID;
  for (int i = 0; i < BTA_AG_NUM_IDX && services != 0; i++, services >>= 1) {
    /* if service is set in mask */
    if (services & 1) {
      RFCOMM_RemoveServer(p_scb->serv_handle[i]);
      p_scb->serv_handle[i] = 0;
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_is_server_closed
 *
 * Description      Returns true if all servers are closed.
 *
 *
 * Returns          true if all servers are closed, false otherwise
 *
 ******************************************************************************/
bool bta_ag_is_server_closed(tBTA_AG_SCB* p_scb) {
  uint8_t xx;
  bool is_closed = true;

  for (xx = 0; xx < BTA_AG_NUM_IDX; xx++) {
    if (p_scb->serv_handle[xx] != 0) is_closed = false;
  }

  return is_closed;
}

/*******************************************************************************
 *
 * Function         bta_ag_rfc_do_open
 *
 * Description      Open an RFCOMM connection to the peer device.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_rfc_do_open(tBTA_AG_SCB* p_scb, const tBTA_AG_DATA& data) {
  int management_callback_index = bta_ag_scb_to_idx(p_scb) - 1;
  int status = RFCOMM_CreateConnectionWithSecurity(
      bta_ag_uuid[p_scb->conn_service], p_scb->peer_scn, false, BTA_AG_MTU,
      p_scb->peer_addr, &(p_scb->conn_handle),
      bta_ag_mgmt_cback_tbl[management_callback_index],
      BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT);
  log::verbose("p_scb=0x{}, conn_handle={}, mgmt_cback_index={}, status={}",
               fmt::ptr(p_scb), p_scb->conn_handle, management_callback_index,
               status);
  if (status == PORT_SUCCESS) {
    bta_ag_setup_port(p_scb, p_scb->conn_handle);
  } else {
    /* RFCOMM create connection failed; send ourselves RFCOMM close event */
    log::error("RFCOMM_CreateConnection ERROR {} for {}", status,
               ADDRESS_TO_LOGGABLE_STR(p_scb->peer_addr));
    bta_ag_sm_execute(p_scb, BTA_AG_RFC_CLOSE_EVT, data);
  }
}

/*******************************************************************************
 *
 * Function         bta_ag_rfc_do_close
 *
 * Description      Close RFCOMM connection.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_ag_rfc_do_close(tBTA_AG_SCB* p_scb,
                         UNUSED_ATTR const tBTA_AG_DATA& data) {
  log::info("p_scb->conn_handle: 0x{:04x}", p_scb->conn_handle);
  if (p_scb->conn_handle) {
    RFCOMM_RemoveConnection(p_scb->conn_handle);
  } else {
    /* Close API was called while AG is in Opening state.               */
    /* Need to trigger the state machine to send callback to the app    */
    /* and move back to INIT state.                                     */
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&bta_ag_sm_execute_by_handle, bta_ag_scb_to_idx(p_scb),
                       BTA_AG_RFC_CLOSE_EVT, tBTA_AG_DATA::kEmpty));

    /* Cancel SDP if it had been started. */
    /*
    if(p_scb->p_disc_db)
    {
        (void)SDP_CancelServiceSearch (p_scb->p_disc_db);
    }
    */
  }
}
