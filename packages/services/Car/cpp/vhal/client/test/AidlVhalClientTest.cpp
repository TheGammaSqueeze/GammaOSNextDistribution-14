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

#include <aidl/android/hardware/automotive/vehicle/BnVehicle.h>
#include <android/binder_ibinder.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <AidlHalPropValue.h>
#include <AidlVhalClient.h>
#include <VehicleHalTypes.h>
#include <VehicleUtils.h>

#include <atomic>
#include <condition_variable>  // NOLINT
#include <mutex>               // NOLINT
#include <thread>              // NOLINT

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {
namespace aidl_test {

using ::android::hardware::automotive::vehicle::toInt;

using ::aidl::android::hardware::automotive::vehicle::BnVehicle;
using ::aidl::android::hardware::automotive::vehicle::GetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::GetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::GetValueResult;
using ::aidl::android::hardware::automotive::vehicle::GetValueResults;
using ::aidl::android::hardware::automotive::vehicle::IVehicle;
using ::aidl::android::hardware::automotive::vehicle::IVehicleCallback;
using ::aidl::android::hardware::automotive::vehicle::RawPropValues;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::SetValueResult;
using ::aidl::android::hardware::automotive::vehicle::SetValueResults;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfigs;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropError;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropErrors;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValues;

using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::testing::Gt;

class MockVhal final : public BnVehicle {
public:
    using CallbackType = std::shared_ptr<IVehicleCallback>;

    ~MockVhal() {
        std::unique_lock<std::mutex> lk(mLock);
        mCv.wait_for(lk, std::chrono::milliseconds(1000), [this] { return mThreadCount == 0; });
    }

    ScopedAStatus getAllPropConfigs(VehiclePropConfigs* returnConfigs) override {
        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        returnConfigs->payloads = mPropConfigs;
        return ScopedAStatus::ok();
    }

    ScopedAStatus getValues(const CallbackType& callback,
                            const GetValueRequests& requests) override {
        mGetValueRequests = requests.payloads;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        if (mWaitTimeInMs == 0) {
            callback->onGetValues(GetValueResults{.payloads = mGetValueResults});
        } else {
            mThreadCount++;
            std::thread t([this, callback]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(mWaitTimeInMs));
                callback->onGetValues(GetValueResults{.payloads = mGetValueResults});
                mThreadCount--;
                mCv.notify_one();
            });
            // Detach the thread here so we do not have to maintain the thread object. mThreadCount
            // and mCv make sure we wait for all threads to end before we exit.
            t.detach();
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus setValues(const CallbackType& callback,
                            const SetValueRequests& requests) override {
        mSetValueRequests = requests.payloads;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        if (mWaitTimeInMs == 0) {
            callback->onSetValues(SetValueResults{.payloads = mSetValueResults});
        } else {
            mThreadCount++;
            std::thread t([this, callback]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(mWaitTimeInMs));
                callback->onSetValues(SetValueResults{.payloads = mSetValueResults});
                mThreadCount--;
                mCv.notify_one();
            });
            // Detach the thread here so we do not have to maintain the thread object. mThreadCount
            // and mCv make sure we wait for all threads to end before we exit.
            t.detach();
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus getPropConfigs(const std::vector<int32_t>& props,
                                 VehiclePropConfigs* returnConfigs) override {
        mGetPropConfigPropIds = props;
        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        returnConfigs->payloads = mPropConfigs;
        return ScopedAStatus::ok();
    }

    ScopedAStatus subscribe(const CallbackType& callback,
                            const std::vector<SubscribeOptions>& options,
                            [[maybe_unused]] int32_t maxSharedMemoryFileCount) override {
        mSubscriptionCallback = callback;
        mSubscriptionOptions = options;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus unsubscribe([[maybe_unused]] const CallbackType& callback,
                              const std::vector<int32_t>& propIds) override {
        mUnsubscribePropIds = propIds;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus returnSharedMemory([[maybe_unused]] const CallbackType& callback,
                                     [[maybe_unused]] int64_t sharedMemoryId) override {
        return ScopedAStatus::ok();
    }

    // Test Functions

    void setGetValueResults(std::vector<GetValueResult> results) { mGetValueResults = results; }

    std::vector<GetValueRequest> getGetValueRequests() { return mGetValueRequests; }

    void setSetValueResults(std::vector<SetValueResult> results) { mSetValueResults = results; }

    std::vector<SetValueRequest> getSetValueRequests() { return mSetValueRequests; }

    void setWaitTimeInMs(int64_t waitTimeInMs) { mWaitTimeInMs = waitTimeInMs; }

    void setStatus(StatusCode status) { mStatus = status; }

    void setPropConfigs(std::vector<VehiclePropConfig> configs) { mPropConfigs = configs; }

    std::vector<int32_t> getGetPropConfigPropIds() { return mGetPropConfigPropIds; }

    std::vector<SubscribeOptions> getSubscriptionOptions() { return mSubscriptionOptions; }

    void triggerOnPropertyEvent(const std::vector<VehiclePropValue>& values) {
        VehiclePropValues propValues = {
                .payloads = values,
        };
        mSubscriptionCallback->onPropertyEvent(propValues, /*sharedMemoryCount=*/0);
    }

    void triggerSetErrorEvent(const std::vector<VehiclePropError>& errors) {
        VehiclePropErrors propErrors = {
                .payloads = errors,
        };
        mSubscriptionCallback->onPropertySetError(propErrors);
    }

    std::vector<int32_t> getUnsubscribedPropIds() { return mUnsubscribePropIds; }

private:
    std::mutex mLock;
    std::vector<GetValueResult> mGetValueResults;
    std::vector<GetValueRequest> mGetValueRequests;
    std::vector<SetValueResult> mSetValueResults;
    std::vector<SetValueRequest> mSetValueRequests;
    std::vector<VehiclePropConfig> mPropConfigs;
    std::vector<int32_t> mGetPropConfigPropIds;
    int64_t mWaitTimeInMs = 0;
    StatusCode mStatus = StatusCode::OK;
    std::condition_variable mCv;
    std::atomic<int> mThreadCount = 0;
    CallbackType mSubscriptionCallback;
    std::vector<SubscribeOptions> mSubscriptionOptions;
    std::vector<int32_t> mUnsubscribePropIds;
};

class MockSubscriptionCallback : public ISubscriptionCallback {
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

class AidlVhalClientTest : public ::testing::Test {
protected:
    class TestLinkUnlinkImpl final : public AidlVhalClient::ILinkUnlinkToDeath {
    public:
        binder_status_t linkToDeath([[maybe_unused]] AIBinder* binder,
                                    [[maybe_unused]] AIBinder_DeathRecipient* recipient,
                                    void* cookie) override {
            mCookie = cookie;
            return STATUS_OK;
        }

        binder_status_t unlinkToDeath(AIBinder*, AIBinder_DeathRecipient*, void*) override {
            // DO nothing.
            return STATUS_OK;
        }

        void* getCookie() { return mCookie; }

    private:
        void* mCookie;
    };

    constexpr static int32_t TEST_PROP_ID = 1;
    constexpr static int32_t TEST_AREA_ID = 2;
    constexpr static int32_t TEST_PROP_ID_2 = 3;
    constexpr static int32_t TEST_AREA_ID_2 = 4;
    constexpr static int64_t TEST_TIMEOUT_IN_MS = 100;

    void SetUp() override {
        mVhal = SharedRefBase::make<MockVhal>();
        auto impl = std::make_unique<TestLinkUnlinkImpl>();
        // We are sure impl would be alive when we use mLinkUnlinkImpl.
        mLinkUnlinkImpl = impl.get();
        mVhalClient = std::unique_ptr<AidlVhalClient>(
                new AidlVhalClient(mVhal, TEST_TIMEOUT_IN_MS, std::move(impl)));
    }

    AidlVhalClient* getClient() { return mVhalClient.get(); }

    MockVhal* getVhal() { return mVhal.get(); }

    void triggerBinderDied() { AidlVhalClient::onBinderDied(mLinkUnlinkImpl->getCookie()); }

    void triggerBinderUnlinked() { AidlVhalClient::onBinderUnlinked(mLinkUnlinkImpl->getCookie()); }

    size_t countOnBinderDiedCallbacks() { return mVhalClient->countOnBinderDiedCallbacks(); }

private:
    std::shared_ptr<MockVhal> mVhal;
    std::unique_ptr<AidlVhalClient> mVhalClient;
    TestLinkUnlinkImpl* mLinkUnlinkImpl;
};

TEST_F(AidlVhalClientTest, testIsAidl) {
    ASSERT_TRUE(getClient()->isAidlVhal());
}

TEST_F(AidlVhalClientTest, testGetValueNormal) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
            [&lock, &cv, resultPtr,
             gotResultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->getValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getStatus(), VehiclePropertyStatus::AVAILABLE);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(AidlVhalClientTest, testGetValueSync) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<std::unique_ptr<IHalPropValue>> result = getClient()->getValueSync(propValue);

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getStatus(), VehiclePropertyStatus::AVAILABLE);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(AidlVhalClientTest, testGetValueUnavailableStatusSync) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .status = VehiclePropertyStatus::UNAVAILABLE,
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<std::unique_ptr<IHalPropValue>> result = getClient()->getValueSync(propValue);

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getStatus(), VehiclePropertyStatus::UNAVAILABLE);
}

TEST_F(AidlVhalClientTest, testGetValueTimeout) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    // The request will time-out before the response.
    getVhal()->setWaitTimeInMs(200);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
            [&lock, &cv, resultPtr,
             gotResultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->getValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::TIMEOUT);
}

TEST_F(AidlVhalClientTest, testGetValueErrorStatus) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::INTERNAL_ERROR_FROM_VHAL);
}

