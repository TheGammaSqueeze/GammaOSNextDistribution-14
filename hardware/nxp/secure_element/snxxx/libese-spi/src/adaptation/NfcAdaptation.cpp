/******************************************************************************
 *
 *  Copyright 2018-2020,2022-2023 NXP
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
#define LOG_TAG "NxpEseHal-NfcAdaptation"
#include "NfcAdaptation.h"

#include <aidl/vendor/nxp/nxpnfc_aidl/INxpNfc.h>
#include <android/binder_auto_utils.h>
#include <android/binder_enums.h>
#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/nfc/1.0/types.h>
#include <binder/IServiceManager.h>
#include <ese_logs.h>
#include <hwbinder/ProcessState.h>
#include <log/log.h>
#include <pthread.h>

#undef LOG_TAG
#define LOG_TAG "SpiAdaptation"

using android::OK;
using android::sp;
using android::status_t;

using android::hardware::hidl_vec;
using android::hardware::ProcessState;
using android::hardware::Return;
using android::hardware::Void;
using INxpNfc = vendor::nxp::nxpnfc::V2_0::INxpNfc;
using INxpNfcAidl = ::aidl::vendor::nxp::nxpnfc_aidl::INxpNfc;

#define MAX_NFC_GET_RETRY 30
#define NFC_GET_SERVICE_DELAY_MS 100
std::string NXPNFC_AIDL_HAL_SERVICE_NAME =
    "vendor.nxp.nxpnfc_aidl.INxpNfc/default";

sp<INxpNfc> NfcAdaptation::mHalNxpNfc = nullptr;
std::shared_ptr<INxpNfcAidl> NfcAdaptation::mAidlHalNxpNfc = nullptr;
ThreadMutex NfcAdaptation::sIoctlLock;
NfcAdaptation* NfcAdaptation::mpInstance = NULL;
ThreadMutex NfcAdaptation::sLock;

int omapi_status;

void NfcAdaptation::Initialize() {
  int retry = 0;
  const char* func = "NfcAdaptation::Initialize";
  NXP_LOG_ESE_D("%s", func);
  // Try get AIDL
  do {
    ::ndk::SpAIBinder binder(
        AServiceManager_checkService(NXPNFC_AIDL_HAL_SERVICE_NAME.c_str()));
    mAidlHalNxpNfc = INxpNfcAidl::fromBinder(binder);
    if (mAidlHalNxpNfc != nullptr) {
      NXP_LOG_ESE_E("%s: INxpNfcAidl::fromBinder returned", func);
      break;
    }
    usleep(NFC_GET_SERVICE_DELAY_MS * 1000);
  } while (retry++ < MAX_NFC_GET_RETRY);
  if (mAidlHalNxpNfc == nullptr) {
    ALOGE("Failed to get NXP NFC AIDLHAL .. Try for HIDL HAL");
    mHalNxpNfc = INxpNfc::tryGetService();
    if (mHalNxpNfc != nullptr) {
      ALOGI("NXP NFC HAL service is available");
    } else {
      ALOGE("Failed to get INxpNfc::tryGetService");
    }
  }
  NXP_LOG_ESE_D("%s: exit", func);
}
/*******************************************************************************
**
** Function:    NfcAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
NfcAdaptation& NfcAdaptation::GetInstance() {
  AutoThreadMutex a(sLock);

  if (!mpInstance) mpInstance = new NfcAdaptation;
  return *mpInstance;
}
/*******************************************************************************
**
** Function:    ThreadMutex::ThreadMutex()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::ThreadMutex() {
  pthread_mutexattr_t mutexAttr;

  pthread_mutexattr_init(&mutexAttr);
  pthread_mutex_init(&mMutex, &mutexAttr);
  pthread_mutexattr_destroy(&mutexAttr);
}
/*******************************************************************************
**
** Function:    ThreadMutex::~ThreadMutex()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::~ThreadMutex() { pthread_mutex_destroy(&mMutex); }

/*******************************************************************************
**
** Function:    AutoThreadMutex::AutoThreadMutex()
**
** Description: class constructor, automatically lock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::AutoThreadMutex(ThreadMutex& m) : mm(m) { mm.lock(); }

/*******************************************************************************
**
** Function:    AutoThreadMutex::~AutoThreadMutex()
**
** Description: class destructor, automatically unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::~AutoThreadMutex() { mm.unlock(); }

/*******************************************************************************
**
** Function:    ThreadMutex::lock()
**
** Description: lock the mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::lock() { pthread_mutex_lock(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadMutex::unblock()
**
** Description: unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::unlock() { pthread_mutex_unlock(&mMutex); }

/*******************************************************************************
**
** Function:    NfcAdaptation::NfcAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::NfcAdaptation() { mCurrentIoctlData = NULL; }

/*******************************************************************************
**
** Function:    NfcAdaptation::~NfcAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::~NfcAdaptation() { mpInstance = NULL; }

/*******************************************************************************
**
** Function:    NfcAdaptation::resetEse
**
** Description:  This function a wrapper function which triggers Ese reset
**
**
**
** Returns:     -1 or 0.
**
*******************************************************************************/
ESESTATUS NfcAdaptation::resetEse(uint64_t level) {
  const char* func = "NfcAdaptation::resetEse";
  ESESTATUS result = ESESTATUS_FAILED;
  bool ret = 0;

  NXP_LOG_ESE_D("%s : Enter", func);

  if (mAidlHalNxpNfc != nullptr) {
    mAidlHalNxpNfc->resetEse(level, &ret);
    if (ret) {
      NXP_LOG_ESE_E("NfcAdaptation::resetEse mAidlHalNxpNfc completed");
      result = ESESTATUS_SUCCESS;
    } else {
      NXP_LOG_ESE_E("NfcAdaptation::resetEse mAidlHalNxpNfc failed");
    }
  } else if (mHalNxpNfc != nullptr) {
    ret = mHalNxpNfc->resetEse(level);
    if (ret) {
      NXP_LOG_ESE_E("NfcAdaptation::resetEse mHalNxpNfc completed");
      result = ESESTATUS_SUCCESS;
    } else {
      NXP_LOG_ESE_E("NfcAdaptation::resetEse mHalNxpNfc failed");
    }
  }

  return result;
}

