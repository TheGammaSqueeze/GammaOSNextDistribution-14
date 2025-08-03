/*
 * Copyright (C) 2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "vendor.lineage.powershare@1.0-service.pixel"

#include <android-base/logging.h>
#include <binder/ProcessState.h>
#include <hidl/HidlTransportSupport.h>

#include "PowerShare.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::sp;
using android::status_t;
using android::OK;

using ::vendor::lineage::powershare::pixel::PowerShare;

int main() {
    sp<PowerShare> powerShare;
    status_t status;

    LOG(INFO) << "PowerShare HAL service is starting.";

    powerShare = new PowerShare();
    if (powerShare == nullptr) {
        LOG(ERROR) << "Can not create an instance of PowerShare HAL, exiting.";
        goto shutdown;
    }

    configureRpcThreadpool(1, true /*callerWillJoin*/);

    status = powerShare->registerAsService();
    if (status != OK) {
        LOG(ERROR) << "Could not register service for PowerShare HAL (" << status << ")";
        goto shutdown;
    }

    LOG(INFO) << "PowerShare HAL service is ready.";
    joinRpcThreadpool();
    // Should not pass this line

shutdown:
    // In normal operation, we don't expect the thread pool to shutdown
    LOG(ERROR) << "PowerShare HAL service is shutting down.";
    return 1;
}
