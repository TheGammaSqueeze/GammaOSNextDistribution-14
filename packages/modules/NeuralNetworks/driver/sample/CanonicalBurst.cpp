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

#include "CanonicalBurst.h"

#include <DefaultExecution.h>
#include <android-base/logging.h>
#include <nnapi/IBurst.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <memory>
#include <optional>
#include <utility>
#include <vector>

namespace android::nn::sample {

Burst::Burst(std::shared_ptr<const PreparedModel> preparedModel)
    : kPreparedModel(std::move(preparedModel)) {
    CHECK(kPreparedModel != nullptr);
}

Burst::OptionalCacheHold Burst::cacheMemory(const SharedMemory& /*memory*/) const {
    return nullptr;
}

ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> Burst::execute(
        const Request& request, MeasureTiming measure, const nn::OptionalTimePoint& deadline,
        const nn::OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    return kPreparedModel->execute(request, measure, deadline, loopTimeoutDuration, {}, {});
}

GeneralResult<SharedExecution> Burst::createReusableExecution(
        const Request& request, MeasureTiming measure,
        const nn::OptionalDuration& loopTimeoutDuration,
        const std::vector<TokenValuePair>& /*hints*/,
        const std::vector<ExtensionNameAndPrefix>& /*extensionNameToPrefix*/) const {
    return std::make_shared<DefaultExecution>(kPreparedModel, request, measure,
                                              loopTimeoutDuration);
}

}  // namespace android::nn::sample
