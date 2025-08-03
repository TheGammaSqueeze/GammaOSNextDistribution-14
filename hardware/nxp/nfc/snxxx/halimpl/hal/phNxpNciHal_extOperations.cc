/*
 * Copyright 2019-2023 NXP
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

#include "phNxpNciHal_extOperations.h"

#include <phNxpLog.h>
#include <phTmlNfc.h>

#include "phNfcCommon.h"
#include "phNxpNciHal_IoctlOperations.h"
#include "phNxpNciHal_ULPDet.h"

#define NCI_HEADER_SIZE 3
#define NCI_SE_CMD_LEN 4
nxp_nfc_config_ext_t config_ext;
static vector<uint8_t> uicc1HciParams(0);
static vector<uint8_t> uicc2HciParams(0);
static vector<uint8_t> uiccHciCeParams(0);
extern phNxpNciHal_Control_t nxpncihal_ctrl;
extern phTmlNfc_Context_t* gpphTmlNfc_Context;
extern NFCSTATUS phNxpNciHal_ext_send_sram_config_to_flash();

/*******************************************************************************
**
** Function         phNxpNciHal_getExtVendorConfig()
**
** Description      this function gets and updates the extension params
**
*******************************************************************************/
void phNxpNciHal_getExtVendorConfig() {
  unsigned long num = 0;
  memset(&config_ext, 0x00, sizeof(nxp_nfc_config_ext_t));

  if ((GetNxpNumValue(NAME_NXP_AUTONOMOUS_ENABLE, &num, sizeof(num)))) {
    config_ext.autonomous_mode = (uint8_t)num;
  }
  if ((GetNxpNumValue(NAME_NXP_GUARD_TIMER_VALUE, &num, sizeof(num)))) {
    config_ext.guard_timer_value = (uint8_t)num;
  }
}

/******************************************************************************
 * Function         phNxpNciHal_updateAutonomousPwrState
 *
 * Description      This function can be used to update autonomous pwr state.
 *                  num: value to check  switch off bit is set or not.
 *
 * Returns          uint8_t
 *
 ******************************************************************************/
uint8_t phNxpNciHal_updateAutonomousPwrState(uint8_t num) {
  if ((config_ext.autonomous_mode == true) &&
      ((num & SWITCH_OFF_MASK) == SWITCH_OFF_MASK)) {
    num = (num | AUTONOMOUS_SCREEN_OFF_LOCK_MASK);
  }
  return num;
}
/******************************************************************************
 * Function         phNxpNciHal_setAutonomousMode
 *
 * Description      This function can be used to set NFCC in autonomous mode
 *
 * Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
 *                  or NFCSTATUS_FEATURE_NOT_SUPPORTED
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_setAutonomousMode() {
  if (IS_CHIP_TYPE_L(sn100u)) {
    NXPLOG_NCIHAL_D("%s : Not applicable for chipType %s", __func__,
                    pConfigFL->product[nfcFL.chipType]);
    return NFCSTATUS_SUCCESS;
  }
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  uint8_t autonomous_mode_value = 0x01;
  if (config_ext.autonomous_mode == true) autonomous_mode_value = 0x02;

  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  mEEPROM_info.buffer = (uint8_t*)&autonomous_mode_value;
  mEEPROM_info.bufflen = sizeof(autonomous_mode_value);
  mEEPROM_info.request_type = EEPROM_AUTONOMOUS_MODE;

  return request_EEPROM(&mEEPROM_info);
}
/******************************************************************************
 * Function         phNxpNciHal_setGuardTimer
 *
 * Description      This function can be used to set nfcc Guard timer
 *
 * Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_setGuardTimer() {
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  NFCSTATUS status = NFCSTATUS_FEATURE_NOT_SUPPORTED;

  if (IS_CHIP_TYPE_GE(sn100u)) {
    if (config_ext.autonomous_mode != true) config_ext.guard_timer_value = 0x00;

    mEEPROM_info.request_mode = SET_EEPROM_DATA;
    mEEPROM_info.buffer = &config_ext.guard_timer_value;
    mEEPROM_info.bufflen = sizeof(config_ext.guard_timer_value);
    mEEPROM_info.request_type = EEPROM_GUARD_TIMER;

    status = request_EEPROM(&mEEPROM_info);
  }
  return status;
}

/******************************************************************************
 * Function         get_system_property_se_type
 *
 * Description      This will read NFCEE status from system properties
 *                  and returns status.
 *
 * Returns          NFCEE enabled(0x01)/disabled(0x00)
 *
 ******************************************************************************/
