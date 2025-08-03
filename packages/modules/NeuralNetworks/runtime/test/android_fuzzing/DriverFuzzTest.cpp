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

#include <CanonicalDevice.h>
#include <TestHarness.h>
#include <android-base/logging.h>
#include <nnapi/IDevice.h>
#include <nnapi/IPreparedModel.h>
#include <nnapi/Result.h>
#include <nnapi/TestUtils.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>

#include <memory>

namespace android::nn {
namespace {

SharedDevice getDevice() {
    /**
     * TODO: INSERT CUSTOM DEVICE HERE
     *
     * This code can test a canonical IDevice directly, a HIDL IDevice by using the corresponding
     * wrapper in neuralnetworks_utils_hal_1_*, or an AIDL IDevice by using the corresponding
     * wrapper in neuralnetworks_utils_hal_aidl. E.g.:
     *   HIDL 1.0 -- ::android::hardware::neuralnetworks::V1_0::utils::Device::create
     *   HIDL 1.1 -- ::android::hardware::neuralnetworks::V1_1::utils::Device::create
     *   HIDL 1.2 -- ::android::hardware::neuralnetworks::V1_2::utils::Device::create
     *   HIDL 1.3 -- ::android::hardware::neuralnetworks::V1_3::utils::Device::create
     *   AIDL     -- ::aidl::android::hardware::neuralnetworks::utils::Device::create
     */
    static const SharedDevice device = std::make_shared<const sample::Device>("example-driver");
    return device;
}

ExecutionResult<void> runTest(const ::test_helper::TestModel& testModel) {
    // Set up device.
    const auto device = getDevice();
    CHECK(device != nullptr);

    // Set up model.
    const auto model = NN_TRY(test::createModel(testModel));

    // Attempt to prepare the model.
    const auto preparedModel = NN_TRY(device->prepareModel(
            model, ExecutionPreference::DEFAULT, Priority::DEFAULT,
            /*deadline=*/{}, /*modelCache=*/{},
            /*dataCache=*/{}, /*token=*/{}, /*hints=*/{}, /*extensionPrefix*/ {}));
    CHECK(preparedModel != nullptr);

    // Set up request.
    const auto request = NN_TRY(test::createRequest(testModel));

    // Perform execution.
    NN_TRY(preparedModel->execute(request, MeasureTiming::YES, /*deadline=*/{},
                                  /*loopTimeoutDuration=*/{}, /*hints=*/{},
                                  /*extensionPrefix*/ {}));

    return {};
}

}  // namespace
}  // namespace android::nn

void nnapiFuzzTest(const ::test_helper::TestModel& testModel) {
    android::nn::runTest(testModel);
}
