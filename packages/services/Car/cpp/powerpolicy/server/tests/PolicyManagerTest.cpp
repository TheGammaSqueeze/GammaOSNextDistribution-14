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

#include "PolicyManager.h"

#include <aidl/android/hardware/automotive/vehicle/VehicleApPowerStateReport.h>
#include <android-base/file.h>
#include <gmock/gmock.h>

#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;
using ::aidl::android::hardware::automotive::vehicle::VehicleApPowerStateReport;
using ::testing::UnorderedElementsAre;
using ::tinyxml2::XML_SUCCESS;
using ::tinyxml2::XMLDocument;

namespace test {

constexpr const char* kDirPrefix = "/tests/data/";

constexpr const char* kValidPowerPolicyXmlFile = "valid_power_policy.xml";
constexpr const char* kValidPowerPolicyCustomComponentsXmlFile =
        "valid_power_policy_custom_components.xml";
constexpr const char* kInvalidPowerPolicyCustomComponentsXmlFile =
        "invalid_power_policy_custom_components.xml";
constexpr const char* kValidPowerPolicyNoPowerPolicyGroupsXmlFile =
        "valid_power_policy_no_power_policy_groups.xml";
constexpr const char* kValidPowerPolicyNoSystemPowerPolicyXmlFile =
        "valid_power_policy_no_system_power_policy.xml";
constexpr const char* kValidPowerPolicyPowerPoliciesOnlyXmlFile =
        "valid_power_policy_policies_only.xml";
constexpr const char* kValidPowerPolicySystemPowerPolicyOnlyXmlFile =
        "valid_power_policy_system_power_policy_only.xml";
constexpr const char* kValidPowerPolicyWithDefaultPolicyGroup =
        "valid_power_policy_default_policy_group.xml";
constexpr const char* kValidPowerPolicyWithInvalidDefaultPolicyGroup =
        "invalid_system_power_policy_incorrect_default_power_policy_group_id.xml";
const std::vector<const char*> kInvalidPowerPolicyXmlFiles =
        {"invalid_power_policy_incorrect_id.xml",
         "invalid_power_policy_incorrect_othercomponent.xml",
         "invalid_power_policy_incorrect_value.xml", "invalid_power_policy_unknown_component.xml",
         "invalid_system_power_policy_incorrect_default_power_policy_group_id.xml"};
const std::vector<const char*> kInvalidPowerPolicyGroupXmlFiles =
        {"invalid_power_policy_group_incorrect_state.xml",
         "invalid_power_policy_group_missing_policy.xml"};
const std::vector<const char*> kInvalidSystemPowerPolicyXmlFiles =
        {"invalid_system_power_policy_incorrect_component.xml",
         "invalid_system_power_policy_incorrect_id.xml"};

constexpr const char* kExistingPowerPolicyId = "expected_to_be_registered";
constexpr const char* kExistingPowerPolicyId_OtherOff = "policy_id_other_off";
constexpr const char* kExistingPowerPolicyId_OtherOn = "policy_id_other_on";
constexpr const char* kExistingPowerPolicyId_OtherUntouched = "policy_id_other_untouched";
constexpr const char* kExistingPowerPolicyId_OtherNone = "policy_id_other_none";
constexpr const char* kNonExistingPowerPolicyId = "non_existing_power_poicy_id";
constexpr const char* kValidPowerPolicyGroupId = "mixed_policy_group";
constexpr const char* kInvalidPowerPolicyGroupId = "invalid_policy_group";
constexpr const char* kSystemPolicyIdNoUserInteraction = "system_power_policy_no_user_interaction";
constexpr const char* kSystemPolicyIdinitialOn = "system_power_policy_initial_on";
constexpr const char* kSystemPolicyIdinitialAllOn = "system_power_policy_all_on";
constexpr const char* kSystemPolicyIdSuspendPrep = "system_power_policy_suspend_prep";
constexpr const char* kMixedPolicyGroupName = "mixed_policy_group";

constexpr const int CUSTOM_COMPONENT_ID_1000 = 1000;
constexpr const int CUSTOM_COMPONENT_AUX_INPUT = 1002;
constexpr const int CUSTOM_COMPONENT_SPECIAL_SENSOR = 1003;

const VehicleApPowerStateReport kExistingTransition = VehicleApPowerStateReport::WAIT_FOR_VHAL;
const VehicleApPowerStateReport kNonExistingTransition = static_cast<VehicleApPowerStateReport>(-1);

CarPowerPolicy createCarPowerPolicy(const std::string& id,
                                    const std::vector<PowerComponent>& enabledComponents,
                                    const std::vector<PowerComponent>& disabledComponents) {
    CarPowerPolicy policy;
    policy.policyId = id;
    policy.enabledComponents = enabledComponents;
    policy.disabledComponents = disabledComponents;
    return policy;
}

CarPowerPolicy createCarPowerPolicyWithCustomComponents(
        const std::string& id, const std::vector<PowerComponent>& enabledComponents,
        const std::vector<PowerComponent>& disabledComponents,
        const std::vector<int>& enabledCustomComponents,
        const std::vector<int>& disabledCustomComponents) {
    CarPowerPolicy policy;
    policy.policyId = id;
    policy.enabledComponents = enabledComponents;
    policy.disabledComponents = disabledComponents;
    policy.enabledCustomComponents = enabledCustomComponents;
    policy.disabledCustomComponents = disabledCustomComponents;
    return policy;
}

const CarPowerPolicy kExistingPowerPolicyWithCustomComponents_OtherOff =
        createCarPowerPolicyWithCustomComponents("policy_id_custom_other_off",
                                                 {PowerComponent::WIFI},
                                                 {PowerComponent::AUDIO, PowerComponent::MEDIA,
                                                  PowerComponent::DISPLAY,
                                                  PowerComponent::BLUETOOTH,
                                                  PowerComponent::CELLULAR,
                                                  PowerComponent::ETHERNET,
                                                  PowerComponent::PROJECTION, PowerComponent::NFC,
                                                  PowerComponent::INPUT,
                                                  PowerComponent::VOICE_INTERACTION,
                                                  PowerComponent::VISUAL_INTERACTION,
                                                  PowerComponent::TRUSTED_DEVICE_DETECTION,
                                                  PowerComponent::LOCATION,
                                                  PowerComponent::MICROPHONE, PowerComponent::CPU},
                                                 {CUSTOM_COMPONENT_AUX_INPUT},
                                                 {CUSTOM_COMPONENT_ID_1000,
                                                  CUSTOM_COMPONENT_SPECIAL_SENSOR});

const CarPowerPolicy kExistingPowerPolicy_OtherOff_With_Custom_Components =
        createCarPowerPolicyWithCustomComponents("policy_id_other_off", {PowerComponent::WIFI},
                                                 {PowerComponent::AUDIO, PowerComponent::MEDIA,
                                                  PowerComponent::DISPLAY,
                                                  PowerComponent::BLUETOOTH,
                                                  PowerComponent::CELLULAR,
                                                  PowerComponent::ETHERNET,
                                                  PowerComponent::PROJECTION, PowerComponent::NFC,
                                                  PowerComponent::INPUT,
                                                  PowerComponent::VOICE_INTERACTION,
                                                  PowerComponent::VISUAL_INTERACTION,
                                                  PowerComponent::TRUSTED_DEVICE_DETECTION,
                                                  PowerComponent::LOCATION,
                                                  PowerComponent::MICROPHONE, PowerComponent::CPU},
                                                 {CUSTOM_COMPONENT_AUX_INPUT},
                                                 {CUSTOM_COMPONENT_ID_1000,
                                                  CUSTOM_COMPONENT_SPECIAL_SENSOR});
const CarPowerPolicy kExistingPowerPolicy_OtherOff =
        createCarPowerPolicy("policy_id_other_off", {PowerComponent::WIFI},
                             {PowerComponent::AUDIO, PowerComponent::MEDIA, PowerComponent::DISPLAY,
                              PowerComponent::BLUETOOTH, PowerComponent::CELLULAR,
                              PowerComponent::ETHERNET, PowerComponent::PROJECTION,
                              PowerComponent::NFC, PowerComponent::INPUT,
                              PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE, PowerComponent::CPU});
const CarPowerPolicy kExistingPowerPolicyWithCustomComponents_OtherOn =
        createCarPowerPolicyWithCustomComponents("policy_id_other_on",
                                                 {PowerComponent::WIFI, PowerComponent::MEDIA,
                                                  PowerComponent::DISPLAY,
                                                  PowerComponent::BLUETOOTH,
                                                  PowerComponent::CELLULAR,
                                                  PowerComponent::ETHERNET,
                                                  PowerComponent::PROJECTION, PowerComponent::NFC,
                                                  PowerComponent::INPUT, PowerComponent::LOCATION,
                                                  PowerComponent::MICROPHONE, PowerComponent::CPU},
                                                 {PowerComponent::AUDIO,
                                                  PowerComponent::VOICE_INTERACTION,
                                                  PowerComponent::VISUAL_INTERACTION,
                                                  PowerComponent::TRUSTED_DEVICE_DETECTION},
                                                 {CUSTOM_COMPONENT_ID_1000,
                                                  CUSTOM_COMPONENT_SPECIAL_SENSOR},
                                                 {CUSTOM_COMPONENT_AUX_INPUT});
const CarPowerPolicy kExistingPowerPolicy_ToBeRegistered =
        createCarPowerPolicy("expected_to_be_registered",
                             {PowerComponent::WIFI, PowerComponent::AUDIO, PowerComponent::MEDIA,
                              PowerComponent::DISPLAY, PowerComponent::BLUETOOTH,
                              PowerComponent::CELLULAR, PowerComponent::ETHERNET,
                              PowerComponent::PROJECTION, PowerComponent::NFC,
                              PowerComponent::INPUT, PowerComponent::VOICE_INTERACTION,
                              PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE, PowerComponent::CPU},
                             {});
const CarPowerPolicy kExistingPowerPolicy_OtherOn =
        createCarPowerPolicy("policy_id_other_on",
                             {PowerComponent::MEDIA, PowerComponent::DISPLAY,
                              PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                              PowerComponent::CELLULAR, PowerComponent::ETHERNET,
                              PowerComponent::PROJECTION, PowerComponent::NFC,
                              PowerComponent::INPUT, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE, PowerComponent::CPU},
                             {PowerComponent::AUDIO, PowerComponent::VOICE_INTERACTION,
                              PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION});
const CarPowerPolicy kExistingPowerPolicy_OtherOn_WithOEMComponents =
        createCarPowerPolicyWithCustomComponents("policy_id_other_on",
                                                 {PowerComponent::MEDIA, PowerComponent::DISPLAY,
                                                  PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                                                  PowerComponent::CELLULAR,
                                                  PowerComponent::ETHERNET,
                                                  PowerComponent::PROJECTION, PowerComponent::NFC,
                                                  PowerComponent::INPUT, PowerComponent::LOCATION,
                                                  PowerComponent::MICROPHONE, PowerComponent::CPU},
                                                 {PowerComponent::AUDIO,
                                                  PowerComponent::VOICE_INTERACTION,
                                                  PowerComponent::VISUAL_INTERACTION,
                                                  PowerComponent::TRUSTED_DEVICE_DETECTION},
                                                 {CUSTOM_COMPONENT_ID_1000,
                                                  CUSTOM_COMPONENT_AUX_INPUT,
                                                  CUSTOM_COMPONENT_SPECIAL_SENSOR},
                                                 {});
const CarPowerPolicy kExistingPowerPolicy_OtherUntouched =
        createCarPowerPolicy("policy_id_other_untouched",
                             {PowerComponent::AUDIO, PowerComponent::DISPLAY,
                              PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                              PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION},
                             {});
const CarPowerPolicy kExistingPowerPolicy_OtherUntouchedCustom =
        createCarPowerPolicyWithCustomComponents("policy_id_other_untouched",
                                                 {PowerComponent::AUDIO, PowerComponent::DISPLAY,
                                                  PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                                                  PowerComponent::VOICE_INTERACTION,
                                                  PowerComponent::VISUAL_INTERACTION,
                                                  PowerComponent::TRUSTED_DEVICE_DETECTION},
                                                 {}, {CUSTOM_COMPONENT_AUX_INPUT}, {});
const CarPowerPolicy kExistingPowerPolicy_OtherNone =
        createCarPowerPolicy("policy_id_other_none", {PowerComponent::WIFI},
                             {PowerComponent::AUDIO, PowerComponent::VOICE_INTERACTION,
                              PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION});
const CarPowerPolicy& kExistingTransitionPolicy = kExistingPowerPolicy_OtherOn;
const CarPowerPolicy kSystemPowerPolicyAllOn =
        createCarPowerPolicy("system_power_policy_all_on",
                             {PowerComponent::AUDIO, PowerComponent::MEDIA, PowerComponent::DISPLAY,
                              PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                              PowerComponent::CELLULAR, PowerComponent::ETHERNET,
                              PowerComponent::PROJECTION, PowerComponent::NFC,
                              PowerComponent::INPUT, PowerComponent::VOICE_INTERACTION,
                              PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE, PowerComponent::CPU},
                             {});
const CarPowerPolicy kSystemPowerPolicyInitialOn =
        createCarPowerPolicy("system_power_policy_initial_on",
                             {PowerComponent::AUDIO, PowerComponent::DISPLAY, PowerComponent::CPU},
                             {PowerComponent::MEDIA, PowerComponent::BLUETOOTH,
                              PowerComponent::WIFI, PowerComponent::CELLULAR,
                              PowerComponent::ETHERNET, PowerComponent::PROJECTION,
                              PowerComponent::NFC, PowerComponent::INPUT,
                              PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE});
const CarPowerPolicy kSystemPowerPolicyNoUserInteraction =
        createCarPowerPolicy("system_power_policy_no_user_interaction",
                             {PowerComponent::WIFI, PowerComponent::CELLULAR,
                              PowerComponent::ETHERNET, PowerComponent::TRUSTED_DEVICE_DETECTION,
                              PowerComponent::CPU},
                             {PowerComponent::AUDIO, PowerComponent::MEDIA, PowerComponent::DISPLAY,
                              PowerComponent::BLUETOOTH, PowerComponent::PROJECTION,
                              PowerComponent::NFC, PowerComponent::INPUT,
                              PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::LOCATION, PowerComponent::MICROPHONE});
const CarPowerPolicy kSystemPowerPolicySuspendPrep =
        createCarPowerPolicy("system_power_policy_suspend_prep", {},
                             {PowerComponent::AUDIO, PowerComponent::BLUETOOTH,
                              PowerComponent::WIFI, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE, PowerComponent::CPU});
const CarPowerPolicy kModifiedSystemPowerPolicy =
        createCarPowerPolicy("system_power_policy_no_user_interaction",
                             {PowerComponent::BLUETOOTH, PowerComponent::WIFI,
                              PowerComponent::CELLULAR, PowerComponent::ETHERNET,
                              PowerComponent::NFC, PowerComponent::CPU},
                             {PowerComponent::AUDIO, PowerComponent::MEDIA, PowerComponent::DISPLAY,
                              PowerComponent::PROJECTION, PowerComponent::INPUT,
                              PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                              PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                              PowerComponent::MICROPHONE});

std::string getTestDataPath(const char* filename) {
    static std::string baseDir = android::base::GetExecutableDirectory();
    return baseDir + kDirPrefix + filename;
}

template <typename T>
void printVectors(const std::vector<T>& a, const std::vector<T>& b, std::string (*toStringFn)(T)) {
    auto vectorToString = [&toStringFn](const std::vector<T>& v) -> std::string {
        std::ostringstream stringStream;
        std::for_each(v.begin(), v.end(), [&stringStream, &toStringFn](const auto& element) {
            stringStream << toStringFn(element) << " ";
        });
        return stringStream.str();
    };
    ALOGE("Vector a: %s", vectorToString(a).c_str());
    ALOGE("Vector b: %s", vectorToString(b).c_str());
}

template <typename T>
bool compareComponentVectors(const std::vector<T>& a, const std::vector<T>& b,
                             std::string (*toStringFn)(T)) {
    int lenA = a.size();
    int lenB = b.size();
    if (lenA != lenB) {
        ALOGE("Component vectors mismatch");
        printVectors(a, b, toStringFn);
        return false;
    }
    std::unordered_set<T> componentSet;
    for (const auto component : a) {
        componentSet.insert(component);
    }
    for (const auto component : b) {
        if (componentSet.count(component) == 0) {
            return false;
        }
        componentSet.erase(component);
    }
    return componentSet.size() == 0;
}

bool compareComponents(const std::vector<PowerComponent>& a, const std::vector<PowerComponent>& b) {
    return compareComponentVectors(a, b,
                                   aidl::android::frameworks::automotive::powerpolicy::toString);
}

std::string intToString(int component) {
    return std::to_string(component);
}

bool compareCustomComponents(const std::optional<std::vector<int>>& optionalA,
                             const std::optional<std::vector<int>>& optionalB) {
    if (!optionalA.has_value() && !optionalB.has_value()) {
        return true;
    }

    if (!(optionalA.has_value() && optionalB.has_value())) {  // one of the arrays is empty
        return false;
    }

    const auto& a = *optionalA;
    const auto& b = *optionalB;

    return compareComponentVectors(a, b, intToString);
}

bool isEqual(const CarPowerPolicy& a, const CarPowerPolicy& b) {
    if (a.policyId != b.policyId) {
        ALOGE("isEqual  a.polictID != b.policyId %s, %s", a.policyId.c_str(), b.policyId.c_str());
    }

    return a.policyId == b.policyId &&
            compareComponents(a.enabledComponents, b.enabledComponents) &&
            compareComponents(a.disabledComponents, b.disabledComponents) &&
            compareCustomComponents(a.enabledCustomComponents, b.enabledCustomComponents) &&
            compareCustomComponents(a.disabledCustomComponents, b.disabledCustomComponents);
}

bool comparePolicies(const std::vector<CarPowerPolicy>& actualPolicies,
                     std::unordered_map<std::string, CarPowerPolicy> expectedPolicies) {
    if (actualPolicies.size() != expectedPolicies.size()) return false;
    for (const auto& policy : actualPolicies) {
        if (expectedPolicies.count(policy.policyId) == 0) {
            return false;
        }
        if (!isEqual(policy, expectedPolicies[policy.policyId])) return false;
        expectedPolicies.erase(policy.policyId);
    }
    return expectedPolicies.size() == 0;
}

void checkPolicies(const PolicyManager& policyManager) {
    ASSERT_FALSE(policyManager.getPowerPolicy(kNonExistingPowerPolicyId).ok());

    // otherComponents behavior = off
    auto policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOff);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherOff));
    // otherComponents behavior = on
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOn);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherOn));
    // otherComponents behavior = untouched
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherUntouched);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherUntouched));
    // otherComponents behavior = none
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherNone);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherNone));
}

