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

#define LOG_TAG "MetaModel"

#include "MetaModel.h"

#include <algorithm>
#include <map>
#include <numeric>
#include <set>
#include <sstream>
#include <type_traits>
#include <utility>
#include <vector>

#include "GraphDump.h"
#include "LegacyUtils.h"
#include "nnapi/TypeUtils.h"
#include "nnapi/Types.h"
#include "nnapi/Validation.h"

namespace android::nn {

namespace {

// Add an element to the end of the vector, set it to the specified value, and
// return a pair consisting of the index of the new element and a pointer to the
// new element.
template <class T>
std::pair<uint32_t, T*> extend(std::vector<T>* vec, const T& val) {
    vec->push_back(val);
    return {vec->size() - 1, &vec->back()};
}

// Add an element to the end of the vector and return a pair consisting of the
// index of the new element and a pointer to the new element.
template <class T>
std::pair<uint32_t, T*> extend(std::vector<T>* vec) {
    return extend(vec, {});
}

bool invalid(const Model& model, Version version, bool strictSlicing) {
    // A model must have at least one operation.  However, it's possible that a
    // slice has no operations (because no operations from the original model
    // are compliant with the sliced model type).  In this case, the sliced
    // model would be invalid.
    const bool looksEmpty = (model.main.operations.size() == 0);
    if (strictSlicing) {
        CHECK_EQ(looksEmpty, (model.main.operands.size() == 0));
    }
    if (looksEmpty) return true;

    // A model must have at least one output.  However, it's possible for a
    // model to contain dead operations (i.e., outputs on which no model outputs
    // are data dependent).  A slice might contain only dead operations, and
    // hence have no model outputs.  In this case, the sliced model would be
    // invalid.
    if (model.main.outputIndexes.size() == 0) return true;

    // We shouldn't have to check whether the model is valid. However, it could
    // be invalid if there is an error in the slicing algorithm.
    auto maybeVersion = validate(model);
    if (!maybeVersion.has_value()) {
        LOG(WARNING) << "Sliced model fails validate(): " << maybeVersion.error();
        CHECK(!strictSlicing);
        return true;
    }
    if (!isCompliantVersion(maybeVersion.value(), version)) {
        LOG(WARNING) << "Sliced model fails validate(): insufficient version ("
                     << maybeVersion.value() << " vs " << version << ")";
        CHECK(!strictSlicing);
        return true;
    }

    return false;
}

}  // anonymous namespace

MetaModel::MetaModel(Model model, bool strictSlicing)
    : mModel(std::move(model)),
      mModelMinimumSupportedVersion(validate(mModel).value()),
      mStrictSlicing(strictSlicing) {}

MetaModel::ReturnedSlice MetaModel::getSlice(Version version) const {
    // All slices of versions of at least mModelMinimumSupportedVersion are identical, so do not
    // create more than one such slice.
    version.level = std::min(version.level, mModelMinimumSupportedVersion.level);
    version.runtimeOnlyFeatures &= mModelMinimumSupportedVersion.runtimeOnlyFeatures;

    auto& slice = mCachedSlices[version];
    if (slice.mState == SliceState::UNINITIALIZED) {
        slice = makeSlice(version);
    }
    if (slice.mState == SliceState::INVALID) {
        return {};
    }
    return MetaModel::ReturnedSlice(std::make_pair(
            slice.mModel, Mapper([&slice](uint32_t slicedOperationIndex) {
                return slice.mSlicedOperationIndexToOrigIndex.at(slicedOperationIndex);
            })));
}

// Utility class for makeSlice().
//
// For each output operand of a noncompliant operation that is the input
// operand of at least one compliant operation, we will ensure that there is
// a sliced model input whose "type" is that of the output operand.  This is
// a map from operand "type" (in the original model) to model input operand
// index (in the sliced model).  We only use the subset of the fields that are
// relevant (OperandType, dimensions, scale, zeroPoint, extraParams), but
// exclude irrelevant fields from the map key (lifetime, location).
//
// We also use this map for model input operands of the original model that
// become input operands of the sliced model.  This means that an original
// model input operand might be commoned with other original model input
// operands and/or with original model temporary operands.
class MetaModel::OrigOperandToSlicedInputOperandIndex {
   public:
    // `slicedOperands` and `slicedInputIndexes` will be modified as part of
    // OrigOperandToSlicedInputOperandIndex::getIndex. `slicedVersion`, `operandValuesSize`, and
    // `poolSizes` are used as a check to ensure that the sliced operand is valid and compliant with
    // the sliced version. `operandValuesSize` is the size of the operand values in the sliced model
    // (which is the same as the original model). `poolSizes` is the size of the memories in the
    // sliced model (which is the same as the original model).
    OrigOperandToSlicedInputOperandIndex(std::vector<Operand>* slicedOperands,
                                         std::vector<uint32_t>* slicedInputIndexes,
                                         Version slicedVersion, size_t operandValuesSize,
                                         std::vector<size_t> poolSizes)
        : mSlicedOperands(*slicedOperands),
          mSlicedInputIndexes(*slicedInputIndexes),
          kSlicedVersion(slicedVersion),
          kOperandValuesSize(operandValuesSize),
          kPoolSizes(std::move(poolSizes)) {}

