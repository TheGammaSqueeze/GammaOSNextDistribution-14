//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "codegen.h"

#include <expresscatalog-utils.h>
#include <stdio.h>

namespace android {
namespace express {

bool CodeGenerator::generateCode(const MetricInfoMap& metricsIds) const {
    bool result = true;
    if (mFilePath.size()) {
        FILE* fdOut = fopen(mFilePath.c_str(), "we");
        if (fdOut == nullptr) {
            LOGE("Unable to open file for write %s\n", mFilePath.c_str());
            return false;
        }

        result = generateCodeImpl(fdOut, metricsIds);
        if (result) {
            LOGD("File written: %s\n", mFilePath.c_str());
        } else {
            LOGE("Failed to generate code for %s\n", mFilePath.c_str());
        }
        fclose(fdOut);
    }
    return result;
}

}  // namespace express
}  // namespace android
