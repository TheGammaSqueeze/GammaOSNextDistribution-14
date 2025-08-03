/*
 *    Copyright (C) 2013 SAMSUNG S.LSI
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 */

#include <hardware/nfc.h>
#include <malloc.h>
#include <string.h>

#include "device.h"
#include "hal.h"
#include "hal_msg.h"
#include "osi.h"
#include "util.h"

#include <cutils/properties.h>

uint32_t fw_update_state = 0;
/* START [181106] Patch for supporting NCI v2.0 */
// [1. NCI Version Management]
int gNciVersion = NCI_VER_1_0;  // 0x10 : NCI 1.0, 0x20 : NCI2.0
/* END [181106] Patch for supporting NCI v2.0 */

static void nfc_hal_state_switch(tNFC_HAL_MSG* msg, eHAL_STATE state) {
  tNFC_HAL_MSG* new_msg;

  new_msg = (tNFC_HAL_MSG*)OSI_mem_get(HAL_EVT_SIZE);
  if (!new_msg) {
    OSI_loge("Failed to memory allocate!");
    nfc_stack_cback(HAL_NFC_ERROR_EVT, HAL_NFC_STATUS_OK);
    return;
  }

  nfc_hal_info.state = state;
  memcpy(new_msg, msg, sizeof(HAL_EVT_SIZE));
  OSI_queue_put(nfc_hal_info.msg_q, (void*)new_msg);
}

void hal_sleep(__attribute__((unused)) void* param) {
  nfc_hal_info.flag &= ~HAL_FLAG_PROP_ONE_TIMER;
  nfc_hal_info.cfg.override_timeout = 0;
  device_sleep();
}

void hal_update_sleep_timer(void) {
  device_wakeup();

  /* workaround for double timer */
  if (nfc_hal_info.flag & HAL_FLAG_MASK_USING_TIMER) return;

  if (nfc_hal_info.flag & HAL_FLAG_PROP_ONE_TIMER)
    OSI_timer_start(nfc_hal_info.sleep_timer, nfc_hal_info.cfg.override_timeout,
                    (tOSI_TIMER_CALLBACK)hal_sleep, NULL);
  else
    OSI_timer_start(nfc_hal_info.sleep_timer, nfc_hal_info.cfg.sleep_timeout,
                    (tOSI_TIMER_CALLBACK)hal_sleep, NULL);
}

int __send_to_device(uint8_t* data, size_t len) {
  hal_update_sleep_timer();
  if (nfc_hal_info.nci_last_pkt)
    memcpy((void*)nfc_hal_info.nci_last_pkt, (void*)data, len);

  return device_write(data, len);
}

void nfc_hal_open_sm(tNFC_HAL_MSG* msg) {
  tNFC_NCI_PKT* pkt = &msg->nci_packet;

  switch (msg->event) {
    case HAL_EVT_OPEN:
      device_set_mode(NFC_DEV_MODE_ON);
      hal_nci_send_prop_fw_cfg();
      break;
    case HAL_EVT_READ:
      nci_read_payload(msg);
      util_nci_analyzer(pkt);
      if (NCI_MT(pkt) != NCI_MT_RSP || NCI_GID(pkt) != NCI_GID_PROP ||
          NCI_OID(pkt) != NCI_PROP_FW_CFG) {
        OSI_logd("Not matched rsponse!! we expect NCI_PROP_FW_CFG_RSP");
      } else {
        if (NCI_STATUS(pkt) != NCI_STATUS_OK &&
            NCI_STATUS(pkt) != NCI_STATUS_E_SYNTAX &&
            NCI_STATUS(pkt) != NCI_CLOCK_STATUS_SYNTAX_ERROR &&
            NCI_STATUS(pkt) != NCI_CLOCK_STATUS_MISMATCHED &&
            NCI_STATUS(pkt) != NCI_CLOCK_STATUS_FULL) {
          OSI_loge("Failed to config FW, status: %d", NCI_STATUS(pkt));
          break;
        } else {
          if (NCI_STATUS(pkt) == NCI_STATUS_OK) {
            nfc_hal_info.state = HAL_STATE_POSTINIT;
            nfc_stack_cback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_OK);
            break;
          }
          OSI_loge("Failed to config FW, status: %d", NCI_STATUS(pkt));
        }
      }
      break;
    case HAL_EVT_COMPLETE_FAILED:
      device_set_mode(NFC_DEV_MODE_OFF);
      nfc_stack_cback(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_FAILED);
      break;

    case HAL_EVT_TERMINATE:
      // TODO: terminate
      break;
    default:
      break;
  }
}

