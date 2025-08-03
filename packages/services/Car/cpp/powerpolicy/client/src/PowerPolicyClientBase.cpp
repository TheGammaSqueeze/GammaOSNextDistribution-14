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

#include "PowerPolicyClientBase.h"

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <utils/SystemClock.h>

#include <algorithm>
#include <memory>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

namespace aafap = ::aidl::android::frameworks::automotive::powerpolicy;

using aafap::CarPowerPolicy;
using aafap::CarPowerPolicyFilter;
using aafap::ICarPowerPolicyChangeCallback;
using aafap::ICarPowerPolicyServer;
using aafap::PowerComponent;
using android::uptimeMillis;
using android::base::Error;
using android::base::Result;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

namespace {

constexpr const char* kPowerPolicyServerInterface =
        "android.frameworks.automotive.powerpolicy.ICarPowerPolicyServer/default";

constexpr std::chrono::milliseconds kPowerPolicyDaemomFindMarginalTimeMs = 500ms;
constexpr int32_t kMaxConnectionAttempt = 5;

}  // namespace

bool hasComponent(const std::vector<PowerComponent>& components, PowerComponent component) {
    std::vector<PowerComponent>::const_iterator it =
            std::find(components.cbegin(), components.cend(), component);
    return it != components.cend();
}

PowerPolicyClientBase::PowerPolicyClientBase() :
      mDeathRecipient(AIBinder_DeathRecipient_new(PowerPolicyClientBase::onBinderDied)) {}

PowerPolicyClientBase::~PowerPolicyClientBase() {
    if (mPolicyServer != nullptr) {
        auto status =
                ScopedAStatus::fromStatus(AIBinder_unlinkToDeath(mPolicyServer->asBinder().get(),
                                                                 mDeathRecipient.get(), nullptr));
        if (!status.isOk()) {
            LOG(WARNING) << "Unlinking from death recipient failed";
        }
    }
}

void PowerPolicyClientBase::onBinderDied(void* cookie) {
    PowerPolicyClientBase* client = static_cast<PowerPolicyClientBase*>(cookie);
    client->handleBinderDeath();
}

void PowerPolicyClientBase::init() {
    mConnectionThread = std::thread([this]() {
        Result<void> ret;
        int attemptCount = 1;
        while (attemptCount <= kMaxConnectionAttempt) {
            ret = connectToDaemon();
            if (ret.ok()) {
                return;
            }
            LOG(WARNING) << "Connection attempt #" << attemptCount << " failed: " << ret.error();
            attemptCount++;
        }
        onInitFailed();
    });
}

void PowerPolicyClientBase::handleBinderDeath() {
    LOG(INFO) << "Power policy daemon died. Reconnecting...";
    {
        std::unique_lock writeLock(mRWMutex);
        mPolicyServer = nullptr;
    }
    init();
}

Result<void> PowerPolicyClientBase::connectToDaemon() {
    {
        std::shared_lock readLock(mRWMutex);
        if (mPolicyServer.get() != nullptr) {
            LOG(INFO) << "Power policy daemon is already connected";
            return {};
        }
    }
    int64_t currentUptime = uptimeMillis();
    SpAIBinder binder(AServiceManager_getService(kPowerPolicyServerInterface));
    if (binder.get() == nullptr) {
        return Error() << "Failed to get car power policy daemon";
    }
    int64_t elapsedTime = uptimeMillis() - currentUptime;
    if (elapsedTime > kPowerPolicyDaemomFindMarginalTimeMs.count()) {
        LOG(WARNING) << "Finding power policy daemon took too long(" << elapsedTime << " ms)";
    }
    std::shared_ptr<ICarPowerPolicyServer> server = ICarPowerPolicyServer::fromBinder(binder);
    if (server == nullptr) {
        return Error() << "Failed to connect to car power policy daemon";
    }
    binder = this->asBinder();
    if (binder.get() == nullptr) {
        return Error() << "Failed to get car power policy client binder object";
    }
    auto status = ScopedAStatus::fromStatus(
            AIBinder_linkToDeath(server->asBinder().get(), mDeathRecipient.get(), this));
    if (!status.isOk()) {
        return Error() << "Linking to death recipient failed";
    }

    std::shared_ptr<ICarPowerPolicyChangeCallback> client =
            ICarPowerPolicyChangeCallback::fromBinder(binder);
    const auto& components = getComponentsOfInterest();
    const auto& customComponents = getCustomComponentsOfInterest();
    CarPowerPolicyFilter filter;
    filter.components = components;
    filter.customComponents = customComponents;
    status = server->registerPowerPolicyChangeCallback(client, filter);
    if (!status.isOk()) {
        status = ScopedAStatus::fromStatus(AIBinder_unlinkToDeath(server->asBinder().get(),
                mDeathRecipient.get(), nullptr));
        if (!status.isOk()) {
            LOG(WARNING) << "Unlinking from death recipient failed";
        }
        return Error() << "Register power policy change challback failed";
    }

    {
        std::unique_lock writeLock(mRWMutex);
        mPolicyServer = server;
    }

    LOG(INFO) << "Connected to power policy daemon";
    return {};
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
