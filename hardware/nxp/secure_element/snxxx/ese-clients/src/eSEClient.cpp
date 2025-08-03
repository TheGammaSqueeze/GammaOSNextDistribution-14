/******************************************************************************
 *
 *  Copyright 2018-2020, 2023 NXP
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

#ifdef NXP_BOOTTIME_UPDATE
#include "eSEClient.h"

#include <IChannel.h>
#include <JcDnld.h>
#include <LsClient.h>
#include <aidl/vendor/nxp/nxpnfc_aidl/INxpNfc.h>
#include <dirent.h>
#include <ese_config.h>
#include <log/log.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vendor/nxp/nxpnfc/2.0/INxpNfc.h>

#include "NfcAdaptation.h"
#include "NxpEse.h"
#include "hal_nxpese.h"
#include "phNxpEse_Apdu_Api.h"
#include "phNxpEse_Spm.h"

using android::sp;
using android::hardware::hidl_vec;
using android::hardware::Void;
using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
using INxpNfc = vendor::nxp::nxpnfc::V2_0::INxpNfc;
using INxpNfcAidl = ::aidl::vendor::nxp::nxpnfc_aidl::INxpNfc;

std::string NXPNFC_AIDL_HAL_SERVICE_NAME =
    "vendor.nxp.nxpnfc_aidl.INxpNfc/default";

sp<INxpNfc> mHalNxpNfc = nullptr;
std::shared_ptr<INxpNfcAidl> mAidlHalNxpNfc = nullptr;

void seteSEClientState(uint8_t state);

IChannel_t Ch;
se_extns_entry se_intf;
void* eSEClientUpdate_ThreadHandler(void* data);
void* eSEClientUpdate_Thread(void* data);
void* eSEUpdate_SE_SeqHandler(void* data);
void eSEClientUpdate_Thread();
SESTATUS ESE_ChannelInit(IChannel* ch);
SESTATUS handleJcopOsDownload();
void* LSUpdate_Thread(void* data);
uint8_t performLSUpdate();
SESTATUS initializeEse(phNxpEse_initMode mode, SEDomainID Id);
ese_update_state_t ese_update = ESE_UPDATE_COMPLETED;
SESTATUS eSEUpdate_SeqHandler();
int16_t SE_Open() { return SESTATUS_SUCCESS; }

void SE_Reset() { phNxpEse_coldReset(); }

bool SE_Transmit(uint8_t* xmitBuffer, int32_t xmitBufferSize,
                 uint8_t* recvBuffer, int32_t recvBufferMaxSize,
                 int32_t& recvBufferActualSize, int32_t timeoutMillisec) {
  phNxpEse_data cmdData;
  phNxpEse_data rspData;

  cmdData.len = xmitBufferSize;
  cmdData.p_data = xmitBuffer;

  recvBufferMaxSize++;
  timeoutMillisec++;
  if (phNxpEse_Transceive(&cmdData, &rspData) != ESESTATUS_SUCCESS) {
    ALOGE("%s: Ese Transceive failed", __FUNCTION__);
  }
  recvBufferActualSize = rspData.len;

  if (rspData.p_data != NULL && rspData.len) {
    memcpy(&recvBuffer[0], rspData.p_data, rspData.len);
  }

  ALOGE("%s: size = 0x%x ", __FUNCTION__, recvBufferActualSize);
  return true;
}

void SE_JcopDownLoadReset() { phNxpEse_resetJcopUpdate(); }

bool SE_Close(int16_t mHandle) {
  if (mHandle != 0)
    return true;
  else
    return false;
}
uint8_t SE_getInterfaceInfo() { return INTF_SE; }

/***************************************************************************
**
** Function:        checkEseClientUpdate
**
** Description:     Check the initial condition
                    and interface for eSE Client update for LS and JCOP download
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void checkEseClientUpdate() {
  ALOGD("%s enter:  ", __func__);
  checkeSEClientRequired(ESE_INTF_SPI);
  se_intf.isJcopUpdateRequired = getJcopUpdateRequired();
  se_intf.isLSUpdateRequired = getLsUpdateRequired();
  se_intf.sJcopUpdateIntferface = getJcopUpdateIntf();
  se_intf.sLsUpdateIntferface = getLsUpdateIntf();
  if ((se_intf.isJcopUpdateRequired && se_intf.sJcopUpdateIntferface) ||
      (se_intf.isLSUpdateRequired && se_intf.sLsUpdateIntferface))
    seteSEClientState(ESE_UPDATE_STARTED);
}

/***************************************************************************
**
** Function:        perform_eSEClientUpdate
**
** Description:     Perform LS and JCOP download during hal service init
**
** Returns:         SUCCESS / SESTATUS_FAILED
**
*******************************************************************************/
SESTATUS perform_eSEClientUpdate() {
  ALOGD("%s enter:  ", __func__);

  eSEClientUpdate_Thread();
  return SESTATUS_SUCCESS;
}

