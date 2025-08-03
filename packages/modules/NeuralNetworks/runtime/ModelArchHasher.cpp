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

#define LOG_TAG "ModelArchHasher"

#include "ModelArchHasher.h"

#include <android-base/logging.h>
#include <nnapi/Types.h>
#include <openssl/sha.h>

namespace android::nn {

namespace {

bool update(SHA256_CTX* hasher, const void* bytes, size_t length) {
    return SHA256_Update(hasher, bytes, length) != 0;
}

bool updateSubgraph(SHA256_CTX* hasher, const Model::Subgraph& subgraph) {
    bool success = true;
    for (auto& operand : subgraph.operands) {
        success &= update(hasher, static_cast<const void*>(&operand.type), sizeof(operand.type));
        success &= update(
                hasher, static_cast<const void*>(operand.dimensions.data()),
                sizeof(decltype(operand.dimensions)::value_type) * operand.dimensions.size());
        success &= update(hasher, static_cast<const void*>(&operand.scale), sizeof(operand.scale));
        success &= update(hasher, static_cast<const void*>(&operand.zeroPoint),
                          sizeof(operand.zeroPoint));
        success &= update(hasher, static_cast<const void*>(&operand.lifetime),
                          sizeof(operand.lifetime));
        success &= update(hasher, static_cast<const void*>(&operand.extraParams),
                          sizeof(operand.extraParams));
    }

    for (auto& operation : subgraph.operations) {
        success &=
                update(hasher, static_cast<const void*>(&operation.type), sizeof(operation.type));
        success &= update(hasher, static_cast<const void*>(operation.inputs.data()),
                          sizeof(decltype(operation.inputs)::value_type) * operation.inputs.size());
        success &=
                update(hasher, static_cast<const void*>(operation.outputs.data()),
                       sizeof(decltype(operation.outputs)::value_type) * operation.outputs.size());
    }

    success &= update(
            hasher, static_cast<const void*>(subgraph.inputIndexes.data()),
            sizeof(decltype(subgraph.inputIndexes)::value_type) * subgraph.inputIndexes.size());
    success &= update(
            hasher, static_cast<const void*>(subgraph.outputIndexes.data()),
            sizeof(decltype(subgraph.outputIndexes)::value_type) * subgraph.outputIndexes.size());
    return success;
}

}  // namespace

bool calcModelArchHash(const Model& model, uint8_t* data) {
    SHA256_CTX mHasher;
    if (SHA256_Init(&mHasher) == 0) {
        return false;
    }

    bool success = true;
    success &= updateSubgraph(&mHasher, model.main);
    for (auto& subgraph : model.referenced) {
        success &= updateSubgraph(&mHasher, subgraph);
    }
    if (!success) {
        return false;
    }

    if (SHA256_Final(data, &mHasher) == 0) {
        return false;
    }
    return true;
}

}  // namespace android::nn
