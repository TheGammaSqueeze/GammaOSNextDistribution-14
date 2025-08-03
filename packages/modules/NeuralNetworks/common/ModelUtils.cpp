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

#define LOG_TAG "ModelUtils"

#include "ModelUtils.h"

#include <android-base/logging.h>

#include <algorithm>
#include <numeric>
#include <unordered_set>
#include <utility>
#include <vector>

#include "nnapi/TypeUtils.h"
#include "nnapi/Types.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace {

// Map each `true` value in `includes` with a unique integer. `false` values are ignored. E.g.:
//   includes = {false, true, true, false, true}
//   returned = {    X,    0,    1,     X,    2}
std::vector<uint32_t> getMapping(const std::vector<bool>& includes) {
    std::vector<uint32_t> mapping;
    mapping.reserve(includes.size());
    std::transform_exclusive_scan(includes.begin(), includes.end(), std::back_inserter(mapping), 0u,
                                  std::plus<>{}, [](bool included) { return included ? 1u : 0u; });
    return mapping;
}

// Remap indexes in `indexes` by the mapping `mapping`.
// Precondition: indexes != nullptr
void remapIndexes(std::vector<uint32_t>* indexes, const std::vector<uint32_t>& mapping) {
    CHECK(indexes != nullptr);
    for (uint32_t& index : (*indexes)) {
        index = mapping.at(index);
    }
}

// Keep elements from `elements` specified by `elementsToKeep`, removing all other elements.
// Precondition: elements != nullptr
// Precondition: elements->size() == elementsToKeep.size()
template <typename Type>
void keepSelectedElements(std::vector<Type>* elements, const std::vector<bool>& elementsToKeep) {
    CHECK(elements != nullptr);
    CHECK_EQ(elements->size(), elementsToKeep.size());

    size_t elementsCopied = 0;
    for (size_t i = 0; i < elementsToKeep.size(); ++i) {
        if (elementsToKeep[i]) {
            if (elementsCopied != i) {
                (*elements)[elementsCopied] = std::move((*elements)[i]);
            }
            elementsCopied++;
        }
    }
    elements->resize(elementsCopied);
}

// Find which operands in model.main.operands are read or written by model.main.operations and
// model.main.inputIndexes.
// Postcondition: returned.size() == model.main.operands.size()
std::vector<bool> identifyUsedOperands(const Model& model) {
    std::vector<bool> used(model.main.operands.size(), false);
    auto markUsed = [&used](const std::vector<uint32_t>& indexes) {
        std::for_each(indexes.begin(), indexes.end(),
                      [&used](uint32_t index) { used.at(index) = true; });
    };
    for (const auto& operation : model.main.operations) {
        markUsed(operation.inputs);
        markUsed(operation.outputs);
    }
    markUsed(model.main.inputIndexes);
    CHECK_EQ(used.size(), model.main.operands.size());
    return used;
}

// Forward declaration.
void identifyUsedSubgraphs(uint32_t current, const std::vector<Model::Subgraph>& subgraphs,
                           std::vector<bool>* used);

// Helper function to find which subgraphs are reachable by `operands`.
// Precondition: used != nullptr
// Precondition: subgraphs.size() == used->size()
void identifyUsedSubgraphs(const std::vector<Operand>& operands,
                           const std::vector<Model::Subgraph>& subgraphs, std::vector<bool>* used) {
    for (const auto& operand : operands) {
        if (operand.lifetime == Operand::LifeTime::SUBGRAPH) {
            identifyUsedSubgraphs(operand.location.offset, subgraphs, used);
        }
    }
}

