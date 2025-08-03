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
#include "SecureElement.h"

#ifdef NXP_BOOTTIME_UPDATE
#include "NxpEse.h"
#include "eSEClient.h"
#endif
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <hidl/HidlTransportSupport.h>

#include "hal_nxpese.h"
#include "phNxpEse_Apdu_Api.h"
#include "phNxpEse_Api.h"
/* Mutex to synchronize multiple transceive */
#include <memunreachable/memunreachable.h>
namespace android {
namespace hardware {
namespace secure_element {
namespace V1_2 {
namespace implementation {

#define DEFAULT_BASIC_CHANNEL 0x00
#define INVALID_LEN_SW1 0x64
#define INVALID_LEN_SW2 0xFF
#define SW1_BYTES_REMAINING 0x61
#define NUM_OF_CH4 0x04
#define NUM_OF_CH5 0x05
typedef struct gsTransceiveBuffer {
  phNxpEse_data cmdData;
  phNxpEse_data rspData;
  hidl_vec<uint8_t>* pRspDataBuff;
} sTransceiveBuffer_t;

static Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                    hidl_vec<uint8_t>& result);
static sTransceiveBuffer_t gsTxRxBuffer;
static hidl_vec<uint8_t> gsRspDataBuff(256);
sp<V1_0::ISecureElementHalCallback> SecureElement::mCallbackV1_0 = nullptr;
std::vector<bool> SecureElement::mOpenedChannels;
#ifdef NXP_BOOTTIME_UPDATE
using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
#endif
SecureElement::SecureElement()
    : mMaxChannelCount(0), mOpenedchannelCount(0), mIsEseInitialized(false) {}

void SecureElement::NotifySeWaitExtension(phNxpEse_wtxState state) {
  if (state == WTX_ONGOING) {
    LOG(INFO) << "SecureElement::WTX ongoing";
  } else if (state == WTX_END) {
    LOG(INFO) << "SecureElement::WTX ended";
  }
}

Return<void> SecureElement::init(
    const sp<
        ::android::hardware::secure_element::V1_0::ISecureElementHalCallback>&
        clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  bool mIsInitDone = false;
  phNxpEse_initParams initParams;
  gsTxRxBuffer.pRspDataBuff = &gsRspDataBuff;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;
  initParams.fPtr_WtxNtf = SecureElement::NotifySeWaitExtension;

  if (clientCallback == nullptr) {
    return Void();
  } else {
    clientCallback->linkToDeath(this, 0 /*cookie*/);
  }
  LOG(INFO) << "SecureElement::init called here";
#ifdef NXP_BOOTTIME_UPDATE
  if (ese_update != ESE_UPDATE_COMPLETED) {
    mCallbackV1_0 = clientCallback;
    clientCallback->onStateChange(false);
    LOG(INFO) << "ESE JCOP Download in progress";
    NxpEse::setSeCallBack(clientCallback);
    return Void();
    // Register
  }
#endif
  if (mIsEseInitialized) {
    clientCallback->onStateChange(true);
    return Void();
  }

  phNxpEse_setWtxCountLimit(OsuHalExtn::getInstance().getOSUMaxWtxCount());
  status = phNxpEse_open(initParams);
  if (status == ESESTATUS_SUCCESS || ESESTATUS_BUSY == status) {
    ESESTATUS initStatus = ESESTATUS_SUCCESS;
    ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(0)) {
      initStatus = phNxpEse_init(initParams);
      if (ESESTATUS_SUCCESS == initStatus) {
        if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9) {
          /*update OS mode during first init*/
          IS_OSU_MODE(OsuHalExtn::getInstance().INIT, 0);
        }

        if (ESESTATUS_SUCCESS == phNxpEse_ResetEndPoint_Cntxt(0)) {
          LOG(INFO) << "ESE SPI init complete!!!";
          mIsInitDone = true;
        }
        deInitStatus = phNxpEse_deInit();
        if (ESESTATUS_SUCCESS != deInitStatus) mIsInitDone = false;
      }
    }
    status = phNxpEse_close(deInitStatus);
    /*Enable terminal post recovery(i.e. close success) from transmit failure */
    if (status == ESESTATUS_SUCCESS &&
        (initStatus == ESESTATUS_TRANSCEIVE_FAILED ||
         initStatus == ESESTATUS_FAILED)) {
      if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9)
        IS_OSU_MODE(OsuHalExtn::getInstance().INIT, 0);
      mIsInitDone = true;
    }
  }
  phNxpEse_setWtxCountLimit(RESET_APP_WTX_COUNT);
  if (status == ESESTATUS_SUCCESS && mIsInitDone) {
    mHasPriorityAccess = phNxpEse_isPriorityAccessEnabled();
    mMaxChannelCount = getMaxChannelCnt();
    mOpenedChannels.resize(mMaxChannelCount, false);
    clientCallback->onStateChange(true);
    mCallbackV1_0 = clientCallback;
  } else {
    LOG(ERROR) << "eSE-Hal Init failed";
    clientCallback->onStateChange(false);
  }
  return Void();
}
void SecureElement::registerCallback(
    const sp<V1_1::ISecureElementHalCallback>& callback) {
  if (callback == nullptr) {
    return;
  }
  mCallbacks.push_back(callback);

  LOG(INFO) << __func__ << " New client " << callback.get()
            << " registered . total clients = " << mCallbacks.size();

  auto linkRet = callback->linkToDeath(this, 0u /* cookie */);
  if (!linkRet.withDefault(false)) {
    LOG(WARNING) << __func__ << " Cannot link to death: "
                 << (linkRet.isOk() ? "linkToDeath returns false"
                                    : linkRet.description());
  }
}

void SecureElement::unregisterCallback(const sp<IBase>& callback) {
  if (callback == nullptr) return;
  bool removed = false;

  for (auto it = mCallbacks.begin(); it != mCallbacks.end();) {
    if (interfacesEqual(*it, callback)) {
      it = mCallbacks.erase(it);
      removed = true;
    } else {
      ++it;
    }
  }
  callback->unlinkToDeath(this).isOk();  // ignore errors

  if (removed)
    LOG(INFO) << __func__ << " client " << callback.get()
              << " removed. Total clients = " << mCallbacks.size();
  else
    LOG(WARNING) << __func__ << " Failed to remove client. total clients = "
                 << mCallbacks.size();
}
Return<void> SecureElement::init_1_1(
    const sp<
        ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
        clientCallback) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  bool mIsInitDone = false;
  phNxpEse_initParams initParams;
  gsTxRxBuffer.pRspDataBuff = &gsRspDataBuff;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;
  initParams.fPtr_WtxNtf = SecureElement::NotifySeWaitExtension;

