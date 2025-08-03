/******************************************************************************
 *
 *  Copyright 2005-2012 Broadcom Corporation
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
 *  This file contains the HID host main functions and state machine.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_hh"

#include <bluetooth/log.h>
#include <string.h>  // memset

#include <cstdint>

#include "bta/hh/bta_hh_int.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"

using namespace bluetooth;

/*****************************************************************************
 * Global data
 ****************************************************************************/
tBTA_HH_CB bta_hh_cb;

/*****************************************************************************
 * Static functions
 ****************************************************************************/
static const char* bta_hh_evt_code(tBTA_HH_INT_EVT evt_code);
static const char* bta_hh_state_code(tBTA_HH_STATE state_code);

static void bta_hh_better_state_machine(tBTA_HH_DEV_CB* p_cb, uint16_t event,
                                        const tBTA_HH_DATA* p_data) {
  log::verbose("state:{}, event:{}", bta_hh_state_code(p_cb->state),
               bta_hh_evt_code(static_cast<tBTA_HH_INT_EVT>(event)));
  switch (p_cb->state) {
    case BTA_HH_IDLE_ST:
      switch (event) {
        case BTA_HH_API_OPEN_EVT:
          p_cb->state = BTA_HH_W4_CONN_ST;
          bta_hh_connect(p_cb, p_data);
          break;
        case BTA_HH_INT_OPEN_EVT:
          p_cb->state = BTA_HH_W4_CONN_ST;
          bta_hh_open_act(p_cb, p_data);
          break;
        case BTA_HH_INT_CLOSE_EVT:
          bta_hh_open_failure(p_cb, p_data);
          break;
        case BTA_HH_API_MAINT_DEV_EVT:
          bta_hh_maint_dev_act(p_cb, p_data);
          break;
        case BTA_HH_OPEN_CMPL_EVT:
          p_cb->state = BTA_HH_CONN_ST;
          bta_hh_open_cmpl_act(p_cb, p_data);
          break;
        case BTA_HH_GATT_OPEN_EVT:
          p_cb->state = BTA_HH_W4_CONN_ST;
          bta_hh_gatt_open(p_cb, p_data);
          break;
      }
      break;
    case BTA_HH_W4_CONN_ST:
      switch (event) {
        case BTA_HH_API_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          break;
        case BTA_HH_INT_OPEN_EVT:
          bta_hh_open_act(p_cb, p_data);
          break;
        case BTA_HH_INT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_open_failure(p_cb, p_data);
          break;
        case BTA_HH_SDP_CMPL_EVT:
          bta_hh_sdp_cmpl(p_cb, p_data);
          break;
        case BTA_HH_API_WRITE_DEV_EVT:
          bta_hh_write_dev_act(p_cb, p_data);
          break;
        case BTA_HH_API_MAINT_DEV_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_maint_dev_act(p_cb, p_data);
          break;
        case BTA_HH_OPEN_CMPL_EVT:
          p_cb->state = BTA_HH_CONN_ST;
          bta_hh_open_cmpl_act(p_cb, p_data);
          break;
        case BTA_HH_GATT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_le_open_fail(p_cb, p_data);
          break;
        case BTA_HH_GATT_OPEN_EVT:
          bta_hh_gatt_open(p_cb, p_data);
          break;
        case BTA_HH_START_ENC_EVT:
          p_cb->state = BTA_HH_W4_SEC;
          bta_hh_start_security(p_cb, p_data);
          break;
      }
      break;
    case BTA_HH_CONN_ST:
      switch (event) {
        case BTA_HH_API_CLOSE_EVT:
          bta_hh_api_disc_act(p_cb, p_data);
          break;
        case BTA_HH_INT_OPEN_EVT:
          bta_hh_open_act(p_cb, p_data);
          break;
        case BTA_HH_INT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_close_act(p_cb, p_data);
          break;
        case BTA_HH_INT_DATA_EVT:
          bta_hh_data_act(p_cb, p_data);
          break;
        case BTA_HH_INT_CTRL_DATA:
          bta_hh_ctrl_dat_act(p_cb, p_data);
          break;
        case BTA_HH_INT_HANDSK_EVT:
          bta_hh_handsk_act(p_cb, p_data);
          break;
        case BTA_HH_API_WRITE_DEV_EVT:
          bta_hh_write_dev_act(p_cb, p_data);
          break;
        case BTA_HH_API_GET_DSCP_EVT:
          bta_hh_get_dscp_act(p_cb, p_data);
          break;
        case BTA_HH_API_MAINT_DEV_EVT:
          bta_hh_maint_dev_act(p_cb, p_data);
          break;
        case BTA_HH_GATT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_gatt_close(p_cb, p_data);
          break;
      }
      break;
    case BTA_HH_W4_SEC:
      switch (event) {
        case BTA_HH_API_CLOSE_EVT:
          bta_hh_api_disc_act(p_cb, p_data);
          break;
        case BTA_HH_INT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_open_failure(p_cb, p_data);
          break;
        case BTA_HH_API_MAINT_DEV_EVT:
          bta_hh_maint_dev_act(p_cb, p_data);
          break;
        case BTA_HH_GATT_CLOSE_EVT:
          p_cb->state = BTA_HH_IDLE_ST;
          bta_hh_le_open_fail(p_cb, p_data);
          break;
        case BTA_HH_ENC_CMPL_EVT:
          p_cb->state = BTA_HH_W4_CONN_ST;
          bta_hh_security_cmpl(p_cb, p_data);
          break;
        case BTA_HH_GATT_ENC_CMPL_EVT:
          bta_hh_le_notify_enc_cmpl(p_cb, p_data);
          break;
      }
      break;
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_sm_execute
 *
 * Description      State machine event handling function for HID Host
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_hh_sm_execute(tBTA_HH_DEV_CB* p_cb, uint16_t event,
                       const tBTA_HH_DATA* p_data) {
  tBTA_HH cback_data;
  tBTA_HH_EVT cback_event = 0;
  tBTA_HH_STATE in_state;
  tBTA_HH_INT_EVT debug_event = static_cast<tBTA_HH_INT_EVT>(event);

  memset(&cback_data, 0, sizeof(tBTA_HH));

  /* handle exception, no valid control block was found */
  if (!p_cb) {
    log::verbose("Event:{}, bta_hh_cb.p_cback:{}", bta_hh_evt_code(debug_event),
                 fmt::ptr(bta_hh_cb.p_cback));
    /* BTA HH enabled already? otherwise ignore the event although it's bad*/
    if (bta_hh_cb.p_cback != NULL) {
      switch (event) {
        /* no control block available for new connection */
        case BTA_HH_API_OPEN_EVT:
          cback_event = BTA_HH_OPEN_EVT;
          /* build cback data */
          cback_data.conn.link_spec = ((tBTA_HH_API_CONN*)p_data)->link_spec;
          cback_data.conn.status = BTA_HH_ERR_DB_FULL;
          cback_data.conn.handle = BTA_HH_INVALID_HANDLE;
          break;
        /* DB full, BTA_HhAddDev */
        case BTA_HH_API_MAINT_DEV_EVT:
          cback_event = p_data->api_maintdev.sub_event;

          if (p_data->api_maintdev.sub_event == BTA_HH_ADD_DEV_EVT) {
            cback_data.dev_info.link_spec = p_data->api_maintdev.link_spec;
            cback_data.dev_info.status = BTA_HH_ERR_DB_FULL;
            cback_data.dev_info.handle = BTA_HH_INVALID_HANDLE;
          } else {
            cback_data.dev_info.status = BTA_HH_ERR_HDL;
            cback_data.dev_info.handle =
                (uint8_t)p_data->api_maintdev.hdr.layer_specific;
          }
          break;
        case BTA_HH_API_WRITE_DEV_EVT:
          cback_event = (p_data->api_sndcmd.t_type - HID_TRANS_GET_REPORT) +
                        BTA_HH_GET_RPT_EVT;
          osi_free_and_reset((void**)&p_data->api_sndcmd.p_data);
          if (p_data->api_sndcmd.t_type == HID_TRANS_SET_PROTOCOL ||
              p_data->api_sndcmd.t_type == HID_TRANS_SET_REPORT ||
              p_data->api_sndcmd.t_type == HID_TRANS_SET_IDLE) {
            cback_data.dev_status.status = BTA_HH_ERR_HDL;
            cback_data.dev_status.handle =
                (uint8_t)p_data->api_sndcmd.hdr.layer_specific;
          } else if (p_data->api_sndcmd.t_type != HID_TRANS_DATA &&
                     p_data->api_sndcmd.t_type != HID_TRANS_CONTROL) {
            cback_data.hs_data.handle =
                (uint8_t)p_data->api_sndcmd.hdr.layer_specific;
            cback_data.hs_data.status = BTA_HH_ERR_HDL;
            /* hs_data.rsp_data will be all zero, which is not valid value */
          } else if (p_data->api_sndcmd.t_type == HID_TRANS_CONTROL &&
                     p_data->api_sndcmd.param ==
                         BTA_HH_CTRL_VIRTUAL_CABLE_UNPLUG) {
            cback_data.status = BTA_HH_ERR_HDL;
            cback_event = BTA_HH_VC_UNPLUG_EVT;
          } else
            cback_event = 0;
          break;

        case BTA_HH_API_CLOSE_EVT:
          cback_event = BTA_HH_CLOSE_EVT;

          cback_data.dev_status.status = BTA_HH_ERR_HDL;
          cback_data.dev_status.handle =
              (uint8_t)p_data->api_sndcmd.hdr.layer_specific;
          break;

        default:
          /* invalid handle, call bad API event */
          log::error("wrong device handle:{}", p_data->hdr.layer_specific);
          /* Free the callback buffer now */
          if (p_data != NULL)
            osi_free_and_reset((void**)&p_data->hid_cback.p_data);
          break;
      }
      if (cback_event) (*bta_hh_cb.p_cback)(cback_event, &cback_data);
    }
  }
  /* corresponding CB is found, go to state machine */
  else {
    in_state = p_cb->state;
    log::verbose("State 0x{:02x} [{}], Event [{}]", in_state,
                 bta_hh_state_code(in_state), bta_hh_evt_code(debug_event));

    if ((p_cb->state == BTA_HH_NULL_ST) || (p_cb->state >= BTA_HH_INVALID_ST)) {
      log::error("Invalid state State=0x{:x}, Event={}", p_cb->state, event);
      return;
    }

    bta_hh_better_state_machine(p_cb, event, p_data);

    if (in_state != p_cb->state) {
      log::debug("HHID State Change: [{}] -> [{}] after Event [{}]",
                 bta_hh_state_code(in_state), bta_hh_state_code(p_cb->state),
                 bta_hh_evt_code(debug_event));
    }
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_hdl_event
 *
 * Description      HID host main event handling function.
 *
 *
 * Returns          void
 *
 ******************************************************************************/
bool bta_hh_hdl_event(const BT_HDR_RIGID* p_msg) {
  uint8_t index = BTA_HH_IDX_INVALID;
  tBTA_HH_DEV_CB* p_cb = NULL;

  /* all events processed in state machine need to find corresponding
     CB before proceed */
  if (p_msg->event == BTA_HH_API_OPEN_EVT) {
    index = bta_hh_find_cb(((tBTA_HH_API_CONN*)p_msg)->link_spec);
  } else if (p_msg->event == BTA_HH_API_MAINT_DEV_EVT) {
    /* if add device */
    if (((tBTA_HH_MAINT_DEV*)p_msg)->sub_event == BTA_HH_ADD_DEV_EVT) {
      index = bta_hh_find_cb(((tBTA_HH_MAINT_DEV*)p_msg)->link_spec);
    } else /* else remove device by handle */ {
      index = bta_hh_dev_handle_to_cb_idx((uint8_t)p_msg->layer_specific);
      /* If BT disable is done while the HID device is connected and
       * Link_Key uses unauthenticated combination
       * then we can get into a situation where remove_bonding is called
       * with the index set to 0 (without getting
       * cleaned up). Only when VIRTUAL_UNPLUG is called do we cleanup the
       * index and make it MAX_KNOWN.
       * So if REMOVE_DEVICE is called and in_use is false then we should
       * treat this as a NULL p_cb. Hence we
       * force the index to be IDX_INVALID
       */
      if ((index != BTA_HH_IDX_INVALID) && (!bta_hh_cb.kdev[index].in_use)) {
        index = BTA_HH_IDX_INVALID;
      }
    }
  } else if (p_msg->event == BTA_HH_INT_OPEN_EVT) {
    index = bta_hh_find_cb(((tBTA_HH_CBACK_DATA*)p_msg)->link_spec);
  } else {
    index = bta_hh_dev_handle_to_cb_idx((uint8_t)p_msg->layer_specific);
  }

  if (index != BTA_HH_IDX_INVALID) p_cb = &bta_hh_cb.kdev[index];

  log::verbose("handle={} dev_cb[{}]", p_msg->layer_specific, index);
  bta_hh_sm_execute(p_cb, p_msg->event, (tBTA_HH_DATA*)p_msg);

  return (true);
}

/*****************************************************************************
 *  Debug Functions
 ****************************************************************************/
/*******************************************************************************
 *
 * Function         bta_hh_evt_code
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
static const char* bta_hh_evt_code(tBTA_HH_INT_EVT evt_code) {
  switch (evt_code) {
    case BTA_HH_API_OPEN_EVT:
      return "BTA_HH_API_OPEN_EVT";
    case BTA_HH_API_CLOSE_EVT:
      return "BTA_HH_API_CLOSE_EVT";
    case BTA_HH_INT_OPEN_EVT:
      return "BTA_HH_INT_OPEN_EVT";
    case BTA_HH_INT_CLOSE_EVT:
      return "BTA_HH_INT_CLOSE_EVT";
    case BTA_HH_INT_HANDSK_EVT:
      return "BTA_HH_INT_HANDSK_EVT";
    case BTA_HH_INT_DATA_EVT:
      return "BTA_HH_INT_DATA_EVT";
    case BTA_HH_INT_CTRL_DATA:
      return "BTA_HH_INT_CTRL_DATA";
    case BTA_HH_API_WRITE_DEV_EVT:
      return "BTA_HH_API_WRITE_DEV_EVT";
    case BTA_HH_SDP_CMPL_EVT:
      return "BTA_HH_SDP_CMPL_EVT";
    case BTA_HH_API_MAINT_DEV_EVT:
      return "BTA_HH_API_MAINT_DEV_EVT";
    case BTA_HH_API_GET_DSCP_EVT:
      return "BTA_HH_API_GET_DSCP_EVT";
    case BTA_HH_OPEN_CMPL_EVT:
      return "BTA_HH_OPEN_CMPL_EVT";
    case BTA_HH_GATT_CLOSE_EVT:
      return "BTA_HH_GATT_CLOSE_EVT";
    case BTA_HH_GATT_OPEN_EVT:
      return "BTA_HH_GATT_OPEN_EVT";
    case BTA_HH_START_ENC_EVT:
      return "BTA_HH_START_ENC_EVT";
    case BTA_HH_ENC_CMPL_EVT:
      return "BTA_HH_ENC_CMPL_EVT";
    default:
      return "unknown HID Host event code";
  }
}

/*******************************************************************************
 *
 * Function         bta_hh_state_code
 *
 * Description      get string representation of HID host state code.
 *
 * Returns          void
 *
 ******************************************************************************/
static const char* bta_hh_state_code(tBTA_HH_STATE state_code) {
  switch (state_code) {
    case BTA_HH_NULL_ST:
      return "BTA_HH_NULL_ST";
    case BTA_HH_IDLE_ST:
      return "BTA_HH_IDLE_ST";
    case BTA_HH_W4_CONN_ST:
      return "BTA_HH_W4_CONN_ST";
    case BTA_HH_CONN_ST:
      return "BTA_HH_CONN_ST";
    case BTA_HH_W4_SEC:
      return "BTA_HH_W4_SEC";
    default:
      return "unknown HID Host state";
  }
}
