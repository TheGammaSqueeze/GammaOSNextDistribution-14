/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
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
 *
 ******************************************************************************/
#define LOG_TAG "StEse-SecureElement"
#include "SecureElement.h"
#include <android_logmsg.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef int (*STAram_init)(void);
typedef int (*StAram_Transceive)(StEse_data*, StEse_data*);
static StAram_Transceive Aram_transceive = nullptr;
static int aram_channel = 0;

extern bool ese_debug_enabled;
static bool OpenLogicalChannelProcessing = false;
static bool OpenBasicChannelProcessing = false;

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_0 {
namespace implementation {

sp<V1_0::ISecureElementHalCallback> SecureElement::mCallbackV1_0 = nullptr;

SecureElement::SecureElement()
    : mOpenedchannelCount(0), mOpenedChannels{false, false, false, false} {}

Return<void> SecureElement::init(
    const sp<
        ::android::hardware::secure_element::V1_0::ISecureElementHalCallback>&
        clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  aram_channel = 0;
  STLOG_HAL_D("%s: Enter", __func__);
  if (clientCallback == nullptr) {
    return Void();
  } else {
    mCallbackV1_0 = clientCallback;
    if (!mCallbackV1_0->linkToDeath(this, 0 /*cookie*/)) {
      STLOG_HAL_E("%s: Failed to register death notification", __func__);
    }
  }

  if (isSeInitialized()) {
    clientCallback->onStateChange(true);
    return Void();
  }

  // Ignore this dlopen if you don't have libstpreprocess.so
#if defined(ST_LIB_32)
  void* stdll = dlopen("/vendor/lib/libstpreprocess.so", RTLD_NOW);
#else
  void* stdll = dlopen("/vendor/lib64/libstpreprocess.so", RTLD_NOW);
#endif
  if (stdll) {
    STAram_init fn_init = (STAram_init)dlsym(stdll, "STAram_init");
    if (fn_init) {
      if (ESESTATUS_SUCCESS == fn_init()) {
        STLOG_HAL_D("%s: Enter", __func__);
        Aram_transceive = (StAram_Transceive)dlsym(stdll, "StAram_Transceive");
      } else {
        Aram_transceive = nullptr;
        STLOG_HAL_E("%s: Error in loading StAram_Transceive", __func__);
      }
    }
  }

  status = seHalInit();
  if (status != ESESTATUS_SUCCESS) {
    clientCallback->onStateChange(false);
    return Void();
  } else {
    clientCallback->onStateChange(true);
    return Void();
  }
}

Return<void> SecureElement::getAtr(getAtr_cb _hidl_cb) {
  STLOG_HAL_D("%s: Enter", __func__);
  hidl_vec<uint8_t> response;
  uint8_t* ATR;
  ATR = StEse_getAtr();
  if (ATR != nullptr) {
    uint8_t len = *ATR;
    if (len) {
      response.resize(len);
      memcpy(&response[0], ATR, len);
    }
  }
  _hidl_cb(response);
  return Void();
}

Return<bool> SecureElement::isCardPresent() { return true; }

Return<void> SecureElement::transmit(const hidl_vec<uint8_t>& data,
                                     transmit_cb _hidl_cb) {
  ESESTATUS status = ESESTATUS_FAILED;
  StEse_data cmdApdu;
  StEse_data rspApdu;
  memset(&cmdApdu, 0x00, sizeof(StEse_data));
  memset(&rspApdu, 0x00, sizeof(StEse_data));

  STLOG_HAL_D("%s: Enter", __func__);
  cmdApdu.len = data.size();
  if (cmdApdu.len >= MIN_APDU_LENGTH) {
    cmdApdu.p_data = (uint8_t*)malloc(data.size() * sizeof(uint8_t));
    memcpy(cmdApdu.p_data, data.data(), cmdApdu.len);
    /* Check aram_channel number after open logic channel */
    if (aram_channel && (0x03 & cmdApdu.p_data[0]) == aram_channel &&
        Aram_transceive) {
      /* Replace responses for ARAM operations*/
      status = (ESESTATUS)Aram_transceive(&cmdApdu, &rspApdu);
    } else {
      status = StEse_Transceive(&cmdApdu, &rspApdu);
    }
  }

  hidl_vec<uint8_t> result;
  if (status != ESESTATUS_SUCCESS) {
    STLOG_HAL_E("%s: transmit failed!!!", __func__);
    seHalResetSe();
  } else {
    result.resize(rspApdu.len);
    memcpy(&result[0], rspApdu.p_data, rspApdu.len);
  }
  _hidl_cb(result);
  free(cmdApdu.p_data);
  free(rspApdu.p_data);
  return Void();
}

Return<void> SecureElement::openLogicalChannel(const hidl_vec<uint8_t>& aid,
                                               uint8_t p2,
                                               openLogicalChannel_cb _hidl_cb) {
  hidl_vec<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};
  hidl_vec<uint8_t> ARA_M_AID = {0xA0, 0x00, 0x00, 0x01, 0x51,
                                 0x41, 0x43, 0x4C, 0x00};
  OpenLogicalChannelProcessing = true;
  LogicalChannelResponse resApduBuff;
  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));
  STLOG_HAL_D("%s: Enter", __func__);

  if (aid.size() > 16) {
    STLOG_HAL_E("%s: Invalid AID size: %u", __func__, (unsigned)aid.size());
    _hidl_cb(resApduBuff, SecureElementStatus::FAILED);
    OpenLogicalChannelProcessing = false;
    return Void();
  }

  if (!isSeInitialized()) {
    STLOG_HAL_D("%s: Enter SeInitialized", __func__);
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      STLOG_HAL_E("%s: seHalInit Failed!!!", __func__);
      _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
      OpenLogicalChannelProcessing = false;
      return Void();
    }
  }

  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  ESESTATUS status = ESESTATUS_FAILED;
  StEse_data cmdApdu;
  StEse_data rspApdu;

  memset(&cmdApdu, 0x00, sizeof(StEse_data));
  memset(&rspApdu, 0x00, sizeof(StEse_data));

  cmdApdu.len = manageChannelCommand.size();
  cmdApdu.p_data =
      (uint8_t*)malloc(manageChannelCommand.size() * sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    memcpy(cmdApdu.p_data, manageChannelCommand.data(), cmdApdu.len);
    status = StEse_Transceive(&cmdApdu, &rspApdu);
  }
  if (status != ESESTATUS_SUCCESS) {
    /*Transceive failed*/
    sestatus = SecureElementStatus::IOERROR;
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x90 &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    /*ManageChannel successful*/
    resApduBuff.channelNumber = rspApdu.p_data[0];
    mOpenedchannelCount++;
    mOpenedChannels[resApduBuff.channelNumber] = true;
    sestatus = SecureElementStatus::SUCCESS;
    if (ARA_M_AID == aid) {
      STLOG_HAL_D("%s: ARAM AID match", __func__);
      aram_channel = resApduBuff.channelNumber;
    } else {
      /* Clear aram_channel number */
      if (aram_channel == resApduBuff.channelNumber) aram_channel = 0;
    }
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x6A &&
             rspApdu.p_data[rspApdu.len - 1] == 0x81) {
    sestatus = SecureElementStatus::CHANNEL_NOT_AVAILABLE;
  } else if (((rspApdu.p_data[rspApdu.len - 2] == 0x6E) ||
              (rspApdu.p_data[rspApdu.len - 2] == 0x6D)) &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
  }
  /*Free the allocations*/
  free(cmdApdu.p_data);
  cmdApdu.p_data = NULL;
  free(rspApdu.p_data);
  rspApdu.p_data = NULL;
  if (sestatus != SecureElementStatus::SUCCESS) {
    /* if the SE is unresponsive, reset it */
    if (sestatus == SecureElementStatus::IOERROR) {
      seHalResetSe();
    }

    /*If manageChannel is failed in any of above cases
    send the callback and return*/
    _hidl_cb(resApduBuff, sestatus);
    STLOG_HAL_E("%s: Exit - manage channel failed!!", __func__);
    OpenLogicalChannelProcessing = false;
    return Void();
  }

  STLOG_HAL_D("%s: Sending selectApdu", __func__);
  /*Reset variables if manageChannel is success*/
  sestatus = SecureElementStatus::FAILED;
  status = ESESTATUS_FAILED;

  memset(&cmdApdu, 0x00, sizeof(StEse_data));
  memset(&rspApdu, 0x00, sizeof(StEse_data));

  cmdApdu.len = (int32_t)(6 + aid.size());
  cmdApdu.p_data = (uint8_t*)malloc(cmdApdu.len * sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    uint8_t xx = 0;
    cmdApdu.p_data[xx++] = resApduBuff.channelNumber;
    cmdApdu.p_data[xx++] = 0xA4;        // INS
    cmdApdu.p_data[xx++] = 0x04;        // P1
    cmdApdu.p_data[xx++] = p2;          // P2
    cmdApdu.p_data[xx++] = aid.size();  // Lc
    memcpy(&cmdApdu.p_data[xx], aid.data(), aid.size());
    cmdApdu.p_data[xx + aid.size()] = 0x00;  // Le

    if (Aram_transceive && (aram_channel == resApduBuff.channelNumber)) {
      status = (ESESTATUS)Aram_transceive(&cmdApdu, &rspApdu);
    } else {
      status = StEse_Transceive(&cmdApdu, &rspApdu);
    }
  }

  if (status != ESESTATUS_SUCCESS) {
    /*Transceive failed*/
    sestatus = SecureElementStatus::IOERROR;
  } else {
    uint8_t sw1 = rspApdu.p_data[rspApdu.len - 2];
    uint8_t sw2 = rspApdu.p_data[rspApdu.len - 1];
    /*Return response on success, empty vector on failure*/
    /*Status is success*/
    if (sw1 == 0x90 && sw2 == 0x00) {
      /*Copy the response including status word*/
      resApduBuff.selectResponse.resize(rspApdu.len);
      memcpy(&resApduBuff.selectResponse[0], rspApdu.p_data, rspApdu.len);
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (sw1 == 0x6A && sw2 == 0x82) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (sw1 == 0x6A && sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    }
  }

  if (sestatus != SecureElementStatus::SUCCESS) {
    /* if the SE is unresponsive, reset it */
    if (sestatus == SecureElementStatus::IOERROR) {
      seHalResetSe();
    } else {
      STLOG_HAL_E("%s: Select APDU failed! Close channel..", __func__);
      SecureElementStatus closeChannelStatus =
          closeChannel(resApduBuff.channelNumber);
      if (closeChannelStatus != SecureElementStatus::SUCCESS) {
        STLOG_HAL_E("%s: closeChannel Failed", __func__);
      } else {
        resApduBuff.channelNumber = 0xff;
      }
    }
  }
  _hidl_cb(resApduBuff, sestatus);
  free(cmdApdu.p_data);
  free(rspApdu.p_data);
  STLOG_HAL_V("%s: Exit", __func__);
  OpenLogicalChannelProcessing = false;
  return Void();
}

