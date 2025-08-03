/******************************************************************************
 *
 *  Copyright 2018 NXP
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
#define LOG_TAG "nxpese@1.0-service"
#include <android-base/stringprintf.h>
#include <android/hardware/secure_element/1.0/ISecureElement.h>
#include <base/logging.h>
#include <hidl/LegacySupport.h>
#include <string.h>
#include <vendor/nxp/nxpese/1.0/INxpEse.h>

#include "NxpEse.h"
#include "SecureElement.h"
#include "VirtualISO.h"
#include "eSEClient.h"

// Generated HIDL files
using android::OK;
using android::base::StringPrintf;
using android::hardware::configureRpcThreadpool;
using android::hardware::defaultPassthroughServiceImplementation;
using android::hardware::joinRpcThreadpool;
using android::hardware::registerPassthroughServiceImplementation;
using android::hardware::secure_element::V1_0::ISecureElement;
using android::hardware::secure_element::V1_0::implementation::SecureElement;
using vendor::nxp::nxpese::V1_0::INxpEse;
using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
using vendor::nxp::virtual_iso::V1_0::implementation::VirtualISO;

using android::OK;
using android::sp;
using android::status_t;

int main() {
  status_t status;

  char terminalID[5];
  const char* SEterminal = "eSEx";
  bool ret = false;

  android::sp<ISecureElement> se_service = nullptr;
  android::sp<INxpEse> nxp_se_service = nullptr;
  android::sp<ISecureElement> virtual_iso_service = nullptr;

  ALOGI("Secure Element HAL Service 1.0 is starting.");
  se_service = new SecureElement();
  if (se_service == nullptr) {
    LOG(ERROR) << StringPrintf(
        "Can not create an instance of Secure Element HAL Iface, exiting.");
    goto shutdown;
  }
  configureRpcThreadpool(1, true /*callerWillJoin*/);

  checkEseClientUpdate();
  ret = geteSETerminalId(terminalID);
  ALOGI("Terminal val = %s", terminalID);
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    ALOGI("Terminal ID found");
    status = se_service->registerAsService(terminalID);

    if (status != OK) {
      LOG(ERROR) << StringPrintf(
          "Could not register service for Secure Element HAL Iface (%d).",
          status);
      goto shutdown;
    }
    ALOGI("Secure Element Service is ready");

    ALOGI("NXP Secure Element Extn Service 1.0 is starting.");
    nxp_se_service = new NxpEse();
    if (nxp_se_service == nullptr) {
      LOG(ERROR) << StringPrintf(
          "Can not create an instance of NXP Secure "
          "Element Extn Iface,exiting.");
      goto shutdown;
    }
    status = nxp_se_service->registerAsService();
    if (status != OK) {
      LOG(ERROR) << StringPrintf(
          "Could not register service for Power Secure "
          "Element Extn Iface (%d).",
          status);
      goto shutdown;
    }
    ALOGI("Secure Element Service is ready");
  }

  ALOGI("Virtual ISO HAL Service 1.0 is starting.");
  virtual_iso_service = new VirtualISO();
  if (virtual_iso_service == nullptr) {
    LOG(ERROR) << StringPrintf(
        "Can not create an instance of Virtual ISO HAL Iface, exiting.");
    goto shutdown;
  }
  ret = geteUICCTerminalId(terminalID);
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    status = virtual_iso_service->registerAsService(terminalID);
    if (status != OK) {
      LOG(ERROR) << StringPrintf(
          "Could not register service for Virtual ISO HAL Iface (%d).", status);
      goto shutdown;
    }
  }

  ALOGI("Virtual ISO: Secure Element Service is ready");
  perform_eSEClientUpdate();
  joinRpcThreadpool();
// Should not pass this line
shutdown:
  // In normal operation, we don't expect the thread pool to exit
  LOG(ERROR) << StringPrintf("Secure Element Service is shutting down");
  return 1;
}
