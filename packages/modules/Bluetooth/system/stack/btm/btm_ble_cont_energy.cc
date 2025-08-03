/******************************************************************************
 *
 *  Copyright 2014  Broadcom Corporation
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

#define LOG_TAG "btm_ble_cont_energy"

#include <bluetooth/log.h>
#include <inttypes.h>
#include <string.h>

#include "btm_ble_api.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_types.h"

using namespace bluetooth;

extern tBTM_CB btm_cb;

tBTM_BLE_ENERGY_INFO_CB ble_energy_info_cb;

/*******************************************************************************
 *
 * Function         btm_ble_cont_energy_cmpl_cback
 *
 * Description      Controller VSC complete callback
 *
 * Parameters
 *
 * Returns          void
 *
 ******************************************************************************/
static void btm_ble_cont_energy_cmpl_cback(tBTM_VSC_CMPL* p_params) {
  uint8_t* p = p_params->p_param_buf;
  uint16_t len = p_params->param_len;
  uint32_t total_tx_time = 0, total_rx_time = 0, total_idle_time = 0,
           total_energy_used = 0;

  if (len < 17) {
    log::error("wrong length for btm_ble_cont_energy_cmpl_cback");
    return;
  }

  uint8_t raw_status;
  STREAM_TO_UINT8(raw_status, p);
  tHCI_STATUS status = to_hci_status_code(raw_status);
  STREAM_TO_UINT32(total_tx_time, p);
  STREAM_TO_UINT32(total_rx_time, p);
  STREAM_TO_UINT32(total_idle_time, p);
  STREAM_TO_UINT32(total_energy_used, p);

  log::verbose(
      "energy_info status={},tx_t={}, rx_t={}, ener_used={}, idle_t={}", status,
      total_tx_time, total_rx_time, total_energy_used, total_idle_time);

  if (NULL != ble_energy_info_cb.p_ener_cback)
    ble_energy_info_cb.p_ener_cback(total_tx_time, total_rx_time,
                                    total_idle_time, total_energy_used,
                                    static_cast<tHCI_STATUS>(status));

  return;
}

/*******************************************************************************
 *
 * Function         BTM_BleGetEnergyInfo
 *
 * Description      This function obtains the energy info
 *
 * Parameters      p_ener_cback - Callback pointer
 *
 * Returns          status
 *
 ******************************************************************************/
tBTM_STATUS BTM_BleGetEnergyInfo(tBTM_BLE_ENERGY_INFO_CBACK* p_ener_cback) {
  tBTM_BLE_VSC_CB cmn_ble_vsc_cb;

  BTM_BleGetVendorCapabilities(&cmn_ble_vsc_cb);

  log::verbose("BTM_BleGetEnergyInfo");

  if (0 == cmn_ble_vsc_cb.energy_support) {
    log::error("Controller does not support get energy info");
    return BTM_ERR_PROCESSING;
  }

  ble_energy_info_cb.p_ener_cback = p_ener_cback;
  BTM_VendorSpecificCommand(HCI_BLE_ENERGY_INFO, 0, NULL,
                            btm_ble_cont_energy_cmpl_cback);
  return BTM_CMD_STARTED;
}