Return<void> SecureElement::openBasicChannel(const hidl_vec<uint8_t>& aid,
                                             uint8_t p2,
                                             openBasicChannel_cb _hidl_cb) {
  hidl_vec<uint8_t> result;
  OpenBasicChannelProcessing = true;
  STLOG_HAL_D("%s: Enter", __func__);

  if (aid.size() > 16) {
    STLOG_HAL_E("%s: Invalid AID size: %u", __func__, (unsigned)aid.size());
    _hidl_cb(result, SecureElementStatus::FAILED);
    OpenBasicChannelProcessing = false;
    return Void();
  }

  if (!isSeInitialized()) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      STLOG_HAL_E("%s: seHalInit Failed!!!", __func__);
      _hidl_cb(result, SecureElementStatus::IOERROR);
      OpenBasicChannelProcessing = false;
      return Void();
    }
  }

  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  ESESTATUS status = ESESTATUS_FAILED;
  StEse_data cmdApdu;
  StEse_data rspApdu;

  memset(&cmdApdu, 0x00, sizeof(StEse_data));
  memset(&rspApdu, 0x00, sizeof(StEse_data));

  cmdApdu.len = (int32_t)(6 + aid.size());
  cmdApdu.p_data = (uint8_t*)malloc(cmdApdu.len * sizeof(uint8_t));
  if (cmdApdu.p_data != NULL) {
    uint8_t xx = 0;
    cmdApdu.p_data[xx++] = 0x00;        // basic channel
    cmdApdu.p_data[xx++] = 0xA4;        // INS
    cmdApdu.p_data[xx++] = 0x04;        // P1
    cmdApdu.p_data[xx++] = p2;          // P2
    cmdApdu.p_data[xx++] = aid.size();  // Lc
    memcpy(&cmdApdu.p_data[xx], aid.data(), aid.size());
    cmdApdu.p_data[xx + aid.size()] = 0x00;  // Le

    status = StEse_Transceive(&cmdApdu, &rspApdu);
  }

  if (status != ESESTATUS_SUCCESS) {
    /* Transceive failed */
    sestatus = SecureElementStatus::IOERROR;
  } else {
    uint8_t sw1 = rspApdu.p_data[rspApdu.len - 2];
    uint8_t sw2 = rspApdu.p_data[rspApdu.len - 1];
    /*Return response on success, empty vector on failure*/
    /*Status is success*/
    if ((sw1 == 0x90) && (sw2 == 0x00)) {
      /*Copy the response including status word*/
      result.resize(rspApdu.len);
      memcpy(&result[0], rspApdu.p_data, rspApdu.len);
      /*Set basic channel reference if it is not set */
      if (!mOpenedChannels[0]) {
        mOpenedChannels[0] = true;
        mOpenedchannelCount++;
      }
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (sw1 == 0x6A && sw2 == 0x82) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (sw1 == 0x6A && sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    }
  }

  /* if the SE is unresponsive, reset it */
  if (sestatus == SecureElementStatus::IOERROR) {
    seHalResetSe();
  }

  if ((sestatus != SecureElementStatus::SUCCESS) && mOpenedChannels[0]) {
    SecureElementStatus closeChannelStatus =
        closeChannel(DEFAULT_BASIC_CHANNEL);
    if (closeChannelStatus != SecureElementStatus::SUCCESS) {
      STLOG_HAL_E("%s: closeChannel Failed", __func__);
    }
  }
  _hidl_cb(result, sestatus);
  free(cmdApdu.p_data);
  free(rspApdu.p_data);
  STLOG_HAL_V("%s: Exit", __func__);
  OpenBasicChannelProcessing = false;
  return Void();
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
SecureElement::closeChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_FAILED;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;

  StEse_data cmdApdu;
  StEse_data rspApdu;

  STLOG_HAL_D("%s: Enter : %d", __func__, channelNumber);

  if ((channelNumber < DEFAULT_BASIC_CHANNEL) ||
      (channelNumber >= MAX_LOGICAL_CHANNELS) ||
      (mOpenedChannels[channelNumber] == false)) {
    STLOG_HAL_E("%s: invalid channel!!!", __func__);
    sestatus = SecureElementStatus::FAILED;
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    /* Reset aram_channel to 0 */
    if (channelNumber == aram_channel) aram_channel = 0;

    memset(&cmdApdu, 0x00, sizeof(StEse_data));
    memset(&rspApdu, 0x00, sizeof(StEse_data));
    cmdApdu.p_data = (uint8_t*)malloc(5 * sizeof(uint8_t));
    if (cmdApdu.p_data != NULL) {
      uint8_t xx = 0;

      cmdApdu.p_data[xx++] = channelNumber;
      cmdApdu.p_data[xx++] = 0x70;           // INS
      cmdApdu.p_data[xx++] = 0x80;           // P1
      cmdApdu.p_data[xx++] = channelNumber;  // P2
      cmdApdu.p_data[xx++] = 0x00;           // Lc
      cmdApdu.len = xx;

      status = StEse_Transceive(&cmdApdu, &rspApdu);
    }
    if (status != ESESTATUS_SUCCESS) {
      sestatus = SecureElementStatus::FAILED;
    } else if ((rspApdu.p_data[rspApdu.len - 2] == 0x90) &&
               (rspApdu.p_data[rspApdu.len - 1] == 0x00)) {
      sestatus = SecureElementStatus::SUCCESS;
    } else {
      sestatus = SecureElementStatus::FAILED;
    }
    free(cmdApdu.p_data);
    free(rspApdu.p_data);
  }

  if ((channelNumber == DEFAULT_BASIC_CHANNEL) ||
      (sestatus == SecureElementStatus::SUCCESS)) {
    mOpenedChannels[channelNumber] = false;
    mOpenedchannelCount--;
    /*If there are no channels remaining close secureElement*/
    if ((mOpenedchannelCount == 0) && !OpenLogicalChannelProcessing &&
        !OpenBasicChannelProcessing) {
      sestatus = seHalDeInit();
    } else {
      sestatus = SecureElementStatus::SUCCESS;
    }
  }

  STLOG_HAL_V("%s: Exit", __func__);
  return sestatus;
}