static int8_t get_system_property_se_type(uint8_t se_type) {
  int8_t retVal = -1;
  char valueStr[PROPERTY_VALUE_MAX] = {0};
  if (se_type >= NUM_SE_TYPES) return retVal;
  int len = 0;
  switch (se_type) {
    case SE_TYPE_ESE:
      len = property_get("nfc.product.support.ese", valueStr, "");
      break;
    case SE_TYPE_EUICC:
      len = property_get("nfc.product.support.euicc", valueStr, "");
      break;
    case SE_TYPE_UICC:
      len = property_get("nfc.product.support.uicc", valueStr, "");
      break;
    case SE_TYPE_UICC2:
      len = property_get("nfc.product.support.uicc2", valueStr, "");
      break;
  }
  if (strlen(valueStr) == 0 || len <= 0) {
    return retVal;
  }
  retVal = atoi(valueStr);
  return retVal;
}

/******************************************************************************
 * Function         phNxpNciHal_read_and_update_se_state
 *
 * Description      This will read NFCEE status from system properties
 *                  and update to NFCC to enable/disable.
 *
 * Returns          none
 *
 ******************************************************************************/
void phNxpNciHal_read_and_update_se_state() {
  NFCSTATUS status = NFCSTATUS_FAILED;
  int16_t i = 0;
  int8_t val = -1;
  int16_t num_se = 0;
  uint8_t retry_cnt = 0;
  int8_t values[NUM_SE_TYPES];

  for (i = 0; i < NUM_SE_TYPES; i++) {
    val = get_system_property_se_type(i);
    switch (i) {
      case SE_TYPE_ESE:
        NXPLOG_NCIHAL_D("Get property : SUPPORT_ESE %d", val);
        values[SE_TYPE_ESE] = val;
        if (val > -1) {
          num_se++;
        }
        break;
      case SE_TYPE_EUICC:
        NXPLOG_NCIHAL_D("Get property : SUPPORT_EUICC %d", val);
        values[SE_TYPE_EUICC] = val;
        // Since eSE and eUICC share the same config address
        // They account for one SE
        if (val > -1 && values[SE_TYPE_ESE] == -1) {
          num_se++;
        }
        break;
      case SE_TYPE_UICC:
        NXPLOG_NCIHAL_D("Get property : SUPPORT_UICC %d", val);
        values[SE_TYPE_UICC] = val;
        if (val > -1) {
          num_se++;
        }
        break;
      case SE_TYPE_UICC2:
        values[SE_TYPE_UICC2] = val;
        if (val > -1) {
          num_se++;
        }
        NXPLOG_NCIHAL_D("Get property : SUPPORT_UICC2 %d", val);
        break;
    }
  }
  if (num_se < 1) {
    return;
  }
  uint8_t set_cfg_cmd[NCI_HEADER_SIZE + 1 +
                      (num_se * NCI_SE_CMD_LEN)];  // 1 for Number of Argument
  uint8_t* index = &set_cfg_cmd[0];
  *index++ = NCI_MT_CMD;
  *index++ = NXP_CORE_SET_CONFIG_CMD;
  *index++ = (num_se * NCI_SE_CMD_LEN) + 1;
  *index++ = num_se;
  for (i = 0; i < NUM_SE_TYPES; i++) {
    switch (i) {
      case SE_TYPE_ESE:
      case SE_TYPE_EUICC:
        if (values[SE_TYPE_ESE] == -1 && values[SE_TYPE_EUICC] == -1) {
          // No value defined
          break;
        }
        *index++ = 0xA0;
        *index++ = 0xED;
        *index++ = 0x01;

        *index = 0x00;
        if (values[SE_TYPE_ESE] > -1) {
          *index = *index | values[SE_TYPE_ESE];
        }
        if (values[SE_TYPE_EUICC] > -1) {
          *index = *index | values[SE_TYPE_EUICC] << 1;
        }
        NXPLOG_NCIHAL_D("Combined value for eSE/eUICC is 0x%.2x", *index);
        index++;
        i++;  // both cases taken care

        break;
      case SE_TYPE_UICC:
        if (values[SE_TYPE_UICC] > -1) {
          *index++ = 0xA0;
          *index++ = 0xEC;
          *index++ = 0x01;
          *index++ = values[SE_TYPE_UICC];
        }
        break;
      case SE_TYPE_UICC2:
        if (values[SE_TYPE_UICC2] > -1) {
          *index++ = 0xA0;
          *index++ = 0xD4;
          *index++ = 0x01;
          *index++ = values[SE_TYPE_UICC2];
        }
        break;
    }
  }

  while (status != NFCSTATUS_SUCCESS && retry_cnt < 3) {
    status = phNxpNciHal_send_ext_cmd(sizeof(set_cfg_cmd), set_cfg_cmd);
    retry_cnt++;
    NXPLOG_NCIHAL_E("set Cfg Retry cnt=%x", retry_cnt);
  }
}

