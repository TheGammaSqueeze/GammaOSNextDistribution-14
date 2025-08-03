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
#include <aidl/android/hardware/nfc/INfc.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/nfc/1.2/INfc.h>

#include "SecureElement.h"
#include "VirtualISO.h"
#include "phNxpConfig.h"

#define MAX_NFC_GET_RETRY 30
#define NFC_GET_SERVICE_DELAY_MS 100
#define TERMINAL_LEN 5
#define NAME_NXP_SPI_SE_TERMINAL_NUM "NXP_SPI_SE_TERMINAL_NUM"
#define NAME_NXP_VISO_SE_TERMINAL_NUM "NXP_VISO_SE_TERMINAL_NUM"

using INfc = android::hardware::nfc::V1_2::INfc;
using INfcAidl = ::aidl::android::hardware::nfc::INfc;
using ::aidl::android::hardware::secure_element::SecureElement;
using VirtualISO = aidl::vendor::nxp::virtual_iso::VirtualISO;

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
  char terminalID[5] = "eSE1";
  const char* SEterminal = "eSEx";
  bool ret = false;

  ALOGI("Secure Element AIDL HAL Service starting up");
  if (!ABinderProcess_setThreadPoolMaxThreadCount(1)) {
    ALOGE("failed to set thread pool max thread count");
    return EXIT_FAILURE;
  }

  waitForNFCHAL();
  ALOGI("Secure Element AIDL HAL Service starting up");
  std::shared_ptr<SecureElement> se_service =
      ndk::SharedRefBase::make<SecureElement>();
  std::shared_ptr<VirtualISO> virtual_iso_service = nullptr;

  if (GetNxpStrValue(NAME_NXP_SPI_SE_TERMINAL_NUM, terminalID, TERMINAL_LEN)) {
    LOG(ERROR) << "eSETerminalId found";
    ALOGE("eSETerminalId found val = %s ", terminalID);

    ret = true;
  }
  ALOGI("Terminal val = %s", terminalID);
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    ALOGI("Terminal ID found");
    const std::string seInstName =
        std::string() + SecureElement::descriptor + "/" + terminalID;
    binder_status_t status = AServiceManager_addService(
        se_service->asBinder().get(), seInstName.c_str());
    if (status != OK) {
      ALOGE("Could not register service for Secure Element HAL Iface (%d).",
            status);
      goto shutdown;
    }
    ALOGI("Secure Element Service is ready");
  }
#ifdef NXP_VISO_ENABLE
  ALOGI("Virtual ISO HAL Service 1.0 is starting.");
  virtual_iso_service = ndk::SharedRefBase::make<VirtualISO>();

  ret = false;
  if (GetNxpStrValue(NAME_NXP_VISO_SE_TERMINAL_NUM, terminalID, TERMINAL_LEN)) {
    ALOGE("eUICCTerminalId found val = %s ", terminalID);
    ret = true;
  }
  if ((ret) && (strncmp(SEterminal, terminalID, 3) == 0)) {
    const std::string vISO_InstName =
        std::string() + SecureElement::descriptor + "/" + terminalID;
    binder_status_t status = AServiceManager_addService(
        virtual_iso_service->asBinder().get(), vISO_InstName.c_str());

    if (status != OK) {
      ALOGE("Could not register service for Virtual ISO HAL Iface (%d).",
            status);
      goto shutdown;
    }
  }

  ALOGI("Virtual ISO: Secure Element Service is ready");
#endif
  ABinderProcess_joinThreadPool();
// Should not pass this line
shutdown:
  // In normal operation, we don't expect the thread pool to exit
  ALOGE("Secure Element Service is shutting down");
  return EXIT_FAILURE;
}