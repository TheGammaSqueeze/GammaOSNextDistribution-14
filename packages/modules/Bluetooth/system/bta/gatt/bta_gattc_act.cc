/******************************************************************************
 *
 *  Copyright 2003-2012 Broadcom Corporation
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
 *  This file contains the GATT client action functions for the state
 *  machine.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_gattc"

#include <android_bluetooth_flags.h>
#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include "bta/gatt/bta_gattc_int.h"
#include "bta/include/bta_api.h"
#include "btif/include/btif_debug_conn.h"
#include "device/include/controller.h"
#include "hardware/bt_gatt_types.h"
#include "include/check.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_uuid16.h"
#include "stack/include/btm_ble_api_types.h"
#include "stack/include/btm_sec_api.h"
#include "stack/include/l2c_api.h"
#include "stack/include/main_thread.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using base::StringPrintf;
using bluetooth::Uuid;
using namespace bluetooth;

/*****************************************************************************
 *  Constants
 ****************************************************************************/
static void bta_gattc_conn_cback(tGATT_IF gattc_if, const RawAddress& bda,
                                 uint16_t conn_id, bool connected,
                                 tGATT_DISCONN_REASON reason,
                                 tBT_TRANSPORT transport);

static void bta_gattc_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                 tGATT_STATUS status,
                                 tGATT_CL_COMPLETE* p_data);
static void bta_gattc_deregister_cmpl(tBTA_GATTC_RCB* p_clreg);
static void bta_gattc_enc_cmpl_cback(tGATT_IF gattc_if, const RawAddress& bda);
static void bta_gattc_cong_cback(uint16_t conn_id, bool congested);
static void bta_gattc_phy_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                       uint8_t tx_phy, uint8_t rx_phy,
                                       tGATT_STATUS status);
static void bta_gattc_conn_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t interval, uint16_t latency,
                                        uint16_t timeout, tGATT_STATUS status);
static void bta_gattc_subrate_chg_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t subrate_factor,
                                        uint16_t latency, uint16_t cont_num,
                                        uint16_t timeout, tGATT_STATUS status);
static void bta_gattc_init_bk_conn(const tBTA_GATTC_API_OPEN* p_data,
                                   tBTA_GATTC_RCB* p_clreg);

static tGATT_CBACK bta_gattc_cl_cback = {
    .p_conn_cb = bta_gattc_conn_cback,
    .p_cmpl_cb = bta_gattc_cmpl_cback,
    .p_disc_res_cb = bta_gattc_disc_res_cback,
    .p_disc_cmpl_cb = bta_gattc_disc_cmpl_cback,
    .p_req_cb = nullptr,
    .p_enc_cmpl_cb = bta_gattc_enc_cmpl_cback,
    .p_congestion_cb = bta_gattc_cong_cback,
    .p_phy_update_cb = bta_gattc_phy_update_cback,
    .p_conn_update_cb = bta_gattc_conn_update_cback,
    .p_subrate_chg_cb = bta_gattc_subrate_chg_cback,
};

/* opcode(tGATTC_OPTYPE) order has to be comply with internal event order */
static uint16_t bta_gattc_opcode_to_int_evt[] = {
    /* Skip: GATTC_OPTYPE_NONE */
    /* Skip: GATTC_OPTYPE_DISCOVERY */
    BTA_GATTC_API_READ_EVT,   /* GATTC_OPTYPE_READ */
    BTA_GATTC_API_WRITE_EVT,  /* GATTC_OPTYPE_WRITE */
    BTA_GATTC_API_EXEC_EVT,   /* GATTC_OPTYPE_EXE_WRITE */
    BTA_GATTC_API_CFG_MTU_EVT /* GATTC_OPTYPE_CONFIG */
};

static const char* bta_gattc_op_code_name[] = {
    "Unknown",      /* GATTC_OPTYPE_NONE */
    "Discovery",    /* GATTC_OPTYPE_DISCOVERY */
    "Read",         /* GATTC_OPTYPE_READ */
    "Write",        /* GATTC_OPTYPE_WRITE */
    "Exec",         /* GATTC_OPTYPE_EXE_WRITE */
    "Config",       /* GATTC_OPTYPE_CONFIG */
    "Notification", /* GATTC_OPTYPE_NOTIFICATION */
    "Indication"    /* GATTC_OPTYPE_INDICATION */
};

/*****************************************************************************
 *  Action Functions
 ****************************************************************************/

void bta_gattc_reset_discover_st(tBTA_GATTC_SERV* p_srcb, tGATT_STATUS status);

/** Enables GATTC module */
static void bta_gattc_enable() {
  log::verbose("");

  if (bta_gattc_cb.state == BTA_GATTC_STATE_DISABLED) {
    /* initialize control block */
    bta_gattc_cb = tBTA_GATTC_CB();
    bta_gattc_cb.state = BTA_GATTC_STATE_ENABLED;
  } else {
    log::verbose("GATTC is already enabled");
  }
}

/** Disable GATTC module by cleaning up all active connections and deregister
 * all application */
void bta_gattc_disable() {
  uint8_t i;

  log::verbose("");

  if (bta_gattc_cb.state != BTA_GATTC_STATE_ENABLED) {
    log::error("not enabled, or disabled in progress");
    return;
  }

  for (i = 0; i < BTA_GATTC_CL_MAX; i++) {
    if (!bta_gattc_cb.cl_rcb[i].in_use) continue;

    bta_gattc_cb.state = BTA_GATTC_STATE_DISABLING;
    bta_gattc_deregister(&bta_gattc_cb.cl_rcb[i]);
  }

  /* no registered apps, indicate disable completed */
  if (bta_gattc_cb.state != BTA_GATTC_STATE_DISABLING) {
    bta_gattc_cb = tBTA_GATTC_CB();
    bta_gattc_cb.state = BTA_GATTC_STATE_DISABLED;
  }
}

/** start an application interface */
static void bta_gattc_start_if(uint8_t client_if) {
  log::debug("client_if={}", client_if);
  if (!bta_gattc_cl_get_regcb(client_if)) {
    log::error("Unable to start app.: Unknown client_if={}", client_if);
    return;
  }

  GATT_StartIf(client_if);
}

/** Register a GATT client application with BTA */
void bta_gattc_register(const Uuid& app_uuid, tBTA_GATTC_CBACK* p_cback,
                        BtaAppRegisterCallback cb, bool eatt_support) {
  tGATT_STATUS status = GATT_NO_RESOURCES;
  uint8_t client_if = 0;
  log::debug("state: {}, uuid={}", bta_gattc_cb.state, app_uuid.ToString());

  /* check if  GATTC module is already enabled . Else enable */
  if (bta_gattc_cb.state == BTA_GATTC_STATE_DISABLED) {
    log::debug("GATTC module not enabled, enabling it");
    bta_gattc_enable();
  }
  /* todo need to check duplicate uuid */
  for (uint8_t i = 0; i < BTA_GATTC_CL_MAX; i++) {
    if (!bta_gattc_cb.cl_rcb[i].in_use) {
      bta_gattc_cb.cl_rcb[i].client_if = GATT_Register(
          app_uuid, "GattClient", &bta_gattc_cl_cback, eatt_support);
      if (bta_gattc_cb.cl_rcb[i].client_if == 0) {
        log::error(
            "Register with GATT stack failed with index {}, trying next index",
            i);
        status = GATT_ERROR;
      } else {
        bta_gattc_cb.cl_rcb[i].in_use = true;
        bta_gattc_cb.cl_rcb[i].p_cback = p_cback;
        bta_gattc_cb.cl_rcb[i].app_uuid = app_uuid;

        /* BTA use the same client interface as BTE GATT statck */
        client_if = bta_gattc_cb.cl_rcb[i].client_if;

        log::debug(
            "Registered GATT client interface {} with uuid={}, starting it on "
            "main thread",
            client_if, app_uuid.ToString());

        do_in_main_thread(FROM_HERE,
                          base::BindOnce(&bta_gattc_start_if, client_if));

        status = GATT_SUCCESS;
        break;
      }
    }
  }

  if (!cb.is_null()) {
    cb.Run(client_if, status);
  } else {
    log::warn("No GATT callback available, client_if={}, status={}", client_if,
              status);
  }
}

