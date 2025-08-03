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
 *  This file contains functions that handle BTM interface functions for the
 *  Bluetooth device including Rest, HCI buffer size and others
 *
 ******************************************************************************/

#define LOG_TAG "devctl"
#include <base/logging.h>
#include <bluetooth/log.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "acl_api_types.h"
#include "btif/include/btif_bqr.h"
#include "btm_sec_cb.h"
#include "btm_sec_int_types.h"
#include "device/include/controller.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "main/shim/btm_api.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sec.h"
#include "stack/gatt/connection_manager.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_api_types.h"
#include "stack/include/bt_types.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_ble_privacy.h"
#include "stack/include/hcidefs.h"
#include "stack/include/l2cap_controller_interface.h"
#include "types/raw_address.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

void btm_inq_db_reset(void);
void btm_pm_reset(void);
/******************************************************************************/
/*               L O C A L    D A T A    D E F I N I T I O N S                */
/******************************************************************************/

#ifndef BTM_DEV_RESET_TIMEOUT
#define BTM_DEV_RESET_TIMEOUT 4
#endif

// TODO: Reevaluate this value in the context of timers with ms granularity
#define BTM_DEV_NAME_REPLY_TIMEOUT_MS    \
  (2 * 1000) /* 2 seconds for name reply \
                */

#define BTM_INFO_TIMEOUT 5 /* 5 seconds for info response */

/******************************************************************************/
/*            L O C A L    F U N C T I O N     P R O T O T Y P E S            */
/******************************************************************************/

static void decode_controller_support();
static void BTM_BT_Quality_Report_VSE_CBack(uint8_t length,
                                            const uint8_t* p_stream);

/*******************************************************************************
 *
 * Function         btm_dev_init
 *
 * Description      This function is on the BTM startup
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_dev_init() {
  /* Initialize nonzero defaults */
  memset(btm_sec_cb.cfg.bd_name, 0, sizeof(tBTM_LOC_BD_NAME));

  btm_cb.devcb.read_local_name_timer = alarm_new("btm.read_local_name_timer");
  btm_cb.devcb.read_rssi_timer = alarm_new("btm.read_rssi_timer");
  btm_cb.devcb.read_failed_contact_counter_timer =
      alarm_new("btm.read_failed_contact_counter_timer");
  btm_cb.devcb.read_automatic_flush_timeout_timer =
      alarm_new("btm.read_automatic_flush_timeout_timer");
  btm_cb.devcb.read_link_quality_timer =
      alarm_new("btm.read_link_quality_timer");
  btm_cb.devcb.read_tx_power_timer = alarm_new("btm.read_tx_power_timer");
}

void btm_dev_free() {
  alarm_free(btm_cb.devcb.read_local_name_timer);
  alarm_free(btm_cb.devcb.read_rssi_timer);
  alarm_free(btm_cb.devcb.read_failed_contact_counter_timer);
  alarm_free(btm_cb.devcb.read_automatic_flush_timeout_timer);
  alarm_free(btm_cb.devcb.read_link_quality_timer);
  alarm_free(btm_cb.devcb.read_tx_power_timer);
}

/*******************************************************************************
 *
 * Function         btm_db_reset
 *
 * Returns          void
 *
 ******************************************************************************/
