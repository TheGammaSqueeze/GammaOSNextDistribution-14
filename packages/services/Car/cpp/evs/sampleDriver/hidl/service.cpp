/*
 * Copyright (C) 2016-2019 The Android Open Source Project
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

#include "EvsEnumerator.h"
#include "EvsGlDisplay.h"
#include "ServiceNames.h"

#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/StrongPointer.h>

#include <unistd.h>

#include <atomic>

// libhidl:
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;

// Generated HIDL files
using ::android::frameworks::automotive::display::V1_0::IAutomotiveDisplayProxyService;
using ::android::hardware::automotive::evs::V1_1::IEvsDisplay;
using ::android::hardware::automotive::evs::V1_1::IEvsEnumerator;

using ::android::hardware::automotive::evs::V1_1::implementation::EvsEnumerator;

int main() {
    LOG(INFO) << "EVS Hardware Enumerator service is starting";

    android::sp<IAutomotiveDisplayProxyService> carWindowService =
            IAutomotiveDisplayProxyService::getService("default");
    if (carWindowService == nullptr) {
        LOG(ERROR) << "Cannot use AutomotiveDisplayProxyService.  Exiting.";
        return 1;
    }

#ifdef EVS_DEBUG
    SetMinimumLogSeverity(android::base::DEBUG);
#endif

    // Start a thread to monitor hotplug devices
    std::atomic<bool> running{true};
    std::thread hotplugHandler(EvsEnumerator::EvsHotplugThread, std::ref(running));

    android::sp<IEvsEnumerator> service = new EvsEnumerator(carWindowService);

    configureRpcThreadpool(1, true /* callerWillJoin */);

    // Register our service -- if somebody is already registered by our name,
    // they will be killed (their thread pool will throw an exception).
    android::status_t status = service->registerAsService(kEnumeratorServiceName);
    if (status == android::OK) {
        LOG(DEBUG) << kEnumeratorServiceName << " is ready.";
        joinRpcThreadpool();
    } else {
        LOG(ERROR) << "Could not register service " << kEnumeratorServiceName << " (" << status
                   << ").";
    }

    // Exit a hotplug device thread
    running = false;
    if (hotplugHandler.joinable()) {
        hotplugHandler.join();
    }

    // In normal operation, we don't expect the thread pool to exit
    LOG(ERROR) << "EVS Hardware Enumerator is shutting down";
    return 1;
}
