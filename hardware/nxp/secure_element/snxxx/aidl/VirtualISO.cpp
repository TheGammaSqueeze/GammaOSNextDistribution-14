/******************************************************************************
 *
 *  Copyright 2023 NXP
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
#include "VirtualISO.h"

#include "SecureElement.h"
// Undefined LOG_TAG as it is also defined in log.h
#undef LOG_TAG
#include <memunreachable/memunreachable.h>
// #include "hal_nxpese.h"
#include "phNxpEse_Apdu_Api.h"
#include "phNxpEse_Api.h"

namespace aidl {
namespace vendor {
namespace nxp {
namespace virtual_iso {

#define LOG_TAG "nxpVIsoese_aidl-service"

#define DEFAULT_BASIC_CHANNEL 0x00

typedef struct gsTransceiveBuffer {
  phNxpEse_data cmdData;
  phNxpEse_data rspData;
  std::vector<uint8_t>* pRspDataBuff;
} sTransceiveBuffer_t;
static sTransceiveBuffer_t gsTxRxBuffer;
static std::vector<uint8_t> gsRspDataBuff(256);

std::shared_ptr<ISecureElementCallback> VirtualISO::mCallback = nullptr;
AIBinder_DeathRecipient* clientDeathRecipient = nullptr;
std::vector<bool> VirtualISO::mOpenedChannels;

VirtualISO::VirtualISO()
    : mMaxChannelCount(0), mOpenedchannelCount(0), mIsEseInitialized(false) {}

void OnDeath(void* cookie) {
  (void)cookie;
  // TODO: Implement graceful closure, to close ongoing tx-rx and deinit
  // T=1 stack
  // close(0);
}

ScopedAStatus VirtualISO::init(
    const std::shared_ptr<ISecureElementCallback>& clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  bool mIsInitDone = false;
  phNxpEse_initParams initParams;
  LOG(INFO) << "Virtual ISO::init Enter";
  gsTxRxBuffer.pRspDataBuff = &gsRspDataBuff;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;

  if (clientCallback == nullptr) {
    return ScopedAStatus::ok();
  } else {
    clientDeathRecipient = AIBinder_DeathRecipient_new(OnDeath);
    auto linkRet =
        AIBinder_linkToDeath(clientCallback->asBinder().get(),
                             clientDeathRecipient, this /* cookie */);
    if (linkRet != STATUS_OK) {
      LOG(ERROR) << __func__ << ": linkToDeath failed: " << linkRet;
      // Just ignore the error.
    }
  }

  if (mIsEseInitialized) {
    clientCallback->onStateChange(true, "NXP VISIO HAL init ok");
    return ScopedAStatus::ok();
  }
  status = phNxpEse_open(initParams);
  if (status == ESESTATUS_SUCCESS || ESESTATUS_BUSY == status) {
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(1) &&
        ESESTATUS_SUCCESS == phNxpEse_init(initParams)) {
      if (ESESTATUS_SUCCESS == phNxpEse_ResetEndPoint_Cntxt(1)) {
        LOG(INFO) << "VISO init complete!!!";
        mIsInitDone = true;
      }
      deInitStatus = phNxpEse_deInit();
      if (ESESTATUS_SUCCESS != deInitStatus) mIsInitDone = false;
    }
    status = phNxpEse_close(deInitStatus);
  }
  if (status == ESESTATUS_SUCCESS && mIsInitDone) {
    mMaxChannelCount = (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
    mOpenedChannels.resize(mMaxChannelCount, false);
    clientCallback->onStateChange(true, "NXP VISIO HAL init ok");
  } else {
    LOG(ERROR) << "VISO-Hal Init failed";
    clientCallback->onStateChange(false, "NXP VISIO HAL init failed");
  }
  return ScopedAStatus::ok();
}

ScopedAStatus VirtualISO::getAtr(std::vector<uint8_t>* _aidl_return) {
  std::vector<uint8_t> response;
  *_aidl_return = std::move(response);
  return ScopedAStatus::ok();
}

ScopedAStatus VirtualISO::isCardPresent(bool* _aidl_return) {
  LOG(INFO) << __func__;
  *_aidl_return = true;
  return ScopedAStatus::ok();
}

