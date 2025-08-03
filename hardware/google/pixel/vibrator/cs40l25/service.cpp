/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <log/log.h>

#include "Hardware.h"
#include "Stats.h"
#include "Vibrator.h"

using ::aidl::android::hardware::vibrator::HwApi;
using ::aidl::android::hardware::vibrator::HwCal;
using ::aidl::android::hardware::vibrator::StatsApi;
using ::aidl::android::hardware::vibrator::Vibrator;
using ::android::defaultServiceManager;
using ::android::ProcessState;
using ::android::sp;
using ::android::String16;

#if !defined(VIBRATOR_NAME)
#define VIBRATOR_NAME "default"
#endif

int main() {
    auto svc = ndk::SharedRefBase::make<Vibrator>(
            std::make_unique<HwApi>(), std::make_unique<HwCal>(), std::make_unique<StatsApi>());
    const auto svcName = std::string() + svc->descriptor + "/" + VIBRATOR_NAME;

    ProcessState::initWithDriver("/dev/vndbinder");

    auto svcBinder = svc->asBinder();
    binder_status_t status = AServiceManager_addService(svcBinder.get(), svcName.c_str());
    LOG_ALWAYS_FATAL_IF(status != STATUS_OK);

    ProcessState::self()->setThreadPoolMaxThreadCount(1);
    ProcessState::self()->startThreadPool();

    ABinderProcess_setThreadPoolMaxThreadCount(0);
    ABinderProcess_joinThreadPool();

    return EXIT_FAILURE;  // should not reach
}
