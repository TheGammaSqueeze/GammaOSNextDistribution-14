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

#ifndef CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYIMPL_H_
#define CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYIMPL_H_

#include "TelemetryServer.h"

#include <aidl/android/frameworks/automotive/telemetry/BnCarTelemetry.h>
#include <aidl/android/frameworks/automotive/telemetry/CallbackConfig.h>
#include <aidl/android/frameworks/automotive/telemetry/CarData.h>
#include <utils/String16.h>
#include <utils/Vector.h>

#include <vector>

namespace android {
namespace automotive {
namespace telemetry {

using ::aidl::android::frameworks::automotive::telemetry::CallbackConfig;
using ::aidl::android::frameworks::automotive::telemetry::CarData;
using ::aidl::android::frameworks::automotive::telemetry::ICarTelemetryCallback;

// Implementation of android.frameworks.automotive.telemetry.ICarTelemetry.
class CarTelemetryImpl : public aidl::android::frameworks::automotive::telemetry::BnCarTelemetry {
public:
    // Doesn't own `server`.
    explicit CarTelemetryImpl(TelemetryServer* server);

    ndk::ScopedAStatus write(const std::vector<CarData>& dataList) override;

    ndk::ScopedAStatus addCallback(const CallbackConfig& config,
                                   const std::shared_ptr<ICarTelemetryCallback>& callback) override;

    ndk::ScopedAStatus removeCallback(
            const std::shared_ptr<ICarTelemetryCallback>& callback) override;

private:
    TelemetryServer* mTelemetryServer;  // not owned
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_SRC_CARTELEMETRYIMPL_H_