void nfc_hal_postinit_sm(tNFC_HAL_MSG* msg) {
  tNFC_NCI_PKT* pkt = &msg->nci_packet;

  switch (msg->event) {
    case HAL_EVT_CORE_INIT:
      nfc_hal_info.vs_info.state = VS_INIT;
      nfc_hal_state_switch(msg, HAL_STATE_VS);
      break;

    case HAL_EVT_WRITE:
      if (NCI_GID(pkt) == NCI_GID_CORE) {
        if (NCI_OID(pkt) == NCI_CORE_RESET && NCI_LEN(pkt) == 1) {
          if (nfc_hal_info.flag & HAL_FLAG_ALREADY_RESET) goto complete;

          nfc_hal_info.flag |= HAL_FLAG_W4_CORE_RESET_RSP;
          OSI_timer_start(nfc_hal_info.nci_timer, 1000,
                          (tOSI_TIMER_CALLBACK)fw_force_update, NULL);
          OSI_logd("set flag to 0x%06X", nfc_hal_info.flag);
        } else if (NCI_OID(pkt) == NCI_CORE_INIT &&
                   (NCI_LEN(pkt) == 0 || NCI_LEN(pkt) == 2)) {
          if (nfc_hal_info.flag & HAL_FLAG_ALREADY_INIT) goto complete;

          nfc_hal_info.flag |= HAL_FLAG_W4_CORE_INIT_RSP;
          OSI_timer_start(nfc_hal_info.nci_timer, 1000,
                          (tOSI_TIMER_CALLBACK)nci_init_timeout, NULL);
          OSI_logd("set flag to 0x%06X", nfc_hal_info.flag);
        }
      }
      hal_nci_send(&msg->nci_packet);
      break;

    case HAL_EVT_READ:
      nci_read_payload(msg);
      if (NCI_GID(pkt) == NCI_GID_CORE) {
        if (NCI_OID(pkt) == NCI_CORE_RESET) {
          OSI_logd("Respond CORE_RESET_RSP");
          nfc_hal_info.flag &= ~HAL_FLAG_W4_CORE_RESET_RSP;
          nfc_hal_info.flag |= HAL_FLAG_ALREADY_RESET;
          /* START [19082300] Patch for supporting NCI v2.0 */
          // [1. NCI Version Management]
          // gNciVersion : 0x10 : NCI1.0, 0x20 : NCI2.0
          if ((NCI_LEN(pkt) == 0x03) && NCI_MT(pkt) == NCI_MT_RSP)
            gNciVersion = NCI_VER_1_0;
          else {
            gNciVersion = NCI_VER_2_0;
          }
          /* END [19082300] Patch for supporting NCI v2.0 */
        } else if (NCI_OID(pkt) == NCI_CORE_INIT) {
          OSI_logd("Respond CORE_INIT_RSP");
          nfc_hal_info.flag &= ~HAL_FLAG_W4_CORE_INIT_RSP;
          nfc_hal_info.flag |= HAL_FLAG_ALREADY_INIT;
        }
        OSI_timer_stop(nfc_hal_info.nci_timer);
      }
      util_nci_analyzer(pkt);
      nfc_data_callback(&msg->nci_packet);
      break;

    case HAL_EVT_COMPLETE:
    complete:
      nfc_hal_info.flag |= HAL_FLAG_NTF_TRNS_ERROR | HAL_FLAG_RETRY_TRNS;
      nfc_hal_info.state = HAL_STATE_SERVICE;

      OSI_logd("Complete postinit sm");

      nfc_stack_cback(HAL_NFC_POST_INIT_CPLT_EVT, HAL_NFC_STATUS_OK);
      break;
    case HAL_EVT_COMPLETE_FAILED:
      nfc_stack_cback(HAL_NFC_POST_INIT_CPLT_EVT, HAL_NFC_STATUS_FAILED);
      break;

    /* START - VTS */
    case HAL_EVT_POWER_CYCLE:
      OSI_logt("HAL_EVT_POWER_CYCLE");
      device_sleep();
      device_close();
      OSI_logt("HAL state change to POWERCYCLE");
      nfc_hal_state_switch(msg, HAL_STATE_POWERCYCLE);
      break;
      /* END - VTS */

    case HAL_EVT_TERMINATE:
      // TODO: terminate
      break;
    default:
      break;
  }
}

