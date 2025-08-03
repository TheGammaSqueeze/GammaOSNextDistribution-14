%% template file for generating OperandTypes.h.
%% see README.md.
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERAND_TYPES_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERAND_TYPES_H

namespace android::nn {

%%
%% The function `getAlignmentForLength` assumes that required (or optimal)
%% alignment is a function of length (not data type), and assumes that there
%% is a maximum alignment requirement. If a new operand type is added with a
%% stricter alignment requirement, then `getAlignmentForLength` may need to
%% be modified.
%%
%insert Operand_1.0_Comment
enum class OperandType {
%insert Operand_1.0

%insert Operand_1.2

%insert Operand_1.3

    /**
     * DEPRECATED. Since HAL version 1.2, extensions are the preferred
     * alternative to OEM operation and data types.
     *
     * OEM specific scalar value.
     */
    OEM = 10000,

    /**
     * DEPRECATED. Since HAL version 1.2, extensions are the preferred
     * alternative to OEM operation and data types.
     *
     * A tensor of OEM specific values.
     */
    TENSOR_OEM_BYTE = 10001,
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_OPERAND_TYPES_H
