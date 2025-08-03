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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DEFAULT_EXECUTION_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DEFAULT_EXECUTION_H

#include <android-base/macros.h>
#include <nnapi/IExecution.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <memory>
#include <utility>
#include <vector>

namespace android::nn {

class DefaultExecution final : public IExecution {
   public:
    DefaultExecution(SharedPreparedModel preparedModel, Request request, MeasureTiming measure,
                     OptionalDuration loopTimeoutDuration)
        : kPreparedModel(std::move(preparedModel)),
          kRequest(std::move(request)),
          kMeasure(measure),
          kLoopTimeoutDuration(loopTimeoutDuration) {
        CHECK(kPreparedModel != nullptr);
    }

    ExecutionResult<std::pair<std::vector<OutputShape>, Timing>> compute(
            const OptionalTimePoint& deadline) const override {
        return kPreparedModel->execute(kRequest, kMeasure, deadline, kLoopTimeoutDuration, {}, {});
    }

    GeneralResult<std::pair<SyncFence, ExecuteFencedInfoCallback>> computeFenced(
            const std::vector<SyncFence>& waitFor, const OptionalTimePoint& deadline,
            const OptionalDuration& timeoutDurationAfterFence) const override {
        return kPreparedModel->executeFenced(kRequest, waitFor, kMeasure, deadline,
                                             kLoopTimeoutDuration, timeoutDurationAfterFence, {},
                                             {});
    }

   private:
    const SharedPreparedModel kPreparedModel;
    const Request kRequest;
    const MeasureTiming kMeasure;
    const OptionalDuration kLoopTimeoutDuration;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_DEFAULT_EXECUTION_H