SESTATUS ESE_ChannelInit(IChannel* ch) {
  ch->open = SE_Open;
  ch->close = SE_Close;
  ch->transceive = SE_Transmit;
  ch->transceiveRaw = SE_Transmit;
  ch->doeSE_Reset = SE_Reset;
  ch->doeSE_JcopDownLoadReset = SE_JcopDownLoadReset;
  ch->getInterfaceInfo = SE_getInterfaceInfo;
  return SESTATUS_SUCCESS;
}

/*******************************************************************************
**
** Function:        eSEClientUpdate_Thread
**
** Description:     Perform eSE update
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void eSEClientUpdate_Thread() {
  pthread_t thread;
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
  if (pthread_create(&thread, &attr, &eSEClientUpdate_ThreadHandler, NULL) !=
      0) {
    ALOGD("Thread creation failed");
  } else {
    ALOGD("Thread creation success");
  }
  pthread_attr_destroy(&attr);
}
/*******************************************************************************
**
** Function:        eSEClientUpdate_Thread
**
** Description:     Perform eSE update
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void eSEClientUpdate_SE_Thread() {
  pthread_t thread;
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
  if (pthread_create(&thread, &attr, &eSEUpdate_SE_SeqHandler, NULL) != 0) {
    ALOGD("Thread creation failed");
  } else {
    ALOGD("Thread creation success");
  }
  pthread_attr_destroy(&attr);
}
/*******************************************************************************
**
** Function:        eSEClientUpdate_ThreadHandler
**
** Description:     Perform JCOP Download
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void* eSEUpdate_SE_SeqHandler(void* data) {
  (void)data;
  ALOGD("%s Enter\n", __func__);
  eSEUpdate_SeqHandler();
  ALOGD("%s Exit eSEUpdate_SE_SeqHandler\n", __func__);
  return NULL;
}
/*******************************************************************************
**
** Function:        eSEClientUpdate_ThreadHandler
**
** Description:     Perform JCOP Download
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void* eSEClientUpdate_ThreadHandler(void* data) {
  (void)data;
  int cnt = 0;

  ALOGD("%s Enter\n", __func__);
  if (mAidlHalNxpNfc == nullptr) {
    do {
      ::ndk::SpAIBinder binder(
          AServiceManager_checkService(NXPNFC_AIDL_HAL_SERVICE_NAME.c_str()));
      mAidlHalNxpNfc = INxpNfcAidl::fromBinder(binder);
      if (!mAidlHalNxpNfc) {
        usleep(100 * 1000);
        cnt++;
      }
    } while (((mAidlHalNxpNfc == nullptr) && (cnt < 3)));
  }

  if (mAidlHalNxpNfc) {
    ALOGD("Boot Time Update not supported for mAidlHalNxpNfc.");
    ALOGD("%s Exit\n", __func__);
    pthread_exit(NULL);
    return NULL;
  } else {
    mHalNxpNfc = INxpNfc::tryGetService();
    if (mHalNxpNfc == nullptr) ALOGD(": Failed to retrieve the NXP NFC HAL!");
    if (mHalNxpNfc != nullptr) {
      ALOGD("INxpNfc::getService() returned %p (%s)", mHalNxpNfc.get(),
            (mHalNxpNfc->isRemote() ? "remote" : "local"));
    }

    if (mHalNxpNfc != nullptr) {
      if (!se_intf.isJcopUpdateRequired && mHalNxpNfc->isJcopUpdateRequired()) {
        se_intf.isJcopUpdateRequired = true;
        ALOGD(" se_intf.isJcopUpdateRequired = %d",
              se_intf.isJcopUpdateRequired);
      }
      if (!se_intf.isLSUpdateRequired && mHalNxpNfc->isLsUpdateRequired()) {
        se_intf.isLSUpdateRequired = true;
        ALOGD("se_intf.isLSUpdateRequired = %d", se_intf.isLSUpdateRequired);
      }
    }
  }

  if (se_intf.isJcopUpdateRequired) {
    if (se_intf.sJcopUpdateIntferface == ESE_INTF_NFC) {
      seteSEClientState(ESE_JCOP_UPDATE_REQUIRED);
      return NULL;
    } else if (se_intf.sJcopUpdateIntferface == ESE_INTF_SPI) {
      seteSEClientState(ESE_JCOP_UPDATE_REQUIRED);
    }
  }

  if ((ESE_JCOP_UPDATE_REQUIRED != ese_update) &&
      (se_intf.isLSUpdateRequired)) {
    if (se_intf.sLsUpdateIntferface == ESE_INTF_NFC) {
      seteSEClientState(ESE_LS_UPDATE_REQUIRED);
      return NULL;
    } else if (se_intf.sLsUpdateIntferface == ESE_INTF_SPI) {
      seteSEClientState(ESE_LS_UPDATE_REQUIRED);
    }
  }

  if ((ese_update == ESE_JCOP_UPDATE_REQUIRED) ||
      (ese_update == ESE_LS_UPDATE_REQUIRED))
    eSEUpdate_SeqHandler();

  ALOGD("%s Exit eSEClientUpdate_Thread\n", __func__);
  return NULL;
}

/*******************************************************************************
**
** Function:        handleJcopOsDownload
**
** Description:     Perform JCOP update
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
SESTATUS handleJcopOsDownload() {
  SESTATUS status = SESTATUS_FAILED;
  uint8_t retstat;
  status = initializeEse(ESE_MODE_OSU, ESE);
  if (status == SESTATUS_SUCCESS) {
    retstat = JCDNLD_Init(&Ch);
    if (retstat != STATUS_SUCCESS) {
      ALOGE("%s: JCDND initialization failed", __FUNCTION__);
      if (phNxpEse_ResetEndPoint_Cntxt(0) != ESESTATUS_SUCCESS) {
        ALOGE("%s: Reset SE EndPoint failed", __FUNCTION__);
      }
      phNxpEse_close(ESESTATUS_SUCCESS);
      return status;
    } else {
      retstat = JCDNLD_StartDownload();
      if (retstat != SESTATUS_SUCCESS) {
        ALOGE("%s: JCDNLD_StartDownload failed", __FUNCTION__);
      }
    }
    JCDNLD_DeInit();
    if (phNxpEse_ResetEndPoint_Cntxt(0) != ESESTATUS_SUCCESS) {
      ALOGE("%s: Reset SE EndPoint failed", __FUNCTION__);
    }
    phNxpEse_close(ESESTATUS_SUCCESS);
  }
  status = SESTATUS_SUCCESS;
  return status;
}

/*******************************************************************************
**
** Function:        performLSUpdate
**
** Description:     Perform LS update
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
uint8_t performLSUpdate() {
  const char* SEterminal = "eSEx";
  bool ret = false;
  char terminalID[5];
  uint8_t status = SESTATUS_FAILED;
  bool isSEPresent = false;
  bool isVISOPresent = false;
  ret = geteSETerminalId(terminalID);
  ALOGI("performLSUpdate Terminal val = %s", terminalID);
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    isSEPresent = true;
  }
  ret = geteUICCTerminalId(terminalID);
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    isVISOPresent = true;
  }
  seteSEClientState(ESE_UPDATE_STARTED);
  if (isSEPresent) {
    ALOGE("%s:On eSE domain ", __FUNCTION__);
    status = initializeEse(ESE_MODE_NORMAL, ESE);
    ALOGE("%s:On eSE domain ", __FUNCTION__);
    if (status == SESTATUS_SUCCESS) {
      status = performLSDownload(&Ch);
      if (phNxpEse_ResetEndPoint_Cntxt(ESE) != ESESTATUS_SUCCESS) {
        ALOGE("%s: Reset SE EndPoint failed", __FUNCTION__);
      }
    }
    phNxpEse_close(ESESTATUS_SUCCESS);
  }
  if (isVISOPresent) {
    ALOGE("%s:On eUICC domain ", __FUNCTION__);
    status = initializeEse(ESE_MODE_NORMAL, EUICC);
    if (status == SESTATUS_SUCCESS) {
      status = performLSDownload(&Ch);
      if (phNxpEse_ResetEndPoint_Cntxt(EUICC) != ESESTATUS_SUCCESS) {
        ALOGE("%s: Reset SE EndPoint failed", __FUNCTION__);
      }
    }
    phNxpEse_close(ESESTATUS_SUCCESS);
  }
  return status;
}

/*******************************************************************************
**
** Function:        initializeEse
**
** Description:     Open & Initialize libese
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
SESTATUS initializeEse(phNxpEse_initMode mode, SEDomainID Id) {
  uint8_t retstat;
  SESTATUS status = SESTATUS_FAILED;
  phNxpEse_initParams initParams;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));

  initParams.initMode = mode;
  ALOGE("%s: Mode = %d", __FUNCTION__, mode);
  retstat = phNxpEse_open(initParams);
  if (retstat != ESESTATUS_SUCCESS) {
    return status;
  }
  retstat = phNxpEse_SetEndPoint_Cntxt(Id);
  if (retstat != ESESTATUS_SUCCESS) {
    ALOGE("%s: Set SE EndPoint failed", __FUNCTION__);
  }
  retstat = phNxpEse_init(initParams);
  if (retstat != ESESTATUS_SUCCESS) {
    if (phNxpEse_ResetEndPoint_Cntxt(Id) != ESESTATUS_SUCCESS) {
      ALOGE("%s: Reset SE EndPoint failed", __FUNCTION__);
    }
    phNxpEse_close(ESESTATUS_SUCCESS);
    return status;
  }
  ESE_ChannelInit(&Ch);
  status = SESTATUS_SUCCESS;
  return status;
}

/*******************************************************************************
**
** Function:        seteSEClientState
**
** Description:     Function to set the eSEUpdate state
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void seteSEClientState(uint8_t state) {
  ALOGE("%s: State = %d", __FUNCTION__, state);
  ese_update = (ese_update_state_t)state;
}

/*******************************************************************************
**
** Function:        sendeSEUpdateState
**
** Description:     Notify NFC HAL LS / JCOP download state
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
void sendeSEUpdateState(uint8_t state) {
  ALOGE("%s: State = %d", __FUNCTION__, state);
  phNxpEse_SPM_SetEseClientUpdateState(state);
}

/*******************************************************************************
**
** Function:        eSEUpdate_SeqHandler
**
** Description:     ESE client update handler
**
** Returns:         SUCCESS of ok
**
*******************************************************************************/
SESTATUS eSEUpdate_SeqHandler() {
  switch (ese_update) {
    case ESE_UPDATE_STARTED:
      break;
    case ESE_JCOP_UPDATE_REQUIRED:
      ALOGE("%s: ESE_JCOP_UPDATE_REQUIRED", __FUNCTION__);
      if (se_intf.isJcopUpdateRequired) {
        if (se_intf.sJcopUpdateIntferface == ESE_INTF_SPI) {
          handleJcopOsDownload();
          sendeSEUpdateState(ESE_JCOP_UPDATE_COMPLETED);
          setJcopUpdateRequired(false);
        } else if (se_intf.sJcopUpdateIntferface == ESE_INTF_NFC) {
          return SESTATUS_SUCCESS;
        }
      }
      [[fallthrough]];
    case ESE_JCOP_UPDATE_COMPLETED:
      ALOGE("%s: ESE_JCOP_UPDATE_COMPLETED", __FUNCTION__);
      [[fallthrough]];
    case ESE_LS_UPDATE_REQUIRED:
      if (se_intf.isLSUpdateRequired) {
        if (se_intf.sLsUpdateIntferface == ESE_INTF_SPI) {
          performLSUpdate();
          sendeSEUpdateState(ESE_LS_UPDATE_COMPLETED);
          setLsUpdateRequired(false);
        } else if (se_intf.sLsUpdateIntferface == ESE_INTF_NFC) {
          seteSEClientState(ESE_LS_UPDATE_REQUIRED);
          return SESTATUS_SUCCESS;
        }
      }
      ALOGE("%s: ESE_LS_UPDATE_REQUIRED", __FUNCTION__);
      [[fallthrough]];
    case ESE_LS_UPDATE_COMPLETED:
      ALOGE("%s: ESE_LS_UPDATE_COMPLETED", __FUNCTION__);
      [[fallthrough]];
    case ESE_UPDATE_COMPLETED:
      seteSEClientState(ESE_UPDATE_COMPLETED);
      sendeSEUpdateState(ESE_UPDATE_COMPLETED);
      NxpEse::initSEService();
      NxpEse::initVIrtualISOService();
      ALOGE("%s: ESE_UPDATE_COMPLETED", __FUNCTION__);
      break;
  }
  return SESTATUS_SUCCESS;
}
#endif /* NXP_BOOTTIME_UPDATE */