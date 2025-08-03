/******************************************************************************
 *
 *  Copyright 2016 The Android Open Source Project
 *  Copyright 2002-2012 Broadcom Corporation
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
 *  this file contains the connection interface functions
 *
 ******************************************************************************/

#include <base/functional/callback.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>

#include <cstdint>

#include "bta/include/bta_sec_api.h"
#include "common/init_flags.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/hid/hidd_int.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/stack_metrics_logging.h"
#include "types/raw_address.h"

using namespace bluetooth;

static void hidd_l2cif_connect_ind(const RawAddress& bd_addr, uint16_t cid,
                                   uint16_t psm, uint8_t id);
static void hidd_l2cif_connect_cfm(uint16_t cid, uint16_t result);
static void hidd_l2cif_config_ind(uint16_t cid, tL2CAP_CFG_INFO* p_cfg);
static void hidd_l2cif_config_cfm(uint16_t cid, uint16_t result,
                                  tL2CAP_CFG_INFO* p_cfg);
static void hidd_l2cif_disconnect_ind(uint16_t cid, bool ack_needed);
static void hidd_l2cif_disconnect(uint16_t cid);
static void hidd_l2cif_data_ind(uint16_t cid, BT_HDR* p_msg);
static void hidd_l2cif_cong_ind(uint16_t cid, bool congested);
static void hidd_on_l2cap_error(uint16_t lcid, uint16_t result);
static const tL2CAP_APPL_INFO dev_reg_info = {hidd_l2cif_connect_ind,
                                              hidd_l2cif_connect_cfm,
                                              hidd_l2cif_config_ind,
                                              hidd_l2cif_config_cfm,
                                              hidd_l2cif_disconnect_ind,
                                              NULL,
                                              hidd_l2cif_data_ind,
                                              hidd_l2cif_cong_ind,
                                              NULL,
                                              hidd_on_l2cap_error,
                                              NULL,
                                              NULL,
                                              NULL,
                                              NULL};