void checkPoliciesWithCustomComponents(const PolicyManager& policyManager) {
    ASSERT_FALSE(policyManager.getPowerPolicy(kNonExistingPowerPolicyId).ok());

    // otherComponents behavior = off
    auto policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOff);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy,
                        kExistingPowerPolicy_OtherOff_With_Custom_Components));
    // policy with custom components
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOff);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy,
                        kExistingPowerPolicy_OtherOff_With_Custom_Components));
    // otherComponents behavior = on
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOn);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(
            isEqual(*policyMeta->powerPolicy, kExistingPowerPolicyWithCustomComponents_OtherOn));
    // otherComponents behavior = untouched
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherUntouched);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherUntouchedCustom));
    // otherComponents behavior = none
    policyMeta = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherNone);
    ASSERT_TRUE(policyMeta.ok());
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kExistingPowerPolicy_OtherNone));
}

void checkPowerPolicyGroups(const PolicyManager& policyManager) {
    auto policy = policyManager.getDefaultPowerPolicyForState(kValidPowerPolicyGroupId,
                                                              kExistingTransition);
    ASSERT_TRUE(policy.ok());
    ASSERT_TRUE(isEqual(**policy, kExistingTransitionPolicy));
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kNonExistingTransition)
                    .ok());
}

void checkSystemPowerPolicy(const PolicyManager& policyManager,
                            const CarPowerPolicy& expectedPolicy) {
    auto policyMeta = policyManager.getPowerPolicy(kSystemPolicyIdNoUserInteraction);
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, expectedPolicy));
}

