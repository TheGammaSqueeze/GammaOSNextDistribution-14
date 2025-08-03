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
#define LOG_TAG "nxpese@1.2-service"
#include <aidl/android/hardware/nfc/INfc.h>
#include <android/hardware/nfc/1.2/INfc.h>
#include <android/hardware/secure_element/1.2/ISecureElement.h>
#include <hidl/LegacySupport.h>
#include <log/log.h>
#include <string.h>

#include <regex>

#include "SecureElement.h"
#include "VirtualISO.h"
#ifdef NXP_BOOTTIME_UPDATE
#include <vendor/nxp/nxpese/1.0/INxpEse.h>
#include "NxpEse.h"
#include "eSEClient.h"
#endif

#define MAX_NFC_GET_RETRY 30
#define NFC_GET_SERVICE_DELAY_MS 100

// Generated HIDL files
using android::OK;
using android::base::StringPrintf;
using android::hardware::configureRpcThreadpool;
using android::hardware::defaultPassthroughServiceImplementation;
using android::hardware::joinRpcThreadpool;
using android::hardware::registerPassthroughServiceImplementation;
using INfc = android::hardware::nfc::V1_2::INfc;
using android::hardware::secure_element::V1_2::ISecureElement;
using android::hardware::secure_element::V1_2::implementation::SecureElement;
#ifdef NXP_BOOTTIME_UPDATE
using vendor::nxp::nxpese::V1_0::INxpEse;
using vendor::nxp::nxpese::V1_0::implementation::NxpEse;
#endif
using vendor::nxp::virtual_iso::V1_0::implementation::VirtualISO;
using INfcAidl = ::aidl::android::hardware::nfc::INfc;

using android::OK;
using android::sp;
using android::status_t;

std::string NFC_AIDL_HAL_SERVICE_NAME = "android.hardware.nfc.INfc/default";

static inline void waitForNFCHAL() {
  int retry = 0;
  android::sp<INfc> nfc_service = nullptr;
  std::shared_ptr<INfcAidl> nfc_aidl_service = nullptr;

  ALOGI("Waiting for NFC HAL .. ");
  do {
    ::ndk::SpAIBinder binder(
        AServiceManager_checkService(NFC_AIDL_HAL_SERVICE_NAME.c_str()));
    nfc_aidl_service = INfcAidl::fromBinder(binder);
    if (nfc_aidl_service != nullptr) {
      ALOGI("NFC HAL service is registered");
      break;
    }
    /* Wait for 100 MS for HAL RETRY*/
    usleep(NFC_GET_SERVICE_DELAY_MS * 1000);
  } while (retry++ < MAX_NFC_GET_RETRY);

  if (nfc_aidl_service == nullptr) {
    ALOGE("Failed to get NFC AIDLHAL Service, trying to get HIDL service");
    nfc_service = INfc::tryGetService();
    if (nfc_service != nullptr) {
      ALOGI("NFC HAL service is registered");
    } else {
      ALOGE("Failed to get NFC HAL Service");
    }
  }
}

int main() {
  status_t status;

  char terminalID[5] = "eSE1";
  const char* SEterminal = "eSEx";
  bool ret = false;

  android::sp<ISecureElement> se_service = nullptr;
#ifdef NXP_BOOTTIME_UPDATE
  android::sp<INxpEse> nxp_se_service = nullptr;
#endif
  android::sp<ISecureElement> virtual_iso_service = nullptr;

  try {
    waitForNFCHAL();
    ALOGI("Secure Element HAL Service 1.2 is starting.");
    se_service = new SecureElement();
    if (se_service == nullptr) {
      ALOGE("Can not create an instance of Secure Element HAL Iface, exiting.");
      goto shutdown;
    }
    configureRpcThreadpool(1, true /*callerWillJoin*/);

#ifdef NXP_BOOTTIME_UPDATE
    checkEseClientUpdate();
    ret = geteSETerminalId(terminalID);
#else
    ret = true;
#endif
    ALOGI("Terminal val = %s", terminalID);
    if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
      ALOGI("Terminal ID found");
      status = se_service->registerAsService(terminalID);

      if (status != OK) {
        ALOGE("Could not register service for Secure Element HAL Iface (%d).",
              status);
        goto shutdown;
      }
      ALOGI("Secure Element Service is ready");
#ifdef NXP_BOOTTIME_UPDATE
      ALOGI("NXP Secure Element Extn Service 1.0 is starting.");
      nxp_se_service = new NxpEse();
      if (nxp_se_service == nullptr) {
        ALOGE(
            "Can not create an instance of NXP Secure Element Extn "
            "Iface,exiting.");
        goto shutdown;
      }
      status = nxp_se_service->registerAsService();
      if (status != OK) {
        ALOGE(
            "Could not register service for Power Secure Element Extn Iface "
            "(%d).",
            status);
        goto shutdown;
      }
      ALOGI("Secure Element Service is ready");
#endif
    }

#ifdef NXP_VISO_ENABLE
    ALOGI("Virtual ISO HAL Service 1.0 is starting.");
    virtual_iso_service = new VirtualISO();
    if (virtual_iso_service == nullptr) {
      ALOGE("Can not create an instance of Virtual ISO HAL Iface, exiting.");
      goto shutdown;
    }
#ifdef NXP_BOOTTIME_UPDATE
    ret = geteUICCTerminalId(terminalID);
#else
    strncpy(terminalID, "eSE2", 4);
    ret = true;
#endif
    if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
      status = virtual_iso_service->registerAsService(terminalID);
      if (status != OK) {
        ALOGE("Could not register service for Virtual ISO HAL Iface (%d).",
              status);
        goto shutdown;
      }
    }

    ALOGI("Virtual ISO: Secure Element Service is ready");
#endif
#ifdef NXP_BOOTTIME_UPDATE
    perform_eSEClientUpdate();
#endif
    joinRpcThreadpool();
  } catch (const std::length_error& e) {
    ALOGE("Length Exception occurred = %s ", e.what());
  } catch (const std::__1::ios_base::failure& e) {
    ALOGE("ios failure Exception occurred = %s ", e.what());
  } catch (std::__1::regex_error& e) {
    ALOGE("Regex Exception occurred = %s ", e.what());
  }
// Should not pass this line
shutdown:
  // In normal operation, we don't expect the thread pool to exit
  ALOGE("Secure Element Service is shutting down");
  return 1;
}