/*******************************************************************************
 *
 * Function         hidd_check_config_done
 *
 * Description      Checks if connection is configured and callback can be fired
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_check_config_done() {
  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_state == HID_CONN_STATE_CONFIG) {
    p_hcon->conn_state = HID_CONN_STATE_CONNECTED;

    hd_cb.device.state = HIDD_DEV_CONNECTED;

    hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_OPEN, 0, NULL);

    // send outstanding data on intr
    if (hd_cb.pending_data) {
      L2CA_DataWrite(p_hcon->intr_cid, hd_cb.pending_data);
      hd_cb.pending_data = NULL;
    }
  }
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_connect_ind
 *
 * Description      Handles incoming L2CAP connection (we act as server)
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_connect_ind(const RawAddress& bd_addr, uint16_t cid,
                                   uint16_t psm, uint8_t id) {
  tHID_DEV_DEV_CTB* p_dev;
  bool accept = TRUE;  // accept by default

  log::verbose("psm={:04x} cid={:04x}", psm, cid);

  p_dev = &hd_cb.device;

  if (!hd_cb.allow_incoming) {
    log::warn("incoming connections not allowed, rejecting");
    L2CA_DisconnectReq(cid);

    return;
  }

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  switch (psm) {
    case HID_PSM_INTERRUPT:
      if (p_hcon->ctrl_cid == 0) {
        accept = FALSE;
        log::warn("incoming INTR without CTRL, rejecting");
      }

      if (p_hcon->conn_state != HID_CONN_STATE_CONNECTING_INTR) {
        accept = FALSE;
        log::warn("incoming INTR in invalid state ({}), rejecting",
                  p_hcon->conn_state);
      }

      break;

    case HID_PSM_CONTROL:
      if (p_hcon->conn_state != HID_CONN_STATE_UNUSED) {
        accept = FALSE;
        log::warn("incoming CTRL in invalid state ({}), rejecting",
                  p_hcon->conn_state);
      }

      break;

    default:
      accept = FALSE;
      log::error("received invalid PSM, rejecting");
      break;
  }

  if (!accept) {
    L2CA_DisconnectReq(cid);
    return;
  }

  // for CTRL we need to go through security and we reply in callback from there
  if (psm == HID_PSM_CONTROL) {
    // We are ready to accept connection from this device, since we aren't
    // connected to anything and are in the correct state.
    p_dev->in_use = TRUE;
    p_dev->addr = bd_addr;
    p_dev->state = HIDD_DEV_NO_CONN;

    p_hcon->conn_flags = 0;
    p_hcon->ctrl_cid = cid;
    p_hcon->disc_reason = HID_SUCCESS;
    p_hcon->conn_state = HID_CONN_STATE_CONNECTING_INTR;
    return;
  }

  // for INTR we go directly to config state
  p_hcon->conn_state = HID_CONN_STATE_CONFIG;
  p_hcon->intr_cid = cid;
}

static void hidd_on_l2cap_error(uint16_t lcid, uint16_t result) {
  log::warn("connection of config failed, now disconnect");

  hidd_conn_disconnect();

  // NOTE that the client doesn't care about error code
  hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_CLOSE,
                 HID_L2CAP_CONN_FAIL | (uint32_t)result, NULL);
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_connect_cfm
 *
 * Description      Handles L2CAP connection response (we act as client)
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_connect_cfm(uint16_t cid, uint16_t result) {
  tHID_CONN* p_hcon = &hd_cb.device.conn;

  log::verbose("cid={:04x} result={}", cid, result);

  if (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid) {
    log::warn("unknown cid");
    return;
  }

  if (!(p_hcon->conn_flags & HID_CONN_FLAGS_IS_ORIG) ||
      ((cid == p_hcon->ctrl_cid) &&
       (p_hcon->conn_state != HID_CONN_STATE_CONNECTING_CTRL)) ||
      ((cid == p_hcon->intr_cid) &&
       (p_hcon->conn_state != HID_CONN_STATE_CONNECTING_INTR))) {
    log::warn("unexpected");
    return;
  }

  if (result != L2CAP_CONN_OK) {
    log::error("invoked with non OK status");
    return;
  }

  /* CTRL connect conf */
  if (cid == p_hcon->ctrl_cid) {
    p_hcon->disc_reason = HID_SUCCESS;
    p_hcon->conn_state = HID_CONN_STATE_CONFIG;
  } else {
    p_hcon->conn_state = HID_CONN_STATE_CONFIG;
  }

  return;
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_config_ind
 *
 * Description      Handles incoming L2CAP configuration request
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_config_ind(uint16_t cid, tL2CAP_CFG_INFO* p_cfg) {
  log::verbose("cid={:04x}", cid);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid) {
    log::warn("unknown cid");
    return;
  }

  if ((!p_cfg->mtu_present) || (p_cfg->mtu > HID_DEV_MTU_SIZE))
    p_hcon->rem_mtu_size = HID_DEV_MTU_SIZE;
  else
    p_hcon->rem_mtu_size = p_cfg->mtu;
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_config_cfm
 *
 * Description      Handles incoming L2CAP configuration response
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_config_cfm(uint16_t cid, uint16_t initiator,
                                  tL2CAP_CFG_INFO* p_cfg) {
  hidd_l2cif_config_ind(cid, p_cfg);

  log::verbose("cid={:04x}", cid);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid) {
    log::warn("unknown cid");
    return;
  }

  // update flags
  if (cid == p_hcon->ctrl_cid) {
    if (p_hcon->conn_flags & HID_CONN_FLAGS_IS_ORIG) {
      p_hcon->disc_reason = HID_L2CAP_CONN_FAIL;
      if ((p_hcon->intr_cid =
               L2CA_ConnectReq2(HID_PSM_INTERRUPT, hd_cb.device.addr,
                                BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT)) == 0) {
        hidd_conn_disconnect();
        p_hcon->conn_state = HID_CONN_STATE_UNUSED;

        log::warn("could not start L2CAP connection for INTR");
        hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_CLOSE,
                       HID_ERR_L2CAP_FAILED, NULL);
        log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                                HIDD_ERR_L2CAP_NOT_STARTED_INCOMING,
                            1);
        return;
      } else {
        p_hcon->conn_state = HID_CONN_STATE_CONNECTING_INTR;
      }
    }
  }

  hidd_check_config_done();
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_disconnect_ind
 *
 * Description      Handler incoming L2CAP disconnection request
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_disconnect_ind(uint16_t cid, bool ack_needed) {
  log::verbose("cid={:04x} ack_needed={}", cid, ack_needed);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_state == HID_CONN_STATE_UNUSED ||
      (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid)) {
    log::warn("unknown cid");
    return;
  }

  p_hcon->conn_state = HID_CONN_STATE_DISCONNECTING;

  if (cid == p_hcon->ctrl_cid)
    p_hcon->ctrl_cid = 0;
  else
    p_hcon->intr_cid = 0;

  if ((p_hcon->ctrl_cid == 0) && (p_hcon->intr_cid == 0)) {
    log::verbose("INTR and CTRL disconnected");

    // clean any outstanding data on intr
    if (hd_cb.pending_data) {
      osi_free(hd_cb.pending_data);
      hd_cb.pending_data = NULL;
    }

    hd_cb.device.state = HIDD_DEV_NO_CONN;
    p_hcon->conn_state = HID_CONN_STATE_UNUSED;

    hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_CLOSE, p_hcon->disc_reason,
                   NULL);
  }
}

