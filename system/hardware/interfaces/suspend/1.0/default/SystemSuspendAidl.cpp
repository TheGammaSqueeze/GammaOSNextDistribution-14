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

#include "SystemSuspendAidl.h"

#include <binder/IPCThreadState.h>

namespace aidl {
namespace android {
namespace system {
namespace suspend {

static inline int getCallingPid() {
    return ::android::IPCThreadState::self()->getCallingPid();
}

WakeLock::WakeLock(SystemSuspend* systemSuspend, const std::string& name, int pid)
    : mReleased(), mSystemSuspend(systemSuspend), mName(name), mPid(pid) {
    mSystemSuspend->incSuspendCounter(mName);
}

WakeLock::~WakeLock() {
    releaseOnce();
}

ndk::ScopedAStatus WakeLock::release() {
    releaseOnce();
    return ndk::ScopedAStatus::ok();
}

void WakeLock::releaseOnce() {
    std::call_once(mReleased, [this]() {
        mSystemSuspend->decSuspendCounter(mName);
        mSystemSuspend->updateWakeLockStatOnRelease(mName, mPid);
    });
}

SystemSuspendAidl::SystemSuspendAidl(SystemSuspend* systemSuspend)
    : mSystemSuspend(systemSuspend) {}

ndk::ScopedAStatus SystemSuspendAidl::acquireWakeLock(WakeLockType /* type */,
                                                      const std::string& name,
                                                      std::shared_ptr<IWakeLock>* _aidl_return) {
    auto pid = getCallingPid();
    if (_aidl_return == nullptr) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_ILLEGAL_ARGUMENT));
    }
    *_aidl_return = ndk::SharedRefBase::make<WakeLock>(mSystemSuspend, name, pid);
    mSystemSuspend->updateWakeLockStatOnAcquire(name, pid);
    return ndk::ScopedAStatus::ok();
}

}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
