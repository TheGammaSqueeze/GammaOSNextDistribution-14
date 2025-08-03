/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "CanClient.h"

#include <android-base/logging.h>
#include <android/hardware/automotive/can/1.0/ICanMessageListener.h>
#include <android/hidl/manager/1.1/IServiceManager.h>
#include <hidl-utils/hidl-utils.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {
namespace utils {

using hidl::manager::V1_1::IServiceManager;
using hidl::manager::V1_0::IServiceNotification;

CanClient::CanClient(const std::string& busName) : mBusName(busName) {}

::ndk::ScopedAStatus CanClient::start() {
    VehicleBus::start();
    LOG(VERBOSE) << "Waiting for ICanBus/" << mBusName;
    ICanBus::registerForNotifications(mBusName, static_cast<IServiceNotification*>(this));
    return ::ndk::ScopedAStatus::ok();
}

CanClient::~CanClient() {
    auto manager = IServiceManager::getService();
    CHECK(manager != nullptr) << "Can't fetch IServiceManager";
    manager->unregisterForNotifications("", "", static_cast<IServiceNotification*>(this));

    close();
}

void CanClient::onReady(const sp<ICanBus>&) {}

Return<void> CanClient::onRegistration(const hidl_string&, const hidl_string& name, bool) {
    LOG(VERBOSE) << "ICanBus/" << name << " is registered";
    auto bus = ICanBus::tryGetService(name);
    if (bus == nullptr) {
        LOG(WARNING) << "Can't fetch ICanBus/" << name;
        return {};
    }

    std::lock_guard<std::mutex> lck(mCanBusGuard);
    if (mCanBus) {
        LOG(DEBUG) << "Bus " << mBusName << " service is already registered";
        return {};
    }
    mCanBus = bus;

    // TODO(b/146214370): configure CAN message filtering (see first argument to listen())
    Result halResult;
    sp<ICloseHandle> listenerCloseHandle;
    // TODO(b/146214370): check why the cast requires transfer SEPolicy permission
    auto res = bus->listen({}, static_cast<ICanMessageListener*>(this),
            hidl_utils::fill(&halResult, &listenerCloseHandle));
    mListenerCloseHandle = CloseHandleWrapper(listenerCloseHandle);
    if (!res.isOk() || halResult != Result::OK) {
        LOG(WARNING) << "Listen call failed";
        close();
        return {};
    }

    auto errRes = bus->listenForErrors(static_cast<ICanErrorListener*>(this));
    if (!errRes.isOk()) {
        LOG(WARNING) << "listenForErrors call failed";
        close();
        return {};
    }
    mErrorCloseHandle = CloseHandleWrapper(errRes);

    if (!bus->linkToDeath(static_cast<hidl_death_recipient*>(this), 0).withDefault(false)) {
        LOG(WARNING) << "linkToDeath failed";
        close();
        return {};
    }

    LOG(INFO) << "Bus " << mBusName << " successfully configured";
    onReady(mCanBus);
    return {};
}

void CanClient::serviceDied(uint64_t, const wp<hidl::base::V1_0::IBase>&) {
    onError(ErrorEvent::INTERFACE_DOWN, true);
}

Return<void> CanClient::onError(ErrorEvent error, bool isFatal) {
    if (!isFatal) {
        LOG(VERBOSE) << "Got non-fatal error from CAN bus HAL: " << toString(error);
        return {};
    }

    LOG(DEBUG) << "Got fatal error from CAN bus HAL: " << toString(error);

    if (!close()) {
        LOG(WARNING) << "Service is dead already";
        return {};
    }
    LOG(INFO) << "Bus " << mBusName << " became unavailable, waiting for it to come back...";

    return {};
}

bool CanClient::close() {
    std::lock_guard<std::mutex> lck(mCanBusGuard);
    mListenerCloseHandle.close();
    mErrorCloseHandle.close();
    if (mCanBus == nullptr) return false;
    if (!mCanBus->unlinkToDeath(static_cast<hidl_death_recipient*>(this)).isOk()) {
        LOG(WARNING) << "unlinkToDeath failed";
    }
    mCanBus = nullptr;
    return true;
}

}  // namespace utils
}  // namespace V1_0
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android
