/**
 * Copyright 2022, The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_LOOPERWRAPPER_H
#define CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_LOOPERWRAPPER_H

#include <utils/Looper.h>
#include <utils/RefBase.h>
#include <utils/Timers.h>

namespace aidl::android::automotive::evs::implementation {

// This class wraps around android::Looper methods.  Please refer to
// utils/Looper.h for the details.
class LooperWrapper : public ::android::RefBase {
public:
    LooperWrapper() : mLooper(nullptr) {}
    virtual ~LooperWrapper() {}

    void setLooper(::android::sp<::android::Looper> looper) { mLooper = looper; }
    void wake();
    virtual nsecs_t now() { return systemTime(SYSTEM_TIME_MONOTONIC); }
    virtual int pollAll(int timeoutMillis);
    virtual void sendMessage(const ::android::sp<::android::MessageHandler>& handler,
                             const ::android::Message& message);
    virtual void sendMessageAtTime(nsecs_t uptime,
                                   const ::android::sp<::android::MessageHandler>& handler,
                                   const ::android::Message& message);
    virtual void removeMessages(const ::android::sp<::android::MessageHandler>& handler);

protected:
    ::android::sp<::android::Looper> mLooper;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_LOOPERWRAPPER_H
