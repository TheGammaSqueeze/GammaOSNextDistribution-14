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

#include "SharedMemory.h"

#include <android-base/logging.h>

#include <algorithm>
#include <limits>
#include <optional>
#include <utility>
#include <variant>
#include <vector>

#include "Result.h"
#include "TypeUtils.h"
#include "Types.h"

namespace android::nn {
namespace {

bool hasNoPointerData(const Operand& operand);
bool hasNoPointerData(const Model::Subgraph& subgraph);
bool hasNoPointerData(const Request::Argument& argument);

template <typename Type>
bool hasNoPointerData(const std::vector<Type>& objects) {
    return std::all_of(objects.begin(), objects.end(),
                       [](const auto& object) { return hasNoPointerData(object); });
}

bool hasNoPointerData(const DataLocation& location) {
    return std::visit([](auto ptr) { return ptr == nullptr; }, location.pointer);
}

bool hasNoPointerData(const Operand& operand) {
    return hasNoPointerData(operand.location);
}

bool hasNoPointerData(const Model::Subgraph& subgraph) {
    return hasNoPointerData(subgraph.operands);
}

bool hasNoPointerData(const Request::Argument& argument) {
    return hasNoPointerData(argument.location);
}

void copyPointersToSharedMemory(Operand* operand, ConstantMemoryBuilder* memoryBuilder) {
    CHECK(operand != nullptr);
    CHECK(memoryBuilder != nullptr);

    if (operand->lifetime != Operand::LifeTime::POINTER) {
        return;
    }

    const void* data = std::visit([](auto ptr) { return static_cast<const void*>(ptr); },
                                  operand->location.pointer);
    CHECK(data != nullptr);
    operand->lifetime = Operand::LifeTime::CONSTANT_REFERENCE;
    operand->location = memoryBuilder->append(data, operand->location.length);
}

void copyPointersToSharedMemory(Model::Subgraph* subgraph, ConstantMemoryBuilder* memoryBuilder) {
    CHECK(subgraph != nullptr);
    std::for_each(subgraph->operands.begin(), subgraph->operands.end(),
                  [memoryBuilder](auto& operand) {
                      copyPointersToSharedMemory(&operand, memoryBuilder);
                  });
}

}  // anonymous namespace

MutableMemoryBuilder::MutableMemoryBuilder(uint32_t poolIndex) : mPoolIndex(poolIndex) {}

DataLocation MutableMemoryBuilder::append(size_t length, size_t alignment, size_t padding) {
    CHECK_GT(length, 0u);
    mSize = roundUp(mSize, alignment);
    const size_t offset = mSize;
    const size_t paddedLength = roundUp(length, padding);
    CHECK_LE(offset, std::numeric_limits<uint32_t>::max());
    CHECK_LE(paddedLength, std::numeric_limits<uint32_t>::max());
    mSize += paddedLength;
    return {.poolIndex = mPoolIndex,
            .offset = static_cast<uint32_t>(offset),
            .length = static_cast<uint32_t>(length),
            .padding = static_cast<uint32_t>(paddedLength - length)};
}

bool MutableMemoryBuilder::empty() const {
    return mSize == 0;
}

GeneralResult<SharedMemory> MutableMemoryBuilder::finish() {
    return createSharedMemory(mSize);
}

ConstantMemoryBuilder::ConstantMemoryBuilder(uint32_t poolIndex) : mBuilder(poolIndex) {}

DataLocation ConstantMemoryBuilder::append(const void* data, size_t length) {
    const auto location = mBuilder.append(length);
    CHECK_EQ(location.length, length);
    mSlices.push_back({.data = data, .length = length, .offset = location.offset});
    return location;
}

bool ConstantMemoryBuilder::empty() const {
    return mBuilder.empty();
}

GeneralResult<SharedMemory> ConstantMemoryBuilder::finish() {
    // Allocate the memory.
    auto memory = NN_TRY(mBuilder.finish());

    // Map the memory.
    const auto [pointer, size, context] = NN_TRY(map(memory););

    // Get mutable pointer.
    uint8_t* mutablePointer = static_cast<uint8_t*>(std::get<void*>(pointer));

    // Copy data to the memory pool.
    std::for_each(mSlices.begin(), mSlices.end(), [mutablePointer](const auto& slice) {
        std::memcpy(mutablePointer + slice.offset, slice.data, slice.length);
    });

    return memory;
}

bool hasNoPointerData(const Model& model) {
    return hasNoPointerData(model.main) && hasNoPointerData(model.referenced);
}

bool hasNoPointerData(const Request& request) {
    return hasNoPointerData(request.inputs) && hasNoPointerData(request.outputs);
}

GeneralResult<std::reference_wrapper<const Model>> flushDataFromPointerToShared(
        const Model* model, std::optional<Model>* maybeModelInSharedOut) {
    CHECK(model != nullptr);
    CHECK(maybeModelInSharedOut != nullptr);

    if (hasNoPointerData(*model)) {
        return *model;
    }

    // Make a copy of the model in order to make modifications. The modified model is returned to
    // the caller through `maybeModelInSharedOut` if the function succeeds.
    Model modelInShared = *model;

    ConstantMemoryBuilder memoryBuilder(modelInShared.pools.size());
    copyPointersToSharedMemory(&modelInShared.main, &memoryBuilder);
    std::for_each(modelInShared.referenced.begin(), modelInShared.referenced.end(),
                  [&memoryBuilder](auto& subgraph) {
                      copyPointersToSharedMemory(&subgraph, &memoryBuilder);
                  });

    if (!memoryBuilder.empty()) {
        auto memory = NN_TRY(memoryBuilder.finish());
        modelInShared.pools.push_back(std::move(memory));
    }

    *maybeModelInSharedOut = modelInShared;
    return **maybeModelInSharedOut;
}

template <>
void InputRelocationTracker::flush() const {
    // Copy from pointers to shared memory.
    uint8_t* memoryPtr = static_cast<uint8_t*>(std::get<void*>(kMapping.pointer));
    for (const auto& [data, length, offset] : kRelocationInfos) {
        std::memcpy(memoryPtr + offset, data, length);
    }
}

template <>
void OutputRelocationTracker::flush() const {
    // Copy from shared memory to pointers.
    const uint8_t* memoryPtr = static_cast<const uint8_t*>(
            std::visit([](auto ptr) { return static_cast<const void*>(ptr); }, kMapping.pointer));
    for (const auto& [data, length, offset] : kRelocationInfos) {
        std::memcpy(data, memoryPtr + offset, length);
    }
}

GeneralResult<std::reference_wrapper<const Request>> convertRequestFromPointerToShared(
        const Request* request, uint32_t alignment, uint32_t padding,
        std::optional<Request>* maybeRequestInSharedOut, RequestRelocation* relocationOut) {
    CHECK(request != nullptr);
    CHECK(maybeRequestInSharedOut != nullptr);
    CHECK(relocationOut != nullptr);

    if (hasNoPointerData(*request)) {
        return *request;
    }

    // Make a copy of the request in order to make modifications. The modified request is returned
    // to the caller through `maybeRequestInSharedOut` if the function succeeds.
    Request requestInShared = *request;

    RequestRelocation relocation;

    // Change input pointers to shared memory.
    MutableMemoryBuilder inputBuilder(requestInShared.pools.size());
    std::vector<InputRelocationInfo> inputRelocationInfos;
    for (auto& input : requestInShared.inputs) {
        const auto& location = input.location;
        if (input.lifetime != Request::Argument::LifeTime::POINTER) {
            continue;
        }

        input.lifetime = Request::Argument::LifeTime::POOL;
        const void* data = std::visit([](auto ptr) { return static_cast<const void*>(ptr); },
                                      location.pointer);
        CHECK(data != nullptr);
        input.location = inputBuilder.append(location.length, alignment, padding);
        inputRelocationInfos.push_back({data, input.location.length, input.location.offset});
    }

    // Allocate input memory.
    if (!inputBuilder.empty()) {
        auto memory = NN_TRY(inputBuilder.finish());
        requestInShared.pools.push_back(memory);
        relocation.input = NN_TRY(
                InputRelocationTracker::create(std::move(inputRelocationInfos), std::move(memory)));
    }

    // Change output pointers to shared memory.
    MutableMemoryBuilder outputBuilder(requestInShared.pools.size());
    std::vector<OutputRelocationInfo> outputRelocationInfos;
    for (auto& output : requestInShared.outputs) {
        const auto& location = output.location;
        if (output.lifetime != Request::Argument::LifeTime::POINTER) {
            continue;
        }

        output.lifetime = Request::Argument::LifeTime::POOL;
        void* data = std::get<void*>(location.pointer);
        CHECK(data != nullptr);
        output.location = outputBuilder.append(location.length, alignment, padding);
        outputRelocationInfos.push_back({data, output.location.length, output.location.offset});
    }

    // Allocate output memory.
    if (!outputBuilder.empty()) {
        auto memory = NN_TRY(outputBuilder.finish());
        requestInShared.pools.push_back(memory);
        relocation.output = NN_TRY(OutputRelocationTracker::create(std::move(outputRelocationInfos),
                                                                   std::move(memory)));
    }

    *maybeRequestInSharedOut = requestInShared;
    *relocationOut = std::move(relocation);
    return **maybeRequestInSharedOut;
}

}  // namespace android::nn
