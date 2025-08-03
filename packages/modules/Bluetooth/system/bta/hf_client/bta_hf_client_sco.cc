/******************************************************************************
 *
 *  Copyright (c) 2014 The Android Open Source Project
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

#include <bluetooth/log.h>

#include <cstdint>

#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_ag_api.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btm_api.h"

#define BTA_HF_CLIENT_NO_EDR_ESCO                                \
  (ESCO_PKT_TYPES_MASK_NO_2_EV3 | ESCO_PKT_TYPES_MASK_NO_3_EV3 | \
   ESCO_PKT_TYPES_MASK_NO_2_EV5 | ESCO_PKT_TYPES_MASK_NO_3_EV5)

using namespace bluetooth;

enum {
  BTA_HF_CLIENT_SCO_LISTEN_E,
  BTA_HF_CLIENT_SCO_OPEN_E,       /* open request */
  BTA_HF_CLIENT_SCO_CLOSE_E,      /* close request */
  BTA_HF_CLIENT_SCO_SHUTDOWN_E,   /* shutdown request */
  BTA_HF_CLIENT_SCO_CONN_OPEN_E,  /* SCO opened */
  BTA_HF_CLIENT_SCO_CONN_CLOSE_E, /* SCO closed */
};

/*******************************************************************************
 *
 * Function         bta_hf_client_remove_sco
 *
 * Description      Removes the specified SCO from the system.
 *
 * Returns          bool   - true if SCO removal was started
 *
 ******************************************************************************/
static bool bta_hf_client_sco_remove(tBTA_HF_CLIENT_CB* client_cb) {
  bool removed_started = false;
  tBTM_STATUS status;

  log::verbose("");

  if (client_cb->sco_idx != BTM_INVALID_SCO_INDEX) {
    status = BTM_RemoveSco(client_cb->sco_idx);

    log::verbose("idx 0x{:04x}, status:0x{:x}", client_cb->sco_idx, status);

    if (status == BTM_CMD_STARTED) {
      removed_started = true;
    }
    /* If no connection reset the SCO handle */
    else if ((status == BTM_SUCCESS) || (status == BTM_UNKNOWN_ADDR)) {
      client_cb->sco_idx = BTM_INVALID_SCO_INDEX;
    }
  }
  return removed_started;
}

