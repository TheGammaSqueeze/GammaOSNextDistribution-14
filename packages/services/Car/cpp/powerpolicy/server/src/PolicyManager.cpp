/**
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
#define DEBUG false  // STOPSHIP if true.

#include "PolicyManager.h"

#include "android-base/parseint.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <utils/Log.h>

#include <tinyxml2.h>

#include <cstring>
#include <unordered_set>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;
using ::aidl::android::hardware::automotive::vehicle::VehicleApPowerStateReport;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::tinyxml2::XML_SUCCESS;
using ::tinyxml2::XMLDocument;
using ::tinyxml2::XMLElement;

namespace {

// Vendor power policy filename.
constexpr const char kVendorPolicyFile[] = "/vendor/etc/automotive/power_policy.xml";

// Tags and attributes in vendor power policy XML file.
constexpr const char kTagRoot[] = "powerPolicy";
constexpr const char kTagPolicyGroups[] = "policyGroups";
constexpr const char kTagPolicyGroup[] = "policyGroup";
constexpr const char kTagDefaultPolicy[] = "defaultPolicy";
constexpr const char kTagNoDefaultPolicy[] = "noDefaultPolicy";
constexpr const char kTagPolicies[] = "policies";
constexpr const char kTagPolicy[] = "policy";
constexpr const char kTagOtherComponents[] = "otherComponents";
constexpr const char kTagComponent[] = "component";
constexpr const char kTagSystemPolicyOverrides[] = "systemPolicyOverrides";
constexpr const char kAttrBehavior[] = "behavior";
constexpr const char kAttrId[] = "id";
constexpr const char kAttrState[] = "state";
constexpr const char kAttrDefaultPolicyGroup[] = "defaultPolicyGroup";
constexpr const char kTagCustomComponents[] = "customComponents";
constexpr const char kTagCustomComponent[] = "customComponent";
constexpr const char kAttrValue[] = "value";
// Power states.
constexpr const char kPowerStateOn[] = "on";
constexpr const char kPowerStateOff[] = "off";
constexpr const char kPowerStateUntouched[] = "untouched";

// Power transitions that a power policy can be applied with.
constexpr const char kPowerTransitionWaitForVhal[] = "WaitForVHAL";
constexpr const char kPowerTransitionOn[] = "On";

const PowerComponent INVALID_POWER_COMPONENT = static_cast<PowerComponent>(-1);
const int32_t INVALID_CUSTOM_POWER_COMPONENT = -1;
const int32_t MINIMUM_CUSTOM_COMPONENT_VALUE =
        static_cast<int>(PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE);
const int32_t INVALID_VEHICLE_POWER_STATE = -1;
const int32_t WAIT_FOR_VHAL_STATE = static_cast<int32_t>(VehicleApPowerStateReport::WAIT_FOR_VHAL);
const int32_t ON_STATE = static_cast<int32_t>(VehicleApPowerStateReport::ON);

constexpr const char kPowerComponentPrefix[] = "POWER_COMPONENT_";
constexpr const char kSystemPolicyPrefix[] = "system_power_policy_";

// System power policy definition: ID, enabled components, and disabled components.
const std::vector<PowerComponent> kNoUserInteractionEnabledComponents =
        {PowerComponent::WIFI, PowerComponent::CELLULAR, PowerComponent::ETHERNET,
         PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::CPU};
const std::vector<PowerComponent> kNoUserInteractionDisabledComponents =
        {PowerComponent::AUDIO,
         PowerComponent::MEDIA,
         PowerComponent::DISPLAY,
         PowerComponent::BLUETOOTH,
         PowerComponent::PROJECTION,
         PowerComponent::NFC,
         PowerComponent::INPUT,
         PowerComponent::VOICE_INTERACTION,
         PowerComponent::VISUAL_INTERACTION,
         PowerComponent::LOCATION,
         PowerComponent::MICROPHONE};
const std::vector<PowerComponent> kAllComponents = {PowerComponent::AUDIO,
                                                    PowerComponent::MEDIA,
                                                    PowerComponent::DISPLAY,
                                                    PowerComponent::BLUETOOTH,
                                                    PowerComponent::WIFI,
                                                    PowerComponent::CELLULAR,
                                                    PowerComponent::ETHERNET,
                                                    PowerComponent::PROJECTION,
                                                    PowerComponent::NFC,
                                                    PowerComponent::INPUT,
                                                    PowerComponent::VOICE_INTERACTION,
                                                    PowerComponent::VISUAL_INTERACTION,
                                                    PowerComponent::TRUSTED_DEVICE_DETECTION,
                                                    PowerComponent::LOCATION,
                                                    PowerComponent::MICROPHONE,
                                                    PowerComponent::CPU};
const std::vector<PowerComponent> kInitialOnComponents = {PowerComponent::AUDIO,
                                                          PowerComponent::DISPLAY,
                                                          PowerComponent::CPU};
const std::vector<PowerComponent> kNoComponents;
const std::vector<PowerComponent> kSuspendPrepDisabledComponents = {PowerComponent::AUDIO,
                                                                    PowerComponent::BLUETOOTH,
                                                                    PowerComponent::WIFI,
                                                                    PowerComponent::LOCATION,
                                                                    PowerComponent::MICROPHONE,
                                                                    PowerComponent::CPU};
const std::unordered_set<PowerComponent> kNoUserInteractionConfigurableComponents =
        {PowerComponent::BLUETOOTH, PowerComponent::NFC, PowerComponent::TRUSTED_DEVICE_DETECTION};

void iterateAllPowerComponents(const std::function<bool(PowerComponent)>& processor) {
    for (const auto component : ::ndk::enum_range<PowerComponent>()) {
        if (component >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;
        }
        if (!processor(component)) {
            break;
        }
    }
}

PowerComponent toPowerComponent(std::string_view id, std::string_view prefix) {
    if (!StartsWith(id, prefix)) {
        return INVALID_POWER_COMPONENT;
    }
    std::string_view componentId = id.substr(prefix.size());
    PowerComponent matchedComponent = INVALID_POWER_COMPONENT;
    iterateAllPowerComponents([componentId, &matchedComponent](PowerComponent component) -> bool {
        if (componentId == toString(component)) {
            matchedComponent = component;
            return false;
        }
        return true;
    });
    return matchedComponent;
}

int toCustomPowerComponent(const std::unordered_map<std::string, int>& customComponents,
                           const std::string_view& id) {
    if (customComponents.size() == 0) {
        return INVALID_CUSTOM_POWER_COMPONENT;
    }
    return customComponents.count(std::string(id)) > 0 ? customComponents.at(std::string(id))
                                                       : INVALID_CUSTOM_POWER_COMPONENT;
}

const char* safePtrPrint(const char* ptr) {
    return ptr == nullptr ? "nullptr" : ptr;
}

int32_t toVehiclePowerState(const char* state) {
    if (!strcmp(state, kPowerTransitionWaitForVhal)) {
        return WAIT_FOR_VHAL_STATE;
    }
    if (!strcmp(state, kPowerTransitionOn)) {
        return ON_STATE;
    }
    return INVALID_VEHICLE_POWER_STATE;
}

bool isValidPowerState(int32_t state) {
    return state != INVALID_VEHICLE_POWER_STATE;
}

void logXmlError(const std::string& errMsg) {
    ALOGW("Proceed without registered policies: %s", errMsg.c_str());
}

Result<void> readComponents(const XMLElement* pPolicy, CarPowerPolicyPtr policy,
                            std::unordered_set<PowerComponent>* visited,
                            std::unordered_set<int>* visitedCustomComponents,
                            const std::unordered_map<std::string, int>& customComponents) {
    auto updateVisitedComponents = [](const auto& componentId, auto* visitedComponents) {
        visitedComponents->insert(componentId);
    };

    auto updateComponentState = [](const auto& componentId, const auto& powerState,
                                   auto* enabledComponents,
                                   auto* disabledComponents) -> Result<void> {
        if (!strcmp(powerState, kPowerStateOn)) {
            enabledComponents->push_back(componentId);
        } else if (!strcmp(powerState, kPowerStateOff)) {
            disabledComponents->push_back(componentId);
        } else {
            return Error() << StringPrintf("XML configuration has invalid value(%s) in |%s| tag",
                                           safePtrPrint(powerState), kTagComponent);
        }
        return {};
    };
    for (const XMLElement* pComponent = pPolicy->FirstChildElement(kTagComponent);
         pComponent != nullptr; pComponent = pComponent->NextSiblingElement(kTagComponent)) {
        const char* id;
        if (pComponent->QueryStringAttribute(kAttrId, &id) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                           kTagComponent);
        }
        PowerComponent componentId = toPowerComponent(id, kPowerComponentPrefix);
        int customComponentId = INVALID_CUSTOM_POWER_COMPONENT;
        if (componentId == INVALID_POWER_COMPONENT) {
            customComponentId = toCustomPowerComponent(customComponents, id);
        }

        if (componentId == INVALID_POWER_COMPONENT &&
            customComponentId == INVALID_CUSTOM_POWER_COMPONENT) {
            return Error() << StringPrintf("XML configuration has invalid value(%s) in |%s| "
                                           "attribute of |%s| tag",
                                           safePtrPrint(id), kAttrId, kTagComponent);
        }

        if ((componentId != INVALID_POWER_COMPONENT && visited->count(componentId) > 0) ||
            (customComponentId != INVALID_CUSTOM_POWER_COMPONENT &&
             visitedCustomComponents->count(customComponentId) > 0)) {
            return Error() << StringPrintf("XML configuration has duplicated component(%s) in |%s| "
                                           "attribute of |%s| tag",
                                           toString(componentId).c_str(), kAttrId, kTagComponent);
        }

        if (componentId != INVALID_POWER_COMPONENT) {
            updateVisitedComponents(componentId, visited);
        } else if (customComponentId >= MINIMUM_CUSTOM_COMPONENT_VALUE) {
            updateVisitedComponents(customComponentId, visitedCustomComponents);
        }

        const char* powerState = pComponent->GetText();
        Result<void> result{};
        if (componentId != INVALID_POWER_COMPONENT) {
            result = updateComponentState(componentId, powerState, &policy->enabledComponents,
                                          &policy->disabledComponents);
        } else if (customComponentId >= MINIMUM_CUSTOM_COMPONENT_VALUE) {
            result = updateComponentState(customComponentId, powerState,
                                          &policy->enabledCustomComponents,
                                          &policy->disabledCustomComponents);
        }

        if (!result.ok()) {
            return result.error();
        }
    }
    return {};
}

Result<void> readOtherComponents(const XMLElement* pPolicy, CarPowerPolicyPtr policy,
                                 const std::unordered_set<PowerComponent>& visited,
                                 const std::unordered_map<std::string, int>& customComponents,
                                 const std::unordered_set<int>& visitedCustomComponents) {
    const char* otherComponentBehavior = kPowerStateUntouched;
    const XMLElement* pElement = pPolicy->FirstChildElement(kTagOtherComponents);
    if (pElement != nullptr) {
        if (pElement->QueryStringAttribute(kAttrBehavior, &otherComponentBehavior) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag",
                                           kAttrBehavior, kTagOtherComponents);
        }
    }

    std::vector<int> customComponentsVector;
    customComponentsVector.reserve(customComponents.size());
    std::transform(customComponents.begin(), customComponents.end(),
                   std::back_inserter(customComponentsVector),
                   [](const auto& element) { return element.second; });

    if (!strcmp(otherComponentBehavior, kPowerStateOn)) {
        iterateAllPowerComponents([&visited, &policy](PowerComponent component) -> bool {
            if (visited.count(component) == 0) {
                policy->enabledComponents.push_back(component);
            }
            return true;
        });

        std::copy_if(customComponentsVector.begin(), customComponentsVector.end(),
                     std::back_inserter(policy->enabledCustomComponents),
                     [&visitedCustomComponents](int componentId) {
                         return visitedCustomComponents.count(componentId) == 0;
                     });
    } else if (!strcmp(otherComponentBehavior, kPowerStateOff)) {
        iterateAllPowerComponents([&visited, &policy](PowerComponent component) -> bool {
            if (visited.count(component) == 0) {
                policy->disabledComponents.push_back(component);
            }
            return true;
        });
        std::copy_if(customComponentsVector.begin(), customComponentsVector.end(),
                     std::back_inserter(policy->disabledCustomComponents),
                     [&visitedCustomComponents](int componentId) {
                         return visitedCustomComponents.count(componentId) == 0;
                     });
    } else if (!strcmp(otherComponentBehavior, kPowerStateUntouched)) {
        // Do nothing
    } else {
        return Error() << StringPrintf("XML configuration has invalid value(%s) in |%s| attribute "
                                       "of |%s| tag",
                                       safePtrPrint(otherComponentBehavior), kAttrBehavior,
                                       kTagOtherComponents);
    }
    return {};
}

Result<std::vector<CarPowerPolicyPtr>> readPolicies(
        const XMLElement* pRoot, const char* tag, bool includeOtherComponents,
        const std::unordered_map<std::string, int>& customComponents) {
    std::vector<CarPowerPolicyPtr> policies;
    const XMLElement* pPolicies = pRoot->FirstChildElement(tag);
    if (pPolicies == nullptr) {
        return std::vector<CarPowerPolicyPtr>();
    }
    for (const XMLElement* pPolicy = pPolicies->FirstChildElement(kTagPolicy); pPolicy != nullptr;
         pPolicy = pPolicy->NextSiblingElement(kTagPolicy)) {
        std::unordered_set<PowerComponent> visited;
        std::unordered_set<int> visitedCustomComponents;

        const char* policyId;
        if (pPolicy->QueryStringAttribute(kAttrId, &policyId) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                           kTagPolicy);
        }
        if (includeOtherComponents && isSystemPowerPolicy(policyId)) {
            return Error() << "Policy ID should not start with \"system_power_policy_\"";
        }
        auto policy = std::make_shared<CarPowerPolicy>();
        policy->policyId = policyId;

        auto ret = readComponents(pPolicy, policy, &visited, &visitedCustomComponents,
                                  customComponents);
        if (!ret.ok()) {
            return ret.error();
        }
        if (includeOtherComponents) {
            ret = readOtherComponents(pPolicy, policy, visited, customComponents,
                                      visitedCustomComponents);
            if (!ret.ok()) {
                return ret.error();
            }
        }
        policies.push_back(policy);
    }
    return policies;
}

Result<PolicyGroup> readPolicyGroup(
        const XMLElement* pPolicyGroup,
        const std::unordered_map<std::string, CarPowerPolicyPtr>& registeredPowerPolicies) {
    PolicyGroup policyGroup;
    for (const XMLElement* pDefaultPolicy = pPolicyGroup->FirstChildElement(kTagDefaultPolicy);
         pDefaultPolicy != nullptr;
         pDefaultPolicy = pDefaultPolicy->NextSiblingElement(kTagDefaultPolicy)) {
        const char* state;
        if (pDefaultPolicy->QueryStringAttribute(kAttrState, &state) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrState,
                                           kTagDefaultPolicy);
        }
        int32_t powerState = toVehiclePowerState(state);
        if (!isValidPowerState(powerState)) {
            return Error() << StringPrintf("Target state(%s) is not valid", state);
        }
        const char* policyId;
        if (pDefaultPolicy->QueryStringAttribute(kAttrId, &policyId) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                           kTagDefaultPolicy);
        }
        if (registeredPowerPolicies.count(policyId) == 0) {
            return Error() << StringPrintf("Policy(id: %s) is not registered", policyId);
        }
        policyGroup.emplace(powerState, policyId);
    }
    for (const XMLElement* pNoPolicy = pPolicyGroup->FirstChildElement(kTagNoDefaultPolicy);
         pNoPolicy != nullptr; pNoPolicy = pNoPolicy->NextSiblingElement(kTagNoDefaultPolicy)) {
        const char* state;
        if (pNoPolicy->QueryStringAttribute(kAttrState, &state) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrState,
                                           kTagNoDefaultPolicy);
        }
        int32_t powerState = toVehiclePowerState(state);
        if (!isValidPowerState(powerState)) {
            return Error() << StringPrintf("Target state(%s) is not valid", state);
        }
        if (policyGroup.count(powerState) > 0) {
            return Error()
                    << StringPrintf("Target state(%s) is specified both in |%s| and |%s| tags",
                                    state, kTagDefaultPolicy, kTagNoDefaultPolicy);
        }
    }
    return policyGroup;
}

struct PolicyGroups {
    std::unordered_map<std::string, PolicyGroup> groups;
    std::string defaultGroup;
};

Result<PolicyGroups> readPolicyGroups(
        const XMLElement* pRoot,
        const std::unordered_map<std::string, CarPowerPolicyPtr>& registeredPowerPolicies) {
    const XMLElement* pPolicyGroups = pRoot->FirstChildElement(kTagPolicyGroups);

    PolicyGroups policyGroups;

    if (pPolicyGroups == nullptr) {
        return policyGroups;
    }

    const char* pDefaultPolicyGroupId = nullptr;
    pPolicyGroups->QueryStringAttribute(kAttrDefaultPolicyGroup, &pDefaultPolicyGroupId);
    if (pDefaultPolicyGroupId != nullptr) {
        policyGroups.defaultGroup = pDefaultPolicyGroupId;
    }

    for (const XMLElement* pPolicyGroup = pPolicyGroups->FirstChildElement(kTagPolicyGroup);
         pPolicyGroup != nullptr;
         pPolicyGroup = pPolicyGroup->NextSiblingElement(kTagPolicyGroup)) {
        const char* policyGroupId;
        if (pPolicyGroup->QueryStringAttribute(kAttrId, &policyGroupId) != XML_SUCCESS) {
            return Error() << StringPrintf("Failed to read |%s| attribute in |%s| tag", kAttrId,
                                           kTagPolicyGroup);
        }
        const auto& policyGroup = readPolicyGroup(pPolicyGroup, registeredPowerPolicies);
        if (!policyGroup.ok()) {
            return Error() << policyGroup.error();
        }
        policyGroups.groups.emplace(policyGroupId, *policyGroup);
    }
    return policyGroups;
}

bool isConfigurableComponent(PowerComponent component) {
    return kNoUserInteractionConfigurableComponents.count(component) > 0;
}

Result<void> checkConfigurableComponents(const std::vector<PowerComponent>& components) {
    for (auto component : components) {
        if (!isConfigurableComponent(component)) {
            return Error()
                    << StringPrintf("Component(%s) is not configurable in system power policy.",
                                    toString(component).c_str());
        }
    }
    return {};
}

Result<std::vector<CarPowerPolicyPtr>> readSystemPolicyOverrides(
        const XMLElement* pRoot, const std::unordered_map<std::string, int>& customComponents) {
    const auto& systemPolicyOverrides =
            readPolicies(pRoot, kTagSystemPolicyOverrides, false, customComponents);
    if (!systemPolicyOverrides.ok()) {
        return Error() << systemPolicyOverrides.error().message();
    }
    for (auto policy : *systemPolicyOverrides) {
        if (policy->policyId != kSystemPolicyIdNoUserInteraction) {
            return Error() << StringPrintf("System power policy(%s) is not supported.",
                                           policy->policyId.c_str());
        }
        auto ret = checkConfigurableComponents(policy->enabledComponents);
        if (!ret.ok()) {
            return ret.error();
        }
        ret = checkConfigurableComponents(policy->disabledComponents);
        if (!ret.ok()) {
            return ret.error();
        }
    }
    return systemPolicyOverrides;
}

Result<std::unordered_map<std::string, int>> readCustomComponents(const XMLElement* pRoot) {
    const XMLElement* pCustomComponents = pRoot->FirstChildElement(kTagCustomComponents);
    std::unordered_map<std::string, int> customComponentsMap;

    if (pCustomComponents == nullptr) {
        return {};
    }

    for (const XMLElement* pCustomComponent =
                 pCustomComponents->FirstChildElement(kTagCustomComponent);
         pCustomComponent != nullptr;
         pCustomComponent = pCustomComponent->NextSiblingElement(kTagCustomComponent)) {
        const char* componentName = pCustomComponent->GetText();

        int value = 0;
        pCustomComponent->QueryIntAttribute(kAttrValue, &value);

        if (value < MINIMUM_CUSTOM_COMPONENT_VALUE) {
            // log error
            logXmlError(StringPrintf("Component value is not in allowed range. componentName =  "
                                     "%s, value = %d",
                                     componentName, value));
            return Error() << StringPrintf("Component value is not in allowed range");
        }
        customComponentsMap.insert({componentName, value});
    }

    return customComponentsMap;
}

// configureComponents assumes that previously validated components are passed.
void configureComponents(const std::vector<PowerComponent>& configComponents,
                         std::vector<PowerComponent>* componentsAddedTo,
                         std::vector<PowerComponent>* componentsRemovedFrom) {
    for (const auto component : configComponents) {
        auto it = std::find(componentsAddedTo->begin(), componentsAddedTo->end(), component);
        if (it == componentsAddedTo->end()) {
            componentsAddedTo->push_back(component);
        }
        it = std::find(componentsRemovedFrom->begin(), componentsRemovedFrom->end(), component);
        if (it != componentsRemovedFrom->end()) {
            componentsRemovedFrom->erase(it);
        }
    }
}

Result<void> stringsToComponents(const std::vector<std::string>& arr,
                                 std::vector<PowerComponent>* components,
                                 std::vector<int>* customComponents) {
    for (const auto& c : arr) {
        const char* component = c.c_str();
        PowerComponent componentId = toPowerComponent(component, "");
        if (componentId == INVALID_POWER_COMPONENT) {
            int customComponentId = 0;
            bool result = android::base::ParseInt(component, &customComponentId);
            if (!result || customComponentId < MINIMUM_CUSTOM_COMPONENT_VALUE) {
                return Error() << StringPrintf("%s is not a valid component", component);
            }
            customComponents->push_back(customComponentId);
        } else {
            components->push_back(componentId);
        }
    }
    return {};
}

CarPowerPolicyPtr createPolicy(const char* policyId,
                               const std::vector<PowerComponent>& enabledComponents,
                               const std::vector<PowerComponent>& disabledComponents,
                               const std::vector<int>& enabledCustomComponents,
                               const std::vector<int>& disabledCustomComponents) {
    CarPowerPolicyPtr policy = std::make_shared<CarPowerPolicy>();
    policy->policyId = policyId;
    policy->enabledComponents = enabledComponents;
    policy->disabledComponents = disabledComponents;
    policy->disabledCustomComponents = disabledCustomComponents;
    policy->enabledCustomComponents = enabledCustomComponents;
    return policy;
}

}  // namespace

std::string toString(const std::vector<PowerComponent>& components) {
    size_t size = components.size();
    if (size == 0) {
        return "none";
    }
    std::string filterStr = toString(components[0]);
    for (size_t i = 1; i < size; i++) {
        StringAppendF(&filterStr, ", %s", toString(components[i]).c_str());
    }
    return filterStr;
}

std::string toString(const CarPowerPolicy& policy) {
    return StringPrintf("%s(enabledComponents: %s, disabledComponents: %s)",
                        policy.policyId.c_str(), toString(policy.enabledComponents).c_str(),
                        toString(policy.disabledComponents).c_str());
}

bool isSystemPowerPolicy(const std::string& policyId) {
    return StartsWith(policyId, kSystemPolicyPrefix);
}

void PolicyManager::init() {
    initRegularPowerPolicy(/*override=*/true);
    mPolicyGroups.clear();
    initPreemptivePowerPolicy();
    readPowerPolicyConfiguration();
}