/******************************************************************************
 * Function         phNxpNciHal_read_fw_dw_status
 *
 * Description      This will read the value of fw download status flag
 *                  from eeprom
 *
 * Parameters       value - this parameter will be updated with the flag
 *                  value from eeprom.
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_read_fw_dw_status(uint8_t& value) {
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  mEEPROM_info.buffer = &value;
  mEEPROM_info.bufflen = sizeof(value);
  mEEPROM_info.request_type = EEPROM_FW_DWNLD;
  mEEPROM_info.request_mode = GET_EEPROM_DATA;
  return request_EEPROM(&mEEPROM_info);
}

/******************************************************************************
 * Function         phNxpNciHal_write_fw_dw_status
 *
 * Description      This will update value of fw download status flag
 *                  to eeprom
 *
 * Parameters       value - this value will be updated to eeprom flag.
 *
 * Returns          status of the write
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_write_fw_dw_status(uint8_t value) {
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  mEEPROM_info.buffer = &value;
  mEEPROM_info.bufflen = sizeof(value);
  mEEPROM_info.request_type = EEPROM_FW_DWNLD;
  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  return request_EEPROM(&mEEPROM_info);
}

/******************************************************************************
 * Function         phNxpNciHal_save_uicc_params
 *
 * Description      This will read the UICC HCI param values
 *                  from eeprom and store in global variable
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_save_uicc_params() {
  if (IS_CHIP_TYPE_L(sn220u)) {
    NXPLOG_NCIHAL_E("%s Not supported", __func__);
    return NFCSTATUS_SUCCESS;
  }

  NFCSTATUS status = NFCSTATUS_FAILED;

  /* Getting UICC2 CL params */
  uicc1HciParams.resize(0xFF);
  status = phNxpNciHal_get_uicc_hci_params(
      uicc1HciParams, uicc1HciParams.size(), EEPROM_UICC1_SESSION_ID);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s: Save UICC1 CLPP failed .", __func__);
  }

  /* Getting UICC2 CL params */
  uicc2HciParams.resize(0xFF);
  status = phNxpNciHal_get_uicc_hci_params(
      uicc2HciParams, uicc2HciParams.size(), EEPROM_UICC2_SESSION_ID);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s: Save UICC2 CLPP failed .", __func__);
  }

  /* Get UICC CE HCI State */
  uiccHciCeParams.resize(0xFF);
  status = phNxpNciHal_get_uicc_hci_params(
      uiccHciCeParams, uiccHciCeParams.size(), EEPROM_UICC_HCI_CE_STATE);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("%s: Save UICC_HCI_CE_STATE failed .", __func__);
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_restore_uicc_params
 *
 * Description      This will set the UICC HCI param values
 *                  back to eeprom from global variable
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_restore_uicc_params() {
  if (IS_CHIP_TYPE_L(sn220u)) {
    NXPLOG_NCIHAL_E("%s Not supported", __func__);
    return NFCSTATUS_SUCCESS;
  }

  NFCSTATUS status = NFCSTATUS_FAILED;
  if (uicc1HciParams.size() > 0) {
    status = phNxpNciHal_set_uicc_hci_params(
        uicc1HciParams, uicc1HciParams.size(), EEPROM_UICC1_SESSION_ID);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("%s: Restore UICC1 CLPP failed .", __func__);
    } else {
      uicc1HciParams.resize(0);
    }
  }
  if (uicc2HciParams.size() > 0) {
    status = phNxpNciHal_set_uicc_hci_params(
        uicc2HciParams, uicc2HciParams.size(), EEPROM_UICC2_SESSION_ID);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("%s: Restore UICC2 CLPP failed .", __func__);
    } else {
      uicc2HciParams.resize(0);
    }
  }
  if (uiccHciCeParams.size() > 0) {
    status = phNxpNciHal_set_uicc_hci_params(
        uiccHciCeParams, uiccHciCeParams.size(), EEPROM_UICC_HCI_CE_STATE);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_E("%s: Restore UICC_HCI_CE_STATE failed .", __func__);
    } else {
      uiccHciCeParams.resize(0);
    }
  }
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_get_uicc_hci_params
 *
 * Description      This will read the UICC HCI param values
 *                  from eeprom
 *
 * Parameters       value - this parameter will be updated with the flag
 *                  value from eeprom.
 *
 * Returns          status of the read
 *
 ******************************************************************************/
