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

#include <VehicleBus.h>

#include <android/binder_auto_utils.h>

#include <android-base/logging.h>

namespace aidl::android::hardware::automotive::vehicle {

VehicleBus::VehicleBus() {
    mDeathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(&VehicleBus::onBinderDied));
}

VehicleBus::~VehicleBus() {}

::ndk::ScopedAStatus VehicleBus::start() {
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus VehicleBus::setOnNewPropValuesCallback(
    const std::shared_ptr<IVehicleBusCallback>& callback) {
    std::lock_guard<std::mutex> g(mLock);

    if (mVehicleBusCallback) {
        return ::ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(
            ERROR_INVALID_OPERATION, "Can't set callback twice!");

    }

    AIBinder_linkToDeath(callback->asBinder().get(), mDeathRecipient.get(),
                         static_cast<void*>(this));
    mVehicleBusCallback = callback;
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus VehicleBus::unsetOnNewPropValuesCallback(
        const std::shared_ptr<IVehicleBusCallback>& callback) {
    std::lock_guard<std::mutex> g(mLock);

    if (mVehicleBusCallback != callback) {
        return ::ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(
            ERROR_INVALID_OPERATION, "Invalid callback argument");
    }

    AIBinder_unlinkToDeath(callback->asBinder().get(), mDeathRecipient.get(),
                           static_cast<void*>(this));
    mVehicleBusCallback = nullptr;
    return ::ndk::ScopedAStatus::ok();
}

void VehicleBus::sendPropertyEvent(
    const std::vector<VehiclePropValue>& propValues) {
    std::lock_guard<std::mutex> g(mLock);

    if (mVehicleBusCallback == nullptr) {
        LOG(ERROR) << "Callback isn't set";
        return;
    }
    mVehicleBusCallback->onNewPropValues(propValues);
}

void VehicleBus::updateTimestamps(std::vector<VehiclePropValue>& propValues,
                                  uint64_t timestamp) {
    for (auto&& pv : propValues) {
        pv.timestamp = timestamp;
    }
}

void VehicleBus::onBinderDied(void* cookie) {
    VehicleBus* server = reinterpret_cast<VehicleBus*>(cookie);
    server->handleBinderDied();
}

void VehicleBus::handleBinderDied() {
    std::lock_guard<std::mutex> g(mLock);
    mVehicleBusCallback = nullptr;
    LOG(ERROR) << "Received onBinderDied on registered VehicleBusCallback";
}

};  // namespace aidl::android::hardware::automotive::vehicle