static void hidd_l2cif_disconnect(uint16_t cid) {
  L2CA_DisconnectReq(cid);

  log::verbose("cid={:04x}", cid);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_state == HID_CONN_STATE_UNUSED ||
      (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid)) {
    log::warn("unknown cid");
    return;
  }

  if (cid == p_hcon->ctrl_cid) {
    p_hcon->ctrl_cid = 0;
  } else {
    p_hcon->intr_cid = 0;

    // now disconnect CTRL
    L2CA_DisconnectReq(p_hcon->ctrl_cid);
    if (bluetooth::common::init_flags::
            clear_hidd_interrupt_cid_on_disconnect_is_enabled()) {
      p_hcon->ctrl_cid = 0;
    }
  }

  if ((p_hcon->ctrl_cid == 0) && (p_hcon->intr_cid == 0)) {
    log::verbose("INTR and CTRL disconnected");

    hd_cb.device.state = HIDD_DEV_NO_CONN;
    p_hcon->conn_state = HID_CONN_STATE_UNUSED;

    if (hd_cb.pending_vc_unplug) {
      hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_VC_UNPLUG,
                     p_hcon->disc_reason, NULL);
      hd_cb.pending_vc_unplug = FALSE;
    } else {
      hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_CLOSE,
                     p_hcon->disc_reason, NULL);
    }
  }
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_cong_ind
 *
 * Description      Handles L2CAP congestion status event
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_cong_ind(uint16_t cid, bool congested) {
  log::verbose("cid={:04x} congested={}", cid, congested);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_state == HID_CONN_STATE_UNUSED ||
      (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid)) {
    log::warn("unknown cid");
    return;
  }

  if (congested) {
    p_hcon->conn_flags |= HID_CONN_FLAGS_CONGESTED;
  } else {
    p_hcon->conn_flags &= ~HID_CONN_FLAGS_CONGESTED;
  }
}

/*******************************************************************************
 *
 * Function         hidd_l2cif_data_ind
 *
 * Description      Handler incoming data on L2CAP channel
 *
 * Returns          void
 *
 ******************************************************************************/
