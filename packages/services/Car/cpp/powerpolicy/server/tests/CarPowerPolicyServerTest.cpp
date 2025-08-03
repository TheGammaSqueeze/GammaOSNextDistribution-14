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

#include "CarPowerPolicyServer.h"

#include <aidl/android/automotive/powerpolicy/internal/BnCarPowerPolicyDelegateCallback.h>
#include <aidl/android/automotive/powerpolicy/internal/PowerPolicyFailureReason.h>
#include <aidl/android/automotive/powerpolicy/internal/PowerPolicyInitData.h>
#include <aidl/android/frameworks/automotive/powerpolicy/BnCarPowerPolicyChangeCallback.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicy.h>
#include <aidl/android/frameworks/automotive/powerpolicy/CarPowerPolicyFilter.h>
#include <aidl/android/frameworks/automotive/powerpolicy/ICarPowerPolicyChangeCallback.h>
#include <aidl/android/frameworks/automotive/powerpolicy/ICarPowerPolicyServer.h>
#include <aidl/android/frameworks/automotive/powerpolicy/PowerComponent.h>
#include <android-base/file.h>
#include <android-base/thread_annotations.h>
#include <binder/IPCThreadState.h>
#include <gmock/gmock.h>
#include <private/android_filesystem_config.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/StrongPointer.h>

#include <android_car_feature.h>
#include <tinyxml2.h>

#include <chrono>  // NOLINT(build/c++11)
#include <mutex>   // NOLINT(build/c++11)
#include <thread>  // NOLINT(build/c++11)
#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using android::IBinder;

using ::aidl::android::automotive::powerpolicy::internal::BnCarPowerPolicyDelegateCallback;
using ::aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegate;
using ::aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegateCallback;
using ::aidl::android::automotive::powerpolicy::internal::PowerPolicyFailureReason;
using ::aidl::android::automotive::powerpolicy::internal::PowerPolicyInitData;
using ::aidl::android::frameworks::automotive::powerpolicy::BnCarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyServer;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;

using ::android::car::feature::car_power_policy_refactoring;

using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

using ::std::chrono_literals::operator""ms;

using ::testing::_;
using ::testing::Invoke;
using ::testing::Return;

using ::tinyxml2::XML_SUCCESS;
using ::tinyxml2::XMLDocument;

namespace {

constexpr const char* kDirPrefix = "/tests/data/";
constexpr const char* kValidPowerPolicyXmlFile = "valid_power_policy.xml";
constexpr const char* kTestLooperThreadName = "LooperThread";
constexpr std::chrono::duration kCallbackWaitTime = 5000ms;
constexpr std::chrono::duration kGeneralWaitTime = 2000ms;

class MockPowerPolicyChangeCallback : public BnCarPowerPolicyChangeCallback {
public:
    ScopedAStatus onPolicyChanged(const CarPowerPolicy& /*policy*/) override {
        return ScopedAStatus::ok();
    }
};

class MockPowerPolicyDelegateCallback : public BnCarPowerPolicyDelegateCallback {
public:
    MOCK_METHOD(ScopedAStatus, updatePowerComponents, (const CarPowerPolicy&), (override));
    MOCK_METHOD(ScopedAStatus, onApplyPowerPolicySucceeded, (int32_t, const CarPowerPolicy&),
                (override));
    MOCK_METHOD(ScopedAStatus, onApplyPowerPolicyFailed, (int32_t, PowerPolicyFailureReason),
                (override));
    MOCK_METHOD(ScopedAStatus, onPowerPolicyChanged, (const CarPowerPolicy&), (override));
};

std::string getTestDataPath(const char* filename) {
    static std::string baseDir = android::base::GetExecutableDirectory();
    return baseDir + kDirPrefix + filename;
}

class ScopedChangeCallingUid final : public RefBase {
public:
    explicit ScopedChangeCallingUid(uid_t uid) {
        mCallingUid = IPCThreadState::self()->getCallingUid();
        mCallingPid = IPCThreadState::self()->getCallingPid();
        if (mCallingUid == uid) {
            return;
        }
        mChangedUid = uid;
        int64_t token = ((int64_t)mChangedUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }
    ~ScopedChangeCallingUid() {
        if (mCallingUid == mChangedUid) {
            return;
        }
        int64_t token = ((int64_t)mCallingUid << 32) | mCallingPid;
        IPCThreadState::self()->restoreCallingIdentity(token);
    }

private:
    uid_t mCallingUid;
    uid_t mChangedUid;
    pid_t mCallingPid;
};

}  // namespace

namespace internal {

class CarPowerPolicyServerPeer : public RefBase {
public:
    CarPowerPolicyServerPeer() {
        std::unique_ptr<MockLinkUnlinkImpl> impl = std::make_unique<MockLinkUnlinkImpl>();
        // We know this would be alive as long as server is alive.
        mLinkUnlinkImpl = impl.get();
        mServer = ndk::SharedRefBase::make<CarPowerPolicyServer>();
        mServer->setLinkUnlinkImpl(std::move(impl));
        mBinder = mServer->asBinder();
        mServerProxy = ICarPowerPolicyServer::fromBinder(mBinder);
    }

