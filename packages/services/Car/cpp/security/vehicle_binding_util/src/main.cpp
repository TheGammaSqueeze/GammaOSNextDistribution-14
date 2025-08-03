/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "VehicleBindingUtil.h"

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <binder/IServiceManager.h>
#include <binder/Status.h>

#include <IVhalClient.h>

#include <iostream>
#include <map>
#include <string>
#include <thread>  // NOLINT(build/c++11)

namespace {

using ::android::defaultServiceManager;
using ::android::automotive::security::BindingStatus;
using ::android::automotive::security::DefaultCsrng;
using ::android::automotive::security::DefaultExecutor;
using ::android::frameworks::automotive::vhal::IVhalClient;

static int printHelp(int argc, char* argv[]);
static int setBinding(int /*argc*/, char*[] /*argv*/);

constexpr int64_t SLEEP_TIME_MILLISECONDS = 100;
constexpr int64_t TIMEOUT_MILLISECONDS = 30000;

// Avoid calling complex destructor on cleanup.
const auto& subcommandTable = *new std::map<std::string, std::function<int(int, char*[])>>{
        {"help", printHelp},
        {"set_binding", setBinding},
};

static int setBinding(int /*argc*/, char*[] /*argv*/) {
    std::shared_ptr<IVhalClient> service = IVhalClient::tryCreate();
    size_t retryCount = 0;
    while (service == nullptr && retryCount < TIMEOUT_MILLISECONDS / SLEEP_TIME_MILLISECONDS) {
        service = IVhalClient::tryCreate();
        std::this_thread::sleep_for(std::chrono::milliseconds(SLEEP_TIME_MILLISECONDS));
        retryCount++;
    }

    if (service == nullptr) {
        LOG(ERROR) << "Timeout waiting for VHAL";
        return static_cast<int>(BindingStatus::WAIT_VHAL_TIMEOUT);
    }

    auto status = setVehicleBindingSeed(service, DefaultExecutor{}, DefaultCsrng{});
    if (status != BindingStatus::OK) {
        LOG(ERROR) << "Unable to set the binding seed. Encryption keys are not "
                   << "bound to the platform.";
        return static_cast<int>(status);
    }

    return 0;
}

static int printHelp(int /*argc*/, char* argv[]) {
    std::cout << "Usage: " << argv[0] << " <subcommand> [args]" << std::endl
              << "Valid subcommands: " << std::endl;
    for (const auto& i : subcommandTable) {
        std::cout << "    " << i.first << std::endl;
    }
    return 0;
}

}  // namespace

int main(int argc, char* argv[]) {
    setenv("ANDROID_LOG_TAGS", "*:v", 1);
    android::base::InitLogging(argv,
                               (getppid() == 1) ? &android::base::KernelLogger
                                                : &android::base::StderrLogger);
    if (argc < 2) {
        LOG(ERROR) << "Please specify a subcommand.";
        printHelp(argc, argv);
        return -1;
    }

    auto subcommand = subcommandTable.find(argv[1]);
    if (subcommand == subcommandTable.end()) {
        LOG(ERROR) << "Invalid subcommand: " << argv[1];
        printHelp(argc, argv);
        return -1;
    }

    return subcommand->second(argc, argv);
}