void checkInvalidPolicies(const PolicyManager& policyManager) {
    ASSERT_FALSE(policyManager.getPowerPolicy(kExistingPowerPolicyId).ok());
    ASSERT_FALSE(policyManager.getPowerPolicy(kNonExistingPowerPolicyId).ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kExistingTransition)
                    .ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kNonExistingTransition)
                    .ok());
    auto policyMeta = policyManager.getPowerPolicy(kSystemPolicyIdNoUserInteraction);
    ASSERT_TRUE(isEqual(*policyMeta->powerPolicy, kSystemPowerPolicyNoUserInteraction));
}

void assertDefaultPolicies(const PolicyManager& policyManager) {
    ASSERT_TRUE(policyManager.getPowerPolicy(kSystemPolicyIdSuspendPrep).ok());
    ASSERT_TRUE(policyManager.getPowerPolicy(kSystemPolicyIdNoUserInteraction).ok());
    ASSERT_TRUE(policyManager.getPowerPolicy(kSystemPolicyIdinitialOn).ok());
    ASSERT_TRUE(policyManager.getPowerPolicy(kSystemPolicyIdinitialAllOn).ok());
}

}  // namespace test

namespace internal {

class PolicyManagerPeer {
public:
    explicit PolicyManagerPeer(PolicyManager* manager) : mManager(manager) {
        manager->initRegularPowerPolicy(/*override=*/true);
        manager->initPreemptivePowerPolicy();
    }

