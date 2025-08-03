/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "HidlEnumerator.h"
#include "ServiceNames.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <hidl/HidlTransportSupport.h>

#include <string_view>

namespace {

using ::aidl::android::automotive::evs::implementation::Enumerator;
using ::aidl::android::automotive::evs::implementation::HidlEnumerator;
using ::android::hardware::configureRpcThreadpool;

const std::string kSeparator = "/";

void startService(const std::string_view& hardwareServiceName,
                  const std::string_view& managerServiceName) {
    LOG(INFO) << "EVS managed service connecting to hardware service at " << hardwareServiceName;
    std::shared_ptr<Enumerator> aidlService = ::ndk::SharedRefBase::make<Enumerator>();
    if (!aidlService->init(hardwareServiceName)) {
        LOG(FATAL) << "Error while connecting to the hardware service, " << hardwareServiceName;
    }

    // Register our service -- if somebody is already registered by our name,
    // they will be killed (their thread pool will throw an exception).
    const std::string instanceName =
            std::string(Enumerator::descriptor) + kSeparator + std::string(managerServiceName);
    LOG(INFO) << "EVS managed service is starting as " << instanceName;
    auto aidlStatus =
            AServiceManager_addService(aidlService->asBinder().get(), instanceName.data());
    if (aidlStatus != EX_NONE) {
        LOG(FATAL) << "Error while registering EVS manager service: "
                   << ::android::statusToString(aidlStatus);
    }

    // We also register our service to the hwservice manager.  This is an
    // optional functionality so we ignore any errors.
    configureRpcThreadpool(/* maxThreads = */ 1, /* callerWillJoin = */ false);
    ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator> hidlService =
            new (std::nothrow) HidlEnumerator(aidlService);
    if (!hidlService) {
        LOG(WARNING) << "Failed to initialize HIDL service";
    } else {
        auto hidlStatus = hidlService->registerAsService(managerServiceName.data());
        if (hidlStatus != ::android::OK) {
            LOG(WARNING) << "Failed to register EVS manager service to the hwservice manager, "
                         << ::android::statusToString(hidlStatus);
        }
    }

    LOG(INFO) << "Registration complete";
}

}  // namespace

int main(int argc, char** argv) {
    LOG(INFO) << "EVS manager starting";

    // Set up default behavior, then check for command line options
    bool printHelp = false;
    std::string_view evsHardwareServiceName = kHardwareEnumeratorName;
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
        return EXIT_SUCCESS;
    }

    // Prepare the RPC serving thread pool.  We're configuring it with no additional
    // threads beyond the main thread which will "join" the pool below.
    if (!ABinderProcess_setThreadPoolMaxThreadCount(/* numThreads = */ 1)) {
        LOG(ERROR) << "Failed to set thread pool";
        return EXIT_FAILURE;
    }

    // The connection to the underlying hardware service must happen on a dedicated thread to ensure
    // that the hwbinder response can be processed by the thread pool without blocking.
    std::thread registrationThread(startService, evsHardwareServiceName, kManagedEnumeratorName);

    // Send this main thread to become a permanent part of the thread pool.
    // This is not expected to return.
    ABinderProcess_startThreadPool();
    LOG(INFO) << "Main thread entering thread pool";

    // In normal operation, we don't expect the thread pool to exit
    ABinderProcess_joinThreadPool();
    LOG(ERROR) << "EVS Hardware Enumerator is shutting down";

    return EXIT_SUCCESS;
}
