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

#include "phNxpNciHal_IoctlOperations.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <map>
#include <set>

#include "EseAdaptation.h"
#include "NfccTransport.h"
#include "NfccTransportFactory.h"
#include "phDnldNfc_Internal.h"
#include "phNfcCommon.h"
#include "phNxpNciHal_Adaptation.h"
#include "phNxpNciHal_ULPDet.h"
#include "phNxpNciHal_ext.h"
#include "phNxpNciHal_extOperations.h"
#include "phNxpNciHal_utils.h"

using android::base::WriteStringToFile;
using namespace ::std;
using namespace ::android::base;

#define TERMINAL_LEN 5
/* HAL_NFC_STATUS_REFUSED sent to restart NFC service */
#define HAL_NFC_STATUS_RESTART HAL_NFC_STATUS_REFUSED

/****************************************************************
 * Global Variables Declaration
 ***************************************************************/
/* External global variable to get FW version from NCI response*/
extern uint32_t wFwVerRsp;
/* External global variable to get FW version from FW file*/
extern uint16_t wFwVer;
/* NCI HAL Control structure */
extern phNxpNciHal_Control_t nxpncihal_ctrl;
extern phNxpNci_getCfg_info_t* mGetCfg_info;
extern EseAdaptation* gpEseAdapt;
extern nfc_stack_callback_t* p_nfc_stack_cback_backup;
#ifndef FW_DWNLD_FLAG
extern uint8_t fw_dwnld_flag;
#endif

/* TML Context */
extern phTmlNfc_Context_t* gpphTmlNfc_Context;
extern bool nfc_debug_enabled;
extern NFCSTATUS phNxpLog_EnableDisableLogLevel(uint8_t enable);
extern phNxpNciClock_t phNxpNciClock;
extern NfcHalThreadMutex sHalFnLock;

/*******************************************************************************
 **
 ** Function:        property_get_intf()
 **
 ** Description:     Gets property value for the input property name
 **
 ** Parameters       propName:   Name of the property whichs value need to get
 **                  valueStr:   output value of the property.
 **                  defaultStr: default value of the property if value is not
 **                              there this will be set to output value.
 **
 ** Returns:         actual length of the property value
 **
 ********************************************************************************/
int property_get_intf(const char* propName, char* valueStr,
                      const char* defaultStr) {
  string propValue;
  string propValueDefault = defaultStr;
  int len = 0;

  propValue = phNxpNciHal_getSystemProperty(propName);
  if (propValue.length() > 0) {
    NXPLOG_NCIHAL_D("property_get_intf , key[%s], propValue[%s], length[%zu]",
                    propName, propValue.c_str(), propValue.length());
    len = propValue.length();
    strlcpy(valueStr, propValue.c_str(), PROPERTY_VALUE_MAX);
  } else {
    if (propValueDefault.length() > 0) {
      len = propValueDefault.length();
      strlcpy(valueStr, propValueDefault.c_str(), PROPERTY_VALUE_MAX);
    }
  }

  return len;
}

/*******************************************************************************
 **
 ** Function:        property_set_intf()
 **
 ** Description:     Sets property value for the input property name
 **
 ** Parameters       propName:   Name of the property whichs value need to set
 **                  valueStr:   value of the property.
 **
 ** Returns:        returns 0 on success, < 0 on failure
 **
 ********************************************************************************/
int property_set_intf(const char* propName, const char* valueStr) {
  NXPLOG_NCIHAL_D("property_set_intf, key[%s], value[%s]", propName, valueStr);
  if (phNxpNciHal_setSystemProperty(propName, valueStr))
    return NFCSTATUS_SUCCESS;
  else
    return NFCSTATUS_FAILED;
}

extern size_t readConfigFile(const char* fileName, uint8_t** p_data);

static string phNxpNciHal_parseBytesString(string in);
static bool phNxpNciHal_parseValueFromString(string& in);
static bool phNxpNciHal_CheckKeyNeeded(string key);
static string phNxpNciHal_UpdatePwrStateConfigs(string& config);
static bool phNxpNciHal_IsAutonmousModeSet(string config);
static string phNxpNciHal_extractConfig(string& config);
static void phNxpNciHal_getFilteredConfig(string& config);