    void expectValidPowerPolicyXML(const char* filename) { readXmlFile(filename); }
    void expectInvalidPowerPolicyXML(const char* filename) { readXmlFile(filename); }

private:
    void readXmlFile(const char* filename) {
        XMLDocument xmlDoc;
        std::string path = test::getTestDataPath(filename);
        xmlDoc.LoadFile(path.c_str());
        ASSERT_TRUE(xmlDoc.ErrorID() == XML_SUCCESS);
        mManager->readPowerPolicyFromXml(xmlDoc);
    }

private:
    PolicyManager* mManager;
};

}  // namespace internal

namespace test {

class PolicyManagerTest : public ::testing::Test {};

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicy) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    checkPolicies(policyManager);
}

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicyGroup) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    checkPowerPolicyGroups(policyManager);
}

TEST_F(PolicyManagerTest, TestValidXml_SystemPowerPolicy) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    checkSystemPowerPolicy(policyManager, kModifiedSystemPowerPolicy);
}

TEST_F(PolicyManagerTest, TestValidXml_NoPowerPolicyGroups) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyNoPowerPolicyGroupsXmlFile);

    checkPolicies(policyManager);
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kExistingTransition)
                    .ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kNonExistingTransition)
                    .ok());
    checkSystemPowerPolicy(policyManager, kModifiedSystemPowerPolicy);
}