static void hidd_l2cif_data_ind(uint16_t cid, BT_HDR* p_msg) {
  uint8_t* p_data = (uint8_t*)(p_msg + 1) + p_msg->offset;
  uint8_t msg_type, param;
  bool err = FALSE;

  log::verbose("cid={:04x}", cid);

  if (p_msg->len < 1) {
    log::error("Invalid data length, ignore");
    osi_free(p_msg);
    return;
  }

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_state == HID_CONN_STATE_UNUSED ||
      (p_hcon->ctrl_cid != cid && p_hcon->intr_cid != cid)) {
    log::warn("unknown cid");
    osi_free(p_msg);
    return;
  }

  msg_type = HID_GET_TRANS_FROM_HDR(*p_data);
  param = HID_GET_PARAM_FROM_HDR(*p_data);

  if (msg_type == HID_TRANS_DATA && cid == p_hcon->intr_cid) {
    // skip HID header
    p_msg->offset++;
    p_msg->len--;

    hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_INTR_DATA, 0, p_msg);
    return;
  }

  switch (msg_type) {
    case HID_TRANS_GET_REPORT:
      // at this stage we don't know if Report Id shall be included in request
      // so we pass complete packet in callback and let other code analyze this
      hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_GET_REPORT,
                     !!(param & HID_PAR_GET_REP_BUFSIZE_FOLLOWS), p_msg);
      break;

    case HID_TRANS_SET_REPORT:
      // as above
      hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_SET_REPORT, 0, p_msg);
      break;

    case HID_TRANS_GET_IDLE:
      hidd_conn_send_data(HID_CHANNEL_CTRL, HID_TRANS_DATA,
                          HID_PAR_REP_TYPE_OTHER, hd_cb.device.idle_time, 0,
                          NULL);
      osi_free(p_msg);
      break;

    case HID_TRANS_SET_IDLE:
      if (p_msg->len != 2) {
        log::error("invalid len ({}) set idle request received", p_msg->len);
        err = TRUE;
      } else {
        hd_cb.device.idle_time = p_data[1];
        log::verbose("idle_time = {}", hd_cb.device.idle_time);
        if (hd_cb.device.idle_time) {
          log::warn("idle_time of {} ms not supported by HID Device",
                    (hd_cb.device.idle_time * 4));
          err = TRUE;
        }
      }
      if (!err) {
        hidd_conn_send_data(0, HID_TRANS_HANDSHAKE,
                            HID_PAR_HANDSHAKE_RSP_SUCCESS, 0, 0, NULL);
      } else {
        hidd_conn_send_data(0, HID_TRANS_HANDSHAKE,
                            HID_PAR_HANDSHAKE_RSP_ERR_INVALID_PARAM, 0, 0,
                            NULL);
      }
      osi_free(p_msg);
      break;

    case HID_TRANS_GET_PROTOCOL:
      hidd_conn_send_data(HID_CHANNEL_CTRL, HID_TRANS_DATA,
                          HID_PAR_REP_TYPE_OTHER, !hd_cb.device.boot_mode, 0,
                          NULL);
      osi_free(p_msg);
      break;

    case HID_TRANS_SET_PROTOCOL:
      hd_cb.device.boot_mode = !(param & HID_PAR_PROTOCOL_MASK);
      hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_SET_PROTOCOL,
                     param & HID_PAR_PROTOCOL_MASK, NULL);
      hidd_conn_send_data(0, HID_TRANS_HANDSHAKE, HID_PAR_HANDSHAKE_RSP_SUCCESS,
                          0, 0, NULL);
      osi_free(p_msg);
      break;

    case HID_TRANS_CONTROL:
      switch (param) {
        case HID_PAR_CONTROL_SUSPEND:
          hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_SUSPEND, 0, NULL);
          break;

        case HID_PAR_CONTROL_EXIT_SUSPEND:
          hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_EXIT_SUSPEND, 0,
                         NULL);
          break;

        case HID_PAR_CONTROL_VIRTUAL_CABLE_UNPLUG:
          hidd_conn_disconnect();

          // set flag so we can notify properly when disconnected
          hd_cb.pending_vc_unplug = TRUE;
          break;
      }

      osi_free(p_msg);
      break;

    case HID_TRANS_DATA:
    default:
      log::warn("got unsupported msg ({})", msg_type);
      hidd_conn_send_data(0, HID_TRANS_HANDSHAKE,
                          HID_PAR_HANDSHAKE_RSP_ERR_UNSUPPORTED_REQ, 0, 0,
                          NULL);
      osi_free(p_msg);
      break;
  }
}

/*******************************************************************************
 *
 * Function         hidd_conn_reg
 *
 * Description      Registers L2CAP channels
 *
 * Returns          void
 *
 ******************************************************************************/
