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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_PARTIAL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_PARTIAL_H

#include <android-base/logging.h>

#include <memory>
#include <thread>
#include <vector>

#include "SampleDriverAidl.h"

namespace android {
namespace nn {
namespace sample_driver_aidl {

// A base class for sample drivers that support only a subset of NNAPI
// operations. Classes of such drivers should inherit from this class and
// implement getSupportedOperationsImpl function which is used for filtering out
// unsupported ops.
class SampleDriverPartial : public SampleDriver {
   public:
    SampleDriverPartial(const char* name, const IOperationResolver* operationResolver =
                                                  BuiltinOperationResolver::get())
        : SampleDriver(name, operationResolver) {}
    ndk::ScopedAStatus getSupportedOperations(const aidl_hal::Model& model,
                                              std::vector<bool>* supportedOperations) override;
    ndk::ScopedAStatus prepareModel(
            const aidl_hal::Model& model, aidl_hal::ExecutionPreference preference,
            aidl_hal::Priority priority, int64_t deadline,
            const std::vector<ndk::ScopedFileDescriptor>& modelCache,
            const std::vector<ndk::ScopedFileDescriptor>& dataCache,
            const std::vector<uint8_t>& token,
            const std::shared_ptr<aidl_hal::IPreparedModelCallback>& callback) override;

   protected:
    // Given a valid NNAPI Model returns a boolean vector that indicates which
    // ops in the model are supported by a driver.
    virtual std::vector<bool> getSupportedOperationsImpl(const Model& model) const = 0;
};

}  // namespace sample_driver_aidl
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_AIDL_SAMPLE_DRIVER_AIDL_PARTIAL_H
