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
#include "SecureElement.h"

#include <log/log.h>

#include "phNfcStatus.h"
#include "phNxpEse_Apdu_Api.h"
#include "phNxpEse_Api.h"
/* Mutex to synchronize multiple transceive */
#include <memunreachable/memunreachable.h>

namespace aidl {
namespace android {
namespace hardware {
namespace secure_element {

#define DEFAULT_BASIC_CHANNEL 0x00
#define INVALID_LEN_SW1 0x64
#define INVALID_LEN_SW2 0xFF
#define SW1_BYTES_REMAINING 0x61
#define NUM_OF_CH4 0x04
#define NUM_OF_CH5 0x05

typedef struct gsTransceiveBuffer {
  phNxpEse_data cmdData;
  phNxpEse_data rspData;
  std::vector<uint8_t>* pRspDataBuff;
} sTransceiveBuffer_t;

static int getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                               std::vector<uint8_t>& result);
static sTransceiveBuffer_t gsTxRxBuffer;
static std::vector<uint8_t> gsRspDataBuff(256);
std::shared_ptr<ISecureElementCallback> SecureElement::mCb = nullptr;
AIBinder_DeathRecipient* clientDeathRecipient = nullptr;
std::vector<bool> SecureElement::mOpenedChannels;
static const std::vector<std::vector<uint8_t>> kWeaverAIDs = {
    {0xA0, 0x00, 0x00, 0x03, 0x96, 0x10, 0x10},  // Primary AID
    {0xA0, 0x00, 0x00, 0x03, 0x96, 0x54, 0x53, 0x00, 0x00, 0x00, 0x01, 0x00,
     0x23, 0x00, 0x00, 0x00},  // Alternate AID
};

static bool isWeaverApplet(std::vector<uint8_t> aid) {
  if (std::find(kWeaverAIDs.begin(), kWeaverAIDs.end(), aid) !=
      kWeaverAIDs.end()) {
    return true;
  }
  return false;
}

SecureElement::SecureElement()
    : mMaxChannelCount(0), mOpenedchannelCount(0), mIsEseInitialized(false) {}

void SecureElement::updateSeHalInitState(bool mstate) {
  mIsEseInitialized = mstate;
}
void OnDeath(void* cookie) {
  (void)cookie;
  LOG(ERROR) << " SecureElement serviceDied!!!";
  SecureElement* se = static_cast<SecureElement*>(cookie);
  se->updateSeHalInitState(false);
  if (se->seHalDeInit() != SESTATUS_SUCCESS) {
    LOG(ERROR) << "SE Deinit not successful";
  }
}

void SecureElement::NotifySeWaitExtension(phNxpEse_wtxState state) {
  if (state == WTX_ONGOING) {
    LOG(INFO) << "SecureElement::WTX ongoing";
  } else if (state == WTX_END) {
    LOG(INFO) << "SecureElement::WTX ended";
  }
}

ScopedAStatus SecureElement::init(
    const std::shared_ptr<ISecureElementCallback>& clientCallback) {
  LOG(INFO) << __func__ << " callback: " << clientCallback.get();
  if (!clientCallback) {
    return ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
  }

  mCb = clientCallback;
  ESESTATUS status = ESESTATUS_SUCCESS;
  bool mIsInitDone = false;
  phNxpEse_initParams initParams;
  gsTxRxBuffer.pRspDataBuff = &gsRspDataBuff;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;
  initParams.fPtr_WtxNtf = SecureElement::NotifySeWaitExtension;

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

  LOG(INFO) << "SecureElement::init called here";
  if (mIsEseInitialized) {
    mCb->onStateChange(true, "NXP SE HAL init ok");
    return ScopedAStatus::ok();
  }

  phNxpEse_setWtxCountLimit(OsuHalExtn::getInstance().getOSUMaxWtxCount());
  status = phNxpEse_open(initParams);
  if (status == ESESTATUS_SUCCESS || ESESTATUS_BUSY == status) {
    ESESTATUS initStatus = ESESTATUS_SUCCESS;
    ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
    if (ESESTATUS_SUCCESS == phNxpEse_SetEndPoint_Cntxt(0)) {
      initStatus = phNxpEse_init(initParams);
      if (initStatus == ESESTATUS_SUCCESS) {
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
    mCb->onStateChange(true, "NXP SE HAL init ok");
  } else {
    LOG(ERROR) << "eSE-Hal Init failed";
    mCb->onStateChange(false, "NXP SE HAL init failed");
  }
  return ScopedAStatus::ok();
}

ScopedAStatus SecureElement::getAtr(std::vector<uint8_t>* _aidl_return) {
  LOG(INFO) << __func__;

  AutoMutex guard(seHalLock);
  LOG(ERROR) << "Processing ATR.....";
  phNxpEse_data atrData;
  std::vector<uint8_t> response;
  ESESTATUS status = ESESTATUS_FAILED;
  bool mIsSeHalInitDone = false;

  // In dedicated mode getATR not allowed
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_6_2) &&
      (IS_OSU_MODE(OsuHalExtn::getInstance().GETATR))) {
    LOG(ERROR) << "%s: Not allowed in dedicated mode!!!" << __func__;
    *_aidl_return = response;
    return ndk::ScopedAStatus::ok();
  }

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      *_aidl_return = response; /*Return with empty Vector*/
      return ndk::ScopedAStatus::ok();
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
    *_aidl_return = response; /*Return with empty Vector*/
    return ndk::ScopedAStatus::ok();
  } else {
    response.resize(atrData.len);
    memcpy(&response[0], atrData.p_data, atrData.len);
  }

