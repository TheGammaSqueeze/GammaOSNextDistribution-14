/*
 * Copyright 2022 The Android Open Source Project
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

#include "Enumerator.h"
#include "MockHidlEvsHal_1_0.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "utils/include/Utils.h"
#include "wrappers/include/AidlDisplay.h"
#include "wrappers/include/AidlEnumerator.h"
#include "wrappers/include/HidlDisplay.h"
#include "wrappers/include/HidlEnumerator.h"

#include <cutils/android_filesystem_config.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>

#include <unistd.h>

#include <cstdlib>
#include <future>

namespace {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::Stream;

using FrameCallbackFunc =
        std::function<::android::hardware::Return<void>(const hidlevs::V1_0::BufferDesc&)>;
using StreamStartedCallbackFunc = std::function<void()>;

constexpr size_t kNumMockEvsCameras = 4;
constexpr size_t kNumMockEvsDisplays = 2;

const std::unordered_set<int32_t> gAllowedUid({AID_ROOT, AID_SYSTEM, AID_AUTOMOTIVE_EVS});

constexpr auto doNothingFunc = []() { /* do nothing */ };

}  // namespace

namespace aidl::android::automotive::evs::implementation {

// This test verifies evsmanagerd implementation with a mock HIDL IEvs*
// implementations and a HIDL EVS client.
class EvsEnumeratorHidlUnitTest_1_0 : public ::testing::Test {
public:
    EvsEnumeratorHidlUnitTest_1_0() {
        // Instantiates IEvsEnumerator
        mAidlEnumerator = ndk::SharedRefBase::make<Enumerator>();
        EXPECT_NE(nullptr, mAidlEnumerator);

        // Disable a permission check
        mAidlEnumerator->enablePermissionCheck(/* enable= */ false);
    }

    ~EvsEnumeratorHidlUnitTest_1_0() override {
        // Clean-up work should be here.  No exception should be thrown.
    }

    void SetUp() override {
        // Additional place to set up the test environment.  This will be called
        // right after the constructor.
        mMockEvsHal = std::make_shared<MockHidlEvsHal_1_0>(kNumMockEvsCameras, kNumMockEvsDisplays);
        EXPECT_NE(nullptr, mMockEvsHal);
        mMockEvsHal->initialize();

        ::android::sp<hidlevs::V1_0::IEvsEnumerator> mockEnumerator = mMockEvsHal->getEnumerator();
        EXPECT_NE(nullptr, mockEnumerator);

        std::shared_ptr<AidlEnumerator> aidlWrapper =
                ndk::SharedRefBase::make<AidlEnumerator>(mockEnumerator);
        std::shared_ptr<IEvsEnumerator> hwEnumerator =
                IEvsEnumerator::fromBinder(aidlWrapper->asBinder());
        EXPECT_TRUE(mAidlEnumerator->init(hwEnumerator, /* enableMonitor= */ true));

        mEnumerator = new (std::nothrow) HidlEnumerator(mAidlEnumerator);
    }

    void TearDown() override {
        // This will be called immediately after each test; right before the
        // destructor.
    }

    bool VerifyCameraStream(const hidlevs::V1_0::CameraDesc& desc, size_t framesToReceive,
                            std::chrono::duration<long double> maxInterval,
                            std::chrono::duration<long double> stopTimeout, const std::string& name,
                            StreamStartedCallbackFunc cb);

protected:
    // Class members declared here can be used by all tests in the test suite
    // for EvsEnumerator
    std::shared_ptr<Enumerator> mAidlEnumerator;
    ::android::sp<HidlEnumerator> mEnumerator;
    std::shared_ptr<MockHidlEvsHal_1_0> mMockEvsHal;

    class StreamCallback : public hidlevs::V1_0::IEvsCameraStream {
    public:
        StreamCallback(FrameCallbackFunc frameCallbackFunc) : mFrameCallback(frameCallbackFunc) {}

        ::android::hardware::Return<void> deliverFrame(
                const hidlevs::V1_0::BufferDesc& buffer) override {
            return mFrameCallback(buffer);
        }