    ~CarPowerPolicyServerPeer() {
        if (mServer->mHandlerLooper != nullptr) {
            release();
        }
    }

    ScopedAStatus getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) {
        return mServerProxy->getCurrentPowerPolicy(aidlReturn);
    }

    ScopedAStatus registerPowerPolicyChangeCallback(
            const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback,
            const CarPowerPolicyFilter& filter) {
        return mServerProxy->registerPowerPolicyChangeCallback(callback, filter);
    }

    ScopedAStatus unregisterPowerPolicyChangeCallback(
            const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback) {
        return mServerProxy->unregisterPowerPolicyChangeCallback(callback);
    }

    ScopedAStatus applyPowerPolicy(const std::string& policyId) {
        return mServerProxy->applyPowerPolicy(policyId);
    }

    ScopedAStatus notifyCarServiceReady(
            const std::shared_ptr<ICarPowerPolicyDelegateCallback>& callback,
            PowerPolicyInitData* aidlReturn) {
        return mServer->notifyCarServiceReadyInternal(callback, aidlReturn);
    }

    ScopedAStatus applyPowerPolicyAsync(int32_t requestId, const std::string& policyId,
                                        bool force) {
        return mServer->applyPowerPolicyAsync(requestId, policyId, force);
    }

    ScopedAStatus applyPowerPolicyPerPowerStateChangeAsync(
            int32_t requestId, ICarPowerPolicyDelegate::PowerState state) {
        return mServer->applyPowerPolicyPerPowerStateChangeAsync(requestId, state);
    }

    ScopedAStatus setPowerPolicyGroup(const std::string& policyGroupId) {
        return mServer->setPowerPolicyGroup(policyGroupId);
    }

    void init() {
        initializeLooper();
        ASSERT_NO_FATAL_FAILURE(initializePolicyManager());
        initializePowerComponentHandler();
        ASSERT_NO_FATAL_FAILURE(applyInitialPolicy());
    }

    void release() { finalizeLooper(); }

    void onClientBinderDied(void* cookie) { mServer->onClientBinderDied(cookie); }

    std::vector<CallbackInfo> getPolicyChangeCallbacks() {
        return mServer->getPolicyChangeCallbacks();
    }

    size_t countOnClientBinderDiedContexts() { return mServer->countOnClientBinderDiedContexts(); }

    std::unordered_set<void*> getCookies() { return mLinkUnlinkImpl->getCookies(); }

    void expectLinkToDeathStatus(AIBinder* binder, status_t linkToDeathResult) {
        mLinkUnlinkImpl->expectLinkToDeathStatus(binder, linkToDeathResult);
    }

private:
    void initializeLooper() {
        sp<Looper> looper = Looper::prepare(/*opts=*/0);
        mServer->mHandlerLooper = looper;
        std::mutex mutex;
        std::condition_variable cv;
        bool looperReady = false;
        mHandlerLooperThread = std::thread([looper, &cv, &mutex, &looperReady, this]() {
            Looper::setForThread(looper);
            if (int result = pthread_setname_np(pthread_self(), kTestLooperThreadName);
                result != 0) {
                ALOGE("Failed to set test looper thread name: %s", strerror(result));
            }
            mShouldTerminateLooper.store(false);
            {
                std::unique_lock lock(mutex);
                looperReady = true;
                cv.notify_all();
            }
            while (!mShouldTerminateLooper.load()) {
                looper->pollOnce(/*timeoutMillis=*/-1);
            }
        });
        std::unique_lock lock(mutex);
        // Wait until thread looper is ready.
        cv.wait(lock, [&looperReady] { return looperReady; });
    }

