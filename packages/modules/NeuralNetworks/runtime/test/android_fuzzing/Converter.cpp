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

#include "Converter.h"

#include <android-base/logging.h>
#include <nnapi/TypeUtils.h>

#include <algorithm>
#include <random>
#include <utility>
#include <vector>

namespace android::nn::fuzz {
namespace {

using namespace test_helper;

constexpr uint32_t kMaxSize = 65536;

TestOperandType convert(OperandType type) {
    return static_cast<TestOperandType>(type);
}

TestOperationType convert(OperationType type) {
    return static_cast<TestOperationType>(type);
}

TestOperandLifeTime convert(OperandLifeTime lifetime) {
    return static_cast<TestOperandLifeTime>(lifetime);
}

std::vector<float> convert(const Scales& scales) {
    const auto& repeatedScale = scales.scale();
    return std::vector<float>(repeatedScale.begin(), repeatedScale.end());
}

TestSymmPerChannelQuantParams convert(const SymmPerChannelQuantParams& params) {
    std::vector<float> scales = convert(params.scales());
    const uint32_t channelDim = params.channel_dim();
    return {.scales = std::move(scales), .channelDim = channelDim};
}

std::vector<uint32_t> convert(const Dimensions& dimensions) {
    const auto& repeatedDimension = dimensions.dimension();
    return std::vector<uint32_t>(repeatedDimension.begin(), repeatedDimension.end());
}

TestBuffer convert(size_t size, bool initialize, const Buffer& buffer) {
    switch (buffer.type_case()) {
        case Buffer::TypeCase::TYPE_NOT_SET:
        case Buffer::TypeCase::kEmpty:
            break;
        case Buffer::TypeCase::kScalar: {
            const uint32_t scalar = buffer.scalar();
            return TestBuffer(sizeof(scalar), &scalar);
        }
        case Buffer::TypeCase::kRandomSeed: {
            if (!initialize) {
                return TestBuffer(size);
            }
            const uint32_t randomSeed = buffer.random_seed();
            std::default_random_engine generator{randomSeed};
            return TestBuffer::createRandom(size, &generator);
        }
    }
    return TestBuffer();
}

TestOperand convert(const Operand& operand) {
    const TestOperandType type = convert(operand.type());
    std::vector<uint32_t> dimensions = convert(operand.dimensions());
    const float scale = operand.scale();
    const int32_t zeroPoint = operand.zero_point();
    const TestOperandLifeTime lifetime = convert(operand.lifetime());
    auto channelQuant = convert(operand.channel_quant());

    const bool isIgnored = lifetime == TestOperandLifeTime::SUBGRAPH_OUTPUT;
    const auto opType = static_cast<nn::OperandType>(type);
    const size_t size = getNonExtensionSize(opType, dimensions).value_or(0) % kMaxSize;
    const bool makeEmpty = (lifetime == TestOperandLifeTime::NO_VALUE ||
                            lifetime == TestOperandLifeTime::TEMPORARY_VARIABLE);
    const size_t bufferSize = makeEmpty ? 0 : size;
    TestBuffer data = convert(bufferSize, !isIgnored, operand.data());

    return {.type = type,
            .dimensions = std::move(dimensions),
            .numberOfConsumers = 0,
            .scale = scale,
            .zeroPoint = zeroPoint,
            .lifetime = lifetime,
            .channelQuant = std::move(channelQuant),
            .isIgnored = isIgnored,
            .data = std::move(data)};
}

std::vector<TestOperand> convert(const Operands& operands) {
    std::vector<TestOperand> testOperands;
    testOperands.reserve(operands.operand_size());
    const auto& repeatedOperand = operands.operand();
    std::transform(repeatedOperand.begin(), repeatedOperand.end(), std::back_inserter(testOperands),
                   [](const auto& operand) { return convert(operand); });
    return testOperands;
}

std::vector<uint32_t> convert(const Indexes& indexes) {
    const auto& repeatedIndex = indexes.index();
    return std::vector<uint32_t>(repeatedIndex.begin(), repeatedIndex.end());
}

TestOperation convert(const Operation& operation) {
    const TestOperationType type = convert(operation.type());
    std::vector<uint32_t> inputs = convert(operation.inputs());
    std::vector<uint32_t> outputs = convert(operation.outputs());
    return {.type = type, .inputs = std::move(inputs), .outputs = std::move(outputs)};
}

std::vector<TestOperation> convert(const Operations& operations) {
    std::vector<TestOperation> testOperations;
    testOperations.reserve(operations.operation_size());
    const auto& repeatedOperation = operations.operation();
    std::transform(repeatedOperation.begin(), repeatedOperation.end(),
                   std::back_inserter(testOperations),
                   [](const auto& operation) { return convert(operation); });
    return testOperations;
}

void calculateNumberOfConsumers(const std::vector<TestOperation>& operations,
                                std::vector<TestOperand>* operands) {
    CHECK(operands != nullptr);
    const auto addConsumer = [operands](uint32_t operand) {
        if (operand < operands->size()) {
            operands->at(operand).numberOfConsumers++;
        }
    };
    const auto addAllConsumers = [&addConsumer](const TestOperation& operation) {
        std::for_each(operation.inputs.begin(), operation.inputs.end(), addConsumer);
    };
    std::for_each(operations.begin(), operations.end(), addAllConsumers);
}

TestSubgraph convert(const Subgraph& subgraph) {
    std::vector<TestOperand> operands = convert(subgraph.operands());
    std::vector<TestOperation> operations = convert(subgraph.operations());
    std::vector<uint32_t> inputIndexes = convert(subgraph.input_indexes());
    std::vector<uint32_t> outputIndexes = convert(subgraph.output_indexes());

    // Calculate number of consumers.
    calculateNumberOfConsumers(operations, &operands);

    return {.operands = std::move(operands),
            .operations = std::move(operations),
            .inputIndexes = std::move(inputIndexes),
            .outputIndexes = std::move(outputIndexes)};
}

std::vector<TestSubgraph> convert(const Subgraphs& subgraphs) {
    std::vector<TestSubgraph> testSubgraphs;
    testSubgraphs.reserve(subgraphs.subgraph_size());
    const auto& repeatedSubgraph = subgraphs.subgraph();
    std::transform(repeatedSubgraph.begin(), repeatedSubgraph.end(),
                   std::back_inserter(testSubgraphs),
                   [](const auto& subgraph) { return convert(subgraph); });
    return testSubgraphs;
}

TestModel convert(const Model& model) {
    TestSubgraph main = convert(model.main());
    std::vector<TestSubgraph> referenced = convert(model.referenced());
    const bool isRelaxed = model.is_relaxed();

    return {.main = std::move(main), .referenced = std::move(referenced), .isRelaxed = isRelaxed};
}

}  // anonymous namespace

TestModel convertToTestModel(const Test& model) {
    return convert(model.model());
}

}  // namespace android::nn::fuzz
