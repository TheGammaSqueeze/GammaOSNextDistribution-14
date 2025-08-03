/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "CarDisplayProxy.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include <string_view>

namespace {

using ::aidl::android::frameworks::automotive::display::implementation::CarDisplayProxy;

constexpr size_t kMaxBinderThreadCount = 1;
constexpr std::string_view kServiceName = "/default";

}  // namespace

int main([[maybe_unused]] int argc, [[maybe_unused]] char** argv) {
    LOG(INFO) << "cardisplayproxy service is starting";
    std::shared_ptr<CarDisplayProxy> service = ::ndk::SharedRefBase::make<CarDisplayProxy>();

    // Registers our service
    const auto instanceName = std::string(CarDisplayProxy::descriptor) + std::string(kServiceName);
    binder_exception_t status =
            AServiceManager_addService(service->asBinder().get(), instanceName.data());
    if (status != EX_NONE) {
        LOG(FATAL) << "Error while registering cardisplayproxy service: "
                   << ::android::statusToString(status).data();
    }

    // Prepares the RPC serving thread pool
    if (!ABinderProcess_setThreadPoolMaxThreadCount(kMaxBinderThreadCount)) {
        LOG(ERROR) << "Filed to set the binder thread pool";
        return EXIT_FAILURE;
    }
    ABinderProcess_startThreadPool();
    LOG(INFO) << "Main thread entering thread pool";

    // In normal operation, we do not expect the thread pool to exit.
    ABinderProcess_joinThreadPool();
    LOG(ERROR) << "cardisplayproxyd is shutting down.";

    return EXIT_SUCCESS;
}