NFCSTATUS
phNxpNciHal_get_uicc_hci_params(vector<uint8_t>& ptr, uint8_t bufflen,
                                phNxpNci_EEPROM_request_type_t uiccType) {
  if (IS_CHIP_TYPE_L(sn220u)) {
    NXPLOG_NCIHAL_E("%s Not supported", __func__);
    return NFCSTATUS_SUCCESS;
  }
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  mEEPROM_info.buffer = &ptr[0];
  mEEPROM_info.bufflen = bufflen;
  mEEPROM_info.request_type = uiccType;
  mEEPROM_info.request_mode = GET_EEPROM_DATA;
  NFCSTATUS status = request_EEPROM(&mEEPROM_info);
  ptr.resize(mEEPROM_info.bufflen);
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_set_uicc_hci_params
 *
 * Description      This will update the UICC HCI param values
 *                  to eeprom
 *
 * Parameters       value - this value will be updated to eeprom flag.
 *
 * Returns          status of the write
 *
 *****************************************************************************/
NFCSTATUS
phNxpNciHal_set_uicc_hci_params(vector<uint8_t>& ptr, uint8_t bufflen,
                                phNxpNci_EEPROM_request_type_t uiccType) {
  if (IS_CHIP_TYPE_L(sn220u)) {
    NXPLOG_NCIHAL_E("%s Not supported", __func__);
    return NFCSTATUS_SUCCESS;
  }
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  mEEPROM_info.buffer = &ptr[0];
  mEEPROM_info.bufflen = bufflen;
  mEEPROM_info.request_type = uiccType;
  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  return request_EEPROM(&mEEPROM_info);
}

/*****************************************************************************
 * Function         phNxpNciHal_send_get_cfg
 *
 * Description      This function is called to get the configurations from
 * EEPROM
 *
 * Params           cmd_get_cfg, Buffer to get the get command
 *                  cmd_len,     Length of the command
 * Returns          SUCCESS/FAILURE
 *
 *
 *****************************************************************************/
NFCSTATUS phNxpNciHal_send_get_cfg(const uint8_t* cmd_get_cfg, long cmd_len) {
  NXPLOG_NCIHAL_D("%s Enter", __func__);
  NFCSTATUS status = NFCSTATUS_FAILED;
  uint8_t retry_cnt = 0;

  if (cmd_get_cfg == NULL || cmd_len <= NCI_GET_CONFI_MIN_LEN) {
    NXPLOG_NCIHAL_E("%s invalid command..! returning... ", __func__);
    return status;
  }

  do {
    status = phNxpNciHal_send_ext_cmd(cmd_len, (uint8_t*)cmd_get_cfg);
  } while ((status != NFCSTATUS_SUCCESS) &&
           (retry_cnt++ < NXP_MAX_RETRY_COUNT));

  NXPLOG_NCIHAL_D("%s status : 0x%02X", __func__, status);
  return status;
}

/*****************************************************************************
 * Function         phNxpNciHal_configure_merge_sak
 *
 * Description      This function is called to apply iso_dep sak merge settings
 *                  as per the config option NAME_NXP_ISO_DEP_MERGE_SAK
 *
 * Params           None

 * Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
 *
 *****************************************************************************/
NFCSTATUS phNxpNciHal_configure_merge_sak() {
  if (IS_CHIP_TYPE_L(sn100u)) {
    NXPLOG_NCIHAL_D("%s : Not applicable for chipType %s", __func__,
                    pConfigFL->product[nfcFL.chipType]);
    return NFCSTATUS_SUCCESS;
  }
  long retlen = 0;
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  NXPLOG_NCIHAL_D("Performing ISODEP sak merge settings");
  uint8_t val = 0;

  if (!GetNxpNumValue(NAME_NXP_ISO_DEP_MERGE_SAK, (void*)&retlen,
                      sizeof(retlen))) {
    retlen = 0x01;
    NXPLOG_NCIHAL_D(
        "ISO_DEP_MERGE_SAK not found. default shall be enabled : 0x%02lx",
        retlen);
  }
  val = (uint8_t)retlen;
  mEEPROM_info.buffer = &val;
  mEEPROM_info.bufflen = sizeof(val);
  mEEPROM_info.request_type = EEPROM_ISODEP_MERGE_SAK;
  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  return request_EEPROM(&mEEPROM_info);
}
#if (NXP_SRD == TRUE)
/******************************************************************************
 * Function         phNxpNciHal_setSrdtimeout
 *
 * Description      This function can be used to set srd SRD Timeout.
 *
 * Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS or
 *                  NFCSTATUS_FEATURE_NOT_SUPPORTED
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_setSrdtimeout() {
  long retlen = 0;
  uint8_t* buffer = nullptr;
  long bufflen = 260;
  const int NXP_SRD_TIMEOUT_BUF_LEN = 2;
  const uint16_t TIMEOUT_MASK = 0xFFFF;
  const uint16_t MAX_TIMEOUT_VALUE = 0xFD70;
  uint16_t isValid_timeout;
  uint8_t timeout_buffer[NXP_SRD_TIMEOUT_BUF_LEN];
  NFCSTATUS status = NFCSTATUS_FEATURE_NOT_SUPPORTED;
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};

  NXPLOG_NCIHAL_D("Performing SRD Timeout settings");

  buffer = (uint8_t*)malloc(bufflen * sizeof(uint8_t));
  if (NULL == buffer) {
    return NFCSTATUS_FAILED;
  }
  memset(buffer, 0x00, bufflen);
  if (GetNxpByteArrayValue(NAME_NXP_SRD_TIMEOUT, (char*)buffer, bufflen,
                           &retlen)) {
    if (retlen == NXP_SRD_TIMEOUT_BUF_LEN) {
      isValid_timeout = ((buffer[1] << 8) & TIMEOUT_MASK);
      isValid_timeout = (isValid_timeout | buffer[0]);
      if (isValid_timeout > MAX_TIMEOUT_VALUE) {
        /*if timeout is setting more than 18hrs
         * than setting to MAX limit 0xFD70*/
        buffer[0] = 0x70;
        buffer[1] = 0xFD;
      }
      memcpy(&timeout_buffer, buffer, NXP_SRD_TIMEOUT_BUF_LEN);
      mEEPROM_info.buffer = timeout_buffer;
      mEEPROM_info.bufflen = sizeof(timeout_buffer);
      mEEPROM_info.request_type = EEPROM_SRD_TIMEOUT;
      mEEPROM_info.request_mode = SET_EEPROM_DATA;
      status = request_EEPROM(&mEEPROM_info);
    }
  }
  if (buffer != NULL) {
    free(buffer);
    buffer = NULL;
  }

  return status;
}
#endif