typedef std::map<std::string, std::string> systemProperty;
systemProperty gsystemProperty = {
    {"nfc.nxp_log_level_global", ""},
    {"nfc.nxp_log_level_extns", ""},
    {"nfc.nxp_log_level_hal", ""},
    {"nfc.nxp_log_level_nci", ""},
    {"nfc.nxp_log_level_dnld", ""},
    {"nfc.nxp_log_level_tml", ""},
    {"nfc.fw.dfl", ""},
    {"nfc.fw.downloadmode_force", ""},
    {"nfc.debug_enabled", ""},
    {"nfc.product.support.ese", ""},
    {"nfc.product.support.uicc", ""},
    {"nfc.product.support.uicc2", ""},
    {"nfc.fw.rfreg_ver", ""},
    {"nfc.fw.rfreg_display_ver", ""},
    {"nfc.fw.dfl_areacode", ""},
    {"nfc.cover.cover_id", ""},
    {"nfc.cover.state", ""},
    {"ro.factory.factory_binary", ""},
};
const char default_nxp_config_path[] = "/vendor/etc/libnfc-nxp.conf";
std::set<string> gNciConfigs = {"NXP_SE_COLD_TEMP_ERROR_DELAY",
                                "NXP_SWP_RD_TAG_OP_TIMEOUT",
                                "NXP_DUAL_UICC_ENABLE",
                                "DEFAULT_AID_ROUTE",
                                "DEFAULT_MIFARE_CLT_ROUTE",
                                "DEFAULT_FELICA_CLT_ROUTE",
                                "DEFAULT_AID_PWR_STATE",
                                "DEFAULT_DESFIRE_PWR_STATE",
                                "DEFAULT_MIFARE_CLT_PWR_STATE",
                                "DEFAULT_FELICA_CLT_PWR_STATE",
                                "HOST_LISTEN_TECH_MASK",
                                "FORWARD_FUNCTIONALITY_ENABLE",
                                "DEFAULT_GSMA_PWR_STATE",
                                "NXP_DEFAULT_UICC2_SELECT",
                                "NXP_SMB_TRANSCEIVE_TIMEOUT",
                                "NXP_SMB_ERROR_RETRY",
                                "NXP_CHECK_DEFAULT_PROTO_SE_ID",
                                "NXPLOG_NCIHAL_LOGLEVEL",
                                "NXPLOG_EXTNS_LOGLEVEL",
                                "NXPLOG_TML_LOGLEVEL",
                                "NXPLOG_FWDNLD_LOGLEVEL",
                                "NXPLOG_NCIX_LOGLEVEL",
                                "NXPLOG_NCIR_LOGLEVEL",
                                "NXP_NFC_SE_TERMINAL_NUM",
                                "NXP_POLL_FOR_EFD_TIMEDELAY",
                                "NXP_NFCC_MERGE_SAK_ENABLE",
                                "NXP_STAG_TIMEOUT_CFG",
                                "DEFAULT_T4TNFCEE_AID_POWER_STATE",
                                "RF_STORAGE",
                                "FW_STORAGE",
                                "NXP_CORE_CONF",
                                "NXP_RF_FILE_VERSION_INFO",
                                "NXP_AUTONOMOUS_ENABLE",
                                "NXP_PROP_RESET_EMVCO_CMD",
                                "NFA_CONFIG_FORMAT",
                                "NXP_T4T_NFCEE_ENABLE",
                                "NXP_DISCONNECT_TAG_IN_SCRN_OFF",
                                "NXP_CE_PRIORITY_ENABLED",
                                "NXP_RDR_REQ_GUARD_TIME",
                                "OFF_HOST_SIM2_PIPE_ID",
                                "NXP_ENABLE_DISABLE_LOGS",
                                "NXP_RDR_DISABLE_ENABLE_LPCD",
                                "NXP_SUPPORT_NON_STD_CARD",
                                "NXP_GET_HW_INFO_LOG",
                                "NXP_WLC_MODE",
                                "NXP_T4T_NDEF_NFCEE_AID",
                                "NXP_NON_STD_CARD_TIMEDIFF",
                                "NXP_SRD_TIMEOUT",
                                "NXP_UICC_ETSI_SUPPORT",
                                "NXP_MINIMAL_FW_VERSION",
                                "NXP_RESTART_RF_FOR_NFCEE_RECOVERY",
                                "NXP_NFCC_RECOVERY_SUPPORT",
                                "NXP_AGC_DEBUG_ENABLE",
                                "NXP_EXTENDED_FIELD_DETECT_MODE",
                                "NXP_SE_SMB_TERMINAL_TYPE",
                                "OFF_HOST_ESIM_PIPE_ID",
                                "OFF_HOST_ESIM2_PIPE_ID"};

