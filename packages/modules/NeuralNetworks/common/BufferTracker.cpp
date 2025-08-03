/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "BufferTracker.h"

#include <android-base/macros.h>

#include <memory>
#include <mutex>
#include <set>
#include <stack>
#include <utility>
#include <vector>

#include "CpuExecutor.h"
#include "LegacyUtils.h"
#include "nnapi/TypeUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {

std::shared_ptr<ManagedBuffer> ManagedBuffer::create(uint32_t size,
                                                     std::set<PreparedModelRole> roles,
                                                     const Operand& operand) {
    std::unique_ptr<uint8_t[]> buffer(new (std::nothrow) uint8_t[size]);
    if (buffer == nullptr) {
        return nullptr;
    }
    if (isExtension(operand.type)) {
        LOG(ERROR) << "ManagedBuffer cannot handle extension operands.";
        return nullptr;
    }
    return std::make_shared<ManagedBuffer>(std::move(buffer), size, std::move(roles), operand);
}

ManagedBuffer::ManagedBuffer(std::unique_ptr<uint8_t[]> buffer, uint32_t size,
                             std::set<PreparedModelRole> roles, const Operand& operand)
    : kBuffer(std::move(buffer)),
      kSize(size),
      kRoles(std::move(roles)),
      kOperandType(operand.type),
      kInitialDimensions(operand.dimensions),
      mUpdatedDimensions(operand.dimensions) {
    CHECK(!isExtension(kOperandType));
}

ErrorStatus ManagedBuffer::validateRequest(uint32_t poolIndex, const Request& request,
                                           const IPreparedModel* preparedModel) const {
    CHECK_LT(poolIndex, request.pools.size());
    CHECK(std::holds_alternative<Request::MemoryDomainToken>(request.pools[poolIndex]));
    std::lock_guard<std::mutex> guard(mMutex);

    bool usedAsInput = false, usedAsOutput = false;
    for (uint32_t i = 0; i < request.inputs.size(); i++) {
        if (request.inputs[i].lifetime != Request::Argument::LifeTime::POOL) continue;
        if (request.inputs[i].location.poolIndex != poolIndex) continue;
        // Validate if the input role is specified during allocation.
        if (kRoles.count({preparedModel, IOType::INPUT, i}) == 0) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- invalid buffer role.";
            return ErrorStatus::INVALID_ARGUMENT;
        }
        if (!mInitialized) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- using uninitialized buffer as input "
                          "request.";
            return ErrorStatus::GENERAL_FAILURE;
        }
        auto combined = combineDimensions(mUpdatedDimensions, request.inputs[i].dimensions);
        if (!combined.has_value()) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- incompatible dimensions ("
                       << toString(mUpdatedDimensions) << " vs "
                       << toString(request.inputs[i].dimensions) << ")";
            return ErrorStatus::INVALID_ARGUMENT;
        }
        usedAsInput = true;
    }
    for (uint32_t i = 0; i < request.outputs.size(); i++) {
        if (request.outputs[i].lifetime != Request::Argument::LifeTime::POOL) continue;
        if (request.outputs[i].location.poolIndex != poolIndex) continue;
        if (usedAsInput || usedAsOutput) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- using the same device memory for "
                          "input/output or multiple outputs";
            return ErrorStatus::INVALID_ARGUMENT;
        }
        // Validate if the output role is specified during allocation.
        if (kRoles.count({preparedModel, IOType::OUTPUT, i}) == 0) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- invalid buffer role.";
            return ErrorStatus::INVALID_ARGUMENT;
        }
        auto combined = combineDimensions(kInitialDimensions, request.outputs[i].dimensions);
        if (!combined.has_value()) {
            LOG(ERROR) << "ManagedBuffer::validateRequest -- incompatible dimensions ("
                       << toString(kInitialDimensions) << " vs "
                       << toString(request.outputs[i].dimensions) << ")";
            return ErrorStatus::INVALID_ARGUMENT;
        }
        usedAsOutput = true;
    }
    return ErrorStatus::NONE;
}

