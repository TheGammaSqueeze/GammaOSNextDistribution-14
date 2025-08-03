/******************************************************************************
 *
 *  Copyright 2020-2023 NXP
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
#include "OsuHalExtn.h"

#include <ese_config.h>

#define LOG_TAG "OsuHalExtn"
#include <log/log.h>

#define DEFAULT_MAX_WTX_COUNT 60

const static std::vector<uint8_t> OSU_AID = {0x4F, 0x70, 0x80, 0x13, 0x04,
                                             0xDE, 0xAD, 0xBE, 0xEF};
const static uint8_t defaultSelectAid[] = {0x00, 0xA4, 0x04, 0x00, 0x00};

/*******************************************************************************
**
** Function:    OsuHalExtn::isOsuMode
**
** Description: Check, update and return current mode for below SE HAL API
**              requests openBasicChannel() and transmit()
**
** Returns:     OsuApduMode(NON_OSU_MODE, OSU_PROP_MODE, OSU_RST_MODE,
**              OSU_BLOCKED_MODE, OSU_GP_MODE)
**
*******************************************************************************/
OsuHalExtn::OsuApduMode OsuHalExtn::isOsuMode(const std::vector<uint8_t>& evt,
                                              uint8_t type,
                                              phNxpEse_data* pCmdData) {
  OsuApduMode osuSubState = (isAppOSUMode ? OSU_PROP_MODE : NON_OSU_MODE);

  switch (type) {
    case OPENBASIC:
      /*
       * update & return OSU_PROP_MODE if OpenBasicChannel AID request matches
       * OSU_AID
       */
      if (evt.size() && !memcmp(&evt[0], &OSU_AID[0], OSU_AID.size())) {
        isAppOSUMode = true;
        osuSubState = OSU_PROP_MODE;
        ALOGD("Dedicated mode is set !!!!!!!!!!!!!!!!!");
      } else if (isOsuMode()) {
        // OpenBasic channel requests with non OSU_AID are not allowed/blocked
        osuSubState = OSU_BLOCKED_MODE;
        ALOGE("Non OSU AID Not allowed");
      }
      break;
    case TRANSMIT:
      // Validate input data before processing
      if (pCmdData != NULL && pCmdData->p_data != NULL) {
        memcpy(pCmdData->p_data, evt.data(), evt.size());
        if (isOsuMode()) {
          /*
           * Process transmit request(unwrap APDU, proprietary actions) in OSU
           * mode
           */
          osuSubState = checkTransmit(pCmdData->p_data, &pCmdData->len, evt);
        } else {
          pCmdData->len = evt.size();
          osuSubState = NON_OSU_MODE;
        }
      } else {
        if (pCmdData != NULL) pCmdData->len = evt.size();
        osuSubState = NON_OSU_MODE;
      }
      break;
  }
  return osuSubState;
}

/*******************************************************************************
**
** Function:    OsuHalExtn::isOsuMode
**
** Description: Check, update and return current mode for below SE HAL API
**              requests init(), openLogicalChannel() and closeChannel()
**
** Returns:     true(OSU)/false(normal)
**
*******************************************************************************/
bool OsuHalExtn::isOsuMode(uint8_t type, uint8_t channel) {
  switch (type) {
    case INIT:
      checkAndUpdateOsuMode();
      break;
    case OPENLOGICAL:
      // No action, only return current mode
      break;
    case GETATR:
      // No action, only return current mode
      break;
    case CLOSE:
      /*
       * If in OSU mode close basic channel is called
       * clear osu APP and update JCOP mode
       */
      if (channel == ISO7816_BASIC_CHANNEL && isOsuMode()) {
        if (phNxpEse_doResetProtection(false) != ESESTATUS_SUCCESS) {
          ALOGE("Disable Reset Protection Failed");
        }
        phNxpEse_setWtxCountLimit(RESET_APP_WTX_COUNT);
        isAppOSUMode = false;
        ALOGD("Setting to normal mode!!!");
      }
      break;
  }
  return isOsuMode();
}

/*******************************************************************************
**
** Function:    OsuHalExtn::checkAndUpdateOsuMode
**
** Description: Check and update current JCOP mode OSU/Normal
**
** Returns:     None
**
*******************************************************************************/
void OsuHalExtn::checkAndUpdateOsuMode() {
  isJcopOSUMode = (phNxpEse_GetOsMode() == OSU_MODE);
}

/*******************************************************************************
**
** Function:    OsuHalExtn::getInstance
**
** Description: get class singleton object
**
** Returns:     OsuHalExtn
**
*******************************************************************************/
OsuHalExtn& OsuHalExtn::getInstance() {
  static OsuHalExtn manager;
  return manager;
}