/****************************************************************
 * Local Functions
 ***************************************************************/

/******************************************************************************
 ** Function         phNxpNciHal_ioctlIf
 **
 ** Description      This function shall be called from HAL when libnfc-nci
 **                  calls phNxpNciHal_ioctl() to perform any IOCTL operation
 **
 ** Returns          return 0 on success and -1 on fail,
 ******************************************************************************/
int phNxpNciHal_ioctlIf(long arg, void* p_data) {
  NXPLOG_NCIHAL_D("%s : enter - arg = %ld", __func__, arg);
  ese_nxp_IoctlInOutData_t* pInpOutData = (ese_nxp_IoctlInOutData_t*)p_data;
  int ret = -1;

  switch (arg) {
    case HAL_ESE_IOCTL_NFC_JCOP_DWNLD:
      if (pInpOutData == NULL) {
        NXPLOG_NCIHAL_E("%s : received invalid param", __func__);
        break;
      }

      if (gpEseAdapt == NULL) {
        gpEseAdapt = &EseAdaptation::GetInstance();
        if (gpEseAdapt == NULL) {
          NXPLOG_NCIHAL_E("%s :invalid gpEseAdapt param", __func__);
          break;
        }
        gpEseAdapt->Initialize();
      }

      NXPLOG_NCIHAL_D("HAL_ESE_IOCTL_NFC_JCOP_DWNLD Enter value is %d: \n",
                      pInpOutData->inp.data.nxpCmd.p_cmd[0]);

      gpEseAdapt->HalIoctl(HAL_ESE_IOCTL_NFC_JCOP_DWNLD, pInpOutData);
      ret = 0;
      break;
    default:
      NXPLOG_NCIHAL_E("%s : Wrong arg = %ld", __func__, arg);
      break;
  }
  NXPLOG_NCIHAL_D("%s : exit - ret = %d", __func__, ret);
  return ret;
}

/*******************************************************************************
 **
 ** Function         phNxpNciHal_getSystemProperty
 **
 ** Description      It shall be used to get property value of the given Key
 **
 ** Parameters       string key
 **
 ** Returns          If Key is found, returns the respective property values
 **                  else returns the null/empty string
 *******************************************************************************/
string phNxpNciHal_getSystemProperty(string key) {
  string propValue;
  std::map<std::string, std::string>::iterator prop;

  if (key == "libnfc-nxp.conf") {
    return phNxpNciHal_getNxpConfigIf();
  } else {
    prop = gsystemProperty.find(key);
    if (prop != gsystemProperty.end()) {
      propValue = prop->second;
    } else {
      /* else Pass a null string */
    }
  }
  return propValue;
}
/*******************************************************************************
 **
 ** Function         phNxpNciHal_setSystemProperty
 **
 ** Description      It shall be used to save/change value to system property
 **                  based on provided key.
 **
 ** Parameters       string key, string value
 **
 ** Returns          true if success, false if fail
 *******************************************************************************/