tHID_STATUS hidd_conn_reg(void) {
  log::verbose("");

  memset(&hd_cb.l2cap_cfg, 0, sizeof(tL2CAP_CFG_INFO));

  hd_cb.l2cap_cfg.mtu_present = TRUE;
  hd_cb.l2cap_cfg.mtu = HID_DEV_MTU_SIZE;
  memset(&hd_cb.l2cap_intr_cfg, 0, sizeof(tL2CAP_CFG_INFO));
  hd_cb.l2cap_intr_cfg.mtu_present = TRUE;
  hd_cb.l2cap_intr_cfg.mtu = HID_DEV_MTU_SIZE;

  if (!L2CA_Register2(HID_PSM_CONTROL, dev_reg_info, false /* enable_snoop */,
                      nullptr, HID_DEV_MTU_SIZE, 0,
                      BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT)) {
    log::error("HID Control (device) registration failed");
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_L2CAP_FAILED_CONTROL,
                        1);
    return (HID_ERR_L2CAP_FAILED);
  }

  if (!L2CA_Register2(HID_PSM_INTERRUPT, dev_reg_info, false /* enable_snoop */,
                      nullptr, HID_DEV_MTU_SIZE, 0,
                      BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT)) {
    L2CA_Deregister(HID_PSM_CONTROL);
    log::error("HID Interrupt (device) registration failed");
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_L2CAP_FAILED_INTERRUPT,
                        1);
    return (HID_ERR_L2CAP_FAILED);
  }

  return (HID_SUCCESS);
}

/*******************************************************************************
 *
 * Function         hidd_conn_dereg
 *
 * Description      Deregisters L2CAP channels
 *
 * Returns          void
 *
 ******************************************************************************/
void hidd_conn_dereg(void) {
  log::verbose("");

  L2CA_Deregister(HID_PSM_CONTROL);
  L2CA_Deregister(HID_PSM_INTERRUPT);
}

/*******************************************************************************
 *
 * Function         hidd_conn_initiate
 *
 * Description      Initiates HID connection to plugged device
 *
 * Returns          HID_SUCCESS
 *
 ******************************************************************************/
tHID_STATUS hidd_conn_initiate(void) {
  tHID_DEV_DEV_CTB* p_dev = &hd_cb.device;

  log::verbose("");

  if (!p_dev->in_use) {
    log::warn("no virtual cable established");
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_NOT_REGISTERED_AT_INITIATE,
                        1);
    return (HID_ERR_NOT_REGISTERED);
  }

  if (p_dev->conn.conn_state != HID_CONN_STATE_UNUSED) {
    log::warn("connection already in progress");
    log_counter_metrics(
        android::bluetooth::CodePathCounterKeyEnum::HIDD_ERR_CONN_IN_PROCESS,
        1);
    return (HID_ERR_CONN_IN_PROCESS);
  }

  p_dev->conn.ctrl_cid = 0;
  p_dev->conn.intr_cid = 0;
  p_dev->conn.disc_reason = HID_L2CAP_CONN_FAIL;

  p_dev->conn.conn_flags = HID_CONN_FLAGS_IS_ORIG;

  /* Check if L2CAP started the connection process */
  if ((p_dev->conn.ctrl_cid =
           L2CA_ConnectReq2(HID_PSM_CONTROL, p_dev->addr,
                            BTA_SEC_AUTHENTICATE | BTA_SEC_ENCRYPT)) == 0) {
    log::warn("could not start L2CAP connection");
    hd_cb.callback(hd_cb.device.addr, HID_DHOST_EVT_CLOSE, HID_ERR_L2CAP_FAILED,
                   NULL);
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_L2CAP_FAILED_INITIATE,
                        1);
  } else {
    p_dev->conn.conn_state = HID_CONN_STATE_CONNECTING_CTRL;
  }

  return (HID_SUCCESS);
}

/*******************************************************************************
 *
 * Function         hidd_conn_disconnect
 *
 * Description      Disconnects existing HID connection
 *
 * Returns          HID_SUCCESS
 *
 ******************************************************************************/
tHID_STATUS hidd_conn_disconnect(void) {
  log::verbose("");

  // clean any outstanding data on intr
  if (hd_cb.pending_data) {
    osi_free(hd_cb.pending_data);
    hd_cb.pending_data = NULL;
  }

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if ((p_hcon->ctrl_cid != 0) || (p_hcon->intr_cid != 0)) {
    p_hcon->conn_state = HID_CONN_STATE_DISCONNECTING;

    /* Set l2cap idle timeout to 0 (so ACL link is disconnected
     * immediately after last channel is closed) */
    L2CA_SetIdleTimeoutByBdAddr(hd_cb.device.addr, 0, BT_TRANSPORT_BR_EDR);

    if (p_hcon->intr_cid) {
      hidd_l2cif_disconnect(p_hcon->intr_cid);
    } else if (p_hcon->ctrl_cid) {
      hidd_l2cif_disconnect(p_hcon->ctrl_cid);
    }
  } else {
    log::warn("already disconnected");
    p_hcon->conn_state = HID_CONN_STATE_UNUSED;
  }

  return (HID_SUCCESS);
}

