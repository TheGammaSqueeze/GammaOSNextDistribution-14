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

#include "VehicleBindingUtil.h"

#include <android/hardware/automotive/vehicle/2.0/types.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <hidl/Status.h>
#include <utils/SystemClock.h>

#include <AidlHalPropConfig.h>
#include <AidlHalPropValue.h>
#include <IHalPropConfig.h>
#include <IHalPropValue.h>
#include <IVhalClient.h>
#include <VehicleHalTypes.h>
#include <VehicleUtils.h>

#include <iterator>

namespace android {
namespace automotive {
namespace security {

namespace {

using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::android::frameworks::automotive::vhal::AidlHalPropConfig;
using ::android::frameworks::automotive::vhal::AidlHalPropValue;
using ::android::frameworks::automotive::vhal::ClientStatusError;
using ::android::frameworks::automotive::vhal::ErrorCode;
using ::android::frameworks::automotive::vhal::IHalPropConfig;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::ISubscriptionCallback;
using ::android::frameworks::automotive::vhal::ISubscriptionClient;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::frameworks::automotive::vhal::VhalClientResult;
using ::android::hardware::Void;

template <typename T>
using VhalReturn = android::hardware::Return<T>;

using ::testing::_;
using ::testing::DoAll;
using ::testing::ElementsAreArray;
using ::testing::NotNull;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::Test;

class MockVehicle : public IVhalClient {
public:
    bool isAidlVhal() override { return true; }

    MOCK_METHOD(std::unique_ptr<IHalPropValue>, createHalPropValue, (int32_t), (override));

    MOCK_METHOD(std::unique_ptr<IHalPropValue>, createHalPropValue, (int32_t, int32_t), (override));

    MOCK_METHOD(void, getValue, (const IHalPropValue&, std::shared_ptr<GetValueCallbackFunc>),
                (override));

    MOCK_METHOD(void, setValue, (const IHalPropValue&, std::shared_ptr<SetValueCallbackFunc>),
                (override));

    MOCK_METHOD(VhalClientResult<void>, addOnBinderDiedCallback,
                (std::shared_ptr<OnBinderDiedCallbackFunc>), (override));

    MOCK_METHOD(VhalClientResult<void>, removeOnBinderDiedCallback,
                (std::shared_ptr<OnBinderDiedCallbackFunc>), (override));

    MOCK_METHOD(VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>>, getAllPropConfigs,
                (), (override));

    MOCK_METHOD(VhalClientResult<std::vector<std::unique_ptr<IHalPropConfig>>>, getPropConfigs,
                (std::vector<int32_t>), (override));

    MOCK_METHOD(std::unique_ptr<ISubscriptionClient>, getSubscriptionClient,
                (std::shared_ptr<ISubscriptionCallback>), (override));
};

class MockCsrng : public Csrng {
public:
    MOCK_METHOD(bool, fill, (void*, size_t), (const override));
};

class MockExecutor : public Executor {
public:
    MOCK_METHOD(int, run, (const std::vector<std::string>&, int*), (const override));
};

class VehicleBindingUtilTests : public Test {
protected:
    void SetUp() override {
        ON_CALL(*mMockVehicle, createHalPropValue(_)).WillByDefault([](int32_t propId) {
            return std::make_unique<AidlHalPropValue>(propId);
        });

        ON_CALL(*mMockVehicle, createHalPropValue(_, _))
                .WillByDefault([](int32_t propId, int32_t areaId) {
                    return std::make_unique<AidlHalPropValue>(propId, areaId);
                });
    }

    void setMockVhalPropertySupported() {
        std::vector<int32_t> expectedProps = {
                toInt(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED)};

        EXPECT_CALL(*mMockVehicle, getPropConfigs(expectedProps))
                .WillOnce([](const std::vector<int32_t>&) {
                    std::vector<std::unique_ptr<IHalPropConfig>> configs;
                    VehiclePropConfig config;
                    configs.push_back(std::make_unique<AidlHalPropConfig>(std::move(config)));
                    return std::move(configs);
                });
    }

    void setMockVhalPropertyValue(const std::vector<uint8_t>& seed) {
        EXPECT_CALL(*mMockVehicle, getValue(_, _))
                .WillOnce(
                        [seed](const IHalPropValue& propValue,
                               const std::shared_ptr<MockVehicle::GetValueCallbackFunc>& callback) {
                            EXPECT_EQ(propValue.getPropId(),
                                      toInt(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED));
                            std::unique_ptr<IHalPropValue> value =
                                    std::make_unique<AidlHalPropValue>(propValue.getPropId());
                            value->setByteValues(seed);
                            (*callback)(std::move(value));
                        });
    }

    void setTestRandomness(const char seed[SEED_BYTE_SIZE]) {
        EXPECT_CALL(mMockCsrng, fill(NotNull(), SEED_BYTE_SIZE))
                .WillOnce([seed](void* buf, size_t) {
                    memcpy(buf, seed, SEED_BYTE_SIZE);
                    return true;
                });
    }

    static std::vector<uint8_t> toVector(const char seed[SEED_BYTE_SIZE]) {
        return {seed, seed + SEED_BYTE_SIZE};
    }

