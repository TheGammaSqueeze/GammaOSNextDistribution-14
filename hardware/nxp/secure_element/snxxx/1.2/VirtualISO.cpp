/******************************************************************************
 *
 *  Copyright 2018-2023 NXP
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
#ifdef NXP_BOOTTIME_UPDATE
#include "NxpEse.h"
#include "eSEClient.h"
#endif
#include <android-base/logging.h>
// Undefined LOG_TAG as it is also defined in log.h
#undef LOG_TAG
#include <memunreachable/memunreachable.h>

#include "hal_nxpese.h"
#include "phNxpEse_Apdu_Api.h"
#include "phNxpEse_Api.h"

namespace vendor {
namespace nxp {
namespace virtual_iso {
namespace V1_0 {
namespace implementation {

#define LOG_TAG "nxpVIsoese@1.2-service"

#define DEFAULT_BASIC_CHANNEL 0x00

using ::android::hardware::secure_element::V1_2::ISecureElement;
#ifdef NXP_BOOTTIME_UPDATE
using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
#endif

typedef struct gsTransceiveBuffer {
  phNxpEse_data cmdData;
  phNxpEse_data rspData;
  hidl_vec<uint8_t>* pRspDataBuff;
} sTransceiveBuffer_t;

static sTransceiveBuffer_t gsTxRxBuffer;
static hidl_vec<uint8_t> gsRspDataBuff(256);
sp<::android::hardware::secure_element::V1_0::ISecureElementHalCallback>
    VirtualISO::mCallbackV1_0 = nullptr;
sp<::android::hardware::secure_element::V1_1::ISecureElementHalCallback>
    VirtualISO::mCallbackV1_1 = nullptr;
std::vector<bool> VirtualISO::mOpenedChannels;

VirtualISO::VirtualISO()
    : mMaxChannelCount(0), mOpenedchannelCount(0), mIsEseInitialized(false) {}

Return<void> VirtualISO::init(
    const sp<
        ::android::hardware::secure_element::V1_0::ISecureElementHalCallback>&
        clientCallback) {
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
    return Void();
  } else {
    clientCallback->linkToDeath(this, 0 /*cookie*/);
  }
#ifdef NXP_BOOTTIME_UPDATE
  if (ese_update != ESE_UPDATE_COMPLETED) {
    mCallbackV1_0 = clientCallback;
    clientCallback->onStateChange(false);
    LOG(INFO) << "ESE JCOP Download in progress";
    NxpEse::setVirtualISOCallBack(clientCallback);
    return Void();
    // Register
  }
#endif
  if (mIsEseInitialized) {
    clientCallback->onStateChange(true);
    return Void();
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
    clientCallback->onStateChange(true);
  } else {
    LOG(ERROR) << "VISO-Hal Init failed";
    clientCallback->onStateChange(false);
  }
  return Void();
}

Return<void> VirtualISO::init_1_1(
    const sp<
        ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
        clientCallback) {
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
    return Void();
  } else {
    clientCallback->linkToDeath(this, 0 /*cookie*/);
  }
#ifdef NXP_BOOTTIME_UPDATE
  if (ese_update != ESE_UPDATE_COMPLETED) {
    mCallbackV1_1 = clientCallback;
    clientCallback->onStateChange_1_1(false, "NXP SE update going on");
    LOG(INFO) << "ESE JCOP Download in progress";
    NxpEse::setVirtualISOCallBack_1_1(clientCallback);
    return Void();
    // Register
  }
#endif
  if (mIsEseInitialized) {
    clientCallback->onStateChange_1_1(true, "NXP VISIO HAL init ok");
    return Void();
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
    clientCallback->onStateChange_1_1(true, "NXP VISIO HAL init ok");
  } else {
    LOG(ERROR) << "VISO-Hal Init failed";
    clientCallback->onStateChange_1_1(false, "NXP VISIO HAL init failed");
  }
  return Void();
}

Return<void> VirtualISO::getAtr(getAtr_cb _hidl_cb) {
  hidl_vec<uint8_t> response;
  _hidl_cb(response);
  return Void();
}

Return<bool> VirtualISO::isCardPresent() { return true; }