ScopedAStatus VirtualISO::transmit(const std::vector<uint8_t>& data,
                                   std::vector<uint8_t>* _aidl_return) {
  ESESTATUS status = ESESTATUS_FAILED;
  std::vector<uint8_t> result;
  phNxpEse_memset(&gsTxRxBuffer.cmdData, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&gsTxRxBuffer.rspData, 0x00, sizeof(phNxpEse_data));
  gsTxRxBuffer.cmdData.len = data.size();
  gsTxRxBuffer.cmdData.p_data =
      (uint8_t*)phNxpEse_memalloc(data.size() * sizeof(uint8_t));
  if (NULL == gsTxRxBuffer.cmdData.p_data) {
    LOG(ERROR) << "transmit failed to allocate the Memory!!!";
    /*Return empty vec*/
    *_aidl_return = result;
    return ScopedAStatus::ok();
  }
  memcpy(gsTxRxBuffer.cmdData.p_data, data.data(), gsTxRxBuffer.cmdData.len);
  LOG(ERROR) << "Acquired the lock in VISO ";
  status = phNxpEse_SetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
  status = phNxpEse_Transceive(&gsTxRxBuffer.cmdData, &gsTxRxBuffer.rspData);

  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "transmit failed!!!";
  } else {
    result.resize(gsTxRxBuffer.rspData.len);
    memcpy(&result[0], gsTxRxBuffer.rspData.p_data, gsTxRxBuffer.rspData.len);
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }

  *_aidl_return = std::move(result);

  if (NULL != gsTxRxBuffer.cmdData.p_data) {
    phNxpEse_free(gsTxRxBuffer.cmdData.p_data);
    gsTxRxBuffer.cmdData.p_data = NULL;
  }
  if (NULL != gsTxRxBuffer.rspData.p_data) {
    phNxpEse_free(gsTxRxBuffer.rspData.p_data);
    gsTxRxBuffer.rspData.p_data = NULL;
  }

  return ScopedAStatus::ok();
}