  if (clientCallback == nullptr) return Void();

  LOG(INFO) << "SecureElement::init called here";
#ifdef NXP_BOOTTIME_UPDATE
  if (ese_update != ESE_UPDATE_COMPLETED) {
    clientCallback->onStateChange_1_1(false, "NXP SE update going on");
    LOG(INFO) << "ESE JCOP Download in progress";
    NxpEse::setSeCallBack_1_1(clientCallback);
    return Void();
  }
#endif
  registerCallback(clientCallback);
  if (mIsEseInitialized) {
    clientCallback->onStateChange_1_1(true, "NXP SE HAL init ok");
    return Void();
  }

  phNxpEse_setWtxCountLimit(OsuHalExtn::getInstance().getOSUMaxWtxCount());
  status = phNxpEse_open(initParams);
  if (status == ESESTATUS_SUCCESS || ESESTATUS_BUSY == status) {
    ESESTATUS initStatus = ESESTATUS_SUCCESS;
    ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(0)) {
      initStatus = phNxpEse_init(initParams);
      if (initStatus == ESESTATUS_SUCCESS) {
        /*update OS mode during first init*/
        if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9)
          IS_OSU_MODE(OsuHalExtn::getInstance().INIT, 0);

        if (ESESTATUS_SUCCESS == phNxpEse_ResetEndPoint_Cntxt(0)) {
          LOG(INFO) << "ESE SPI init complete!!!";
          mIsInitDone = true;
        }
        deInitStatus = phNxpEse_deInit();
        if (ESESTATUS_SUCCESS != deInitStatus) mIsInitDone = false;
      }
    }
    status = phNxpEse_close(deInitStatus);
    /*Enable terminal post recovery(i.e. close success) from transmit failure */
    if (status == ESESTATUS_SUCCESS &&
        (initStatus == ESESTATUS_TRANSCEIVE_FAILED ||
         initStatus == ESESTATUS_FAILED)) {
      if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9)
        IS_OSU_MODE(OsuHalExtn::getInstance().INIT, 0);
      mIsInitDone = true;
    }
  }
  phNxpEse_setWtxCountLimit(RESET_APP_WTX_COUNT);
  if (status == ESESTATUS_SUCCESS && mIsInitDone) {
    mHasPriorityAccess = phNxpEse_isPriorityAccessEnabled();
    mMaxChannelCount = getMaxChannelCnt();
    mOpenedChannels.resize(mMaxChannelCount, false);
    clientCallback->onStateChange_1_1(true, "NXP SE HAL init ok");
  } else {
    LOG(ERROR) << "eSE-Hal Init failed";
    clientCallback->onStateChange_1_1(false, "NXP SE HAL init failed");
    // remove it from the list of registered callbacks
    // Client anyway has to call the init again
    unregisterCallback(clientCallback);
  }
  return Void();
}

