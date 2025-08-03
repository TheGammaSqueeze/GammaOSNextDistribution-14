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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_PREPARED_MODEL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_PREPARED_MODEL_H

#include <BufferTracker.h>
#include <CpuExecutor.h>
#include <nnapi/IExecution.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <memory>
#include <tuple>
#include <utility>
#include <vector>

namespace android::nn::sample {

class PreparedModel final : public IPreparedModel,
                            public std::enable_shared_from_this<PreparedModel> {
   public:
    PreparedModel(Model model, ExecutionPreference preference, Priority priority,
                  const IOperationResolver* operationResolver,
                  std::shared_ptr<BufferTracker> bufferTracker,
                  std::vector<RunTimePoolInfo> poolInfos);

    ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> execute(
            const Request& request, MeasureTiming measure, const OptionalTimePoint& deadline,
            const OptionalDuration& loopTimeoutDuration, const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

    GeneralResult<std::pair<SyncFence, ExecuteFencedInfoCallback>> executeFenced(
            const Request& request, const std::vector<SyncFence>& waitFor, MeasureTiming measure,
            const OptionalTimePoint& deadline, const OptionalDuration& loopTimeoutDuration,
            const OptionalDuration& timeoutDurationAfterFence,
            const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

    GeneralResult<nn::SharedExecution> createReusableExecution(
            const Request& request, MeasureTiming measure,
            const OptionalDuration& loopTimeoutDuration, const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

    GeneralResult<SharedBurst> configureExecutionBurst() const override;

    std::any getUnderlyingResource() const override;

   private:
    const Model kModel;
    [[maybe_unused]] const ExecutionPreference kExecutionPreference;
    [[maybe_unused]] const Priority kExecutionPriority;
    const IOperationResolver& kOperationResolver;
    const std::shared_ptr<BufferTracker> kBufferTracker;
    const std::vector<RunTimePoolInfo> kPoolInfos;
};

}  // namespace android::nn::sample

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_PREPARED_MODEL_H
