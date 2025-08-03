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

#include "CanonicalBuffer.h"

#include <android-base/logging.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <algorithm>
#include <memory>
#include <utility>

namespace android::nn::sample {
namespace {

void copyRunTimePoolInfos(const RunTimePoolInfo& srcPool, const RunTimePoolInfo& dstPool) {
    CHECK(srcPool.getBuffer() != nullptr);
    CHECK(dstPool.getBuffer() != nullptr);
    CHECK(srcPool.getSize() == dstPool.getSize());
    std::copy(srcPool.getBuffer(), srcPool.getBuffer() + srcPool.getSize(), dstPool.getBuffer());
    dstPool.flush();
}

GeneralResult<void> copyFromInternal(const SharedMemory& src, const Dimensions& dimensions,
                                     const std::shared_ptr<ManagedBuffer>& bufferWrapper) {
    CHECK(bufferWrapper != nullptr);
    const auto srcPool = RunTimePoolInfo::createFromMemory(src);
    if (!srcPool.has_value()) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "SampleBuffer::copyFrom -- unable to map src memory.";
    }
    const ErrorStatus validationStatus =
            bufferWrapper->validateCopyFrom(dimensions, srcPool->getSize());
    if (validationStatus != ErrorStatus::NONE) {
        return NN_ERROR(validationStatus);
    }
    const auto dstPool = bufferWrapper->createRunTimePoolInfo();
    copyRunTimePoolInfos(srcPool.value(), dstPool);

    return {};
}

}  // namespace

Buffer::Buffer(std::shared_ptr<ManagedBuffer> buffer, std::unique_ptr<BufferTracker::Token> token)
    : kBuffer(std::move(buffer)), kToken(std::move(token)) {
    CHECK(kBuffer != nullptr);
    CHECK(kToken != nullptr);
}

Request::MemoryDomainToken Buffer::getToken() const {
    return Request::MemoryDomainToken{kToken->get()};
}

GeneralResult<void> Buffer::copyTo(const SharedMemory& dst) const {
    const auto dstPool = RunTimePoolInfo::createFromMemory(dst);
    if (!dstPool.has_value()) {
        return NN_ERROR(ErrorStatus::GENERAL_FAILURE)
               << "SampleBuffer::copyTo -- unable to map dst memory.";
    }

    const ErrorStatus validationStatus = kBuffer->validateCopyTo(dstPool->getSize());
    if (validationStatus != ErrorStatus::NONE) {
        return NN_ERROR(validationStatus);
    }

    const auto srcPool = kBuffer->createRunTimePoolInfo();
    copyRunTimePoolInfos(srcPool, dstPool.value());

    return {};
}

GeneralResult<void> Buffer::copyFrom(const SharedMemory& src, const Dimensions& dimensions) const {
    if (const auto result = copyFromInternal(src, dimensions, kBuffer); !result.ok()) {
        kBuffer->setInitialized(false);
        NN_TRY(result);
    }

    kBuffer->updateDimensions(dimensions);
    kBuffer->setInitialized(true);

    return {};
}

}  // namespace android::nn::sample
