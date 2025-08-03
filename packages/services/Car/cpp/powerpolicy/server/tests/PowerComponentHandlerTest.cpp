/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "PowerComponentHandler.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <memory>
#include <string>
#include <tuple>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;

using ::testing::UnorderedElementsAreArray;

namespace {

static constexpr int32_t CUSTOM_COMPONENT_ID_1000 = 1000;
static constexpr int32_t CUSTOM_COMPONENT_ID_1002 = 1002;

CarPowerPolicyPtr createPolicy(const std::string& policyId,
                               const std::vector<PowerComponent>& enabledComponents,
                               const std::vector<PowerComponent>& disabledComponents,
                               const std::vector<int>& enabledCustomComponents,
                               const std::vector<int>& disabledCustomComponents) {
    CarPowerPolicyPtr policy = std::make_shared<CarPowerPolicy>();
    policy->policyId = policyId;
    policy->enabledComponents = enabledComponents;
    policy->disabledComponents = disabledComponents;
    policy->enabledCustomComponents = enabledCustomComponents;
    policy->disabledCustomComponents = disabledCustomComponents;
    return policy;
}

void assertEqual(const CarPowerPolicyPtr& left, const CarPowerPolicyPtr& right) {
    ASSERT_EQ(left->policyId, right->policyId);
    EXPECT_THAT(left->enabledComponents, UnorderedElementsAreArray(right->enabledComponents));
    EXPECT_THAT(left->disabledComponents, UnorderedElementsAreArray(right->disabledComponents));
    EXPECT_THAT(left->enabledCustomComponents,
                UnorderedElementsAreArray(right->enabledCustomComponents));
    EXPECT_THAT(left->disabledCustomComponents,
                UnorderedElementsAreArray(right->disabledCustomComponents));
}

}  // namespace

class PowerComponentHandlerTest : public ::testing::Test {
public:
    PowerComponentHandlerTest() { handler.init(); }

    PowerComponentHandler handler;
};

TEST_F(PowerComponentHandlerTest, TestInitialPowerComponentStates) {
    CarPowerPolicyPtr policy = handler.getAccumulatedPolicy();
    std::vector<PowerComponent> allComponents;
    for (auto componentId : ::ndk::enum_range<PowerComponent>()) {
        if (componentId >= PowerComponent::MINIMUM_CUSTOM_COMPONENT_VALUE) {
            continue;
        }
        allComponents.push_back(componentId);
    }

    EXPECT_THAT(allComponents, UnorderedElementsAreArray(policy->disabledComponents));
}

TEST_F(PowerComponentHandlerTest, TestGetPowerComponentState) {
    CarPowerPolicyPtr policy =
            createPolicy("test_policy", {PowerComponent::WIFI, PowerComponent::NFC},
                         {PowerComponent::AUDIO, PowerComponent::DISPLAY}, {}, {});
    handler.applyPowerPolicy(policy);

    ASSERT_TRUE(*handler.getPowerComponentState(PowerComponent::WIFI));
    ASSERT_TRUE(*handler.getPowerComponentState(PowerComponent::NFC));
    ASSERT_FALSE(*handler.getPowerComponentState(PowerComponent::AUDIO));
    ASSERT_FALSE(*handler.getPowerComponentState(PowerComponent::DISPLAY));
}

TEST_F(PowerComponentHandlerTest, TestGetCustomPowerComponentState) {
    CarPowerPolicyPtr policy =
            createPolicy("test_policy", {PowerComponent::WIFI, PowerComponent::NFC},
                         {PowerComponent::AUDIO, PowerComponent::DISPLAY},
                         {CUSTOM_COMPONENT_ID_1000}, {CUSTOM_COMPONENT_ID_1002});
    handler.applyPowerPolicy(policy);

    ASSERT_TRUE(*handler.getCustomPowerComponentState(CUSTOM_COMPONENT_ID_1000));
    ASSERT_FALSE(*handler.getCustomPowerComponentState(CUSTOM_COMPONENT_ID_1002));
}