void BTM_db_reset(void) {
  tBTM_CMPL_CB* p_cb;

  btm_inq_db_reset();

  if (btm_cb.devcb.p_rln_cmpl_cb) {
    p_cb = btm_cb.devcb.p_rln_cmpl_cb;
    btm_cb.devcb.p_rln_cmpl_cb = NULL;

    if (p_cb) (*p_cb)((void*)NULL);
  }

  if (btm_cb.devcb.p_rssi_cmpl_cb) {
    p_cb = btm_cb.devcb.p_rssi_cmpl_cb;
    btm_cb.devcb.p_rssi_cmpl_cb = NULL;

    if (p_cb) {
      tBTM_RSSI_RESULT btm_rssi_result;
      btm_rssi_result.status = BTM_DEV_RESET;
      (*p_cb)(&btm_rssi_result);
    }
  }

  if (btm_cb.devcb.p_failed_contact_counter_cmpl_cb) {
    p_cb = btm_cb.devcb.p_failed_contact_counter_cmpl_cb;
    btm_cb.devcb.p_failed_contact_counter_cmpl_cb = NULL;

    if (p_cb) {
      tBTM_FAILED_CONTACT_COUNTER_RESULT btm_failed_contact_counter_result;
      btm_failed_contact_counter_result.status = BTM_DEV_RESET;
      (*p_cb)(&btm_failed_contact_counter_result);
    }
  }

  if (btm_cb.devcb.p_automatic_flush_timeout_cmpl_cb) {
    p_cb = btm_cb.devcb.p_automatic_flush_timeout_cmpl_cb;
    btm_cb.devcb.p_automatic_flush_timeout_cmpl_cb = NULL;

    if (p_cb) {
      tBTM_AUTOMATIC_FLUSH_TIMEOUT_RESULT btm_automatic_flush_timeout_result;
      btm_automatic_flush_timeout_result.status = BTM_DEV_RESET;
      (*p_cb)(&btm_automatic_flush_timeout_result);
    }
  }
}

static bool set_sec_state_idle(void* data, void* context) {
  tBTM_SEC_DEV_REC* p_dev_rec = static_cast<tBTM_SEC_DEV_REC*>(data);
  p_dev_rec->sec_rec.sec_state = BTM_SEC_STATE_IDLE;
  return true;
}

void BTM_reset_complete() {
  const controller_t* controller = controller_get_interface();

  /* Tell L2CAP that all connections are gone */
  l2cu_device_reset();

  /* Clear current security state */
  list_foreach(btm_sec_cb.sec_dev_rec, set_sec_state_idle, NULL);

  /* After the reset controller should restore all parameters to defaults. */
  btm_cb.btm_inq_vars.inq_counter = 1;
  btm_cb.btm_inq_vars.inq_scan_window = HCI_DEF_INQUIRYSCAN_WINDOW;
  btm_cb.btm_inq_vars.inq_scan_period = HCI_DEF_INQUIRYSCAN_INTERVAL;
  btm_cb.btm_inq_vars.inq_scan_type = HCI_DEF_SCAN_TYPE;

  btm_cb.btm_inq_vars.page_scan_window = HCI_DEF_PAGESCAN_WINDOW;
  btm_cb.btm_inq_vars.page_scan_period = HCI_DEF_PAGESCAN_INTERVAL;
  btm_cb.btm_inq_vars.page_scan_type = HCI_DEF_SCAN_TYPE;

  btm_cb.ble_ctr_cb.set_connection_state_idle();
  connection_manager::reset(true);

  btm_pm_reset();

  l2c_link_init(controller->get_acl_buffer_count_classic());

  // setup the random number generator
  std::srand(std::time(nullptr));

  /* Set up the BLE privacy settings */
  if (controller->SupportsBle() && controller->SupportsBlePrivacy() &&
      controller->get_ble_resolving_list_max_size() > 0) {
    btm_ble_resolving_list_init(controller->get_ble_resolving_list_max_size());
    /* set the default random private address timeout */
    btsnd_hcic_ble_set_rand_priv_addr_timeout(
        btm_get_next_private_addrress_interval_ms() / 1000);
  } else {
    log::info(
        "Le Address Resolving list disabled due to lack of controller support");
  }

  if (controller->SupportsBle()) {
    l2c_link_processs_ble_num_bufs(controller->get_acl_buffer_count_ble());
  }

  BTM_SetPinType(btm_sec_cb.cfg.pin_type, btm_sec_cb.cfg.pin_code,
                 btm_sec_cb.cfg.pin_code_len);

  decode_controller_support();
}

/*******************************************************************************
 *
 * Function         BTM_IsDeviceUp
 *
 * Description      This function is called to check if the device is up.
 *
 * Returns          true if device is up, else false
 *
 ******************************************************************************/
bool BTM_IsDeviceUp(void) { return controller_get_interface()->get_is_ready(); }

