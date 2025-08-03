/**
 * Copyright (c) 2020, The Android Open Source Project
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

#define LOG_TAG "carpowerpolicyd"

#include "CarPowerPolicyServer.h"

#include <android-base/result.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <log/log.h>
#include <utils/Looper.h>
#include <utils/StrongPointer.h>

#include <signal.h>

using android::IPCThreadState;
using android::Looper;
using android::ProcessState;
using android::sp;
using android::frameworks::automotive::powerpolicy::CarPowerPolicyServer;

namespace {

const size_t kMaxBinderThreadCount = 2;

}  // namespace

int main(int /*argc*/, char** /*argv*/) {
    // Set up the binder
    sp<ProcessState> ps(ProcessState::self());
    ps->setThreadPoolMaxThreadCount(kMaxBinderThreadCount);
    ps->startThreadPool();
    ps->giveThreadPoolName();
    IPCThreadState::self()->disableBackgroundScheduling(true);

    // Set up the looper
    sp<Looper> looper(Looper::prepare(/*opts=*/0));

    // Start the services
    auto result = CarPowerPolicyServer::startService(looper);
    if (!result.ok()) {
        ALOGE("Failed to start service: %s", result.error().message().c_str());
        CarPowerPolicyServer::terminateService();
        exit(result.error().code());
    }

    while (true) {
        looper->pollAll(/*timeoutMillis=*/-1);
    }
    ALOGW("Car power policy server escaped from its loop.");

    return 0;
}
