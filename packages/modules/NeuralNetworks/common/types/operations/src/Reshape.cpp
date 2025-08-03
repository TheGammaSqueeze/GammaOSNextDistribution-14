/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "Reshape.h"

#include <vector>

#include "OperationsValidationUtils.h"
#include "nnapi/Validation.h"

namespace android::nn {
namespace reshape {

Result<Version> validateDepthToSpace(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 3 || context->getNumInputs() == 2) &&
                 context->getNumOutputs() == 1)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 3 or 2) or output operands (" << context->getNumOutputs()
            << ", expected 1) for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    if (context->getNumInputs() == 3) {
        inExpectedTypes.push_back(OperandType::BOOL);
        version = combineVersions(version, kVersionFeatureLevel3);
    } else {
        version = combineVersions(version, kVersionFeatureLevel1);
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validateSpaceToDepth(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 3 || context->getNumInputs() == 2) &&
                 context->getNumOutputs() == 1)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 3 or 2) or output operands (" << context->getNumOutputs()
            << ", expected 1) for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED, OperandType::INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    if (context->getNumInputs() == 3) {
        inExpectedTypes.push_back(OperandType::BOOL);
        version = combineVersions(version, kVersionFeatureLevel3);
    } else {
        version = combineVersions(version, kVersionFeatureLevel1);
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validatePad(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 2 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(2, 1);
    auto inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel2;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            version = kVersionFeatureLevel4;
        } else {
            if (context->getInputShape(0).offset == 0) {
                version = kVersionFeatureLevel2;
            } else {
                version = kVersionFeatureLevel3;
            }
        }
        inExpectedTypes = {
                inputType,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {inputType};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    const auto inputRank = context->getInputShape(0).dimensions.size();
    NN_RET_CHECK_LE(inputRank, 4u)
            << "Unsupported input tensor rank for operation " << context->getOperationName();
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validatePadV2(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 3 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(3, 1);
    auto inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_INT32,
                OperandType::FLOAT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_INT32,
                OperandType::FLOAT16,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM ||
               inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
            version = kVersionFeatureLevel4;
        } else {
            version = kVersionFeatureLevel3;
        }
        inExpectedTypes = {
                inputType,
                OperandType::TENSOR_INT32,
                OperandType::INT32,
        };  // TODO(b/116699425): Make it UINT8.
        outExpectedTypes = {inputType};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    const auto inputRank = context->getInputShape(0).dimensions.size();
    NN_RET_CHECK_LE(inputRank, 4u)
            << "Unsupported input tensor rank for operation " << context->getOperationName();
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validateBatchToSpaceND(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 3 || context->getNumInputs() == 2) &&
                 context->getNumOutputs() == 1)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 3 or 2) or output operands (" << context->getNumOutputs()
            << ", expected 1) for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    Version version = kVersionFeatureLevel1;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    if (context->getNumInputs() == 3) {
        inExpectedTypes.push_back(OperandType::BOOL);
        version = combineVersions(version, kVersionFeatureLevel3);
    } else {
        version = combineVersions(version, kVersionFeatureLevel2);
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validateSpaceToBatchND(const IOperationValidationContext* context) {
    NN_RET_CHECK((context->getNumInputs() == 4 || context->getNumInputs() == 3) &&
                 context->getNumOutputs() == 1)
            << "Invalid number of input operands (" << context->getNumInputs()
            << ", expected 4 or 3) or output operands (" << context->getNumOutputs()
            << ", expected 1) for operation " << context->getOperationName();
    auto inputType = context->getInputType(0);
    Version version = kVersionFeatureLevel1;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT32,
                OperandType::TENSOR_INT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {
                OperandType::TENSOR_FLOAT16,
                OperandType::TENSOR_INT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        if (context->getInputShape(0).offset != 0) {
            version = kVersionFeatureLevel3;
        }
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM,
                OperandType::TENSOR_INT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {
                OperandType::TENSOR_QUANT8_ASYMM_SIGNED,
                OperandType::TENSOR_INT32,
                OperandType::TENSOR_INT32,
        };
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    if (context->getNumInputs() == 4) {
        inExpectedTypes.push_back(OperandType::BOOL);
        version = combineVersions(version, kVersionFeatureLevel3);
    } else {
        version = combineVersions(version, kVersionFeatureLevel2);
    }
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

Result<Version> validateReshape(const IOperationValidationContext* context) {
    NN_RET_CHECK(context->getNumInputs() == 2 && context->getNumOutputs() == 1)
            << context->invalidInOutNumberMessage(2, 1);
    auto inputType = context->getInputType(0);
    Version version;
    std::vector<OperandType> inExpectedTypes;
    std::vector<OperandType> outExpectedTypes;
    if (inputType == OperandType::TENSOR_FLOAT32) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_FLOAT32, OperandType::TENSOR_INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT32};
    } else if (inputType == OperandType::TENSOR_FLOAT16) {
        version = kVersionFeatureLevel3;
        inExpectedTypes = {OperandType::TENSOR_FLOAT16, OperandType::TENSOR_INT32};
        outExpectedTypes = {OperandType::TENSOR_FLOAT16};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM) {
        version = kVersionFeatureLevel1;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM, OperandType::TENSOR_INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM};
    } else if (inputType == OperandType::TENSOR_QUANT8_ASYMM_SIGNED) {
        version = kVersionFeatureLevel4;
        inExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED, OperandType::TENSOR_INT32};
        outExpectedTypes = {OperandType::TENSOR_QUANT8_ASYMM_SIGNED};
    } else if (inputType == OperandType::TENSOR_INT32) {
        version = kVersionFeatureLevel6;
        inExpectedTypes = {OperandType::TENSOR_INT32, OperandType::TENSOR_INT32};
        outExpectedTypes = {OperandType::TENSOR_INT32};
    } else {
        NN_RET_CHECK_FAIL() << "Unsupported input tensor type for operation "
                            << context->getOperationName();
    }
    const auto inputRank = context->getInputShape(0).dimensions.size();
    NN_RET_CHECK_LE(inputRank, 4u)
            << "Unsupported input tensor rank for operation " << context->getOperationName();
    NN_TRY(context->validateOperationOperandTypes(inExpectedTypes, outExpectedTypes));
    return version;
}

}  // namespace reshape

NN_DEFINE_VALIDATION_FUNCTION(DEPTH_TO_SPACE, reshape::validateDepthToSpace);
NN_DEFINE_VALIDATION_FUNCTION(RESHAPE, reshape::validateReshape);
NN_DEFINE_VALIDATION_FUNCTION(SPACE_TO_DEPTH, reshape::validateSpaceToDepth);
NN_DEFINE_VALIDATION_FUNCTION(BATCH_TO_SPACE_ND, reshape::validateBatchToSpaceND);
NN_DEFINE_VALIDATION_FUNCTION(PAD, reshape::validatePad);
NN_DEFINE_VALIDATION_FUNCTION(SPACE_TO_BATCH_ND, reshape::validateSpaceToBatchND);
NN_DEFINE_VALIDATION_FUNCTION(PAD_V2, reshape::validatePadV2);

}  // namespace android::nn
