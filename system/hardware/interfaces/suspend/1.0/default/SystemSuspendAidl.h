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

#include <aidl/android/system/suspend/BnSystemSuspend.h>
#include <aidl/android/system/suspend/BnWakeLock.h>

#include <string>

#include "SystemSuspend.h"

namespace aidl {
namespace android {
namespace system {
namespace suspend {

using ::android::system::suspend::V1_0::SystemSuspend;

class WakeLock : public BnWakeLock {
   public:
    WakeLock(SystemSuspend* systemSuspend, const std::string& name, int pid);
    ~WakeLock();

    ndk::ScopedAStatus release() override;

   private:
    inline void releaseOnce();
    std::once_flag mReleased;

    SystemSuspend* mSystemSuspend;
    std::string mName;
    int mPid;
};

class SystemSuspendAidl : public BnSystemSuspend {
   public:
    SystemSuspendAidl(SystemSuspend* systemSuspend);
    ndk::ScopedAStatus acquireWakeLock(WakeLockType type, const std::string& name,
                                       std::shared_ptr<IWakeLock>* _aidl_return) override;

   private:
    SystemSuspend* mSystemSuspend;
};

}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
