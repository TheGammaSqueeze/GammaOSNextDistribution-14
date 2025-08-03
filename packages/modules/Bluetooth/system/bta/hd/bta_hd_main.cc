/******************************************************************************
 *
 *  Copyright 2016 The Android Open Source Project
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

#include <cstdint>

#include "internal_include/bt_target.h"
#if defined(BTA_HD_INCLUDED) && (BTA_HD_INCLUDED == TRUE)

#include <bluetooth/log.h>

#include "bta/hd/bta_hd_int.h"
#include "stack/include/bt_hdr.h"

using namespace bluetooth;

/*****************************************************************************
 * Constants and types
 ****************************************************************************/

/* state machine states */
enum {
  BTA_HD_INIT_ST,
  BTA_HD_IDLE_ST,              /* not connected, waiting for connection */
  BTA_HD_CONN_ST,              /* host connected */
  BTA_HD_TRANSIENT_TO_INIT_ST, /* transient state: going back from CONN to INIT
                                  */
};
typedef uint8_t tBTA_HD_STATE;

/*****************************************************************************
 * Global data
 ****************************************************************************/
tBTA_HD_CB bta_hd_cb;

static tBTA_HD_STATE get_state() { return bta_hd_cb.state; }

static void set_state(tBTA_HD_STATE state) { bta_hd_cb.state = state; }

static void bta_hd_better_state_machine(uint16_t event, tBTA_HD_DATA* p_data) {
  switch (get_state()) {
    case BTA_HD_INIT_ST:
      switch (event) {
        case BTA_HD_API_REGISTER_APP_EVT:
          set_state(BTA_HD_IDLE_ST);
          bta_hd_register_act(p_data);
          break;
        case BTA_HD_API_ADD_DEVICE_EVT:
          bta_hd_add_device_act(p_data);
          break;
        case BTA_HD_API_REMOVE_DEVICE_EVT:
          bta_hd_remove_device_act(p_data);
          break;
      }
      break;
    case BTA_HD_IDLE_ST:
      switch (event) {
        case BTA_HD_API_UNREGISTER_APP_EVT:
          set_state(BTA_HD_INIT_ST);
          bta_hd_unregister_act();
          break;
        case BTA_HD_API_CONNECT_EVT:
          bta_hd_connect_act(p_data);
          break;
        case BTA_HD_API_DISCONNECT_EVT:
          bta_hd_disconnect_act();
          break;
        case BTA_HD_API_ADD_DEVICE_EVT:
          bta_hd_add_device_act(p_data);
          break;
        case BTA_HD_API_REMOVE_DEVICE_EVT:
          bta_hd_remove_device_act(p_data);
          break;
        case BTA_HD_API_SEND_REPORT_EVT:
          bta_hd_send_report_act(p_data);
          break;
        case BTA_HD_INT_OPEN_EVT:
          set_state(BTA_HD_CONN_ST);
          bta_hd_open_act(p_data);
          break;
        case BTA_HD_INT_CLOSE_EVT:
          bta_hd_close_act(p_data);
          break;
      }
      break;
    case BTA_HD_CONN_ST:
      switch (event) {
        case BTA_HD_API_UNREGISTER_APP_EVT:
          set_state(BTA_HD_TRANSIENT_TO_INIT_ST);
          bta_hd_disconnect_act();
          break;
        case BTA_HD_API_DISCONNECT_EVT:
          bta_hd_disconnect_act();
          break;
        case BTA_HD_API_ADD_DEVICE_EVT:
          bta_hd_add_device_act(p_data);
          break;
        case BTA_HD_API_REMOVE_DEVICE_EVT:
          bta_hd_remove_device_act(p_data);
          break;
        case BTA_HD_API_SEND_REPORT_EVT:
          bta_hd_send_report_act(p_data);
          break;
        case BTA_HD_API_REPORT_ERROR_EVT:
          bta_hd_report_error_act(p_data);
          break;
        case BTA_HD_API_VC_UNPLUG_EVT:
          bta_hd_vc_unplug_act();
          break;
        case BTA_HD_INT_CLOSE_EVT:
          set_state(BTA_HD_IDLE_ST);
          bta_hd_close_act(p_data);
          break;
        case BTA_HD_INT_INTR_DATA_EVT:
          bta_hd_intr_data_act(p_data);
          break;
        case BTA_HD_INT_GET_REPORT_EVT:
          bta_hd_get_report_act(p_data);
          break;
        case BTA_HD_INT_SET_REPORT_EVT:
          bta_hd_set_report_act(p_data);
          break;
        case BTA_HD_INT_SET_PROTOCOL_EVT:
          bta_hd_set_protocol_act(p_data);
          break;
        case BTA_HD_INT_VC_UNPLUG_EVT:
          set_state(BTA_HD_IDLE_ST);
          bta_hd_vc_unplug_done_act(p_data);
          break;
        case BTA_HD_INT_SUSPEND_EVT:
          bta_hd_suspend_act(p_data);
          break;
        case BTA_HD_INT_EXIT_SUSPEND_EVT:
          bta_hd_exit_suspend_act(p_data);
          break;
      }
      break;
    case BTA_HD_TRANSIENT_TO_INIT_ST:
      switch (event) {
        case BTA_HD_INT_CLOSE_EVT:
          set_state(BTA_HD_INIT_ST);
          bta_hd_unregister2_act(p_data);
          break;
        case BTA_HD_INT_VC_UNPLUG_EVT:
          set_state(BTA_HD_INIT_ST);
          bta_hd_unregister2_act(p_data);
          break;
      }
      break;
  }
}

/*******************************************************************************
 *
 * Function         bta_hd_hdl_event
 *
 * Description      HID device main event handling function.
 *
 * Returns          void
 *
 ******************************************************************************/
bool bta_hd_hdl_event(const BT_HDR_RIGID* p_msg) {
  log::verbose("p_msg->event={}", p_msg->event);

  switch (p_msg->event) {
    case BTA_HD_API_ENABLE_EVT:
      bta_hd_api_enable((tBTA_HD_DATA*)p_msg);
      break;

    case BTA_HD_API_DISABLE_EVT:
      if (bta_hd_cb.state == BTA_HD_CONN_ST) {
        log::warn("host connected, disconnect before disabling");

        // unregister (and disconnect)
        bta_hd_cb.disable_w4_close = TRUE;
        bta_hd_better_state_machine(BTA_HD_API_UNREGISTER_APP_EVT,
                                    (tBTA_HD_DATA*)p_msg);
      } else {
        bta_hd_api_disable();
      }
      break;

    default:
      bta_hd_better_state_machine(p_msg->event, (tBTA_HD_DATA*)p_msg);
  }
  return (TRUE);
}

#endif /* BTA_HD_INCLUDED */