void nfc_hal_vs_sm(tNFC_HAL_MSG* msg) {
  tNFC_HAL_VS_INFO* vs = &nfc_hal_info.vs_info;

  if (msg->event != HAL_EVT_READ && msg->event != HAL_EVT_CORE_INIT) {
    OSI_loge("Unexpected event [%d]", msg->event);
    return;
  }

  if (vs->state != VS_INIT) {
    nci_read_payload(msg);
    util_nci_analyzer(pkt);
  }

  switch (vs->state) {
    case VS_INIT:
      hal_nci_send_clearLmrt();
      vs->state = VS_W4_COMPLETE;
      break;
    case VS_W4_COMPLETE:
      OSI_logd("Vendor Specific is complete.");
      msg->event = HAL_EVT_COMPLETE;
      nfc_hal_state_switch(msg, HAL_STATE_POSTINIT);
      break;
    default:
      OSI_loge("Unexpected event [%d]", msg->event);
      break;
  }
}

void nfc_hal_service_sm(tNFC_HAL_MSG* msg) {
  tNFC_NCI_PKT* pkt = &msg->nci_packet;
  /* START [H16031401] */
  nfc_hal_info.msg_event = msg->event;
  /* END [H16031401] */

  switch (msg->event) {
    /* START - VTS */
    case HAL_EVT_CORE_INIT:
      nfc_hal_info.vs_info.state = VS_INIT;
      nfc_hal_state_switch(msg, HAL_STATE_VS);
      break;
    /* END - VTS */
    case HAL_EVT_WRITE:
      if (nfc_hal_prehandler(pkt)) hal_nci_send(pkt);
      break;
    case HAL_EVT_READ:
      nci_read_payload(msg);
      util_nci_analyzer(pkt);
      hal_update_sleep_timer();
      if (nfc_hal_prehandler(pkt)) nfc_data_callback(pkt);
      break;
    case HAL_EVT_CONTROL_GRANTED:
      nfc_hal_state_switch(msg, HAL_STATE_GRANTED);
      break;
    case HAL_EVT_TERMINATE:
      // TODO: terminate
      break;
    default:
      break;
  }
}

static void nfc_hal_grant_finish(void) {
  nfc_stack_cback(HAL_NFC_RELEASE_CONTROL_EVT, HAL_NFC_STATUS_OK);
  nfc_hal_info.state = HAL_STATE_SERVICE;
  nfc_hal_info.grant_cback = NULL;
}

void nfc_hal_grant_sm(tNFC_HAL_MSG* msg) {
  tNFC_NCI_PKT* pkt = &msg->nci_packet;
  uint8_t cback_ret = HAL_GRANT_FINISH;

  /* Granted mode is not need to SLEEP.
   * hal should pend granted mode just few time */
  switch (msg->event) {
    case HAL_EVT_READ:
      nci_read_payload(msg);
      util_nci_analyzer(pkt);
      cback_ret = nfc_hal_info.grant_cback(pkt);
      if (cback_ret == HAL_GRANT_FINISH) nfc_hal_grant_finish();

      if (cback_ret != HAL_GRANT_SEND_NEXT) break;
      [[fallthrough]];
    case HAL_EVT_CONTROL_GRANTED:
      pkt = (tNFC_NCI_PKT*)OSI_queue_get(nfc_hal_info.nci_q);
      if (pkt) {
        // TODO: Should CLF respond?
        hal_nci_send(pkt);
        OSI_mem_free((tOSI_MEM_HANDLER)pkt);
      } else
        nfc_hal_grant_finish();

      break;

    case HAL_EVT_WRITE:
      OSI_loge("HAL is in granted mode!");
      break;
  }
}
/* START - VTS */
void nfc_hal_power_sm(tNFC_HAL_MSG* msg) {
  switch (msg->event) {
    case HAL_EVT_POWER_CYCLE:
      // have to do is hal open
      OSI_logt("HAL_EVT_POWER_CYCLE");
      // nfc_hal_init();

      if (device_open()) return;

      msg->event = HAL_EVT_OPEN;
      nfc_hal_state_switch(msg, HAL_STATE_OPEN);
      break;
    default:
      break;
  }
}
/* END - VTS */

