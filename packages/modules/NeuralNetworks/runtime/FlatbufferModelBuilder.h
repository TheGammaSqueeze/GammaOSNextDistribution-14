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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_H

#include <tensorflow/lite/schema/schema_generated.h>

#include <utility>
#include <vector>

#include "FlatbufferModelBuilderUtils.h"
#include "ModelBuilder.h"
#include "NeuralNetworks.h"

namespace android {
namespace nn {

class FlatbufferModelBuilder : public ModelBuilder {
   public:
    // Return generated TFLite Model if successful
    Result<const tflite::Model*> createTfliteModel();

   private:
    void verifyModel(const tflite::Model* model);

    // Clears mBufferVector and initializes the first Buffer to be an empty Buffer
    // for Tensors that do not have a buffer.
    void initializeBufferVector();
    // Clears mOpCodeIndexForOperationType and initializes elements to be -1
    void initializeOpCodeIndexForOperationType();

    // Helper functions to convert Subgraphs
    Result<SubGraphFlatbuffer> createSubGraphFlatbuffer(const Model::Subgraph& subgraph);
    Result<std::vector<SubGraphFlatbuffer>> createSubGraphs();

    // Generates metadata for each Buffer
    // Must be called after mBufferVector is filled.
    std::vector<MetadataFlatbuffer> createMetadataVector();

    flatbuffers::FlatBufferBuilder mBuilder;
    Model mModel;

    std::vector<OperatorCodeFlatbuffer> mOpCodesVector;
    std::vector<int> mOpCodeIndexForOperationType;
    std::vector<BufferFlatbuffer> mBufferVector;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_FLATBUFFER_MODEL_BUILDER_H