void SecureElement::serviceDied(uint64_t /*cookie*/, const wp<IBase>& /*who*/) {
  STLOG_HAL_E("%s: SecureElement serviceDied!!!", __func__);
  /* Reset aram_channel to 0 */
  aram_channel = 0;
  SecureElementStatus sestatus = seHalDeInit();
  if (sestatus != SecureElementStatus::SUCCESS) {
    STLOG_HAL_E("%s: seHalDeInit Faliled!!!", __func__);
  }
  if (mCallbackV1_0 != nullptr) {
    mCallbackV1_0->unlinkToDeath(this);
  }
}

bool SecureElement::isSeInitialized() { return StEseApi_isOpen(); }

ESESTATUS SecureElement::seHalInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;

  STLOG_HAL_D("%s: Enter", __func__);
  aram_channel = 0;
  status = StEse_init();
  if (status != ESESTATUS_SUCCESS) {
    STLOG_HAL_E("%s: SecureElement open failed!!!", __func__);
  }
  STLOG_HAL_V("%s: Exit", __func__);
  return status;
}

void SecureElement::seHalResetSe() {
  ESESTATUS status = ESESTATUS_SUCCESS;

  STLOG_HAL_D("%s: Enter", __func__);
  if (!isSeInitialized()) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      STLOG_HAL_E("%s: seHalInit Failed!!!", __func__);
    }
  }

  if (status == ESESTATUS_SUCCESS) {
    mCallbackV1_0->onStateChange(false);

    status = StEse_Reset();
    if (status != ESESTATUS_SUCCESS) {
      STLOG_HAL_E("%s: SecureElement reset failed!!", __func__);
    } else {
      for (uint8_t xx = 0; xx < MAX_LOGICAL_CHANNELS; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      mCallbackV1_0->onStateChange(true);
    }
  }
  STLOG_HAL_V("%s: Exit", __func__);
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
SecureElement::seHalDeInit() {
  STLOG_HAL_D("%s: Enter", __func__);
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  status = StEse_close();
  if (status != ESESTATUS_SUCCESS) {
    sestatus = SecureElementStatus::FAILED;
  } else {
    sestatus = SecureElementStatus::SUCCESS;

    for (uint8_t xx = 0; xx < MAX_LOGICAL_CHANNELS; xx++) {
      mOpenedChannels[xx] = false;
    }
    mOpenedchannelCount = 0;
  }
  STLOG_HAL_V("%s: Exit", __func__);
  return sestatus;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace secure_element
}  // namespace hardware
}  // namespace android
