/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/
#include <android/hardware/secure_element/1.0/ISecureElement.h>
#include <hidl/LegacySupport.h>
#include <log/log.h>

#include "SecureElement.h"

// Generated HIDL files
using android::hardware::secure_element::V1_0::ISecureElement;
using android::hardware::secure_element::V1_0::implementation::SecureElement;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::OK;
using android::sp;
using android::status_t;

int main() {
  ALOGD("Thales Secure Element HAL for eSE2 Service 1.6.0 is starting. libse-gto v1.13");
  sp<ISecureElement> se_service = new SecureElement("eSE2");
  configureRpcThreadpool(1, true);
  status_t status = se_service->registerAsService("eSE2");
  if (status != OK) {
    LOG_ALWAYS_FATAL(
        "registerAsService (%d).",
        status);
    return -1;
  }

  ALOGD("Thales Secure Element Service is ready");
  joinRpcThreadpool();
  return 1;
}