    void finalizeLooper() {
        mShouldTerminateLooper.store(true);
        mServer->mHandlerLooper->wake();
        if (mHandlerLooperThread.joinable()) {
            mHandlerLooperThread.join();
        }
    }

    void initializePolicyManager() {
        PolicyManager& policyManager = mServer->mPolicyManager;
        policyManager.initRegularPowerPolicy(/*override=*/true);
        policyManager.initPreemptivePowerPolicy();

        XMLDocument xmlDoc;
        std::string path = getTestDataPath(kValidPowerPolicyXmlFile);
        xmlDoc.LoadFile(path.c_str());
        ASSERT_TRUE(xmlDoc.ErrorID() == XML_SUCCESS);
        policyManager.readPowerPolicyFromXml(xmlDoc);
    }

    void initializePowerComponentHandler() {
        PowerComponentHandler& componentHandler = mServer->mComponentHandler;
        componentHandler.init();
    }

    void applyInitialPolicy() {
        auto policyMeta = mServer->mPolicyManager.getPowerPolicy(kSystemPolicyIdInitialOn);
        ASSERT_TRUE(policyMeta.ok());
        mServer->mCurrentPowerPolicyMeta = *policyMeta;
    }

private:
    class MockLinkUnlinkImpl : public CarPowerPolicyServer::LinkUnlinkImpl {
    public:
        MOCK_METHOD(binder_status_t, linkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                    (override));
        MOCK_METHOD(binder_status_t, unlinkToDeath, (AIBinder*, AIBinder_DeathRecipient*, void*),
                    (override));

        void expectLinkToDeathStatus(AIBinder* binder, binder_status_t linkToDeathResult) {
            EXPECT_CALL(*this, linkToDeath(binder, _, _))
                    .WillRepeatedly(
                            Invoke([this, linkToDeathResult](AIBinder*, AIBinder_DeathRecipient*,
                                                             void* cookie) {
                                Mutex::Autolock lock(mMutex);
                                mCookies.insert(cookie);
                                return linkToDeathResult;
                            }));
            EXPECT_CALL(*this, unlinkToDeath(binder, _, _))
                    .WillRepeatedly(
                            Invoke([this](AIBinder*, AIBinder_DeathRecipient*, void* cookie) {
                                Mutex::Autolock lock(mMutex);
                                mCookies.erase(cookie);
                                return STATUS_OK;
                            }));
        }

        std::unordered_set<void*> getCookies() {
            Mutex::Autolock lock(mMutex);
            return mCookies;
        }

    private:
        android::Mutex mMutex;
        std::unordered_set<void*> mCookies GUARDED_BY(mMutex);
    };

    MockLinkUnlinkImpl* mLinkUnlinkImpl;
    std::shared_ptr<CarPowerPolicyServer> mServer;
    std::shared_ptr<ICarPowerPolicyServer> mServerProxy;
    std::thread mHandlerLooperThread;
    SpAIBinder mBinder;
    std::atomic<bool> mShouldTerminateLooper;
};

}  // namespace internal

class CarPowerPolicyServerTest : public ::testing::Test {
public:
    std::shared_ptr<ICarPowerPolicyChangeCallback> getPowerPolicyChangeCallback() {
        std::shared_ptr<MockPowerPolicyChangeCallback> callback =
                ndk::SharedRefBase::make<MockPowerPolicyChangeCallback>();
        return ICarPowerPolicyChangeCallback::fromBinder(callback->asBinder());
    }

    // Sets calling UID to imitate System's process.
    void setSystemCallingUid() {
        mScopedChangeCallingUid = sp<ScopedChangeCallingUid>::make(AID_SYSTEM);
    }