    static std::vector<std::string> makeVdcArgs() {
        return {"/system/bin/vdc", "cryptfs", "bindkeys"};
    }

    std::shared_ptr<MockVehicle> mMockVehicle = std::make_shared<MockVehicle>();
    MockExecutor mMockExecutor;
    MockCsrng mMockCsrng;
};

// Verify that we fail as expected if the VHAL property is not supported. This
// is not necessarily an error, and is expected on platforms that don't
// implement the feature.
TEST_F(VehicleBindingUtilTests, VhalPropertyUnsupported) {
    std::vector<int32_t> expectedProps = {toInt(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED)};
    EXPECT_CALL(*mMockVehicle, getPropConfigs(expectedProps))
            .WillOnce([](const std::vector<int32_t>&) {
                std::vector<std::unique_ptr<IHalPropConfig>> configs;
                return std::move(configs);
            });

    EXPECT_EQ(BindingStatus::NOT_SUPPORTED,
              setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

// Verify that we properly handle an attempt to generate a random seed.
TEST_F(VehicleBindingUtilTests, GetRandomnessFails) {
    setMockVhalPropertySupported();
    setMockVhalPropertyValue({});
    EXPECT_CALL(mMockCsrng, fill(_, SEED_BYTE_SIZE)).WillOnce(Return(false));
    EXPECT_EQ(BindingStatus::ERROR, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

// Verify that we properly handle an attempt to generate a random seed.
TEST_F(VehicleBindingUtilTests, GetSeedVhalPropertyFails) {
    setMockVhalPropertySupported();

    EXPECT_CALL(*mMockVehicle, getValue(_, _))
            .WillOnce([](const IHalPropValue& propValue,
                         const std::shared_ptr<MockVehicle::GetValueCallbackFunc>& callback) {
                EXPECT_EQ(propValue.getPropId(),
                          toInt(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED));
                (*callback)(ClientStatusError(StatusCode::NOT_AVAILABLE));
            });
    EXPECT_EQ(BindingStatus::ERROR, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

TEST_F(VehicleBindingUtilTests, SetSeedVhalPropertyFails) {
    setMockVhalPropertySupported();
    setMockVhalPropertyValue({});
    setTestRandomness("I am not random");

    EXPECT_CALL(*mMockVehicle, setValue(_, _))
            .WillOnce([](const IHalPropValue&,
                         const std::shared_ptr<MockVehicle::SetValueCallbackFunc>& callback) {
                (*callback)(ClientStatusError(StatusCode::NOT_AVAILABLE));
            });

    EXPECT_EQ(BindingStatus::ERROR, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

TEST_F(VehicleBindingUtilTests, SetSeedWithNewRandomSeed) {
    setMockVhalPropertySupported();
    setMockVhalPropertyValue({});
    constexpr char SEED[SEED_BYTE_SIZE] = "Seed Value Here";
    setTestRandomness(SEED);

    EXPECT_CALL(*mMockVehicle, setValue(_, _))
            .WillOnce([SEED](const IHalPropValue& value,
                             const std::shared_ptr<MockVehicle::SetValueCallbackFunc>& callback) {
                EXPECT_EQ(value.getPropId(),
                          toInt(VehicleProperty::STORAGE_ENCRYPTION_BINDING_SEED));
                EXPECT_THAT(value.getByteValues(), testing::ElementsAreArray(SEED));
                (*callback)({});
            });

    EXPECT_CALL(mMockExecutor, run(ElementsAreArray(makeVdcArgs()), _)).WillOnce(Return(0));

    EXPECT_EQ(BindingStatus::OK, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

TEST_F(VehicleBindingUtilTests, SetSeedWithExistingProperty) {
    setMockVhalPropertySupported();
    const auto SEED = toVector("16 bytes of seed");
    setMockVhalPropertyValue(SEED);
    EXPECT_CALL(mMockExecutor, run(ElementsAreArray(makeVdcArgs()), _)).WillOnce(Return(0));
    EXPECT_EQ(BindingStatus::OK, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

TEST_F(VehicleBindingUtilTests, SetSeedVdcExecFails) {
    setMockVhalPropertySupported();
    const auto SEED = toVector("abcdefghijklmnop");
    setMockVhalPropertyValue(SEED);
    EXPECT_CALL(mMockExecutor, run(ElementsAreArray(makeVdcArgs()), _)).WillOnce(Return(-1));
    EXPECT_EQ(BindingStatus::ERROR, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

TEST_F(VehicleBindingUtilTests, SetSeedVdcExitsWithNonZeroStatus) {
    setMockVhalPropertySupported();
    const auto SEED = toVector("1123581321345589");
    setMockVhalPropertyValue(SEED);
    EXPECT_CALL(mMockExecutor, run(ElementsAreArray(makeVdcArgs()), _))
            .WillOnce(DoAll(SetArgPointee<1>(-1), Return(0)));
    EXPECT_EQ(BindingStatus::ERROR, setVehicleBindingSeed(mMockVehicle, mMockExecutor, mMockCsrng));
}

}  // namespace
}  // namespace security
}  // namespace automotive
}  // namespace android