Return<void> VirtualISO::transmit(const hidl_vec<uint8_t>& data,
                                  transmit_cb _hidl_cb) {
  ESESTATUS status = ESESTATUS_FAILED;
  hidl_vec<uint8_t> result;
  phNxpEse_memset(&gsTxRxBuffer.cmdData, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&gsTxRxBuffer.rspData, 0x00, sizeof(phNxpEse_data));
  gsTxRxBuffer.cmdData.len = data.size();
  gsTxRxBuffer.cmdData.p_data =
      (uint8_t*)phNxpEse_memalloc(data.size() * sizeof(uint8_t));
  if (NULL == gsTxRxBuffer.cmdData.p_data) {
    LOG(ERROR) << "transmit failed to allocate the Memory!!!";
    /*Return empty hidl_vec*/
    _hidl_cb(result);
    return Void();
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

  _hidl_cb(result);
  if (NULL != gsTxRxBuffer.cmdData.p_data) {
    phNxpEse_free(gsTxRxBuffer.cmdData.p_data);
    gsTxRxBuffer.cmdData.p_data = NULL;
  }
  if (NULL != gsTxRxBuffer.rspData.p_data) {
    phNxpEse_free(gsTxRxBuffer.rspData.p_data);
    gsTxRxBuffer.rspData.p_data = NULL;
  }

  return Void();
}

Return<void> VirtualISO::openLogicalChannel(const hidl_vec<uint8_t>& aid,
                                            uint8_t p2,
                                            openLogicalChannel_cb _hidl_cb) {
  hidl_vec<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};

  LogicalChannelResponse resApduBuff;

  if (GET_CHIP_OS_VERSION() <= OS_VERSION_6_2) {
    uint8_t maxLogicalChannelSupported = mMaxChannelCount - 1;
    uint8_t openedLogicalChannelCount = mOpenedchannelCount;
    if (mOpenedChannels[0]) openedLogicalChannelCount--;

    if (openedLogicalChannelCount >= maxLogicalChannelSupported) {
      LOG(ERROR) << "%s: Reached Max supported Logical Channel" << __func__;
      _hidl_cb(resApduBuff, SecureElementStatus::CHANNEL_NOT_AVAILABLE);
      return Void();
    }
  }

  LOG(INFO) << "Acquired the lock in VISO openLogicalChannel";

  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
    mOpenedChannels.resize(mMaxChannelCount, false);
  }

  SecureElementStatus sestatus = SecureElementStatus::IOERROR;
  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = manageChannelCommand.size();
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(manageChannelCommand.size() *
                                               sizeof(uint8_t));
  memcpy(cmdApdu.p_data, manageChannelCommand.data(), cmdApdu.len);

  memset(&sestatus, 0x00, sizeof(sestatus));

  status = phNxpEse_SetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
  status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  if (status != ESESTATUS_SUCCESS) {
    resApduBuff.channelNumber = 0xff;
    if (NULL != rspApdu.p_data && rspApdu.len > 0) {
      if ((rspApdu.p_data[0] == 0x64 && rspApdu.p_data[1] == 0xFF)) {
        sestatus = SecureElementStatus::IOERROR;
      }
    }
    if (SecureElementStatus::IOERROR != sestatus) {
      sestatus = SecureElementStatus::FAILED;
    }
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x6A &&
             rspApdu.p_data[rspApdu.len - 1] == 0x81) {
    resApduBuff.channelNumber = 0xff;
    sestatus = SecureElementStatus::CHANNEL_NOT_AVAILABLE;
  } else if (rspApdu.p_data[rspApdu.len - 2] == 0x90 &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    resApduBuff.channelNumber = rspApdu.p_data[0];
    mOpenedchannelCount++;
    mOpenedChannels[resApduBuff.channelNumber] = true;
    sestatus = SecureElementStatus::SUCCESS;
  } else if (((rspApdu.p_data[rspApdu.len - 2] == 0x6E) ||
              (rspApdu.p_data[rspApdu.len - 2] == 0x6D)) &&
             rspApdu.p_data[rspApdu.len - 1] == 0x00) {
    sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
  }

  /*Free the allocations*/
  phNxpEse_free(cmdApdu.p_data);
  phNxpEse_free(rspApdu.p_data);

  if (sestatus != SecureElementStatus::SUCCESS) {
    if (mOpenedchannelCount == 0) {
      sestatus = seHalDeInit();
      if (sestatus != SecureElementStatus::SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
      }
    }
    /*If manageChannel is failed in any of above cases
    send the callback and return*/
    status = phNxpEse_ResetEndPoint_Cntxt(1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
    _hidl_cb(resApduBuff, sestatus);
    return Void();
  }
  LOG(INFO) << "openLogicalChannel Sending selectApdu";
  sestatus = SecureElementStatus::IOERROR;
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
    LOG(ERROR) << StringPrintf("%s: Invalid Channel no: %02x", __func__,
                               resApduBuff.channelNumber);
    resApduBuff.channelNumber = 0xff;
    _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
    return Void();
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
      sestatus = SecureElementStatus::IOERROR;
    } else {
      sestatus = SecureElementStatus::FAILED;
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
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    } else {
      sestatus = SecureElementStatus::FAILED;
    }
  }
  if (sestatus != SecureElementStatus::SUCCESS) {
    SecureElementStatus closeChannelStatus =
        internalCloseChannel(resApduBuff.channelNumber);
    if (closeChannelStatus != SecureElementStatus::SUCCESS) {
      LOG(ERROR) << "%s: closeChannel Failed" << __func__;
    } else {
      resApduBuff.channelNumber = 0xff;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }
  _hidl_cb(resApduBuff, sestatus);
  phNxpEse_free(cpdu.pdata);
  phNxpEse_free(rpdu.pdata);

  return Void();
}