bool phNxpNciHal_setSystemProperty(string key, string value) {
  bool stat = true;
  if (strcmp(key.c_str(), "nfc.debug_enabled") != 0)
    NXPLOG_NCIHAL_D("%s : Enter Key = %s, value = %s", __func__, key.c_str(),
                    value.c_str());

  unsigned tmp = 0;
  if (strcmp(key.c_str(), "nfc.debug_enabled") == 0) {
    if (ParseUint(value.c_str(), &tmp)) {
      if (phNxpLog_EnableDisableLogLevel((uint8_t)tmp) != NFCSTATUS_SUCCESS) {
        stat = false;
      }
    } else {
      NXPLOG_NCIHAL_W(
          "%s : Failed to parse the string to uint. "
          "nfc.debug_enabled string : %s",
          __func__, value.c_str());
    }
  } else if (strcmp(key.c_str(), "nfc.cover.state") == 0) {
    unsigned cid, cstate;
    string strtmp;
    if (ParseUint(value.c_str(), &cstate)) {
      strtmp = phNxpNciHal_getSystemProperty("nfc.cover.cover_id");
      if (ParseUint(strtmp.c_str(), &cid)) {
        if (fpPropConfCover != NULL) {
          stat = (fpPropConfCover(cstate, cid) == NFCSTATUS_SUCCESS) ? true
                                                                     : false;
        }
      } else {
        NXPLOG_NCIHAL_W(
            "%s : Failed to parse the string to uint. "
            "nfc.cover.cover_id string : %s",
            __func__, value.c_str());
      }
    } else {
      NXPLOG_NCIHAL_W(
          "%s : Failed to parse the string to uint. "
          "nfc.cover.state string : %s",
          __func__, value.c_str());
    }
  } else if (strcmp(key.c_str(), "nfc.cmd_timeout") == 0) {
    NXPLOG_NCIHAL_E("%s : nci_timeout, sem post", __func__);
    sem_post(&(nxpncihal_ctrl.syncSpiNfc));
  } else if (strcmp(key.c_str(), "nfc.ulpdet") == 0) {
    NXPLOG_NCIHAL_E("%s : set ulpdet", __func__);
    if (!phNxpNciHal_isULPDetSupported()) return false;
    bool flag = false;
    if (strcmp(value.c_str(), "1") == 0) {
      flag = true;
    }
    phNxpNciHal_setULPDetFlag(flag);
  }
  gsystemProperty[key] = std::move(value);
  return stat;
}

/*******************************************************************************
**
** Function         phNxpNciHal_getNxpConfig
**
** Description      It shall be used to read config values from the
*libnfc-nxp.conf
**
** Parameters       nxpConfigs config
**
** Returns          void
*******************************************************************************/
string phNxpNciHal_getNxpConfigIf() {
  std::string config;
  uint8_t* p_config = nullptr;
  size_t config_size = readConfigFile(default_nxp_config_path, &p_config);
  if (config_size) {
    config.assign((char*)p_config, config_size);
    free(p_config);
    phNxpNciHal_getFilteredConfig(config);
  }
  return config;
}

/*******************************************************************************
**
** Function         phNxpNciHal_getFilteredConfig
**
** Description      It reads only configs needed for libnfc from
*                   libnfc-nxp.conf
**
** Parameters       string config
**
** Returns          void
*******************************************************************************/
static void phNxpNciHal_getFilteredConfig(string& config) {
  config = phNxpNciHal_extractConfig(config);
  if (phNxpNciHal_IsAutonmousModeSet(config)) {
    config = phNxpNciHal_UpdatePwrStateConfigs(config);
  }
}