Result<CarPowerPolicyMeta> PolicyManager::getPowerPolicy(const std::string& policyId) const {
    if (mRegisteredPowerPolicies.count(policyId) > 0) {
        return CarPowerPolicyMeta{
                .powerPolicy = mRegisteredPowerPolicies.at(policyId),
                .isPreemptive = false,
        };
    }
    if (mPreemptivePowerPolicies.count(policyId) > 0) {
        return CarPowerPolicyMeta{
                .powerPolicy = mPreemptivePowerPolicies.at(policyId),
                .isPreemptive = true,
        };
    }
    return Error() << StringPrintf("Power policy(id: %s) is not found", policyId.c_str());
}

Result<CarPowerPolicyPtr> PolicyManager::getDefaultPowerPolicyForState(
        const std::string& groupId, VehicleApPowerStateReport state) const {
    auto groupIdToUse = groupId.empty() ? mDefaultPolicyGroup : groupId;

    if (mPolicyGroups.count(groupIdToUse) == 0) {
        return Error() << StringPrintf("Power policy group %s is not found", groupIdToUse.c_str());
    }

    PolicyGroup policyGroup = mPolicyGroups.at(groupIdToUse);
    int32_t key = static_cast<int32_t>(state);
    if (policyGroup.count(key) == 0) {
        return Error() << StringPrintf("Policy for %s is not found", toString(state).c_str());
    }
    return mRegisteredPowerPolicies.at(policyGroup.at(key));
}

