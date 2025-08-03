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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BURST_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BURST_H

#include <nnapi/IBurst.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <memory>
#include <optional>
#include <utility>
#include <vector>

#include "CanonicalPreparedModel.h"

namespace android::nn::sample {

// Class that adapts nn::sample::PreparedModel to nn::sample::IBurst.
class Burst final : public IBurst {
   public:
    explicit Burst(std::shared_ptr<const PreparedModel> preparedModel);

    OptionalCacheHold cacheMemory(const SharedMemory& memory) const override;

    ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> execute(
            const Request& request, MeasureTiming measure, const nn::OptionalTimePoint& deadline,
            const nn::OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

    GeneralResult<SharedExecution> createReusableExecution(
            const Request& request, MeasureTiming measure,
            const nn::OptionalDuration& loopTimeoutDuration,
            const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

   private:
    const std::shared_ptr<const PreparedModel> kPreparedModel;
};

}  // namespace android::nn::sample

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BURST_H