/*******************************************************************************
**
** Function         phNxpNciHal_extractConfig
**
** Description      It parses complete config file and extracts only
*                   enabled options ignores comments etc.
**
** Parameters       string config
**
** Returns          Resultant string
*******************************************************************************/
static string phNxpNciHal_extractConfig(string& config) {
  stringstream ss(config);
  string line;
  string result;
  bool apduGate = false;
  while (getline(ss, line)) {
    line = Trim(line);
    if (line.empty()) continue;
    if (line.at(0) == '#') continue;
    if (line.at(0) == 0) continue;

    auto search = line.find('=');
    if (search == string::npos) continue;

    string key(Trim(line.substr(0, search)));
    if (!phNxpNciHal_CheckKeyNeeded(key)) continue;
    if (key == "NXP_NFC_SE_TERMINAL_NUM" && !apduGate) {
      line = "NXP_SE_APDU_GATE_SUPPORT=0x01\n";
      result += line;
      apduGate = true;
      continue;
    }
    string value_string(Trim(line.substr(search + 1, string::npos)));

    if (value_string[0] == '{' &&
        value_string[value_string.length() - 1] != '}') {
      string line_append;

      do {
        getline(ss, line_append);
        if (line_append.empty()) break;
        if (line_append.at(0) == '#') break;
        if (line_append.at(0) == 0) break;
        line_append = Trim(line_append);
        value_string.append(line_append);
      } while (line_append[line_append.length() - 1] != '}');
    }

    if (!phNxpNciHal_parseValueFromString(value_string)) continue;

    line = key + "=" + value_string + "\n";
    result += line;
    if (key == "NXP_GET_HW_INFO_LOG" &&
        (value_string == "1" || value_string == "0x01")) {
      if (!apduGate) {
        line = "NXP_SE_APDU_GATE_SUPPORT=0x01\n";
        result += line;
        apduGate = true;
      }
    }
  }

  return result;
}

/*******************************************************************************
**
** Function         phNxpNciHal_IsAutonmousModeSet
**
** Description      It check whether autonomous mode is enabled
*                   in config file
**
** Parameters       string config
**
** Returns          boolean(TRUE/FALSE)
*******************************************************************************/
static bool phNxpNciHal_IsAutonmousModeSet(string config) {
  stringstream ss(config);
  string line;
  unsigned tmp = 0;
  while (getline(ss, line)) {
    auto search = line.find('=');
    if (search == string::npos) continue;

    string key(Trim(line.substr(0, search)));
    if (key == "NXP_AUTONOMOUS_ENABLE") {
      string value(Trim(line.substr(search + 1, string::npos)));
      if (ParseUint(value.c_str(), &tmp)) {
        if (tmp == 1) {
          return true;
        } else {
          NXPLOG_NCIHAL_D("Autonomous flag disabled");
          return false;
        }
      }
    } else {
      continue;
    }
  }
  NXPLOG_NCIHAL_D("Autonomous flag disabled");
  return false;
}

/*******************************************************************************
**
** Function         phNxpNciHal_UpdatePwrStateConfigs
**
** Description      Updates default pwr state accordingly if autonomous mode
*                   is enabled
**
** Parameters       string config
**
** Returns          Resultant string
*******************************************************************************/
static string phNxpNciHal_UpdatePwrStateConfigs(string& config) {
  stringstream ss(config);
  string line;
  string result;
  unsigned tmp = 0;
  while (getline(ss, line)) {
    auto search = line.find('=');
    if (search == string::npos) continue;

    string key(Trim(line.substr(0, search)));
    if ((key == "DEFAULT_AID_PWR_STATE" || key == "DEFAULT_DESFIRE_PWR_STATE" ||
         key == "DEFAULT_MIFARE_CLT_PWR_STATE" ||
         key == "DEFAULT_FELICA_CLT_PWR_STATE")) {
      string value(Trim(line.substr(search + 1, string::npos)));
      if (ParseUint(value.c_str(), &tmp)) {
        tmp = phNxpNciHal_updateAutonomousPwrState(tmp);
        value = to_string(tmp);
        line = key + "=" + value + "\n";
        result += line;
      }
    } else {
      result += (line + "\n");
      continue;
    }
  }
  return result;
}

/*******************************************************************************
**
** Function         phNxpNciHal_CheckKeyNeeded
**
** Description      Check if the config needed for libnfc as per gNciConfigs
*                   list
**
** Parameters       string config
**
** Returns          bool(true/false)
*******************************************************************************/
static bool phNxpNciHal_CheckKeyNeeded(string key) {
  return ((gNciConfigs.find(key) != gNciConfigs.end()) ? true : false);
}