/*******************************************************************************
 *
 * Function         btm_read_local_name_timeout
 *
 * Description      Callback when reading the local name times out.
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_read_local_name_timeout(UNUSED_ATTR void* data) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_rln_cmpl_cb;
  btm_cb.devcb.p_rln_cmpl_cb = NULL;
  if (p_cb) (*p_cb)((void*)NULL);
}

static void decode_controller_support() {
  /* Create (e)SCO supported packet types mask */
  btm_cb.btm_sco_pkt_types_supported = 0;
  btm_cb.sco_cb.esco_supported = false;
  if (bluetooth::shim::GetController()->SupportsSco()) {
    btm_cb.btm_sco_pkt_types_supported = ESCO_PKT_TYPES_MASK_HV1;

    if (bluetooth::shim::GetController()->SupportsHv2Packets())
      btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_HV2;

    if (bluetooth::shim::GetController()->SupportsHv3Packets())
      btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_HV3;
  }

  if (bluetooth::shim::GetController()->SupportsEv3Packets())
    btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_EV3;

  if (bluetooth::shim::GetController()->SupportsEv4Packets())
    btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_EV4;

  if (bluetooth::shim::GetController()->SupportsEv5Packets())
    btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_EV5;

  if (btm_cb.btm_sco_pkt_types_supported & BTM_ESCO_LINK_ONLY_MASK) {
    btm_cb.sco_cb.esco_supported = true;

    /* Add in EDR related eSCO types */
    if (bluetooth::shim::GetController()->SupportsEsco2mPhy()) {
      if (!bluetooth::shim::GetController()->Supports3SlotEdrPackets())
        btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_NO_2_EV5;
    } else {
      btm_cb.btm_sco_pkt_types_supported |=
          (ESCO_PKT_TYPES_MASK_NO_2_EV3 + ESCO_PKT_TYPES_MASK_NO_2_EV5);
    }

    if (bluetooth::shim::GetController()->SupportsEsco3mPhy()) {
      if (!bluetooth::shim::GetController()->Supports3SlotEdrPackets())
        btm_cb.btm_sco_pkt_types_supported |= ESCO_PKT_TYPES_MASK_NO_3_EV5;
    } else {
      btm_cb.btm_sco_pkt_types_supported |=
          (ESCO_PKT_TYPES_MASK_NO_3_EV3 + ESCO_PKT_TYPES_MASK_NO_3_EV5);
    }
  }

  log::verbose("Local supported SCO packet types: 0x{:04x}",
               btm_cb.btm_sco_pkt_types_supported);

  BTM_acl_after_controller_started(controller_get_interface());
  btm_sec_dev_reset();

  if (bluetooth::shim::GetController()->SupportsRssiWithInquiryResults()) {
    if (bluetooth::shim::GetController()->SupportsExtendedInquiryResponse())
      BTM_SetInquiryMode(BTM_INQ_RESULT_EXTENDED);
    else
      BTM_SetInquiryMode(BTM_INQ_RESULT_WITH_RSSI);
  }

  l2cu_set_non_flushable_pbf(
      bluetooth::shim::GetController()->SupportsNonFlushablePb());
  BTM_EnableInterlacedPageScan();
  BTM_EnableInterlacedInquiryScan();
}

/*******************************************************************************
 *
 * Function         BTM_SetLocalDeviceName
 *
 * Description      This function is called to set the local device name.
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetLocalDeviceName(const char* p_name) {
  uint8_t* p;

  if (!p_name || !p_name[0] || (strlen((char*)p_name) > BD_NAME_LEN))
    return (BTM_ILLEGAL_VALUE);

  if (!controller_get_interface()->get_is_ready()) return (BTM_DEV_RESET);
  /* Save the device name if local storage is enabled */
  p = (uint8_t*)btm_sec_cb.cfg.bd_name;
  if (p != (uint8_t*)p_name)
    strlcpy((char*)btm_sec_cb.cfg.bd_name, p_name, BTM_MAX_LOC_BD_NAME_LEN + 1);

  btsnd_hcic_change_name(p);
  return (BTM_CMD_STARTED);
}

