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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_VALIDATION_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_VALIDATION_H

#include <functional>
#include <memory>
#include <set>
#include <tuple>
#include <vector>

#include "nnapi/Result.h"
#include "nnapi/Types.h"

namespace android::nn {

// Utility functions

// Takes two minimum versions needed for two features and returns a the minimum version that must be
// supported in order to support both features.
Version combineVersions(Version minVersionNeeded1, Version minVersionNeeded2);

// Indicates whether a feature at version `minVersionNeeded` is supported on version
// `maxVersionSupported`. For example:
// * a feature at kVersionFeatureLevel2 is supported on a driver at kVersionFeatureLevel3.
// * a feature at kVersionFeatureLevel3 is supported on a driver at kVersionFeatureLevel3.
// * a feature at kVersionFeatureLevel5 is not supported on a driver at kVersionFeatureLevel3.
// * a feature that is runtime only (i.e., invalid with respect to the HAL specification) is not
//   supported on a driver that does not support runtime-only features.
bool isCompliantVersion(Version minVersionNeeded, Version maxVersionSupported);

Result<Version> validate(const DeviceStatus& deviceStatus);
Result<Version> validate(const ExecutionPreference& executionPreference);
Result<Version> validate(const DeviceType& deviceType);
Result<Version> validate(const MeasureTiming& measureTiming);
Result<Version> validate(const OperandType& operandType);
Result<Version> validate(const Priority& priority);
Result<Version> validate(const ErrorStatus& errorStatus);
Result<Version> validate(const FusedActivationFunc& activation);
Result<Version> validate(const OutputShape& outputShape);
Result<Version> validate(const Timing& timing);
Result<Version> validate(const Capabilities& capabilities);
Result<Version> validate(const Extension& extension);
Result<Version> validate(const SharedHandle& handle);
Result<Version> validate(const SharedMemory& memory);
Result<Version> validate(const Model& model);
Result<Version> validate(const BufferDesc& bufferDesc);
Result<Version> validate(const BufferRole& bufferRole);
Result<Version> validate(const Request& request);
Result<Version> validate(const OptionalTimePoint& optionalTimePoint);
Result<Version> validate(const OptionalDuration& optionalTimeoutDuration);
Result<Version> validate(const CacheToken& cacheToken);
Result<Version> validate(const SyncFence& syncFence);
Result<Version> validate(const TokenValuePair& tokenValuePair);

Result<Version> validate(const std::vector<OutputShape>& outputShapes);
Result<Version> validate(const std::vector<Extension>& extensions);
Result<Version> validate(const std::vector<SharedHandle>& handles);
Result<Version> validate(const std::vector<BufferRole>& bufferRoles);
Result<Version> validate(const std::vector<SyncFence>& syncFences);
Result<Version> validate(const std::vector<TokenValuePair>& metaData);
Result<Version> validate(const std::vector<ExtensionNameAndPrefix>& extensionNamesAndPrefixes);

// Validate request applied to model.
// This function assumes that `model` has already been validated, and the returned Version does not
// account for the version the model.
Result<Version> validateRequestForModel(const Request& request, const Model& model,
                                        bool allowUnspecifiedOutput = true);

// Validate memory descriptor.
enum class IOType { INPUT, OUTPUT };
using PreparedModelRole = std::tuple<const IPreparedModel*, IOType, uint32_t>;

// Verifies that the input arguments to IDevice::allocate are valid.
// Optionally, this function can return a flattened prepared model roles and a combined operand.
// Pass nullptr if either value is not needed.
// IMPORTANT: This function cannot validate dimensions and extraParams with extension operand type.
// Each driver should do their own validation of extension type dimensions and extraParams.
Result<Version> validateMemoryDesc(
        const BufferDesc& desc, const std::vector<SharedPreparedModel>& preparedModels,
        const std::vector<BufferRole>& inputRoles, const std::vector<BufferRole>& outputRoles,
        const std::function<const Model*(const SharedPreparedModel&)>& getModel,
        std::set<PreparedModelRole>* preparedModelRoles, Operand* combinedOperand);

Result<void> validateOperandSymmPerChannelQuantParams(
        const Operand& operand, const Operand::SymmPerChannelQuantParams& channelQuant,
        const char* tag);

// Validates an operand type.
//
// extensionOperandTypeInfo must be nullptr iff the type is not an extension type.
//
// If allowPartial is true, the dimensions may be underspecified.
Result<void> validateOperandType(const Operand& type,
                                 const Extension::OperandTypeInformation* extensionOperandTypeInfo,
                                 const char* tag, bool allowPartial);
Result<void> validateOperandList(const std::vector<uint32_t>& list, size_t operandCount,
                                 const char* tag);

// Validates the operation, and ensures it uses subgraphs in a valid way, but does not validate any
// subgraphs or operands themselves.
//
// This function is currently used by ModelBuilder.
Result<void> validateOperationButNotOperands(const Operation& operation,
                                             const std::vector<Operand>& operands,
                                             const std::vector<Model::Subgraph>& subgraphs);

// Forward declaration for a utility class for caching a referenced subgraph's version.
struct SubgraphVersionCache;

// Function to create an opaque handle to a utility class for caching a referenced subgraph's
// version.
std::unique_ptr<SubgraphVersionCache, void (*)(SubgraphVersionCache*)> createSubgraphVersionCache(
        size_t subgraphCount);

// Validate the operation or operand, also validating any subgraphs and operands it may use,
// recursively.
//
// `subgraphVersionCache` is used to cache validation information for `subgraphs`, which would
// otherwise be unnecessarily re-validated. For this reason, `subgraphVersionCache` must be non-null
// and must have been created with the number of referenced subgraphs in `subgraphs`. The provided
// subgraphs must not form a reference cycle.
//
// These functions are currently used by MetaModel.
Result<Version> validateOperationAndAnythingItDependsOn(
        const Operation& operation, const std::vector<Operand>& operands, size_t operandValuesSize,
        const std::vector<size_t>& poolSizes, const std::vector<Model::Subgraph>& subgraphs,
        SubgraphVersionCache* subgraphVersionCache);
Result<Version> validateOperandAndAnythingItDependsOn(const Operand& operand,
                                                      size_t operandValuesSize,
                                                      const std::vector<size_t>& poolSizes,
                                                      const std::vector<Model::Subgraph>& subgraphs,
                                                      SubgraphVersionCache* subgraphVersionCache);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_VALIDATION_H