ScopedAStatus VirtualISO::openLogicalChannel(
    const std::vector<uint8_t>& aid, int8_t p2,
    ::aidl::android::hardware::secure_element::LogicalChannelResponse*
        _aidl_return) {
  std::vector<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};

  LogicalChannelResponse resApduBuff;

  if (GET_CHIP_OS_VERSION() <= OS_VERSION_6_2) {
    uint8_t maxLogicalChannelSupported = mMaxChannelCount - 1;
    uint8_t openedLogicalChannelCount = mOpenedchannelCount;
    if (mOpenedChannels[0]) openedLogicalChannelCount--;

    if (openedLogicalChannelCount >= maxLogicalChannelSupported) {
      LOG(ERROR) << "%s: Reached Max supported Logical Channel" << __func__;
      *_aidl_return = resApduBuff;
      return ScopedAStatus::fromServiceSpecificError(CHANNEL_NOT_AVAILABLE);
    }
  }

  LOG(INFO) << "Acquired the lock in VISO openLogicalChannel";

  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      *_aidl_return = resApduBuff;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
    mOpenedChannels.resize(mMaxChannelCount, false);
  }

  int sestatus = ISecureElement::IOERROR;

  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = manageChannelCommand.size();
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(manageChannelCommand.size() *
                                               sizeof(uint8_t));
  memcpy(cmdApdu.p_data, manageChannelCommand.data(), cmdApdu.len);

  sestatus = SESTATUS_SUCCESS;

  status = phNxpEse_SetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
  status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  if (status != ESESTATUS_SUCCESS) {
    resApduBuff.channelNumber = 0xff;
    if (NULL != rspApdu.p_data && rspApdu.len > 0) {
      if ((rspApdu.p_data[0] == 0x64 && rspApdu.p_data[1] == 0xFF)) {
        sestatus = ISecureElement::IOERROR;
      }
    }
    if (sestatus != ISecureElement::IOERROR) {
      sestatus = ISecureElement::FAILED;
    }
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x6A &&
             rspApdu.p_data[rspApdu.len - 1] == 0x81) {
    resApduBuff.channelNumber = 0xff;
    sestatus = ISecureElement::CHANNEL_NOT_AVAILABLE;
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x90 &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    resApduBuff.channelNumber = rspApdu.p_data[0];
    mOpenedchannelCount++;
    mOpenedChannels[resApduBuff.channelNumber] = true;
    sestatus = SESTATUS_SUCCESS;
  } else if (((rspApdu.p_data[rspApdu.len - 2] == 0x6E) ||
              (rspApdu.p_data[rspApdu.len - 2] == 0x6D)) &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    sestatus = ISecureElement::UNSUPPORTED_OPERATION;
  }

  /*Free the allocations*/
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);

  if (sestatus != SESTATUS_SUCCESS) {
    if (mOpenedchannelCount == 0) {
      sestatus = seHalDeInit();
      if (sestatus != SESTATUS_SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
      }
    }
    /*If manageChannel is failed in any of above cases
    send the callback and return*/
    status = phNxpEse_ResetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
    *_aidl_return = resApduBuff;
    return ScopedAStatus::fromServiceSpecificError(sestatus);
  }
  LOG(INFO) << "openLogicalChannel Sending selectApdu";
  sestatus = ISecureElement::IOERROR;
  status = ESESTATUS_FAILED;

  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;
  phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
  phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));

  if ((resApduBuff.channelNumber > 0x03) &&
      (resApduBuff.channelNumber < 0x14)) {
    /* update CLA byte according to GP spec Table 11-12*/
    cpdu.cla =
        0x40 + (resApduBuff.channelNumber - 4); /* Class of instruction */
  } else if ((resApduBuff.channelNumber > 0x00) &&
             (resApduBuff.channelNumber < 0x04)) {
    /* update CLA byte according to GP spec Table 11-11*/
    cpdu.cla = resApduBuff.channelNumber; /* Class of instruction */
  } else {
    ALOGE("%s: Invalid Channel no: %02x", __func__, resApduBuff.channelNumber);
    resApduBuff.channelNumber = 0xff;
    *_aidl_return = resApduBuff;
    return ScopedAStatus::fromServiceSpecificError(IOERROR);
  }
  cpdu.ins = 0xA4; /* Instruction code */
  cpdu.p1 = 0x04;  /* Instruction parameter 1 */
  cpdu.p2 = p2;    /* Instruction parameter 2 */
  cpdu.lc = aid.size();
  cpdu.le_type = 0x01;
  cpdu.pdata = (uint8_t*)phNxpEse_memalloc(aid.size() * sizeof(uint8_t));
  memcpy(cpdu.pdata, aid.data(), cpdu.lc);
  cpdu.le = 256;

  rpdu.len = 0x02;
  rpdu.pdata = (uint8_t*)phNxpEse_memalloc(cpdu.le * sizeof(uint8_t));

  status = phNxpEse_7816_Transceive(&cpdu, &rpdu);
  if (status != ESESTATUS_SUCCESS) {
    /*Transceive failed*/
    if (rpdu.len > 0 && (rpdu.sw1 == 0x64 && rpdu.sw2 == 0xFF)) {
      sestatus = ISecureElement::IOERROR;
    } else {
      sestatus = ISecureElement::FAILED;
    }
  } else {
    /*Status word to be passed as part of response
    So include additional length*/
    uint16_t responseLen = rpdu.len + 2;
    resApduBuff.selectResponse.resize(responseLen);
    memcpy(&resApduBuff.selectResponse[0], rpdu.pdata, rpdu.len);
    resApduBuff.selectResponse[responseLen - 1] = rpdu.sw2;
    resApduBuff.selectResponse[responseLen - 2] = rpdu.sw1;

    /*Status is success*/
    if (rpdu.sw1 == 0x90 && rpdu.sw2 == 0x00) {
      sestatus = SESTATUS_SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) {
      sestatus = ISecureElement::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = ISecureElement::UNSUPPORTED_OPERATION;
    } else {
      sestatus = ISecureElement::FAILED;
    }
  }
  if (sestatus != SESTATUS_SUCCESS) {
    int closeChannelStatus = internalCloseChannel(resApduBuff.channelNumber);
    if (closeChannelStatus != SESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: closeChannel Failed" << __func__;
    } else {
      resApduBuff.channelNumber = 0xff;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }
  *_aidl_return = std::move(resApduBuff);
  phNxpEse_free(cpdu.pdata);
  phNxpEse_free(rpdu.pdata);

  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
}