TEST_F(PolicyManagerTest, TestValidXml_NoSystemPowerPolicy) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyNoSystemPowerPolicyXmlFile);

    checkPolicies(policyManager);
    checkPowerPolicyGroups(policyManager);
    checkSystemPowerPolicy(policyManager, kSystemPowerPolicyNoUserInteraction);
}

TEST_F(PolicyManagerTest, TestValidXml_PoliciesOnly) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyPowerPoliciesOnlyXmlFile);

    checkPolicies(policyManager);
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kExistingTransition)
                    .ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kNonExistingTransition)
                    .ok());
    checkSystemPowerPolicy(policyManager, kSystemPowerPolicyNoUserInteraction);
}

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicyCustomComponents) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyCustomComponentsXmlFile);
    checkPoliciesWithCustomComponents(policyManager);
}

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicyCustomComponents_Valid) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyCustomComponentsXmlFile);
    auto policy = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOff);
    ASSERT_TRUE(policy.ok());
}

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicyCustomComponents_invalid_xml) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kInvalidPowerPolicyCustomComponentsXmlFile);
    auto policy = policyManager.getPowerPolicy(kExistingPowerPolicyId_OtherOff);
    ASSERT_FALSE(policy.ok());
}

TEST_F(PolicyManagerTest, TestValidXml_SystemPowerPolicyOnly) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicySystemPowerPolicyOnlyXmlFile);

    ASSERT_FALSE(policyManager.getPowerPolicy(kExistingPowerPolicyId).ok());
    ASSERT_FALSE(policyManager.getPowerPolicy(kNonExistingPowerPolicyId).ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kExistingTransition)
                    .ok());
    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kValidPowerPolicyGroupId, kNonExistingTransition)
                    .ok());
    checkSystemPowerPolicy(policyManager, kModifiedSystemPowerPolicy);
}