TEST_F(AidlVhalClientTest, testGetValueNonOkayResult) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::INTERNAL_ERROR_FROM_VHAL);
}

TEST_F(AidlVhalClientTest, testGetValueIgnoreInvalidRequestId) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
            // This result has invalid request ID and should be ignored.
            GetValueResult{
                    .requestId = 1,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<std::unique_ptr<IHalPropValue>> result;
    VhalClientResult<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(AidlVhalClientTest, testSetValueNormal) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](VhalClientResult<void> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->setValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_TRUE(result.ok());
}

TEST_F(AidlVhalClientTest, testSetValueSync) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<void> result = getClient()->setValueSync(propValue);

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_TRUE(result.ok());
}

TEST_F(AidlVhalClientTest, testSetValueTimeout) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    // The request will time-out before the response.
    getVhal()->setWaitTimeInMs(200);
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](VhalClientResult<void> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->setValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::TIMEOUT);
}

TEST_F(AidlVhalClientTest, testSetValueErrorStatus) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<void> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::INTERNAL_ERROR_FROM_VHAL);
}

TEST_F(AidlVhalClientTest, testSetValueNonOkayResult) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<void> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::INTERNAL_ERROR_FROM_VHAL);
}

TEST_F(AidlVhalClientTest, testSetValueIgnoreInvalidRequestId) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
            // This result has invalid request ID and should be ignored.
            SetValueResult{
                    .requestId = 1,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    VhalClientResult<void> result;
    VhalClientResult<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](VhalClientResult<void> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_TRUE(result.ok());
}

