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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARGUMENT_INFO_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARGUMENT_INFO_H

#include <LegacyUtils.h>

#include <utility>
#include <vector>

#include "NeuralNetworks.h"

namespace android {
namespace nn {

// TODO move length out of DataLocation
//
// NOTE: The primary usage pattern is that a ModelArgumentInfo instance
//       is not modified once it is created (unless it is reassigned to).
//       There are a small number of use cases where it NEEDS to be modified,
//       and we have a limited number of methods that support this.
class ModelArgumentInfo {
   public:
    ModelArgumentInfo() {}

    static std::pair<int, ModelArgumentInfo> createFromPointer(
            const Operand& operand, const ANeuralNetworksOperandType* type,
            void* data /* nullptr means HAS_NO_VALUE */, uint32_t length,
            bool paddingEnabled = true);
    static std::pair<int, ModelArgumentInfo> createFromMemory(
            const Operand& operand, const ANeuralNetworksOperandType* type, uint32_t poolIndex,
            uint32_t offset, uint32_t length, bool paddingEnabled = true);

    enum State { POINTER, MEMORY, HAS_NO_VALUE, UNSPECIFIED };

    State state() const { return mState; }

    bool unspecified() const { return mState == UNSPECIFIED; }

    void* buffer() const {
        CHECK_EQ(mState, POINTER);
        return mBuffer;
    }

    const std::vector<uint32_t>& initialDimensions() const {
        CHECK(mState == POINTER || mState == MEMORY);
        return mInitialDimensions;
    }
    const std::vector<uint32_t>& dimensions() const {
        CHECK(mState == POINTER || mState == MEMORY);
        return mDimensions;
    }
    std::vector<uint32_t>& dimensions() {
        CHECK(mState == POINTER || mState == MEMORY);
        return mDimensions;
    }

    bool isSufficient() const {
        CHECK(mState == POINTER || mState == MEMORY);
        return mIsSufficient;
    }
    bool& isSufficient() {
        CHECK(mState == POINTER || mState == MEMORY);
        return mIsSufficient;
    }

    uint32_t length() const {
        CHECK(mState == POINTER || mState == MEMORY);
        return mLocationAndLength.length;
    }

    uint32_t padding() const {
        CHECK(mState == POINTER || mState == MEMORY);
        return mLocationAndLength.padding;
    }

    const DataLocation& locationAndLength() const {
        CHECK_EQ(mState, MEMORY);
        return mLocationAndLength;
    }
    DataLocation& locationAndLength() {
        CHECK_EQ(mState, MEMORY);
        return mLocationAndLength;
    }

    // Restore updatable properties to creation-time values.
    void reset() {
        mDimensions = mInitialDimensions;
        mIsSufficient = true;
    }

    // Convert ModelArgumentInfo to canonical Request::Argument. Unlike createRequestArguments,
    // this method will keep the pointer type in the canonical type.
    Request::Argument createRequestArgument() const;

   private:
    int updateDimensionInfo(const Operand& operand, const ANeuralNetworksOperandType* newType);

    // Whether the argument was specified as being in a Memory, as a pointer,
    // has no value, or has not been specified.
    // If POINTER then:
    //   mLocationAndLength.length is valid.
    //   mBuffer is valid.
    // If MEMORY then:
    //   mLocationAndLength.{poolIndex, offset, length} is valid.
    // In both MEMORY and POINTER cases:
    //   mInitialDimensions is valid.
    //   mDimensions is valid.
    //   mIsSufficient is valid.

    // Properties that are fixed at creation.
    State mState = UNSPECIFIED;
    void* mBuffer = nullptr;
    std::vector<uint32_t> mInitialDimensions;
    // TODO(b/183021356): This field is logically fixed at creation, but actually updated in
    // StepExecutor::mapInputOrOutput when constructing StepExecutor ModelArgumentInfos from
    // ExecutionBuilder ModelArgumentInfos. We should find a way to avoid this update.
    DataLocation mLocationAndLength;

    // Properties that are updatable after creation.
    std::vector<uint32_t> mDimensions;
    bool mIsSufficient = true;
};

// Convert ModelArgumentInfo to canonical Request::Argument. For pointer arguments, use the location
// information in ptrArgsLocations.
std::vector<Request::Argument> createRequestArguments(
        const std::vector<ModelArgumentInfo>& argumentInfos,
        const std::vector<DataLocation>& ptrArgsLocations);

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MODEL_ARGUMENT_INFO_H