/*******************************************************************************
**
** Function         phNxpNciHal_parseValueFromString
**
** Description      Parse value determine data type of config option
**
** Parameters       string config
**
** Returns          bool(true/false)
*******************************************************************************/
static bool phNxpNciHal_parseValueFromString(string& in) {
  unsigned tmp = 0;
  bool stat = false;
  if (in.length() >= 1) {
    switch (in[0]) {
      case '"':
        if (in[in.length() - 1] == '"' && in.length() > 2) stat = true;
        break;
      case '{':
        if (in[in.length() - 1] == '}' && in.length() >= 3) {
          in = phNxpNciHal_parseBytesString(in);
          stat = true;
        }
        break;
      default:
        if (ParseUint(in.c_str(), &tmp)) stat = true;
        break;
    }
  } else {
    NXPLOG_NCIHAL_E("%s : Invalid config string ", __func__);
  }
  return stat;
}

/*******************************************************************************
**
** Function         phNxpNciHal_parseBytesString
**
** Description      Parse bytes from string
**
** Parameters       string config
**
** Returns          Resultant string
*******************************************************************************/
static string phNxpNciHal_parseBytesString(string in) {
  size_t pos;
  in.erase(remove(in.begin(), in.end(), ' '), in.end());
  pos = in.find(",");
  while (pos != string::npos) {
    in = in.replace(pos, 1, ":");
    pos = in.find(",", pos);
  }
  return in;
}

/*******************************************************************************
**
** Function         phNxpNciHal_resetEse
**
** Description      It shall be used to reset eSE by proprietary command.
**
** Parameters
**
** Returns          status of eSE reset response
*******************************************************************************/
NFCSTATUS phNxpNciHal_resetEse(uint64_t resetType) {
  NFCSTATUS status = NFCSTATUS_FAILED;

  {
    NfcHalAutoThreadMutex a(sHalFnLock);
    if (nxpncihal_ctrl.halStatus == HAL_STATUS_CLOSE) {
      if (NFCSTATUS_SUCCESS != phNxpNciHal_MinOpen()) {
        return NFCSTATUS_FAILED;
      }
    }
  }

  CONCURRENCY_LOCK();
  status = gpTransportObj->EseReset(gpphTmlNfc_Context->pDevHandle,
                                    (EseResetType)resetType);
  CONCURRENCY_UNLOCK();
  if (status != NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_E("EsePowerCycle failed");
  }

  if (nxpncihal_ctrl.halStatus == HAL_STATUS_MIN_OPEN) {
    phNxpNciHal_close(false);
  }

  return status;
}

/*******************************************************************************
 **
 ** Function:        phNxpNciHal_GetNfcGpiosStatus()
 **
 ** Description:     Sets the gpios status flag byte
 **
 ** Parameters       gpiostatus: flag byte
 **
 ** Returns:        returns 0 on success, < 0 on failure
 **
 ********************************************************************************/
NFCSTATUS phNxpNciHal_GetNfcGpiosStatus(uint32_t* gpiosstatus) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  status = gpTransportObj->NfcGetGpioStatus(gpphTmlNfc_Context->pDevHandle,
                                            gpiosstatus);
  return status;
}
/******************************************************************************
 * Function         phNxpNciHal_setNxpTransitConfig
 *
 * Description      This function overwrite libnfc-nxpTransit.conf file
 *                  with transitConfValue.
 *
 * Returns          bool.
 *
 ******************************************************************************/
bool phNxpNciHal_setNxpTransitConfig(char* transitConfValue) {
  bool status = true;
  NXPLOG_NCIHAL_D("%s : Enter", __func__);
  std::string transitConfFileName = "/data/vendor/nfc/libnfc-nxpTransit.conf";
  long transitConfValueLen = strlen(transitConfValue) + 1;

  if (transitConfValueLen > 1) {
    if (!WriteStringToFile(transitConfValue, transitConfFileName)) {
      NXPLOG_NCIHAL_E("WriteStringToFile: Failed");
      status = false;
    }
  } else {
    if (!WriteStringToFile("", transitConfFileName)) {
      NXPLOG_NCIHAL_E("WriteStringToFile: Failed");
      status = false;
    }
    if (remove(transitConfFileName.c_str())) {
      NXPLOG_NCIHAL_E("Unable to remove file");
      status = false;
    }
  }
  NXPLOG_NCIHAL_D("%s : Exit", __func__);
  return status;
}

