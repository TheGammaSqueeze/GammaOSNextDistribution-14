/*
 * Copyright (c) 2022, The Android Open Source Project
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

#include "IVhalClient.h"

#include "AidlVhalClient.h"
#include "HidlVhalClient.h"

#include <android-base/stringprintf.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

#include <condition_variable>  // NOLINT
#include <mutex>               // NOLINT

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

using ::android::base::StringPrintf;

std::shared_ptr<IVhalClient> IVhalClient::create() {
    auto client = AidlVhalClient::create();
    if (client != nullptr) {
        return client;
    }

    return HidlVhalClient::create();
}

std::shared_ptr<IVhalClient> IVhalClient::tryCreate() {
    auto client = AidlVhalClient::tryCreate();
    if (client != nullptr) {
        return client;
    }

    return HidlVhalClient::tryCreate();
}

std::shared_ptr<IVhalClient> IVhalClient::tryCreateAidlClient(const char* descriptor) {
    return AidlVhalClient::tryCreate(descriptor);
}

std::shared_ptr<IVhalClient> IVhalClient::tryCreateHidlClient(const char* descriptor) {
    return HidlVhalClient::tryCreate(descriptor);
}

VhalClientResult<std::unique_ptr<IHalPropValue>> IVhalClient::getValueSync(
        const IHalPropValue& requestValue) {
    struct {
        std::mutex lock;
        std::condition_variable cv;
        VhalClientResult<std::unique_ptr<IHalPropValue>> result;
        bool gotResult = false;
    } s;

    auto callback = std::make_shared<IVhalClient::GetValueCallbackFunc>(
            [&s](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(s.lock);
                    s.result = std::move(r);
                    s.gotResult = true;
                    s.cv.notify_one();
                }
            });

    getValue(requestValue, callback);

    std::unique_lock<std::mutex> lk(s.lock);
    s.cv.wait(lk, [&s] { return s.gotResult; });

    return std::move(s.result);
}

VhalClientResult<void> IVhalClient::setValueSync(const IHalPropValue& requestValue) {
    struct {
        std::mutex lock;
        std::condition_variable cv;
        VhalClientResult<void> result;
        bool gotResult = false;
    } s;

    auto callback =
            std::make_shared<IVhalClient::SetValueCallbackFunc>([&s](VhalClientResult<void> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(s.lock);
                    s.result = std::move(r);
                    s.gotResult = true;
                    s.cv.notify_one();
                }
            });

    setValue(requestValue, callback);

    std::unique_lock<std::mutex> lk(s.lock);
    s.cv.wait(lk, [&s] { return s.gotResult; });

    return std::move(s.result);
}

ErrorCode VhalClientError::value() const {
    return mCode;
}

std::string VhalClientError::toString(ErrorCode code) {
    switch (code) {
        case ErrorCode::OK:
            return "OK";
        case ErrorCode::INVALID_ARG:
            return "INVALID_ARG";
        case ErrorCode::TIMEOUT:
            return "TIMEOUT";
        case ErrorCode::TRANSACTION_ERROR:
            return "TRANSACTION_ERROR";
        case ErrorCode::TRY_AGAIN_FROM_VHAL:
            return "TRY_AGAIN_FROM_VHAL";
        case ErrorCode::NOT_AVAILABLE_FROM_VHAL:
            return "NOT_AVAILABLE_FROM_VHAL";
        case ErrorCode::ACCESS_DENIED_FROM_VHAL:
            return "ACCESS_DENIED_FROM_VHAL";
        case ErrorCode::INTERNAL_ERROR_FROM_VHAL:
            return "INTERNAL_ERROR_FROM_VHAL";
        default:
            return StringPrintf("Unknown error. Code: %d", static_cast<int>(code));
    }
}

std::string VhalClientError::print() const {
    return VhalClientError::toString(mCode);
}

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