/** De-Register a GATT client application with BTA */
void bta_gattc_deregister(tBTA_GATTC_RCB* p_clreg) {
  uint8_t accept_list_size = 0;
  if (controller_get_interface()->SupportsBle()) {
    accept_list_size = controller_get_interface()->get_ble_acceptlist_size();
  }

  /* remove bg connection associated with this rcb */
  for (uint8_t i = 0; i < accept_list_size; i++) {
    if (!bta_gattc_cb.bg_track[i].in_use) continue;

    if (bta_gattc_cb.bg_track[i].cif_mask & ((tBTA_GATTC_CIF_MASK)1 << (p_clreg->client_if - 1))) {
      bta_gattc_mark_bg_conn(p_clreg->client_if,
                             bta_gattc_cb.bg_track[i].remote_bda, false);
      GATT_CancelConnect(p_clreg->client_if,
                         bta_gattc_cb.bg_track[i].remote_bda, false);
    }
  }

  if (p_clreg->num_clcb == 0) {
    bta_gattc_deregister_cmpl(p_clreg);
    return;
  }

  /* close all CLCB related to this app */
  for (uint8_t i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
    if (!bta_gattc_cb.clcb[i].in_use || (bta_gattc_cb.clcb[i].p_rcb != p_clreg))
      continue;

    p_clreg->dereg_pending = true;

    BT_HDR_RIGID buf;
    buf.event = BTA_GATTC_API_CLOSE_EVT;
    buf.layer_specific = bta_gattc_cb.clcb[i].bta_conn_id;
    bta_gattc_close(&bta_gattc_cb.clcb[i], (tBTA_GATTC_DATA*)&buf);
  }
}

/** process connect API request */
void bta_gattc_process_api_open(const tBTA_GATTC_DATA* p_msg) {
  uint16_t event = ((BT_HDR_RIGID*)p_msg)->event;

  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(p_msg->api_conn.client_if);
  if (!p_clreg) {
    log::error("Failed, unknown client_if={}", p_msg->api_conn.client_if);
    return;
  }

  if (p_msg->api_conn.connection_type != BTM_BLE_DIRECT_CONNECTION) {
    bta_gattc_init_bk_conn(&p_msg->api_conn, p_clreg);
    return;
  }

  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_alloc_clcb(
      p_msg->api_conn.client_if, p_msg->api_conn.remote_bda,
      p_msg->api_conn.transport);
  if (p_clcb != nullptr) {
    bta_gattc_sm_execute(p_clcb, event, p_msg);
  } else {
    log::error("No resources to open a new connection.");

    bta_gattc_send_open_cback(p_clreg, GATT_NO_RESOURCES,
                              p_msg->api_conn.remote_bda, GATT_INVALID_CONN_ID,
                              p_msg->api_conn.transport, 0);
  }
}

/** process connect API request */
void bta_gattc_process_api_open_cancel(const tBTA_GATTC_DATA* p_msg) {
  CHECK(p_msg != nullptr);

  uint16_t event = ((BT_HDR_RIGID*)p_msg)->event;

  if (!p_msg->api_cancel_conn.is_direct) {
    log::debug("Cancel GATT client background connection");
    bta_gattc_cancel_bk_conn(&p_msg->api_cancel_conn);
    return;
  }
  log::debug("Cancel GATT client direct connection");

  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_cif(
      p_msg->api_cancel_conn.client_if, p_msg->api_cancel_conn.remote_bda,
      BT_TRANSPORT_LE);
  if (p_clcb != NULL) {
    bta_gattc_sm_execute(p_clcb, event, p_msg);
    return;
  }

  log::error("No such connection need to be cancelled");

  tBTA_GATTC_RCB* p_clreg =
      bta_gattc_cl_get_regcb(p_msg->api_cancel_conn.client_if);

  if (p_clreg && p_clreg->p_cback) {
    tBTA_GATTC cb_data;
    cb_data.status = GATT_ERROR;
    (*p_clreg->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
  }
}

/** process encryption complete message */
static void bta_gattc_process_enc_cmpl(tGATT_IF client_if,
                                       const RawAddress& bda) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(client_if);

  if (!p_clreg || !p_clreg->p_cback) return;

  tBTA_GATTC cb_data;
  memset(&cb_data, 0, sizeof(tBTA_GATTC));

  cb_data.enc_cmpl.client_if = client_if;
  cb_data.enc_cmpl.remote_bda = bda;

  (*p_clreg->p_cback)(BTA_GATTC_ENC_CMPL_CB_EVT, &cb_data);
}

void bta_gattc_cancel_open_error(tBTA_GATTC_CLCB* p_clcb,
                                 UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  cb_data.status = GATT_ERROR;

  if (p_clcb && p_clcb->p_rcb && p_clcb->p_rcb->p_cback)
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
}

void bta_gattc_open_error(tBTA_GATTC_CLCB* p_clcb,
                          UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  log::error("Connection already opened. wrong state");

  bta_gattc_send_open_cback(p_clcb->p_rcb, GATT_SUCCESS, p_clcb->bda,
                            p_clcb->bta_conn_id, p_clcb->transport, 0);
}

void bta_gattc_open_fail(tBTA_GATTC_CLCB* p_clcb,
                         UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  if (IS_FLAG_ENABLED(enumerate_gatt_errors) &&
      p_data->int_conn.reason == GATT_CONN_TIMEOUT) {
    log::warn(
        "Connection timed out after 30 seconds. conn_id={}. Return "
        "GATT_CONNECTION_TIMEOUT({})",
        loghex(p_clcb->bta_conn_id), GATT_CONNECTION_TIMEOUT);
    bta_gattc_send_open_cback(p_clcb->p_rcb, GATT_CONNECTION_TIMEOUT,
                              p_clcb->bda, p_clcb->bta_conn_id,
                              p_clcb->transport, 0);
  } else {
    log::warn("Cannot establish Connection. conn_id={}. Return GATT_ERROR({})",
              loghex(p_clcb->bta_conn_id), GATT_ERROR);
    bta_gattc_send_open_cback(p_clcb->p_rcb, GATT_ERROR, p_clcb->bda,
                              p_clcb->bta_conn_id, p_clcb->transport, 0);
  }

  /* open failure, remove clcb */
  bta_gattc_clcb_dealloc(p_clcb);
}

/** Process API connection function */
void bta_gattc_open(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_DATA gattc_data;

  /* open/hold a connection */
  if (!GATT_Connect(p_clcb->p_rcb->client_if, p_data->api_conn.remote_bda,
                    p_data->api_conn.remote_addr_type,
                    BTM_BLE_DIRECT_CONNECTION, p_data->api_conn.transport,
                    p_data->api_conn.opportunistic,
                    p_data->api_conn.initiating_phys)) {
    log::error("Connection open failure");
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_OPEN_FAIL_EVT, p_data);
    return;
  }

  tBTA_GATTC_RCB* p_clreg = p_clcb->p_rcb;
  /* Re-enable notification registration for closed connection */
  for (int i = 0; i < BTA_GATTC_NOTIF_REG_MAX; i++) {
    if (p_clreg->notif_reg[i].in_use &&
        p_clreg->notif_reg[i].remote_bda == p_clcb->bda &&
        p_clreg->notif_reg[i].app_disconnected) {
      p_clreg->notif_reg[i].app_disconnected = false;
    }
  }

  /* a connected remote device */
  if (GATT_GetConnIdIfConnected(
          p_clcb->p_rcb->client_if, p_data->api_conn.remote_bda,
          &p_clcb->bta_conn_id, p_data->api_conn.transport)) {
    gattc_data.int_conn.hdr.layer_specific = p_clcb->bta_conn_id;

    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT, &gattc_data);
  }
  /* else wait for the callback event */
}

/** Process API Open for a background connection */
static void bta_gattc_init_bk_conn(const tBTA_GATTC_API_OPEN* p_data,
                                   tBTA_GATTC_RCB* p_clreg) {
  if (!bta_gattc_mark_bg_conn(p_data->client_if, p_data->remote_bda, true)) {
    log::warn("Unable to find space for accept list connection mask");
    bta_gattc_send_open_cback(p_clreg, GATT_NO_RESOURCES, p_data->remote_bda,
                              GATT_INVALID_CONN_ID, BT_TRANSPORT_LE, 0);
    return;
  }

  /* always call open to hold a connection */
  if (!GATT_Connect(p_data->client_if, p_data->remote_bda,
                    p_data->connection_type, p_data->transport, false)) {
    log::error("Unable to connect to remote bd_addr={}",
               ADDRESS_TO_LOGGABLE_CSTR(p_data->remote_bda));
    bta_gattc_send_open_cback(p_clreg, GATT_ILLEGAL_PARAMETER,
                              p_data->remote_bda, GATT_INVALID_CONN_ID,
                              BT_TRANSPORT_LE, 0);
    return;
  }

  uint16_t conn_id;
  if (!GATT_GetConnIdIfConnected(p_data->client_if, p_data->remote_bda,
                                 &conn_id, p_data->transport)) {
    log::info("Not a connected remote device yet");
    return;
  }

  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_alloc_clcb(
      p_data->client_if, p_data->remote_bda, BT_TRANSPORT_LE);
  if (!p_clcb) {
    log::warn("Unable to find connection link for device:{}",
              ADDRESS_TO_LOGGABLE_CSTR(p_data->remote_bda));
    return;
  }

  p_clcb->bta_conn_id = conn_id;
  tBTA_GATTC_DATA gattc_data = {
      .hdr =
          {
              .layer_specific = conn_id,
          },
  };

  /* open connection */
  bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT,
                       static_cast<const tBTA_GATTC_DATA*>(&gattc_data));
}