bool PolicyManager::isPowerPolicyGroupAvailable(const std::string& groupId) const {
    return mPolicyGroups.count(groupId) > 0;
}

bool PolicyManager::isPreemptivePowerPolicy(const std::string& policyId) const {
    return mPreemptivePowerPolicies.count(policyId) > 0;
}

Result<void> PolicyManager::definePowerPolicy(const std::string& policyId,
                                              const std::vector<std::string>& enabledComponents,
                                              const std::vector<std::string>& disabledComponents) {
    if (mRegisteredPowerPolicies.count(policyId) > 0) {
        return Error() << StringPrintf("%s is already registered", policyId.c_str());
    }
    auto policy = std::make_shared<CarPowerPolicy>();
    policy->policyId = policyId;
    auto ret = stringsToComponents(enabledComponents, &policy->enabledComponents,
                                   &policy->enabledCustomComponents);
    if (!ret.ok()) {
        return ret;
    }
    ret = stringsToComponents(disabledComponents, &policy->disabledComponents,
                              &policy->disabledCustomComponents);
    if (!ret.ok()) {
        return ret;
    }
    mRegisteredPowerPolicies.emplace(policyId, policy);
    return {};
}

Result<void> PolicyManager::definePowerPolicyGroup(
        const std::string& policyGroupId, const std::vector<std::string>& powerPolicyPerState) {
    if (isPowerPolicyGroupAvailable(policyGroupId)) {
        return Error() << StringPrintf("%s is already registered", policyGroupId.c_str());
    }
    if (powerPolicyPerState.size() != 2) {
        return Error() << StringPrintf(
                       "Power policies for both WaitForVHAL and On should be given");
    }
    PolicyGroup policyGroup;
    int32_t i = 0;
    for (const int32_t powerState : {WAIT_FOR_VHAL_STATE, ON_STATE}) {
        if (const auto& policy = getPowerPolicy(powerPolicyPerState[i]); policy.ok()) {
            policyGroup[powerState] = powerPolicyPerState[i];
        } else if (!powerPolicyPerState[i].empty()) {
            return Error() << StringPrintf(
                           "Power policy group with unregistered policy cannot be registered");
        }
        i++;
    }
    mPolicyGroups.emplace(policyGroupId, policyGroup);
    return {};
}

