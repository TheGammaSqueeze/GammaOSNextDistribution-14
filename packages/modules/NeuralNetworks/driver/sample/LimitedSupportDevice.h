/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_LIMITED_SUPPORT_DEVICE_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_LIMITED_SUPPORT_DEVICE_H

#include <nnapi/IBuffer.h>
#include <nnapi/IDevice.h>
#include <nnapi/OperandTypes.h>
#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace android::nn::sample {

// Class that adapts an IDevice object to allow the caller to:
// (1) provide custom Capabilities
// (2) customize the behavior of getSupportedOperations
class LimitedSupportDevice final : public IDevice {
   public:
    using SupportedOperationsFunction =
            std::function<GeneralResult<std::vector<bool>>(const Model&)>;

    // Precondition: device != nullptr
    // Precondition: validate(capabilities).ok()
    // Precondition: supportedOperationsFunction != nullptr
    LimitedSupportDevice(SharedDevice device, Capabilities capabilities,
                         SupportedOperationsFunction supportedOperationsFunction);

    const std::string& getName() const override;
    const std::string& getVersionString() const override;
    Version getFeatureLevel() const override;
    DeviceType getType() const override;
    const std::vector<Extension>& getSupportedExtensions() const override;
    const Capabilities& getCapabilities() const override;
    std::pair<uint32_t, uint32_t> getNumberOfCacheFilesNeeded() const override;

    GeneralResult<void> wait() const override;

    GeneralResult<std::vector<bool>> getSupportedOperations(const Model& model) const override;

    GeneralResult<SharedPreparedModel> prepareModel(
            const Model& model, ExecutionPreference preference, Priority priority,
            OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
            const std::vector<SharedHandle>& dataCache, const CacheToken& token,
            const std::vector<TokenValuePair>& hints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix) const override;

    GeneralResult<SharedPreparedModel> prepareModelFromCache(
            OptionalTimePoint deadline, const std::vector<SharedHandle>& modelCache,
            const std::vector<SharedHandle>& dataCache, const CacheToken& token) const override;

    GeneralResult<SharedBuffer> allocate(const BufferDesc& desc,
                                         const std::vector<SharedPreparedModel>& preparedModels,
                                         const std::vector<BufferRole>& inputRoles,
                                         const std::vector<BufferRole>& outputRoles) const override;

   private:
    const nn::SharedDevice kDevice;
    const Capabilities kCapabilities;
    const SupportedOperationsFunction kSupportedOperationsFunction;
};

// Get a set of SharedDevices that have different performance characteristics and supported
// operations. Currently returns four different variants:
// * nnapi-sample_float_fast
// * nnapi-sample_float_slow
// * nnapi-sample_minimal
// * nnapi-sample_quant
std::vector<SharedDevice> getExampleLimitedDevices();

}  // namespace android::nn::sample

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_LIMITED_SUPPORT_DEVICE_H
