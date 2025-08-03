/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "Enumerator.h"
#include "EnumeratorProxy.h"
#include "ServiceNames.h"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/StrongPointer.h>

#include <unistd.h>

#include <mutex>  // NOLINT
#include <string>

using android::OK;
using android::status_t;
using android::automotive::evs::V1_1::implementation::Enumerator;
using android::automotive::evs::V1_1::implementation::EnumeratorProxy;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::automotive::evs::V1_0::IEvsDisplay;
using android::hardware::automotive::evs::V1_1::IEvsEnumerator;

struct Context {
    mutable std::mutex lock;
    const char* hardwareServiceName;
    const char* managerServiceName;
    std::unique_ptr<IEvsEnumerator> enumerator = nullptr;
};

static void startService(Context* context) {
    LOG(INFO) << "EVS managed service connecting to hardware service at "
              << context->hardwareServiceName;
    std::lock_guard<std::mutex> lock_guard{context->lock};
    context->enumerator =
            std::make_unique<EnumeratorProxy>(Enumerator::build(context->hardwareServiceName));
    if (!context->enumerator) {
        LOG(ERROR) << "Failed to connect to hardware service - quitting from registrationThread";
        exit(1);
    }

    // Register our service -- if somebody is already registered by our name,
    // they will be killed (their thread pool will throw an exception).
    LOG(INFO) << "EVS managed service is starting as " << context->managerServiceName;
    if (status_t status = context->enumerator->registerAsService(); status != OK) {
        LOG(ERROR) << "Could not register service " << context->managerServiceName
                   << " status = " << status << " - quitting from registrationThread";
        exit(2);
    }

    LOG(INFO) << "Registration complete";
}

int main(int argc, char** argv) {
    LOG(INFO) << "EVS manager starting";

#ifdef EVS_DEBUG
    SetMinimumLogSeverity(android::base::DEBUG);
#endif

    // Set up default behavior, then check for command line options
    bool printHelp = false;
    const char* evsHardwareServiceName = kHardwareEnumeratorName;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--mock") == 0) {
            evsHardwareServiceName = kMockEnumeratorName;
        } else if (strcmp(argv[i], "--target") == 0) {
            i++;
            if (i >= argc) {
                LOG(ERROR) << "--target <service> was not provided with a service name";
            } else {
                evsHardwareServiceName = argv[i];
            }
        } else if (strcmp(argv[i], "--help") == 0) {
            printHelp = true;
        } else {
            printf("Ignoring unrecognized command line arg '%s'\n", argv[i]);
            printHelp = true;
        }
    }
    if (printHelp) {
        printf("Options include:\n");
        printf("  --mock                   Connect to the mock driver at EvsEnumeratorHw-Mock\n");
        printf("  --target <service_name>  Connect to the named IEvsEnumerator service");
    }

    // Prepare the RPC serving thread pool.  We're configuring it with no additional
    // threads beyond the main thread which will "join" the pool below.
    configureRpcThreadpool(1, true /* callerWillJoin */);

    // The connection to the underlying hardware service must happen on a dedicated thread to ensure
    // that the hwbinder response can be processed by the thread pool without blocking.
    Context context{.hardwareServiceName = evsHardwareServiceName,
                    .managerServiceName = kManagedEnumeratorName,
                    .enumerator = nullptr};

    std::thread registrationThread(startService, &context);

    // Send this main thread to become a permanent part of the thread pool.
    // This is not expected to return.
    LOG(INFO) << "Main thread entering thread pool";
    joinRpcThreadpool();

    // In normal operation, we don't expect the thread pool to exit
    LOG(ERROR) << "EVS Hardware Enumerator is shutting down";
    return 1;
}