Result<void> PolicyManager::dump(int fd, const Vector<String16>& /*args*/) {
    const char* indent = "  ";
    const char* doubleIndent = "    ";
    const char* tripleIndent = "      ";

    WriteStringToFd(StringPrintf("%sRegistered power policies:%s\n", indent,
                                 mRegisteredPowerPolicies.size() ? "" : " none"),
                    fd);
    for (auto& it : mRegisteredPowerPolicies) {
        WriteStringToFd(StringPrintf("%s- %s\n", doubleIndent, toString(*it.second).c_str()), fd);
    }
    WriteStringToFd(StringPrintf("%sPower policy groups:%s\n", indent,
                                 mPolicyGroups.size() ? "" : " none"),
                    fd);
    for (auto& itGroup : mPolicyGroups) {
        WriteStringToFd(StringPrintf("%s%s\n", doubleIndent, itGroup.first.c_str()), fd);
        for (auto& itMapping : itGroup.second) {
            VehicleApPowerStateReport state =
                    static_cast<VehicleApPowerStateReport>(itMapping.first);
            WriteStringToFd(StringPrintf("%s- %s --> %s\n", tripleIndent, toString(state).c_str(),
                                         itMapping.second.c_str()),
                            fd);
        }
    }
    WriteStringToFd(StringPrintf("%sNo user interaction power policy: %s\n", indent,
                                 toString(*mPreemptivePowerPolicies.at(
                                                  kSystemPolicyIdNoUserInteraction))
                                         .c_str()),
                    fd);
    return {};
}

