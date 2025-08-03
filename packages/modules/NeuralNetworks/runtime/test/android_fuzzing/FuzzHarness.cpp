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

#include <android-base/logging.h>
#include <nnapi/TypeUtils.h>
#include <src/libfuzzer/libfuzzer_macro.h>

#include <algorithm>

#include "Converter.h"
#include "Model.pb.h"
#include "TestHarness.h"

// Fuzz test logic. This function will either run to completion and return, or crash.
extern void nnapiFuzzTest(const ::test_helper::TestModel& testModel);

namespace {

using ::android::nn::getNonExtensionSize;
using ::android::nn::OperandType;
using ::android::nn::fuzz::convertToTestModel;
using ::test_helper::TestModel;
using ::test_helper::TestOperand;
using ::test_helper::TestSubgraph;

bool operandOverflows(const TestOperand& operand) {
    const auto operandType = static_cast<OperandType>(operand.type);
    return !getNonExtensionSize(operandType, operand.dimensions).has_value();
}

bool hasOperandThatOverflows(const TestSubgraph& subgraph) {
    return std::any_of(subgraph.operands.begin(), subgraph.operands.end(), operandOverflows);
}

bool shouldSkip(const TestModel& model) {
    return hasOperandThatOverflows(model.main) ||
           std::any_of(model.referenced.begin(), model.referenced.end(), hasOperandThatOverflows);
}

void limitLoggingToCrashes() {
    [[maybe_unused]] static const auto oldSeverity = ::android::base::SetMinimumLogSeverity(
            ::android::base::LogSeverity::FATAL_WITHOUT_ABORT);
}

}  // namespace

DEFINE_PROTO_FUZZER(const ::android::nn::fuzz::Test& model) {
    // Limit NNAPI fuzz test logging to crashes (which is what the test cares about) to reduce the
    // noise and potentially speed up testing.
    limitLoggingToCrashes();

    const TestModel testModel = convertToTestModel(model);
    if (!shouldSkip(testModel)) {
        nnapiFuzzTest(testModel);
    }
}
