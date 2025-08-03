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

#pragma once

#include <aidl/android/hardware/neuralnetworks/BnBuffer.h>
#include <aidl/android/hardware/neuralnetworks/BnDevice.h>

#include <memory>
#include <stack>
#include <string>
#include <utility>
#include <vector>

#include "NeuralNetworksShim.h"
#include "ShimBufferTracker.h"
#include "SupportLibrary.h"
#include "SupportLibraryWrapper.h"

#include <android-base/macros.h>
#include <android-base/thread_annotations.h>

namespace aidl::android::hardware::neuralnetworks {

class ShimDevice : public BnDevice {
   public:
    ShimDevice(std::shared_ptr<const NnApiSupportLibrary>, ANeuralNetworksDevice*,
               std::string serviceName);
    ::ndk::ScopedAStatus allocate(const BufferDesc& desc,
                                  const std::vector<IPreparedModelParcel>& preparedModels,
                                  const std::vector<BufferRole>& inputRoles,
                                  const std::vector<BufferRole>& outputRoles,
                                  DeviceBuffer* deviceBuffer) override;
    ::ndk::ScopedAStatus getCapabilities(Capabilities* capabilities) override;
    ::ndk::ScopedAStatus getNumberOfCacheFilesNeeded(
            NumberOfCacheFiles* numberOfCacheFiles) override;
    ::ndk::ScopedAStatus getSupportedExtensions(std::vector<Extension>* extensions) override;
    ::ndk::ScopedAStatus getSupportedOperations(const Model& model,
                                                std::vector<bool>* supportedOperations) override;
    ::ndk::ScopedAStatus getType(DeviceType* deviceType) override;
    ::ndk::ScopedAStatus getVersionString(std::string* versionString) override;
    ::ndk::ScopedAStatus prepareModel(
            const Model& model, ExecutionPreference preference, Priority priority,
            int64_t deadlineNs, const std::vector<::ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<::ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token,
            const std::shared_ptr<IPreparedModelCallback>& callback) override;
    ::ndk::ScopedAStatus prepareModelWithConfig(
            const Model& model, const PrepareModelConfig& config,
            const std::shared_ptr<IPreparedModelCallback>& callback) override;
    ::ndk::ScopedAStatus prepareModelFromCache(
            int64_t deadlineNs, const std::vector<::ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<::ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token,
            const std::shared_ptr<IPreparedModelCallback>& callback) override;

   private:
    ndk::ScopedAStatus prepareModelCommon(
            const Model& model, ExecutionPreference preference, Priority priority,
            int64_t deadlineNs, const std::vector<::ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<::ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token, const std::vector<TokenValuePair>& compilationHints,
            const std::vector<ExtensionNameAndPrefix>& extensionNameToPrefix,
            const std::shared_ptr<IPreparedModelCallback>& callback);

    std::shared_ptr<const NnApiSupportLibrary> mNnapi;
    std::shared_ptr<ShimBufferTracker> mBufferTracker;
    std::string mServiceName;
    ANeuralNetworksDevice* mDevice;
    Capabilities mCapabilities;
    NumberOfCacheFiles mNumberOfCacheFiles;
    std::vector<Extension> mExtensions;
};

}  // namespace aidl::android::hardware::neuralnetworks
