/*
 * Copyright 2021, The Android Open Source Project
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

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/ProcessState.h>
#include <sched.h>

#include "Composer.h"

using aidl::android::hardware::graphics::composer3::impl::Composer;
using aidl::android::hardware::graphics::composer3::impl::IComposerHal;

using android::base::InitLogging;
using android::base::StderrLogger;
using android::sp;

int main(int /*argc*/, char* argv[]) {
    InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));
    LOG(INFO) << "hwc3 starting up";

    // same as SF main thread
    struct sched_param param = {0};
    param.sched_priority = 2;
    if (sched_setscheduler(0, SCHED_FIFO | SCHED_RESET_ON_FORK, &param) != 0) {
        LOG(ERROR) << "Couldn't set SCHED_FIFO: " << errno;
    }

    std::shared_ptr<Composer> composer = ndk::SharedRefBase::make<Composer>();
    CHECK(composer != nullptr);

    const std::string instance = std::string() + Composer::descriptor + "/default";
    binder_status_t status =
            AServiceManager_addService(composer->asBinder().get(), instance.c_str());
    CHECK(status == STATUS_OK);

    // Thread pool for vendor libbinder for internal vendor services
    android::ProcessState::self()->setThreadPoolMaxThreadCount(2);
    android::ProcessState::self()->startThreadPool();

    // Thread pool for system libbinder (via libbinder_ndk) for aidl services
    // IComposer and IDisplay
    ABinderProcess_setThreadPoolMaxThreadCount(5);
    ABinderProcess_startThreadPool();
    ABinderProcess_joinThreadPool();

    return EXIT_FAILURE;  // should not reach
}
