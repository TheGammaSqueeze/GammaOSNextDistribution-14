/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_SUBGRAPH_CONTEXT_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_SUBGRAPH_CONTEXT_H

#include <utility>
#include <vector>

#include "FlatbufferModelBuilderUtils.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {

// This keeps track of all the data needed to convert NNAPI subgraphs to TFLite subgraphs
// This also provides information needed to convert NNAPI Operations to TFLite Operators
// Once the subgraph is done building, call finish() to return the flatbuffer
class SubGraphContext {
   public:
    SubGraphContext(const Model* model, const Model::Subgraph* subgraph,
                    flatbuffers::FlatBufferBuilder* builder,
                    std::vector<OperatorCodeFlatbuffer>* opCodesVector,
                    std::vector<int>* opCodeIndexForOperationType,
                    std::vector<BufferFlatbuffer>* bufferVector);

    SubGraphFlatbuffer finish();

    // If the operandIdx is -1, it suggests that the tensor being added doesn't have a
    // corresponding Operand from the NNAPI NDK model.
    // Returns index of Tensor being added.
    int addTensorFlatbuffer(TensorFlatbuffer tensor, int32_t operandIdx = -1);
    void addOperatorFlatbuffer(OperatorFlatbuffer opFlatbuffer);
    void addSubGraphInput(int32_t operandIdx);
    void addSubGraphOutput(int32_t operandIdx);

    const Model::Subgraph* getSubgraph() const { return mSubgraph; }
    // Returns -1 if there is no corresponding tensor index
    int getTensorIdxFromOperandIdx(int operandIdx) const;
    uint32_t addOpCode(OperationType operationType);
    flatbuffers::FlatBufferBuilder& getBuilder() { return *mBuilder; }

    // OperandLifeTime must be CONSTANT_COPY or CONSTANT_REFERENCE
    // Will crash if OperandLifeTime is not either of the two.
    // dataSize is the size of data in bytes.
    template <typename Type>
    void copyConstantValueToData(const Operand& operand, Type* data, size_t dataSize);
    template <typename Type>
    Type getConstantScalar(const Operand& operand);

    // Returns Buffer index
    uint32_t addBufferFromData(const uint8_t* data, uint32_t length);
    // makeSymmetric turns asymmetric tensors to symmetric by doing setting data = data - zeroPoint
    // makeSymmetric is supported only for constant OperandType::TENSOR_QUANT8_ASYMM_SIGNED
    // If unsupported type is passed, makeSymmetric is ignored
    Result<void> createTensorFlatbufferFromOperand(uint32_t operandIdx, bool makeSymmetric = false);

   private:
    const Mapping& getMapping(uint32_t poolIndex);
    std::pair<const uint8_t*, uint32_t> getConstantPointerAndLength(const Operand& operand);

    const Model* mModel;
    const Model::Subgraph* mSubgraph;
    flatbuffers::FlatBufferBuilder* mBuilder;

    std::vector<OperatorCodeFlatbuffer>* mOpCodesVector;
    std::vector<int>* mOpCodeIndexForOperationType;
    std::vector<BufferFlatbuffer>* mBufferVector;

    std::vector<OperatorFlatbuffer> mOperatorVector;
    std::vector<TensorFlatbuffer> mTensorVector;
    std::vector<int32_t> mInputTensors;
    std::vector<int32_t> mOutputTensors;
    std::vector<int> mOperandToTensorIdx;
    // Each index corresponds to the pool index of shared memory
    std::vector<Mapping> mMappings;
};

template <typename Type>
void SubGraphContext::copyConstantValueToData(const Operand& operand, Type* data, size_t dataSize) {
    auto [pointer, length] = getConstantPointerAndLength(operand);
    CHECK_GE(dataSize, length);

    std::memcpy(data, pointer, length);
}

template <typename Type>
Type SubGraphContext::getConstantScalar(const Operand& operand) {
    Type data;
    copyConstantValueToData(operand, &data, sizeof(Type));
    return data;
}

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_SUBGRAPH_CONTEXT_H