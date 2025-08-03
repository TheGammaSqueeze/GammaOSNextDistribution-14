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

#include <android-base/logging.h>

#include <cstdlib>
#include <optional>
#include <utility>

#include "NeuralNetworksWrapper.h"
#include "TestHarness.h"

namespace {

using ::android::nn::wrapper::Compilation;
using ::android::nn::wrapper::Execution;
using ::android::nn::wrapper::Model;
using ::android::nn::wrapper::OperandType;
using ::android::nn::wrapper::Result;
using ::android::nn::wrapper::SymmPerChannelQuantParams;
using ::android::nn::wrapper::Type;
using ::test_helper::TestModel;
using ::test_helper::TestOperand;
using ::test_helper::TestOperandLifeTime;
using ::test_helper::TestOperandType;
using ::test_helper::TestSubgraph;

OperandType getOperandType(const TestOperand& op) {
    const auto& dims = op.dimensions;
    if (op.type == TestOperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL) {
        return OperandType(
                static_cast<Type>(op.type), dims,
                SymmPerChannelQuantParams(op.channelQuant.scales, op.channelQuant.channelDim));
    } else {
        return OperandType(static_cast<Type>(op.type), dims, op.scale, op.zeroPoint);
    }
}

enum class Visited : uint8_t {
    NOT_YET_VISITED,
    CURRENTLY_VISITING,
    ALREADY_VISITED,
};

bool areSubgraphsAcyclic(const TestModel& testModel, size_t index, std::vector<Visited>* visited,
                         std::vector<size_t>* order) {
    if (index >= visited->size()) return false;
    Visited& status = (*visited)[index];

    if (status == Visited::CURRENTLY_VISITING) return false;
    if (status == Visited::ALREADY_VISITED) return true;
    status = Visited::CURRENTLY_VISITING;

    const auto& subgraph = index == 0 ? testModel.main : testModel.referenced[index - 1];
    for (const auto& operand : subgraph.operands) {
        if (operand.lifetime != TestOperandLifeTime::SUBGRAPH) continue;
        if (operand.data.size() < sizeof(uint32_t)) return false;
        if (operand.data.get<void>() == nullptr) return false;
        const uint32_t subgraphIndex = *operand.data.get<uint32_t>();
        if (!areSubgraphsAcyclic(testModel, subgraphIndex + 1, visited, order)) return false;
    }

    status = Visited::ALREADY_VISITED;
    order->push_back(index);
    return true;
}

std::optional<std::vector<size_t>> getSubgraphOrder(const TestModel& testModel) {
    std::vector<Visited> visited(testModel.referenced.size() + 1, Visited::NOT_YET_VISITED);
    std::vector<size_t> order;
    order.reserve(visited.size());
    if (!areSubgraphsAcyclic(testModel, 0, &visited, &order)) return std::nullopt;
    return order;
}

std::optional<Model> CreateSubgraph(const TestModel& testModel, size_t subgraphIndex,
                                    const std::vector<Model>& subgraphs) {
    const TestSubgraph& testSubgraph =
            subgraphIndex == 0 ? testModel.main : testModel.referenced[subgraphIndex - 1];
    Model model;

    // Operands.
    for (const auto& operand : testSubgraph.operands) {
        auto type = getOperandType(operand);
        auto index = model.addOperand(&type);

        switch (operand.lifetime) {
            case TestOperandLifeTime::CONSTANT_COPY:
            case TestOperandLifeTime::CONSTANT_REFERENCE:
                model.setOperandValue(index, operand.data.get<void>(), operand.data.size());
                break;
            case TestOperandLifeTime::NO_VALUE:
                model.setOperandValue(index, nullptr, 0);
                break;
            case TestOperandLifeTime::SUBGRAPH: {
                const uint32_t referencedSubgraphIndex = *operand.data.get<uint32_t>();
                model.setOperandValueFromModel(index, &subgraphs[referencedSubgraphIndex]);
            } break;
            case TestOperandLifeTime::SUBGRAPH_INPUT:
            case TestOperandLifeTime::SUBGRAPH_OUTPUT:
            case TestOperandLifeTime::TEMPORARY_VARIABLE:
                // Nothing to do here.
                break;
        }
        if (!model.isValid()) return std::nullopt;
    }

    // Operations.
    for (const auto& operation : testSubgraph.operations) {
        model.addOperation(static_cast<int>(operation.type), operation.inputs, operation.outputs);
        if (!model.isValid()) return std::nullopt;
    }

    // Inputs and outputs.
    model.identifyInputsAndOutputs(testSubgraph.inputIndexes, testSubgraph.outputIndexes);
    if (!model.isValid()) return std::nullopt;

    // Relaxed computation.
    model.relaxComputationFloat32toFloat16(testModel.isRelaxed);
    if (!model.isValid()) return std::nullopt;

    if (model.finish() != Result::NO_ERROR) {
        return std::nullopt;
    }

    return model;
}

// The first Model returned is the main model. Any subsequent Models are referenced models.
std::optional<std::vector<Model>> CreateModels(const TestModel& testModel) {
    auto subgraphOrder = getSubgraphOrder(testModel);
    if (!subgraphOrder.has_value()) return std::nullopt;

    std::vector<Model> subgraphs(testModel.referenced.size() + 1);
    for (size_t index : subgraphOrder.value()) {
        auto subgraph = CreateSubgraph(testModel, index, subgraphs);
        if (!subgraph.has_value()) return std::nullopt;
        subgraphs[index] = std::move(subgraph).value();
    }

    return subgraphs;
}

std::optional<Compilation> CreateCompilation(const Model& model) {
    Compilation compilation(&model);
    if (compilation.finish() != Result::NO_ERROR) {
        return std::nullopt;
    }
    return compilation;
}

std::optional<Execution> CreateExecution(const Compilation& compilation,
                                         const TestModel& testModel) {
    Execution execution(&compilation);

    // Model inputs.
    for (uint32_t i = 0; i < testModel.main.inputIndexes.size(); i++) {
        const auto& operand = testModel.main.operands[testModel.main.inputIndexes[i]];
        if (execution.setInput(i, operand.data.get<void>(), operand.data.size()) !=
            Result::NO_ERROR) {
            return std::nullopt;
        }
    }

    // Model outputs.
    for (uint32_t i = 0; i < testModel.main.outputIndexes.size(); i++) {
        const auto& operand = testModel.main.operands[testModel.main.outputIndexes[i]];
        if (execution.setOutput(i, const_cast<void*>(operand.data.get<void>()),
                                operand.data.size()) != Result::NO_ERROR) {
            return std::nullopt;
        }
    }

    return execution;
}

}  // anonymous namespace

void nnapiFuzzTest(const TestModel& testModel) {
    // set up model
    auto models = CreateModels(testModel);
    if (!models.has_value() || models->empty()) {
        return;
    }

    // set up compilation
    auto compilation = CreateCompilation(models->front());
    if (!compilation.has_value()) {
        return;
    }

    // set up execution
    auto execution = CreateExecution(*compilation, testModel);
    if (!execution.has_value()) {
        return;
    }

    // perform execution
    execution->compute();
}