std::string PolicyManager::getDefaultPolicyGroup() const {
    return mDefaultPolicyGroup;
}

std::vector<int32_t> PolicyManager::getCustomComponents() const {
    std::vector<int32_t> customComponents;
    for (const auto& [_, component] : mCustomComponents) {
        customComponents.push_back(component);
    }

    return customComponents;
}

std::vector<CarPowerPolicy> PolicyManager::getRegisteredPolicies() const {
    std::vector<CarPowerPolicy> registeredPolicies;
    auto policyMapToVector =
            [&registeredPolicies](
                    const std::unordered_map<std::string, CarPowerPolicyPtr>& policyMap) {
                for (const auto& [_, policy] : policyMap) {
                    registeredPolicies.push_back(*policy);
                }
            };
    policyMapToVector(mPreemptivePowerPolicies);
    policyMapToVector(mRegisteredPowerPolicies);

    return registeredPolicies;
}

void PolicyManager::readPowerPolicyConfiguration() {
    XMLDocument xmlDoc;
    xmlDoc.LoadFile(kVendorPolicyFile);
    if (xmlDoc.ErrorID() != XML_SUCCESS) {
        logXmlError(StringPrintf("Failed to read and/or parse %s", kVendorPolicyFile));
        return;
    }
    readPowerPolicyFromXml(xmlDoc);
}

