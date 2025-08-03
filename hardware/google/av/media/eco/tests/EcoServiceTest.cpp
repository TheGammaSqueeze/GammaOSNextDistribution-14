/*
 * Copyright (C) 2019 The Android Open Source Project
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

// Unit Test for ECOService.

#define LOG_NDEBUG 0
#define LOG_TAG "ECOServiceTest"

#include <android-base/unique_fd.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <android/binder_parcel.h>
#include <cutils/ashmem.h>
#include <gtest/gtest.h>
#include <math.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <utils/Log.h>
#include <utils/Timers.h>

#include "FakeECOServiceInfoListener.h"
#include "FakeECOServiceStatsProvider.h"
#include "eco/ECOService.h"
#include "eco/ECOUtils.h"

namespace android {
namespace media {
namespace eco {

using ::ndk::ScopedAStatus;

namespace {

static constexpr uint32_t kTestWidth = 1280;
static constexpr uint32_t kTestHeight = 720;
static constexpr bool kIsCameraRecording = true;
static constexpr float kFrameRate = 30.0f;

}  // namespace

// A helper class to help create ECOService and manage ECOService.
class EcoServiceTest : public ::testing::Test {
public:
    EcoServiceTest() { ALOGD("EcoServiceTest created"); }

    std::shared_ptr<IECOService> createService() {
        mECOService = IECOService::fromBinder(
                ndk::SpAIBinder(AServiceManager_waitForService("media.ecoservice")));
        if (mECOService == nullptr) {
            ALOGE("Failed to connect to ecoservice");
            return nullptr;
        }

        return mECOService;
    }

    ~EcoServiceTest() { ALOGD("EcoServiceTest destroyed"); }

private:
    std::shared_ptr<IECOService> mECOService = nullptr;
};

TEST_F(EcoServiceTest, NormalObtainSessionWithInvalidWidth) {
    std::shared_ptr<IECOService> service = createService();
    EXPECT_TRUE(service != nullptr);

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(-1 /* width */, kTestHeight, kIsCameraRecording, &session);
    EXPECT_FALSE(session);
}

TEST_F(EcoServiceTest, NormalObtainSessionWithInvalidHeight) {
    std::shared_ptr<IECOService> service = createService();

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(kTestWidth, -1 /* height */, kIsCameraRecording, &session);
    EXPECT_FALSE(session);
}

TEST_F(EcoServiceTest, NormalObtainSessionWithCameraRecordingFalse) {
    std::shared_ptr<IECOService> service = createService();

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, false /* isCameraRecording */, &session);
    EXPECT_TRUE(session);
}

TEST_F(EcoServiceTest, NormalObtainSingleSession) {
    std::shared_ptr<IECOService> service = createService();
    EXPECT_TRUE(service != nullptr);

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, kIsCameraRecording, &session);
    EXPECT_TRUE(session);
}

TEST_F(EcoServiceTest, NormalObtainSessionTwice) {
    std::shared_ptr<IECOService> service = createService();
    EXPECT_TRUE(service != nullptr);

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session1 = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, kIsCameraRecording, &session1);
    EXPECT_TRUE(session1);

    std::shared_ptr<IECOSession> session2 = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, kIsCameraRecording, &session2);
    EXPECT_TRUE(session2);

    // The two session instances should be the same.
    EXPECT_TRUE(session1->asBinder() == session2->asBinder());
}

TEST_F(EcoServiceTest, ObtainTwoSessions) {
    std::shared_ptr<IECOService> service = createService();
    EXPECT_TRUE(service != nullptr);

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session1 = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, kIsCameraRecording, &session1);
    EXPECT_TRUE(session1);

    std::shared_ptr<IECOSession> session2 = nullptr;

    service->obtainSession(kTestWidth - 1, kTestHeight - 1, kIsCameraRecording, &session2);
    EXPECT_TRUE(session2);

    // The two session instances must not be the same.
    EXPECT_TRUE(session1->asBinder() != session2->asBinder());

    // Check the session number.
    int32_t count = 0;
    service->getNumOfSessions(&count);
    EXPECT_EQ(count, 2);

    // Get the list of sessions from service.
    std::vector<::ndk::SpAIBinder> sessionList;
    service->getSessions(&sessionList);
    bool foundFirstSession = false, foundSecondSession = false;

    for (std::vector<::ndk::SpAIBinder>::iterator it = sessionList.begin(); it != sessionList.end();
         ++it) {
        if (session1->asBinder() == it->get()) {
            foundFirstSession = true;
        }
        if (session2->asBinder() == it->get()) {
            foundSecondSession = true;
        }
    }

    // Expect found both sessions.
    EXPECT_TRUE(foundFirstSession);
    EXPECT_TRUE(foundSecondSession);
}

TEST_F(EcoServiceTest, TestNormalFlowWithOneListenerAndOneProvider) {
    std::shared_ptr<IECOService> service = createService();
    EXPECT_TRUE(service != nullptr);

    // Provider obtains the session from the service.
    std::shared_ptr<IECOSession> session = nullptr;

    service->obtainSession(kTestWidth, kTestHeight, kIsCameraRecording, &session);
    EXPECT_TRUE(session);

    // Create provider and add it to the session.
    std::shared_ptr<FakeECOServiceStatsProvider> fakeProvider =
            ndk::SharedRefBase::make<FakeECOServiceStatsProvider>(kTestWidth, kTestHeight,
                                                                  kIsCameraRecording, kFrameRate);
    fakeProvider->setECOSession(session);

    ECOData providerConfig(ECOData::DATA_TYPE_STATS_PROVIDER_CONFIG,
                           systemTime(SYSTEM_TIME_BOOTTIME));
    providerConfig.setString(KEY_PROVIDER_NAME, "FakeECOServiceStatsProvider");
    providerConfig.setInt32(KEY_PROVIDER_TYPE,
                            ECOServiceStatsProvider::STATS_PROVIDER_TYPE_VIDEO_ENCODER);
    bool res;
    ScopedAStatus status = session->addStatsProvider(fakeProvider, providerConfig, &res);

    // Create listener and add it to the session.
    std::shared_ptr<FakeECOServiceInfoListener> fakeListener =
            ndk::SharedRefBase::make<FakeECOServiceInfoListener>(kTestWidth, kTestHeight,
                                                                 kIsCameraRecording);
    fakeListener->setECOSession(session);

    // Create the listener config.
    ECOData listenerConfig(ECOData::DATA_TYPE_INFO_LISTENER_CONFIG,
                           systemTime(SYSTEM_TIME_BOOTTIME));
    listenerConfig.setString(KEY_LISTENER_NAME, "FakeECOServiceInfoListener");
    listenerConfig.setInt32(KEY_LISTENER_TYPE, ECOServiceInfoListener::INFO_LISTENER_TYPE_CAMERA);

    // Specify the qp thresholds for receiving notification.
    listenerConfig.setInt32(KEY_LISTENER_QP_BLOCKINESS_THRESHOLD, 40);
    listenerConfig.setInt32(KEY_LISTENER_QP_CHANGE_THRESHOLD, 5);

    status = session->addInfoListener(fakeListener, listenerConfig, &res);
}

}  // namespace eco
}  // namespace media
}  // namespace android
