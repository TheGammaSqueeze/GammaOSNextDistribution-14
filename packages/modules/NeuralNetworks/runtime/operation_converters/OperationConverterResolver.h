/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_OPERATION_CONVERTER_RESOLVER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_OPERATION_CONVERTER_RESOLVER_H

#include "OperationConverter.h"
#include "SubGraphContext.h"

namespace android {
namespace nn {

// OperationConverterResolver is used to register all operation converters that implement
// IOperationConverter. This retrieves the correct converter to use based on OperationType
class OperationConverterResolver {
   public:
    static const OperationConverterResolver* get() {
        static OperationConverterResolver instance;
        return &instance;
    }
    const IOperationConverter* findOperationConverter(OperationType operationType) const;

   private:
    OperationConverterResolver();

    void registerOperationConverter(const IOperationConverter* operationConverter,
                                    OperationType operationType);

    const IOperationConverter* mConverters[kNumberOfOperationTypes] = {};
};

// Use to register operation converter into OperationConverterResolver
#define NN_REGISTER_OPERATION_CONVERTER(identifier, OperationConverterClass) \
    const IOperationConverter* registerConverter_##identifier() {            \
        static OperationConverterClass converter;                            \
        return &converter;                                                   \
    }

// Use to indicate which operations are not supported
#define NN_OPERATION_CONVERTER_NOT_IMPLEMENTED(identifier)        \
    const IOperationConverter* registerConverter_##identifier() { \
        return nullptr;                                           \
    }

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_OPERATION_CONVERTERS_OPERATION_CONVERTER_RESOLVER_H