    void testApplyPowerPolicyPerPowerStateChangeAsyncInternal(const std::string& policyGroupId,
                                                              const std::string& expectedPolicyId) {
        sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
        std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
                ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
        server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
        server->init();
        setSystemCallingUid();

        int32_t requestId = 9999;
        int32_t calledRequestId = -1;
        std::string policyIdForUpdate;
        std::string policyIdForNotification;
        std::mutex mutex;
        std::condition_variable cv;
        EXPECT_CALL(*callback, updatePowerComponents)
                .WillRepeatedly(
                        Invoke([&policyIdForUpdate](const CarPowerPolicy& policy) -> ScopedAStatus {
                            policyIdForUpdate = policy.policyId;
                            return ScopedAStatus::ok();
                        }));
        EXPECT_CALL(*callback, onApplyPowerPolicySucceeded)
                .WillRepeatedly(
                        Invoke([&calledRequestId, &policyIdForNotification, &cv,
                                &mutex](int32_t requestId,
                                        const CarPowerPolicy& accumulatedPolicy) -> ScopedAStatus {
                            calledRequestId = requestId;
                            policyIdForNotification = accumulatedPolicy.policyId;
                            std::unique_lock lock(mutex);
                            cv.notify_all();
                            return ScopedAStatus::ok();
                        }));
        PowerPolicyInitData initData;
        server->notifyCarServiceReady(callback, &initData);
        server->setPowerPolicyGroup(policyGroupId);

        ScopedAStatus status =
                server->applyPowerPolicyPerPowerStateChangeAsync(requestId,
                                                                 ICarPowerPolicyDelegate::
                                                                         PowerState::ON);

        ASSERT_TRUE(status.isOk()) << "applyPowerPolicyPerPowerStateChangeAsync should return OK";

        std::unique_lock lock(mutex);
        bool waitResult =
                cv.wait_for(lock, kCallbackWaitTime, [&policyIdForNotification, &expectedPolicyId] {
                    return policyIdForNotification.compare(expectedPolicyId) == 0;
                });
        EXPECT_TRUE(waitResult)
                << "onApplyPowerPolicySucceeded() should be called with the same power policy ID";
        EXPECT_EQ(policyIdForUpdate, expectedPolicyId)
                << "updatePowerComponents should be called with " << expectedPolicyId;
    }

private:
    sp<ScopedChangeCallingUid> mScopedChangeCallingUid;
};

TEST_F(CarPowerPolicyServerTest, TestRegisterCallback) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackOne = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackOne->asBinder().get(), STATUS_OK);

    CarPowerPolicyFilter filter;
    ScopedAStatus status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_FALSE(status.isOk()) << "Duplicated registration is not allowed";
    filter.components = {PowerComponent::BLUETOOTH, PowerComponent::AUDIO};
    status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_FALSE(status.isOk()) << "Duplicated registration is not allowed";

    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackTwo = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackTwo->asBinder().get(), STATUS_OK);

    status = server->registerPowerPolicyChangeCallback(callbackTwo, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(CarPowerPolicyServerTest, TestRegisterCallback_BinderDied) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callback = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_DEAD_OBJECT);
    CarPowerPolicyFilter filter;

    ASSERT_FALSE(server->registerPowerPolicyChangeCallback(callback, filter).isOk())
            << "When linkToDeath fails, registerPowerPolicyChangeCallback should return an error";
}

TEST_F(CarPowerPolicyServerTest, TestOnBinderDied) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callbackOne = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callbackOne->asBinder().get(), STATUS_OK);

    CarPowerPolicyFilter filter;
    ScopedAStatus status = server->registerPowerPolicyChangeCallback(callbackOne, filter);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_EQ(server->getPolicyChangeCallbacks().size(), static_cast<size_t>(1));
    ASSERT_EQ(server->countOnClientBinderDiedContexts(), static_cast<size_t>(1));
    ASSERT_EQ(server->getCookies().size(), static_cast<size_t>(1));

    void* cookie = *(server->getCookies().begin());
    server->onClientBinderDied(cookie);

    ASSERT_TRUE(server->getPolicyChangeCallbacks().empty());

    ASSERT_EQ(server->countOnClientBinderDiedContexts(), static_cast<size_t>(0));
}

TEST_F(CarPowerPolicyServerTest, TestUnregisterCallback) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<ICarPowerPolicyChangeCallback> callback = getPowerPolicyChangeCallback();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    CarPowerPolicyFilter filter;

    server->registerPowerPolicyChangeCallback(callback, filter);
    ScopedAStatus status = server->unregisterPowerPolicyChangeCallback(callback);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_FALSE(server->unregisterPowerPolicyChangeCallback(callback).isOk())
            << "Unregistering an unregistered powerpolicy change callback should return an error";
}

