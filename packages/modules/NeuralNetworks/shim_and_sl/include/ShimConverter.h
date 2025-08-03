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

#include <memory>
#include <utility>
#include <vector>

#include <aidl/android/hardware/neuralnetworks/ErrorStatus.h>
#include <aidl/android/hardware/neuralnetworks/Memory.h>
#include <aidl/android/hardware/neuralnetworks/Model.h>

#include "SupportLibrary.h"
#include "SupportLibraryWrapper.h"

namespace aidl::android::hardware::neuralnetworks {

struct ShimConvertedModel {
    std::vector<std::unique_ptr<::android::nn::sl_wrapper::Memory>> memory;
    std::vector<::android::nn::sl_wrapper::Model> models;
};

bool isValid(const neuralnetworks::Model& model);

/**
 * Convert HAL model into Model ready to be consumed by SL Driver.
 8
 * @param nnapi NNAPI SL Driver implementation
 * @param model HAL NNAPI Model
 * @param copiedOperandValues If model requires it (contains CONSTANT_COPY operands larger
 *                            then 128 bytes), this vector will be used to hold a copy of
 *                            model operand values. Must be non-null.
 * @param errorStatus Output error status in case of failure.
 * @return ShimConvertedModel with all converted memories and models.
 *
 */
std::optional<ShimConvertedModel> convertFromHAL(const NnApiSupportLibrary* nnapi,
                                                 const neuralnetworks::Model& model,
                                                 std::vector<uint8_t>* copiedOperandValues,
                                                 ErrorStatus* errorStatus);
std::unique_ptr<::android::nn::sl_wrapper::Memory> convertFromHAL(
        const NnApiSupportLibrary* nnapi, const neuralnetworks::Memory& pool);

}  // namespace aidl::android::hardware::neuralnetworks
