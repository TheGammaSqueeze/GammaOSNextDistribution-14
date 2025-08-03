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

#pragma once

#include <aidl/android/hardware/neuralnetworks/ErrorStatus.h>
#include <android/binder_auto_utils.h>

#include <string>
#include <vector>

#include "SupportLibraryWrapper.h"

namespace aidl::android::hardware::neuralnetworks {

constexpr int64_t kNoDeadline = -1;

ErrorStatus convertResultToErrorStatus(::android::nn::wrapper::Result status);
bool isValidDimension(const std::vector<int32_t>& v);
ndk::ScopedAStatus toAStatus(ErrorStatus errorStatus, const std::string& errorMessage);
ndk::ScopedAStatus toAStatus(ErrorStatus errorStatus);

#define SLW2SAS_RETURN_IF_ERROR(expr)                                      \
    do {                                                                   \
        const Result nnReturnIfErrorErrorCode = static_cast<Result>(expr); \
        if (nnReturnIfErrorErrorCode != Result::NO_ERROR) {                \
            const auto nnReturnIfErrorErrorCodeConverted =                 \
                    convertResultToErrorStatus(nnReturnIfErrorErrorCode);  \
            return toAStatus(nnReturnIfErrorErrorCodeConverted);           \
        }                                                                  \
    } while (0)

#define SLW2SAS_RETURN_AND_CALLBACK_IF_ERROR(expr, callback)               \
    do {                                                                   \
        const Result nnReturnIfErrorErrorCode = static_cast<Result>(expr); \
        if (nnReturnIfErrorErrorCode != Result::NO_ERROR) {                \
            const auto nnReturnIfErrorErrorCodeConverted =                 \
                    convertResultToErrorStatus(nnReturnIfErrorErrorCode);  \
            callback->notify(nnReturnIfErrorErrorCodeConverted, nullptr);  \
            return toAStatus(nnReturnIfErrorErrorCodeConverted);           \
        }                                                                  \
    } while (0)

#define SLW2SAS_OK_RETURN_AND_ERROR_CALLBACK_IF_ERROR(expr, callback)      \
    do {                                                                   \
        const Result nnReturnIfErrorErrorCode = static_cast<Result>(expr); \
        if (nnReturnIfErrorErrorCode != Result::NO_ERROR) {                \
            const auto nnReturnIfErrorErrorCodeConverted =                 \
                    convertResultToErrorStatus(nnReturnIfErrorErrorCode);  \
            callback->notify(nnReturnIfErrorErrorCodeConverted, nullptr);  \
            return ndk::ScopedAStatus::ok();                               \
        }                                                                  \
    } while (0)

}  // namespace aidl::android::hardware::neuralnetworks