// Helper function to find which subgraphs are reachable by the subgraph at the `current` index, and
// store when a subgraph is used in `used`. `used` also acts as a cache, ensuring each subgraph is
// processed at most once.
// Precondition: used != nullptr
// Precondition: subgraphs.size() == used->size()
// Precondition: current < subgraphs.size()
void identifyUsedSubgraphs(uint32_t current, const std::vector<Model::Subgraph>& subgraphs,
                           std::vector<bool>* used) {
    CHECK(used != nullptr);
    CHECK_EQ(subgraphs.size(), used->size());
    CHECK_LT(current, subgraphs.size());

    // If a subgraph was already marked as used, quickly return to avoid redundant processing.
    if ((*used)[current]) {
        return;
    }

    // Mark the current subgraph as used, then process any subgraph it references recursively.
    (*used)[current] = true;
    identifyUsedSubgraphs(subgraphs[current].operands, subgraphs, used);
}

// Find which subgraphs are reachable by the main operands of `model`.
// Postcondition: returned.size() == model.referenced.size()
std::vector<bool> identifyUsedSubgraphs(const Model& model) {
    std::vector<bool> used(model.referenced.size(), false);
    identifyUsedSubgraphs(model.main.operands, model.referenced, &used);
    CHECK_EQ(used.size(), model.referenced.size());
    return used;
}

// Helper function to find which pools are used by `subgraph`, and store when a pool is used in
// `used`.
// Precondition: used != nullptr
void identifyUsedPools(const Model::Subgraph& subgraph, std::vector<bool>* used) {
    CHECK(used != nullptr);
    for (const auto& operand : subgraph.operands) {
        if (operand.lifetime == Operand::LifeTime::CONSTANT_REFERENCE) {
            used->at(operand.location.poolIndex) = true;
        }
    }
}

// Find which pools are used by `model`.
// Postcondition: returned.size() == model.pools.size()
std::vector<bool> identifyUsedPools(const Model& model) {
    std::vector<bool> used(model.pools.size(), false);
    identifyUsedPools(model.main, &used);
    for (const auto& subgraph : model.referenced) {
        identifyUsedPools(subgraph, &used);
    }
    CHECK_EQ(used.size(), model.pools.size());
    return used;
}

// Fix the DataLocation in `operand` by either remapping an index or by copying constant data.
// Precondition: operand != nullptr
// Precondition: newOperandValues != nullptr
void fixOperandDataLocation(Operand* operand, Model::OperandValues* newOperandValues,
                            const Model::OperandValues& oldOperandValues,
                            const std::vector<uint32_t>& remappedPoolIndex,
                            const std::vector<uint32_t>& remappedSubgraphIndex) {
    CHECK(operand != nullptr);
    CHECK(newOperandValues != nullptr);

    switch (operand->lifetime) {
        case Operand::LifeTime::CONSTANT_COPY: {
            const uint8_t* data = oldOperandValues.data() + operand->location.offset;
            const uint32_t length = operand->location.length;
            operand->location = newOperandValues->append(data, length);
            break;
        }
        case Operand::LifeTime::CONSTANT_REFERENCE:
            operand->location.poolIndex = remappedPoolIndex.at(operand->location.poolIndex);
            break;
        case Operand::LifeTime::SUBGRAPH: {
            uint32_t& subgraphIndex = operand->location.offset;
            subgraphIndex = remappedSubgraphIndex.at(subgraphIndex);
            break;
        }
        case Operand::LifeTime::TEMPORARY_VARIABLE:
        case Operand::LifeTime::SUBGRAPH_INPUT:
        case Operand::LifeTime::SUBGRAPH_OUTPUT:
        case Operand::LifeTime::NO_VALUE:
        case Operand::LifeTime::POINTER:
            break;
    }
}

// Fix all DataLocations in `operands` by either remapping an index or by copying constant data.
// Precondition: operands != nullptr
// Precondition: newOperandValues != nullptr
void fixOperandDataLocations(std::vector<Operand>* operands, Model::OperandValues* newOperandValues,
                             const Model::OperandValues& oldOperandValues,
                             const std::vector<uint32_t>& remappedPoolIndex,
                             const std::vector<uint32_t>& remappedSubgraphIndex) {
    for (Operand& operand : (*operands)) {
        fixOperandDataLocation(&operand, newOperandValues, oldOperandValues, remappedPoolIndex,
                               remappedSubgraphIndex);
    }
}