ErrorStatus ManagedBuffer::validateCopyFrom(const std::vector<uint32_t>& dimensions,
                                            uint32_t size) const {
    if (size != kSize) {
        LOG(ERROR) << "ManagedBuffer::validateCopyFrom -- invalid memory size: " << kSize << " vs "
                   << size;
        return ErrorStatus::INVALID_ARGUMENT;
    }

    if (nonExtensionOperandTypeIsScalar(static_cast<int>(kOperandType))) {
        if (!dimensions.empty()) {
            LOG(ERROR)
                    << "ManagedBuffer::validateCopyFrom -- invalid dimensions for scalar operand: "
                    << toString(dimensions);
            return ErrorStatus::INVALID_ARGUMENT;
        }
        return ErrorStatus::NONE;
    }

    if (dimensions.empty()) {
        if (tensorHasUnspecifiedDimensions(kOperandType, kInitialDimensions)) {
            LOG(ERROR) << "ManagedBuffer::validateCopyFrom -- the initial dimensions are not fully "
                          "specified and no dimension update is provided: "
                       << toString(kInitialDimensions);
            return ErrorStatus::INVALID_ARGUMENT;
        }
    } else {
        if (tensorHasUnspecifiedDimensions(kOperandType, dimensions)) {
            LOG(ERROR) << "ManagedBuffer::validateCopyFrom -- the updated dimensions are not fully "
                          "specified: "
                       << toString(dimensions);
            return ErrorStatus::INVALID_ARGUMENT;
        }
    }

    const auto combined = combineDimensions(kInitialDimensions, dimensions);
    if (!combined.has_value()) {
        LOG(ERROR) << "ManagedBuffer::validateCopyFrom -- incompatible dimensions ("
                   << toString(kInitialDimensions) << " vs " << toString(dimensions) << ")";
        return ErrorStatus::INVALID_ARGUMENT;
    }
    return ErrorStatus::NONE;
}

ErrorStatus ManagedBuffer::validateCopyTo(uint32_t size) const {
    if (size != kSize) {
        LOG(ERROR) << "ManagedBuffer::validateCopyTo -- invalid memory size: " << kSize << " vs "
                   << size;
        return ErrorStatus::INVALID_ARGUMENT;
    }
    std::lock_guard<std::mutex> guard(mMutex);
    if (!mInitialized) {
        LOG(ERROR) << "ManagedBuffer::validateCopyTo -- using uninitialized buffer as source.";
        return ErrorStatus::GENERAL_FAILURE;
    }
    return ErrorStatus::NONE;
}

bool ManagedBuffer::updateDimensions(const std::vector<uint32_t>& dimensions) {
    auto combined = combineDimensions(kInitialDimensions, dimensions);
    if (!combined.has_value()) {
        LOG(ERROR) << "ManagedBuffer::updateDimensions -- incompatible dimensions ("
                   << toString(kInitialDimensions) << " vs " << toString(dimensions) << ")";
        return false;
    }
    std::lock_guard<std::mutex> guard(mMutex);
    mUpdatedDimensions = std::move(combined.value());
    return true;
}

void ManagedBuffer::setInitialized(bool initialized) {
    std::lock_guard<std::mutex> guard(mMutex);
    mInitialized = initialized;
}

BufferTracker::BufferTracker() {
    constexpr size_t kPreallocatedElements = 1024;
    using StackSpace = std::vector<Request::MemoryDomainToken>;
    using Stack = std::stack<Request::MemoryDomainToken, StackSpace>;
    StackSpace stackSpace;
    stackSpace.reserve(kPreallocatedElements);
    mFreeTokens = Stack(std::move(stackSpace));
    mTokenToBuffers.reserve(kPreallocatedElements);
    mTokenToBuffers.emplace_back();
}

std::unique_ptr<BufferTracker::Token> BufferTracker::add(std::shared_ptr<ManagedBuffer> buffer) {
    if (buffer == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::mutex> guard(mMutex);
    auto token = Request::MemoryDomainToken{0};
    if (mFreeTokens.empty()) {
        token = static_cast<Request::MemoryDomainToken>(mTokenToBuffers.size());
        mTokenToBuffers.push_back(std::move(buffer));
    } else {
        token = mFreeTokens.top();
        mFreeTokens.pop();
        const auto index = static_cast<uint32_t>(token);
        mTokenToBuffers[index] = std::move(buffer);
    }
    VLOG(MEMORY) << "BufferTracker::add -- new token = " << token;
    return std::make_unique<Token>(token, shared_from_this());
}

std::shared_ptr<ManagedBuffer> BufferTracker::get(Request::MemoryDomainToken token) const {
    std::lock_guard<std::mutex> guard(mMutex);
    const auto index = static_cast<uint32_t>(token);
    if (mTokenToBuffers.size() <= index || mTokenToBuffers[index] == nullptr) {
        LOG(ERROR) << "BufferTracker::get -- unknown token " << token;
        return nullptr;
    }
    return mTokenToBuffers[index];
}

void BufferTracker::free(Request::MemoryDomainToken token) {
    std::lock_guard<std::mutex> guard(mMutex);
    const auto index = static_cast<uint32_t>(token);
    CHECK_LT(index, mTokenToBuffers.size());
    CHECK(mTokenToBuffers[index] != nullptr);
    VLOG(MEMORY) << "BufferTracker::free -- release token = " << token;
    mTokenToBuffers[index] = nullptr;
    mFreeTokens.push(token);
}

}  // namespace android::nn
