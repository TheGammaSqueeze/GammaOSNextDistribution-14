/*
 * Copyright 2021 The Android Open Source Project
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

#pragma once

#include <android/system/suspend/1.0/ISystemSuspend.h>

#include <string>

#include "SystemSuspend.h"

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

using ::android::hardware::hidl_string;
using ::android::hardware::Return;

class WakeLock : public IWakeLock {
   public:
    WakeLock(SystemSuspend* systemSuspend, const std::string& name, int pid);
    ~WakeLock();

    Return<void> release();

   private:
    inline void releaseOnce();
    std::once_flag mReleased;

    SystemSuspend* mSystemSuspend;
    std::string mName;
    int mPid;
};

class SystemSuspendHidl : public ISystemSuspend {
   public:
    SystemSuspendHidl(SystemSuspend* systemSuspend);
    Return<sp<IWakeLock>> acquireWakeLock(WakeLockType type, const hidl_string& name) override;

   private:
    SystemSuspend* mSystemSuspend;
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android
