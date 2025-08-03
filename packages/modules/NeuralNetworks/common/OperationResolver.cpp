/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "OperationResolver"

#include "OperationResolver.h"

#include "NeuralNetworks.h"

namespace android {
namespace nn {

#define NN_FORWARD_DECLARE_OPERATION_REGISTRATION_FUNCTION(opType) \
    const OperationRegistration* register_##opType();

NN_FOR_EACH_OPERATION(NN_FORWARD_DECLARE_OPERATION_REGISTRATION_FUNCTION)

#undef NN_FORWARD_DECLARE_OPERATION_REGISTRATION_FUNCTION

NN_OPERATION_IS_NOT_IMPLEMENTED(DEPTH_TO_SPACE);
NN_OPERATION_IS_NOT_IMPLEMENTED(EMBEDDING_LOOKUP);
NN_OPERATION_IS_NOT_IMPLEMENTED(HASHTABLE_LOOKUP);
NN_OPERATION_IS_NOT_IMPLEMENTED(LSH_PROJECTION);
NN_OPERATION_IS_NOT_IMPLEMENTED(LSTM);
NN_OPERATION_IS_NOT_IMPLEMENTED(RESHAPE);
NN_OPERATION_IS_NOT_IMPLEMENTED(RNN);
NN_OPERATION_IS_NOT_IMPLEMENTED(SPACE_TO_DEPTH);
NN_OPERATION_IS_NOT_IMPLEMENTED(SVDF);
NN_OPERATION_IS_NOT_IMPLEMENTED(BATCH_TO_SPACE_ND);
NN_OPERATION_IS_NOT_IMPLEMENTED(MEAN);
NN_OPERATION_IS_NOT_IMPLEMENTED(PAD);
NN_OPERATION_IS_NOT_IMPLEMENTED(SPACE_TO_BATCH_ND);
NN_OPERATION_IS_NOT_IMPLEMENTED(ARGMAX);
NN_OPERATION_IS_NOT_IMPLEMENTED(ARGMIN);
NN_OPERATION_IS_NOT_IMPLEMENTED(BIDIRECTIONAL_SEQUENCE_LSTM);
NN_OPERATION_IS_NOT_IMPLEMENTED(CAST);
NN_OPERATION_IS_NOT_IMPLEMENTED(EXPAND_DIMS);
NN_OPERATION_IS_NOT_IMPLEMENTED(GROUPED_CONV_2D);
NN_OPERATION_IS_NOT_IMPLEMENTED(MAXIMUM);
NN_OPERATION_IS_NOT_IMPLEMENTED(MINIMUM);
NN_OPERATION_IS_NOT_IMPLEMENTED(PAD_V2);
NN_OPERATION_IS_NOT_IMPLEMENTED(POW);
NN_OPERATION_IS_NOT_IMPLEMENTED(QUANTIZED_16BIT_LSTM);
NN_OPERATION_IS_NOT_IMPLEMENTED(RANDOM_MULTINOMIAL);
NN_OPERATION_IS_NOT_IMPLEMENTED(SPLIT);
NN_OPERATION_IS_NOT_IMPLEMENTED(TILE);
NN_OPERATION_IS_NOT_IMPLEMENTED(IF);
NN_OPERATION_IS_NOT_IMPLEMENTED(WHILE);
NN_OPERATION_IS_NOT_IMPLEMENTED(OEM_OPERATION);

BuiltinOperationResolver::BuiltinOperationResolver() {
#define NN_REGISTER_OPERATION_FUNCTION(opType) registerOperation(register_##opType());

    NN_FOR_EACH_OPERATION(NN_REGISTER_OPERATION_FUNCTION)

#undef NN_REGISTER_OPERATION_FUNCTION
}

const OperationRegistration* BuiltinOperationResolver::findOperation(
        OperationType operationType) const {
    auto index = static_cast<int32_t>(operationType);
    if (index >= 0 && index < kNumberOfOperationTypes) {
        return mRegistrations[index];
    }
#ifdef NN_EXPERIMENTAL_FEATURE
    if (index >= kStartOfExperimentalOperations &&
        index < kStartOfExperimentalOperations + kNumberOfExperimentalOperationTypes) {
        return mExperimentalRegistrations[index - kStartOfExperimentalOperations];
    }
#endif  // NN_EXPERIMENTAL_FEATURE
    return nullptr;
}

void BuiltinOperationResolver::registerOperation(
        const OperationRegistration* operationRegistration) {
    // Some operations (such as IF and WHILE) are not implemented through registration. These
    // operations call registerOperation with a nullptr, which skips registration.
    if (operationRegistration == nullptr) {
        return;
    }

    auto index = static_cast<int32_t>(operationRegistration->type);

#ifdef NN_EXPERIMENTAL_FEATURE
    if (index >= kStartOfExperimentalOperations) {
        CHECK_LT(index, kStartOfExperimentalOperations + kNumberOfExperimentalOperationTypes);
        CHECK(mExperimentalRegistrations[index - kStartOfExperimentalOperations] == nullptr);
        mExperimentalRegistrations[index - kStartOfExperimentalOperations] = operationRegistration;
        return;
    }
#endif  // NN_EXPERIMENTAL_FEATURE

    CHECK_LE(0, index);
    CHECK_LT(index, kNumberOfOperationTypes);
    CHECK(mRegistrations[index] == nullptr);
    mRegistrations[index] = operationRegistration;
}

}  // namespace nn
}  // namespace android
