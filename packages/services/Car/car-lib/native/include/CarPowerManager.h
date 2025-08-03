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

#ifndef CAR_LIB_NATIVE_INCLUDE_CARPOWERMANAGER_H_
#define CAR_LIB_NATIVE_INCLUDE_CARPOWERMANAGER_H_

#include "android/car/ICar.h"
#include "android/car/hardware/power/BnCarPowerStateListener.h"
#include "android/car/hardware/power/ICarPower.h"

#include <binder/Status.h>
#include <utils/RefBase.h>

namespace android {
namespace car {
namespace hardware {
namespace power {

class CarPowerManager : public android::RefBase {
public:
    // Enumeration of state change events
    // NOTE: The entries in this enum must match the ones in CarPowerStateListener located in
    //       packages/services/Car/car-lib/src/android/car/hardware/power/CarPowerManager.java
    enum class State {
        kInvalid = 0,
        kWaitForVhal = 1,
        kSuspendEnter = 2,
        kSuspendExit = 3,
        kShutdownEnter = 5,
        kOn = 6,
        kShutdownPrepare = 7,
        kShutdownCancelled = 8,
        kHibernationEnter = 9,
        kHibernationExit = 10,
        kPreShutdownPrepare = 11,
        kPostSuspendEnter = 12,
        kPostShutdownEnter = 13,
        kPostHibernationEnter = 14,

        kFirst = kInvalid,
        kLast = kPostHibernationEnter,
    };

    using Listener = std::function<void(State)>;

    CarPowerManager() = default;
    virtual ~CarPowerManager() {
        // Clear the listener if one is set
        clearListener();
    }

    // Removes the listener and turns off callbacks
    // Returns 0 on success
    int clearListener();

    // Requests device to shutdown in lieu of suspend at the next opportunity
    // Returns 0 on success
    int requestShutdownOnNextSuspend();

    // Sets the callback function.  This will execute in the binder thread.
    // Returns 0 on success
    int setListener(Listener listener);

private:
    class CarPowerStateListener final : public BnCarPowerStateListener {
    public:
        explicit CarPowerStateListener(CarPowerManager* parent) : mParent(parent) {}

        android::binder::Status onStateChanged(int state) override {
            android::sp<CarPowerManager> parent = mParent;
            if ((parent == nullptr) || (parent->mListener == nullptr)) {
                ALOGE("CarPowerManagerNative: onStateChanged null pointer detected!");
            } else if ((state < static_cast<int>(State::kFirst)) ||
                       (state > static_cast<int>(State::kLast))) {
                ALOGE("CarPowerManagerNative: onStateChanged unknown state: %d", state);
            } else {
                // Notifies the listener of the state transition
                parent->mListener(static_cast<State>(state));
            }
            return android::binder::Status::ok();
        };

    private:
        android::sp<CarPowerManager> mParent;
    };

    bool connectToCarService();

    android::sp<ICarPower> mICarPower;
    bool mIsConnected;
    Listener mListener;
    android::sp<CarPowerStateListener> mListenerToService;
};

}  // namespace power
}  // namespace hardware
}  // namespace car
}  // namespace android

#endif  // CAR_LIB_NATIVE_INCLUDE_CARPOWERMANAGER_H_
