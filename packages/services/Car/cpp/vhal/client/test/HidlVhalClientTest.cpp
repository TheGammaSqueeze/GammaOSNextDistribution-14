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

#include "HidlHalPropValue.h"
#include "HidlVhalClient.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <android-base/result.h>
#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>
#include <gtest/gtest.h>
#include <utils/StrongPointer.h>

#include <VehicleUtils.h>

#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {
namespace hidl_test {

using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::android::sp;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::automotive::vehicle::toInt;
using ::android::hardware::automotive::vehicle::V2_0::IVehicle;
using ::android::hardware::automotive::vehicle::V2_0::IVehicleCallback;
using ::android::hardware::automotive::vehicle::V2_0::StatusCode;
using ::android::hardware::automotive::vehicle::V2_0::SubscribeFlags;
using ::android::hardware::automotive::vehicle::V2_0::SubscribeOptions;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropConfig;
using ::android::hardware::automotive::vehicle::V2_0::VehiclePropValue;

class MockVhal final : public IVehicle {
public:
    Return<void> getAllPropConfigs(IVehicle::getAllPropConfigs_cb callback) override {
        callback(mPropConfigs);
        return {};
    }

    Return<void> getPropConfigs(const hidl_vec<int32_t>& props,
                                IVehicle::getPropConfigs_cb callback) override {
        mGetPropConfigsProps = props;
        callback(mStatus, mPropConfigs);
        return {};
    }

    Return<void> get(const VehiclePropValue& requestPropValue, IVehicle::get_cb callback) override {
        mRequestPropValue = requestPropValue;
        callback(mStatus, mPropValue);
        return {};
    }

    Return<StatusCode> set(const VehiclePropValue& value) override {
        mRequestPropValue = value;
        return mStatus;
    }

    Return<StatusCode> subscribe(const sp<IVehicleCallback>& callback,
                                 const hidl_vec<SubscribeOptions>& options) override {
        mSubscribedCallback = callback;
        mSubscribeOptions = options;
        return mStatus;
    }

    Return<StatusCode> unsubscribe([[maybe_unused]] const sp<IVehicleCallback>& callback,
                                   int32_t propId) override {
        mUnsubscribedPropId = propId;
        return mStatus;
    }

    Return<void> debugDump([[maybe_unused]] IVehicle::debugDump_cb callback) override { return {}; }

    // Test functions

    void setPropConfigs(std::vector<VehiclePropConfig> configs) { mPropConfigs = configs; }

    void setStatus(StatusCode status) { mStatus = status; }

    void setVehiclePropValue(VehiclePropValue value) { mPropValue = value; }

    std::vector<int32_t> getGetPropConfigsProps() { return mGetPropConfigsProps; }

    VehiclePropValue getRequestPropValue() { return mRequestPropValue; }

    std::vector<SubscribeOptions> getSubscribeOptions() { return mSubscribeOptions; }

    int32_t getUnsubscribedPropId() { return mUnsubscribedPropId; }

    void triggerOnPropertyEvent(const std::vector<VehiclePropValue>& values) {
        mSubscribedCallback->onPropertyEvent(values);
    }

    void triggerSetErrorEvent(StatusCode status, int32_t propId, int32_t areaId) {
        mSubscribedCallback->onPropertySetError(status, propId, areaId);
    }

private:
    std::vector<VehiclePropConfig> mPropConfigs;
    StatusCode mStatus = StatusCode::OK;
    VehiclePropValue mPropValue;

    std::vector<int32_t> mGetPropConfigsProps;
    VehiclePropValue mRequestPropValue;
    sp<IVehicleCallback> mSubscribedCallback;
    std::vector<SubscribeOptions> mSubscribeOptions;
    int32_t mUnsubscribedPropId;
};

class MockSubscriptionCallback final : public ISubscriptionCallback {
public:
    void onPropertyEvent(const std::vector<std::unique_ptr<IHalPropValue>>& values) override {
        for (const auto& value : values) {
            mEventPropIds.push_back(value->getPropId());
        }
    }
    void onPropertySetError(const std::vector<HalPropError>& errors) override { mErrors = errors; }

