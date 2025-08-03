/*
 * Copyright 2019 The Android Open Source Project
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

#ifndef ANDROID_SYSTEM_SYSTEM_SUSPEND_CONTROL_SERVICE_H
#define ANDROID_SYSTEM_SYSTEM_SUSPEND_CONTROL_SERVICE_H

#include <android/system/suspend/BnSuspendControlService.h>
#include <android/system/suspend/internal/BnSuspendControlServiceInternal.h>
#include <android/system/suspend/internal/SuspendInfo.h>
#include <android/system/suspend/internal/WakeLockInfo.h>
#include <android/system/suspend/internal/WakeupInfo.h>

using ::android::system::suspend::BnSuspendControlService;
using ::android::system::suspend::ISuspendCallback;
using ::android::system::suspend::IWakelockCallback;
using ::android::system::suspend::internal::BnSuspendControlServiceInternal;
using ::android::system::suspend::internal::SuspendInfo;
using ::android::system::suspend::internal::WakeLockInfo;
using ::android::system::suspend::internal::WakeupInfo;

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

class SystemSuspend;

class SuspendControlService : public BnSuspendControlService,
                              public virtual IBinder::DeathRecipient {
   public:
    SuspendControlService() = default;
    ~SuspendControlService() override = default;

    binder::Status registerCallback(const sp<ISuspendCallback>& callback,
                                    bool* _aidl_return) override;
    binder::Status registerWakelockCallback(const sp<IWakelockCallback>& callback,
                                            const std::string& name, bool* _aidl_return) override;

    void binderDied(const wp<IBinder>& who) override;

    void notifyWakelock(const std::string& name, bool isAcquired);
    void notifyWakeup(bool success, std::vector<std::string>& wakeupReasons);

   private:
    std::map<std::string, std::vector<sp<IWakelockCallback>>> mWakelockCallbacks;
    std::mutex mCallbackLock;
    std::mutex mWakelockCallbackLock;
    std::vector<sp<ISuspendCallback>> mCallbacks;
    const std::vector<sp<ISuspendCallback>>::iterator findCb(const wp<IBinder>& cb) {
        return std::find_if(
            mCallbacks.begin(), mCallbacks.end(),
            [&cb](const sp<ISuspendCallback>& i) { return cb == IInterface::asBinder(i); });
    }
};

class SuspendControlServiceInternal : public BnSuspendControlServiceInternal {
   public:
    SuspendControlServiceInternal() = default;
    ~SuspendControlServiceInternal() override = default;

    binder::Status enableAutosuspend(const sp<IBinder>& token, bool* _aidl_return) override;
    binder::Status forceSuspend(bool* _aidl_return) override;
    binder::Status getSuspendStats(SuspendInfo* _aidl_return) override;
    binder::Status getWakeLockStats(std::vector<WakeLockInfo>* _aidl_return) override;
    binder::Status getWakeupStats(std::vector<WakeupInfo>* _aidl_return) override;

    void setSuspendService(const wp<SystemSuspend>& suspend);
    status_t dump(int fd, const Vector<String16>& args) override;

   private:
    wp<SystemSuspend> mSuspend;
};

}  // namespace V1_0
}  // namespace suspend
}  // namespace system
}  // namespace android

#endif  // ANDROID_SYSTEM_SYSTEM_SUSPEND_CONTROL_SERVICE_H