Return<void> SecureElement::getAtr(getAtr_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  LOG(ERROR) << "Processing ATR.....";
  phNxpEse_data atrData;
  hidl_vec<uint8_t> response;
  ESESTATUS status = ESESTATUS_FAILED;
  bool mIsSeHalInitDone = false;

  // In dedicated mode getATR not allowed
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_8_9) &&
      (IS_OSU_MODE(OsuHalExtn::getInstance().GETATR))) {
    LOG(ERROR) << "%s: Not allowed in dedicated mode!!!" << __func__;
    _hidl_cb(response);
    return Void();
  }

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      _hidl_cb(response); /*Return with empty Vector*/
      return Void();
    } else {
      mIsSeHalInitDone = true;
    }
  }
  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed";
  }
  status = phNxpEse_getAtr(&atrData);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_getAtr failed";
    _hidl_cb(response); /*Return with empty Vector*/
    return Void();
  } else {
    response.resize(atrData.len);
    memcpy(&response[0], atrData.p_data, atrData.len);
  }

  status = phNxpEse_ResetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed";
  }

  if (status != ESESTATUS_SUCCESS) {
    LOG(INFO) << StringPrintf("ATR Data[BytebyByte]=Look below for %d bytes",
                              atrData.len);
    for (auto i = response.begin(); i != response.end(); ++i)
      LOG(INFO) << StringPrintf("0x%x\t", *i);
  }

  _hidl_cb(response);
  if (atrData.p_data != NULL) {
    phNxpEse_free(atrData.p_data);
  }
  if (mIsSeHalInitDone) {
    if (SecureElementStatus::SUCCESS != seHalDeInit())
      LOG(ERROR) << "phNxpEse_getAtr seHalDeInit failed";
    mIsEseInitialized = false;
    mIsSeHalInitDone = false;
  }
  return Void();
}

Return<bool> SecureElement::isCardPresent() { return true; }