    // Given an operand from the original model, return the index of the
    // corresponding model input operand from the sliced model.  Creates a
    // new operand in the sliced model if necessary.
    uint32_t getIndex(Operand operand) {
        CHECK(operand.lifetime == Operand::LifeTime::SUBGRAPH_INPUT ||
              operand.lifetime == Operand::LifeTime::SUBGRAPH_OUTPUT ||
              operand.lifetime == Operand::LifeTime::TEMPORARY_VARIABLE);

        // Lookup
        auto it = mMap.find(operand);
        if (it != mMap.end()) {
            VLOG(COMPILATION) << "OrigOperandToSlicedInputOperandIndex::getIndex looked for "
                              << operand << " and found " << it->second << ": " << it->first;
            return it->second;
        }

        // Create
        operand.lifetime = Operand::LifeTime::SUBGRAPH_INPUT;
        operand.location = {};

        // Note that the sliced model does not contain any referenced subgraphs, so both `subgraphs`
        // and `subgraphVersionCache` are empty.
        const std::vector<Model::Subgraph> subgraphs;
        auto subgraphVersionCache = createSubgraphVersionCache(subgraphs.size());
        const auto minimumSupportedOperandVersion =
                validateOperandAndAnythingItDependsOn(operand, kOperandValuesSize, kPoolSizes,
                                                      subgraphs, subgraphVersionCache.get())
                        .value();
        CHECK(isCompliantVersion(minimumSupportedOperandVersion, kSlicedVersion));

        uint32_t slicedOperandIndex = extend(&mSlicedOperands, operand).first;
        mMap[operand] = slicedOperandIndex;
        extend(&mSlicedInputIndexes, slicedOperandIndex);
        VLOG(COMPILATION) << "OrigOperandToSlicedInputOperandIndex::getIndex created "
                          << slicedOperandIndex << ": " << operand;
        return slicedOperandIndex;
    }

   private:
    class Compare {
       public:
        bool operator()(const Operand& a, const Operand& b) const {
            if (a.type != b.type) {
                return a.type < b.type;
            }
            if (a.dimensions != b.dimensions) {
                return a.dimensions < b.dimensions;
            }
            if (a.scale != b.scale) {
                return a.scale < b.scale;
            }
            if (a.zeroPoint != b.zeroPoint) {
                return a.zeroPoint < b.zeroPoint;
            }
            return compare(a.extraParams, b.extraParams);
        }