Return<void> VirtualISO::openBasicChannel(const hidl_vec<uint8_t>& aid,
                                          uint8_t p2,
                                          openBasicChannel_cb _hidl_cb) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;
  hidl_vec<uint8_t> result;

  LOG(INFO) << "Acquired the lock in VISO openBasicChannel";

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      _hidl_cb(result, SecureElementStatus::IOERROR);
      return Void();
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

  SecureElementStatus sestatus;
  memset(&sestatus, 0x00, sizeof(sestatus));

  if (status != ESESTATUS_SUCCESS) {
    /* Transceive failed */
    if (rpdu.len > 0 && (rpdu.sw1 == 0x64 && rpdu.sw2 == 0xFF)) {
      sestatus = SecureElementStatus::IOERROR;
    } else {
      sestatus = SecureElementStatus::FAILED;
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

      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    } else {
      sestatus = SecureElementStatus::FAILED;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }
  if (sestatus != SecureElementStatus::SUCCESS) {
    SecureElementStatus closeChannelStatus =
        internalCloseChannel(DEFAULT_BASIC_CHANNEL);
    if (closeChannelStatus != SecureElementStatus::SUCCESS) {
      LOG(ERROR) << "%s: closeChannel Failed" << __func__;
    }
  }
  _hidl_cb(result, sestatus);
  phNxpEse_free(cpdu.pdata);
  phNxpEse_free(rpdu.pdata);
  return Void();
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
VirtualISO::internalCloseChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  LOG(ERROR) << "internalCloseChannel Enter";
  LOG(INFO) << StringPrintf("mMaxChannelCount = %d, Closing Channel = %d",
                            mMaxChannelCount, channelNumber);
  if ((int8_t)channelNumber < DEFAULT_BASIC_CHANNEL ||
      channelNumber >= mMaxChannelCount) {
    LOG(ERROR) << StringPrintf("invalid channel!!! %d", channelNumber);
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
    phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));
    cpdu.cla = channelNumber; /* Class of instruction */
    // For Supplementary Channel update CLA byte according to GP
    if ((channelNumber > 0x03) && (channelNumber < 0x14)) {
      /* update CLA byte according to GP spec Table 11-12*/
      cpdu.cla = 0x40 + (channelNumber - 4); /* Class of instruction */
    }
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
        sestatus = SecureElementStatus::SUCCESS;
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
    sestatus = SecureElementStatus::SUCCESS;
  }
  return sestatus;
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
VirtualISO::closeChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  LOG(INFO) << "Acquired the lock in VISO closeChannel";
  if ((int8_t)channelNumber < DEFAULT_BASIC_CHANNEL ||
      channelNumber >= mMaxChannelCount) {
    LOG(ERROR) << StringPrintf("invalid channel!!! %d", channelNumber);
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
        sestatus = SecureElementStatus::SUCCESS;
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
    sestatus = SecureElementStatus::SUCCESS;
  }
  return sestatus;
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
    phNxpEse_close(deInitStatus);
    mIsEseInitialized = false;
  }
  return status;
}

Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
VirtualISO::seHalDeInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  bool mIsDeInitDone = true;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
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
    sestatus = SecureElementStatus::SUCCESS;
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
Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
VirtualISO::reset() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  LOG(ERROR) << "%s: Enter" << __func__;
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
    }
  }
  if (status == ESESTATUS_SUCCESS) {
    mCallbackV1_1->onStateChange_1_1(false, "reset the SE");
    status = phNxpEse_reset();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: SecureElement reset failed!!" << __func__;
    } else {
      sestatus = SecureElementStatus::SUCCESS;
      if (mOpenedChannels.size() == 0x00) {
        mMaxChannelCount =
            (GET_CHIP_OS_VERSION() > OS_VERSION_6_2) ? 0x0C : 0x04;
        mOpenedChannels.resize(mMaxChannelCount, false);
      }
      for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      mCallbackV1_1->onStateChange_1_1(true, "SE initialized");
    }
  }
  LOG(ERROR) << "%s: Exit" << __func__;
  return sestatus;
}

Return<void> VirtualISO::debug(const hidl_handle& /* fd */,
                               const hidl_vec<hidl_string>& /* options */) {
  LOG(INFO) << "\n SecureElement-VirtualISO HAL MemoryLeak Info = \n"
            << ::android::GetUnreachableMemoryString(true, 10000).c_str();
  return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace virtual_iso
}  // namespace nxp
}  // namespace vendor