/** Process API Cancel Open for a background connection */
void bta_gattc_cancel_bk_conn(const tBTA_GATTC_API_CANCEL_OPEN* p_data) {
  tBTA_GATTC_RCB* p_clreg;
  tBTA_GATTC cb_data;
  cb_data.status = GATT_ERROR;

  /* remove the device from the bg connection mask */
  if (bta_gattc_mark_bg_conn(p_data->client_if, p_data->remote_bda, false)) {
    if (GATT_CancelConnect(p_data->client_if, p_data->remote_bda, false)) {
      cb_data.status = GATT_SUCCESS;
    } else {
      log::error("failed for client_if={}, remote_bda={}, is_direct=false",
                 static_cast<int>(p_data->client_if),
                 ADDRESS_TO_LOGGABLE_CSTR(p_data->remote_bda));
    }
  }
  p_clreg = bta_gattc_cl_get_regcb(p_data->client_if);

  if (p_clreg && p_clreg->p_cback) {
    (*p_clreg->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
  }
}

void bta_gattc_cancel_open_ok(tBTA_GATTC_CLCB* p_clcb,
                              UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (p_clcb->p_rcb->p_cback) {
    cb_data.status = GATT_SUCCESS;
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
  }

  bta_gattc_clcb_dealloc(p_clcb);
}

void bta_gattc_cancel_open(tBTA_GATTC_CLCB* p_clcb,
                           const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (GATT_CancelConnect(p_clcb->p_rcb->client_if,
                         p_data->api_cancel_conn.remote_bda, true)) {
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CANCEL_OPEN_OK_EVT, p_data);
  } else {
    if (p_clcb->p_rcb->p_cback) {
      cb_data.status = GATT_ERROR;
      (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
    }
  }
}

/** receive connection callback from stack */
void bta_gattc_conn(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  tGATT_IF gatt_if;
  log::verbose("server cache state={}", p_clcb->p_srcb->state);

  if (p_data != NULL) {
    log::verbose("conn_id={}", loghex(p_data->hdr.layer_specific));
    p_clcb->bta_conn_id = p_data->int_conn.hdr.layer_specific;

    GATT_GetConnectionInfor(p_data->hdr.layer_specific, &gatt_if, p_clcb->bda,
                            &p_clcb->transport);
  }

  p_clcb->p_srcb->connected = true;

  if (p_clcb->p_srcb->mtu == 0) p_clcb->p_srcb->mtu = GATT_DEF_BLE_MTU_SIZE;

  tBTA_GATTC_RCB* p_clreg = p_clcb->p_rcb;
  /* Re-enable notification registration for closed connection */
  for (int i = 0; i < BTA_GATTC_NOTIF_REG_MAX; i++) {
    if (p_clreg->notif_reg[i].in_use &&
        p_clreg->notif_reg[i].remote_bda == p_clcb->bda &&
        p_clreg->notif_reg[i].app_disconnected) {
      p_clreg->notif_reg[i].app_disconnected = false;
    }
  }

  /* start database cache if needed */
  if (p_clcb->p_srcb->gatt_database.IsEmpty() ||
      p_clcb->p_srcb->state != BTA_GATTC_SERV_IDLE) {
    if (p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE) {
      p_clcb->p_srcb->state = BTA_GATTC_SERV_LOAD;
      // Consider the case that if GATT Server is changed, but no service
      // changed indication is received, the database might be out of date. So
      // if robust caching is known to be supported, always check the db hash
      // first, before loading the stored database.

      // Only load the database if we are bonded, since the device cache is
      // meaningless otherwise (as we need to do rediscovery regardless)
      gatt::Database db = btm_sec_is_a_bonded_dev(p_clcb->bda)
                              ? bta_gattc_cache_load(p_clcb->p_srcb->server_bda)
                              : gatt::Database();
      auto robust_caching_support = GetRobustCachingSupport(p_clcb, db);
      log::info("Connected to {}, robust caching support is {}",
                p_clcb->bda.ToRedactedStringForLogging(),
                robust_caching_support);

      if (!db.IsEmpty()) p_clcb->p_srcb->gatt_database = db;

      if (db.IsEmpty() ||
          robust_caching_support != RobustCachingSupport::UNSUPPORTED) {
        // If the peer device is expected to support robust caching, or if we
        // don't know its services yet, then we should do discovery (which may
        // short-circuit through a hash match, but might also do the full
        // discovery).
        p_clcb->p_srcb->state = BTA_GATTC_SERV_DISC;

        /* set true to read database hash before service discovery */
        if (bta_gattc_is_robust_caching_enabled()) {
          p_clcb->p_srcb->srvc_hdl_db_hash = true;
        }

        /* cache load failure, start discovery */
        bta_gattc_start_discover(p_clcb, NULL);
      } else {
        p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
        bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_SUCCESS);
      }
    } else /* cache is building */
      p_clcb->state = BTA_GATTC_DISCOVER_ST;
  }

  else {
    /* a pending service handle change indication */
    if (p_clcb->p_srcb->srvc_hdl_chg) {
      p_clcb->p_srcb->srvc_hdl_chg = false;

      /* set true to read database hash before service discovery */
      if (bta_gattc_is_robust_caching_enabled()) {
        p_clcb->p_srcb->srvc_hdl_db_hash = true;
      }

      /* start discovery */
      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    }
  }

  if (p_clcb->p_rcb) {
    /* there is no RM for GATT */
    if (p_clcb->transport == BT_TRANSPORT_BR_EDR)
      bta_sys_conn_open(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);

    bta_gattc_send_open_cback(p_clcb->p_rcb, GATT_SUCCESS, p_clcb->bda,
                              p_clcb->bta_conn_id, p_clcb->transport,
                              p_clcb->p_srcb->mtu);
  }
}

/** close a  connection */
void bta_gattc_close_fail(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (p_clcb->p_rcb->p_cback) {
    memset(&cb_data, 0, sizeof(tBTA_GATTC));
    cb_data.close.client_if = p_clcb->p_rcb->client_if;
    cb_data.close.conn_id = p_data->hdr.layer_specific;
    cb_data.close.remote_bda = p_clcb->bda;
    cb_data.close.reason = BTA_GATT_CONN_NONE;
    cb_data.close.status = GATT_ERROR;

    log::warn("conn_id={}. Returns GATT_ERROR({}).",
              loghex(cb_data.close.conn_id), GATT_ERROR);

    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CLOSE_EVT, &cb_data);
  }
}