/******************************************************************************
 * Function         phNxpNciHal_setExtendedFieldMode
 *
 * Description      This function can be used to set nfcc extended field mode
 *
 * Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS or
 *                  NFCSTATUS_FEATURE_NOT_SUPPORTED
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_setExtendedFieldMode() {
  const uint8_t enableWithOutCMAEvents = 0x01;
  const uint8_t enableWithCMAEvents = 0x03;
  const uint8_t disableEvents = 0x00;
  uint8_t extended_field_mode = disableEvents;
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};
  NFCSTATUS status = NFCSTATUS_FEATURE_NOT_SUPPORTED;

  if (IS_CHIP_TYPE_GE(sn100u) &&
      GetNxpNumValue(NAME_NXP_EXTENDED_FIELD_DETECT_MODE, &extended_field_mode,
                     sizeof(extended_field_mode))) {
    if (extended_field_mode == enableWithOutCMAEvents ||
        extended_field_mode == enableWithCMAEvents ||
        extended_field_mode == disableEvents) {
      mEEPROM_info.buffer = &extended_field_mode;
      mEEPROM_info.bufflen = sizeof(extended_field_mode);
      mEEPROM_info.request_type = EEPROM_EXT_FIELD_DETECT_MODE;
      mEEPROM_info.request_mode = SET_EEPROM_DATA;
      status = request_EEPROM(&mEEPROM_info);
    } else {
      NXPLOG_NCIHAL_E("Invalid Extended Field Mode in config");
    }
  }
  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_configGPIOControl()
**
** Description      Helper function to configure GPIO control
**
** Parameters       gpioControl - Byte array with first two bytes are used to
**                  configure gpio for specific functionality (ex:ULPDET,
**                  GPIO LEVEL ...) and 3rd byte indicates the level of GPIO
**                  to be set.
**                  len        - Len of byte array
**
** Returns          NFCSTATUS_FAILED or NFCSTATUS_SUCCESS
*******************************************************************************/
NFCSTATUS phNxpNciHal_configGPIOControl(uint8_t gpioCtrl[], uint8_t len) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;

  if (len == 0) {
    return NFCSTATUS_INVALID_PARAMETER;
  }
  if (nfcFL.chipType <= sn100u) {
    NXPLOG_NCIHAL_D("%s : Not applicable for chipType %s", __func__,
                    pConfigFL->product[nfcFL.chipType]);
    return status;
  }
  phNxpNci_EEPROM_info_t mEEPROM_info = {.request_mode = 0};

  mEEPROM_info.request_mode = SET_EEPROM_DATA;
  mEEPROM_info.buffer = (uint8_t*)gpioCtrl;
  // First two bytes decides purpose of GPIO config
  // LIKE ULPDET, GPIO CTRL
  mEEPROM_info.bufflen = 2;
  mEEPROM_info.request_type = EEPROM_CONF_GPIO_CTRL;

  status = request_EEPROM(&mEEPROM_info);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("%s : Failed to Enable GPIO ctrl", __func__);
    return status;
  }
  if (len >= 3) {
    mEEPROM_info.request_mode = SET_EEPROM_DATA;
    mEEPROM_info.buffer = gpioCtrl + 2;
    // Last byte contains bitmask of GPIO 2/3 values.
    mEEPROM_info.bufflen = 1;
    mEEPROM_info.request_type = EEPROM_SET_GPIO_VALUE;

    status = request_EEPROM(&mEEPROM_info);
    if (status != NFCSTATUS_SUCCESS) {
      NXPLOG_NCIHAL_D("%s : Failed to set GPIO ctrl", __func__);
    }
  }
  return status;
}

