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

#include <aidl/android/hardware/neuralnetworks/BnPreparedModel.h>
#include <android-base/logging.h>

#include <memory>
#include <utility>
#include <vector>

#include "ShimDevice.h"
#include "SupportLibrary.h"
#include "SupportLibraryWrapper.h"

namespace aidl::android::hardware::neuralnetworks {

class ShimPreparedModel : public BnPreparedModel {
   public:
    ShimPreparedModel(std::shared_ptr<const NnApiSupportLibrary> nnapi,
                      std::shared_ptr<ShimBufferTracker> bufferTracker,
                      ::android::nn::sl_wrapper::Compilation compilation,
                      std::vector<::android::nn::sl_wrapper::Model> mainAndReferencedModels,
                      std::vector<std::unique_ptr<::android::nn::sl_wrapper::Memory>> memoryPools,
                      std::vector<uint8_t> copiedOperandValues)
        : mNnapi(nnapi),
          mBufferTracker(bufferTracker),
          mCompilation(std::move(compilation)),
          mMainAndReferencedModels(std::move(mainAndReferencedModels)),
          mMemoryPools(std::move(memoryPools)),
          mCopiedOperandValues(std::move(copiedOperandValues)) {
        CHECK(mMainAndReferencedModels.size() > 0);
    };

    ::ndk::ScopedAStatus executeSynchronously(const Request& request, bool measureTiming,
                                              int64_t deadlineNs, int64_t loopTimeoutDurationNs,
                                              ExecutionResult* executionResults) override;
    ::ndk::ScopedAStatus executeFenced(const Request& request,
                                       const std::vector<::ndk::ScopedFileDescriptor>& waitFor,
                                       bool measureTiming, int64_t deadlineNs,
                                       int64_t loopTimeoutDurationNs, int64_t durationNs,
                                       FencedExecutionResult* fencedExecutionResult) override;
    ::ndk::ScopedAStatus executeSynchronouslyWithConfig(const Request& request,
                                                        const ExecutionConfig& config,
                                                        int64_t deadlineNs,
                                                        ExecutionResult* executionResult) override;
    ::ndk::ScopedAStatus executeFencedWithConfig(
            const Request& request, const std::vector<ndk::ScopedFileDescriptor>& waitFor,
            const ExecutionConfig& config, int64_t deadlineNs, int64_t durationNs,
            FencedExecutionResult* executionResult) override;

    ndk::ScopedAStatus configureExecutionBurst(std::shared_ptr<IBurst>* burst) override;
    ndk::ScopedAStatus createReusableExecution(const Request& request,
                                               const ExecutionConfig& config,
                                               std::shared_ptr<IExecution>* execution) override;

    const ::android::nn::sl_wrapper::Compilation& getCompilation() const { return mCompilation; }
    const ::android::nn::sl_wrapper::Model& getMainModel() const {
        return mMainAndReferencedModels[0];
    }

   private:
    ErrorStatus parseInputs(
            const Request& request, bool measure, int64_t deadlineNs, int64_t loopTimeoutDurationNs,
            ::android::nn::sl_wrapper::Execution* execution,
            std::vector<std::shared_ptr<::android::nn::sl_wrapper::Memory>>* requestMemoryPools,
            const std::vector<TokenValuePair>& executionHints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix);

    ::ndk::ScopedAStatus executeSynchronouslyCommon(
            const Request& request, bool measureTiming, int64_t deadlineNs,
            int64_t loopTimeoutDurationNs, const std::vector<TokenValuePair>& executionHints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix,
            ExecutionResult* executionResult);
    ::ndk::ScopedAStatus executeFencedCommon(
            const Request& request, const std::vector<::ndk::ScopedFileDescriptor>& waitFor,
            bool measureTiming, int64_t deadlineNs, int64_t loopTimeoutDurationNs,
            int64_t durationNs, const std::vector<TokenValuePair>& executionHints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix,
            FencedExecutionResult* fencedExecutionResult);

    std::shared_ptr<const NnApiSupportLibrary> mNnapi;
    std::shared_ptr<ShimBufferTracker> mBufferTracker;

    ::android::nn::sl_wrapper::Compilation mCompilation;
    std::vector<::android::nn::sl_wrapper::Model> mMainAndReferencedModels;
    std::vector<std::unique_ptr<::android::nn::sl_wrapper::Memory>> mMemoryPools;
    std::vector<uint8_t> mCopiedOperandValues;
};

}  // namespace aidl::android::hardware::neuralnetworks
