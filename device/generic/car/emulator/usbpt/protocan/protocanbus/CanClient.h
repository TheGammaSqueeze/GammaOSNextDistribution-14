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

#include "CloseHandleWrapper.h"

#include <android/hardware/automotive/can/1.0/ICanBus.h>
#include <android/hidl/manager/1.0/IServiceNotification.h>
#include <utils/Mutex.h>
#include <VehicleBus.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {
namespace utils {

class CanClient : public ::aidl::android::hardware::automotive::vehicle::VehicleBus,
                  public hidl::manager::V1_0::IServiceNotification,
                  private ICanErrorListener,
                  private ICanMessageListener,
                  private hidl_death_recipient {
public:
    virtual ~CanClient();

    virtual ::ndk::ScopedAStatus start();

protected:
    CanClient(const std::string& busName);

    virtual void onReady(const sp<ICanBus>& canBus);

private:
    const std::string mBusName;

    CloseHandleWrapper mListenerCloseHandle;
    CloseHandleWrapper mErrorCloseHandle;

    mutable std::mutex mCanBusGuard;
    sp<ICanBus> mCanBus GUARDED_BY(mCanBusGuard);

    Return<void> onRegistration(const hidl_string&, const hidl_string& name, bool) override;
    void serviceDied(uint64_t cookie, const wp<hidl::base::V1_0::IBase>& who) override;
    Return<void> onError(ErrorEvent error, bool isFatal) override;
    bool close();

    DISALLOW_COPY_AND_ASSIGN(CanClient);
};

}  // namespace utils
}  // namespace V1_0
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android
