/******************************************************************************
 *
 *  Copyright 2018 NXP
 *  Copyright 2018 ST Microelectronics S.A.
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

#define LOG_TAG "stnfc@1.1-service.st"
#include <android/hardware/nfc/1.1/INfc.h>

#include <hidl/LegacySupport.h>
#include "Nfc.h"

// Generated HIDL files
using android::OK;
using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::nfc::V1_1::INfc;
using android::hardware::nfc::V1_1::implementation::Nfc;

int main() {
  ALOGD(" ST NFC HAL Service 1.1 is starting.");
  sp<INfc> nfc_service = new Nfc();

  configureRpcThreadpool(1, true /*callerWillJoin*/);
  status_t status = nfc_service->registerAsService();
  if (status != OK) {
    LOG_ALWAYS_FATAL("Could not register service for NFC HAL Iface (%d).",
                     status);
    return -1;
  }
  ALOGD(" ST NFC service is ready");
  joinRpcThreadpool();
  return 1;
}