ScopedAStatus VirtualISO::openBasicChannel(const std::vector<uint8_t>& aid,
                                           int8_t p2,
                                           std::vector<uint8_t>* _aidl_return) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;
  std::vector<uint8_t> result;

  LOG(INFO) << "Acquired the lock in VISO openBasicChannel";

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      *_aidl_return = result;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
    mOpenedChannels.resize(mMaxChannelCount, false);
  }

  phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
  phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));

  cpdu.cla = 0x00; /* Class of instruction */
  cpdu.ins = 0xA4; /* Instruction code */
  cpdu.p1 = 0x04;  /* Instruction parameter 1 */
  cpdu.p2 = p2;    /* Instruction parameter 2 */
  cpdu.lc = aid.size();
  cpdu.le_type = 0x01;
  cpdu.pdata = (uint8_t*)phNxpEse_memalloc(aid.size() * sizeof(uint8_t));
  memcpy(cpdu.pdata, aid.data(), cpdu.lc);
  cpdu.le = 256;

  rpdu.len = 0x02;
  rpdu.pdata = (uint8_t*)phNxpEse_memalloc(cpdu.le * sizeof(uint8_t));

  status = phNxpEse_SetEndPoint_Cntxt(1);
  status = phNxpEse_7816_Transceive(&cpdu, &rpdu);

  int sestatus;
  sestatus = SESTATUS_SUCCESS;

  if (status != ESESTATUS_SUCCESS) {
    /* Transceive failed */
    if (rpdu.len > 0 && (rpdu.sw1 == 0x64 && rpdu.sw2 == 0xFF)) {
      sestatus = ISecureElement::IOERROR;
    } else {
      sestatus = ISecureElement::FAILED;
    }
  } else {
    /*Status word to be passed as part of response
    So include additional length*/
    uint16_t responseLen = rpdu.len + 2;
    result.resize(responseLen);
    memcpy(&result[0], rpdu.pdata, rpdu.len);
    result[responseLen - 1] = rpdu.sw2;
    result[responseLen - 2] = rpdu.sw1;

    /*Status is success*/
    if ((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) {
      /*Set basic channel reference if it is not set */
      if (!mOpenedChannels[0]) {
        mOpenedChannels[0] = true;
        mOpenedchannelCount++;
      }

      sestatus = SESTATUS_SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) {
      sestatus = ISecureElement::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = ISecureElement::UNSUPPORTED_OPERATION;
    } else {
      sestatus = ISecureElement::FAILED;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }
  if (sestatus != SESTATUS_SUCCESS) {
    int closeChannelStatus = internalCloseChannel(DEFAULT_BASIC_CHANNEL);
    if (closeChannelStatus != SESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: closeChannel Failed" << __func__;
    }
  }
  *_aidl_return = std::move(result);
  phNxpEse_free(cpdu.pdata);
  phNxpEse_free(rpdu.pdata);
  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
}

int VirtualISO::internalCloseChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  int sestatus = ISecureElement::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  ALOGE("internalCloseChannel Enter");
  ALOGI("mMaxChannelCount = %d, Closing Channel = %d", mMaxChannelCount,
        channelNumber);
  if ((int8_t)channelNumber < DEFAULT_BASIC_CHANNEL ||
      channelNumber >= mMaxChannelCount) {
    ALOGE("invalid channel!!! %d", channelNumber);
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
    phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));
    cpdu.cla = channelNumber; /* Class of instruction */
    // For Supplementary Channel update CLA byte according to GP
    if ((channelNumber > 0x03) && (channelNumber < 0x14)) {
      /* update CLA byte according to GP spec Table 11-12*/
      cpdu.cla = 0x40 + (channelNumber - 4); /* Class of instruction */
    }
    cpdu.ins = 0x70;         /* Instruction code */
    cpdu.p1 = 0x80;          /* Instruction parameter 1 */
    cpdu.p2 = channelNumber; /* Instruction parameter 2 */
    cpdu.lc = 0x00;
    cpdu.le = 0x9000;
    status = phNxpEse_SetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    }
    status = phNxpEse_7816_Transceive(&cpdu, &rpdu);

    if (status == ESESTATUS_SUCCESS) {
      if ((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) {
        sestatus = SESTATUS_SUCCESS;
      }
    }
    status = phNxpEse_ResetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
    if (mOpenedChannels[channelNumber]) {
      mOpenedChannels[channelNumber] = false;
      mOpenedchannelCount--;
    }
  }
  /*If there are no channels remaining close secureElement*/
  if (mOpenedchannelCount == 0) {
    sestatus = seHalDeInit();
  } else {
    sestatus = SESTATUS_SUCCESS;
  }
  return sestatus;
}