    std::vector<int32_t> getEventPropIds() { return mEventPropIds; }

    std::vector<HalPropError> getErrors() { return mErrors; }

private:
    std::vector<int32_t> mEventPropIds;
    std::vector<HalPropError> mErrors;
};

class HidlVhalClientTest : public ::testing::Test {
protected:
    constexpr static int32_t TEST_PROP_ID = 1;
    constexpr static int32_t TEST_AREA_ID = 2;
    constexpr static int32_t TEST_PROP_ID_2 = 3;
    constexpr static int32_t TEST_AREA_ID_2 = 4;

    const VehiclePropValue TEST_VALUE{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
            .value.int32Values = {1},
    };

    void SetUp() override {
        mVhal = new MockVhal();
        mVhalClient = std::make_unique<HidlVhalClient>(mVhal);
    }

    MockVhal* getVhal() { return mVhal.get(); }

    HidlVhalClient* getClient() { return mVhalClient.get(); }

    void triggerBinderDied() { mVhalClient->onBinderDied(); }

private:
    sp<MockVhal> mVhal;
    std::unique_ptr<HidlVhalClient> mVhalClient;
};

TEST_F(HidlVhalClientTest, testIsAidl) {
    ASSERT_FALSE(getClient()->isAidlVhal());
}

TEST_F(HidlVhalClientTest, testGetValue) {
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;
    auto callback = std::make_shared<HidlVhalClient::GetValueCallbackFunc>(
            [resultPtr, gotResultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                *resultPtr = std::move(r);
                *gotResultPtr = true;
            });
    getVhal()->setVehiclePropValue(TEST_VALUE);

    getClient()->getValue(HidlHalPropValue(TEST_PROP_ID, TEST_AREA_ID), callback);

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getRequestPropValue().prop, TEST_PROP_ID);
    ASSERT_EQ(getVhal()->getRequestPropValue().areaId, TEST_AREA_ID);
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getStatus(), VehiclePropertyStatus::AVAILABLE);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(HidlVhalClientTest, testGetValueUnavailableStatus) {
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;
    auto callback = std::make_shared<HidlVhalClient::GetValueCallbackFunc>(
            [resultPtr, gotResultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                *resultPtr = std::move(r);
                *gotResultPtr = true;
            });
    getVhal()->setVehiclePropValue(VehiclePropValue{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
            .status = android::hardware::automotive::vehicle::V2_0::VehiclePropertyStatus::
                    UNAVAILABLE,
    });

    getClient()->getValue(HidlHalPropValue(TEST_PROP_ID, TEST_AREA_ID), callback);

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getRequestPropValue().prop, TEST_PROP_ID);
    ASSERT_EQ(getVhal()->getRequestPropValue().areaId, TEST_AREA_ID);
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getStatus(), VehiclePropertyStatus::UNAVAILABLE);
}

TEST_F(HidlVhalClientTest, testGetValueError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;
    auto callback = std::make_shared<HidlVhalClient::GetValueCallbackFunc>(
            [resultPtr, gotResultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                *resultPtr = std::move(r);
                *gotResultPtr = true;
            });

    getClient()->getValue(HidlHalPropValue(TEST_PROP_ID, TEST_AREA_ID), callback);

    ASSERT_TRUE(gotResult);
    ASSERT_FALSE(result.ok());
}