Return<void> SecureElement::transmit(const hidl_vec<uint8_t>& data,
                                     transmit_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  ESESTATUS status = ESESTATUS_FAILED;
  hidl_vec<uint8_t> result;
  phNxpEse_memset(&gsTxRxBuffer.cmdData, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&gsTxRxBuffer.rspData, 0x00, sizeof(phNxpEse_data));
  gsTxRxBuffer.cmdData.len = (uint32_t)data.size();
  gsTxRxBuffer.cmdData.p_data =
      (uint8_t*)phNxpEse_memalloc(data.size() * sizeof(uint8_t));
  if (NULL == gsTxRxBuffer.cmdData.p_data) {
    LOG(ERROR) << "transmit failed to allocate the Memory!!!";
    /*Return empty hidl_vec*/
    _hidl_cb(result);
    return Void();
  }
  if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9) {
    OsuHalExtn::OsuApduMode mode = IS_OSU_MODE(
        data, OsuHalExtn::getInstance().TRANSMIT, &gsTxRxBuffer.cmdData);
    if (mode == OsuHalExtn::getInstance().OSU_BLOCKED_MODE) {
      LOG(ERROR) << "Not allowed in dedicated mode!!!";
      /*Return empty hidl_vec*/
      _hidl_cb(result);
      return Void();
    } else if (mode == OsuHalExtn::getInstance().OSU_RST_MODE) {
      uint8_t sw[2] = {0x90, 0x00};
      result.resize(sizeof(sw));
      memcpy(&result[0], sw, sizeof(sw));
      _hidl_cb(result);
      return Void();
    }
  } else {
    memcpy(gsTxRxBuffer.cmdData.p_data, data.data(), gsTxRxBuffer.cmdData.len);
  }
  LOG(INFO) << "Acquired lock for SPI";
  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
  status = phNxpEse_Transceive(&gsTxRxBuffer.cmdData, &gsTxRxBuffer.rspData);

  if (status == ESESTATUS_SUCCESS) {
    result.resize(gsTxRxBuffer.rspData.len);
    memcpy(&result[0], gsTxRxBuffer.rspData.p_data, gsTxRxBuffer.rspData.len);
  } else if (status == ESESTATUS_INVALID_RECEIVE_LENGTH) {
    uint8_t respBuf[] = {INVALID_LEN_SW1, INVALID_LEN_SW2};
    result.resize(sizeof(respBuf));
    memcpy(&result[0], respBuf, sizeof(respBuf));
  } else {
    LOG(ERROR) << "transmit failed!!!";
  }
  status = phNxpEse_ResetEndPoint_Cntxt(0);
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

Return<void> SecureElement::openLogicalChannel(const hidl_vec<uint8_t>& aid,
                                               uint8_t p2,
                                               openLogicalChannel_cb _hidl_cb) {
  AutoMutex guard(seHalLock);
  hidl_vec<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};

  LogicalChannelResponse resApduBuff;
  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));
  if (aid.size() > MAX_AID_LENGTH) {
    LOG(ERROR) << "%s: AID out of range!!!" << __func__;
    _hidl_cb(resApduBuff, SecureElementStatus::FAILED);
    return Void();
  }

  /*
   * Basic channel & reserved channel if any is removed
   * from count
   */
  uint8_t maxLogicalChannelSupported =
      mMaxChannelCount - getReserveChannelCnt(aid) - 1;

  uint8_t openedLogicalChannelCount = mOpenedchannelCount;
  if (mOpenedChannels[0]) openedLogicalChannelCount--;

  if (openedLogicalChannelCount >= maxLogicalChannelSupported) {
    LOG(ERROR) << StringPrintf("%s: Reached Max supported(%d) Logical Channel",
                               __func__, openedLogicalChannelCount);
    _hidl_cb(resApduBuff, SecureElementStatus::CHANNEL_NOT_AVAILABLE);
    return Void();
  }

  LOG(INFO) << "Acquired the lock from SPI openLogicalChannel";

  // In dedicated mode openLogical not allowed
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_6_2) &&
      (IS_OSU_MODE(OsuHalExtn::getInstance().OPENLOGICAL))) {
    LOG(ERROR) << "%s: Not allowed in dedicated mode!!!" << __func__;
    _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
    return Void();
  }
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      _hidl_cb(resApduBuff, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = getMaxChannelCnt();
    mOpenedChannels.resize(mMaxChannelCount, false);
  }

  SecureElementStatus sestatus = SecureElementStatus::IOERROR;
  ESESTATUS status = ESESTATUS_FAILED;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));

  phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = (uint32_t)manageChannelCommand.size();
  cmdApdu.p_data = (uint8_t*)phNxpEse_memalloc(manageChannelCommand.size() *
                                               sizeof(uint8_t));
  memcpy(cmdApdu.p_data, manageChannelCommand.data(), cmdApdu.len);

  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
  status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
  if (status != ESESTATUS_SUCCESS) {
    resApduBuff.channelNumber = 0xff;
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
      SecureElementStatus deInitStatus = seHalDeInit();
      if (deInitStatus != SecureElementStatus::SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
      }
    }
    /*If manageChanle is failed in any of above cases
    send the callback and return*/
    status = phNxpEse_ResetEndPoint_Cntxt(0);
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
    /* update CLA byte accoridng to GP spec Table 11-12*/
    cpdu.cla =
        0x40 + (resApduBuff.channelNumber - 4); /* Class of instruction */
  } else if ((resApduBuff.channelNumber > 0x00) &&
             (resApduBuff.channelNumber < 0x04)) {
    /* update CLA byte accoridng to GP spec Table 11-11*/
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
  cpdu.lc = (uint16_t)aid.size();
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

    if (rpdu.sw1 == SW1_BYTES_REMAINING) {
      sestatus =
          getResponseInternal(cpdu.cla, rpdu, resApduBuff.selectResponse);
      if (sestatus != SecureElementStatus::SUCCESS) {
        LOG(ERROR) << "%s: getResponseInternal Failed" << __func__;
      }
    }

    /*Status is success*/
    if ((rpdu.sw1 == 0x90 && rpdu.sw2 == 0x00) || (rpdu.sw1 == 0x62) ||
        (rpdu.sw1 == 0x63)) {
      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) ||
             (rpdu.sw1 == 0x69 && (rpdu.sw2 == 0x99 || rpdu.sw2 == 0x85))) {
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
  status = phNxpEse_ResetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
  }
  _hidl_cb(resApduBuff, sestatus);
  phNxpEse_free(cpdu.pdata);
  phNxpEse_free(rpdu.pdata);

  return Void();
}