/******************************************************************************
** Function         phNxpNciHal_Abort
**
** Description      This function shall be used to trigger the abort in libnfc
**
** Parameters       None
**
** Returns          bool.
**
*******************************************************************************/
bool phNxpNciHal_Abort() {
  bool ret = true;

  NXPLOG_NCIHAL_D("phNxpNciHal_Abort aborting. \n");
  /* When JCOP download is triggered phNxpNciHal_open is blocked, in this case
     only we need to abort the libnfc , this can be done only by check the
     p_nfc_stack_cback_backup pointer which is assigned before the JCOP
     download.*/
  if (p_nfc_stack_cback_backup != NULL) {
    (*p_nfc_stack_cback_backup)(HAL_NFC_OPEN_CPLT_EVT, HAL_NFC_STATUS_RESTART);
  } else {
    ret = false;
    NXPLOG_NCIHAL_D("phNxpNciHal_Abort not triggered\n");
  }
  return ret;
}

/*******************************************************************************
 **
 ** Function:        phNxpNciHal_CheckFwRegFlashRequired()
 **
 ** Description:     Updates FW and Reg configurations if required
 **
 ** Returns:         status
 **
 ********************************************************************************/
int phNxpNciHal_CheckFwRegFlashRequired(uint8_t* fw_update_req,
                                        uint8_t* rf_update_req,
                                        uint8_t skipEEPROMRead) {
  NXPLOG_NCIHAL_D("phNxpNciHal_CheckFwRegFlashRequired() : enter");
  int status = NFCSTATUS_OK;
  long option;
  if (fpRegRfFwDndl != NULL) {
    status = fpRegRfFwDndl(fw_update_req, rf_update_req, skipEEPROMRead);
  } else {
    status = phDnldNfc_InitImgInfo();
    NXPLOG_NCIHAL_D("FW version from the binary(.so/bin) = 0x%x", wFwVer);
    NXPLOG_NCIHAL_D("FW version found on the device = 0x%x", wFwVerRsp);

    if (!GetNxpNumValue(NAME_NXP_FLASH_CONFIG, &option,
                        sizeof(unsigned long))) {
      NXPLOG_NCIHAL_D("Flash option not found; giving default value");
      option = 1;
    }
    switch (option) {
      case FLASH_UPPER_VERSION:
        wFwUpdateReq = (utf8_t)wFwVer > (utf8_t)wFwVerRsp ? true : false;
        break;
      case FLASH_DIFFERENT_VERSION:
        wFwUpdateReq = ((wFwVerRsp & 0x0000FFFF) != wFwVer) ? true : false;
        break;
      case FLASH_ALWAYS:
        wFwUpdateReq = true;
        break;
      default:
        NXPLOG_NCIHAL_D("Invalid flash option selected");
        status = NFCSTATUS_INVALID_PARAMETER;
        break;
    }
  }
  *fw_update_req = wFwUpdateReq;

  if (false == wFwUpdateReq) {
    NXPLOG_NCIHAL_D("FW update not required");
    phDnldNfc_ReSetHwDevHandle();
  } else {
    property_set("nfc.fw.downloadmode_force", "1");
  }

  NXPLOG_NCIHAL_D(
      "phNxpNciHal_CheckFwRegFlashRequired() : exit - status = %x "
      "wFwUpdateReq=%u, wRfUpdateReq=%u",
      status, *fw_update_req, *rf_update_req);
  return status;
}

/******************************************************************************
 * Function         phNxpNciHal_txNfccClockSetCmd
 *
 * Description      This function is called after successful download
 *                  to apply the clock setting provided in config file
 *
 * Returns          void.
 *
 ******************************************************************************/