/*******************************************************************************
**
** Function         phNxpNciHal_decodeGpioStatus()
**
** Description      this function decodes gpios status of the nfc pins
**
*******************************************************************************/
void phNxpNciHal_decodeGpioStatus(void) {
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  status = phNxpNciHal_GetNfcGpiosStatus(&gpios_data.gpios_status_data);
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("Get Gpio Status: Failed");
  } else {
    NXPLOG_NCIR_D("%s: NFC_IRQ = %d NFC_VEN = %d NFC_FW_DWL =%d", __func__,
                  gpios_data.platform_gpios_status.irq,
                  gpios_data.platform_gpios_status.ven,
                  gpios_data.platform_gpios_status.fw_dwl);
  }
}

/******************************************************************************
 * Function         phNxpNciHal_setDCDCConfig()
 *
 * Description      Sets DCDC On/Off
 *
 * Returns          void
 *
 *****************************************************************************/

void phNxpNciHal_setDCDCConfig(void) {
  uint8_t NXP_CONF_DCDC_ON[] = {
      0x20, 0x02, 0xDA, 0x04, 0xA0, 0x0E, 0x30, 0x7B, 0x00, 0xDE, 0xBA, 0xC4,
      0xC4, 0xC9, 0x00, 0x00, 0x00, 0xA8, 0x00, 0x37, 0xBE, 0xFF, 0xFF, 0x03,
      0x00, 0x00, 0x00, 0x28, 0x28, 0x28, 0x28, 0x0A, 0x50, 0x50, 0x00, 0x0A,
      0x64, 0x0D, 0x08, 0x04, 0x81, 0x0E, 0x20, 0x00, 0x00, 0x00, 0x00, 0x17,
      0x33, 0x14, 0x07, 0x84, 0x38, 0x20, 0x0F, 0xA0, 0xA4, 0x85, 0x14, 0x00,
      0x01, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x07, 0x00, 0x0B, 0x00, 0x0E,
      0x00, 0x12, 0x00, 0x15, 0x00, 0x19, 0x00, 0x1D, 0x00, 0x21, 0x00, 0x24,
      0x00, 0x28, 0x00, 0x2B, 0x00, 0x2E, 0x00, 0x32, 0x00, 0x35, 0x00, 0x38,
      0x00, 0x3B, 0x00, 0x3E, 0x00, 0x41, 0x00, 0x44, 0x00, 0x47, 0x00, 0x4A,
      0x00, 0x4C, 0x00, 0x4F, 0x00, 0x51, 0x00, 0x53, 0x00, 0x56, 0x00, 0x58,
      0x00, 0x5A, 0x00, 0x5C, 0x00, 0x5E, 0x00, 0x5F, 0x00, 0x61, 0x00, 0x63,
      0x00, 0x64, 0x00, 0x66, 0x00, 0x67, 0x00, 0x69, 0x00, 0x6A, 0x00, 0x6B,
      0x00, 0x6C, 0x00, 0x6D, 0x00, 0x6E, 0x00, 0x6F, 0x00, 0x70, 0x00, 0x71,
      0x00, 0x72, 0x00, 0x73, 0x00, 0x73, 0x00, 0x74, 0x00, 0x75, 0x00, 0x75,
      0x00, 0x76, 0x00, 0x76, 0x00, 0x77, 0x00, 0x77, 0x00, 0x78, 0x00, 0x78,
      0x00, 0x79, 0x00, 0x79, 0x00, 0x79, 0x00, 0x7A, 0x00, 0x7A, 0x00, 0xA0,
      0x10, 0x11, 0x01, 0x06, 0x74, 0x78, 0x00, 0x00, 0x41, 0xF4, 0xC5, 0x00,
      0xFF, 0x04, 0x00, 0x04, 0x80, 0x5E, 0x01, 0xA0, 0x11, 0x07, 0x01, 0x80,
      0x32, 0x01, 0xC8, 0x03, 0x00};

  uint8_t NXP_CONF_DCDC_OFF[] = {
      0x20, 0x02, 0xDA, 0x04, 0xA0, 0x0E, 0x30, 0x7B, 0x00, 0x9E, 0xBA, 0x01,
      0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x37, 0xBE, 0xFF, 0xFF, 0x03,
      0x00, 0x00, 0x00, 0x12, 0x12, 0x12, 0x12, 0x0A, 0x50, 0x50, 0x00, 0x0A,
      0x64, 0x0D, 0x08, 0x04, 0x81, 0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x17,
      0x33, 0x14, 0x07, 0x84, 0x38, 0x20, 0x0F, 0xA0, 0xA4, 0x85, 0x14, 0x00,
      0x01, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x07, 0x00, 0x0B, 0x00, 0x0E,
      0x00, 0x12, 0x00, 0x15, 0x00, 0x19, 0x00, 0x1D, 0x00, 0x21, 0x00, 0x24,
      0x00, 0x28, 0x00, 0x2B, 0x00, 0x2E, 0x00, 0x32, 0x00, 0x35, 0x00, 0x38,
      0x00, 0x3B, 0x00, 0x3E, 0x00, 0x41, 0x00, 0x44, 0x00, 0x47, 0x00, 0x4A,
      0x00, 0x4C, 0x00, 0x4F, 0x00, 0x51, 0x00, 0x53, 0x00, 0x56, 0x00, 0x58,
      0x00, 0x5A, 0x00, 0x5C, 0x00, 0x5E, 0x00, 0x5F, 0x00, 0x61, 0x00, 0x63,
      0x00, 0x64, 0x00, 0x66, 0x00, 0x67, 0x00, 0x69, 0x00, 0x6A, 0x00, 0x6B,
      0x00, 0x6C, 0x00, 0x6D, 0x00, 0x6E, 0x00, 0x6F, 0x00, 0x70, 0x00, 0x71,
      0x00, 0x72, 0x00, 0x73, 0x00, 0x73, 0x00, 0x74, 0x00, 0x75, 0x00, 0x75,
      0x00, 0x76, 0x00, 0x76, 0x00, 0x77, 0x00, 0x77, 0x00, 0x78, 0x00, 0x78,
      0x00, 0x79, 0x00, 0x79, 0x00, 0x79, 0x00, 0x7A, 0x00, 0x7A, 0x00, 0xA0,
      0x10, 0x11, 0x01, 0x06, 0x74, 0x78, 0x00, 0x00, 0x41, 0xF4, 0xC5, 0x00,
      0xFF, 0x04, 0x00, 0x04, 0x80, 0x5E, 0x01, 0xA0, 0x11, 0x07, 0x01, 0x80,
      0x32, 0x01, 0xC8, 0x03, 0x00};
  unsigned long enable = 0;
  NFCSTATUS status = NFCSTATUS_FAILED;
  if (!GetNxpNumValue(NAME_NXP_ENABLE_DCDC_ON, (void*)&enable,
                      sizeof(enable))) {
    NXPLOG_NCIHAL_D("NAME_NXP_ENABLE_DCDC_ON not found:");
    return;
  }
  NXPLOG_NCIHAL_D("Perform DCDC config");
  if (enable == 1) {
    // DCDC On
    status = phNxpNciHal_send_ext_cmd(sizeof(NXP_CONF_DCDC_ON),
                                      &(NXP_CONF_DCDC_ON[0]));
  } else {
    // DCDC Off
    status = phNxpNciHal_send_ext_cmd(sizeof(NXP_CONF_DCDC_OFF),
                                      &(NXP_CONF_DCDC_OFF[0]));
  }
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("SetConfig for DCDC failed");
  }
}