TEST_F(HidlVhalClientTest, testSetValue) {
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;
    auto callback = std::make_shared<HidlVhalClient::SetValueCallbackFunc>(
            [resultPtr, gotResultPtr](VhalClientResult<void> r) {
                *resultPtr = std::move(r);
                *gotResultPtr = true;
            });

    getClient()->setValue(HidlHalPropValue(TEST_PROP_ID, TEST_AREA_ID), callback);

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getRequestPropValue().prop, TEST_PROP_ID);
    ASSERT_EQ(getVhal()->getRequestPropValue().areaId, TEST_AREA_ID);
    ASSERT_TRUE(result.ok());
}
TEST_F(HidlVhalClientTest, testSetValueError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;
    auto callback = std::make_shared<HidlVhalClient::SetValueCallbackFunc>(
            [resultPtr, gotResultPtr](VhalClientResult<void> r) {
                *resultPtr = std::move(r);
                *gotResultPtr = true;
            });

    getClient()->setValue(HidlHalPropValue(TEST_PROP_ID, TEST_AREA_ID), callback);

    ASSERT_TRUE(gotResult);
    ASSERT_FALSE(result.ok());
}

TEST_F(HidlVhalClientTest, testAddOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    getClient()->addOnBinderDiedCallback(std::make_shared<HidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; }));
    getClient()->addOnBinderDiedCallback(std::make_shared<HidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; }));
    triggerBinderDied();

    ASSERT_TRUE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);
}

TEST_F(HidlVhalClientTest, testRemoveOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    auto callbackOne = std::make_shared<HidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; });
    auto callbackTwo = std::make_shared<HidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; });
    getClient()->addOnBinderDiedCallback(callbackOne);
    getClient()->addOnBinderDiedCallback(callbackTwo);
    getClient()->removeOnBinderDiedCallback(callbackOne);
    triggerBinderDied();

    ASSERT_FALSE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);
}