#ifdef NXP_BOOTTIME_UPDATE
/*******************************************************************************
**
** Function:    NfcAdaptation::setEseUpdateState
**
** Description:  This is a wrapper functions notifies upper layer about
** the jcob download completion.
**
** Returns:     -1 or 0.
**
*******************************************************************************/
ESESTATUS NfcAdaptation::setEseUpdateState(void* p_data) {
  const char* func = "NfcAdaptation::setEseUpdateState";
  ::android::hardware::nfc::V1_0::NfcData data;
  ESESTATUS result = ESESTATUS_FAILED;
  bool ret = 0;

  NXP_LOG_ESE_D("%s : Enter", func);

  ese_nxp_IoctlInOutData_t* pInpOutData = (ese_nxp_IoctlInOutData_t*)p_data;
  data.setToExternal((uint8_t*)pInpOutData, sizeof(ese_nxp_IoctlInOutData_t));

  if (mAidlHalNxpNfc != nullptr) {
    NXP_LOG_ESE_D(
        "NfcAdaptation::setEseUpdateState not supported for mAidlHalNxpNfc");
  } else if (mHalNxpNfc != nullptr) {
    ret = mHalNxpNfc->setEseUpdateState(
        (::vendor::nxp::nxpnfc::V2_0::NxpNfcHalEseState)
            pInpOutData->inp.data.nxpCmd.p_cmd[0]);
    if (ret) {
      NXP_LOG_ESE_E("NfcAdaptation::setEseUpdateState mHalNxpNfc completed");
      result = ESESTATUS_SUCCESS;
    } else {
      NXP_LOG_ESE_E("NfcAdaptation::setEseUpdateState mHalNxpNfc failed");
    }
  }

  return result;
}
#endif
/*******************************************************************************
**
** Function:    ThreadCondVar::ThreadCondVar()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::ThreadCondVar() {
  pthread_condattr_t CondAttr;

  pthread_condattr_init(&CondAttr);
  pthread_cond_init(&mCondVar, &CondAttr);

  pthread_condattr_destroy(&CondAttr);
}

/*******************************************************************************
**
** Function:    ThreadCondVar::~ThreadCondVar()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::~ThreadCondVar() { pthread_cond_destroy(&mCondVar); }
