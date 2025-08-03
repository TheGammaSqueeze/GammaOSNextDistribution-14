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

#define LOG_TAG "ServerFlag"

#include "ServerFlag.h"

#include <android-base/logging.h>
#include <android-base/parsebool.h>
#include <android-base/parseint.h>
#include <nnapi/Types.h>
#include <stdint.h>

#include <string>

#if !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)
#include <server_configurable_flags/get_flags.h>
#endif  // !defined(NN_COMPATIBILITY_LIBRARY_BUILD) && !defined(NN_EXPERIMENTAL_FEATURE)

namespace android::nn {

#ifndef NN_COMPATIBILITY_LIBRARY_BUILD
#ifndef NN_EXPERIMENTAL_FEATURE
int64_t getServerFeatureLevelFlag() {
    return getServerFeatureLevelFlag(server_configurable_flags::GetServerConfigurableFlag);
}

bool getServerTelemetryEnableFlag() {
    return getServerTelemetryEnableFlag(server_configurable_flags::GetServerConfigurableFlag);
}
#endif  // NN_EXPERIMENTAL_FEATURE

int64_t getServerFeatureLevelFlag(GetServerConfigurableFlagFunc serverFunc) {
    const std::string featureLevelString =
            serverFunc(kExprCategoryName, kCurrentFeatureLevelFlagName,
                       std::to_string(kDefaultFeatureLevelNum));

    int64_t featureLevel = kDefaultFeatureLevelNum;
    const bool success = base::ParseInt(featureLevelString, &featureLevel, kMinFeatureLevelNum,
                                        kMaxFeatureLevelNum);
    if (!success) {
        LOG(WARNING) << "Failed to parse result of GetServerConfigurableFlag, errno=" << errno;
    }
    return featureLevel;
}

bool getServerTelemetryEnableFlag(GetServerConfigurableFlagFunc serverFunc) {
    const std::string telemetryEnabledString =
            serverFunc(kExprCategoryName, kTelemetryEnableFlagName,
                       std::to_string(kDefaultTelemetryEnableValue));

    const auto parseBoolResult = base::ParseBool(telemetryEnabledString);
    switch (parseBoolResult) {
        case base::ParseBoolResult::kError:
            LOG(WARNING) << "Failed to parse result of GetServerConfigurableFlag";
            return kDefaultTelemetryEnableValue;
        case base::ParseBoolResult::kFalse:
            return false;
        case base::ParseBoolResult::kTrue:
            return true;
    }
    LOG(WARNING) << "Unrecognized return from base::ParseBool: "
                 << static_cast<int32_t>(parseBoolResult);
    return kDefaultTelemetryEnableValue;
}

#endif  // NN_COMPATIBILITY_LIBRARY_BUILD

Version serverFeatureLevelToVersion(int64_t serverFeatureLevel) {
    Version version;
    switch (serverFeatureLevel) {
        case 5:
            return kVersionFeatureLevel5;
        case 6:
            return kVersionFeatureLevel6;
        case 7:
            return kVersionFeatureLevel7;
        case 8:
            return kVersionFeatureLevel8;
        default:
            LOG(FATAL) << "Invalid feature level flag value " << serverFeatureLevel;
            return {};
    }
}

}  // namespace android::nn
