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

#include "CarTelemetryImpl.h"

#include "BufferedCarData.h"

#include <aidl/android/frameworks/automotive/telemetry/CarData.h>
#include <android/binder_ibinder.h>

#include <stdio.h>

#include <memory>

namespace android {
namespace automotive {
namespace telemetry {

CarTelemetryImpl::CarTelemetryImpl(TelemetryServer* server) : mTelemetryServer(server) {}

ndk::ScopedAStatus CarTelemetryImpl::write(const std::vector<CarData>& dataList) {
    uid_t publisherUid = ::AIBinder_getCallingUid();
    mTelemetryServer->writeCarData(dataList, publisherUid);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus CarTelemetryImpl::addCallback(
        const CallbackConfig& config, const std::shared_ptr<ICarTelemetryCallback>& callback) {
    auto result = mTelemetryServer->addCallback(config, callback);
    if (!result.ok()) {
        return ndk::ScopedAStatus::fromExceptionCodeWithMessage(result.error().code(),
                                                                result.error().message().c_str());
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus CarTelemetryImpl::removeCallback(
        const std::shared_ptr<ICarTelemetryCallback>& callback) {
    auto result = mTelemetryServer->removeCallback(callback);
    if (!result.ok()) {
        return ndk::ScopedAStatus::fromExceptionCodeWithMessage(result.error().code(),
                                                                result.error().message().c_str());
    }
    return ndk::ScopedAStatus::ok();
}

}  // namespace telemetry
}  // namespace automotive
}  // namespace android
