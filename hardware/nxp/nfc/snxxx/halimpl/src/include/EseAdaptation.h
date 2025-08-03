/******************************************************************************
 *
 *
 *  Copyright 2015-2019, 2021-2023 NXP
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
#pragma once
#include <utils/RefBase.h>
#include <vendor/nxp/nxpese/1.0/INxpEse.h>

#include "NxpNfcThreadMutex.h"
#include "ese_hal_api.h"
#include "hal_nxpese.h"
using vendor::nxp::nxpese::V1_0::INxpEse;

class EseAdaptation {
 public:
  void Initialize();
  void InitializeHalDeviceContext();
  virtual ~EseAdaptation();
  static EseAdaptation& GetInstance();
  static int HalIoctl(long arg, void* p_data);
  tHAL_ESE_ENTRY* GetHalEntryFuncs();
  ese_nxp_IoctlInOutData_t* mCurrentIoctlData;
  tHAL_ESE_ENTRY mSpiHalEntryFuncs;  // function pointers for HAL entry points

 private:
  EseAdaptation();
  void signal();
  static EseAdaptation* mpInstance;
  static NfcHalThreadMutex sLock;
  static NfcHalThreadMutex sIoctlLock;
  NfcHalThreadCondVar mCondVar;
  static tHAL_ESE_CBACK* mHalCallback;
  static tHAL_ESE_DATA_CBACK* mHalDataCallback;
  static NfcHalThreadCondVar mHalOpenCompletedEvent;
  static NfcHalThreadCondVar mHalCloseCompletedEvent;
  static NfcHalThreadCondVar mHalIoctlEvent;
  static android::sp<vendor::nxp::nxpese::V1_0::INxpEse> mHalNxpEse;
  static NfcHalThreadCondVar mHalCoreResetCompletedEvent;
  static NfcHalThreadCondVar mHalCoreInitCompletedEvent;
  static NfcHalThreadCondVar mHalInitCompletedEvent;
  static uint32_t Thread();
  static void HalDeviceContextDataCallback(uint16_t data_len, uint8_t* p_data);

  static void HalOpen(tHAL_ESE_CBACK* p_hal_cback,
                      tHAL_ESE_DATA_CBACK* p_data_cback);
  static void HalClose();
  static void HalWrite(uint16_t data_len, uint8_t* p_data);
  static void HalRead(uint16_t data_len, uint8_t* p_data);
};
tHAL_ESE_ENTRY* getInstance();