void phNxpNciHal_txNfccClockSetCmd(void) {
  NFCSTATUS status = NFCSTATUS_FAILED;

  uint8_t set_clock_cmd[] = {0x20, 0x02, 0x05, 0x01, 0xA0, 0x03, 0x01, 0x08};
  uint8_t setClkCmdLen = sizeof(set_clock_cmd);
  unsigned long clockSource = 0;
  unsigned long frequency = 0;
  uint32_t pllSetRetryCount = 3, dpllSetRetryCount = 3,
           setClockCmdWriteRetryCnt = 0;
  uint8_t* pCmd4PllSetting = NULL;
  uint8_t* pCmd4DpllSetting = NULL;
  uint32_t pllCmdLen = 0, dpllCmdLen = 0;
  int srcCfgFound = 0, freqCfgFound = 0;

  srcCfgFound = (GetNxpNumValue(NAME_NXP_SYS_CLK_SRC_SEL, &clockSource,
                                sizeof(clockSource)) > 0);

  freqCfgFound = (GetNxpNumValue(NAME_NXP_SYS_CLK_FREQ_SEL, &frequency,
                                 sizeof(frequency)) > 0);

  NXPLOG_NCIHAL_D("%s : clock source = %lu, frequency = %lu", __FUNCTION__,
                  clockSource, frequency);

  if (srcCfgFound && freqCfgFound && (clockSource == CLK_SRC_PLL)) {
    phNxpNciClock.isClockSet = TRUE;

    switch (frequency) {
      case CLK_FREQ_13MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_13MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_13MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_13MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_13MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_13MHZ);
        break;
      }
      case CLK_FREQ_19_2MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_19_2MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_19_2MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_19_2MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_19_2MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_19_2MHZ);
        break;
      }
      case CLK_FREQ_24MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_24MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_24MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_24MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_24MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_24MHZ);
        break;
      }
      case CLK_FREQ_26MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_26MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_26MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_26MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_26MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_26MHZ);
        break;
      }
      case CLK_FREQ_32MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_32MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_32MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_32MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_32MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_32MHZ);
        break;
      }
      case CLK_FREQ_38_4MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_38_4MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_38_4MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_38_4MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_38_4MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_38_4MHZ);
        break;
      }
      case CLK_FREQ_48MHZ: {
        NXPLOG_NCIHAL_D("PLL setting for CLK_FREQ_48MHZ");
        pCmd4PllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_PLL_48MHZ;
        pllCmdLen = sizeof(PN557_SET_CONFIG_CMD_PLL_48MHZ);
        pCmd4DpllSetting = (uint8_t*)PN557_SET_CONFIG_CMD_DPLL_48MHZ;
        dpllCmdLen = sizeof(PN557_SET_CONFIG_CMD_DPLL_48MHZ);
        break;
      }
      default:
        phNxpNciClock.isClockSet = FALSE;
        NXPLOG_NCIHAL_E("ERROR: Invalid clock frequency!!");
        return;
    }
  }
  switch (clockSource) {
    case CLK_SRC_PLL: {
      set_clock_cmd[setClkCmdLen - 1] = 0x00;
      while (status != NFCSTATUS_SUCCESS &&
             setClockCmdWriteRetryCnt++ < MAX_RETRY_COUNT)
        status = phNxpNciHal_send_ext_cmd(setClkCmdLen, set_clock_cmd);

      status = NFCSTATUS_FAILED;

      while (status != NFCSTATUS_SUCCESS && pllSetRetryCount-- > 0)
        status = phNxpNciHal_send_ext_cmd(pllCmdLen, pCmd4PllSetting);

      status = NFCSTATUS_FAILED;

      while (status != NFCSTATUS_SUCCESS && dpllSetRetryCount-- > 0)
        status = phNxpNciHal_send_ext_cmd(dpllCmdLen, pCmd4DpllSetting);

      break;
    }
    case CLK_SRC_XTAL: {
      status = phNxpNciHal_send_ext_cmd(setClkCmdLen, set_clock_cmd);
      if (status != NFCSTATUS_SUCCESS) {
        NXPLOG_NCIHAL_E("XTAL clock setting failed !!");
      }
      break;
    }
    default:
      NXPLOG_NCIHAL_E("Wrong clock source. Don't apply any modification");
      return;
  }
  phNxpNciClock.isClockSet = FALSE;
  if (status == NFCSTATUS_SUCCESS &&
      phNxpNciClock.p_rx_data[3] == NFCSTATUS_SUCCESS) {
    NXPLOG_NCIHAL_D("PLL and DPLL settings applied successfully");
  }
  return;
}
