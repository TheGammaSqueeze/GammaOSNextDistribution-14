/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <log/log.h>

#include "Gnss.h"

int main(int /* argc */, char* /* argv */ []) {
    using ::aidl::android::hardware::gnss::implementation::Gnss;

    ABinderProcess_setThreadPoolMaxThreadCount(2);
    ABinderProcess_startThreadPool();

    const auto gnss = ndk::SharedRefBase::make<Gnss>();

    {
        const std::string instance = std::string(Gnss::descriptor) + "/default";
        if (AServiceManager_registerLazyService(gnss->asBinder().get(),
                                                instance.c_str()) != STATUS_OK) {
          ALOGE("%s:%d: Could not register '%s'", __func__, __LINE__, instance.c_str());
          return android::NO_INIT;
        }
    }

    ABinderProcess_joinThreadPool();
    return 0;   // lazy HALs do exit.
}
