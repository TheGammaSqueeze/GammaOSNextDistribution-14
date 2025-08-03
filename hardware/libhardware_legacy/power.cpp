/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "power"
#define ATRACE_TAG ATRACE_TAG_POWER

#include <hardware_legacy/power.h>
#include <wakelock/wakelock.h>

#include <aidl/android/system/suspend/ISystemSuspend.h>
#include <aidl/android/system/suspend/IWakeLock.h>
#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <utils/Trace.h>

#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>

using aidl::android::system::suspend::ISystemSuspend;
using aidl::android::system::suspend::IWakeLock;
using aidl::android::system::suspend::WakeLockType;

static std::mutex gLock;
static std::unordered_map<std::string, std::shared_ptr<IWakeLock>> gWakeLockMap;

static const std::shared_ptr<ISystemSuspend> getSystemSuspendServiceOnce() {
    static std::shared_ptr<ISystemSuspend> suspendService =
        []() -> std::shared_ptr<ISystemSuspend> {
        std::string suspendServiceName =
            ISystemSuspend::descriptor + std::string("/default");
        if (!AServiceManager_isDeclared(suspendServiceName.c_str())) {
            return nullptr;
        }
        return ISystemSuspend::fromBinder(ndk::SpAIBinder(
            AServiceManager_waitForService(suspendServiceName.c_str())));
    }();
    return suspendService;
}

int acquire_wake_lock(int, const char* id) {
    ATRACE_CALL();
    const auto suspendService = getSystemSuspendServiceOnce();
    if (!suspendService) {
        LOG(ERROR) << "Failed to get SystemSuspend service";
        return -1;
    }

    std::lock_guard<std::mutex> l{gLock};
    if (!gWakeLockMap[id]) {
        std::shared_ptr<IWakeLock> wl = nullptr;
        auto status = suspendService->acquireWakeLock(WakeLockType::PARTIAL, id, &wl);
        // It's possible that during device shutdown SystemSuspend service has already exited.
        // Check that the wakelock object is not null.
        if (!wl) {
            LOG(ERROR) << "ISuspendService::acquireWakeLock() call failed: "
                       << status.getDescription();
            return -1;
        } else {
            gWakeLockMap[id] = wl;
        }
    }
    return 0;
}

int release_wake_lock(const char* id) {
    ATRACE_CALL();
    std::lock_guard<std::mutex> l{gLock};
    if (gWakeLockMap[id]) {
        // Ignore errors on release() call since hwbinder driver will clean up the underlying object
        // once we clear the corresponding shared_ptr.
        auto status = gWakeLockMap[id]->release();
        if (!status.isOk()) {
            LOG(ERROR) << "IWakeLock::release() call failed: " << status.getDescription();
        }
        gWakeLockMap[id] = nullptr;
        return 0;
    }
    return -1;
}

namespace android {
namespace wakelock {

class WakeLock::WakeLockImpl {
  public:
    WakeLockImpl(const std::string& name);
    ~WakeLockImpl();
    bool acquireOk();

  private:
    std::shared_ptr<IWakeLock> mWakeLock;
};

std::optional<WakeLock> WakeLock::tryGet(const std::string& name) {
    std::unique_ptr<WakeLockImpl> wlImpl = std::make_unique<WakeLockImpl>(name);
    if (wlImpl->acquireOk()) {
        return { std::move(wlImpl) };
    } else {
        LOG(ERROR) << "Failed to acquire wakelock: " << name;
        return {};
    }
}

WakeLock::WakeLock(std::unique_ptr<WakeLockImpl> wlImpl) : mImpl(std::move(wlImpl)) {}

WakeLock::~WakeLock() = default;

WakeLock::WakeLockImpl::WakeLockImpl(const std::string& name) : mWakeLock(nullptr) {
    const auto suspendService = getSystemSuspendServiceOnce();
    if (!suspendService) {
        LOG(ERROR) << "Failed to get SystemSuspend service";
        return;
    }

    std::shared_ptr<IWakeLock> wl = nullptr;
    auto status = suspendService->acquireWakeLock(WakeLockType::PARTIAL, name, &wl);
    // It's possible that during device shutdown SystemSuspend service has already exited.
    // Check that the wakelock object is not null.
    if (!wl) {
        LOG(ERROR) << "ISuspendService::acquireWakeLock() call failed: " << status.getDescription();
    } else {
        mWakeLock = wl;
    }
}

WakeLock::WakeLockImpl::~WakeLockImpl() {
    if (!acquireOk()) {
        return;
    }
    auto status = mWakeLock->release();
    if (!status.isOk()) {
        LOG(ERROR) << "IWakeLock::release() call failed: " << status.getDescription();
    }
}

bool WakeLock::WakeLockImpl::acquireOk() {
    return mWakeLock != nullptr;
}

}  // namespace wakelock
}  // namespace android
