/******************************************************************************
 *
 *  Copyright 2015-2018,2020-2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
#define LOG_TAG "NxpHal"
#include "NxpNfcCapability.h"
#include <phNxpLog.h>

capability* capability::instance = NULL;
tNFC_chipType capability::chipType = pn81T;
tNfc_featureList nfcFL;

capability::capability() {}

capability* capability::getInstance() {
  if (NULL == instance) {
    instance = new capability();
  }
  return instance;
}

tNFC_chipType capability::processChipType(uint8_t* msg, uint16_t msg_len) {
  if ((msg != NULL) && (msg_len != 0)) {
    if ((msg_len > 2) && msg[0] == NCI_CMD_RSP_SUCCESS_SW1 &&
        msg[1] == NCI_CMD_RSP_SUCCESS_SW2) {
      chipType = determineChipTypeFromNciRsp(msg, msg_len);
    } else if (msg[0] == FW_DL_RSP_FIRST_BYTE) {
      chipType = determineChipTypeFromDLRsp(msg, msg_len);
    } else {
      ALOGD("%s Wrong msg_len. Setting Default ChipType pn80T", __func__);
      chipType = pn81T;
    }
  }
  ALOGD("%s Product : %s", __func__, product[chipType]);
  return chipType;
}

tNFC_chipType capability::determineChipTypeFromNciRsp(uint8_t* msg,
                                                      uint16_t msg_len) {
  tNFC_chipType chip_type = pn81T;
  if ((msg == NULL) || (msg_len < 3)) {
    ALOGD("%s Wrong msg_len. Setting Default ChipType pn80T", __func__);
    return chip_type;
  }
  if (GET_FW_ROM_VERSION_NCI_RESP(msg, msg_len) ==
          FW_MOBILE_ROM_VERSION_PN557 &&
      (GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
           FW_MOBILE_MAJOR_NUMBER_PN557 ||
       GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
           FW_MOBILE_MAJOR_NUMBER_PN557_V2))
    chip_type = pn557;
  else if (GET_FW_ROM_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_ROM_VERSION_PN553 &&
           GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_MAJOR_NUMBER_PN553)
    chip_type = pn553;
  else if (GET_FW_ROM_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_ROM_VERSION_SN100U &&
           GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_MAJOR_NUMBER_SN100U)
    chip_type = sn100u;
  /* PN560 & SN220 have same rom & fw major version but chip type is different*/
  else if (GET_FW_ROM_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_ROM_VERSION_SN220U &&
           GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
               FW_MOBILE_MAJOR_NUMBER_SN220U) {
    if ((msg_len >= 4) &&
        (GET_HW_VERSION_NCI_RESP(msg, msg_len) == HW_PN560_V1 ||
         GET_HW_VERSION_NCI_RESP(msg, msg_len) == HW_PN560_V2)) {
      chip_type = pn560;
    } else {
      chip_type = sn220u;
    }
  } else if (GET_FW_ROM_VERSION_NCI_RESP(msg, msg_len) ==
                 FW_MOBILE_ROM_VERSION_SN300U &&
             GET_FW_MAJOR_VERSION_NCI_RESP(msg, msg_len) ==
                 FW_MOBILE_MAJOR_NUMBER_SN300U) {
    chip_type = sn300u;
  }
  return chip_type;
}

tNFC_chipType capability::determineChipTypeFromDLRsp(uint8_t* msg,
                                                     uint16_t msg_len) {
  tNFC_chipType chip_type = pn81T;
  if ((msg == NULL) || (msg_len < 3)) {
    ALOGD("%s Wrong msg_len. Setting Default ChipType pn80T", __func__);
    return chip_type;
  }
  /* PN560 & SN220 have same rom & fw major version but chip type is different*/
  if (msg[offsetFwRomCodeVersion] == FW_MOBILE_ROM_VERSION_SN220U &&
      msg[offsetFwMajorVersion] == FW_MOBILE_MAJOR_NUMBER_SN220U) {
    if (msg[offsetDlRspChipType] == HW_PN560_V1 ||
        msg[offsetDlRspChipType] == HW_PN560_V2) {
      chip_type = pn560;
    } else {
      chip_type = sn220u;
    }
  } else if (msg[offsetFwRomCodeVersion] == FW_MOBILE_ROM_VERSION_SN100U &&
             msg[offsetFwMajorVersion] == FW_MOBILE_MAJOR_NUMBER_SN100U)
    chip_type = sn100u;
  else if (msg[offsetFwRomCodeVersion] == FW_MOBILE_ROM_VERSION_PN557 &&
           (msg[offsetFwMajorVersion_pn557] == FW_MOBILE_ROM_VERSION_PN557 ||
            msg[offsetFwMajorVersion_pn557] == FW_MOBILE_MAJOR_NUMBER_PN557_V2))
    chip_type = pn557;
  else if (msg[offsetFwRomCodeVersion] == FW_MOBILE_ROM_VERSION_SN300U &&
           (msg[offsetFwMajorVersion] == FW_MOBILE_MAJOR_NUMBER_SN300U ||
            msg[offsetDlRspChipType] == HW_SN300U)) {
    chip_type = sn300u;
  }
  return chip_type;
}

uint32_t capability::getFWVersionInfo(uint8_t* msg, uint16_t msg_len) {
  uint32_t versionInfo = 0;
  if ((msg != NULL) && (msg_len != 0)) {
    if (msg[0] == 0x00) {
      versionInfo = msg[offsetFwRomCodeVersion] << 16;
      versionInfo |= msg[offsetFwMajorVersion] << 8;
      versionInfo |= msg[offsetFwMinorVersion];
    }
  }
  return versionInfo;
}