TEST_F(PowerComponentHandlerTest, TestApplyPowerPolicy_multipleTimes) {
    std::vector<std::tuple<std::string, std::vector<PowerComponent>, std::vector<PowerComponent>>>
            testCases = {
                    {"test_policy1", {PowerComponent::WIFI}, {PowerComponent::AUDIO}},
                    {"test_policy2",
                     {PowerComponent::WIFI, PowerComponent::DISPLAY},
                     {PowerComponent::NFC}},
                    {"test_policy3",
                     {PowerComponent::CPU, PowerComponent::INPUT},
                     {PowerComponent::WIFI}},
                    {"test_policy4", {PowerComponent::MEDIA, PowerComponent::AUDIO}, {}},
            };
    CarPowerPolicyPtr expectedPolicy =
            createPolicy("test_policy4",
                         {PowerComponent::AUDIO, PowerComponent::MEDIA, PowerComponent::DISPLAY,
                          PowerComponent::INPUT, PowerComponent::CPU},
                         {PowerComponent::BLUETOOTH, PowerComponent::WIFI, PowerComponent::CELLULAR,
                          PowerComponent::ETHERNET, PowerComponent::PROJECTION, PowerComponent::NFC,
                          PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                          PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                          PowerComponent::MICROPHONE},
                         {}, {});

    for (const auto& tc : testCases) {
        auto [policyId, enabledComponents, disabledComponents] = tc;
        CarPowerPolicyPtr policy =
                createPolicy(policyId, enabledComponents, disabledComponents, {}, {});
        handler.applyPowerPolicy(policy);
    }

    ASSERT_NO_FATAL_FAILURE(assertEqual(expectedPolicy, handler.getAccumulatedPolicy()));
}

TEST_F(PowerComponentHandlerTest, TestApplyPowerPolicy_change_policies_with_custom_components) {
    CarPowerPolicyPtr policy =
            createPolicy("test_policy1", {PowerComponent::WIFI, PowerComponent::AUDIO}, {}, {}, {});

    handler.applyPowerPolicy(policy);

    CarPowerPolicyPtr expectedPolicy =
            createPolicy("test_policy1", {PowerComponent::WIFI, PowerComponent::AUDIO},
                         {PowerComponent::MEDIA, PowerComponent::DISPLAY, PowerComponent::INPUT,
                          PowerComponent::CPU, PowerComponent::BLUETOOTH, PowerComponent::CELLULAR,
                          PowerComponent::ETHERNET, PowerComponent::PROJECTION, PowerComponent::NFC,
                          PowerComponent::VOICE_INTERACTION, PowerComponent::VISUAL_INTERACTION,
                          PowerComponent::TRUSTED_DEVICE_DETECTION, PowerComponent::LOCATION,
                          PowerComponent::MICROPHONE},
                         {}, {});
    ASSERT_NO_FATAL_FAILURE(assertEqual(expectedPolicy, handler.getAccumulatedPolicy()));

    policy = createPolicy("test_policy2", {}, {}, {CUSTOM_COMPONENT_ID_1002}, {});

    handler.applyPowerPolicy(policy);

    expectedPolicy.get()->policyId = "test_policy2";
    expectedPolicy.get()->enabledCustomComponents = {CUSTOM_COMPONENT_ID_1002};

    ASSERT_NO_FATAL_FAILURE(assertEqual(handler.getAccumulatedPolicy(), expectedPolicy));

    policy = createPolicy("test_policy3", {}, {},
                          {CUSTOM_COMPONENT_ID_1002, CUSTOM_COMPONENT_ID_1000}, {});

    handler.applyPowerPolicy(policy);

    expectedPolicy.get()->policyId = "test_policy3";
    expectedPolicy.get()->enabledCustomComponents = {CUSTOM_COMPONENT_ID_1002,
                                                     CUSTOM_COMPONENT_ID_1000};

    ASSERT_NO_FATAL_FAILURE(assertEqual(handler.getAccumulatedPolicy(), expectedPolicy));

    policy = createPolicy("test_policy4", {}, {}, {CUSTOM_COMPONENT_ID_1000},
                          {CUSTOM_COMPONENT_ID_1002});

    handler.applyPowerPolicy(policy);

    expectedPolicy.get()->policyId = "test_policy4";
    expectedPolicy.get()->enabledCustomComponents = {CUSTOM_COMPONENT_ID_1000};
    expectedPolicy.get()->disabledCustomComponents = {CUSTOM_COMPONENT_ID_1002};

    ASSERT_NO_FATAL_FAILURE(assertEqual(handler.getAccumulatedPolicy(), expectedPolicy));
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