  status = phNxpEse_ResetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed";
  }

  if (status != ESESTATUS_SUCCESS) {
    ALOGD("ATR Data[BytebyByte]=Look below for %d bytes", atrData.len);
    for (auto i = response.begin(); i != response.end(); ++i)
      ALOGI("0x%x\t", *i);
  }

  *_aidl_return = std::move(response);
  if (atrData.p_data != NULL) {
    phNxpEse_free(atrData.p_data);
  }
  if (mIsSeHalInitDone) {
    if (SESTATUS_SUCCESS != seHalDeInit())
      LOG(ERROR) << "phNxpEse_getAtr seHalDeInit failed";
    mIsEseInitialized = false;
    mIsSeHalInitDone = false;
  }
  return ndk::ScopedAStatus::ok();
}

ScopedAStatus SecureElement::isCardPresent(bool* _aidl_return) {
  LOG(INFO) << __func__;
  *_aidl_return = true;
  return ScopedAStatus::ok();
}

ScopedAStatus SecureElement::transmit(const std::vector<uint8_t>& data,
                                      std::vector<uint8_t>* _aidl_return) {
  AutoMutex guard(seHalLock);
  ESESTATUS status = ESESTATUS_FAILED;
  std::vector<uint8_t> result;
  if (!mOpenedchannelCount) {
    // 0x69, 0x86 = COMMAND NOT ALLOWED
    uint8_t sw[2] = {0x69, 0x86};
    result.resize(sizeof(sw));
    memcpy(&result[0], sw, sizeof(sw));
    return ScopedAStatus::fromServiceSpecificError(CHANNEL_NOT_AVAILABLE);
  }

  phNxpEse_memset(&gsTxRxBuffer.cmdData, 0x00, sizeof(phNxpEse_data));
  phNxpEse_memset(&gsTxRxBuffer.rspData, 0x00, sizeof(phNxpEse_data));
  gsTxRxBuffer.cmdData.len = (uint32_t)data.size();
  gsTxRxBuffer.cmdData.p_data =
      (uint8_t*)phNxpEse_memalloc(data.size() * sizeof(uint8_t));
  if (NULL == gsTxRxBuffer.cmdData.p_data) {
    LOG(ERROR) << "transmit failed to allocate the Memory!!!";
    /*Return empty vec*/
    *_aidl_return = result;
    return ScopedAStatus::ok();
  }
  if (GET_CHIP_OS_VERSION() < OS_VERSION_8_9) {
    OsuHalExtn::OsuApduMode mode = IS_OSU_MODE(
        data, OsuHalExtn::getInstance().TRANSMIT, &gsTxRxBuffer.cmdData);
    if (mode == OsuHalExtn::getInstance().OSU_BLOCKED_MODE) {
      LOG(ERROR) << "Not allowed in dedicated mode!!!";
      /*Return empty vec*/
      *_aidl_return = result;
      return ScopedAStatus::ok();
    } else if (mode == OsuHalExtn::getInstance().OSU_RST_MODE) {
      uint8_t sw[2] = {0x90, 0x00};
      result.resize(sizeof(sw));
      memcpy(&result[0], sw, sizeof(sw));
      *_aidl_return = result;
      return ScopedAStatus::ok();
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

ScopedAStatus SecureElement::openLogicalChannel(
    const std::vector<uint8_t>& aid, int8_t p2,
    ::aidl::android::hardware::secure_element::LogicalChannelResponse*
        _aidl_return) {
  AutoMutex guard(seHalLock);
  std::vector<uint8_t> manageChannelCommand = {0x00, 0x70, 0x00, 0x00, 0x01};

  LogicalChannelResponse resApduBuff;
  resApduBuff.channelNumber = 0xff;
  memset(&resApduBuff, 0x00, sizeof(resApduBuff));
  if (aid.size() > MAX_AID_LENGTH) {
    LOG(ERROR) << "%s: AID out of range!!!" << __func__;
    *_aidl_return = resApduBuff;
    return ScopedAStatus::fromServiceSpecificError(FAILED);
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
    ALOGE("%s: Reached Max supported(%d) Logical Channel", __func__,
          openedLogicalChannelCount);
    *_aidl_return = resApduBuff;
    return ScopedAStatus::fromServiceSpecificError(CHANNEL_NOT_AVAILABLE);
  }

  LOG(INFO) << "Acquired the lock from SPI openLogicalChannel";

  // In dedicated mode openLogical not allowed
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_8_9) &&
      (IS_OSU_MODE(OsuHalExtn::getInstance().OPENLOGICAL))) {
    LOG(ERROR) << "%s: Not allowed in dedicated mode!!!" << __func__;
    *_aidl_return = resApduBuff;
    return ScopedAStatus::fromServiceSpecificError(IOERROR);
  }
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      *_aidl_return = resApduBuff;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
    }
  }

