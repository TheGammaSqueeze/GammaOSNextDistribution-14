/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYINTERNALIMPL_H_
#define CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYINTERNALIMPL_H_

#include "TelemetryServer.h"

#include <aidl/android/automotive/telemetry/internal/BnCarTelemetryInternal.h>
#include <aidl/android/automotive/telemetry/internal/CarDataInternal.h>
#include <aidl/android/automotive/telemetry/internal/ICarDataListener.h>
#include <android/binder_status.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/Vector.h>

namespace android {
namespace automotive {
namespace telemetry {

// Implementation of android.automotive.telemetry.ICarTelemetryInternal.
class CarTelemetryInternalImpl :
      public aidl::android::automotive::telemetry::internal::BnCarTelemetryInternal {
public:
    // Doesn't own `server`.
    explicit CarTelemetryInternalImpl(TelemetryServer* server);

    ndk::ScopedAStatus setListener(
            const std::shared_ptr<aidl::android::automotive::telemetry::internal::ICarDataListener>&
                    listener) override;

    ndk::ScopedAStatus clearListener() override;

    ndk::ScopedAStatus addCarDataIds(const std::vector<int32_t>& ids) override;

    ndk::ScopedAStatus removeCarDataIds(const std::vector<int32_t>& ids) override;

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

private:
    // Death recipient callback that is called when ICarDataListener dies.
    // The cookie is a pointer to a CarTelemetryInternalImpl object.
    static void listenerBinderDied(void* cookie);

    void listenerBinderDiedImpl();

    TelemetryServer* mTelemetryServer;  // not owned
    ndk::ScopedAIBinder_DeathRecipient mBinderDeathRecipient;
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYINTERNALIMPL_H_