    private:
        FrameCallbackFunc mFrameCallback;
    };
};

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyPermissionCheck) {
    bool isAllowedUid = gAllowedUid.find(getuid()) != gAllowedUid.end();
    mAidlEnumerator->enablePermissionCheck(true);

    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> list;
    if (!isAllowedUid) {
        EXPECT_TRUE(mEnumerator->getCameraList([&list](auto received) { list = received; }).isOk());
        EXPECT_EQ(0, list.size());

        ::android::sp<hidlevs::V1_0::IEvsDisplay> display = mEnumerator->openDisplay();
        EXPECT_EQ(display, nullptr);
        EXPECT_TRUE(mEnumerator->closeDisplay(display).isOk());
    }

    // TODO(b/240619903): Adds more lines to verify the behavior when
    //                    current user is allowed to use the EVS service.
    mAidlEnumerator->enablePermissionCheck(false);
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyOpenAndCloseDisplay) {
    ::android::sp<hidlevs::V1_0::IEvsDisplay> d = mEnumerator->openDisplay();
    EXPECT_NE(nullptr, d);

    hidlevs::V1_0::DisplayDesc desc;
    d->getDisplayInfo([&desc](const auto& read) { desc = read; });
    EXPECT_EQ(0, desc.vendorFlags);

    EXPECT_EQ(d->getDisplayState(), hidlevs::V1_0::DisplayState::NOT_VISIBLE);

    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK,
              d->setDisplayState(hidlevs::V1_0::DisplayState::VISIBLE));
    EXPECT_EQ(hidlevs::V1_0::DisplayState::VISIBLE, d->getDisplayState());

    EXPECT_TRUE(mEnumerator->closeDisplay(d).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyOpenAndCloseCamera) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> hidlCameras;
    EXPECT_TRUE(
            mEnumerator->getCameraList([&hidlCameras](auto received) { hidlCameras = received; })
                    .isOk());
    EXPECT_EQ(kNumMockEvsCameras, hidlCameras.size());

    for (const auto& camera : hidlCameras) {
        ::android::sp<hidlevs::V1_0::IEvsCamera> c = mEnumerator->openCamera(camera.cameraId);
        EXPECT_NE(nullptr, c);

        hidlevs::V1_0::CameraDesc desc;
        c->getCameraInfo([&desc](const auto& read) { desc = read; });
        EXPECT_EQ(desc.cameraId, camera.cameraId);
        EXPECT_EQ(desc.vendorFlags, camera.vendorFlags);

        const uint32_t id = std::rand();
        const int32_t v = std::rand();
        EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, c->setExtendedInfo(id, v));
        EXPECT_EQ(v, c->getExtendedInfo(id));

        EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());
    }
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyStartAndStopVideoStream) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> cameras;
    mEnumerator->getCameraList([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 1000ms;
    constexpr auto kStopTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task(
                std::bind(&EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream, this, desc,
                          kFramesToReceive, kMaxFrameInterval, kStopTimeout, desc.cameraId,
                          doNothingFunc));
        std::future<bool> result = task.get_future();
        std::thread t(std::move(task));
        t.detach();

        EXPECT_EQ(std::future_status::ready, result.wait_for(kResultTimeout));
        EXPECT_TRUE(result.get());

        // TODO(b/250699038): This test will likely fail to request a video
        //                    stream on the next camera without this interval.
        std::this_thread::sleep_for(500ms);
    }
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyMultipleClientsStreaming) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> cameras;
    mEnumerator->getCameraList([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kStopTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task0(
                std::bind(&EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream, this, desc,
                          kFramesToReceive, kMaxFrameInterval, kStopTimeout, "client0",
                          doNothingFunc));
        std::packaged_task<bool()> task1(
                std::bind(&EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream, this, desc,
                          kFramesToReceive, kMaxFrameInterval, kStopTimeout, "client1",
                          doNothingFunc));

        std::future<bool> result0 = task0.get_future();
        std::future<bool> result1 = task1.get_future();

        std::thread t0(std::move(task0));
        std::thread t1(std::move(task1));
        t0.detach();
        t1.detach();

        EXPECT_EQ(std::future_status::ready, result0.wait_for(kResultTimeout));
        EXPECT_EQ(std::future_status::ready, result1.wait_for(kResultTimeout));
        EXPECT_TRUE(result0.get());
        EXPECT_TRUE(result1.get());

        // TODO(b/250699038): This test will likely fail to request a video
        //                    stream on the next camera without this interval.
        std::this_thread::sleep_for(500ms);
    }
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyMultipleCamerasStreaming) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> cameras;
    mEnumerator->getCameraList([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kStopTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto i = 0; i < cameras.size() - 1; ++i) {
        const auto& desc0 = cameras[i];
        const auto& desc1 = cameras[i + 1];

        std::packaged_task<bool()> task0(
                std::bind(&EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream, this, desc0,
                          kFramesToReceive, kMaxFrameInterval, kStopTimeout, desc0.cameraId,
                          doNothingFunc));
        std::packaged_task<bool()> task1(
                std::bind(&EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream, this, desc1,
                          kFramesToReceive, kMaxFrameInterval, kStopTimeout, desc1.cameraId,
                          doNothingFunc));

        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::future<bool> result0 = task0.get_future();
        std::future<bool> result1 = task1.get_future();

        std::thread t0(std::move(task0));
        std::thread t1(std::move(task1));
        t0.detach();
        t1.detach();

        EXPECT_EQ(std::future_status::ready, result0.wait_for(kResultTimeout));
        EXPECT_EQ(std::future_status::ready, result1.wait_for(kResultTimeout));
        EXPECT_TRUE(result0.get());
        EXPECT_TRUE(result1.get());

        // TODO(b/250699038): This test will likely fail to request a video
        //                    stream on the next camera without this interval.
        std::this_thread::sleep_for(500ms);
    }
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyGetCameraInfo) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> cameras;
    mEnumerator->getCameraList([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    ::android::sp<hidlevs::V1_0::IEvsCamera> c = mEnumerator->openCamera(cameras[0].cameraId);
    EXPECT_NE(nullptr, c);

    hidlevs::V1_0::CameraDesc desc;
    c->getCameraInfo([&desc](const auto& read) { desc = read; });
    EXPECT_EQ(desc, cameras[0]);
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyExtendedInfo) {
    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> cameras;
    mEnumerator->getCameraList([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    ::android::sp<hidlevs::V1_0::IEvsCamera> c = mEnumerator->openCamera(cameras[0].cameraId);
    EXPECT_NE(nullptr, c);

    const uint32_t id = std::rand();
    const int32_t value = std::rand();
    hidlevs::V1_0::EvsResult result = c->setExtendedInfo(id, value);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);

    int32_t read = c->getExtendedInfo(id);
    EXPECT_EQ(value, read);

    constexpr int invalidId = 0x10;
    read = c->getExtendedInfo(invalidId);
    EXPECT_EQ(0, read);
}

TEST_F(EvsEnumeratorHidlUnitTest_1_0, VerifyDisplayBuffer) {
    ::android::sp<hidlevs::V1_0::IEvsDisplay> d = mEnumerator->openDisplay();
    EXPECT_NE(nullptr, d);

    hidlevs::V1_0::BufferDesc b;
    d->getTargetBuffer([&b](const hidlevs::V1_0::BufferDesc& buffer) { b = buffer; });
    EXPECT_NE(nullptr, b.memHandle);

    hidlevs::V1_0::EvsResult r = d->returnTargetBufferForDisplay(b);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, r);

    mEnumerator->closeDisplay(d);
}

bool EvsEnumeratorHidlUnitTest_1_0::VerifyCameraStream(
        const hidlevs::V1_0::CameraDesc& desc, size_t framesToReceive,
        std::chrono::duration<long double> maxInterval,
        std::chrono::duration<long double> stopTimeout, const std::string& name,
        StreamStartedCallbackFunc callback) {
    std::mutex m;
    std::condition_variable cv;
    hidlevs::V1_0::BufferDesc receivedFrame;
    size_t counter = 0;
    bool gotFrameCallback = false, gotFirstFrame = false, gotNullFrame = false;
    auto frameCb = FrameCallbackFunc([&](const hidlevs::V1_0::BufferDesc& forwarded) {
        std::lock_guard lk(m);
        receivedFrame = forwarded;

        LOG(INFO) << name << " received a frame, " << ++counter;
        if (!gotFirstFrame) {
            callback();
            gotFirstFrame = true;
        }

        if (forwarded.memHandle != nullptr) {
            gotFrameCallback = true;
        } else {
            gotNullFrame = true;
        }
        cv.notify_all();
        return ::android::hardware::Void();
    });

    ::android::sp<hidlevs::V1_0::IEvsCamera> c = mEnumerator->openCamera(desc.cameraId);
    EXPECT_NE(nullptr, c);

    // Request to start a video stream and wait for a given number of frames.
    ::android::sp<StreamCallback> cb = new (std::nothrow) StreamCallback(frameCb);
    EXPECT_NE(nullptr, cb);
    EXPECT_TRUE(c->startVideoStream(cb).isOk());

    std::unique_lock lk(m);
    for (auto i = 0; i < framesToReceive; ++i) {
        EXPECT_TRUE(cv.wait_for(lk, maxInterval, [&gotFrameCallback] { return gotFrameCallback; }));
        EXPECT_TRUE(gotFrameCallback);
        if (!gotFrameCallback) {
            continue;
        }

        EXPECT_TRUE(c->doneWithFrame(receivedFrame).isOk());
        gotFrameCallback = false;
    }
    lk.unlock();

    // Request to stop a video stream and wait.
    EXPECT_TRUE(c->stopVideoStream().isOk());

    lk.lock();
    EXPECT_TRUE(cv.wait_for(lk, stopTimeout, [&gotNullFrame] { return gotNullFrame; }));
    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());

    return true;
}

}  // namespace aidl::android::automotive::evs::implementation