  if (mOpenedChannels.size() == 0x00) {
    mMaxChannelCount = getMaxChannelCnt();
    mOpenedChannels.resize(mMaxChannelCount, false);
  }

  int sestatus = ISecureElement::IOERROR;
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
      int deInitStatus = seHalDeInit();
      if (deInitStatus != SESTATUS_SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
      }
    }
    /*If manageChannel is failed in any of above cases
    send the callback and return*/
    status = phNxpEse_ResetEndPoint_Cntxt(0);
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

    if (rpdu.sw1 == SW1_BYTES_REMAINING) {
      sestatus =
          getResponseInternal(cpdu.cla, rpdu, resApduBuff.selectResponse);
      if (sestatus != SESTATUS_SUCCESS) {
        LOG(ERROR) << "%s: getResponseInternal Failed" << __func__;
      }
    }

    /*Status is success*/
    if ((rpdu.sw1 == 0x90 && rpdu.sw2 == 0x00) || (rpdu.sw1 == 0x62) ||
        (rpdu.sw1 == 0x63)) {
      sestatus = SESTATUS_SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) ||
             (rpdu.sw1 == 0x69 && (rpdu.sw2 == 0x99 || rpdu.sw2 == 0x85))) {
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
  status = phNxpEse_ResetEndPoint_Cntxt(0);
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

ScopedAStatus SecureElement::openBasicChannel(
    const std::vector<uint8_t>& aid, int8_t p2,
    std::vector<uint8_t>* _aidl_return) {
  std::vector<uint8_t> result;
  if (aid.size() > MAX_AID_LENGTH) {
    LOG(ERROR) << "%s: AID out of range!!!" << __func__;
    *_aidl_return = result;
    return ScopedAStatus::fromServiceSpecificError(FAILED);
  }
  AutoMutex guard(seHalLock);
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;
  std::vector<uint8_t> ls_aid = {0xA0, 0x00, 0x00, 0x03, 0x96, 0x41, 0x4C,
                                 0x41, 0x01, 0x43, 0x4F, 0x52, 0x01};

  if (mOpenedChannels[0]) {
    LOG(ERROR) << "openBasicChannel failed, channel already in use";
    *_aidl_return = result;
    return ScopedAStatus::fromServiceSpecificError(UNSUPPORTED_OPERATION);
  }

  LOG(ERROR) << "Acquired the lock in SPI openBasicChannel";
  if ((GET_CHIP_OS_VERSION() < OS_VERSION_8_9) &&
      IS_OSU_MODE(aid, OsuHalExtn::getInstance().OPENBASIC) ==
          OsuHalExtn::OSU_PROP_MODE) {
    uint8_t sw[2] = {0x90, 0x00};
    result.resize(sizeof(sw));
    memcpy(&result[0], sw, 2);
    if (mIsEseInitialized) {
      /* Close existing sessions if any to start dedicated OSU Mode
       * with OSU specific settings in TZ/TEE */
      if (seHalDeInit() != SESTATUS_SUCCESS) {
        LOG(INFO) << "seDeInit Failed";
        *_aidl_return = result;
        return ScopedAStatus::fromServiceSpecificError(IOERROR);
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
      *_aidl_return = result;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
    }
    if (phNxpEse_doResetProtection(true) != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: Enable Reset Protection Failed!!!" << __func__;
      *_aidl_return = result;
      return ScopedAStatus::fromServiceSpecificError(FAILED);
    } else {
      mOpenedChannels[0] = true;
      mOpenedchannelCount++;
      *_aidl_return = result;
      return ScopedAStatus::ok();
    }
  }

  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "%s: seHalInit Failed!!!" << __func__;
      *_aidl_return = result;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
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
        if (seHalDeInit() != SESTATUS_SUCCESS) {
          LOG(INFO) << "seDeInit Failed";
        }
      }
      *_aidl_return = result;
      return ScopedAStatus::fromServiceSpecificError(IOERROR);
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
    if (rpdu.sw1 == SW1_BYTES_REMAINING) {
      sestatus = getResponseInternal(cpdu.cla, rpdu, result);
      if (sestatus != SESTATUS_SUCCESS) {
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

      sestatus = SESTATUS_SUCCESS;
    }
    /*AID provided doesn't match any applet on the secure element*/
    else if ((rpdu.sw1 == 0x6A && rpdu.sw2 == 0x82) ||
             (rpdu.sw1 == 0x69 && (rpdu.sw2 == 0x99 || rpdu.sw2 == 0x85))) {
      sestatus = ISecureElement::NO_SUCH_ELEMENT_ERROR;
    }
    /*Operation provided by the P2 parameter is not permitted by the applet.*/
    else if (rpdu.sw1 == 0x6A && rpdu.sw2 == 0x86) {
      sestatus = ISecureElement::UNSUPPORTED_OPERATION;
    } else {
      sestatus = ISecureElement::FAILED;
    }
  }
  status = phNxpEse_ResetEndPoint_Cntxt(0);
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

int SecureElement::internalCloseChannel(uint8_t channelNumber) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  int sestatus = ISecureElement::FAILED;
  phNxpEse_7816_cpdu_t cpdu;
  phNxpEse_7816_rpdu_t rpdu;

  LOG(ERROR) << "Acquired the lock in SPI internalCloseChannel";
  ALOGD("mMaxChannelCount = %d, Closing Channel = %d", mMaxChannelCount,
        channelNumber);
  if (channelNumber >= mMaxChannelCount) {
    ALOGE("invalid channel!!! %d", channelNumber);
  } else if (channelNumber > DEFAULT_BASIC_CHANNEL &&
             mOpenedChannels[channelNumber]) {
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
    status = phNxpEse_SetEndPoint_Cntxt(0);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_SetEndPoint_Cntxt failed!!!";
    }
    status = phNxpEse_7816_Transceive(&cpdu, &rpdu);
    if (status == ESESTATUS_SUCCESS) {
      if ((rpdu.sw1 == 0x90) && (rpdu.sw2 == 0x00)) {
        sestatus = SESTATUS_SUCCESS;
      }
    }
    status = phNxpEse_ResetEndPoint_Cntxt(0);
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << "phNxpEse_ResetEndPoint_Cntxt failed!!!";
    }
  } else if (channelNumber == DEFAULT_BASIC_CHANNEL &&
             mOpenedChannels[channelNumber]) {
    sestatus = SESTATUS_SUCCESS;
  }
  if (channelNumber < mMaxChannelCount) {
    if (mOpenedChannels[channelNumber]) {
      mOpenedChannels[channelNumber] = false;
      mOpenedchannelCount--;
    }
  }
  /*If there are no channels remaining close secureElement*/
  if (mOpenedchannelCount == 0) {
    if (SESTATUS_SUCCESS != seHalDeInit()) {
      LOG(ERROR) << "internalCloseChannel seHalDeInit failed";
    }
  } else {
    sestatus = SESTATUS_SUCCESS;
  }
  return sestatus;
}