/*******************************************************************************
 *
 * Function         BTM_ReadLocalDeviceName
 *
 * Description      This function is called to read the local device name.
 *
 * Returns          status of the operation
 *                  If success, BTM_SUCCESS is returned and p_name points stored
 *                              local device name
 *                  If BTM doesn't store local device name, BTM_NO_RESOURCES is
 *                              is returned and p_name is set to NULL
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadLocalDeviceName(const char** p_name) {
  *p_name = (const char*)btm_sec_cb.cfg.bd_name;
  return (BTM_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BTM_ReadLocalDeviceNameFromController
 *
 * Description      Get local device name from controller. Do not use cached
 *                  name (used to get chip-id prior to btm reset complete).
 *
 * Returns          BTM_CMD_STARTED if successful, otherwise an error
 *
 ******************************************************************************/
tBTM_STATUS BTM_ReadLocalDeviceNameFromController(
    tBTM_CMPL_CB* p_rln_cmpl_cback) {
  /* Check if rln already in progress */
  if (btm_cb.devcb.p_rln_cmpl_cb) return (BTM_NO_RESOURCES);

  /* Save callback */
  btm_cb.devcb.p_rln_cmpl_cb = p_rln_cmpl_cback;

  btsnd_hcic_read_name();
  alarm_set_on_mloop(btm_cb.devcb.read_local_name_timer,
                     BTM_DEV_NAME_REPLY_TIMEOUT_MS, btm_read_local_name_timeout,
                     NULL);

  return BTM_CMD_STARTED;
}

/*******************************************************************************
 *
 * Function         btm_read_local_name_complete
 *
 * Description      This function is called when local name read complete.
 *                  message is received from the HCI.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_read_local_name_complete(uint8_t* p, UNUSED_ATTR uint16_t evt_len) {
  tBTM_CMPL_CB* p_cb = btm_cb.devcb.p_rln_cmpl_cb;
  uint8_t status;

  alarm_cancel(btm_cb.devcb.read_local_name_timer);

  /* If there was a callback address for read local name, call it */
  btm_cb.devcb.p_rln_cmpl_cb = NULL;

  if (p_cb) {
    STREAM_TO_UINT8(status, p);

    if (status == HCI_SUCCESS)
      (*p_cb)(p);
    else
      (*p_cb)(NULL);
  }
}

/*******************************************************************************
 *
 * Function         BTM_SetDeviceClass
 *
 * Description      This function is called to set the local device class
 *
 * Returns          status of the operation
 *
 ******************************************************************************/