TEST_F(PolicyManagerTest, TestValidXml_TestDefaultPowerPolicyGroupId) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyWithDefaultPolicyGroup);

    ASSERT_TRUE(policyManager.getDefaultPolicyGroup() == kMixedPolicyGroupName);
}

TEST_F(PolicyManagerTest, TestValidXml_TestInvalidDefaultPowerPolicyGroupId) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyWithInvalidDefaultPolicyGroup);

    ASSERT_EQ(policyManager.getDefaultPolicyGroup(), "");

    ASSERT_FALSE(
            policyManager
                    .getDefaultPowerPolicyForState(kInvalidPowerPolicyGroupId, kExistingTransition)
                    .ok());
}

TEST_F(PolicyManagerTest, TestDefaultPowerPolicies) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);

    assertDefaultPolicies(policyManager);
}

TEST_F(PolicyManagerTest, TestValidXml_DefaultPowerPolicies) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    assertDefaultPolicies(policyManager);
}

TEST_F(PolicyManagerTest, TestInvalidPowerPolicyXml) {
    for (const auto& filename : kInvalidPowerPolicyXmlFiles) {
        PolicyManager policyManager;
        internal::PolicyManagerPeer policyManagerPeer(&policyManager);
        policyManagerPeer.expectInvalidPowerPolicyXML(filename);

        checkInvalidPolicies(policyManager);
    }
}