/** close a GATTC connection */
void bta_gattc_close(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_CBACK* p_cback = p_clcb->p_rcb->p_cback;
  tBTA_GATTC_RCB* p_clreg = p_clcb->p_rcb;
  tBTA_GATTC cb_data = {
      .close =
          {
              .conn_id = p_clcb->bta_conn_id,
              .status = GATT_SUCCESS,
              .client_if = p_clcb->p_rcb->client_if,
              .remote_bda = p_clcb->bda,
              .reason = GATT_CONN_OK,
          },
  };

  if (p_clcb->transport == BT_TRANSPORT_BR_EDR) {
    bta_sys_conn_close(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
  }

  /* Disable notification registration for closed connection */
  for (int i = 0; i < BTA_GATTC_NOTIF_REG_MAX; i++) {
    if (p_clreg->notif_reg[i].in_use &&
        p_clreg->notif_reg[i].remote_bda == p_clcb->bda) {
      p_clreg->notif_reg[i].app_disconnected = true;
    }
  }

  if (p_data->hdr.event == BTA_GATTC_INT_DISCONN_EVT) {
    /* Since link has been disconnected by and it is possible that here are
     * already some new p_clcb created for the background connect, the number of
     * p_srcb->num_clcb is NOT 0. This will prevent p_srcb to be cleared inside
     * the bta_gattc_clcb_dealloc.
     *
     * In this point of time, we know that link does not exist, so let's make
     * sure the connection state, mtu and database is cleared.
     */
    bta_gattc_server_disconnected(p_clcb->p_srcb);
  }

  bta_gattc_clcb_dealloc(p_clcb);

  if (p_data->hdr.event == BTA_GATTC_API_CLOSE_EVT) {
    cb_data.close.status = GATT_Disconnect(p_data->hdr.layer_specific);
    cb_data.close.reason = GATT_CONN_TERMINATE_LOCAL_HOST;
    log::debug("Local close event client_if:{} conn_id:{} reason:{}",
               cb_data.close.client_if, cb_data.close.conn_id,
               gatt_disconnection_reason_text(
                   static_cast<tGATT_DISCONN_REASON>(cb_data.close.reason)));
  } else if (p_data->hdr.event == BTA_GATTC_INT_DISCONN_EVT) {
    cb_data.close.status = static_cast<tGATT_STATUS>(p_data->int_conn.reason);
    cb_data.close.reason = p_data->int_conn.reason;
    log::debug("Peer close disconnect event client_if:{} conn_id:{} reason:{}",
               cb_data.close.client_if, cb_data.close.conn_id,
               gatt_disconnection_reason_text(
                   static_cast<tGATT_DISCONN_REASON>(cb_data.close.reason)));
  }

  if (p_cback) (*p_cback)(BTA_GATTC_CLOSE_EVT, &cb_data);

  if (p_clreg->num_clcb == 0 && p_clreg->dereg_pending) {
    bta_gattc_deregister_cmpl(p_clreg);
  }
}

/** when a SRCB finished discovery, tell all related clcb */
void bta_gattc_reset_discover_st(tBTA_GATTC_SERV* p_srcb, tGATT_STATUS status) {
  for (uint8_t i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
    if (bta_gattc_cb.clcb[i].p_srcb == p_srcb) {
      bta_gattc_cb.clcb[i].status = status;
      bta_gattc_sm_execute(&bta_gattc_cb.clcb[i], BTA_GATTC_DISCOVER_CMPL_EVT,
                           NULL);
    }
  }
}

/** close a GATTC connection while in discovery state */
void bta_gattc_disc_close(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data) {
  log::verbose("Discovery cancel conn_id={}", loghex(p_clcb->bta_conn_id));

  if (p_clcb->disc_active)
    bta_gattc_reset_discover_st(p_clcb->p_srcb, GATT_ERROR);
  else
    p_clcb->state = BTA_GATTC_CONN_ST;

  // This function only gets called as the result of a BTA_GATTC_API_CLOSE_EVT
  // while in the BTA_GATTC_DISCOVER_ST state. Once the state changes, the
  // connection itself still needs to be closed to resolve the original event.
  if (p_clcb->state == BTA_GATTC_CONN_ST) {
    log::verbose(
        "State is back to BTA_GATTC_CONN_ST. Trigger connection close");
    bta_gattc_close(p_clcb, p_data);
  }
}

/** when a SRCB start discovery, tell all related clcb and set the state */
static void bta_gattc_set_discover_st(tBTA_GATTC_SERV* p_srcb) {
  uint8_t i;

  for (i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
    if (bta_gattc_cb.clcb[i].p_srcb == p_srcb) {
      bta_gattc_cb.clcb[i].status = GATT_SUCCESS;
      bta_gattc_cb.clcb[i].state = BTA_GATTC_DISCOVER_ST;
      bta_gattc_cb.clcb[i].request_during_discovery =
          BTA_GATTC_DISCOVER_REQ_NONE;
    }
  }
}

/** process service change in discovery state, mark up the auto update flag and
 * set status to be discovery cancel for current discovery.
 */
void bta_gattc_restart_discover(tBTA_GATTC_CLCB* p_clcb,
                                UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  p_clcb->status = GATT_CANCEL;
  p_clcb->auto_update = BTA_GATTC_DISC_WAITING;
}

/** Configure MTU size on the GATT connection */
void bta_gattc_cfg_mtu(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  uint16_t current_mtu = 0;
  auto result = GATTC_TryMtuRequest(p_clcb->bda, p_clcb->transport,
                                    p_clcb->bta_conn_id, &current_mtu);
  switch (result) {
    case MTU_EXCHANGE_DEVICE_DISCONNECTED:
      log::info("Device {} disconnected",
                ADDRESS_TO_LOGGABLE_CSTR(p_clcb->bda));
      bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_CONFIG,
                             GATT_NO_RESOURCES, NULL);
      bta_gattc_continue(p_clcb);
      return;
    case MTU_EXCHANGE_NOT_ALLOWED:
      log::info("Not allowed for BR/EDR devices {}",
                ADDRESS_TO_LOGGABLE_CSTR(p_clcb->bda));
      bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_CONFIG,
                             GATT_ERR_UNLIKELY, NULL);
      bta_gattc_continue(p_clcb);
      return;
    case MTU_EXCHANGE_ALREADY_DONE:
      /* Check if MTU is not already set, if so, just report it back to the user
       * and continue with other requests.
       */
      GATTC_UpdateUserAttMtuIfNeeded(p_clcb->bda, p_clcb->transport,
                                     p_data->api_mtu.mtu);
      bta_gattc_send_mtu_response(p_clcb, p_data, current_mtu);
      return;
    case MTU_EXCHANGE_IN_PROGRESS:
      log::info("Enqueue MTU Request  - waiting for response on p_clcb {}",
                fmt::ptr(p_clcb));
      /* MTU request is in progress and this one will not be sent to remote
       * device. Just push back on the queue and response will be sent up to
       * the upper layer when MTU Exchange will be completed.
       */
      p_clcb->p_q_cmd_queue.push_back(p_data);
      return;

    case MTU_EXCHANGE_NOT_DONE_YET:
      /* OK to proceed */
      break;
  }

  if (bta_gattc_enqueue(p_clcb, p_data) == ENQUEUED_FOR_LATER) return;

  tGATT_STATUS status =
      GATTC_ConfigureMTU(p_clcb->bta_conn_id, p_data->api_mtu.mtu);

  /* if failed, return callback here */
  if (status != GATT_SUCCESS && status != GATT_CMD_STARTED) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_CONFIG, status,
                           NULL);
    bta_gattc_continue(p_clcb);
  }
}

void bta_gattc_start_discover_internal(tBTA_GATTC_CLCB* p_clcb) {
  if (p_clcb->transport == BT_TRANSPORT_LE)
    L2CA_LockBleConnParamsForServiceDiscovery(p_clcb->p_srcb->server_bda, true);

  bta_gattc_init_cache(p_clcb->p_srcb);
  p_clcb->status = bta_gattc_discover_pri_service(
      p_clcb->bta_conn_id, p_clcb->p_srcb, GATT_DISC_SRVC_ALL);
  if (p_clcb->status != GATT_SUCCESS) {
    log::error("discovery on server failed");
    bta_gattc_reset_discover_st(p_clcb->p_srcb, p_clcb->status);
  } else
    p_clcb->disc_active = true;
}

static void bta_gattc_continue_with_version_and_cache_known(
    tBTA_GATTC_CLCB* p_clcb, RobustCachingSupport cache_support,
    bool is_svc_chg);

/** Start a discovery on server */
void bta_gattc_start_discover(tBTA_GATTC_CLCB* p_clcb,
                              UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  log::verbose("conn_id:{} p_clcb->p_srcb->state:{}",
               loghex(p_clcb->bta_conn_id), p_clcb->p_srcb->state);

  if (((p_clcb->p_q_cmd == NULL ||
        p_clcb->auto_update == BTA_GATTC_REQ_WAITING) &&
       p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE) ||
      p_clcb->p_srcb->state == BTA_GATTC_SERV_DISC)
  /* no pending operation, start discovery right away */
  {
    p_clcb->auto_update = BTA_GATTC_NO_SCHEDULE;

    if (p_clcb->p_srcb == NULL) {
      log::error("unknown device, can not start discovery");
      return;
    }

    /* set all srcb related clcb into discovery ST */
    bta_gattc_set_discover_st(p_clcb->p_srcb);

    // Before clear mask, set is_svc_chg to
    // 1. true, invoked by service changed indication
    // 2. false, invoked by connect API
    bool is_svc_chg = p_clcb->p_srcb->srvc_hdl_chg;

    /* clear the service change mask */
    p_clcb->p_srcb->srvc_hdl_chg = false;
    p_clcb->p_srcb->update_count = 0;
    p_clcb->p_srcb->state = BTA_GATTC_SERV_DISC_ACT;
    p_clcb->p_srcb->disc_blocked_waiting_on_version = false;

    auto cache_support =
        GetRobustCachingSupport(p_clcb, p_clcb->p_srcb->gatt_database);
    if (cache_support == RobustCachingSupport::W4_REMOTE_VERSION) {
      log::info(
          "Pausing service discovery till remote version is read conn_id:{}",
          p_clcb->bta_conn_id);
      p_clcb->p_srcb->disc_blocked_waiting_on_version = true;
      p_clcb->p_srcb->blocked_conn_id = p_clcb->bta_conn_id;
      return;
    }

    bta_gattc_continue_with_version_and_cache_known(p_clcb, cache_support,
                                                    is_svc_chg);
  }
  /* pending operation, wait until it finishes */
  else {
    p_clcb->auto_update = BTA_GATTC_DISC_WAITING;

    if (p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE)
      p_clcb->state = BTA_GATTC_CONN_ST; /* set clcb state */
  }
}

