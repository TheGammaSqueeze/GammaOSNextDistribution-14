/******************************************************************************
 *
 *  Copyright 2019-2021 NXP
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

#define LOG_TAG "nxpnfc@2.0-service"
#include <android/hardware/nfc/1.1/INfc.h>
#include <unistd.h>
#include <vendor/nxp/nxpnfc/2.0/INxpNfc.h>

#include <hidl/LegacySupport.h>
#include "Nfc.h"
#include "NxpNfc.h"
#include "eSEClientExtns.h"

#if (NXP_NFC_RECOVERY == TRUE)
#include "phNxpNciHal_Recovery.h"
#endif

// Generated HIDL files
using android::OK;
using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::nfc::V1_2::INfc;
using android::hardware::nfc::V1_2::implementation::Nfc;
using vendor::nxp::nxpnfc::V2_0::INxpNfc;
using vendor::nxp::nxpnfc::V2_0::implementation::NxpNfc;

int main() {
  status_t status;

  sp<INfc> nfc_service = nullptr;
  sp<INxpNfc> nxp_nfc_service = nullptr;

  try {
    ALOGD("NFC HAL Service 1.2 is starting.");
    nfc_service = new Nfc();
    if (nfc_service == nullptr) {
      ALOGE("Can not create an instance of NFC HAL Iface, exiting.");
      return -1;
    }

    configureRpcThreadpool(1, true /*callerWillJoin*/);
#if (NXP_NFC_RECOVERY == TRUE)
    phNxpNciHal_RecoverFWTearDown();
#endif
#ifdef NXP_BOOTTIME_UPDATE
    initializeEseClient();
    checkEseClientUpdate();
#endif
    status = nfc_service->registerAsService();
    if (status != OK) {
      LOG_ALWAYS_FATAL("Could not register service for NFC HAL Iface (%d).",
                       status);
      return -1;
    }

    ALOGI("NXP NFC Extn Service 1.0 is starting.");
    nxp_nfc_service = new NxpNfc();
    if (nxp_nfc_service == nullptr) {
      ALOGE("Can not create an instance of NXP NFC Extn Iface, exiting.");
      return -1;
    }
    status = nxp_nfc_service->registerAsService();
    if (status != OK) {
      ALOGE("Could not register service for NXP NFC Extn Iface (%d).", status);
    }
#ifdef NXP_BOOTTIME_UPDATE
    ALOGE("Before calling JCOP JCOS_doDownload");
    perform_eSEClientUpdate();
    ALOGE("After calling JCOS_doDownload");
#endif
    ALOGI("NFC service is ready");
    joinRpcThreadpool();
  } catch (const std::length_error& le) {
  } catch (const std::__1::ios_base::failure& e) {
  } catch (std::__1::system_error& e) {
  }
  return 1;
}
