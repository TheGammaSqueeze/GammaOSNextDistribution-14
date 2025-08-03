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

#pragma once

#include <aidl/device/generic/car/emulator/BnVehicleBus.h>
#include <aidl/device/generic/car/emulator/IVehicleBusCallback.h>

#include <android-base/thread_annotations.h>

namespace aidl::android::hardware::automotive::vehicle {

class VehicleBus : public device::generic::car::emulator::BnVehicleBus {
public:
    using IVehicleBusCallback = aidl::device::generic::car::emulator::IVehicleBusCallback;

    enum {
        ERROR_INVALID_OPERATION = 1,
    };

    VehicleBus();
    virtual ~VehicleBus();

    virtual ::ndk::ScopedAStatus setOnNewPropValuesCallback(
        const std::shared_ptr<IVehicleBusCallback>& callback);
    virtual ::ndk::ScopedAStatus unsetOnNewPropValuesCallback(
        const std::shared_ptr<IVehicleBusCallback>& callback);
    virtual ::ndk::ScopedAStatus start();

protected:
    void sendPropertyEvent(const std::vector<VehiclePropValue>& propValues);
    static void updateTimestamps(std::vector<VehiclePropValue>& propValues,
                                 uint64_t timestamp);

  private:
    std::mutex mLock;
    std::shared_ptr<IVehicleBusCallback> mVehicleBusCallback GUARDED_BY(mLock);
    ::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

    void handleBinderDied();
    static void onBinderDied(void* cookie);

};

};  // namespace aidl::android::hardware::automotive::vehicle