/*******************************************************************************
 *
 * Function         bta_hf_client_cback_sco
 *
 * Description      Call application callback function with SCO event.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_cback_sco(tBTA_HF_CLIENT_CB* client_cb, uint8_t event) {
  tBTA_HF_CLIENT evt;

  memset(&evt, 0, sizeof(evt));
  evt.bd_addr = client_cb->peer_addr;

  /* call app cback */
  bta_hf_client_app_callback(event, &evt);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_conn_rsp
 *
 * Description      Process the SCO connection request
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_sco_conn_rsp(tBTA_HF_CLIENT_CB* client_cb,
                                       tBTM_ESCO_CONN_REQ_EVT_DATA* p_data) {
  enh_esco_params_t resp;
  uint8_t hci_status = HCI_SUCCESS;

  log::verbose("");

  if (client_cb->sco_state == BTA_HF_CLIENT_SCO_LISTEN_ST) {
    if (p_data->link_type == BTM_LINK_TYPE_SCO) {
      // SCO
      resp = esco_parameters_for_codec(SCO_CODEC_CVSD_D1, true);
    } else if (client_cb->negotiated_codec == BTM_SCO_CODEC_LC3) {
      // eSCO LC3, HFP 1.9
      resp = esco_parameters_for_codec(ESCO_CODEC_LC3_T2, true);
    } else if (client_cb->negotiated_codec == BTM_SCO_CODEC_MSBC) {
      // eSCO mSBC
      resp = esco_parameters_for_codec(ESCO_CODEC_MSBC_T2, true);
    } else if (bta_hf_client_cb_arr.features & BTA_HF_CLIENT_FEAT_ESCO_S4) {
      // eSCO CVSD, HFP 1.7 requires S4
      resp = esco_parameters_for_codec(ESCO_CODEC_CVSD_S4, true);
    } else {
      // eSCO CVSD, S3 is preferred by default(before HFP 1.7)
      resp = esco_parameters_for_codec(ESCO_CODEC_CVSD_S3, true);
    }

    /* tell sys to stop av if any */
    bta_sys_sco_use(BTA_ID_HS, 1, client_cb->peer_addr);
  } else {
    hci_status = HCI_ERR_HOST_REJECT_DEVICE;
  }

  BTM_EScoConnRsp(p_data->sco_inx, hci_status, &resp);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_connreq_cback
 *
 * Description      BTM eSCO connection requests and eSCO change requests
 *                  Only the connection requests are processed by BTA.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_esco_connreq_cback(tBTM_ESCO_EVT event,
                                             tBTM_ESCO_EVT_DATA* p_data) {
  log::verbose("{}", event);

  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_sco_handle(p_data->conn_evt.sco_inx);
  if (client_cb == NULL) {
    log::error("wrong SCO handle to control block {}",
               p_data->conn_evt.sco_inx);
    return;
  }

  if (event != BTM_ESCO_CONN_REQ_EVT) {
    return;
  }

  bta_hf_client_sco_conn_rsp(client_cb, &p_data->conn_evt);

  client_cb->sco_state = BTA_HF_CLIENT_SCO_OPENING_ST;
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_conn_cback
 *
 * Description      BTM SCO connection callback.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_sco_conn_cback(uint16_t sco_idx) {
  log::verbose("{}", sco_idx);

  tBTA_HF_CLIENT_CB* client_cb = bta_hf_client_find_cb_by_sco_handle(sco_idx);
  if (client_cb == NULL) {
    log::error("wrong SCO handle to control block {}", sco_idx);
    return;
  }

  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));
  p_buf->event = BTA_HF_CLIENT_SCO_OPEN_EVT;
  p_buf->layer_specific = client_cb->handle;
  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_disc_cback
 *
 * Description      BTM SCO disconnection callback.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_sco_disc_cback(uint16_t sco_idx) {
  log::verbose("sco_idx {}", sco_idx);

  tBTA_HF_CLIENT_CB* client_cb = bta_hf_client_find_cb_by_sco_handle(sco_idx);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", sco_idx);
    return;
  }

  BT_HDR_RIGID* p_buf = (BT_HDR_RIGID*)osi_malloc(sizeof(BT_HDR_RIGID));
  p_buf->event = BTA_HF_CLIENT_SCO_CLOSE_EVT;
  p_buf->layer_specific = client_cb->handle;
  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_create_sco
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_sco_create(tBTA_HF_CLIENT_CB* client_cb,
                                     bool is_orig) {
  tBTM_STATUS status;

  log::verbose("{}", is_orig);

  /* Make sure this SCO handle is not already in use */
  if (client_cb->sco_idx != BTM_INVALID_SCO_INDEX) {
    log::warn("Index 0x{:04x} already in use", client_cb->sco_idx);
    return;
  }

  // codec parameters
  enh_esco_params_t params;
  // Since HF device is not expected to receive AT+BAC send +BCS command,
  // codec support of the connected AG device will be unknown,
  // so HF device will always establish only CVSD connection.
  if ((bta_hf_client_cb_arr.features & BTA_HF_CLIENT_FEAT_ESCO_S4) &&
      (client_cb->peer_features & BTA_HF_CLIENT_PEER_ESCO_S4)) {
    // eSCO CVSD, HFP 1.7 requires S4
    params = esco_parameters_for_codec(ESCO_CODEC_CVSD_S4, true);
  } else {
    // eSCO CVSD, S3 is preferred by default(before HFP 1.7)
    params = esco_parameters_for_codec(ESCO_CODEC_CVSD_S3, true);
  }

  /* if initiating set current scb and peer bd addr */
  if (is_orig) {
    BTM_SetEScoMode(&params);
    /* tell sys to stop av if any */
    bta_sys_sco_use(BTA_ID_HS, 1, client_cb->peer_addr);
  }

  status = BTM_CreateSco(&client_cb->peer_addr, is_orig, params.packet_types,
                         &client_cb->sco_idx, bta_hf_client_sco_conn_cback,
                         bta_hf_client_sco_disc_cback);
  if (status == BTM_CMD_STARTED && !is_orig) {
    if (!BTM_RegForEScoEvts(client_cb->sco_idx,
                            bta_hf_client_esco_connreq_cback))
      log::verbose("SCO registration success");
  }

  log::verbose("orig {}, inx 0x{:04x}, status 0x{:x}, pkt types 0x{:04x}",
               is_orig, client_cb->sco_idx, status, params.packet_types);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_event
 *
 * Description      Handle SCO events
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_hf_client_sco_event(tBTA_HF_CLIENT_CB* client_cb,
                                    uint8_t event) {
  log::verbose("before state: {} event: {}", client_cb->sco_state, event);

  switch (client_cb->sco_state) {
    case BTA_HF_CLIENT_SCO_SHUTDOWN_ST:
      switch (event) {
        // For WBS we only listen to SCO requests. Even for outgoing SCO
        // requests we first do a AT+BCC and wait for remote to initiate SCO
        case BTA_HF_CLIENT_SCO_LISTEN_E:
          /* create SCO listen connection */
          bta_hf_client_sco_create(client_cb, false);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        // For non WBS cases and enabling outgoing SCO requests we need to force
        // open a SCO channel
        case BTA_HF_CLIENT_SCO_OPEN_E:
          /* remove listening connection */
          bta_hf_client_sco_remove(client_cb);

          /* create SCO connection to peer */
          bta_hf_client_sco_create(client_cb, true);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPENING_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_SHUTDOWN_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_LISTEN_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_LISTEN_E:
          /* create SCO listen connection */
          bta_hf_client_sco_create(client_cb, false);
          break;

        case BTA_HF_CLIENT_SCO_OPEN_E:
          /* remove listening connection */
          bta_hf_client_sco_remove(client_cb);

          /* create SCO connection to peer */
          bta_hf_client_sco_create(client_cb, true);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPENING_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          /* remove listening connection */
          bta_hf_client_sco_remove(client_cb);

          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTDOWN_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* SCO failed; create SCO listen connection */
          bta_hf_client_sco_create(client_cb, false);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_LISTEN_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_OPENING_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_CLOSE_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPEN_CL_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTTING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_OPEN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPEN_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* SCO failed; create SCO listen connection */
          bta_hf_client_sco_create(client_cb, false);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_OPENING_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_OPEN_CL_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_OPEN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPENING_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTTING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_OPEN_E:
          /* close SCO connection */
          bta_hf_client_sco_remove(client_cb);

          client_cb->sco_state = BTA_HF_CLIENT_SCO_CLOSING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* SCO failed; create SCO listen connection */

          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_OPEN_CL_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_OPEN_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_CLOSE_E:
          if (bta_hf_client_sco_remove(client_cb)) {
            client_cb->sco_state = BTA_HF_CLIENT_SCO_CLOSING_ST;
          }
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          /* remove listening connection */
          bta_hf_client_sco_remove(client_cb);

          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTTING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* peer closed SCO */
          bta_hf_client_sco_create(client_cb, false);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_OPEN_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_CLOSING_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_OPEN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_CLOSE_OP_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTTING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* peer closed sco; create SCO listen connection */
          bta_hf_client_sco_create(client_cb, false);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_LISTEN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_CLOSING_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_CLOSE_OP_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_CLOSE_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_CLOSING_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTTING_ST;
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          /* open SCO connection */
          bta_hf_client_sco_create(client_cb, true);
          client_cb->sco_state = BTA_HF_CLIENT_SCO_OPENING_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_CLOSE_OP_ST: Ignoring event {}", event);
          break;
      }
      break;

    case BTA_HF_CLIENT_SCO_SHUTTING_ST:
      switch (event) {
        case BTA_HF_CLIENT_SCO_CONN_OPEN_E:
          /* close SCO connection; wait for conn close event */
          bta_hf_client_sco_remove(client_cb);
          break;

        case BTA_HF_CLIENT_SCO_CONN_CLOSE_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTDOWN_ST;
          break;

        case BTA_HF_CLIENT_SCO_SHUTDOWN_E:
          client_cb->sco_state = BTA_HF_CLIENT_SCO_SHUTDOWN_ST;
          break;

        default:
          log::warn("BTA_HF_CLIENT_SCO_SHUTTING_ST: Ignoring event {}", event);
          break;
      }
      break;

    default:
      break;
  }

  log::verbose("after state: {}", client_cb->sco_state);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_listen
 *
 * Description      Initialize SCO listener
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_listen(tBTA_HF_CLIENT_DATA* p_data) {
  log::verbose("");

  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_handle(p_data->hdr.layer_specific);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", p_data->hdr.layer_specific);
    return;
  }

  bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_LISTEN_E);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_shutdown
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_shutdown(tBTA_HF_CLIENT_CB* client_cb) {
  log::verbose("");

  bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_SHUTDOWN_E);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_conn_open
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_conn_open(tBTA_HF_CLIENT_DATA* p_data) {
  log::verbose("");

  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_handle(p_data->hdr.layer_specific);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", p_data->hdr.layer_specific);
    return;
  }

  bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_CONN_OPEN_E);

  bta_sys_sco_open(BTA_ID_HS, 1, client_cb->peer_addr);

  if (client_cb->negotiated_codec == BTM_SCO_CODEC_LC3) {
    bta_hf_client_cback_sco(client_cb, BTA_HF_CLIENT_AUDIO_LC3_OPEN_EVT);
  } else if (client_cb->negotiated_codec == BTM_SCO_CODEC_MSBC) {
    bta_hf_client_cback_sco(client_cb, BTA_HF_CLIENT_AUDIO_MSBC_OPEN_EVT);
  } else {
    bta_hf_client_cback_sco(client_cb, BTA_HF_CLIENT_AUDIO_OPEN_EVT);
  }
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_conn_close
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_conn_close(tBTA_HF_CLIENT_DATA* p_data) {
  log::verbose("");

  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_handle(p_data->hdr.layer_specific);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", p_data->hdr.layer_specific);
    return;
  }

  /* clear current scb */
  client_cb->sco_idx = BTM_INVALID_SCO_INDEX;

  bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_CONN_CLOSE_E);

  bta_sys_sco_close(BTA_ID_HS, 1, client_cb->peer_addr);

  bta_sys_sco_unuse(BTA_ID_HS, 1, client_cb->peer_addr);

  /* call app callback */
  bta_hf_client_cback_sco(client_cb, BTA_HF_CLIENT_AUDIO_CLOSE_EVT);

  if (client_cb->sco_close_rfc) {
    client_cb->sco_close_rfc = false;
    bta_hf_client_rfc_do_close(p_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_open
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_open(tBTA_HF_CLIENT_DATA* p_data) {
  log::verbose("");

  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_handle(p_data->hdr.layer_specific);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", p_data->hdr.layer_specific);
    return;
  }

  bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_OPEN_E);
}

/*******************************************************************************
 *
 * Function         bta_hf_client_sco_close
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hf_client_sco_close(tBTA_HF_CLIENT_DATA* p_data) {
  tBTA_HF_CLIENT_CB* client_cb =
      bta_hf_client_find_cb_by_handle(p_data->hdr.layer_specific);
  if (client_cb == NULL) {
    log::error("wrong handle to control block {}", p_data->hdr.layer_specific);
    return;
  }

  log::verbose("sco_idx 0x{:x}", client_cb->sco_idx);

  if (client_cb->sco_idx != BTM_INVALID_SCO_INDEX) {
    bta_hf_client_sco_event(client_cb, BTA_HF_CLIENT_SCO_CLOSE_E);
  }
}