ScopedAStatus SecureElement::closeChannel(int8_t channelNumber) {
  AutoMutex guard(seHalLock);
  int sestatus;
  // Close internal allowed when not in dedicated Mode
  if ((GET_CHIP_OS_VERSION() >= OS_VERSION_8_9) ||
      (!IS_OSU_MODE(OsuHalExtn::getInstance().CLOSE, channelNumber))) {
    sestatus = internalCloseChannel(channelNumber);
  } else {
    /*Decrement channel count opened to
     * keep in sync with service */
    if (channelNumber < mMaxChannelCount) {
      if (mOpenedChannels[channelNumber]) {
        mOpenedChannels[channelNumber] = false;
        mOpenedchannelCount--;
      }
    }
    sestatus = SESTATUS_SUCCESS;
  }
  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
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

int SecureElement::seHalDeInit() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  bool mIsDeInitDone = true;
  int sestatus = ISecureElement::FAILED;
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
    sestatus = SESTATUS_SUCCESS;
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

ScopedAStatus SecureElement::reset() {
  LOG(INFO) << __func__;
  ESESTATUS status = ESESTATUS_SUCCESS;
  int sestatus = ISecureElement::FAILED;
  LOG(INFO) << __func__ << " Enter";
  if (!mIsEseInitialized) {
    ESESTATUS status = seHalInit();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << __func__ << " seHalInit Failed!!!";
    }
  }
  if (status == ESESTATUS_SUCCESS) {
    mCb->onStateChange(false, "reset");
    status = phNxpEse_reset();
    if (status != ESESTATUS_SUCCESS) {
      LOG(ERROR) << __func__ << " SecureElement reset failed!!";
    } else {
      sestatus = SESTATUS_SUCCESS;
      if (mOpenedChannels.size() == 0x00) {
        mMaxChannelCount = getMaxChannelCnt();
        mOpenedChannels.resize(mMaxChannelCount, false);
      }
      for (uint8_t xx = 0; xx < mMaxChannelCount; xx++) {
        mOpenedChannels[xx] = false;
      }
      mOpenedchannelCount = 0;
      mCb->onStateChange(true, "reset");
    }
  }
  LOG(ERROR) << __func__ << ": Exit";
  return sestatus == SESTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(sestatus);
}