Return<void> SecureElement::openBasicChannel(const hidl_vec<uint8_t>& aid,
                                             uint8_t p2,
                                             openBasicChannel_cb _hidl_cb) {
  hidl_vec<uint8_t> result;
  if (aid.size() > MAX_AID_LENGTH) {
    LOG(ERROR) << "%s: AID out of range!!!" << __func__;
    _hidl_cb(result, SecureElementStatus::FAILED);
    return Void();
  }
  AutoMutex guard(seHalLock);
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;
  hidl_vec<uint8_t> ls_aid = {0xA0, 0x00, 0x00, 0x03, 0x96, 0x41, 0x4C,
                              0x41, 0x01, 0x43, 0x4F, 0x52, 0x01};

  LOG(ERROR) << "Acquired the lock in SPI openBasicChannel";
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_8_9) &&
      IS_OSU_MODE(aid, OsuHalExtn::getInstance().OPENBASIC) ==
          OsuHalExtn::OSU_PROP_MODE) {
    uint8_t sw[2] = {0x90, 0x00};
    result.resize(sizeof(sw));
    memcpy(&result[0], sw, 2);
    if (mIsEseInitialized) {
      /*Close existing sessions if any to start dedicated OSU Mode
       * with OSU specific settings in TZ/TEE*/
      if (seHalDeInit() != SecureElementStatus::SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
        _hidl_cb(result, SecureElementStatus::IOERROR);
        return Void();
      }
    }
    phNxpEse_setWtxCountLimit(OsuHalExtn::getInstance().getOSUMaxWtxCount());
    ESESTATUS status = ESESTATUS_FAILED;
    uint8_t retry = 0;
    do {
      /*For Reset Recovery*/
      status = seHalInit();
    } while (status != ESESTATUS_SUCCESS && retry++ < 1);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      phNxpEse_setWtxCountLimit(RESET_APP_WTX_COUNT);
      _hidl_cb(result, SecureElementStatus::IOERROR);
      return Void();
    }
    if (phNxpEse_doResetProtection(true) != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: Enable Reset Protection Failed!!!" << __func__;
      _hidl_cb(result, SecureElementStatus::FAILED);
    } else {
      _hidl_cb(result, SecureElementStatus::SUCCESS);
    }
    return Void();
  }

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      _hidl_cb(result, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9) {
    phNxpEse_data atrData;
    if (phNxpEse_getAtr(&atrData) != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_getAtr failed";
    }
    if (atrData.p_data != NULL) {
      phNxpEse_free(atrData.p_data);
    }

    if (phNxpEse_GetOsMode() == OSU_MODE) {
      if (mOpenedchannelCount == 0) {
        if (seHalDeInit() != SecureElementStatus::SUCCESS) {
          LOG(INFO) << "seDeInit Failed";
        }
      }
      _hidl_cb(result, SecureElementStatus::IOERROR);
      return Void();
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = getMaxChannelCnt();
    mOpenedChannels.resize(mMaxChannelCount, false);
  }
  phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
  phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));

  cpdu.cla = 0x00; /* Class of instruction */
  cpdu.ins = 0xA4; /* Instruction code */
  cpdu.p1 = 0x04;  /* Instruction parameter 1 */
  cpdu.p2 = p2;    /* Instruction parameter 2 */
  cpdu.lc = (uint16_t)aid.size();
  cpdu.le_type = 0x01;
  cpdu.pdata = (uint8_t*)phNxpEse_memalloc(aid.size() * sizeof(uint8_t));
  memcpy(cpdu.pdata, aid.data(), cpdu.lc);
  cpdu.le = 256;

  rpdu.len = 0x02;
  rpdu.pdata = (uint8_t*)phNxpEse_memalloc(cpdu.le * sizeof(uint8_t));

  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
  }
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
    if (rpdu.sw1 == SW1_BYTES_REMAINING) {
      sestatus = getResponseInternal(cpdu.cla, rpdu, result);
      if (sestatus != SecureElementStatus::SUCCESS) {
        LOG(ERROR) << "%s: getResponseInternal Failed " << __func__;
      }
    }

    /*Status is success*/
    if (((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) || (rpdu.sw1 == 0x62) ||
        (rpdu.sw1 == 0x63)) {
      /*Set basic channel reference if it is not set */
      if (!mOpenedChannels[0]) {
        mOpenedChannels[0] = true;
        mOpenedchannelCount++;
      }

      sestatus = SecureElementStatus::SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) ||
             (rpdu.sw1 == 0x69 && (rpdu.sw2 == 0x99 || rpdu.sw2 == 0x85))) {
      sestatus = SecureElementStatus::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = SecureElementStatus::UNSUPPORTED_OPERATION;
    } else {
      sestatus = SecureElementStatus::FAILED;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(0);
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

Return<SecureElementStatus> SecureElement::internalCloseChannel(
    uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  LOG(ERROR) << "Acquired the lock in SPI internalCloseChannel";
  LOG(INFO) << StringPrintf("mMaxChannelCount = %d, Closing Channel = %d",
                            mMaxChannelCount, channelNumber);
  if (channelNumber >= mMaxChannelCount) {
    LOG(ERROR) << StringPrintf("invalid channel!!! %d", channelNumber);
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL) {
    phNxpEse_memset(&cpdu, 0x00, sizeof(phNxpEse_7816_cpdu_t));
    phNxpEse_memset(&rpdu, 0x00, sizeof(phNxpEse_7816_rpdu_t));
    cpdu.cla = channelNumber; /* Class of instruction */
    // For Suplementary Channel update CLA byte according to GP
    if ((channelNumber > 0x03) && (channelNumber < 0x14)) {
      /* update CLA byte accoridng to GP spec Table 11-12*/
      cpdu.cla = 0x40 + (channelNumber - 4); /* Class of instruction */
    }
    cpdu.ins = 0x70;          /* Instruction code */
    cpdu.p1 = 0x80;           /* Instruction parameter 1 */
    cpdu.p2 = channelNumber;  /* Instruction parameter 2 */
    cpdu.lc = 0x00;
    cpdu.le = 0x9000;
    status = phNxpEse_SetEndPoint_Cntxt(0);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    }
    status = phNxpEse_7816_Transceive(&cpdu, &rpdu);
    if (status == ESESTATUS_SUCCESS) {
      if ((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) {
        sestatus = SecureElementStatus::SUCCESS;
      }
    }
    status = phNxpEse_ResetEndPoint_Cntxt(0);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
  }
  if (channelNumber < mMaxChannelCount) {
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

Return<SecureElementStatus> SecureElement::closeChannel(uint8_t channelNumber) {
  AutoMutex guard(seHalLock);
  // Close internal allowed when not in dedicated Mode
  if ((GET_CHIP_OS_VERSION() >= OS_VERSION_8_9) ||
      (!IS_OSU_MODE(OsuHalExtn::getInstance().CLOSE, channelNumber))) {
    return internalCloseChannel(channelNumber);
  } else {
    /*Decrement channel count opened to
     * keep in sync with service */
    if (channelNumber < mMaxChannelCount) {
      if (mOpenedChannels[channelNumber]) {
        mOpenedChannels[channelNumber] = false;
        mOpenedchannelCount--;
      }
    }
    return SecureElementStatus::SUCCESS;
  }
}

void SecureElement::serviceDied(uint64_t /*cookie*/, const wp<IBase>& who) {
  LOG(ERROR) << "Remote client Died!!!";
  unregisterCallback(who.promote());
  if (mCallbacks.empty()) {
    LOG(ERROR) << "No alive registered client, resetting the state";
    mIsEseInitialized = false;
    if (seHalDeInit() != SecureElementStatus::SUCCESS) {
      LOG(ERROR) << "SE Deinit not successful";
    }
  } else {
    LOG(INFO) << "There are " << mCallbacks.size()
              << " alive registered clients left";
  }
}
ESESTATUS SecureElement::seHalInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_initParams initParams;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;
  initParams.fPtr_WtxNtf = SecureElement::NotifySeWaitExtension;

  status = phNxpEse_open(initParams);
  if (ESESTATUS_SUCCESS == status || ESESTATUS_BUSY == status) {
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(0) &&
        ESESTATUS_SUCCESS == (status = phNxpEse_init(initParams))) {
      if (ESESTATUS_SUCCESS == phNxpEse_ResetEndPoint_Cntxt(0)) {
        mIsEseInitialized = true;
        LOG(INFO) << "ESE SPI init complete!!!";
        return ESESTATUS_SUCCESS;
      }
    } else {
      LOG(INFO) << "ESE SPI init NOT successful";
      status = ESESTATUS_FAILED;
    }
    deInitStatus = phNxpEse_deInit();
    if (phNxpEse_close(deInitStatus) != ESESTATUS_SUCCESS) {
      LOG(INFO) << "ESE close not successful";
      status = ESESTATUS_FAILED;
    }
    mIsEseInitialized = false;
  }
  return status;
}

Return<SecureElementStatus> SecureElement::seHalDeInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  bool mIsDeInitDone = true;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    mIsDeInitDone = false;
  }
  deInitStatus = phNxpEse_deInit();
  if (ESESTATUS_SUCCESS != deInitStatus) mIsDeInitDone = false;
  status = phNxpEse_ResetEndPoint_Cntxt(0);
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
  mIsEseInitialized = false;
  for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
    mOpenedChannels[xx] = false;
  }
  mOpenedchannelCount = 0;

  return sestatus;
}
void SecureElement::notifyClients(bool connected, std::string reason) {
  LOG(INFO) << "Notifying current state to all " << mCallbacks.size()
            << " clients";

  for (auto it = mCallbacks.begin(); it != mCallbacks.end();) {
    auto ret = (*it)->onStateChange_1_1(connected, reason);
    if (!ret.isOk() && ret.isDeadObject()) {
      LOG(WARNING) << "client  is dead";
      it = mCallbacks.erase(it);
    } else {
      ++it;
    }
  }
}
Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
SecureElement::reset() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  SecureElementStatus sestatus = SecureElementStatus::FAILED;
  LOG(INFO) << __func__ << " Enter";
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << __func__ << " seHalInit Failed!!!";
    }
  }
  if (status == ESESTATUS_SUCCESS) {
    notifyClients(false, "reset the SE");
    status = phNxpEse_reset();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << __func__ << " SecureElement reset failed!!";
    } else {
      sestatus = SecureElementStatus::SUCCESS;
      if (mOpenedChannels.size() == 0x00) {
        mMaxChannelCount = getMaxChannelCnt();
        mOpenedChannels.resize(mMaxChannelCount, false);
      }
      for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      notifyClients(true, "SE initialized");
    }
  }
  LOG(ERROR) << __func__ << ": Exit";
  return sestatus;
}