TEST_F(CarPowerPolicyServerTest, TestGetCurrentPowerPolicy) {
    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    CarPowerPolicy currentPolicy;

    ScopedAStatus status = server->getCurrentPowerPolicy(&currentPolicy);
    ASSERT_FALSE(status.isOk()) << "The current policy at creation should be null";
    // TODO(b/168545262): Add more test cases after VHAL integration is complete.
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromNativeClients) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);
    const std::string powerPolicyId = "policy_id_other_off";

    ScopedAStatus status = server->applyPowerPolicy(powerPolicyId);
    ASSERT_TRUE(status.isOk()) << "applyPowerPolicy should return OK";
    CarPowerPolicy policy;
    status = server->getCurrentPowerPolicy(&policy);
    ASSERT_TRUE(status.isOk()) << "getCurrentPowerPolicy should return OK";
    ASSERT_EQ(policy.policyId, powerPolicyId.c_str())
            << "The current power policy should be the applied one";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromNativeClients_carServiceNotRegistered) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    server->init();
    const std::string powerPolicyId = "policy_id_other_off";

    ScopedAStatus status = server->applyPowerPolicy(powerPolicyId);
    ASSERT_TRUE(status.isOk()) << "applyPowerPolicy should return OK";
    CarPowerPolicy policy;
    status = server->getCurrentPowerPolicy(&policy);
    ASSERT_TRUE(status.isOk()) << "getCurrentPowerPolicy should return OK";
    ASSERT_EQ(policy.policyId, powerPolicyId.c_str())
            << "The current power policy should be the applied one";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromNativeClients_invalidPolicyId) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    server->init();

    ScopedAStatus status = server->applyPowerPolicy("policy_not_exist");
    ASSERT_FALSE(status.isOk()) << "applyPowerPolicy should return an error";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromCarService) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    setSystemCallingUid();
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);
    std::string policyId;
    std::mutex mutex;
    std::condition_variable cv;
    EXPECT_CALL(*callback, updatePowerComponents)
            .WillRepeatedly(
                    Invoke([&policyId, &cv, &mutex](const CarPowerPolicy& policy) -> ScopedAStatus {
                        std::unique_lock lock(mutex);
                        policyId = policy.policyId;
                        cv.notify_all();
                        return ScopedAStatus::ok();
                    }));
    EXPECT_CALL(*callback, onApplyPowerPolicySucceeded)
            .WillRepeatedly(Invoke([]([[maybe_unused]] int32_t requestId,
                                      [[maybe_unused]] const CarPowerPolicy& accumulatedPolicy)
                                           -> ScopedAStatus { return ScopedAStatus::ok(); }));

    ScopedAStatus status = server->applyPowerPolicyAsync(/*requestId=*/9999, "policy_id_other_off",
                                                         /*force=*/false);
    ASSERT_TRUE(status.isOk()) << "applyPowerPolicyAsync should return OK";
    std::unique_lock lock(mutex);
    bool waitResult = cv.wait_for(lock, kCallbackWaitTime, [&policyId] {
        return policyId.compare("policy_id_other_off") == 0;
    });
    ASSERT_TRUE(waitResult)
            << "updatePowerComponents() should be called with the same power policy ID";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromCarService_nonSystemUid) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);

    ScopedAStatus status = server->applyPowerPolicyAsync(/*requestId=*/9999, "policy_id_other_off",
                                                         /*force=*/false);
    ASSERT_FALSE(status.isOk())
            << "applyPowerPolicyAsync should fail when the caller doesn't have system UID";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromCarService_invalidPolicyId) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    setSystemCallingUid();
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);
    std::mutex mutex;
    std::condition_variable cv;
    int32_t requestIdLocal;
    bool methodCalled = false;
    PowerPolicyFailureReason failureReason;
    EXPECT_CALL(*callback, onApplyPowerPolicyFailed)
            .WillRepeatedly(Invoke(
                    [&requestIdLocal, &failureReason, &methodCalled, &cv,
                     &mutex](int32_t requestId, PowerPolicyFailureReason reason) -> ScopedAStatus {
                        std::unique_lock lock(mutex);
                        requestIdLocal = requestId;
                        failureReason = reason;
                        methodCalled = true;
                        cv.notify_all();
                        return ScopedAStatus::ok();
                    }));

    ScopedAStatus status = server->applyPowerPolicyAsync(/*requestId=*/9999, "policy_not_exist",
                                                         /*force=*/false);
    ASSERT_TRUE(status.isOk());
    std::unique_lock lock(mutex);
    bool waitResult =
            cv.wait_for(lock, kCallbackWaitTime, [&methodCalled] { return methodCalled; });
    ASSERT_TRUE(waitResult) << "onApplyPowerPolicyFailed should be called";
    EXPECT_EQ(requestIdLocal, 9999);
    ASSERT_EQ(failureReason, PowerPolicyFailureReason::POWER_POLICY_FAILURE_NOT_REGISTERED_ID);
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyFromCarService_duplicatedRequestId) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    setSystemCallingUid();
    EXPECT_CALL(*callback, updatePowerComponents)
            .WillRepeatedly(
                    Invoke([]([[maybe_unused]] const CarPowerPolicy& policy) -> ScopedAStatus {
                        // To make sure that both requests of applying power policy occur together.
                        std::this_thread::sleep_for(kGeneralWaitTime);
                        return ScopedAStatus::ok();
                    }));
    EXPECT_CALL(*callback, onApplyPowerPolicySucceeded)
            .WillRepeatedly(Invoke([]([[maybe_unused]] int32_t requestId,
                                      [[maybe_unused]] const CarPowerPolicy& accumulatedPolicy)
                                           -> ScopedAStatus { return ScopedAStatus::ok(); }));
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);

    ScopedAStatus status = server->applyPowerPolicyAsync(/*requestId=*/9999, "policy_id_other_off",
                                                         /*force=*/false);
    ASSERT_TRUE(status.isOk()) << "applyPowerPolicyAsync should return OK";

    status = server->applyPowerPolicyAsync(/*requestId=*/9999, "policy_id_other_untouched",
                                           /*force=*/false);
    ASSERT_FALSE(status.isOk())
            << "applyPowerPolicyAsync should return an error when request ID is duplicated";
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyPerPowerStateChangeAsync) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    testApplyPowerPolicyPerPowerStateChangeAsyncInternal("", "system_power_policy_all_on");
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyPerPowerStateChangeAsync_nonSystemUid) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);

    ScopedAStatus status =
            server->applyPowerPolicyPerPowerStateChangeAsync(/*requestId=*/9999,
                                                             ICarPowerPolicyDelegate::PowerState::
                                                                     ON);

    ASSERT_FALSE(status.isOk()) << "applyPowerPolicyPerPowerStateChangeAsync should fail when the "
                                   "caller doesn't have system UID";
}