void bta_gattc_continue_discovery_if_needed(const RawAddress& bd_addr,
                                            uint16_t /* acl_handle */) {
  tBTA_GATTC_SERV* p_srcb = bta_gattc_find_srvr_cache(bd_addr);
  if (!p_srcb || !p_srcb->disc_blocked_waiting_on_version) {
    return;
  }

  uint16_t conn_id = p_srcb->blocked_conn_id;

  p_srcb->disc_blocked_waiting_on_version = false;
  p_srcb->blocked_conn_id = 0;

  log::info("Received remote version, continue service discovery for {}",
            ADDRESS_TO_LOGGABLE_CSTR(bd_addr));

  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  if (!p_clcb) {
    log::error("Can't find CLCB to continue service discovery, id:{}", conn_id);
    return;
  }

  bool is_svc_chg = p_clcb->p_srcb->srvc_hdl_chg;

  auto cache_support =
      GetRobustCachingSupport(p_clcb, p_clcb->p_srcb->gatt_database);
  bta_gattc_continue_with_version_and_cache_known(p_clcb, cache_support,
                                                  is_svc_chg);
}

void bta_gattc_continue_with_version_and_cache_known(
    tBTA_GATTC_CLCB* p_clcb, RobustCachingSupport cache_support,
    bool is_svc_chg) {
  if (cache_support == RobustCachingSupport::UNSUPPORTED ||
      (IS_FLAG_ENABLED(skip_unknown_robust_caching) &&
       cache_support == RobustCachingSupport::UNKNOWN)) {
    // Skip initial DB hash read if no DB hash is known, or if
    // we have strong reason (due to interop,
    // or a prior discovery) to believe that it is unsupported.
    p_clcb->p_srcb->srvc_hdl_db_hash = false;
  }

  /* read db hash if db hash characteristic exists */
  if (bta_gattc_is_robust_caching_enabled() &&
      p_clcb->p_srcb->srvc_hdl_db_hash &&
      bta_gattc_read_db_hash(p_clcb, is_svc_chg)) {
    log::info("pending service discovery, read db hash first conn_id:{}",
              loghex(p_clcb->bta_conn_id));
    p_clcb->p_srcb->srvc_hdl_db_hash = false;
    return;
  }
  bta_gattc_start_discover_internal(p_clcb);
}

/** discovery on server is finished */
void bta_gattc_disc_cmpl(tBTA_GATTC_CLCB* p_clcb,
                         UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  const tBTA_GATTC_DATA* p_q_cmd = p_clcb->p_q_cmd;

  log::verbose("conn_id={}", loghex(p_clcb->bta_conn_id));

  if (p_clcb->transport == BT_TRANSPORT_LE) {
    L2CA_LockBleConnParamsForServiceDiscovery(p_clcb->p_srcb->server_bda,
                                              false);
  }
  p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
  p_clcb->disc_active = false;

  if (p_clcb->status != GATT_SUCCESS) {
    /* clean up cache */
    if (p_clcb->p_srcb) {
      p_clcb->p_srcb->gatt_database.Clear();
    }

    /* used to reset cache in application */
    bta_gattc_cache_reset(p_clcb->p_srcb->server_bda);
  }

  if (p_clcb->p_srcb) {
    p_clcb->p_srcb->pending_discovery.Clear();
  }

  if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING) {
    /* start discovery again */
    p_clcb->auto_update = BTA_GATTC_REQ_WAITING;
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
  }
  /* get any queued command to proceed */
  else if (p_q_cmd != NULL) {
    p_clcb->p_q_cmd = NULL;
    /* execute pending operation of link block still present */
    if (L2CA_IsLinkEstablished(p_clcb->p_srcb->server_bda, p_clcb->transport)) {
      bta_gattc_sm_execute(p_clcb, p_q_cmd->hdr.event, p_q_cmd);
    }
    /* if the command executed requeued the cmd, we don't
     * want to free the underlying buffer that's being
     * referenced by p_clcb->p_q_cmd
     */
    if (!bta_gattc_is_data_queued(p_clcb, p_q_cmd)) {
      osi_free_and_reset((void**)&p_q_cmd);
    }
  } else {
    bta_gattc_continue(p_clcb);
  }

  if (p_clcb->p_rcb->p_cback) {
    tBTA_GATTC bta_gattc;
    bta_gattc.remote_bda = p_clcb->p_srcb->server_bda;
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_SRVC_DISC_DONE_EVT, &bta_gattc);
  }
}

/** Read an attribute */
void bta_gattc_read(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  if (bta_gattc_enqueue(p_clcb, p_data) == ENQUEUED_FOR_LATER) return;

  tGATT_STATUS status;
  if (p_data->api_read.handle != 0) {
    tGATT_READ_PARAM read_param;
    memset(&read_param, 0, sizeof(tGATT_READ_PARAM));
    read_param.by_handle.handle = p_data->api_read.handle;
    read_param.by_handle.auth_req = p_data->api_read.auth_req;
    status = GATTC_Read(p_clcb->bta_conn_id, GATT_READ_BY_HANDLE, &read_param);
  } else {
    tGATT_READ_PARAM read_param;
    memset(&read_param, 0, sizeof(tGATT_READ_BY_TYPE));

    read_param.char_type.s_handle = p_data->api_read.s_handle;
    read_param.char_type.e_handle = p_data->api_read.e_handle;
    read_param.char_type.uuid = p_data->api_read.uuid;
    read_param.char_type.auth_req = p_data->api_read.auth_req;
    status = GATTC_Read(p_clcb->bta_conn_id, GATT_READ_BY_TYPE, &read_param);
  }

  /* read fail */
  if (status != GATT_SUCCESS) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_READ, status,
                           NULL);
    bta_gattc_continue(p_clcb);
  }
}

/** read multiple */
void bta_gattc_read_multi(tBTA_GATTC_CLCB* p_clcb,
                          const tBTA_GATTC_DATA* p_data) {
  if (bta_gattc_enqueue(p_clcb, p_data) == ENQUEUED_FOR_LATER) return;

  if (p_data->api_read_multi.handles.num_attr > GATT_MAX_READ_MULTI_HANDLES) {
    log::error("api_read_multi.num_attr > GATT_MAX_READ_MULTI_HANDLES");
    return;
  }

  tGATT_READ_PARAM read_param;
  memset(&read_param, 0, sizeof(tGATT_READ_PARAM));

  read_param.read_multiple.num_handles =
      p_data->api_read_multi.handles.num_attr;
  read_param.read_multiple.auth_req = p_data->api_read_multi.auth_req;
  read_param.read_multiple.variable_len = p_data->api_read_multi.variable_len;
  memcpy(&read_param.read_multiple.handles,
         p_data->api_read_multi.handles.handles,
         sizeof(uint16_t) * p_data->api_read_multi.handles.num_attr);

  tGATT_READ_TYPE read_type = (read_param.read_multiple.variable_len)
                                  ? GATT_READ_MULTIPLE_VAR_LEN
                                  : GATT_READ_MULTIPLE;
  tGATT_STATUS status = GATTC_Read(p_clcb->bta_conn_id, read_type, &read_param);
  /* read fail */
  if (status != GATT_SUCCESS) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_READ, status,
                           NULL);
    bta_gattc_continue(p_clcb);
  }
}

/** Write an attribute */
void bta_gattc_write(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  if (bta_gattc_enqueue(p_clcb, p_data) == ENQUEUED_FOR_LATER) return;

  tGATT_STATUS status = GATT_SUCCESS;
  tGATT_VALUE attr;

  attr.conn_id = p_clcb->bta_conn_id;
  attr.handle = p_data->api_write.handle;
  attr.offset = p_data->api_write.offset;
  attr.len = p_data->api_write.len;
  attr.auth_req = p_data->api_write.auth_req;

  /* Before coping to the fixed array, make sure it fits. */
  if (attr.len > GATT_MAX_ATTR_LEN) {
    status = GATT_INVALID_ATTR_LEN;
  } else {
    if (p_data->api_write.p_value)
      memcpy(attr.value, p_data->api_write.p_value, p_data->api_write.len);

    status =
        GATTC_Write(p_clcb->bta_conn_id, p_data->api_write.write_type, &attr);
  }

  /* write fail */
  if (status != GATT_SUCCESS) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_WRITE, status,
                           NULL);
    bta_gattc_continue(p_clcb);
  }
}

/** send execute write */
void bta_gattc_execute(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  if (bta_gattc_enqueue(p_clcb, p_data) == ENQUEUED_FOR_LATER) return;

  tGATT_STATUS status =
      GATTC_ExecuteWrite(p_clcb->bta_conn_id, p_data->api_exec.is_execute);
  if (status != GATT_SUCCESS) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_EXE_WRITE, status,
                           NULL);
    bta_gattc_continue(p_clcb);
  }
}

