/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdio.h>

#include "codegen.h"

namespace android {
namespace express {

class CodeGeneratorJava : public CodeGenerator {
public:
    CodeGeneratorJava(std::string filePath, std::string packageName, std::string className)
        : CodeGenerator(std::move(filePath)),
          mPackageName(std::move(packageName)),
          mClassName(std::move(className)) {
    }

protected:
    bool generateCodeImpl(FILE* fd, const MetricInfoMap& metricsIds) const override;

private:
    const std::string mPackageName;
    const std::string mClassName;
};

}  // namespace express
}  // namespace android