static Return<::android::hardware::secure_element::V1_0::SecureElementStatus>
getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                    hidl_vec<uint8_t>& result) {
  SecureElementStatus sestatus = SecureElementStatus::SUCCESS;
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_data cmdApdu;
  phNxpEse_data rspApdu;
  uint16_t responseLen = rpdu.len;  // Response already copied
  uint8_t getRespLe = rpdu.sw2;     // Response pending to receive
  uint8_t getResponse[5] = {0x00, 0xC0, 0x00, 0x00, 0x00};

  getResponse[0] = cla;

  phNxpEse_memset(&cmdApdu, 0x00, sizeof(phNxpEse_data));

  cmdApdu.len = (uint32_t)sizeof(getResponse);
  cmdApdu.p_data = getResponse;

  do {
    // update GET response 61 xx(Le)
    getResponse[4] = getRespLe;

    phNxpEse_memset(&rspApdu, 0x00, sizeof(phNxpEse_data));

    status = phNxpEse_Transceive(&cmdApdu, &rspApdu);
    if (status != ESESTATUS_SUCCESS) {
      /*Transceive failed*/
      if (rspApdu.len > 0 && (rspApdu.p_data[rspApdu.len - 2] == 0x64 &&
                              rspApdu.p_data[rspApdu.len - 1] == 0xFF)) {
        sestatus = SecureElementStatus::IOERROR;
      } else {
        sestatus = SecureElementStatus::FAILED;
      }
      break;
    } else {
      uint32_t respLen = rspApdu.len;

      // skip 2 bytes in case of 61xx SW again
      if (rspApdu.p_data[respLen - 2] == SW1_BYTES_REMAINING) {
        respLen -= 2;
        getRespLe = rspApdu.p_data[respLen - 1];
      }
      // copy response chunk received
      result.resize(responseLen + respLen);
      memcpy(&result[responseLen], rspApdu.p_data, respLen);
      responseLen += respLen;
    }
  } while (rspApdu.p_data[rspApdu.len - 2] == SW1_BYTES_REMAINING);

  // Propagate SW as it is received from card
  if (sestatus == SecureElementStatus::SUCCESS) {
    rpdu.sw1 = rspApdu.p_data[rspApdu.len - 2];
    rpdu.sw2 = rspApdu.p_data[rspApdu.len - 1];
  } else {  // Other Failure cases update failure SW:64FF
    rpdu.sw1 = INVALID_LEN_SW1;
    rpdu.sw2 = INVALID_LEN_SW2;
  }

  return sestatus;
}