void PolicyManager::readPowerPolicyFromXml(const XMLDocument& xmlDoc) {
    const XMLElement* pRootElement = xmlDoc.RootElement();
    if (!pRootElement || strcmp(pRootElement->Name(), kTagRoot)) {
        logXmlError(StringPrintf("XML file is not in the required format"));
        return;
    }

    const auto& customComponents = readCustomComponents(pRootElement);
    if (!customComponents.ok()) {
        logXmlError(StringPrintf("Reading custom components failed: %s",
                                 customComponents.error().message().c_str()));
        return;
    }

    mCustomComponents = *customComponents;
    const auto& registeredPolicies =
            readPolicies(pRootElement, kTagPolicies, true, mCustomComponents);

    if (!registeredPolicies.ok()) {
        logXmlError(StringPrintf("Reading policies failed: %s",
                                 registeredPolicies.error().message().c_str()));
        return;
    }
    std::unordered_map<std::string, CarPowerPolicyPtr> registeredPoliciesMap;
    for (auto policy : *registeredPolicies) {
        registeredPoliciesMap.emplace(policy->policyId, policy);
    }

    const auto& policyGroups = readPolicyGroups(pRootElement, registeredPoliciesMap);
    if (!policyGroups.ok()) {
        logXmlError(StringPrintf("Reading power policy groups for power state failed: %s",
                                 policyGroups.error().message().c_str()));
        return;
    }
    const auto& systemPolicyOverrides = readSystemPolicyOverrides(pRootElement, mCustomComponents);
    if (!systemPolicyOverrides.ok()) {
        logXmlError(StringPrintf("Reading system power policy overrides failed: %s",
                                 systemPolicyOverrides.error().message().c_str()));
        return;
    }

    mRegisteredPowerPolicies = registeredPoliciesMap;
    initRegularPowerPolicy(/*override=*/false);
    mPolicyGroups = policyGroups->groups;
    mDefaultPolicyGroup = policyGroups->defaultGroup;
    // TODO(b/273315694) check if custom components in policies are defined
    reconstructNoUserInteractionPolicy(*systemPolicyOverrides);
}