TEST_F(CarPowerPolicyServerTest,
       TestApplyPowerPolicyPerPowerStateChangeAsync_notSupportedPowerState) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    sp<internal::CarPowerPolicyServerPeer> server = new internal::CarPowerPolicyServerPeer();
    std::shared_ptr<MockPowerPolicyDelegateCallback> callback =
            ndk::SharedRefBase::make<MockPowerPolicyDelegateCallback>();
    server->expectLinkToDeathStatus(callback->asBinder().get(), STATUS_OK);
    server->init();
    setSystemCallingUid();
    EXPECT_CALL(*callback, updatePowerComponents).Times(0);
    EXPECT_CALL(*callback, onPowerPolicyChanged).Times(0);
    PowerPolicyInitData initData;
    server->notifyCarServiceReady(callback, &initData);

    // We don't have default power policy for SHUTDOWN_PREPARE.
    ScopedAStatus status =
            server->applyPowerPolicyPerPowerStateChangeAsync(/*requestId=*/9999,
                                                             ICarPowerPolicyDelegate::PowerState::
                                                                     SHUTDOWN_PREPARE);

    EXPECT_FALSE(status.isOk())
            << "applyPowerPolicyPerPowerStateChangeAsync should return an error";
    EXPECT_EQ(status.getServiceSpecificError(), EX_ILLEGAL_ARGUMENT) << "Error code should be set";

    // Wait for some time to verify that no callback is made to CPMS.
    std::this_thread::sleep_for(kGeneralWaitTime);
}

TEST_F(CarPowerPolicyServerTest, TestApplyPowerPolicyPerPowerStateChangeAsync_withNewGroup) {
    if (!car_power_policy_refactoring()) {
        GTEST_SKIP() << "car_power_policy_refactoring feature flag is not enabled";
    }

    testApplyPowerPolicyPerPowerStateChangeAsyncInternal("basic_policy_group",
                                                         "policy_id_other_untouched");
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
