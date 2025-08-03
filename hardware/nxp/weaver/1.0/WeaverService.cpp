/******************************************************************************
 *
 *  Copyright 2020, 2022 NXP
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
#define LOG_TAG "weaver@1.0-service"
#include <log/log.h>
#include <android/hardware/weaver/1.0/IWeaver.h>
#include <android/hardware/weaver/1.0/types.h>

#include <hidl/LegacySupport.h>
#include <android/binder_process.h>

#include <string.h>
#include "Weaver.h"

// Generated HIDL files
using android::hardware::weaver::V1_0::IWeaver;
using android::hardware::weaver::V1_0::implementation::Weaver;
using android::hardware::defaultPassthroughServiceImplementation;
using android::OK;
using android::hardware::registerPassthroughServiceImplementation;

using android::sp;
using android::status_t;
using android::OK;

int main() {
  try {
    status_t status;

    android::sp<IWeaver> weaver_service = nullptr;
    ALOGI("Weaver HAL Service 1.0 is starting.");
    weaver_service = new Weaver();
    if (weaver_service == nullptr) {
      ALOGE("Can not create an instance of Weaver HAL Interface, exiting.");
      goto shutdown;
    }
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    status = weaver_service->registerAsService();

    if (status != OK) {
      ALOGE("Could not register service for Weaver HAL Interface (%d)", status);
      goto shutdown;
    }
    ALOGI("Weaver Service is ready");
    ABinderProcess_joinThreadPool();
  } catch (std::length_error& e) {
    ALOGE("Length Exception occurred = %s ", e.what());
  } catch (std::__1::ios_base::failure& e) {
    ALOGE("ios failure Exception occurred = %s ", e.what());
  } catch (std::__1::system_error& e) {
    ALOGE("system error Exception occurred = %s ", e.what());
  }
    // Should not pass this line
shutdown:
    // In normal operation, we don't expect the thread pool to exit
    ALOGE("Weaver Service is shutting down");
    return 1;
}
