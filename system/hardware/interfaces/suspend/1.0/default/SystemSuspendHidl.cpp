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

#include "SystemSuspendHidl.h"

#include <hwbinder/IPCThreadState.h>

using ::android::hardware::Void;

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

static inline int getCallingPid() {
    return ::android::hardware::IPCThreadState::self()->getCallingPid();
}

WakeLock::WakeLock(SystemSuspend* systemSuspend, const std::string& name, int pid)
    : mReleased(), mSystemSuspend(systemSuspend), mName(name), mPid(pid) {
    mSystemSuspend->incSuspendCounter(mName);
    mSystemSuspend->updateWakeLockStatOnAcquire(mName, mPid);
}

WakeLock::~WakeLock() {
    releaseOnce();
}

Return<void> WakeLock::release() {
    releaseOnce();
    return Void();
}

void WakeLock::releaseOnce() {
    std::call_once(mReleased, [this]() {
        mSystemSuspend->decSuspendCounter(mName);
        mSystemSuspend->updateWakeLockStatOnRelease(mName, mPid);
    });
}

SystemSuspendHidl::SystemSuspendHidl(SystemSuspend* systemSuspend)
    : mSystemSuspend(systemSuspend) {}

Return<sp<IWakeLock>> SystemSuspendHidl::acquireWakeLock(WakeLockType /* type */,
                                                         const hidl_string& name) {
    auto pid = getCallingPid();
    IWakeLock* wl = new WakeLock{mSystemSuspend, name, pid};
    return wl;
}

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android
