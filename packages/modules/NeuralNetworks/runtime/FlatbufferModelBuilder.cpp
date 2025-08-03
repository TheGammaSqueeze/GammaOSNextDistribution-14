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

#define LOG_TAG "FlatbufferModelBuilder"

#include "FlatbufferModelBuilder.h"

#include <LegacyUtils.h>

#include "FlatbufferModelBuilderUtils.h"
#include "operation_converters/OperationConverterResolver.h"

namespace android {
namespace nn {

void FlatbufferModelBuilder::verifyModel(const tflite::Model* model) {
    flatbuffers::Verifier verifier(mBuilder.GetBufferPointer(), mBuilder.GetSize());
    CHECK(model != nullptr);
    CHECK(model->Verify(verifier));
}

void FlatbufferModelBuilder::initializeBufferVector() {
    mBufferVector.clear();

    std::vector<uint8_t> emptyData;
    auto emptyBuffer = tflite::CreateBufferDirect(mBuilder, &emptyData);
    mBufferVector.push_back(emptyBuffer);
}

void FlatbufferModelBuilder::initializeOpCodeIndexForOperationType() {
    mOpCodeIndexForOperationType.clear();
    mOpCodeIndexForOperationType.resize(kNumberOfOperationTypes, -1);
}

std::vector<MetadataFlatbuffer> FlatbufferModelBuilder::createMetadataVector() {
    std::vector<MetadataFlatbuffer> metadataVector;
    for (uint32_t i = 0; i < mBufferVector.size(); i++) {
        auto metadata = tflite::CreateMetadataDirect(mBuilder, std::to_string(i).c_str() /* name */,
                                                     i /* buffer */);
        metadataVector.push_back(metadata);
    }
    return metadataVector;
}

Result<const tflite::Model*> FlatbufferModelBuilder::createTfliteModel() {
    mModel = makeModel();

    // Initialize and clear data structures
    initializeBufferVector();
    mOpCodesVector.clear();
    initializeOpCodeIndexForOperationType();

    // Generate subgraphs
    auto subgraphsVector = NN_TRY(createSubGraphs());

    auto metadataVector = createMetadataVector();

    ModelFlatbuffer flatbufferModel = tflite::CreateModelDirect(
            mBuilder, 3 /* version*/, &mOpCodesVector /* operator_codes */,
            &subgraphsVector /* subgraphs */, nullptr /* description */,
            &mBufferVector /* buffers */, nullptr /* metadata_buffer */,
            &metadataVector /* metadata */);
    mBuilder.Finish(flatbufferModel);

    const tflite::Model* tfliteModel = tflite::GetModel(mBuilder.GetBufferPointer());
    verifyModel(tfliteModel);
    return tfliteModel;
}

Result<SubGraphFlatbuffer> FlatbufferModelBuilder::createSubGraphFlatbuffer(
        const Model::Subgraph& subgraph) {
    // TFLite does not support unspecified ranks in Operands
    NN_TRY(checkAllTensorOperandsHaveSpecifiedRank(subgraph.operands));
    // TFLite does not support dynamic shapes for subgrah output Operands
    NN_TRY(checkNoSubgraphOutputOperandsHaveDynamicShape(subgraph.operands));

    SubGraphContext context(&mModel, &subgraph, &mBuilder, &mOpCodesVector,
                            &mOpCodeIndexForOperationType, &mBufferVector);
    for (const Operation& operation : subgraph.operations) {
        const IOperationConverter* converter =
                OperationConverterResolver::get()->findOperationConverter(operation.type);
        NN_RET_CHECK(converter != nullptr)
                << "IOperationConverter not implemented for OperationType: " << operation.type;

        NN_TRY(converter->convert(operation, &context));
    }

    for (uint32_t idx : subgraph.inputIndexes) {
        context.addSubGraphInput(idx);
    }
    for (uint32_t idx : subgraph.outputIndexes) {
        context.addSubGraphOutput(idx);
    }

    return context.finish();
}

Result<std::vector<SubGraphFlatbuffer>> FlatbufferModelBuilder::createSubGraphs() {
    // We do not support control flow yet
    NN_RET_CHECK(mModel.referenced.empty()) << "Control flow for multiple subgraphs not supported";

    std::vector<SubGraphFlatbuffer> subGraphVector;

    auto mainSubGraph = NN_TRY(createSubGraphFlatbuffer(mModel.main));
    subGraphVector.push_back(mainSubGraph);

    return subGraphVector;
}

}  // namespace nn
}  // namespace android
