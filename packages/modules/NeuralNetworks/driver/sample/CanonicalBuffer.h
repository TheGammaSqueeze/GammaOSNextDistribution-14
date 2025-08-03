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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BUFFER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BUFFER_H

#include <BufferTracker.h>
#include <CpuExecutor.h>
#include <nnapi/IBuffer.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <memory>

namespace android::nn::sample {

class Buffer final : public IBuffer {
   public:
    Buffer(std::shared_ptr<ManagedBuffer> buffer, std::unique_ptr<BufferTracker::Token> token);

    Request::MemoryDomainToken getToken() const override;

    GeneralResult<void> copyTo(const SharedMemory& dst) const override;
    GeneralResult<void> copyFrom(const SharedMemory& src,
                                 const Dimensions& dimensions) const override;

   private:
    const std::shared_ptr<ManagedBuffer> kBuffer;
    const std::unique_ptr<BufferTracker::Token> kToken;
};

}  // namespace android::nn::sample

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_CANONICAL_BUFFER_H
