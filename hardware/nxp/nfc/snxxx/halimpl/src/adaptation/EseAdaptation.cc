/******************************************************************************
 *
 *
 *  Copyright 2015-2023 NXP
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
#define LOG_TAG "EseAdaptation"
#include "EseAdaptation.h"

#include <hwbinder/ProcessState.h>
#include <log/log.h>

using android::sp;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hardware::Void;

using vendor::nxp::nxpese::V1_0::INxpEse;

extern bool nfc_debug_enabled;

extern "C" void GKI_shutdown();
extern void resetConfig();
extern "C" void verify_stack_non_volatile_store();
extern "C" void delete_stack_non_volatile_store(bool forceDelete);

EseAdaptation* EseAdaptation::mpInstance = NULL;
NfcHalThreadMutex EseAdaptation::sLock;
NfcHalThreadMutex EseAdaptation::sIoctlLock;
sp<INxpEse> EseAdaptation::mHalNxpEse;
tHAL_ESE_CBACK* EseAdaptation::mHalCallback = NULL;
tHAL_ESE_DATA_CBACK* EseAdaptation::mHalDataCallback = NULL;
NfcHalThreadCondVar EseAdaptation::mHalOpenCompletedEvent;
NfcHalThreadCondVar EseAdaptation::mHalCloseCompletedEvent;

NfcHalThreadCondVar EseAdaptation::mHalCoreResetCompletedEvent;
NfcHalThreadCondVar EseAdaptation::mHalCoreInitCompletedEvent;
NfcHalThreadCondVar EseAdaptation::mHalInitCompletedEvent;
#define SIGNAL_NONE 0
#define SIGNAL_SIGNALED 1

/*******************************************************************************
**
** Function:    EseAdaptation::EseAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
EseAdaptation::EseAdaptation() {
  mCurrentIoctlData = NULL;
  memset(&mSpiHalEntryFuncs, 0, sizeof(mSpiHalEntryFuncs));
}

/*******************************************************************************
**
** Function:    EseAdaptation::~EseAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
EseAdaptation::~EseAdaptation() { mpInstance = NULL; }

/*******************************************************************************
**
** Function:    EseAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
EseAdaptation& EseAdaptation::GetInstance() {
  NfcHalAutoThreadMutex a(sLock);

  if (!mpInstance) mpInstance = new EseAdaptation;
  return *mpInstance;
}

/*******************************************************************************
**
** Function:    EseAdaptation::Initialize()
**
** Description: class initializer
**
** Returns:     none
**
*******************************************************************************/
void EseAdaptation::Initialize() {
  const char* func = "EseAdaptation::Initialize";
  ALOGD_IF(nfc_debug_enabled, "%s: enter", func);

  mHalCallback = NULL;
  InitializeHalDeviceContext();

  ALOGD_IF(nfc_debug_enabled, "%s: exit", func);
}

/*******************************************************************************
**
** Function:    EseAdaptation::signal()
**
** Description: signal the CondVar to release the thread that is waiting
**
** Returns:     none
**
*******************************************************************************/
void EseAdaptation::signal() { mCondVar.signal(); }