TEST_F(PolicyManagerTest, TestInvalidPowerPolicyGroupXml) {
    for (const auto& filename : kInvalidPowerPolicyGroupXmlFiles) {
        PolicyManager policyManager;
        internal::PolicyManagerPeer policyManagerPeer(&policyManager);
        policyManagerPeer.expectInvalidPowerPolicyXML(filename);

        checkInvalidPolicies(policyManager);
    }
}

TEST_F(PolicyManagerTest, TestInvalidSystemPowerPolicyXml) {
    for (const auto& filename : kInvalidSystemPowerPolicyXmlFiles) {
        PolicyManager policyManager;
        internal::PolicyManagerPeer policyManagerPeer(&policyManager);
        policyManagerPeer.expectInvalidPowerPolicyXML(filename);

        checkInvalidPolicies(policyManager);
    }
}

TEST_F(PolicyManagerTest, TestValidXml_PowerPolicyGroupAvailable) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    ASSERT_TRUE(policyManager.isPowerPolicyGroupAvailable(kValidPowerPolicyGroupId));
    ASSERT_FALSE(policyManager.isPowerPolicyGroupAvailable(kInvalidPowerPolicyGroupId));
}

TEST_F(PolicyManagerTest, TestSystemPowerPolicyAllOn) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    std::unordered_set<PowerComponent> enabledComponentSet;
    auto policyMeta = policyManager.getPowerPolicy("system_power_policy_all_on");

    ASSERT_TRUE(policyMeta.ok());

    CarPowerPolicyPtr systemPolicyDefault = policyMeta->powerPolicy;
    for (const auto& component : systemPolicyDefault->enabledComponents) {
        enabledComponentSet.insert(component);
    }
    for (const auto component : ::ndk::enum_range<PowerComponent>()) {
        if (component >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;  // skip custom components
        }
        ASSERT_GT(enabledComponentSet.count(component), static_cast<size_t>(0));
        enabledComponentSet.erase(component);
    }

    ASSERT_TRUE(enabledComponentSet.empty());
    ASSERT_TRUE(systemPolicyDefault->disabledComponents.empty());
}