       private:
        static bool compare(const Operand::SymmPerChannelQuantParams& a,
                            const Operand::SymmPerChannelQuantParams& b) {
            if (a.scales != b.scales) {
                return a.scales < b.scales;
            }
            return a.channelDim < b.channelDim;
        }
        static bool compare(const Operand::ExtraParams& a, const Operand::ExtraParams& b) {
            if (a.index() != b.index()) {
                return a.index() < b.index();
            }
            if (std::holds_alternative<Operand::SymmPerChannelQuantParams>(a)) {
                return compare(std::get<Operand::SymmPerChannelQuantParams>(a),
                               std::get<Operand::SymmPerChannelQuantParams>(b));
            }
            if (std::holds_alternative<Operand::ExtensionParams>(a)) {
                return std::get<Operand::ExtensionParams>(a) <
                       std::get<Operand::ExtensionParams>(b);
            }
            if (std::holds_alternative<Operand::NoParams>(a)) {
                return false;
            }
            CHECK(false) << "Unexpected";
            return false;
        }
    };
    std::map<Operand, uint32_t, Compare> mMap;
    std::vector<Operand>& mSlicedOperands;
    std::vector<uint32_t>& mSlicedInputIndexes;
    const Version kSlicedVersion;
    const size_t kOperandValuesSize;
    const std::vector<size_t> kPoolSizes;
};

void MetaModel::processOperations(
        Slice* slice, std::map<uint32_t, uint32_t>* origOperandIndexToSlicedIndex,
        OrigOperandToSlicedInputOperandIndex* origOperandToSlicedInputOperandIndex,
        const std::set<uint32_t>& noncompliantOperations,
        const std::set<uint32_t>& inputOperandIndexesOfCompliantOperations) const {
    const auto& origOperands = mModel.main.operands;
    const auto& origOperations = mModel.main.operations;
    auto& slicedOperands = slice->mModel.main.operands;
    auto& slicedOperations = slice->mModel.main.operations;

    std::vector<uint32_t> origOperandNumberOfConsumers =
            countNumberOfConsumers(origOperands.size(), origOperations).value();

    for (uint32_t origOperationIndex = 0; origOperationIndex < origOperations.size();
         ++origOperationIndex) {
        const Operation& origOperation = origOperations[origOperationIndex];

        if (noncompliantOperations.count(origOperationIndex)) {
            for (uint32_t output : origOperation.outputs) {
                if (!inputOperandIndexesOfCompliantOperations.count(output)) {
                    continue;
                }
                const uint32_t slicedIndex =
                        origOperandToSlicedInputOperandIndex->getIndex(origOperands[output]);
                (*origOperandIndexToSlicedIndex)[output] = slicedIndex;
                VLOG(COMPILATION)
                        << "origOperandIndexToSlicedIndex noncompliant output processing created "
                        << output << " -> " << slicedIndex << ": " << slicedOperands[slicedIndex];
            }
        } else {
            slice->mSlicedOperationIndexToOrigIndex.push_back(origOperationIndex);
            Operation& slicedOperation = *extend(&slicedOperations).second;
            CHECK_EQ(slice->mSlicedOperationIndexToOrigIndex.size(), slicedOperations.size());

            slicedOperation.type = origOperation.type;

            // Model is topologically sorted, so all operation inputs must be
            // present in origOperandIndexToSlicedIndex, and no operation
            // outputs may be.

            // Operation inputs
            // - Fill in slicedOperation.inputs
            slicedOperation.inputs.resize(origOperation.inputs.size());
            std::transform(
                    origOperation.inputs.begin(), origOperation.inputs.end(),
                    slicedOperation.inputs.begin(),
                    [&origOperandIndexToSlicedIndex, &slicedOperands](uint32_t origOperandIndex) {
                        uint32_t slicedOperandIndex =
                                origOperandIndexToSlicedIndex->at(origOperandIndex);
                        VLOG(COMPILATION) << "origOperandIndexToSlicedIndex compliant input "
                                             "processing created "
                                          << origOperandIndex << " -> " << slicedOperandIndex
                                          << ": " << slicedOperands[slicedOperandIndex];
                        return slicedOperandIndex;
                    });

            // Operation outputs
            // - Add new operands to slicedOperands
            // - Update origOperandIndexToSlicedIndex
            // - Fill in slicedOperation.outputs
            // - Record as a model output, if necessary
            const uint32_t firstOutputSlicedOperandIndex = slicedOperands.size();
            slicedOperands.resize(firstOutputSlicedOperandIndex + origOperation.outputs.size());
            slicedOperation.outputs.resize(origOperation.outputs.size());
            for (uint32_t outputNum = 0; outputNum < slicedOperation.outputs.size(); ++outputNum) {
                uint32_t origOperandIndex = origOperation.outputs[outputNum];
                uint32_t slicedOperandIndex = firstOutputSlicedOperandIndex + outputNum;
                auto& slicedOperand = slicedOperands[slicedOperandIndex];
                const auto& origOperand = origOperands[origOperandIndex];
                slicedOperand = origOperand;

                CHECK_EQ(origOperandIndexToSlicedIndex->count(origOperandIndex), size_t(0));
                (*origOperandIndexToSlicedIndex)[origOperandIndex] = slicedOperandIndex;
                slicedOperation.outputs[outputNum] = slicedOperandIndex;

                const auto subgraphOutputLifetime = Operand::LifeTime::SUBGRAPH_OUTPUT;
                if (!inputOperandIndexesOfCompliantOperations.count(origOperandIndex) &&
                    origOperandNumberOfConsumers[origOperandIndex] != 0) {
                    // Was consumed only by noncompliant operations; convert to
                    // an output of the sliced model.
                    slicedOperand.lifetime = subgraphOutputLifetime;
                }

                VLOG(COMPILATION) << "origOperandIndexToSlicedIndex compliant output created "
                                  << origOperandIndex << " -> " << slicedOperandIndex << ": "
                                  << slicedOperand;

                if (slicedOperand.lifetime == subgraphOutputLifetime) {
                    extend(&slice->mModel.main.outputIndexes, slicedOperandIndex);
                }
            }
        }
    }
}

std::set<uint32_t> MetaModel::getNoncompliantOperations(Version version) const {
    const auto [operandValuesSize, poolSizes] = getMemorySizes(mModel);

    auto subgraphVersionCache = createSubgraphVersionCache(mModel.referenced.size());
    std::set<uint32_t> noncompliantOperations;
    for (uint32_t i = 0; i < mModel.main.operations.size(); ++i) {
        const auto& operation = mModel.main.operations[i];
        const auto minSupportedVersion =
                validateOperationAndAnythingItDependsOn(
                        operation, mModel.main.operands, operandValuesSize, poolSizes,
                        mModel.referenced, subgraphVersionCache.get())
                        .value();
        if (!isCompliantVersion(minSupportedVersion, version)) {
            noncompliantOperations.insert(i);
        }
    }
    return noncompliantOperations;
}

bool MetaModel::Comparison::operator()(Version lhs, Version rhs) const {
    constexpr auto toTuple = [](const Version& v) {
        return std::tie(v.level, v.runtimeOnlyFeatures);
    };
    // Lexicographical comparison of the fields. The bool is promoted to an integer for the
    // comparison such that "false < true".
    return toTuple(lhs) < toTuple(rhs);
}

MetaModel::Slice MetaModel::makeSlice(Version version) const {
    Slice slice;

    // Quickly return if the model is already compliant with `version`
    if (isCompliantVersion(mModelMinimumSupportedVersion, version)) {
        slice.mModel = mModel;
        slice.mSlicedOperationIndexToOrigIndex =
                std::vector<uint32_t>(mModel.main.operations.size());
        std::iota(slice.mSlicedOperationIndexToOrigIndex.begin(),
                  slice.mSlicedOperationIndexToOrigIndex.end(), 0u);
        slice.mState = SliceState::NORMAL;
        return slice;
    }

    const auto& origOperands = mModel.main.operands;
    const auto& origOperations = mModel.main.operations;
    auto& slicedOperands = slice.mModel.main.operands;

    // Indexes of elements of noncompliant origOperations
    std::set<uint32_t> noncompliantOperations = getNoncompliantOperations(version);

    // Check if any compliant operations require a subgraph.
    bool someCompliantOperationHasASubgraphOperand = false;
    if (!mModel.referenced.empty()) {
        for (size_t i = 0; i < mModel.main.operations.size(); ++i) {
            const auto& operation = mModel.main.operations[i];
            if (noncompliantOperations.count(i) > 0) {
                continue;
            }
            const auto isSubgraph = [&origOperands](uint32_t opndIdx) {
                return origOperands[opndIdx].lifetime == Operand::LifeTime::SUBGRAPH;
            };
            if (std::any_of(operation.inputs.begin(), operation.inputs.end(), isSubgraph)) {
                someCompliantOperationHasASubgraphOperand = true;
                break;
            }
        }
    }

    // TODO(b/175418767): Currently, MetaModel is not equipped to slice referenced subgraphs. If the
    // original model is not compliant with the specified version and contains referenced subgraphs
    // needed by the slice, return an invalidated slice.
    if (someCompliantOperationHasASubgraphOperand) {
        slice.mState = SliceState::INVALID;
        return slice;
    }

    // Map from an operand index in origOperands to the corresponding operand index in
    // slicedOperands
    std::map<uint32_t, uint32_t> origOperandIndexToSlicedIndex;

    // Collect the operand indexes of every operand that is an input to a
    // compliant operation.  If the operand is a CONSTANT_*, POINTER, or a
    // NO_VALUE, copy it to the sliced model and update
    // origOperandIndexToSlicedIndex accordingly.  Otherwise, we'll deal with
    // the operand in the subsequent "Main loop", where we process operation
    // outputs (intermediates and model outputs).
    std::set<uint32_t> inputOperandIndexesOfCompliantOperations;
    for (uint32_t origOperationIndex = 0; origOperationIndex < origOperations.size();
         ++origOperationIndex) {
        if (noncompliantOperations.count(origOperationIndex)) {
            continue;
        }
        for (uint32_t input : origOperations[origOperationIndex].inputs) {
            if (inputOperandIndexesOfCompliantOperations.insert(input).second) {
                const Operand& origOperand = origOperands[input];
                switch (origOperand.lifetime) {
                    case Operand::LifeTime::CONSTANT_COPY:
                    case Operand::LifeTime::CONSTANT_REFERENCE:
                    case Operand::LifeTime::POINTER:
                    case Operand::LifeTime::NO_VALUE: {
                        const uint32_t slicedOperandIndex =
                                extend(&slicedOperands, origOperand).first;
                        origOperandIndexToSlicedIndex[input] = slicedOperandIndex;
                        VLOG(COMPILATION) << "origOperandIndexToSlicedIndex initialization created "
                                          << input << " -> " << slicedOperandIndex << ": "
                                          << slicedOperands[slicedOperandIndex];
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    const auto [operandValuesSize, poolSizes] = getMemorySizes(mModel);

    OrigOperandToSlicedInputOperandIndex origOperandToSlicedInputOperandIndex(
            &slicedOperands, &slice.mModel.main.inputIndexes, version, operandValuesSize,
            poolSizes);

    // An input of the original model is an input of the sliced model if and
    // only if it is consumed by at least one compliant operation.  Note that in
    // the sliced model we share all model inputs of the same "type"; and that
    // we may later add model inputs to the sliced model.
    for (uint32_t origInputIndex : mModel.main.inputIndexes) {
        if (inputOperandIndexesOfCompliantOperations.count(origInputIndex)) {
            const uint32_t slicedIndex =
                    origOperandToSlicedInputOperandIndex.getIndex(origOperands[origInputIndex]);
            origOperandIndexToSlicedIndex[origInputIndex] = slicedIndex;
            VLOG(COMPILATION) << "origOperandIndexToSlicedIndex inputIndexes processing created "
                              << origInputIndex << " -> " << slicedIndex << ": "
                              << slicedOperands[slicedIndex];
        }
    }

    // Main loop: Process each operation of the original model.
    processOperations(&slice, &origOperandIndexToSlicedIndex, &origOperandToSlicedInputOperandIndex,
                      noncompliantOperations, inputOperandIndexesOfCompliantOperations);

    // To keep things simple, we copy over these fields as-is.  We could instead
    // opt to regenerate them based on the operands present in the sliced model:
    // This would be more complex and probably take more computation time, but
    // it would reduce the size of the sliced model, and hence the time spent
    // copying it around and potentially passing it across process boundaries.
    slice.mModel.operandValues = mModel.operandValues;
    slice.mModel.pools = mModel.pools;

    if (VLOG_IS_ON(COMPILATION)) {
        {
            std::ostringstream fromName;
            fromName << "Slice: From canonical";
            graphDump(fromName.str().c_str(), mModel);
        }
        {
            std::ostringstream toName;
            toName << "Slice: To " << version;
            graphDump(toName.str().c_str(), slice.mModel);
        }
    }

    slice.mState = invalid(slice.mModel, version, mStrictSlicing) ? SliceState::INVALID
                                                                  : SliceState::NORMAL;

    return slice;
}

}  // namespace android::nn
