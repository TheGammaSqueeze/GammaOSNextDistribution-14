/*
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_POWERPOLICY_SERVER_SRC_POWERCOMPONENTHANDLER_H_
#define CPP_POWERPOLICY_SERVER_SRC_POWERCOMPONENTHANDLER_H_

#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicy.h>
#include <android-base/result.h>
#include <utils/Mutex.h>

#include <memory>
#include <unordered_map>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using CarPowerPolicyPtr =
        std::shared_ptr<::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy>;

class PowerComponentHandler final {
public:
    PowerComponentHandler() {}

    // Sets the initial state of all power components.
    void init();
    // Applies the given power policy and updates the latest state of all power components.
    void applyPowerPolicy(const CarPowerPolicyPtr& powerPolicy);
    // Gets the current state of the given power component.
    android::base::Result<bool> getPowerComponentState(
            const ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent componentId)
            const;
    // Gets the current state of the given custom component.
    android::base::Result<bool> getCustomPowerComponentState(const int componentId) const;
    // Gets the accumulated state of all components after applying power policies.
    CarPowerPolicyPtr getAccumulatedPolicy() const;
    // Dumps the internal status.
    android::base::Result<void> dump(int fd);

private:
    mutable android::Mutex mMutex;
    CarPowerPolicyPtr mAccumulatedPolicy GUARDED_BY(mMutex);
};

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_POWERPOLICY_SERVER_SRC_POWERCOMPONENTHANDLER_H_