TEST_F(PolicyManagerTest, TestGetCustomComponents) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyCustomComponentsXmlFile);

    const auto customComponents = policyManager.getCustomComponents();

    // Custom components defined in the XML are 1000, 1002, and 1003.
    ASSERT_THAT(customComponents, UnorderedElementsAre(1000, 1002, 1003));
}

TEST_F(PolicyManagerTest, TestGetRegisteredPolicies) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyPowerPoliciesOnlyXmlFile);
    std::unordered_map<std::string, CarPowerPolicy>
            expectedPolicies{{"expected_to_be_registered", kExistingPowerPolicy_ToBeRegistered},
                             {"policy_id_other_on", kExistingPowerPolicy_OtherOn},
                             {"policy_id_other_off", kExistingPowerPolicy_OtherOff},
                             {"policy_id_other_untouched", kExistingPowerPolicy_OtherUntouched},
                             {"policy_id_other_none", kExistingPowerPolicy_OtherNone},
                             {"system_power_policy_no_user_interaction",
                              kSystemPowerPolicyNoUserInteraction},
                             {"system_power_policy_suspend_prep", kSystemPowerPolicySuspendPrep},
                             {"system_power_policy_all_on", kSystemPowerPolicyAllOn},
                             {"system_power_policy_initial_on", kSystemPowerPolicyInitialOn}};

    const auto powerPolicies = policyManager.getRegisteredPolicies();

    ASSERT_TRUE(comparePolicies(powerPolicies, expectedPolicies));
}

TEST_F(PolicyManagerTest, TestDefinePowerPolicyGroup) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    const auto ret = policyManager.definePowerPolicyGroup("new_policy_group",
                                                          {"policy_id_other_off",
                                                           "policy_id_other_untouched"});

    ASSERT_TRUE(ret.ok()) << "New policy group should be defined";
}

TEST_F(PolicyManagerTest, TestDefinePowerPolicyGroup_doubleRegistration) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    const auto ret = policyManager.definePowerPolicyGroup("basic_policy_group",
                                                          {"policy_id_other_off",
                                                           "policy_id_other_untouched"});

    ASSERT_FALSE(ret.ok()) << "Policy group with the same ID cannot be defined";
}

TEST_F(PolicyManagerTest, TestDefinePowerPolicyGroup_unregisteredPowerPolicy) {
    PolicyManager policyManager;
    internal::PolicyManagerPeer policyManagerPeer(&policyManager);
    policyManagerPeer.expectValidPowerPolicyXML(kValidPowerPolicyXmlFile);

    const auto ret = policyManager.definePowerPolicyGroup("new_policy_group",
                                                          {"policy_id_other_off",
                                                           "unregistered_power_policy"});

    ASSERT_FALSE(ret.ok()) << "Policy group having unregistered power policy cannot be defined";
}

}  // namespace test
}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