void PolicyManager::reconstructNoUserInteractionPolicy(
        const std::vector<CarPowerPolicyPtr>& policyOverrides) {
    CarPowerPolicyPtr systemPolicy = mPreemptivePowerPolicies.at(kSystemPolicyIdNoUserInteraction);
    for (auto policy : policyOverrides) {
        configureComponents(policy->enabledComponents, &systemPolicy->enabledComponents,
                            &systemPolicy->disabledComponents);
        configureComponents(policy->disabledComponents, &systemPolicy->disabledComponents,
                            &systemPolicy->enabledComponents);
    }
}

void PolicyManager::initRegularPowerPolicy(bool override) {
    if (override) {
        mRegisteredPowerPolicies.clear();
    }
    mRegisteredPowerPolicies.emplace(kSystemPolicyIdAllOn,
                                     createPolicy(kSystemPolicyIdAllOn, kAllComponents,
                                                  kNoComponents, {}, {}));

    std::vector<PowerComponent> initialOnDisabledComponents;
    for (const auto component : ::ndk::enum_range<PowerComponent>()) {
        if (component >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;
        }
        if (std::find(kInitialOnComponents.begin(), kInitialOnComponents.end(), component) ==
            kInitialOnComponents.end()) {
            initialOnDisabledComponents.push_back(component);
        }
    }
    mRegisteredPowerPolicies.emplace(kSystemPolicyIdInitialOn,
                                     createPolicy(kSystemPolicyIdInitialOn, kInitialOnComponents,
                                                  initialOnDisabledComponents, {}, {}));
}

void PolicyManager::initPreemptivePowerPolicy() {
    mPreemptivePowerPolicies.clear();
    mPreemptivePowerPolicies.emplace(kSystemPolicyIdNoUserInteraction,
                                     createPolicy(kSystemPolicyIdNoUserInteraction,
                                                  kNoUserInteractionEnabledComponents,
                                                  kNoUserInteractionDisabledComponents, {}, {}));
    mPreemptivePowerPolicies.emplace(kSystemPolicyIdSuspendPrep,
                                     createPolicy(kSystemPolicyIdSuspendPrep, kNoComponents,
                                                  kSuspendPrepDisabledComponents, {}, {}));
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
