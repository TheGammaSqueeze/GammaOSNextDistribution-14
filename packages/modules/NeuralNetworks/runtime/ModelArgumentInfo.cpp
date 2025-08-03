/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "ModelArgumentInfo"

#include "ModelArgumentInfo.h"

#include <LegacyUtils.h>

#include <algorithm>
#include <utility>
#include <vector>

#include "NeuralNetworks.h"
#include "TypeManager.h"

namespace android {
namespace nn {

static const std::pair<int, ModelArgumentInfo> kBadDataModelArgumentInfo{ANEURALNETWORKS_BAD_DATA,
                                                                         {}};

std::pair<int, ModelArgumentInfo> ModelArgumentInfo::createFromPointer(
        const Operand& operand, const ANeuralNetworksOperandType* type, void* data, uint32_t length,
        bool paddingEnabled) {
    if ((data == nullptr) != (length == 0)) {
        const char* dataPtrMsg = data ? "NOT_NULLPTR" : "NULLPTR";
        LOG(ERROR) << "Data pointer must be nullptr if and only if length is zero (data = "
                   << dataPtrMsg << ", length = " << length << ")";
        return kBadDataModelArgumentInfo;
    }

    ModelArgumentInfo ret;
    uint32_t neededLength = 0;
    if (data == nullptr) {
        ret.mState = ModelArgumentInfo::HAS_NO_VALUE;
    } else {
        if (int n = ret.updateDimensionInfo(operand, type)) {
            return {n, ModelArgumentInfo()};
        }
        if (operand.type != OperandType::OEM) {
            neededLength = TypeManager::get()->getSizeOfData(operand.type, ret.mDimensions);
            if (neededLength > length) {
                LOG(ERROR) << "Setting argument with invalid length: " << length
                           << ", minimum length expected: " << neededLength;
                return kBadDataModelArgumentInfo;
            }
        }
        ret.mState = ModelArgumentInfo::POINTER;
    }
    const uint32_t rawLength = neededLength == 0 ? length : neededLength;
    const uint32_t padding = length - rawLength;

    if (!paddingEnabled && padding > 0) {
        LOG(ERROR) << "Setting argument with padded length without enabling input and output "
                      "padding -- length: "
                   << length << ", expected length: " << neededLength;
        return kBadDataModelArgumentInfo;
    }

    ret.mBuffer = data;
    ret.mLocationAndLength = {.poolIndex = 0, .offset = 0, .length = rawLength, .padding = padding};
    return {ANEURALNETWORKS_NO_ERROR, ret};
}

std::pair<int, ModelArgumentInfo> ModelArgumentInfo::createFromMemory(
        const Operand& operand, const ANeuralNetworksOperandType* type, uint32_t poolIndex,
        uint32_t offset, uint32_t length, bool paddingEnabled) {
    ModelArgumentInfo ret;
    if (int n = ret.updateDimensionInfo(operand, type)) {
        return {n, ModelArgumentInfo()};
    }
    const bool isMemorySizeKnown = offset != 0 || length != 0;
    uint32_t neededLength = 0;
    if (isMemorySizeKnown && operand.type != OperandType::OEM) {
        neededLength = TypeManager::get()->getSizeOfData(operand.type, ret.mDimensions);
        if (neededLength > length) {
            LOG(ERROR) << "Setting argument with invalid length: " << length
                       << " (offset: " << offset << "), minimum length expected: " << neededLength;
            return kBadDataModelArgumentInfo;
        }
    }
    const uint32_t rawLength = neededLength == 0 ? length : neededLength;
    const uint32_t padding = length - rawLength;

    if (!paddingEnabled && padding > 0) {
        LOG(ERROR) << "Setting argument with padded length without enabling input and output "
                      "padding -- length: "
                   << length << ", offset: " << offset << ", expected length: " << neededLength;
        return kBadDataModelArgumentInfo;
    }

    ret.mState = ModelArgumentInfo::MEMORY;
    ret.mLocationAndLength = {
            .poolIndex = poolIndex, .offset = offset, .length = rawLength, .padding = padding};
    ret.mBuffer = nullptr;
    return {ANEURALNETWORKS_NO_ERROR, ret};
}

int ModelArgumentInfo::updateDimensionInfo(const Operand& operand,
                                           const ANeuralNetworksOperandType* newType) {
    if (newType == nullptr) {
        mInitialDimensions = operand.dimensions;
    } else {
        const uint32_t count = newType->dimensionCount;
        mInitialDimensions = std::vector<uint32_t>(count);
        std::copy(&newType->dimensions[0], &newType->dimensions[count], mInitialDimensions.begin());
    }
    mDimensions = mInitialDimensions;
    return ANEURALNETWORKS_NO_ERROR;
}

Request::Argument ModelArgumentInfo::createRequestArgument() const {
    switch (mState) {
        case ModelArgumentInfo::POINTER: {
            Request::Argument arg = {.lifetime = Request::Argument::LifeTime::POINTER,
                                     .location = mLocationAndLength,
                                     .dimensions = mDimensions};
            arg.location.pointer = mBuffer;
            return arg;
        }
        case ModelArgumentInfo::MEMORY:
            return {.lifetime = Request::Argument::LifeTime::POOL,
                    .location = mLocationAndLength,
                    .dimensions = mDimensions};
        case ModelArgumentInfo::HAS_NO_VALUE:
            return {.lifetime = Request::Argument::LifeTime::NO_VALUE};
        case ModelArgumentInfo::UNSPECIFIED:
            LOG(FATAL) << "Invalid state: UNSPECIFIED";
            return {};
    };
    LOG(FATAL) << "Invalid state: " << mState;
    return {};
}

std::vector<Request::Argument> createRequestArguments(
        const std::vector<ModelArgumentInfo>& argumentInfos,
        const std::vector<DataLocation>& ptrArgsLocations) {
    const size_t count = argumentInfos.size();
    std::vector<Request::Argument> ioInfos(count);
    uint32_t ptrArgsIndex = 0;
    for (size_t i = 0; i < count; i++) {
        const auto& info = argumentInfos[i];
        switch (info.state()) {
            case ModelArgumentInfo::POINTER:
                ioInfos[i] = {.lifetime = Request::Argument::LifeTime::POOL,
                              .location = ptrArgsLocations[ptrArgsIndex++],
                              .dimensions = info.dimensions()};
                break;
            case ModelArgumentInfo::MEMORY:
            case ModelArgumentInfo::HAS_NO_VALUE:
                ioInfos[i] = info.createRequestArgument();
                break;
            default:
                CHECK(false);
        };
    }
    return ioInfos;
}

}  // namespace nn
}  // namespace android
