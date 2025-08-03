/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "CarTelemetryImpl.h"
#include "CarTelemetryInternalImpl.h"
#include "LooperWrapper.h"
#include "TelemetryServer.h"

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <utils/Looper.h>

#include <thread>  // NOLINT(build/c++11)

using ::android::automotive::telemetry::CarTelemetryImpl;
using ::android::automotive::telemetry::CarTelemetryInternalImpl;
using ::android::automotive::telemetry::LooperWrapper;
using ::android::automotive::telemetry::TelemetryServer;

// TODO(b/174608802): handle SIGQUIT/SIGTERM

constexpr const char kCarTelemetryServiceName[] =
        "android.frameworks.automotive.telemetry.ICarTelemetry/default";
constexpr const char kCarTelemetryInternalServiceName[] =
        "android.automotive.telemetry.internal.ICarTelemetryInternal/default";

// The min delay between each ICarDataListener.onCarDataReceived() calls. It's needed to avoid
// too frequently making binder transactions.
// Binder has <1MS latency for 10KB data.
// TODO(b/186477983): improve sending car data after implementing CarTelemetryService.
// TODO(b/183444070): make it configurable using sysprop
constexpr const std::chrono::nanoseconds kPushCarDataDelayNs = 10ms;

// TODO(b/183444070): make it configurable using sysprop
// CarData count limit in the RingBuffer. In worst case it will use kMaxBufferSize * 10Kb memory,
// which is ~ 1MB.
const int kMaxBufferSize = 100;

int main(void) {
    LOG(INFO) << "Starting cartelemetryd";

    LooperWrapper looper(android::Looper::prepare(/* opts= */ 0));
    TelemetryServer server(&looper, kPushCarDataDelayNs, kMaxBufferSize);
    std::shared_ptr<CarTelemetryImpl> telemetry =
            ndk::SharedRefBase::make<CarTelemetryImpl>(&server);
    std::shared_ptr<CarTelemetryInternalImpl> telemetryInternal =
            ndk::SharedRefBase::make<CarTelemetryInternalImpl>(&server);

    // Wait for the service manager before starting ICarTelemetry service.
    android::base::WaitForProperty("init.svc.servicemanager", "running");

    LOG(VERBOSE) << "Registering " << kCarTelemetryServiceName;
    binder_exception_t exception =
            ::AServiceManager_addService(telemetry->asBinder().get(), kCarTelemetryServiceName);
    if (exception != ::EX_NONE) {
        LOG(FATAL) << "Unable to register " << kCarTelemetryServiceName
                   << ", exception=" << exception;
    }

    LOG(VERBOSE) << "Registering " << kCarTelemetryInternalServiceName;
    exception = ::AServiceManager_addService(telemetryInternal->asBinder().get(),
                                             kCarTelemetryInternalServiceName);
    if (exception != ::EX_NONE) {
        LOG(FATAL) << "Unable to register " << kCarTelemetryInternalServiceName
                   << ", exception=" << exception;
    }

    LOG(VERBOSE) << "Services are registered, starting thread pool";
    ::ABinderProcess_startThreadPool();  // Starts the default 15 binder threads.

    LOG(VERBOSE) << "Running the server.";
    // Loop forever -- pushing data to ICarDataListener runs on this thread, and the binder calls
    // remain responsive in their pool of one thread.
    while (true) {
        looper.pollAll(/* timeoutMillis= */ -1);
    }
    return 1;  // never reaches
}