/* TASK */
void nfc_hal_task(void) {
  tNFC_HAL_MSG* msg;
  eHAL_STATE old_st;

  OSI_logt("enter!");

  if (!nfc_hal_info.msg_task || !nfc_hal_info.nci_timer ||
      !nfc_hal_info.msg_q || !nfc_hal_info.nci_q) {
    OSI_loge("msg_task = %p, nci_timer = %p, msg_q = %p, nci_q = %p",
             nfc_hal_info.msg_task, nfc_hal_info.nci_timer, nfc_hal_info.msg_q,
             nfc_hal_info.nci_q);

    nfc_hal_deinit();
    OSI_loge("nfc_hal initialization is not succeeded.");
    nfc_stack_cback(HAL_NFC_ERROR_EVT, HAL_NFC_STATUS_FAILED);
    return;
  }

  while (OSI_task_isRun(nfc_hal_info.msg_task) == OSI_RUN) {
    msg = (tNFC_HAL_MSG*)OSI_queue_get_wait(nfc_hal_info.msg_q);
    if (!msg) continue;

    OSI_logd("Got a event: %s(%d)", event_to_string(msg->event), msg->event);
    if (msg->event == HAL_EVT_TERMINATE) break;

    OSI_logd("current state: %s", state_to_string(nfc_hal_info.state));
    old_st = nfc_hal_info.state;
    switch (nfc_hal_info.state) {
      case HAL_STATE_INIT:
      case HAL_STATE_DEINIT:
      case HAL_STATE_OPEN:
        nfc_hal_open_sm(msg);
        break;
      case HAL_STATE_VS:
        nfc_hal_vs_sm(msg);
        break;
      case HAL_STATE_POSTINIT:
        nfc_hal_postinit_sm(msg);
        break;
      case HAL_STATE_SERVICE:
        nfc_hal_service_sm(msg);
        break;
      case HAL_STATE_GRANTED:
        nfc_hal_grant_sm(msg);
        break;
      /* START - VTS */
      case HAL_STATE_POWERCYCLE:
        nfc_hal_power_sm(msg);
        break;
      /* END - VTS */
      default:
        break;
    }
    OSI_mem_free((tOSI_MEM_HANDLER)msg);

    if (old_st != nfc_hal_info.state) {
      OSI_logd("hal state is changed: %s -> %s", state_to_string(old_st),
               state_to_string(nfc_hal_info.state));
    }
  }
  OSI_logt("exit!");
}

/* Print */
const char* event_to_string(uint8_t event) {
  switch (event) {
    case HAL_EVT_OPEN:
      return "HAL_EVT_OPEN";
    case HAL_EVT_CORE_INIT:
      return "HAL_EVT_CORE_INIT";
    case HAL_EVT_WRITE:
      return "HAL_EVT_WRITE";
    case HAL_EVT_READ:
      return "HAL_EVT_READ";
    case HAL_EVT_CONTROL_GRANTED:
      return "HAL_EVT_CONTROL_GRANTED";
    /* START - VTS */
    case HAL_EVT_POWER_CYCLE:
      return "HAL_EVT_POWER_CYCLE";
    /* END - VTS */
    case HAL_EVT_TERMINATE:
      return "NFC_HAL_TERMINATE";
    case HAL_EVT_COMPLETE:
      return "NFC_HAL_COMPLETE";
    case HAL_EVT_COMPLETE_FAILED:
      return "NFC_HAL_COMPLETE_FAILED";
  }
  return "Unknown event.";
}

const char* state_to_string(eHAL_STATE state) {
  switch (state) {
    case HAL_STATE_INIT:
      return "INIT";
    case HAL_STATE_DEINIT:
      return "DEINIT";
    case HAL_STATE_OPEN:
      return "OPEN";
    case HAL_STATE_VS:
      return "VENDOR_SPECIFIC";
    case HAL_STATE_POSTINIT:
      return "POST_INIT";
    case HAL_STATE_SERVICE:
      return "SERVICE";
    case HAL_STATE_GRANTED:
      return "GRANT";
    /* START - VTS */
    case HAL_STATE_POWERCYCLE:
      return "POWER_CYCLE";
      /* END - VTS */
    case HAL_STATE_CLOSE:
      return "CLOSE";
  }
  return "Unknown state.";
}
