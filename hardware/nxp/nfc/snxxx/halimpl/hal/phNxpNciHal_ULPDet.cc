/*
 * Copyright 2022-2023 NXP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "phNxpNciHal_ULPDet.h"

#include <phNxpLog.h>
#include <phTmlNfc.h>

#include "phNfcCommon.h"
#include "phNxpNciHal_IoctlOperations.h"
#include "phNxpNciHal_PowerTrackerIface.h"
#include "phNxpNciHal_extOperations.h"

extern phNxpNciHal_Control_t nxpncihal_ctrl;
extern NFCSTATUS phNxpNciHal_ext_send_sram_config_to_flash();
extern PowerTrackerHandle gPowerTrackerHandle;

/*******************************************************************************
**
** Function         phNxpNciHal_isULPDetSupported()
**
** Description      this function is to check ULPDet feature is supported or not
**
** Returns          true or false
*******************************************************************************/
bool phNxpNciHal_isULPDetSupported() {
  unsigned long num = 0;
  if ((GetNxpNumValue(NAME_NXP_DEFAULT_ULPDET_MODE, &num, sizeof(num)))) {
    if ((uint8_t)num > 0) {
      NXPLOG_NCIHAL_E("%s: NxpNci isULPDetSupported true", __func__);
      return true;
    }
  }
  NXPLOG_NCIHAL_E("%s: NxpNci isULPDetSupported false", __func__);
  return false;
}

/*******************************************************************************
**
** Function         phNxpNciHal_setULPDetFlag()
**
** Description      this function is called by Framework API to set ULPDet mode
**                  enable/disable
**
** Parameters       flag - true to enable ULPDet, false to disable
**
** Returns          true or false
*******************************************************************************/
void phNxpNciHal_setULPDetFlag(bool flag) {
  nxpncihal_ctrl.isUlpdetModeEnabled = flag;
  if (gPowerTrackerHandle.stateChange != NULL) {
    RefreshNfccPowerState state = (flag) ? ULPDET_ON : ULPDET_OFF;
    gPowerTrackerHandle.stateChange(state);
  }
}

/*******************************************************************************
**
** Function         phNxpNciHal_getULPDetFlag()
**
** Description      this function get the ULPDet state, true if it is enabled
**                  false if it is disabled
**
** Returns          true or false
*******************************************************************************/
bool phNxpNciHal_getULPDetFlag() { return nxpncihal_ctrl.isUlpdetModeEnabled; }

/*******************************************************************************
**
** Function         phNxpNciHal_propConfULPDetMode()
**
** Description      this function applies the configurations to enable/disable
**                  ULPDet Mode
**
** Parameters       bEnable - true to enable, false to disable
**
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
NFCSTATUS phNxpNciHal_propConfULPDetMode(bool bEnable) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  NXPLOG_NCIHAL_E("%s flag %d", __func__, bEnable);
  if (!phNxpNciHal_isULPDetSupported()) return false;

  uint8_t cmd_coreULPDET[] = {0x2F, 0x00, 0x01, 0x03};
  uint8_t getConfig_A015[] = {0x20, 0x03, 0x03, 0x01, 0xA0, 0x15};
  uint8_t setConfig_A015[] = {0x20, 0x02, 0x05, 0x01, 0xA0, 0x15, 0x01, 0x01};
  uint8_t getConfig_A10F[] = {0x20, 0x03, 0x03, 0x01, 0xA1, 0x0F};
  uint8_t setConfig_A10F[] = {0x20, 0x02, 0x06, 0x01, 0xA1,
                              0x0F, 0x02, 0x00, 0x00};

  uint8_t setUlpdetConfig[] = {0x20, 0x02, 0x0A, 0x02, 0xA0, 0x15, 0x01,
                               0x02, 0xA1, 0x0F, 0x02, 0x01, 0x01};

  do {
    if (bEnable) {
      status =
          phNxpNciHal_send_ext_cmd(sizeof(setUlpdetConfig), setUlpdetConfig);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("ulpdet configs set: Failed");
        break;
      }

      status = phNxpNciHal_send_ext_cmd(sizeof(cmd_coreULPDET), cmd_coreULPDET);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("coreULPDET: Failed");
        break;
      }

      nxpncihal_ctrl.halStatus = HAL_STATUS_CLOSE;
      status = phNxpNciHal_ext_send_sram_config_to_flash();
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("Updation of the SRAM contents failed");
        break;
      }
      (void)phTmlNfc_IoCtl(phTmlNfc_e_PullVenLow);
    } else {
      status = phNxpNciHal_send_ext_cmd(sizeof(getConfig_A015), getConfig_A015);
      if ((status == NFCSTATUS_SUCCESS) &&
          (nxpncihal_ctrl.p_rx_data[8] != 0x01)) {
        status =
            phNxpNciHal_send_ext_cmd(sizeof(setConfig_A015), setConfig_A015);
        if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("Set Config : Failed");
        }
      }

      status = phNxpNciHal_send_ext_cmd(sizeof(getConfig_A10F), getConfig_A10F);
      if ((status == NFCSTATUS_SUCCESS) &&
          (nxpncihal_ctrl.p_rx_data[8] != 0x00)) {
        status =
            phNxpNciHal_send_ext_cmd(sizeof(setConfig_A10F), setConfig_A10F);
        if (status != NFCSTATUS_SUCCESS) {
          NXPLOG_NCIHAL_E("Set Config: Failed");
        }
      }
      /* reset the flag upon exit ulpdet mode */
      phNxpNciHal_setULPDetFlag(false);
    }
  } while (false);
  NXPLOG_NCIHAL_E("%s: exit. status = %d", __func__, status);

  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_handleULPDetCommand()
**
** Description      This handles the ULPDET command and sets the ULPDET flag
**
** Returns          It returns number of bytes received.
*******************************************************************************/
int phNxpNciHal_handleULPDetCommand(uint16_t data_len, const uint8_t* p_data) {
  if (data_len <= 4) {
    return 0;
  }
  uint8_t status = NCI_RSP_FAIL;
  if (phNxpNciHal_isULPDetSupported()) {
    phNxpNciHal_setULPDetFlag(p_data[NCI_MSG_INDEX_FEATURE_VALUE]);
    status = NCI_RSP_OK;
  }

  phNxpNciHal_vendorSpecificCallback(p_data[NCI_OID_INDEX],
                                     p_data[NCI_MSG_INDEX_FOR_FEATURE],  status);

  return p_data[NCI_MSG_LEN_INDEX];
}