ScopedAStatus VirtualISO::closeChannel(int8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  int sestatus = ISecureElement::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  LOG(INFO) << "Acquired the lock in VISO closeChannel";
  if ((int8_t)channelNumber < DEFAULT_BASIC_CHANNEL ||
      channelNumber >= mMaxChannelCount) {
    ALOGE("invalid channel!!! %d", channelNumber);
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
    phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));
    cpdu.cla = channelNumber; /* Class of instruction */
    cpdu.ins = 0x70;          /* Instruction code */
    cpdu.p1 = 0x80;           /* Instruction parameter 1 */
    cpdu.p2 = channelNumber;  /* Instruction parameter 2 */
    cpdu.lc = 0x00;
    cpdu.le = 0x9000;
    status = phNxpEse_SetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    }
    status = phNxpEse_7816_Transceive(&cpdu, &rpdu);

    if (status == ESESTATUS_SUCCESS) {
      if ((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) {
        sestatus = SESTATUS_SUCCESS;
      }
    }
    status = phNxpEse_ResetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
    if (mOpenedChannels[channelNumber]) {
      mOpenedChannels[channelNumber] = false;
      mOpenedchannelCount--;
    }
  }

  /*If there are no channels remaining close secureElement*/
  if (mOpenedchannelCount == 0) {
    sestatus = seHalDeInit();
  } else {
    sestatus = SESTATUS_SUCCESS;
  }
  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
}

ESESTATUS VirtualISO::seHalInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  phNxpEse_initParams initParams;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;

  status = phNxpEse_open(initParams);
  if (ESESTATUS_SUCCESS == status || ESESTATUS_BUSY == status) {
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(1) &&
        ESESTATUS_SUCCESS == phNxpEse_init(initParams)) {
      if (ESESTATUS_SUCCESS == phNxpEse_ResetEndPoint_Cntxt(1)) {
        mIsEseInitialized = true;
        LOG(INFO) << "VISO init complete!!!";
        return ESESTATUS_SUCCESS;
      }
      deInitStatus = phNxpEse_deInit();
    }
    if (phNxpEse_close(deInitStatus) != ESESTATUS_SUCCESS) {
      LOG(INFO) << "VISO close is not successful";
    }
    mIsEseInitialized = false;
  }
  return status;
}
int VirtualISO::seHalDeInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  bool mIsDeInitDone = true;
  int sestatus = ISecureElement::FAILED;
  status = phNxpEse_SetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    mIsDeInitDone = false;
  }
  deInitStatus = phNxpEse_deInit();
  if (ESESTATUS_SUCCESS != deInitStatus) mIsDeInitDone = false;
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    mIsDeInitDone = false;
  }
  status = phNxpEse_close(deInitStatus);
  if (status == ESESTATUS_SUCCESS && mIsDeInitDone) {
    sestatus = SESTATUS_SUCCESS;
  } else {
    LOG(ERROR) << "seHalDeInit: Failed";
  }
  // Clear all the flags as SPI driver is closed.
  mIsEseInitialized = false;
  for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
    mOpenedChannels[xx] = false;
  }
  mOpenedchannelCount = 0;
  return sestatus;
}

ScopedAStatus VirtualISO::reset() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  int sestatus = ISecureElement::FAILED;
  LOG(ERROR) << "%s: Enter" << __func__;
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
    }
  }
  if (status == ESESTATUS_SUCCESS) {
    mCallback->onStateChange(false, "reset the SE");
    status = phNxpEse_reset();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: SecureElement reset failed!!" << __func__;
    } else {
      sestatus = SESTATUS_SUCCESS;
      if (mOpenedChannels.size() == 0x00) {
        mMaxChannelCount =
            (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
        mOpenedChannels.resize(mMaxChannelCount, false);
      }
      for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      mCallback->onStateChange(true, "SE initialized");
    }
  }
  LOG(ERROR) << "%s: Exit" << __func__;
  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
}

}  // namespace virtual_iso
}  // namespace nxp
}  // namespace vendor
}  // namespace aidl