/*******************************************************************************
**
** Function         phNxpNciHal_isVendorSpecificCommand()
**
** Description      this function checks vendor specific command or not
**
** Returns          true if the command is vendor specific otherwise false
*******************************************************************************/
bool phNxpNciHal_isVendorSpecificCommand(uint16_t data_len,
                                         const uint8_t* p_data) {
  if (data_len > 3 && p_data[NCI_GID_INDEX] == (NCI_MT_CMD | NCI_GID_PROP) &&
      p_data[NCI_OID_INDEX] == NCI_MSG_PROP_ANDROID_OID) {
    return true;
  }
  return false;
}

/*******************************************************************************
**
** Function         phNxpNciHal_handleVendorSpecificCommand()
**
** Description      This handles the vendor specific command
**
** Returns          It returns number of bytes received.
*******************************************************************************/
int phNxpNciHal_handleVendorSpecificCommand(uint16_t data_len,
                                            const uint8_t* p_data) {
  if (data_len > 4 &&
      p_data[NCI_MSG_INDEX_FOR_FEATURE] == NCI_ANDROID_POWER_SAVING) {
    return phNxpNciHal_handleULPDetCommand(data_len, p_data);
  }

  return 0;
}

/*******************************************************************************
**
** Function         phNxpNciHal_vendorSpecificCallback()
**
** Params           oid, opcode, status
**
** Description      This function sends response to Vendor Specific commands
**
*******************************************************************************/
void phNxpNciHal_vendorSpecificCallback(int oid, int opcode, int status) {
  static phLibNfc_Message_t msg;
  nxpncihal_ctrl.p_rsp_data[0] = (uint8_t)(NCI_GID_PROP | NCI_MT_RSP);
  nxpncihal_ctrl.p_rsp_data[1] = oid;
  nxpncihal_ctrl.p_rsp_data[2] = NCI_RSP_SIZE;
  nxpncihal_ctrl.p_rsp_data[3] = opcode;
  nxpncihal_ctrl.p_rsp_data[4] = status;
  nxpncihal_ctrl.rsp_len = 5;

  msg.eMsgType = NCI_HAL_RX_MSG;
  msg.pMsgData = NULL;
  msg.Size = 0;
  phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                        (phLibNfc_Message_t*)&msg);
}