TEST_F(HidlVhalClientTest, testGetAllPropConfigs) {
    getVhal()->setPropConfigs({
            VehiclePropConfig{
                    .prop = TEST_PROP_ID,
                    .areaConfigs = {{
                                            .areaId = TEST_AREA_ID,
                                            .minInt32Value = 0,
                                            .maxInt32Value = 1,
                                    },
                                    {
                                            .areaId = TEST_AREA_ID_2,
                                            .minInt32Value = 2,
                                            .maxInt32Value = 3,
                                    }},
            },
            VehiclePropConfig{
                    .prop = TEST_PROP_ID_2,
            },
    });

    auto result = getClient()->getAllPropConfigs();

    ASSERT_TRUE(result.ok());
    std::vector<std::unique_ptr<IHalPropConfig>> configs = std::move(result.value());

    ASSERT_EQ(configs.size(), static_cast<size_t>(2));
    ASSERT_EQ(configs[0]->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(configs[0]->getAreaConfigSize(), static_cast<size_t>(2));

    const std::unique_ptr<IHalAreaConfig>& areaConfig0 = configs[0]->getAreaConfigs()[0];
    ASSERT_EQ(areaConfig0->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(areaConfig0->getMinInt32Value(), 0);
    ASSERT_EQ(areaConfig0->getMaxInt32Value(), 1);

    const std::unique_ptr<IHalAreaConfig>& areaConfig1 = configs[0]->getAreaConfigs()[1];
    ASSERT_EQ(areaConfig1->getAreaId(), TEST_AREA_ID_2);
    ASSERT_EQ(areaConfig1->getMinInt32Value(), 2);
    ASSERT_EQ(areaConfig1->getMaxInt32Value(), 3);

    ASSERT_EQ(configs[1]->getPropId(), TEST_PROP_ID_2);
    ASSERT_EQ(configs[1]->getAreaConfigSize(), static_cast<size_t>(0));
}

TEST_F(HidlVhalClientTest, testGetPropConfigs) {
    getVhal()->setPropConfigs({
            VehiclePropConfig{
                    .prop = TEST_PROP_ID,
                    .areaConfigs = {{
                                            .areaId = TEST_AREA_ID,
                                            .minInt32Value = 0,
                                            .maxInt32Value = 1,
                                    },
                                    {
                                            .areaId = TEST_AREA_ID_2,
                                            .minInt32Value = 2,
                                            .maxInt32Value = 3,
                                    }},
            },
            VehiclePropConfig{
                    .prop = TEST_PROP_ID_2,
            },
    });

    std::vector<int32_t> propIds = {TEST_PROP_ID, TEST_PROP_ID_2};
    auto result = getClient()->getPropConfigs(propIds);

    ASSERT_EQ(getVhal()->getGetPropConfigsProps(), propIds);
    ASSERT_TRUE(result.ok());
    std::vector<std::unique_ptr<IHalPropConfig>> configs = std::move(result.value());

    ASSERT_EQ(configs.size(), static_cast<size_t>(2));
    ASSERT_EQ(configs[0]->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(configs[0]->getAreaConfigSize(), static_cast<size_t>(2));

    const std::unique_ptr<IHalAreaConfig>& areaConfig0 = configs[0]->getAreaConfigs()[0];
    ASSERT_EQ(areaConfig0->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(areaConfig0->getMinInt32Value(), 0);
    ASSERT_EQ(areaConfig0->getMaxInt32Value(), 1);
    ASSERT_FALSE(areaConfig0->isVariableUpdateRateSupported());

    const std::unique_ptr<IHalAreaConfig>& areaConfig1 = configs[0]->getAreaConfigs()[1];
    ASSERT_EQ(areaConfig1->getAreaId(), TEST_AREA_ID_2);
    ASSERT_EQ(areaConfig1->getMinInt32Value(), 2);
    ASSERT_EQ(areaConfig1->getMaxInt32Value(), 3);
    ASSERT_FALSE(areaConfig1->isVariableUpdateRateSupported());

    ASSERT_EQ(configs[1]->getPropId(), TEST_PROP_ID_2);
    ASSERT_EQ(configs[1]->getAreaConfigSize(), static_cast<size_t>(0));
}

TEST_F(HidlVhalClientTest, testGetPropConfigsError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    std::vector<int32_t> propIds = {TEST_PROP_ID, TEST_PROP_ID_2};
    auto result = getClient()->getPropConfigs(propIds);

    ASSERT_FALSE(result.ok());
}

TEST_F(HidlVhalClientTest, testSubscribe) {
    std::vector<::aidl::android::hardware::automotive::vehicle::SubscribeOptions> options = {
            {
                    .propId = TEST_PROP_ID,
                    .areaIds = {TEST_AREA_ID},
                    .sampleRate = 1.0,
            },
            {
                    .propId = TEST_PROP_ID_2,
                    .sampleRate = 2.0,
            },
    };
    std::vector<SubscribeOptions> hidlOptions = {
            {
                    .propId = TEST_PROP_ID,
                    .flags = SubscribeFlags::EVENTS_FROM_CAR,
                    .sampleRate = 1.0,
            },
            {
                    .propId = TEST_PROP_ID_2,
                    .flags = SubscribeFlags::EVENTS_FROM_CAR,
                    .sampleRate = 2.0,
            },
    };

    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->subscribe(options);

    ASSERT_TRUE(result.ok());
    ASSERT_EQ(getVhal()->getSubscribeOptions(), hidlOptions);

    getVhal()->triggerOnPropertyEvent(std::vector<VehiclePropValue>{
            {
                    .prop = TEST_PROP_ID,
                    .areaId = TEST_AREA_ID,
                    .value.int32Values = {1},
            },
    });

    ASSERT_EQ(callback->getEventPropIds(), std::vector<int32_t>({TEST_PROP_ID}));

    getVhal()->triggerSetErrorEvent(StatusCode::INTERNAL_ERROR, TEST_PROP_ID, TEST_AREA_ID);

    auto errors = callback->getErrors();
    ASSERT_EQ(errors.size(), static_cast<size_t>(1));
    ASSERT_EQ(errors[0].propId, TEST_PROP_ID);
    ASSERT_EQ(errors[0].areaId, TEST_AREA_ID);
    ASSERT_EQ(errors[0].status,
              ::aidl::android::hardware::automotive::vehicle::StatusCode::INTERNAL_ERROR);
}

TEST_F(HidlVhalClientTest, testSubscribeError) {
    std::vector<::aidl::android::hardware::automotive::vehicle::SubscribeOptions> options = {
            {
                    .propId = TEST_PROP_ID,
                    .areaIds = {TEST_AREA_ID},
                    .sampleRate = 1.0,
            },
            {
                    .propId = TEST_PROP_ID_2,
                    .sampleRate = 2.0,
            },
    };

    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);
    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->subscribe(options);

    ASSERT_FALSE(result.ok());
}

TEST_F(HidlVhalClientTest, testUnubscribe) {
    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->unsubscribe({TEST_PROP_ID});

    ASSERT_TRUE(result.ok());
    ASSERT_EQ(getVhal()->getUnsubscribedPropId(), TEST_PROP_ID);
}

TEST_F(HidlVhalClientTest, testUnubscribeError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);
    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->unsubscribe({TEST_PROP_ID});

    ASSERT_FALSE(result.ok());
}

TEST_F(HidlVhalClientTest, testGetRemoteInterfaceVersion) {
    // getRemoteInterfaceVersion will always return 0 for HIDL client.
    ASSERT_EQ(getClient()->getRemoteInterfaceVersion(), 0);
}

TEST_F(HidlVhalClientTest, testHidlHalPropValueClone_valueIsTheSame) {
    VehiclePropValue testProp{.prop = TEST_PROP_ID,
                              .areaId = TEST_AREA_ID,
                              .value = {
                                      .int32Values = {1, 2},
                                      .floatValues = {1.1, 2.2},
                              }};
    auto testPropCopy = testProp;
    std::unique_ptr<IHalPropValue> halPropValue =
            std::make_unique<HidlHalPropValue>(std::move(testPropCopy));
    auto halPropValueClone = halPropValue->clone();

    EXPECT_EQ(halPropValueClone->getPropId(), TEST_PROP_ID);
    EXPECT_EQ(halPropValueClone->getAreaId(), TEST_AREA_ID);
    EXPECT_EQ(halPropValueClone->getInt32Values(), std::vector<int32_t>({1, 2}));
    EXPECT_EQ(halPropValueClone->getFloatValues(), std::vector<float>({1.1, 2.2}));
}

TEST_F(HidlVhalClientTest, testHidlHalPropValueClone_modifyCloneDoesNotAffectOrig) {
    std::vector<int32_t> int32Values1 = {1, 2};
    std::vector<float> floatValues1 = {1.1, 2.2};
    std::vector<int32_t> int32Values2 = {5, 4, 3, 2, 1};
    std::vector<float> floatValues2 = {3.3, 2.2, 1.1};

    VehiclePropValue testProp{.prop = TEST_PROP_ID,
                              .areaId = TEST_AREA_ID,
                              .value = {
                                      .int32Values = int32Values1,
                                      .floatValues = floatValues1,
                              }};
    auto testPropCopy = testProp;
    std::unique_ptr<IHalPropValue> halPropValue =
            std::make_unique<HidlHalPropValue>(std::move(testPropCopy));
    auto halPropValueClone = halPropValue->clone();

    halPropValueClone->setInt32Values(int32Values2);
    halPropValueClone->setFloatValues(floatValues2);

    EXPECT_EQ(halPropValue->getInt32Values(), int32Values1);
    EXPECT_EQ(halPropValue->getFloatValues(), floatValues1);
    EXPECT_EQ(halPropValueClone->getInt32Values(), int32Values2);
    EXPECT_EQ(halPropValueClone->getFloatValues(), floatValues2);
}

}  // namespace hidl_test
}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