/** send handle value confirmation */
void bta_gattc_confirm(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  uint16_t cid = p_data->api_confirm.cid;

  if (GATTC_SendHandleValueConfirm(p_data->api_confirm.hdr.layer_specific,
                                   cid) != GATT_SUCCESS) {
    log::error("to cid={} failed", loghex(cid));
  } else {
    /* if over BR_EDR, inform PM for mode change */
    if (p_clcb->transport == BT_TRANSPORT_BR_EDR) {
      bta_sys_busy(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
      bta_sys_idle(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
    }
  }
}

/** read complete */
static void bta_gattc_read_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                const tBTA_GATTC_OP_CMPL* p_data) {
  void* my_cb_data;

  if (!p_clcb->p_q_cmd->api_read.is_multi_read) {
    GATT_READ_OP_CB cb = p_clcb->p_q_cmd->api_read.read_cb;
    my_cb_data = p_clcb->p_q_cmd->api_read.read_cb_data;

    /* if it was read by handle, return the handle requested, if read by UUID,
     * use handle returned from remote
     */
    uint16_t handle = p_clcb->p_q_cmd->api_read.handle;
    if (handle == 0) handle = p_data->p_cmpl->att_value.handle;

    osi_free_and_reset((void**)&p_clcb->p_q_cmd);

    if (cb) {
      cb(p_clcb->bta_conn_id, p_data->status, handle,
         p_data->p_cmpl->att_value.len, p_data->p_cmpl->att_value.value,
         my_cb_data);
    }
  } else {
    GATT_READ_MULTI_OP_CB cb = p_clcb->p_q_cmd->api_read_multi.read_cb;
    my_cb_data = p_clcb->p_q_cmd->api_read_multi.read_cb_data;
    tBTA_GATTC_MULTI handles = p_clcb->p_q_cmd->api_read_multi.handles;

    osi_free_and_reset((void**)&p_clcb->p_q_cmd);

    if (cb) {
      cb(p_clcb->bta_conn_id, p_data->status, handles,
         p_data->p_cmpl->att_value.len, p_data->p_cmpl->att_value.value,
         my_cb_data);
    }
  }
}

/** write complete */
static void bta_gattc_write_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                 const tBTA_GATTC_OP_CMPL* p_data) {
  GATT_WRITE_OP_CB cb = p_clcb->p_q_cmd->api_write.write_cb;
  void* my_cb_data = p_clcb->p_q_cmd->api_write.write_cb_data;

  if (cb) {
    if (p_data->status == 0 &&
        p_clcb->p_q_cmd->api_write.write_type == BTA_GATTC_WRITE_PREPARE) {
      log::debug("Handling prepare write success response: handle 0x{:04x}",
                 p_data->p_cmpl->att_value.handle);
      /* If this is successful Prepare write, lets provide to the callback the
       * data provided by server */
      cb(p_clcb->bta_conn_id, p_data->status, p_data->p_cmpl->att_value.handle,
         p_data->p_cmpl->att_value.len, p_data->p_cmpl->att_value.value,
         my_cb_data);
    } else {
      log::debug("Handling write response type: {}: handle 0x{:04x}",
                 p_clcb->p_q_cmd->api_write.write_type,
                 p_data->p_cmpl->att_value.handle);
      /* Otherwise, provide data which were intended to write. */
      cb(p_clcb->bta_conn_id, p_data->status, p_data->p_cmpl->att_value.handle,
         p_clcb->p_q_cmd->api_write.len, p_clcb->p_q_cmd->api_write.p_value,
         my_cb_data);
    }
  }

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);
}

/** execute write complete */
static void bta_gattc_exec_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                const tBTA_GATTC_OP_CMPL* p_data) {
  tBTA_GATTC cb_data;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);
  p_clcb->status = GATT_SUCCESS;

  /* execute complete, callback */
  cb_data.exec_cmpl.conn_id = p_clcb->bta_conn_id;
  cb_data.exec_cmpl.status = p_data->status;

  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_EXEC_EVT, &cb_data);
}

/** configure MTU operation complete */
static void bta_gattc_cfg_mtu_cmpl(tBTA_GATTC_CLCB* p_clcb,
                                   const tBTA_GATTC_OP_CMPL* p_data) {
  GATT_CONFIGURE_MTU_OP_CB cb = p_clcb->p_q_cmd->api_mtu.mtu_cb;
  void* my_cb_data = p_clcb->p_q_cmd->api_mtu.mtu_cb_data;
  tBTA_GATTC cb_data;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);

  if (p_data->p_cmpl && p_data->status == GATT_SUCCESS)
    p_clcb->p_srcb->mtu = p_data->p_cmpl->mtu;

  /* configure MTU complete, callback */
  p_clcb->status = p_data->status;
  cb_data.cfg_mtu.conn_id = p_clcb->bta_conn_id;
  cb_data.cfg_mtu.status = p_data->status;
  cb_data.cfg_mtu.mtu = p_clcb->p_srcb->mtu;

  if (cb) {
    cb(p_clcb->bta_conn_id, p_data->status, my_cb_data);
  }

  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CFG_MTU_EVT, &cb_data);
}

/** operation completed */
void bta_gattc_op_cmpl(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  if (p_clcb->p_q_cmd == NULL) {
    log::error("No pending command gatt client command");
    return;
  }

  const tGATTC_OPTYPE op = p_data->op_cmpl.op_code;
  switch (op) {
    case GATTC_OPTYPE_READ:
    case GATTC_OPTYPE_WRITE:
    case GATTC_OPTYPE_EXE_WRITE:
    case GATTC_OPTYPE_CONFIG:
      break;

    case GATTC_OPTYPE_NONE:
    case GATTC_OPTYPE_DISCOVERY:
    case GATTC_OPTYPE_NOTIFICATION:
    case GATTC_OPTYPE_INDICATION:
    default:
      log::error("unexpected operation, ignored");
      return;
  }

  if (p_clcb->p_q_cmd->hdr.event !=
          bta_gattc_opcode_to_int_evt[op - GATTC_OPTYPE_READ] &&
      (p_clcb->p_q_cmd->hdr.event != BTA_GATTC_API_READ_MULTI_EVT ||
       op != GATTC_OPTYPE_READ)) {
    uint8_t mapped_op =
        p_clcb->p_q_cmd->hdr.event - BTA_GATTC_API_READ_EVT + GATTC_OPTYPE_READ;

    if (mapped_op > GATTC_OPTYPE_INDICATION) mapped_op = 0;

    log::error("expect op:({} :0x{:04x}), receive unexpected operation ({}).",
               bta_gattc_op_code_name[mapped_op], p_clcb->p_q_cmd->hdr.event,
               bta_gattc_op_code_name[op]);
    return;
  }

  /* Except for MTU configuration, discard responses if service change
   * indication is received before operation completed
   */
  if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING &&
      p_clcb->p_srcb->srvc_hdl_chg && op != GATTC_OPTYPE_CONFIG) {
    log::verbose(
        "Discard all responses when service change indication is received.");
    // TODO Fix constness
    const_cast<tBTA_GATTC_DATA*>(p_data)->op_cmpl.status = GATT_ERROR;
  }

  /* service handle change void the response, discard it */
  if (op == GATTC_OPTYPE_READ) {
    bta_gattc_read_cmpl(p_clcb, &p_data->op_cmpl);
  } else if (op == GATTC_OPTYPE_WRITE) {
    bta_gattc_write_cmpl(p_clcb, &p_data->op_cmpl);
  } else if (op == GATTC_OPTYPE_EXE_WRITE) {
    bta_gattc_exec_cmpl(p_clcb, &p_data->op_cmpl);
  } else if (op == GATTC_OPTYPE_CONFIG) {
    bta_gattc_cfg_mtu_cmpl(p_clcb, &p_data->op_cmpl);

    /* If there are more clients waiting for the MTU results on the same device,
     * lets trigger them now.
     */

    auto outstanding_conn_ids =
        GATTC_GetAndRemoveListOfConnIdsWaitingForMtuRequest(p_clcb->bda);
    for (auto conn_id : outstanding_conn_ids) {
      tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
      log::debug("Continue MTU request clcb {}", fmt::ptr(p_clcb));
      if (p_clcb) {
        log::debug("Continue MTU request for client conn_id=0x{:04x}", conn_id);
        bta_gattc_continue(p_clcb);
      }
    }
  }

  // If receive DATABASE_OUT_OF_SYNC error code, bta_gattc should start service
  // discovery immediately
  if (bta_gattc_is_robust_caching_enabled() &&
      p_data->op_cmpl.status == GATT_DATABASE_OUT_OF_SYNC) {
    log::info("DATABASE_OUT_OF_SYNC, re-discover service");
    p_clcb->auto_update = BTA_GATTC_REQ_WAITING;
    /* request read db hash first */
    p_clcb->p_srcb->srvc_hdl_db_hash = true;
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    return;
  }

  if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING) {
    p_clcb->auto_update = BTA_GATTC_REQ_WAITING;

    /* request read db hash first */
    if (bta_gattc_is_robust_caching_enabled()) {
      p_clcb->p_srcb->srvc_hdl_db_hash = true;
    }

    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    return;
  }

  bta_gattc_continue(p_clcb);
}

