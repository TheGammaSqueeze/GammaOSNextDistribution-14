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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_PARTIAL_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_PARTIAL_H

#include <HalInterfaces.h>
#include <Utils.h>
#include <android-base/logging.h>

#include <thread>
#include <vector>

#include "SampleDriver.h"

namespace android {
namespace nn {
namespace sample_driver {

// A base class for sample drivers that support only a subset of NNAPI
// operations. Classes of such drivers should inherit from this class and
// implement getSupportedOperationsImpl function which is used for filtering out
// unsupported ops.
class SampleDriverPartial : public SampleDriver {
   public:
    SampleDriverPartial(const char* name, const IOperationResolver* operationResolver =
                                                  BuiltinOperationResolver::get())
        : SampleDriver(name, operationResolver) {}
    hardware::Return<void> getSupportedOperations_1_3(const V1_3::Model& model,
                                                      getSupportedOperations_1_3_cb cb) override;
    hardware::Return<V1_3::ErrorStatus> prepareModel_1_3(
            const V1_3::Model& model, V1_1::ExecutionPreference preference, V1_3::Priority priority,
            const V1_3::OptionalTimePoint& deadline,
            const hardware::hidl_vec<hardware::hidl_handle>& modelCache,
            const hardware::hidl_vec<hardware::hidl_handle>& dataCache, const HalCacheToken& token,
            const sp<V1_3::IPreparedModelCallback>& callback) override;

   protected:
    // Given a valid NNAPI Model returns a boolean vector that indicates which
    // ops in the model are supported by a driver.
    virtual std::vector<bool> getSupportedOperationsImpl(const V1_3::Model& model) const = 0;
};

}  // namespace sample_driver
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_DRIVER_SAMPLE_SAMPLE_DRIVER_PARTIAL_H