/*******************************************************************************
**
** Function:    EseAdaptation::Thread()
**
** Description: Creates work threads
**
** Returns:     none
**
*******************************************************************************/
uint32_t EseAdaptation::Thread() {
  const char* func = "EseAdaptation::Thread";
  ALOGD_IF(nfc_debug_enabled, "%s: enter", func);
  { NfcHalThreadCondVar CondVar; }

  EseAdaptation::GetInstance().signal();

  ALOGD_IF(nfc_debug_enabled, "%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    EseAdaptation::GetHalEntryFuncs()
**
** Description: Get the set of HAL entry points.
**
** Returns:     Functions pointers for HAL entry points.
**
*******************************************************************************/
tHAL_ESE_ENTRY* EseAdaptation::GetHalEntryFuncs() {
  ALOGD_IF(nfc_debug_enabled, "GetHalEntryFuncs: enter");
  return &mSpiHalEntryFuncs;
}

/*******************************************************************************
**
** Function:    EseAdaptation::InitializeHalDeviceContext
**
** Description: Ask the generic Android HAL to find the Broadcom-specific HAL.
**
** Returns:     None.
**
*******************************************************************************/

void EseAdaptation::InitializeHalDeviceContext() {
  const char* func = "EseAdaptation::InitializeHalDeviceContext";
  ALOGD_IF(nfc_debug_enabled, "%s: enter", func);
  ALOGD_IF(nfc_debug_enabled, "%s: INxpEse::tryGetService()", func);
  mHalNxpEse = INxpEse::tryGetService();
  ALOGD_IF(mHalNxpEse == nullptr, "%s: Failed to retrieve the NXP ESE HAL!",
           func);
  if (mHalNxpEse != nullptr) {
    ALOGD_IF(nfc_debug_enabled, "%s: INxpEse::getService() returned %p (%s)",
             func, mHalNxpEse.get(),
             (mHalNxpEse->isRemote() ? "remote" : "local"));
  }
  /*Transceive NCI_INIT_CMD*/
  ALOGD_IF(nfc_debug_enabled, "%s: exit", func);
}
/*******************************************************************************
**
** Function:    EseAdaptation::HalDeviceContextDataCallback
**
** Description: Translate generic Android HAL's callback into Broadcom-specific
**              callback function.
**
** Returns:     None.
**
*******************************************************************************/
void EseAdaptation::HalDeviceContextDataCallback(uint16_t data_len,
                                                 uint8_t* p_data) {
  const char* func = "EseAdaptation::HalDeviceContextDataCallback";
  ALOGD_IF(nfc_debug_enabled, "%s: len=%u", func, data_len);
  if (mHalDataCallback) mHalDataCallback(data_len, p_data);
}

/*******************************************************************************
**
** Function:    IoctlCallback
**
** Description: Callback from HAL stub for IOCTL api invoked.
**              Output data for IOCTL is sent as argument
**
** Returns:     None.
**
*******************************************************************************/
void IoctlCallback(hidl_vec<uint8_t> outputData) {
  const char* func = "IoctlCallback";
  ese_nxp_ExtnOutputData_t* pOutData =
      (ese_nxp_ExtnOutputData_t*)&outputData[0];
  ALOGD_IF(nfc_debug_enabled, "%s Ioctl Type=%lu", func,
           (unsigned long)pOutData->ioctlType);
  EseAdaptation* pAdaptation = &EseAdaptation::GetInstance();
  /*Output Data from stub->Proxy is copied back to output data
   * This data will be sent back to libese*/
  memcpy(&pAdaptation->mCurrentIoctlData->out, &outputData[0],
         sizeof(ese_nxp_ExtnOutputData_t));
}
/*******************************************************************************
**
** Function:    EseAdaptation::HalIoctl
**
** Description: Calls ioctl to the Ese driver.
**              If called with a arg value of 0x01 than wired access requested,
**              status of the request would be updated to p_data.
**              If called with a arg value of 0x00 than wired access will be
**              released, status of the request would be updated to p_data.
**              If called with a arg value of 0x02 than current p61 state would
*be
**              updated to p_data.
**
** Returns:     -1 or 0.
**
*******************************************************************************/
int EseAdaptation::HalIoctl(long arg, void* p_data) {
  const char* func = "EseAdaptation::HalIoctl";
  hidl_vec<uint8_t> data;
  NfcHalAutoThreadMutex a(sIoctlLock);
  ese_nxp_IoctlInOutData_t* pInpOutData = (ese_nxp_IoctlInOutData_t*)p_data;
  ALOGD_IF(nfc_debug_enabled, "%s arg=%ld", func, arg);

  EseAdaptation::GetInstance().mCurrentIoctlData = pInpOutData;
  data.setToExternal((uint8_t*)pInpOutData, sizeof(ese_nxp_IoctlInOutData_t));
  if (mHalNxpEse != nullptr) mHalNxpEse->ioctl(arg, data, IoctlCallback);
  ALOGD_IF(nfc_debug_enabled, "%s Ioctl Completed for Type=%lu", func,
           (unsigned long)pInpOutData->out.ioctlType);
  return (pInpOutData->out.result);
}
