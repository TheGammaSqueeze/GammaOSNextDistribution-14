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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_TOOLS_TEST_GENERATOR_TEST_HARNESS_TEST_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_TOOLS_TEST_GENERATOR_TEST_HARNESS_TEST_UTILS_H

#include <nnapi/Result.h>
#include <nnapi/Types.h>

#include "TestHarness.h"

namespace android::nn::test {

GeneralResult<Model> createModel(const ::test_helper::TestModel& testModel);

GeneralResult<Request> createRequest(const ::test_helper::TestModel& testModel);

}  // namespace android::nn::test

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_TOOLS_TEST_GENERATOR_TEST_HARNESS_TEST_UTILS_H
