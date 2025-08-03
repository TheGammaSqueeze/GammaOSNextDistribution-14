/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "ShimUtils"

#include "ShimUtils.h"
#include <android-base/logging.h>

#include <string>
#include <vector>

namespace aidl::android::hardware::neuralnetworks {

using ::aidl::android::hardware::neuralnetworks::ErrorStatus;
using ::android::nn::wrapper::Result;

ndk::ScopedAStatus toAStatus(ErrorStatus errorStatus, const std::string& errorMessage) {
    if (errorStatus == ErrorStatus::NONE) {
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(
            static_cast<int32_t>(errorStatus), errorMessage.c_str());
}

ndk::ScopedAStatus toAStatus(ErrorStatus errorStatus) {
    if (errorStatus == ErrorStatus::NONE) {
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(errorStatus));
}

ErrorStatus convertResultToErrorStatus(Result status) {
    switch (status) {
        case Result::NO_ERROR:
            return ErrorStatus::NONE;
        case Result::OUTPUT_INSUFFICIENT_SIZE:
            return ErrorStatus::OUTPUT_INSUFFICIENT_SIZE;
        case Result::UNAVAILABLE_DEVICE:
            return ErrorStatus::DEVICE_UNAVAILABLE;
        case Result::MISSED_DEADLINE_TRANSIENT:
            return ErrorStatus::MISSED_DEADLINE_TRANSIENT;
        case Result::MISSED_DEADLINE_PERSISTENT:
            return ErrorStatus::MISSED_DEADLINE_PERSISTENT;
        case Result::BAD_DATA:
        case Result::INCOMPLETE:
        case Result::UNEXPECTED_NULL:
        case Result::UNMAPPABLE:
        case Result::OUT_OF_MEMORY:
        case Result::BAD_STATE:
            return ErrorStatus::INVALID_ARGUMENT;
        case Result::OP_FAILED:
            return ErrorStatus::GENERAL_FAILURE;
        case Result::FEATURE_LEVEL_TOO_LOW:
            return ErrorStatus::GENERAL_FAILURE;
    }

    LOG(FATAL) << "Invalid Result " << static_cast<std::underlying_type_t<Result>>(status);
    return {};
}

bool isValidDimension(const std::vector<int32_t>& v) {
    return std::all_of(v.begin(), v.end(), [](int32_t i) { return i >= 0; });
}

}  // namespace aidl::android::hardware::neuralnetworks