/** start a search in the local server cache */
void bta_gattc_search(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  tGATT_STATUS status = GATT_INTERNAL_ERROR;
  tBTA_GATTC cb_data;
  log::verbose("conn_id={}", loghex(p_clcb->bta_conn_id));
  if (p_clcb->p_srcb && !p_clcb->p_srcb->gatt_database.IsEmpty()) {
    status = GATT_SUCCESS;
    /* search the local cache of a server device */
    bta_gattc_search_service(p_clcb, p_data->api_search.p_srvc_uuid);
  }
  cb_data.search_cmpl.status = status;
  cb_data.search_cmpl.conn_id = p_clcb->bta_conn_id;

  /* end of search or no server cache available */
  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_SEARCH_CMPL_EVT, &cb_data);
}

/** enqueue a command into control block, usually because discovery operation is
 * busy */
void bta_gattc_q_cmd(tBTA_GATTC_CLCB* p_clcb, const tBTA_GATTC_DATA* p_data) {
  bta_gattc_enqueue(p_clcb, p_data);
}

/** report API call failure back to apps */
void bta_gattc_fail(tBTA_GATTC_CLCB* p_clcb,
                    UNUSED_ATTR const tBTA_GATTC_DATA* p_data) {
  if (p_clcb->status == GATT_SUCCESS) {
    log::error("operation not supported at current state {}", p_clcb->state);
  }
}

/* De-Register a GATT client application with BTA completed */
static void bta_gattc_deregister_cmpl(tBTA_GATTC_RCB* p_clreg) {
  tGATT_IF client_if = p_clreg->client_if;
  tBTA_GATTC cb_data;
  tBTA_GATTC_CBACK* p_cback = p_clreg->p_cback;

  memset(&cb_data, 0, sizeof(tBTA_GATTC));

  GATT_Deregister(p_clreg->client_if);
  memset(p_clreg, 0, sizeof(tBTA_GATTC_RCB));

  cb_data.reg_oper.client_if = client_if;
  cb_data.reg_oper.status = GATT_SUCCESS;

  if (p_cback) /* callback with de-register event */
    (*p_cback)(BTA_GATTC_DEREG_EVT, &cb_data);

  if (bta_gattc_num_reg_app() == 0 &&
      bta_gattc_cb.state == BTA_GATTC_STATE_DISABLING) {
    bta_gattc_cb.state = BTA_GATTC_STATE_DISABLED;
  }
}

/** callback functions to GATT client stack */
static void bta_gattc_conn_cback(tGATT_IF gattc_if, const RawAddress& bdaddr,
                                 uint16_t conn_id, bool connected,
                                 tGATT_DISCONN_REASON reason,
                                 tBT_TRANSPORT transport) {
  if (connected) {
    log::info("Connected client_if:{} addr:{}, transport:{} reason:{}",
              gattc_if, ADDRESS_TO_LOGGABLE_CSTR(bdaddr),
              bt_transport_text(transport),
              gatt_disconnection_reason_text(reason));
    btif_debug_conn_state(bdaddr, BTIF_DEBUG_CONNECTED, GATT_CONN_OK);
  } else {
    log::info("Disconnected att_id:{} addr:{}, transport:{} reason:{}",
              gattc_if, ADDRESS_TO_LOGGABLE_CSTR(bdaddr),
              bt_transport_text(transport),
              gatt_disconnection_reason_text(reason));
    btif_debug_conn_state(bdaddr, BTIF_DEBUG_DISCONNECTED, GATT_CONN_OK);
  }

  tBTA_GATTC_DATA* p_buf =
      (tBTA_GATTC_DATA*)osi_calloc(sizeof(tBTA_GATTC_DATA));
  p_buf->int_conn.hdr.event =
      connected ? BTA_GATTC_INT_CONN_EVT : BTA_GATTC_INT_DISCONN_EVT;
  p_buf->int_conn.hdr.layer_specific = conn_id;
  p_buf->int_conn.client_if = gattc_if;
  p_buf->int_conn.role = L2CA_GetBleConnRole(bdaddr);
  p_buf->int_conn.reason = reason;
  p_buf->int_conn.transport = transport;
  p_buf->int_conn.remote_bda = bdaddr;

  bta_sys_sendmsg(p_buf);
}

/** encryption complete callback function to GATT client stack */
static void bta_gattc_enc_cmpl_cback(tGATT_IF gattc_if, const RawAddress& bda) {
  tBTA_GATTC_CLCB* p_clcb =
      bta_gattc_find_clcb_by_cif(gattc_if, bda, BT_TRANSPORT_LE);

  if (p_clcb == NULL) return;

  log::verbose("cif:{}", gattc_if);

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&bta_gattc_process_enc_cmpl, gattc_if, bda));
}

/** process refresh API to delete cache and start a new discovery if currently
 * connected */
void bta_gattc_process_api_refresh(const RawAddress& remote_bda) {
  tBTA_GATTC_SERV* p_srvc_cb = bta_gattc_find_srvr_cache(remote_bda);
  if (p_srvc_cb) {
    /* try to find a CLCB */
    if (p_srvc_cb->connected && p_srvc_cb->num_clcb != 0) {
      bool found = false;
      tBTA_GATTC_CLCB* p_clcb = &bta_gattc_cb.clcb[0];
      for (uint8_t i = 0; i < BTA_GATTC_CLCB_MAX; i++, p_clcb++) {
        if (p_clcb->in_use && p_clcb->p_srcb == p_srvc_cb) {
          found = true;
          break;
        }
      }
      if (found) {
        bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
        return;
      }
    }
    /* in all other cases, mark it and delete the cache */

    p_srvc_cb->gatt_database.Clear();
  }

  /* used to reset cache in application */
  bta_gattc_cache_reset(remote_bda);
}

/** process service change indication */
static bool bta_gattc_process_srvc_chg_ind(uint16_t conn_id,
                                           tBTA_GATTC_RCB* p_clrcb,
                                           tBTA_GATTC_SERV* p_srcb,
                                           tBTA_GATTC_CLCB* p_clcb,
                                           tBTA_GATTC_NOTIFY* p_notify,
                                           tGATT_VALUE* att_value) {
  Uuid gattp_uuid = Uuid::From16Bit(UUID_SERVCLASS_GATT_SERVER);
  Uuid srvc_chg_uuid = Uuid::From16Bit(GATT_UUID_GATT_SRV_CHGD);

  if (p_srcb->gatt_database.IsEmpty() && p_srcb->state == BTA_GATTC_SERV_IDLE) {
    gatt::Database db = bta_gattc_cache_load(p_srcb->server_bda);
    if (!db.IsEmpty()) {
      p_srcb->gatt_database = db;
    }
  }

  const gatt::Characteristic* p_char =
      bta_gattc_get_characteristic_srcb(p_srcb, p_notify->handle);
  if (!p_char) return false;
  const gatt::Service* p_svc =
      bta_gattc_get_service_for_handle_srcb(p_srcb, p_char->value_handle);
  if (!p_svc || p_svc->uuid != gattp_uuid || p_char->uuid != srvc_chg_uuid) {
    return false;
  }

  if (att_value->len != BTA_GATTC_SERVICE_CHANGED_LEN) {
    log::error("received malformed service changed indication, skipping");
    return false;
  }

  uint8_t* p = att_value->value;
  uint16_t s_handle = ((uint16_t)(*(p)) + (((uint16_t)(*(p + 1))) << 8));
  uint16_t e_handle = ((uint16_t)(*(p + 2)) + (((uint16_t)(*(p + 3))) << 8));

  log::error("service changed s_handle={}, e_handle={}", loghex(s_handle),
             loghex(e_handle));

  /* mark service handle change pending */
  p_srcb->srvc_hdl_chg = true;
  /* clear up all notification/indication registration */
  bta_gattc_clear_notif_registration(p_srcb, conn_id, s_handle, e_handle);
  /* service change indication all received, do discovery update */
  if (++p_srcb->update_count == bta_gattc_num_reg_app()) {
    /* not an opened connection; or connection busy */
    /* search for first available clcb and start discovery */
    if (p_clcb == NULL || (p_clcb && p_clcb->p_q_cmd != NULL)) {
      for (size_t i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
        if (bta_gattc_cb.clcb[i].in_use &&
            bta_gattc_cb.clcb[i].p_srcb == p_srcb &&
            bta_gattc_cb.clcb[i].p_q_cmd == NULL) {
          p_clcb = &bta_gattc_cb.clcb[i];
          break;
        }
      }
    }
    /* send confirmation here if this is an indication, it should always be */
    GATTC_SendHandleValueConfirm(conn_id, p_notify->cid);

    /* if connection available, refresh cache by doing discovery now */
    if (p_clcb) {
      /* request read db hash first */
      if (bta_gattc_is_robust_caching_enabled()) {
        p_srcb->srvc_hdl_db_hash = true;
      }
      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    }
  }

  /* notify applicationf or service change */
  if (p_clrcb->p_cback) {
    tBTA_GATTC bta_gattc;
    bta_gattc.service_changed.remote_bda = p_srcb->server_bda;
    bta_gattc.service_changed.conn_id = conn_id;
    (*p_clrcb->p_cback)(BTA_GATTC_SRVC_CHG_EVT, &bta_gattc);
  }

  return true;
}