// Fix all operands' DataLocations in `model` by either remapping an index or by copying constant
// data.
// Precondition: model != nullptr
void fixOperandDataLocations(Model* model, const std::vector<uint32_t>& remappedPoolIndex,
                             const std::vector<uint32_t>& remappedSubgraphIndex) {
    const auto operandValues = std::exchange(model->operandValues, Model::OperandValues{});
    fixOperandDataLocations(&model->main.operands, &model->operandValues, operandValues,
                            remappedPoolIndex, remappedSubgraphIndex);
    for (auto& subgraph : model->referenced) {
        fixOperandDataLocations(&subgraph.operands, &model->operandValues, operandValues,
                                remappedPoolIndex, remappedSubgraphIndex);
    }
}

// Find which extensions are used in `model`.
// Postcondition: returned.size() == model.extensionNameToPrefix.size()
std::vector<bool> identifyUsedExtensions(const Model& model) {
    std::unordered_set<uint16_t> prefixes;
    const auto collectPrefix = [&prefixes](const auto& operandOrOperation) {
        const auto prefix = getExtensionPrefix(static_cast<uint32_t>(operandOrOperation.type));
        constexpr uint16_t kStandardPrefix = 0u;
        if (prefix != kStandardPrefix) {
            prefixes.insert(prefix);
        }
    };
    const auto collectPrefixes = [collectPrefix](const Model::Subgraph& subgraph) {
        std::for_each(subgraph.operands.begin(), subgraph.operands.end(), collectPrefix);
        std::for_each(subgraph.operations.begin(), subgraph.operations.end(), collectPrefix);
    };

    collectPrefixes(model.main);
    for (const auto& subgraph : model.referenced) {
        collectPrefixes(subgraph);
    }

    std::vector<bool> used;
    used.reserve(model.extensionNameToPrefix.size());
    for (const auto& extension : model.extensionNameToPrefix) {
        used.push_back(prefixes.count(extension.prefix) > 0);
    }
    CHECK_EQ(used.size(), model.extensionNameToPrefix.size());
    return used;
}

}  // anonymous namespace

void removeDeadOperands(Model* model) {
    CHECK(model != nullptr);

    // Keep only the operands which are used.
    const auto operandsUsed = identifyUsedOperands(*model);
    keepSelectedElements(&model->main.operands, operandsUsed);

    // Fix operand indexes.
    const auto mappedOperandIndices = getMapping(operandsUsed);
    for (auto& operation : model->main.operations) {
        remapIndexes(&operation.inputs, mappedOperandIndices);
        remapIndexes(&operation.outputs, mappedOperandIndices);
    }
    remapIndexes(&model->main.inputIndexes, mappedOperandIndices);
    remapIndexes(&model->main.outputIndexes, mappedOperandIndices);

    // Keep only the subgraphs which are used.
    const auto subgraphsUsed = identifyUsedSubgraphs(*model);
    keepSelectedElements(&model->referenced, subgraphsUsed);

    // Keep only the pools which are used.
    const auto poolsUsed = identifyUsedPools(*model);
    keepSelectedElements(&model->pools, poolsUsed);

    // Fix operand locations.
    const auto mappedPoolIndices = getMapping(poolsUsed);
    const auto mappedSubgraphIndices = getMapping(subgraphsUsed);
    fixOperandDataLocations(model, mappedPoolIndices, mappedSubgraphIndices);

    // Keep only the extensionNameToPrefixes which are used.
    const auto extensionsUsed = identifyUsedExtensions(*model);
    keepSelectedElements(&model->extensionNameToPrefix, extensionsUsed);
}

}  // namespace android::nn