static int getResponseInternal(uint8_t cla, phNxpEse_7816_rpdu_t& rpdu,
                               std::vector<uint8_t>& result) {
  int sestatus = SESTATUS_SUCCESS;
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
        sestatus = ISecureElement::IOERROR;
      } else {
        sestatus = ISecureElement::FAILED;
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
  if (sestatus == SESTATUS_SUCCESS) {
    rpdu.sw1 = rspApdu.p_data[rspApdu.len - 2];
    rpdu.sw2 = rspApdu.p_data[rspApdu.len - 1];
  } else {  // Other Failure cases update failure SW:64FF
    rpdu.sw1 = INVALID_LEN_SW1;
    rpdu.sw2 = INVALID_LEN_SW2;
  }

  return sestatus;
}

uint8_t SecureElement::getReserveChannelCnt(const std::vector<uint8_t>& aid) {
  const std::vector<uint8_t> araAid = {0xA0, 0x00, 0x00, 0x01, 0x51,
                                       0x41, 0x43, 0x4C, 0x00};
  uint8_t reserveChannel = 0;
  // Check priority access enabled then only reserve channel
  if (mHasPriorityAccess && !isWeaverApplet(aid) && aid != araAid) {
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

}  // namespace secure_element
}  // namespace hardware
}  // namespace android
}  // namespace aidl