/*******************************************************************************
**
** Function:    OsuHalExtn::OsuHalExtn()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
OsuHalExtn::OsuHalExtn() noexcept {
  isAppOSUMode = false;
  isJcopOSUMode = false;
}

/*******************************************************************************
**
** Function:    OsuHalExtn::~OsuHalExtn()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
OsuHalExtn::~OsuHalExtn() {}

/*******************************************************************************
**
** Function:    OsuHalExtn::isOsuMode()
**
** Description: Returns current JCOP mode
**
** Returns:     true(OSU)/false(Normal)
**
*******************************************************************************/
bool OsuHalExtn::isOsuMode() { return (isAppOSUMode || isJcopOSUMode); }

/*******************************************************************************
**
** Function:    OsuHalExtn::checkTransmit
**
** Description: Process transmit request in OSU mode
**              1) Unwrap proprietary APDU's to native commands
**              2) Block unexpected command request in OSU Mode
**              3) Perform hard reset on receiving proprietary reset APDU
**
** Returns:     OsuApduMode
**
*******************************************************************************/
OsuHalExtn::OsuApduMode OsuHalExtn::checkTransmit(
    uint8_t* input, uint32_t* outLength, const std::vector<uint8_t>& data) {
  OsuHalExtn::OsuApduMode halMode = NON_OSU_MODE;
  size_t length = data.size();

  // Validate input buffer processing
  if (input == NULL) {
    return halMode;
  }
  /*
   * 1) Transmit request on logical channels(ISO7816_CLA_CHN_MASK)shall be
   *    blocked in OSU mode
   * 2) Empty/Default GP card manager select from OMAPI shall
   *    be blocked in OSU Mode
   */
  if (((*input & ISO7816_CLA_CHN_MASK) != ISO7816_BASIC_CHANNEL) ||
      (isJcopOSUMode && (length == ISO7816_SHORT_APDU_HEADER &&
                         !memcmp(input, defaultSelectAid, length)))) {
    phNxpEse_free(input);
    input = nullptr;
    halMode = OSU_BLOCKED_MODE;
  } else if ((*input == OSU_PROP_CLA) &&
             (*(input + ISO7816_INS_OFFSET) == OSU_PROP_INS) &&
             (*(input + ISO7816_P1_OFFSET) != OSU_PROP_RST_P1)) {
    /*
     * 1) Unwrap GP command to native commands
     * 2) Check APDU type short/extended before unwrapping
     */
    ALOGD("checkTransmit in OSU_PROP_MODE");
    if (*(input + ISO7816_LC_OFFSET) != 0) {
      if (length > ISO7816_SHORT_APDU_HEADER) {
        *outLength = length - ISO7816_SHORT_APDU_HEADER;
        std::copy(data.begin() + ISO7816_SHORT_APDU_HEADER, data.end(), input);
      } else {
        *outLength = 0;
        ALOGE("checkTransmit input data length is incorrect");
      }
    } else {
      if (length > ISO7816_EXTENDED_APDU_HEADER) {
        *outLength = length - ISO7816_EXTENDED_APDU_HEADER;
        std::copy(data.begin() + ISO7816_EXTENDED_APDU_HEADER, data.end(),
                  input);
      } else {
        *outLength = 0;
        ALOGE("checkTransmit input data length is incorrect");
      }
    }
    halMode = OSU_PROP_MODE;
  } else if ((*input == OSU_PROP_CLA) &&
             (*(input + ISO7816_INS_OFFSET) == OSU_PROP_INS) &&
             (*(input + ISO7816_P1_OFFSET) == OSU_PROP_RST_P1)) {
    // eSE hard reset on receiving proprietary reset APDU
    ALOGD("checkTransmit in OSU_PROP_RST_INS");
    if (phNxpEse_SetEndPoint_Cntxt(0) != ESESTATUS_SUCCESS) {
      ALOGE("phNxpEse_SetEndPoint_Cntxt failed!!!");
    }
    phNxpEse_resetJcopUpdate();
    if (phNxpEse_ResetEndPoint_Cntxt(0) != ESESTATUS_SUCCESS) {
      ALOGE("phNxpEse_ResetEndPoint_Cntxt failed!!!");
    }
    // Update mode after eSE reset
    checkAndUpdateOsuMode();
    phNxpEse_free(input);
    input = nullptr;
    halMode = OSU_RST_MODE;
  } else {
    // Process remaining OSU commands
    *outLength = length;
    halMode = OSU_GP_MODE;
  }
  return halMode;
}

/*******************************************************************************
**
** Function:    OsuHalExtn::getOSUMaxWtxCount
**
** Description: Read OSU_MAX_WTX_COUNT from config file allowed when in OSU
**              mode
**
** Returns:     Return maximum WTX count
**
*******************************************************************************/
unsigned long int OsuHalExtn::getOSUMaxWtxCount() {
  return EseConfig::getUnsigned(NAME_NXP_OSU_MAX_WTX_COUNT,
                                DEFAULT_MAX_WTX_COUNT);
}