tBTM_STATUS BTM_SetDeviceClass(DEV_CLASS dev_class) {
  if (btm_cb.devcb.dev_class == dev_class) return (BTM_SUCCESS);

  btm_cb.devcb.dev_class = dev_class;

  if (!controller_get_interface()->get_is_ready()) return (BTM_DEV_RESET);

  btsnd_hcic_write_dev_class(dev_class);

  return (BTM_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BTM_ReadDeviceClass
 *
 * Description      This function is called to read the local device class
 *
 * Returns          the device class
 *
 ******************************************************************************/
DEV_CLASS BTM_ReadDeviceClass(void) { return btm_cb.devcb.dev_class; }

/*******************************************************************************
 *
 * Function         BTM_VendorSpecificCommand
 *
 * Description      Send a vendor specific HCI command to the controller.
 *
 * Notes
 *      Opcode will be OR'd with HCI_GRP_VENDOR_SPECIFIC.
 *
 ******************************************************************************/
void BTM_VendorSpecificCommand(uint16_t opcode, uint8_t param_len,
                               uint8_t* p_param_buf, tBTM_VSC_CMPL_CB* p_cb) {
  log::verbose("BTM: Opcode: 0x{:04X}, ParamLen: {}.", opcode, param_len);

  /* Send the HCI command (opcode will be OR'd with HCI_GRP_VENDOR_SPECIFIC) */
  btsnd_hcic_vendor_spec_cmd(opcode, param_len, p_param_buf, p_cb);
}

/*******************************************************************************
 *
 * Function         BTM_RegisterForVSEvents
 *
 * Description      This function is called to register/deregister for vendor
 *                  specific HCI events.
 *
 *                  If is_register=true, then the function will be registered;
 *                  otherwise, the the function will be deregistered.
 *
 * Returns          BTM_SUCCESS if successful,
 *                  BTM_BUSY if maximum number of callbacks have already been
 *                           registered.
 *
 ******************************************************************************/
tBTM_STATUS BTM_RegisterForVSEvents(tBTM_VS_EVT_CB* p_cb, bool is_register) {
  tBTM_STATUS retval = BTM_SUCCESS;
  uint8_t i, free_idx = BTM_MAX_VSE_CALLBACKS;

  /* See if callback is already registered */
  for (i = 0; i < BTM_MAX_VSE_CALLBACKS; i++) {
    if (btm_cb.devcb.p_vend_spec_cb[i] == NULL) {
      /* Found a free slot. Store index */
      free_idx = i;
    } else if (btm_cb.devcb.p_vend_spec_cb[i] == p_cb) {
      /* Found callback in lookup table. If deregistering, clear the entry. */
      if (!is_register) {
        btm_cb.devcb.p_vend_spec_cb[i] = NULL;
        log::verbose("BTM Deregister For VSEvents is successfully");
      }
      return (BTM_SUCCESS);
    }
  }

  /* Didn't find callback. Add callback to free slot if registering */
  if (is_register) {
    if (free_idx < BTM_MAX_VSE_CALLBACKS) {
      btm_cb.devcb.p_vend_spec_cb[free_idx] = p_cb;
      log::verbose("BTM Register For VSEvents is successfully");
    } else {
      /* No free entries available */
      log::error("BTM_RegisterForVSEvents: too many callbacks registered");

      retval = BTM_NO_RESOURCES;
    }
  }

  return (retval);
}

/*******************************************************************************
 *
 * Function         btm_vendor_specific_evt
 *
 * Description      Process event HCI_VENDOR_SPECIFIC_EVT (BQR)
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_vendor_specific_evt(const uint8_t* p, uint8_t evt_len) {
  uint8_t sub_event_code = HCI_VSE_SUBCODE_BQR_SUB_EVT;
  uint8_t bqr_parameter_length = evt_len;
  const uint8_t* p_bqr_event = p;

  log::verbose("BTM Event: Vendor Specific event from controller");

        // The stream currently points to the BQR sub-event parameters
        switch (sub_event_code) {
        case bluetooth::bqr::QUALITY_REPORT_ID_LMP_LL_MESSAGE_TRACE:
          if (bqr_parameter_length >= bluetooth::bqr::kLogDumpParamTotalLen) {
            bluetooth::bqr::DumpLmpLlMessage(bqr_parameter_length, p_bqr_event);
          } else {
            log::info("Malformed LMP event of length {}", bqr_parameter_length);
          }

          break;

        case bluetooth::bqr::QUALITY_REPORT_ID_BT_SCHEDULING_TRACE:
          if (bqr_parameter_length >= bluetooth::bqr::kLogDumpParamTotalLen) {
            bluetooth::bqr::DumpBtScheduling(bqr_parameter_length, p_bqr_event);
          } else {
            log::info("Malformed TRACE event of length {}",
                      bqr_parameter_length);
          }
          break;

        default:
          log::info("Unhandled BQR subevent 0x{:02x}x", sub_event_code);
        }

        uint8_t i;
        std::vector<uint8_t> reconstructed_event;
        reconstructed_event.reserve(4 + bqr_parameter_length);
        reconstructed_event[0] = HCI_VENDOR_SPECIFIC_EVT;
        reconstructed_event[1] = 3 + bqr_parameter_length;  // event size
        reconstructed_event[2] = HCI_VSE_SUBCODE_BQR_SUB_EVT;
        for (i = 0; i < bqr_parameter_length; i++) {
          reconstructed_event.emplace_back(p[i]);
        }

  for (i = 0; i < BTM_MAX_VSE_CALLBACKS; i++) {
    if (btm_cb.devcb.p_vend_spec_cb[i])
      (*btm_cb.devcb.p_vend_spec_cb[i])(reconstructed_event.size(),
                                        reconstructed_event.data());
  }
}

/*******************************************************************************
 *
 * Function         BTM_WritePageTimeout
 *
 * Description      Send HCI Write Page Timeout.
 *
 ******************************************************************************/
void BTM_WritePageTimeout(uint16_t timeout) {
  log::verbose("BTM: BTM_WritePageTimeout: Timeout: {}.", timeout);

  /* Send the HCI command */
  btsnd_hcic_write_page_tout(timeout);
}

/*******************************************************************************
 *
 * Function         BTM_WriteVoiceSettings
 *
 * Description      Send HCI Write Voice Settings command.
 *                  See hcidefs.h for settings bitmask values.
 *
 ******************************************************************************/
void BTM_WriteVoiceSettings(uint16_t settings) {
  log::verbose("BTM: BTM_WriteVoiceSettings: Settings: 0x{:04x}.", settings);

  /* Send the HCI command */
  btsnd_hcic_write_voice_settings((uint16_t)(settings & 0x03ff));
}

/*******************************************************************************
 *
 * Function         BTM_EnableTestMode
 *
 * Description      Send HCI the enable device under test command.
 *
 *                  Note: Controller can only be taken out of this mode by
 *                      resetting the controller.
 *
 * Returns
 *      BTM_SUCCESS         Command sent.
 *      BTM_NO_RESOURCES    If out of resources to send the command.
 *
 *
 ******************************************************************************/
tBTM_STATUS BTM_EnableTestMode(void) {
  uint8_t cond;

  log::verbose("BTM: BTM_EnableTestMode");

  /* set auto accept connection as this is needed during test mode */
  /* Allocate a buffer to hold HCI command */
  cond = HCI_DO_AUTO_ACCEPT_CONNECT;
  btsnd_hcic_set_event_filter(HCI_FILTER_CONNECTION_SETUP,
                              HCI_FILTER_COND_NEW_DEVICE, &cond, sizeof(cond));

  /* put device to connectable mode */
  if (BTM_SetConnectability(BTM_CONNECTABLE) != BTM_SUCCESS) {
    return BTM_NO_RESOURCES;
  }

  /* put device to discoverable mode */
  if (BTM_SetDiscoverability(BTM_GENERAL_DISCOVERABLE) != BTM_SUCCESS) {
    return BTM_NO_RESOURCES;
  }

  /* mask off all of event from controller */
  bluetooth::shim::BTM_ClearEventMask();

  /* Send the HCI command */
  btsnd_hcic_enable_test_mode();
  return (BTM_SUCCESS);
}

/*******************************************************************************
 *
 * Function         BTM_DeleteStoredLinkKey
 *
 * Description      This function is called to delete link key for the specified
 *                  device addresses from the NVRAM storage attached to the
 *                  Bluetooth controller.
 *
 * Parameters:      bd_addr      - Addresses of the devices
 *                  p_cb         - Call back function to be called to return
 *                                 the results
 *
 ******************************************************************************/
tBTM_STATUS BTM_DeleteStoredLinkKey(const RawAddress* bd_addr,
                                    tBTM_CMPL_CB* p_cb) {
  /* Read and Write STORED link key stems from a legacy use-case and is no
   * longer expected to be used. Disable explicitly for Floss and queue overall
   * deletion from Fluoride.
   */
#if !defined(TARGET_FLOSS)
  /* Check if the previous command is completed */
  if (btm_sec_cb.devcb.p_stored_link_key_cmpl_cb) return (BTM_BUSY);

  bool delete_all_flag = !bd_addr;

  log::verbose("BTM: BTM_DeleteStoredLinkKey: delete_all_flag: {}",
               delete_all_flag ? "true" : "false");

  btm_sec_cb.devcb.p_stored_link_key_cmpl_cb = p_cb;
  if (!bd_addr) {
    /* This is to delete all link keys */
    /* We don't care the BD address. Just pass a non zero pointer */
    RawAddress local_bd_addr = RawAddress::kEmpty;
    btsnd_hcic_delete_stored_key(local_bd_addr, delete_all_flag);
  } else {
    btsnd_hcic_delete_stored_key(*bd_addr, delete_all_flag);
  }
#endif

  return (BTM_SUCCESS);
}

/*******************************************************************************
 *
 * Function         btm_delete_stored_link_key_complete
 *
 * Description      This function is called when the command complete message
 *                  is received from the HCI for the delete stored link key
 *                  command.
 *
 * Returns          void
 *
 ******************************************************************************/
void btm_delete_stored_link_key_complete(uint8_t* p, uint16_t evt_len) {
  tBTM_CMPL_CB* p_cb = btm_sec_cb.devcb.p_stored_link_key_cmpl_cb;
  tBTM_DELETE_STORED_LINK_KEY_COMPLETE result;

  /* If there was a callback registered for read stored link key, call it */
  btm_sec_cb.devcb.p_stored_link_key_cmpl_cb = NULL;

  if (p_cb) {
    /* Set the call back event to indicate command complete */
    result.event = BTM_CB_EVT_DELETE_STORED_LINK_KEYS;

    if (evt_len < 3) {
      log::error("Malformatted event packet, too short");
      return;
    }

    /* Extract the result fields from the HCI event */
    STREAM_TO_UINT8(result.status, p);
    STREAM_TO_UINT16(result.num_keys, p);

    /* Call the call back and pass the result */
    (*p_cb)(&result);
  }
}

/*******************************************************************************
 *
 * Function         BTM_BT_Quality_Report_VSE_CBack
 *
 * Description      Callback invoked on receiving of Vendor Specific Events.
 *                  This function will call registered BQR report receiver if
 *                  Bluetooth Quality Report sub-event is identified.
 *
 * Parameters:      length - Lengths of all of the parameters contained in the
 *                    Vendor Specific Event.
 *                  p_stream - A pointer to the quality report which is sent
 *                    from the Bluetooth controller via Vendor Specific Event.
 *
 ******************************************************************************/
static void BTM_BT_Quality_Report_VSE_CBack(uint8_t length,
                                            const uint8_t* p_stream) {
  if (length == 0) {
    log::warn("Lengths of all of the parameters are zero.");
    return;
  }

  uint8_t sub_event = 0;
  STREAM_TO_UINT8(sub_event, p_stream);
  length--;

  if (sub_event == HCI_VSE_SUBCODE_BQR_SUB_EVT) {
    if (btm_cb.p_bqr_report_receiver == nullptr) {
      log::warn("No registered report receiver.");
      return;
    }

    btm_cb.p_bqr_report_receiver(length, p_stream);
  }
}

/*******************************************************************************
 *
 * Function         BTM_BT_Quality_Report_VSE_Register
 *
 * Description      Register/Deregister for Bluetooth Quality Report VSE sub
 *                  event Callback.
 *
 * Parameters:      is_register - True/False to register/unregister for VSE.
 *                  p_bqr_report_receiver - The receiver for receiving Bluetooth
 *                    Quality Report VSE sub event.
 *
 ******************************************************************************/
tBTM_STATUS BTM_BT_Quality_Report_VSE_Register(
    bool is_register, tBTM_BT_QUALITY_REPORT_RECEIVER* p_bqr_report_receiver) {
  tBTM_STATUS retval =
      BTM_RegisterForVSEvents(BTM_BT_Quality_Report_VSE_CBack, is_register);

  if (retval != BTM_SUCCESS) {
    log::warn("Fail to (un)register VSEvents: {}, is_register: {}", retval,
              logbool(is_register));
    return retval;
  }

  if (is_register) {
    btm_cb.p_bqr_report_receiver = p_bqr_report_receiver;
  } else {
    btm_cb.p_bqr_report_receiver = nullptr;
  }

  log::info("Success to (un)register VSEvents. is_register: {}",
            logbool(is_register));
  return retval;
}