/** process all non-service change indication/notification */
static void bta_gattc_proc_other_indication(tBTA_GATTC_CLCB* p_clcb, uint8_t op,
                                            tGATT_CL_COMPLETE* p_data,
                                            tBTA_GATTC_NOTIFY* p_notify) {
  log::verbose("check p_data->att_value.handle={} p_data->handle={}",
               p_data->att_value.handle, p_data->handle);
  log::verbose("is_notify {}", p_notify->is_notify);

  p_notify->is_notify = (op == GATTC_OPTYPE_INDICATION) ? false : true;
  p_notify->len = p_data->att_value.len;
  p_notify->bda = p_clcb->bda;
  memcpy(p_notify->value, p_data->att_value.value, p_data->att_value.len);
  p_notify->conn_id = p_clcb->bta_conn_id;

  if (p_clcb->p_rcb->p_cback) {
    tBTA_GATTC bta_gattc;
    bta_gattc.notify = *p_notify;
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_NOTIF_EVT, &bta_gattc);
  }
}

/** process indication/notification */
static void bta_gattc_process_indicate(uint16_t conn_id, tGATTC_OPTYPE op,
                                       tGATT_CL_COMPLETE* p_data) {
  uint16_t handle = p_data->att_value.handle;
  tBTA_GATTC_NOTIFY notify;
  RawAddress remote_bda;
  tGATT_IF gatt_if;
  tBT_TRANSPORT transport;

  if (!GATT_GetConnectionInfor(conn_id, &gatt_if, remote_bda, &transport)) {
    log::error("indication/notif for unknown app");
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, p_data->cid);
    return;
  }

  tBTA_GATTC_RCB* p_clrcb = bta_gattc_cl_get_regcb(gatt_if);
  if (p_clrcb == NULL) {
    log::error("indication/notif for unregistered app");
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, p_data->cid);
    return;
  }

  tBTA_GATTC_SERV* p_srcb = bta_gattc_find_srcb(remote_bda);
  if (p_srcb == NULL) {
    log::error("indication/notif for unknown device, ignore");
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, p_data->cid);
    return;
  }

  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  notify.handle = handle;
  notify.cid = p_data->cid;

  /* if service change indication/notification, don't forward to application */
  if (bta_gattc_process_srvc_chg_ind(conn_id, p_clrcb, p_srcb, p_clcb, &notify,
                                     &p_data->att_value))
    return;

  /* if app registered for the notification */
  if (bta_gattc_check_notif_registry(p_clrcb, p_srcb, &notify)) {
    /* connection not open yet */
    if (p_clcb == NULL) {
      p_clcb = bta_gattc_clcb_alloc(gatt_if, remote_bda, transport);

      if (p_clcb == NULL) {
        log::error("No resources");
        return;
      }

      p_clcb->bta_conn_id = conn_id;
      p_clcb->transport = transport;

      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT, NULL);
    }

    if (p_clcb != NULL)
      bta_gattc_proc_other_indication(p_clcb, op, p_data, &notify);
  }
  /* no one intersted and need ack? */
  else if (op == GATTC_OPTYPE_INDICATION) {
    log::verbose("no one interested, ack now");
    GATTC_SendHandleValueConfirm(conn_id, p_data->cid);
  }
}

/** client operation complete callback register with BTE GATT */
static void bta_gattc_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                 tGATT_STATUS status,
                                 tGATT_CL_COMPLETE* p_data) {
  log::verbose("conn_id:{} op:{} status:{}", conn_id, op, status);

  /* notification and indication processed right away */
  if (op == GATTC_OPTYPE_NOTIFICATION || op == GATTC_OPTYPE_INDICATION) {
    bta_gattc_process_indicate(conn_id, op, p_data);
    return;
  }
  /* for all other operation, not expected if w/o connection */
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb) {
    log::error("unknown conn_id={} ignore data", loghex(conn_id));
    return;
  }

  /* if over BR_EDR, inform PM for mode change */
  if (p_clcb->transport == BT_TRANSPORT_BR_EDR) {
    bta_sys_busy(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
    bta_sys_idle(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
  }

  bta_gattc_cmpl_sendmsg(conn_id, op, status, p_data);
}

/** client operation complete send message */
void bta_gattc_cmpl_sendmsg(uint16_t conn_id, tGATTC_OPTYPE op,
                            tGATT_STATUS status, tGATT_CL_COMPLETE* p_data) {
  const size_t len = sizeof(tBTA_GATTC_OP_CMPL) + sizeof(tGATT_CL_COMPLETE);
  tBTA_GATTC_OP_CMPL* p_buf = (tBTA_GATTC_OP_CMPL*)osi_calloc(len);

  p_buf->hdr.event = BTA_GATTC_OP_CMPL_EVT;
  p_buf->hdr.layer_specific = conn_id;
  p_buf->status = status;
  p_buf->op_code = op;

  if (p_data) {
    p_buf->p_cmpl = (tGATT_CL_COMPLETE*)(p_buf + 1);
    memcpy(p_buf->p_cmpl, p_data, sizeof(tGATT_CL_COMPLETE));
  }

  bta_sys_sendmsg(p_buf);
}

/** congestion callback for BTA GATT client */
static void bta_gattc_cong_cback(uint16_t conn_id, bool congested) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (!p_clcb || !p_clcb->p_rcb->p_cback) return;

  tBTA_GATTC cb_data;
  cb_data.congest.conn_id = conn_id;
  cb_data.congest.congested = congested;

  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CONGEST_EVT, &cb_data);
}

static void bta_gattc_phy_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                       uint8_t tx_phy, uint8_t rx_phy,
                                       tGATT_STATUS status) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(gatt_if);

  if (!p_clreg || !p_clreg->p_cback) {
    log::error("client_if={} not found", gatt_if);
    return;
  }

  tBTA_GATTC cb_data;
  cb_data.phy_update.conn_id = conn_id;
  cb_data.phy_update.server_if = gatt_if;
  cb_data.phy_update.tx_phy = tx_phy;
  cb_data.phy_update.rx_phy = rx_phy;
  cb_data.phy_update.status = status;
  (*p_clreg->p_cback)(BTA_GATTC_PHY_UPDATE_EVT, &cb_data);
}

static void bta_gattc_conn_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t interval, uint16_t latency,
                                        uint16_t timeout, tGATT_STATUS status) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(gatt_if);

  if (!p_clreg || !p_clreg->p_cback) {
    log::error("client_if={} not found", gatt_if);
    return;
  }

  tBTA_GATTC cb_data;
  cb_data.conn_update.conn_id = conn_id;
  cb_data.conn_update.interval = interval;
  cb_data.conn_update.latency = latency;
  cb_data.conn_update.timeout = timeout;
  cb_data.conn_update.status = status;
  (*p_clreg->p_cback)(BTA_GATTC_CONN_UPDATE_EVT, &cb_data);
}

static void bta_gattc_subrate_chg_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t subrate_factor,
                                        uint16_t latency, uint16_t cont_num,
                                        uint16_t timeout, tGATT_STATUS status) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(gatt_if);

  if (!p_clreg || !p_clreg->p_cback) {
    log::error("client_if={} not found", gatt_if);
    return;
  }

  tBTA_GATTC cb_data;
  cb_data.subrate_chg.conn_id = conn_id;
  cb_data.subrate_chg.subrate_factor = subrate_factor;
  cb_data.subrate_chg.latency = latency;
  cb_data.subrate_chg.cont_num = cont_num;
  cb_data.subrate_chg.timeout = timeout;
  cb_data.subrate_chg.status = status;
  (*p_clreg->p_cback)(BTA_GATTC_SUBRATE_CHG_EVT, &cb_data);
}
