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

#define LOG_TAG "Operations"

#include "Rank.h"

#include "LegacyUtils.h"
#include "OperationResolver.h"
#include "OperationsExecutionUtils.h"

namespace android {
namespace nn {
namespace rank_op {

bool prepare(IOperationExecutionContext* context) {
    Shape output = context->getOutputShape(kOutputScalar);
    return context->setOutputShape(kOutputScalar, output);
}

bool execute(IOperationExecutionContext* context) {
    *context->getOutputBuffer<int32_t>(kOutputScalar) =
            getNumberOfDimensions(context->getInputShape(kInputTensor));
    return true;
}

}  // namespace rank_op

NN_REGISTER_OPERATION_DEFAULT_VALIDATION(RANK, rank_op::prepare, rank_op::execute);

}  // namespace nn
}  // namespace android