/*******************************************************************************
 *
 * Function         hidd_conn_send_data
 *
 * Description      Sends data to host
 *
 * Returns          tHID_STATUS
 *
 ******************************************************************************/
tHID_STATUS hidd_conn_send_data(uint8_t channel, uint8_t msg_type,
                                uint8_t param, uint8_t data, uint16_t len,
                                uint8_t* p_data) {
  BT_HDR* p_buf;
  uint8_t* p_out;
  uint16_t cid;
  uint16_t buf_size;

  log::verbose("channel({}), msg_type({}), len({})", channel, msg_type, len);

  tHID_CONN* p_hcon = &hd_cb.device.conn;

  if (p_hcon->conn_flags & HID_CONN_FLAGS_CONGESTED) {
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_CONGESTED_AT_FLAG_CHECK,
                        1);
    return HID_ERR_CONGESTED;
  }

  switch (msg_type) {
    case HID_TRANS_HANDSHAKE:
    case HID_TRANS_CONTROL:
      cid = p_hcon->ctrl_cid;
      buf_size = HID_CONTROL_BUF_SIZE;
      break;
    case HID_TRANS_DATA:
      if (channel == HID_CHANNEL_CTRL) {
        cid = p_hcon->ctrl_cid;
        buf_size = HID_CONTROL_BUF_SIZE;
      } else {
        cid = p_hcon->intr_cid;
        buf_size = HID_INTERRUPT_BUF_SIZE;
      }
      break;
    default:
      log_counter_metrics(
          android::bluetooth::CodePathCounterKeyEnum::HIDD_ERR_INVALID_PARAM,
          1);
      return (HID_ERR_INVALID_PARAM);
  }

  p_buf = (BT_HDR*)osi_malloc(buf_size);
  if (p_buf == NULL) {
    log_counter_metrics(
        android::bluetooth::CodePathCounterKeyEnum::HIDD_ERR_NO_RESOURCES, 1);
    return (HID_ERR_NO_RESOURCES);
  }

  p_buf->offset = L2CAP_MIN_OFFSET;

  p_out = (uint8_t*)(p_buf + 1) + p_buf->offset;

  *p_out = HID_BUILD_HDR(msg_type, param);
  p_out++;

  p_buf->len = 1;  // start with header only

  // add report id prefix only if non-zero (which is reserved)
  if (msg_type == HID_TRANS_DATA && (data || param == HID_PAR_REP_TYPE_OTHER)) {
    *p_out = data;  // report_id
    p_out++;
    p_buf->len++;
  }

  if (len > 0 && p_data != NULL) {
    memcpy(p_out, p_data, len);
    p_buf->len += len;
  }

  // check if connected
  if (hd_cb.device.state != HIDD_DEV_CONNECTED) {
    // for DATA on intr we hold transfer and try to reconnect
    if (msg_type == HID_TRANS_DATA && cid == p_hcon->intr_cid) {
      // drop previous data, we do not queue it for now
      if (hd_cb.pending_data) {
        osi_free(hd_cb.pending_data);
      }

      hd_cb.pending_data = p_buf;

      if (hd_cb.device.conn.conn_state == HID_CONN_STATE_UNUSED) {
        hidd_conn_initiate();
      }

      return HID_SUCCESS;
    }
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_NO_CONNECTION_AT_SEND_DATA,
                        1);
    return HID_ERR_NO_CONNECTION;
  }

  log::verbose("report sent");

  if (!L2CA_DataWrite(cid, p_buf)) {
    log_counter_metrics(android::bluetooth::CodePathCounterKeyEnum::
                            HIDD_ERR_CONGESTED_AT_DATA_WRITE,
                        1);
    return (HID_ERR_CONGESTED);
  }

  return (HID_SUCCESS);
}
