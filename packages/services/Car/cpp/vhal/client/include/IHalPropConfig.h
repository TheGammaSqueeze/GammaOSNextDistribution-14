/*
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_VHAL_CLIENT_INCLUDE_IHALPROPCONFIG_H_
#define CPP_VHAL_CLIENT_INCLUDE_IHALPROPCONFIG_H_

#include <cstdint>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

class IHalAreaConfig {
public:
    virtual int32_t getAreaId() const = 0;

    virtual int32_t getAccess() const = 0;

    virtual int32_t getMinInt32Value() const = 0;

    virtual int32_t getMaxInt32Value() const = 0;

    virtual int64_t getMinInt64Value() const = 0;

    virtual int64_t getMaxInt64Value() const = 0;

    virtual float getMinFloatValue() const = 0;

    virtual float getMaxFloatValue() const = 0;

    virtual ~IHalAreaConfig() = default;

    virtual bool isVariableUpdateRateSupported() const = 0;
};

class IHalPropConfig {
public:
    virtual int32_t getPropId() const = 0;

    virtual int32_t getAccess() const = 0;

    virtual int32_t getChangeMode() const = 0;

    const std::vector<std::unique_ptr<IHalAreaConfig>>& getAreaConfigs() const {
        return mAreaConfigs;
    }

    virtual size_t getAreaConfigSize() const = 0;

    virtual std::vector<int32_t> getConfigArray() const = 0;

    virtual std::string getConfigString() const = 0;

    virtual float getMinSampleRate() const = 0;

    virtual float getMaxSampleRate() const = 0;

    virtual ~IHalPropConfig() = default;

protected:
    std::vector<std::unique_ptr<IHalAreaConfig>> mAreaConfigs;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_IHALPROPCONFIG_H_