TEST_F(AidlVhalClientTest, testAddOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    getClient()->addOnBinderDiedCallback(std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; }));
    getClient()->addOnBinderDiedCallback(std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; }));
    triggerBinderDied();

    ASSERT_TRUE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);

    triggerBinderUnlinked();

    ASSERT_EQ(countOnBinderDiedCallbacks(), static_cast<size_t>(0));
}

TEST_F(AidlVhalClientTest, testRemoveOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    auto callbackOne = std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; });
    auto callbackTwo = std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; });
    getClient()->addOnBinderDiedCallback(callbackOne);
    getClient()->addOnBinderDiedCallback(callbackTwo);
    getClient()->removeOnBinderDiedCallback(callbackOne);
    triggerBinderDied();

    ASSERT_FALSE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);

    triggerBinderUnlinked();

    ASSERT_EQ(countOnBinderDiedCallbacks(), static_cast<size_t>(0));
}

TEST_F(AidlVhalClientTest, testGetAllPropConfigs) {
    getVhal()->setPropConfigs({
            VehiclePropConfig{
                    .prop = TEST_PROP_ID,
                    .areaConfigs = {{
                                            .areaId = TEST_AREA_ID,
                                            .minInt32Value = 0,
                                            .maxInt32Value = 1,
                                            .supportVariableUpdateRate = true,
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
    ASSERT_TRUE(areaConfig0->isVariableUpdateRateSupported());

    const std::unique_ptr<IHalAreaConfig>& areaConfig1 = configs[0]->getAreaConfigs()[1];
    ASSERT_EQ(areaConfig1->getAreaId(), TEST_AREA_ID_2);
    ASSERT_EQ(areaConfig1->getMinInt32Value(), 2);
    ASSERT_EQ(areaConfig1->getMaxInt32Value(), 3);
    ASSERT_FALSE(areaConfig1->isVariableUpdateRateSupported());

    ASSERT_EQ(configs[1]->getPropId(), TEST_PROP_ID_2);
    ASSERT_EQ(configs[1]->getAreaConfigSize(), static_cast<size_t>(0));
}

TEST_F(AidlVhalClientTest, testGetAllPropConfigsError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    auto result = getClient()->getAllPropConfigs();

    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), ErrorCode::INTERNAL_ERROR_FROM_VHAL);
}

TEST_F(AidlVhalClientTest, testGetPropConfigs) {
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

    ASSERT_EQ(getVhal()->getGetPropConfigPropIds(), propIds);
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

TEST_F(AidlVhalClientTest, testGetPropConfigsError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    std::vector<int32_t> propIds = {TEST_PROP_ID, TEST_PROP_ID_2};
    auto result = getClient()->getPropConfigs(propIds);

    ASSERT_FALSE(result.ok());
}

TEST_F(AidlVhalClientTest, testSubscribe) {
    std::vector<SubscribeOptions> options = {
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

    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->subscribe(options);

    ASSERT_TRUE(result.ok());
    ASSERT_EQ(getVhal()->getSubscriptionOptions(), options);

    getVhal()->triggerOnPropertyEvent(std::vector<VehiclePropValue>{
            {
                    .prop = TEST_PROP_ID,
                    .areaId = TEST_AREA_ID,
                    .value.int32Values = {1},
            },
    });

    ASSERT_EQ(callback->getEventPropIds(), std::vector<int32_t>({TEST_PROP_ID}));

    getVhal()->triggerSetErrorEvent(std::vector<VehiclePropError>({
            {
                    .propId = TEST_PROP_ID,
                    .areaId = TEST_AREA_ID,
                    .errorCode = StatusCode::INTERNAL_ERROR,
            },
    }));

    auto errors = callback->getErrors();
    ASSERT_EQ(errors.size(), static_cast<size_t>(1));
    ASSERT_EQ(errors[0].propId, TEST_PROP_ID);
    ASSERT_EQ(errors[0].areaId, TEST_AREA_ID);
    ASSERT_EQ(errors[0].status, StatusCode::INTERNAL_ERROR);
}

TEST_F(AidlVhalClientTest, testSubscribeError) {
    std::vector<SubscribeOptions> options = {
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

TEST_F(AidlVhalClientTest, testUnubscribe) {
    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->unsubscribe({TEST_PROP_ID});

    ASSERT_TRUE(result.ok());
    ASSERT_EQ(getVhal()->getUnsubscribedPropIds(), std::vector<int32_t>({TEST_PROP_ID}));
}

TEST_F(AidlVhalClientTest, testUnubscribeError) {
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);
    auto callback = std::make_shared<MockSubscriptionCallback>();
    auto subscriptionClient = getClient()->getSubscriptionClient(callback);
    auto result = subscriptionClient->unsubscribe({TEST_PROP_ID});

    ASSERT_FALSE(result.ok());
}

TEST_F(AidlVhalClientTest, testGetRemoteInterfaceVersion) {
    // The AIDL VHAL should be v2 or higher.
    ASSERT_THAT(getClient()->getRemoteInterfaceVersion(), Gt(1));
}

TEST_F(AidlVhalClientTest, testSubscribeOptionsBuilder) {
    auto optionsBuilder = SubscribeOptionsBuilder(TEST_PROP_ID);
    optionsBuilder.setSampleRate(1.23f);
    optionsBuilder.addAreaId(1);
    optionsBuilder.addAreaId(2);
    optionsBuilder.setResolution(2.34f);

    auto options = optionsBuilder.build();

    ASSERT_EQ(options,
              (SubscribeOptions{
                      .propId = TEST_PROP_ID,
                      .areaIds = {1, 2},
                      .sampleRate = 1.23f,
                      .resolution = 2.34f,
                      // VUR is true by default
                      .enableVariableUpdateRate = true,
              }));
}

TEST_F(AidlVhalClientTest, testSubscribeOptionsBuilder_disableVur) {
    auto optionsBuilder = SubscribeOptionsBuilder(TEST_PROP_ID);
    optionsBuilder.setSampleRate(1.23f);
    optionsBuilder.addAreaId(1);
    optionsBuilder.addAreaId(2);
    optionsBuilder.setResolution(2.34f);
    optionsBuilder.setEnableVariableUpdateRate(false);

    auto options = optionsBuilder.build();

    ASSERT_EQ(options,
              (SubscribeOptions{
                      .propId = TEST_PROP_ID,
                      .areaIds = {1, 2},
                      .sampleRate = 1.23f,
                      .resolution = 2.34f,
                      .enableVariableUpdateRate = false,
              }));
}

TEST_F(AidlVhalClientTest, testAidlHalPropValueClone_valueIsTheSame) {
    VehiclePropValue testProp{.prop = TEST_PROP_ID,
                              .areaId = TEST_AREA_ID,
                              .value = {
                                      .int32Values = {1, 2},
                                      .floatValues = {1.1, 2.2},
                              }};
    auto testPropCopy = testProp;
    std::unique_ptr<IHalPropValue> halPropValue =
            std::make_unique<AidlHalPropValue>(std::move(testPropCopy));
    auto halPropValueClone = halPropValue->clone();

    EXPECT_EQ(halPropValueClone->getPropId(), TEST_PROP_ID);
    EXPECT_EQ(halPropValueClone->getAreaId(), TEST_AREA_ID);
    EXPECT_EQ(halPropValueClone->getInt32Values(), std::vector<int32_t>({1, 2}));
    EXPECT_EQ(halPropValueClone->getFloatValues(), std::vector<float>({1.1, 2.2}));
}

TEST_F(AidlVhalClientTest, testAidlHalPropValueClone_modifyCloneDoesNotAffectOrig) {
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
            std::make_unique<AidlHalPropValue>(std::move(testPropCopy));
    auto halPropValueClone = halPropValue->clone();

    halPropValueClone->setInt32Values(int32Values2);
    halPropValueClone->setFloatValues(floatValues2);

    EXPECT_EQ(halPropValue->getInt32Values(), int32Values1);
    EXPECT_EQ(halPropValue->getFloatValues(), floatValues1);
    EXPECT_EQ(halPropValueClone->getInt32Values(), int32Values2);
    EXPECT_EQ(halPropValueClone->getFloatValues(), floatValues2);
}

}  // namespace aidl_test
}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
