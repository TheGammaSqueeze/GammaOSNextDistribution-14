/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 ******************************************************************************/
#define LOG_TAG "ese@1.0-service.st"
#include <android/hardware/secure_element/1.0/ISecureElement.h>
#include <dlfcn.h>
#include <hidl/LegacySupport.h>
#include <log/log.h>

#include "SecureElement.h"
typedef int (*STEsePreProcess)(void);

// Generated HIDL files
using android::OK;
using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::secure_element::V1_0::ISecureElement;
using android::hardware::secure_element::V1_0::implementation::SecureElement;

int main() {
  ALOGD("Secure Element HAL Service 1.0 is starting.");

  // Ignore this dlopen it doesn't needed.
#if defined(ST_LIB_32)
  void* stdll = dlopen("/vendor/lib/libstpreprocess.so", RTLD_NOW);
#else
  void* stdll = dlopen("/vendor/lib64/libstpreprocess.so", RTLD_NOW);
#endif
  if (stdll) {
    STEsePreProcess fn = (STEsePreProcess)dlsym(stdll, "pre_process");
    if (fn) {
      ALOGD("Result=%d", fn());
    }
  }
  sp<ISecureElement> se_service = new SecureElement();
  configureRpcThreadpool(1, true /*callerWillJoin*/);
  status_t status = se_service->registerAsService("eSE1");
  if (status != OK) {
    LOG_ALWAYS_FATAL(
        "Could not register service for Secure Element HAL Iface (%d).",
        status);
    return -1;
  }

  ALOGD("Secure Element Service is ready");
  joinRpcThreadpool();
  return 1;
}
