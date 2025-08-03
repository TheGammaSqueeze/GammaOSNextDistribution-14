/*
 * Copyright (c) 2020, The Android Open Source Project
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

#define LOG_TAG "carpowerpolicyd"
#define DEBUG false

#include "PowerComponentHandler.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;

using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;

void PowerComponentHandler::init() {
    Mutex::Autolock lock(mMutex);
    mAccumulatedPolicy = std::make_shared<CarPowerPolicy>();
    for (const auto componentId : ::ndk::enum_range<PowerComponent>()) {
        if (componentId >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;  // skip custom components
        }
        mAccumulatedPolicy->disabledComponents.push_back(componentId);
    }
}

void PowerComponentHandler::applyPowerPolicy(const CarPowerPolicyPtr& powerPolicy) {
    Mutex::Autolock lock(mMutex);
    std::unordered_map<PowerComponent, bool> componentStates;
    std::unordered_map<int, bool> customComponentStates;

    auto setComponentStates = [](auto& map, const auto& componentsVec, auto value) {
        for (const auto component : componentsVec) {
            map[component] = value;
        }
    };

    mAccumulatedPolicy->policyId = powerPolicy->policyId;
    setComponentStates(componentStates, mAccumulatedPolicy->enabledComponents, true);
    setComponentStates(componentStates, mAccumulatedPolicy->disabledComponents, false);
    setComponentStates(componentStates, powerPolicy->enabledComponents, true);
    setComponentStates(componentStates, powerPolicy->disabledComponents, false);

    setComponentStates(customComponentStates, mAccumulatedPolicy->enabledCustomComponents, true);
    setComponentStates(customComponentStates, mAccumulatedPolicy->disabledCustomComponents, false);
    setComponentStates(customComponentStates, powerPolicy->enabledCustomComponents, true);
    setComponentStates(customComponentStates, powerPolicy->disabledCustomComponents, false);

    mAccumulatedPolicy->enabledComponents.clear();
    mAccumulatedPolicy->disabledComponents.clear();
    mAccumulatedPolicy->enabledCustomComponents.clear();
    mAccumulatedPolicy->disabledCustomComponents.clear();

    auto setAccumulatedPolicy = [](auto& statesMap, auto& enabledComponents,
                                   auto& disabledComponents) {
        for (const auto [component, state] : statesMap) {
            if (state) {
                enabledComponents.push_back(component);
            } else {
                disabledComponents.push_back(component);
            }
        }
    };

    setAccumulatedPolicy(componentStates, mAccumulatedPolicy->enabledComponents,
                         mAccumulatedPolicy->disabledComponents);
    setAccumulatedPolicy(customComponentStates, mAccumulatedPolicy->enabledCustomComponents,
                         mAccumulatedPolicy->disabledCustomComponents);
}

template <typename T>
Result<bool> getComponentState(const T& componentId, const std::vector<T>& enabledComponents,
                               const std::vector<T>& disabledComponents) {
    auto findComponent = [componentId](const std::vector<T> components) -> bool {
        return std::find(components.begin(), components.end(), componentId) != components.end();
    };

    if (findComponent(enabledComponents)) {
        return true;
    }
    if (findComponent(disabledComponents)) {
        return false;
    }
    return Error() << StringPrintf("Invalid power component(%d)", componentId);
}

Result<bool> PowerComponentHandler::getCustomPowerComponentState(const int componentId) const {
    Mutex::Autolock lock(mMutex);

    return getComponentState(componentId, mAccumulatedPolicy->enabledCustomComponents,
                             mAccumulatedPolicy->disabledCustomComponents);
}

Result<bool> PowerComponentHandler::getPowerComponentState(const PowerComponent componentId) const {
    Mutex::Autolock lock(mMutex);
    return getComponentState(componentId, mAccumulatedPolicy->enabledComponents,
                             mAccumulatedPolicy->disabledComponents);
}

CarPowerPolicyPtr PowerComponentHandler::getAccumulatedPolicy() const {
    Mutex::Autolock lock(mMutex);
    return mAccumulatedPolicy;
}

Result<void> PowerComponentHandler::dump(int fd) {
    Mutex::Autolock lock(mMutex);
    const char* indent = "  ";
    const char* doubleIndent = "    ";

    auto customComponentToString = [](int component) -> std::string {
        return std::to_string(component);
    };

    auto printComponents = [fd](const auto& components, auto toStringFunc) {
        bool isNotFirst = false;
        for (const auto component : components) {
            if (isNotFirst) {
                WriteStringToFd(", ", fd);
            } else {
                isNotFirst = true;
            }
            WriteStringToFd(toStringFunc(component), fd);
        }
        WriteStringToFd("\n", fd);
    };

    WriteStringToFd(StringPrintf("%sCurrent state of power components:\n", indent), fd);
    WriteStringToFd(StringPrintf("%sEnabled components: ", doubleIndent), fd);
    printComponents(mAccumulatedPolicy->enabledComponents,
                    aidl::android::frameworks::automotive::powerpolicy::toString);
    WriteStringToFd(StringPrintf("%sDisabled components: ", doubleIndent), fd);
    printComponents(mAccumulatedPolicy->disabledComponents,
                    aidl::android::frameworks::automotive::powerpolicy::toString);
    WriteStringToFd(StringPrintf("%sEnabled custom components: ", doubleIndent), fd);
    printComponents(mAccumulatedPolicy->enabledCustomComponents, customComponentToString);
    WriteStringToFd(StringPrintf("%sDisabled custom components: ", doubleIndent), fd);
    printComponents(mAccumulatedPolicy->disabledCustomComponents, customComponentToString);

    return {};
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
