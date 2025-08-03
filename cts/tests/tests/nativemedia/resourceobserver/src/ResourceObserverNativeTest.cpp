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

// #define LOG_NDEBUG 0
#define LOG_TAG "ResourceObserverNativeTest"

#include <aidl/android/media/BnResourceObserver.h>
#include <aidl/android/media/IResourceObserverService.h>
#include <android-base/logging.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <gtest/gtest.h>

#include "media/NdkMediaCodec.h"

using namespace android;
using Status = ::ndk::ScopedAStatus;
using ::aidl::android::media::BnResourceObserver;
using ::aidl::android::media::IResourceObserverService;
using ::aidl::android::media::MediaObservableEvent;
using ::aidl::android::media::MediaObservableFilter;
using ::aidl::android::media::MediaObservableParcel;
using ::aidl::android::media::MediaObservableType;

static const char* MIMETYPE_AVC = "video/avc";

static std::string toString(const MediaObservableParcel& observable) {
    return "{" + ::aidl::android::media::toString(observable.type) + ", " +
            std::to_string(observable.value) + "}";
}

class ResourceObserverNativeTest : public ::testing::Test {
public:
    void SetUp() override { ABinderProcess_startThreadPool(); }
    void TearDown() override {}

    struct StatusChangeEvent {
        MediaObservableEvent event;
        int32_t uid;
        int32_t pid;
        std::vector<MediaObservableParcel> observables;
    };
    struct ResourceObserver : public BnResourceObserver {
        explicit ResourceObserver() {}

        // IResourceObserver
        ::ndk::ScopedAStatus onStatusChanged(
                MediaObservableEvent event, int32_t uid, int32_t pid,
                const std::vector<MediaObservableParcel>& observables) override {
            LOG(INFO) << ::aidl::android::media::toString(event) << ", uid: " << uid
                      << ", pid: " << pid << ", " << toString(observables[0]).c_str();

            std::scoped_lock lock{mLock};
            mLastEvent.event = event;
            mLastEvent.uid = uid;
            mLastEvent.pid = pid;
            mLastEvent.observables = observables;
            mStatusChangeCalled = true;
            mCondition.notify_one();
            return ::ndk::ScopedAStatus::ok();
        }
        // ~IResourceObserver

        bool waitForEvent(StatusChangeEvent& event, int64_t timeoutUs = 0) {
            std::unique_lock lock{mLock};
            if (!mStatusChangeCalled && timeoutUs > 0) {
                mCondition.wait_for(lock, std::chrono::microseconds(timeoutUs));
            }
            if (!mStatusChangeCalled) {
                return false;
            }
            event = mLastEvent;
            mStatusChangeCalled = false;
            return true;
        }
        std::mutex mLock;
        std::condition_variable mCondition;
        bool mStatusChangeCalled = false;
        StatusChangeEvent mLastEvent;
    };

    void testResourceObserver(MediaObservableEvent eventType) {
        ::ndk::SpAIBinder binder(AServiceManager_getService("media.resource_observer"));
        std::shared_ptr<IResourceObserverService> service =
                IResourceObserverService::fromBinder(binder);

        EXPECT_NE(service, nullptr);

        std::shared_ptr<ResourceObserver> observer = ::ndk::SharedRefBase::make<ResourceObserver>();
        std::vector<MediaObservableFilter> filters = {{MediaObservableType::kVideoSecureCodec,
                                                       eventType},
                                                      {MediaObservableType::kVideoNonSecureCodec,
                                                       eventType}};

        Status status = service->registerObserver(observer, filters);
        EXPECT_TRUE(status.isOk());

        AMediaCodec* dec = AMediaCodec_createDecoderByType(MIMETYPE_AVC);

        // We only test this if the AVC non-secure decoder can be created.
        if (dec != nullptr) {
            StatusChangeEvent event;
            if ((uint64_t)eventType & (uint64_t)MediaObservableEvent::kBusy) {
                EXPECT_TRUE(observer->waitForEvent(event, 100000));
                verifyEvent(event, MediaObservableEvent::kBusy,
                            MediaObservableType::kVideoNonSecureCodec);
            } else {
                // Should not receive any event, wait 1 second to confirm.
                EXPECT_FALSE(observer->waitForEvent(event, 1000000));
            }

            AMediaCodec_delete(dec);

            if ((uint64_t)eventType & (uint64_t)MediaObservableEvent::kIdle) {
                EXPECT_TRUE(observer->waitForEvent(event, 100000));
                verifyEvent(event, MediaObservableEvent::kIdle,
                            MediaObservableType::kVideoNonSecureCodec);
            } else {
                // Should not receive any event, wait 1 second to confirm.
                EXPECT_FALSE(observer->waitForEvent(event, 1000000));
            }
        }

        status = service->unregisterObserver(observer);
        EXPECT_TRUE(status.isOk());
    }

    void verifyEvent(const StatusChangeEvent& event, MediaObservableEvent expectedEventType,
                     MediaObservableType expectedObservableType) {
        EXPECT_EQ(event.event, expectedEventType);
        EXPECT_EQ(event.pid, getpid());
        EXPECT_EQ(event.uid, getuid());
        EXPECT_EQ(event.observables.size(), 1);
        EXPECT_EQ(event.observables[0].type, expectedObservableType);
        EXPECT_EQ(event.observables[0].value, 1);
    }
};

//-------------------------------------------------------------------------------------------------
TEST_F(ResourceObserverNativeTest, testInvalidParameters) {
    LOG(INFO) << "testInvalidParameters";

    ::ndk::SpAIBinder binder(AServiceManager_getService("media.resource_observer"));
    std::shared_ptr<IResourceObserverService> service =
            IResourceObserverService::fromBinder(binder);

    EXPECT_NE(service, nullptr);

    std::shared_ptr<ResourceObserver> observer = ::ndk::SharedRefBase::make<ResourceObserver>();
    std::vector<MediaObservableFilter> filters = {{MediaObservableType::kVideoSecureCodec,
                                                   MediaObservableEvent::kAll},
                                                  {MediaObservableType::kVideoNonSecureCodec,
                                                   MediaObservableEvent::kAll}};
    std::vector<MediaObservableFilter> emptyFilters;

    // Test register with null observer fails.
    Status status = service->registerObserver(nullptr, filters);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.getServiceSpecificError(), BAD_VALUE);

    // Test register with empty filter list fails.
    status = service->registerObserver(observer, emptyFilters);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.getServiceSpecificError(), BAD_VALUE);

    // Test register duplicate observer fails.
    status = service->registerObserver(observer, filters);
    EXPECT_TRUE(status.isOk());
    status = service->registerObserver(observer, filters);
    EXPECT_FALSE(status.isOk());
    EXPECT_EQ(status.getServiceSpecificError(), ALREADY_EXISTS);

    status = service->unregisterObserver(observer);
    EXPECT_TRUE(status.isOk());
}

TEST_F(ResourceObserverNativeTest, testResourceObserverBusy) {
    LOG(INFO) << "testResourceObserverBusy";

    testResourceObserver(MediaObservableEvent::kBusy);
}

TEST_F(ResourceObserverNativeTest, testResourceObserverIdle) {
    LOG(INFO) << "testResourceObserverIdle";

    testResourceObserver(MediaObservableEvent::kIdle);
}

TEST_F(ResourceObserverNativeTest, testResourceObserverAll) {
    LOG(INFO) << "testResourceObserverAll";

    testResourceObserver(MediaObservableEvent::kAll);
}
