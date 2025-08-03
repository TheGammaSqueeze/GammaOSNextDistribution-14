/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include "Log.h"

#include <android/binder_interface_utils.h>
#include <fuzzbinder/libbinder_ndk_driver.h>

#include "StatsService.h"
#include "packages/UidMap.h"

using namespace android;
using namespace android::os::statsd;
using ndk::SharedRefBase;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    std::shared_ptr<LogEventFilter> logEventFilter = std::make_shared<LogEventFilter>();
    std::shared_ptr<LogEventQueue> eventQueue =
            std::make_shared<LogEventQueue>(8000 /*buffer limit. Same as StatsD binary*/);
    sp<UidMap> uidMap = UidMap::getInstance();
    shared_ptr<StatsService> binder =
            SharedRefBase::make<StatsService>(uidMap, eventQueue, logEventFilter);
    fuzzService(binder->asBinder().get(), FuzzedDataProvider(data, size));
    return 0;
}