uint8_t SecureElement::getReserveChannelCnt(const hidl_vec<uint8_t>& aid) {
  const hidl_vec<uint8_t> weaverAid = {0xA0, 0x00, 0x00, 0x03,
                                       0x96, 0x10, 0x10};
  const hidl_vec<uint8_t> araAid = {0xA0, 0x00, 0x00, 0x01, 0x51,
                                    0x41, 0x43, 0x4C, 0x00};
  uint8_t reserveChannel = 0;
  // Check priority access enabled then only reserve channel
  if (mHasPriorityAccess && aid != weaverAid && aid != araAid) {
    // Exclude basic channel
    reserveChannel = 1;
  }
  return reserveChannel;
}

uint8_t SecureElement::getMaxChannelCnt() {
  /*
   * 1) SN1xx max channel supported 4.
   * 2) SN220 up to v2 max channel supported 5 (If priority access)
   *    otherwise 4 channel.
   * 3) SN220 v3 and higher shall be updated accordingly.
   */
  uint8_t cnt = 0;
  if (GET_CHIP_OS_VERSION() < OS_VERSION_6_2)
    cnt = NUM_OF_CH4;
  else if (GET_CHIP_OS_VERSION() == OS_VERSION_6_2)
    cnt = (mHasPriorityAccess ? NUM_OF_CH5 : NUM_OF_CH4);
  else
    cnt = NUM_OF_CH5;

  return cnt;
}

Return<void> SecureElement::debug(const hidl_handle& /* fd */,
                                  const hidl_vec<hidl_string>& /* options */) {
  LOG(INFO) << "\n SecureElement-SecureElement HAL MemoryLeak Info = \n"
            << ::android::GetUnreachableMemoryString(true, 10000).c_str();
  return Void();
}

}  // namespace implementation
}  // namespace V1_2
}  // namespace secure_element
}  // namespace hardware
}  // namespace android
