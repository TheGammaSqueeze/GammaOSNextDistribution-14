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

#include "Constants.h"
#include "Enumerator.h"
#include "MockHidlEvsHal.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "hardware/gralloc.h"
#include "utils/include/Utils.h"
#include "wrappers/include/AidlCamera.h"
#include "wrappers/include/AidlDisplay.h"
#include "wrappers/include/AidlEnumerator.h"
#include "wrappers/include/HidlDisplay.h"
#include "wrappers/include/HidlEnumerator.h"

#include <cutils/android_filesystem_config.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/GraphicBufferAllocator.h>

#include <unistd.h>

#include <cstdlib>
#include <future>

namespace {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::aidl::android::hardware::automotive::evs::Stream;

using FrameCallbackFunc = std::function<::android::hardware::Return<void>(
        const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>&)>;
using FrameCallbackFunc_1_0 =
        std::function<::android::hardware::Return<void>(const hidlevs::V1_0::BufferDesc&)>;
using EventCallbackFunc =
        std::function<::android::hardware::Return<void>(const hidlevs::V1_1::EvsEventDesc&)>;
using StreamStartedCallbackFunc = std::function<void()>;

constexpr size_t kNumMockEvsCameras = 4;
constexpr size_t kNumMockEvsDisplays = 2;

const std::unordered_set<int32_t> gAllowedUid({AID_ROOT, AID_SYSTEM, AID_AUTOMOTIVE_EVS});

constexpr auto doNothingFunc = []() { /* do nothing */ };

}  // namespace

namespace aidl::android::automotive::evs::implementation {

// This test verifies evsmanagerd implementation with a mock HIDL IEvs*
// implementations and a HIDL EVS client.
class EvsEnumeratorHidlUnitTest : public ::testing::Test {
public:
    EvsEnumeratorHidlUnitTest() {
        // Instantiates IEvsEnumerator
        mAidlEnumerator = ndk::SharedRefBase::make<Enumerator>();
        EXPECT_NE(nullptr, mAidlEnumerator);

        // Disable a permission check
        mAidlEnumerator->enablePermissionCheck(/* enable= */ false);
    }

    ~EvsEnumeratorHidlUnitTest() override {
        // Clean-up work should be here.  No exception should be thrown.
    }

    void SetUp() override {
        // Additional place to set up the test environment.  This will be called
        // right after the constructor.
        mMockEvsHal = std::make_shared<MockHidlEvsHal>(kNumMockEvsCameras, kNumMockEvsDisplays);
        EXPECT_NE(nullptr, mMockEvsHal);
        mMockEvsHal->initialize();

        ::android::sp<hidlevs::V1_0::IEvsEnumerator> mockEnumerator = mMockEvsHal->getEnumerator();
        EXPECT_NE(nullptr, mockEnumerator);

        std::shared_ptr<AidlEnumerator> aidlWrapper =
                ndk::SharedRefBase::make<AidlEnumerator>(mockEnumerator);
        std::shared_ptr<IEvsEnumerator> hwEnumerator =
                IEvsEnumerator::fromBinder(aidlWrapper->asBinder());
        mAidlEnumerator->init(hwEnumerator, /* enableMonitor= */ true);

        mEnumerator = new (std::nothrow) HidlEnumerator(mAidlEnumerator);
    }

    void TearDown() override {
        // This will be called immediately after each test; right before the
        // destructor.
    }

    bool VerifyCameraStream(const hidlevs::V1_1::CameraDesc& desc, size_t framesToReceive,
                            std::chrono::duration<long double> maxInterval,
                            std::chrono::duration<long double> eventTimeout,
                            const std::string& name, StreamStartedCallbackFunc cb);

    bool VerifyCameraStream_1_0(const hidlevs::V1_0::CameraDesc& desc, size_t framesToReceive,
                                std::chrono::duration<long double> maxInterval,
                                std::chrono::duration<long double> stopTimeout,
                                const std::string& name, StreamStartedCallbackFunc cb);

protected:
    // Class members declared here can be used by all tests in the test suite
    // for EvsEnumerator
    std::shared_ptr<Enumerator> mAidlEnumerator;
    ::android::sp<HidlEnumerator> mEnumerator;
    std::shared_ptr<MockHidlEvsHal> mMockEvsHal;

    class StreamCallback : public hidlevs::V1_1::IEvsCameraStream {
    public:
        StreamCallback(FrameCallbackFunc frameCallbackFunc, EventCallbackFunc eventCallbackFunc) :
              mFrameCallback(frameCallbackFunc), mEventCallback(eventCallbackFunc) {}

        ::android::hardware::Return<void> deliverFrame(
                const hidlevs::V1_0::BufferDesc& buffer) override {
            ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc> frames(1);
            AHardwareBuffer_Desc* p =
                    reinterpret_cast<AHardwareBuffer_Desc*>(&frames[0].buffer.description);
            p->width = buffer.width;
            p->height = buffer.height;
            p->layers = 1;
            p->format = buffer.format;
            p->usage = buffer.usage;
            p->stride = buffer.stride;

            frames[0].buffer.nativeHandle = buffer.memHandle;
            frames[0].pixelSize = buffer.pixelSize;
            frames[0].bufferId = buffer.bufferId;
            return mFrameCallback(frames);
        }

        ::android::hardware::Return<void> deliverFrame_1_1(
                const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>& buffers) override {
            return mFrameCallback(buffers);
        }

        ::android::hardware::Return<void> notify(const hidlevs::V1_1::EvsEventDesc& event) {
            return mEventCallback(event);
        }

    private:
        FrameCallbackFunc mFrameCallback;
        EventCallbackFunc mEventCallback;
    };

    class StreamCallback_1_0 : public hidlevs::V1_0::IEvsCameraStream {
    public:
        StreamCallback_1_0(FrameCallbackFunc_1_0 frameCallbackFunc) :
              mFrameCallback(frameCallbackFunc) {}

        ::android::hardware::Return<void> deliverFrame(
                const hidlevs::V1_0::BufferDesc& buffer) override {
            return mFrameCallback(buffer);
        }

    private:
        FrameCallbackFunc_1_0 mFrameCallback;
    };
};

TEST_F(EvsEnumeratorHidlUnitTest, VerifyPermissionCheck) {
    bool isAllowedUid = gAllowedUid.find(getuid()) != gAllowedUid.end();
    mAidlEnumerator->enablePermissionCheck(true);

    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> list;
    if (!isAllowedUid) {
        EXPECT_TRUE(
                mEnumerator->getCameraList_1_1([&list](auto received) { list = received; }).isOk());
        EXPECT_EQ(0, list.size());

        ::android::sp<hidlevs::V1_1::IEvsCamera> invalidCamera =
                mEnumerator->openCamera_1_1(/* cameraId= */ "invalidId", /* streamCfg= */ {});
        EXPECT_EQ(invalidCamera, nullptr);
        EXPECT_TRUE(mEnumerator->closeCamera(invalidCamera).isOk());

        ::android::sp<hidlevs::V1_1::IEvsDisplay> invalidDisplay =
                mEnumerator->openDisplay_1_1(/* displayId= */ 0xFF);
        EXPECT_EQ(invalidDisplay, nullptr);

        hidlevs::V1_0::DisplayState displayState = mEnumerator->getDisplayState();
        EXPECT_EQ(hidlevs::V1_0::DisplayState::NOT_OPEN, displayState);
    }

    // TODO(b/240619903): Adds more lines to verify the behavior when
    //                    current user is allowed to use the EVS service.
    mAidlEnumerator->enablePermissionCheck(false);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyIsHardwareMethod) {
    EXPECT_FALSE(mEnumerator->isHardware());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseDisplay) {
    std::vector<uint8_t> displays;
    EXPECT_TRUE(mEnumerator->getDisplayIdList([&displays](const auto& list) { displays = list; })
                        .isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    for (auto& id : displays) {
        ::android::sp<hidlevs::V1_1::IEvsDisplay> h0, h1;
        h0 = mEnumerator->openDisplay_1_1(id);
        EXPECT_NE(nullptr, h0);

        h1 = mEnumerator->openDisplay_1_1(id);
        EXPECT_NE(nullptr, h1);

        ::android::ui::DisplayMode displayMode;
        ::android::ui::DisplayState displayState;
        EXPECT_TRUE(
                h1->getDisplayInfo_1_1([&](const auto& config, const auto& state) {
                      displayMode =
                              *(reinterpret_cast<const ::android::ui::DisplayMode*>(config.data()));
                      displayState =
                              *(reinterpret_cast<const ::android::ui::DisplayState*>(state.data()));
                  }).isOk());

        hidlevs::V1_0::DisplayState state = mEnumerator->getDisplayState();
        EXPECT_EQ(hidlevs::V1_0::DisplayState::NOT_VISIBLE, state);

        EXPECT_TRUE(mEnumerator->closeDisplay(h1).isOk());

        // closeDisplay() with an invalidated display handle should be okay.
        EXPECT_TRUE(mEnumerator->closeDisplay(h0).isOk());
    }
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseDisplay_1_0) {
    ::android::sp<hidlevs::V1_0::IEvsDisplay> d = mEnumerator->openDisplay();
    EXPECT_NE(nullptr, d);

    hidlevs::V1_0::DisplayDesc desc;
    d->getDisplayInfo([&desc](const auto& read) { desc = read; });
    EXPECT_EQ(0, desc.vendorFlags);

    ::aidl::android::hardware::automotive::evs::DisplayDesc aidlDesc = Utils::makeFromHidl(desc);
    EXPECT_EQ(aidlDesc.id, desc.displayId);
    EXPECT_EQ(aidlDesc.vendorFlags, desc.vendorFlags);

    EXPECT_EQ(d->getDisplayState(), hidlevs::V1_0::DisplayState::NOT_VISIBLE);

    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK,
              d->setDisplayState(hidlevs::V1_0::DisplayState::VISIBLE));
    EXPECT_EQ(hidlevs::V1_0::DisplayState::VISIBLE, d->getDisplayState());

    EXPECT_TRUE(mEnumerator->closeDisplay(d).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseCamera) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> hidlCameras;
    EXPECT_TRUE(
            mEnumerator
                    ->getCameraList_1_1([&hidlCameras](auto received) { hidlCameras = received; })
                    .isOk());
    EXPECT_EQ(kNumMockEvsCameras, hidlCameras.size());

    std::vector<CameraDesc> aidlCameras;
    EXPECT_TRUE(mAidlEnumerator->getCameraList(&aidlCameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, aidlCameras.size());

    for (auto i = 0; i < hidlCameras.size(); ++i) {
        CameraDesc& aidlCamera = aidlCameras[i];
        hidlevs::V1_1::CameraDesc hidlCamera = hidlCameras[i];

        std::vector<Stream> configs;
        EXPECT_TRUE(mAidlEnumerator->getStreamList(aidlCamera, &configs).isOk());
        EXPECT_FALSE(configs.empty());

        ::android::hardware::camera::device::V3_2::Stream hidlStreamConfig =
                Utils::makeToHidl(configs[0]);

        ::android::sp<hidlevs::V1_1::IEvsCamera> h0 =
                mEnumerator->openCamera_1_1(hidlCamera.v1.cameraId, hidlStreamConfig);
        ::android::sp<hidlevs::V1_1::IEvsCamera> h1 =
                mEnumerator->openCamera_1_1(hidlCamera.v1.cameraId, hidlStreamConfig);
        EXPECT_NE(nullptr, h0);
        EXPECT_NE(nullptr, h1);

        EXPECT_TRUE(mEnumerator->closeCamera(h1).isOk());
        EXPECT_TRUE(mEnumerator->closeCamera(h0).isOk());
    }
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseCamera_1_0) {
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

TEST_F(EvsEnumeratorHidlUnitTest, CloseInvalidEvsCamera) {
    ::android::sp<hidlevs::V1_1::IEvsCamera> invalidCamera;
    EXPECT_TRUE(mEnumerator->closeCamera(invalidCamera).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyExclusiveDisplayOwner) {
    ::android::sp<hidlevs::V1_1::IEvsDisplay> success =
            mEnumerator->openDisplay_1_1(kExclusiveDisplayId);
    EXPECT_NE(nullptr, success);

    ::android::sp<hidlevs::V1_1::IEvsDisplay> failed = mEnumerator->openDisplay_1_1(/* id= */ 0);
    EXPECT_EQ(nullptr, failed);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyStartAndStopVideoStream) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task(std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream,
                                                  this, desc, kFramesToReceive, kMaxFrameInterval,
                                                  kEventTimeout, desc.v1.cameraId, doNothingFunc));
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

TEST_F(EvsEnumeratorHidlUnitTest, VerifyStartAndStopVideoStream_1_0) {
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

        std::packaged_task<bool()> task(
                std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream_1_0, this, desc,
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

TEST_F(EvsEnumeratorHidlUnitTest, VerifyMultipleClientsStreaming) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task0(std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream,
                                                   this, desc, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, "client0", doNothingFunc));
        std::packaged_task<bool()> task1(std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream,
                                                   this, desc, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, "client1", doNothingFunc));

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

TEST_F(EvsEnumeratorHidlUnitTest, VerifyMultipleCamerasStreaming) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto i = 0; i < cameras.size() - 1; ++i) {
        const auto& desc0 = cameras[i];
        const auto& desc1 = cameras[i + 1];

        std::packaged_task<bool()> task0(std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream,
                                                   this, desc0, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, desc0.v1.cameraId,
                                                   doNothingFunc));
        std::packaged_task<bool()> task1(std::bind(&EvsEnumeratorHidlUnitTest::VerifyCameraStream,
                                                   this, desc1, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, desc1.v1.cameraId,
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

TEST_F(EvsEnumeratorHidlUnitTest, VerifyPrimaryCameraClient) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<uint8_t> displays;
    EXPECT_TRUE(mEnumerator->getDisplayIdList([&displays](const auto& list) { displays = list; })
                        .isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    ::android::sp<hidlevs::V1_1::IEvsDisplay> validDisplay =
            mEnumerator->openDisplay_1_1(/* id= */ 0xFF);
    EXPECT_NE(nullptr, validDisplay);
    ::android::sp<hidlevs::V1_1::IEvsDisplay> invalidDisplay =
            mEnumerator->openDisplay_1_1(displays[0]);
    EXPECT_EQ(nullptr, invalidDisplay);

    ::android::sp<hidlevs::V1_1::IEvsCamera> c0 =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c0);
    ::android::sp<hidlevs::V1_1::IEvsCamera> c1 =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c1);

    hidlevs::V1_0::EvsResult r0 = c0->forceMaster(validDisplay);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, r0);

    ::android::sp<HidlDisplay> hidlDisplay = reinterpret_cast<HidlDisplay*>(validDisplay.get());
    EXPECT_NE(nullptr, hidlDisplay);
    EXPECT_NE(nullptr, hidlDisplay->getAidlDisplay());

    std::shared_ptr<AidlDisplay> aidlDisplay = ndk::SharedRefBase::make<AidlDisplay>(hidlDisplay);
    EXPECT_NE(nullptr, aidlDisplay);
    EXPECT_NE(nullptr, aidlDisplay->getHidlDisplay());

    hidlevs::V1_0::EvsResult r1 = c1->forceMaster(invalidDisplay);
    EXPECT_NE(hidlevs::V1_0::EvsResult::OK, r1);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyPrimaryCameraClientViaAidlCameraWrapper) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<uint8_t> displays;
    EXPECT_TRUE(mEnumerator->getDisplayIdList([&displays](const auto& list) { displays = list; })
                        .isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    ::android::sp<hidlevs::V1_1::IEvsDisplay> validDisplay =
            mEnumerator->openDisplay_1_1(/* id= */ 0xFF);
    EXPECT_NE(nullptr, validDisplay);

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);

    hidlevs::V1_0::EvsResult r = c->forceMaster(validDisplay);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, r);

    std::shared_ptr<AidlDisplay> aidlDisplay = ndk::SharedRefBase::make<AidlDisplay>(
            reinterpret_cast<HidlDisplay*>(validDisplay.get()));
    EXPECT_NE(nullptr, aidlDisplay);
    EXPECT_NE(nullptr, aidlDisplay->getHidlDisplay());

    // Create AidlCamera object with
    // android::hardware::automotive::evs::V1_1::IEvsCamera object and repeat
    // tests.
    std::shared_ptr<AidlCamera> aidlCamera = ndk::SharedRefBase::make<AidlCamera>(c);
    EXPECT_NE(nullptr, aidlCamera);

    // A target camera already has a primary client so below call should fail.
    EXPECT_FALSE(aidlCamera->setPrimaryClient().isOk());

    // Try to take over a target camera and release.
    EXPECT_TRUE(aidlCamera->forcePrimaryClient(aidlDisplay).isOk());
    EXPECT_TRUE(aidlCamera->unsetPrimaryClient().isOk());

    // Own a target camera again and repeat tests in V1_0 mode.
    EXPECT_TRUE(aidlCamera->setPrimaryClient().isOk());
    aidlCamera = ndk::SharedRefBase::make<AidlCamera>(c, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlCamera);

    // Try to take over a target camera and release; below calls should fail
    // because android::hardware::automotive::evs::V1_0::IEvsCamera does not
    // support a concept of the primary ownership.
    EXPECT_FALSE(aidlCamera->setPrimaryClient().isOk());
    EXPECT_FALSE(aidlCamera->forcePrimaryClient(aidlDisplay).isOk());
    EXPECT_FALSE(aidlCamera->unsetPrimaryClient().isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyGetCameraInfo) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);

    hidlevs::V1_1::CameraDesc desc;
    c->getCameraInfo_1_1([&desc](const auto& read) { desc = read; });
    EXPECT_EQ(desc, cameras[0]);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyExtendedInfo) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);

    constexpr int id = 0x12;
    ::android::hardware::hidl_vec<uint8_t> value({1, 2, 3, 4});
    hidlevs::V1_0::EvsResult result = c->setExtendedInfo_1_1(id, value);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);

    ::android::hardware::hidl_vec<uint8_t> read;
    result = hidlevs::V1_0::EvsResult::OK;
    c->getExtendedInfo_1_1(id,
                           [&result, &read](const auto r, const auto& v) { result = r, read = v; });
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);
    EXPECT_TRUE(std::equal(value.begin(), value.end(), read.begin()));

    constexpr int invalidId = 0x10;
    EXPECT_TRUE(c->getExtendedInfo_1_1(invalidId, [&result, &read](const auto& r, const auto& v) {
                     result = r, read = v;
                 }).isOk());
    EXPECT_NE(hidlevs::V1_0::EvsResult::OK, result);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyIntParameters) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<uint8_t> displays;
    EXPECT_TRUE(mEnumerator->getDisplayIdList([&displays](const auto& list) { displays = list; })
                        .isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);

    ::android::sp<hidlevs::V1_1::IEvsDisplay> validDisplay =
            mEnumerator->openDisplay_1_1(displays[0]);
    EXPECT_NE(nullptr, validDisplay);

    hidlevs::V1_0::EvsResult result = c->forceMaster(validDisplay);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);

    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraParam> parameters;
    c->getParameterList([&parameters](const auto& list) { parameters = list; });

    ::android::hardware::hidl_vec<int32_t> read;
    constexpr int value = 12;
    for (const auto& param : parameters) {
        c->setIntParameter(param, value, [&result, &read](const auto& r, const auto& v) {
            result = r, read = v;
        });
        EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);
        EXPECT_EQ(read.size(), 1);
        EXPECT_EQ(value, read[0]);

        c->getIntParameter(param, [&result, &read](const auto& r, const auto& v) {
            result = r, read = v;
        });
        EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);
        EXPECT_EQ(read.size(), 1);
        EXPECT_EQ(value, read[0]);

        int32_t min, max, step;
        c->getIntParameterRange(param,
                                [&min, &max, &step](const auto& _min, const auto& _max,
                                                    const auto& _step) {
                                    min = _min, max = _max, step = _step;
                                });
        EXPECT_NE(0, step);
    }

    for (const auto& param : ::android::hardware::hidl_enum_range<hidlevs::V1_1::CameraParam>()) {
        auto it = std::find(parameters.begin(), parameters.end(), param);
        if (it != parameters.end()) {
            continue;
        }

        c->setIntParameter(param, value, [&result, &read](const auto& r, const auto& v) {
            result = r, read = v;
        });
        EXPECT_NE(hidlevs::V1_0::EvsResult::OK, result);
        c->getIntParameter(param, [&result, &read](const auto& r, const auto& v) {
            result = r, read = v;
        });
        EXPECT_NE(hidlevs::V1_0::EvsResult::OK, result);
    }

    // Create AidlCamera object with
    // android::hardware::automotive::evs::V1_0::IEvsCamera object and repeat
    // tests.
    std::vector<CameraParam> aidlParamList;
    std::vector<int32_t> values;
    ParameterRange range;
    std::shared_ptr<AidlCamera> aidlCamera =
            ndk::SharedRefBase::make<AidlCamera>(c, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlCamera);

    // Below calls should fail because V1_0::IEvsCamera does not support
    // a parameter programming.
    EXPECT_FALSE(aidlCamera->getParameterList(&aidlParamList).isOk());
    EXPECT_FALSE(aidlCamera->getIntParameter(CameraParam::BRIGHTNESS, &values).isOk());
    EXPECT_FALSE(aidlCamera->getIntParameterRange(CameraParam::BRIGHTNESS, &range).isOk());
    EXPECT_FALSE(aidlCamera->setIntParameter(CameraParam::BRIGHTNESS, /* value= */ 0xFF, &values)
                         .isOk());

    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyDisplayBuffer) {
    std::vector<uint8_t> displays;
    EXPECT_TRUE(mEnumerator->getDisplayIdList([&displays](const auto& list) { displays = list; })
                        .isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    for (const auto& display : displays) {
        ::android::sp<hidlevs::V1_1::IEvsDisplay> d = mEnumerator->openDisplay_1_1(display);
        EXPECT_NE(nullptr, d);

        hidlevs::V1_0::BufferDesc b;
        d->getTargetBuffer([&b](const hidlevs::V1_0::BufferDesc& buffer) { b = buffer; });
        EXPECT_NE(nullptr, b.memHandle);

        hidlevs::V1_0::EvsResult r = d->returnTargetBufferForDisplay(b);
        EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, r);

        mEnumerator->closeDisplay(d);
    }
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyImportExternalBuffer) {
    constexpr size_t kNumExternalBuffers = 5;
    constexpr size_t kExternalBufferWidth = 64;
    constexpr size_t kExternalBufferHeight = 32;
    constexpr int32_t kBufferIdOffset = 0x100;
    constexpr auto usage =
            GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY | GRALLOC_USAGE_SW_WRITE_OFTEN;

    buffer_handle_t memHandle = nullptr;
    ::android::GraphicBufferAllocator& alloc(::android::GraphicBufferAllocator::get());
    ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc> buffers(
            kNumExternalBuffers);
    for (size_t i = 0; i < kNumExternalBuffers; ++i) {
        unsigned pixelsPerLine;
        ::android::status_t result =
                alloc.allocate(kExternalBufferWidth, kExternalBufferHeight,
                               /* format= */ HAL_PIXEL_FORMAT_RGBA_8888, /* layers= */ 1, usage,
                               &memHandle, &pixelsPerLine, 0, "EvsEnumeratorUnitTest");
        if (result != ::android::NO_ERROR) {
            ADD_FAILURE();
            return;
        }

        ::android::hardware::automotive::evs::V1_1::BufferDesc buf;
        AHardwareBuffer_Desc* pDesc =
                reinterpret_cast<AHardwareBuffer_Desc*>(&buf.buffer.description);
        pDesc->width = kExternalBufferWidth;
        pDesc->height = kExternalBufferHeight;
        pDesc->layers = 1;
        pDesc->format = HAL_PIXEL_FORMAT_RGBA_8888;
        pDesc->usage = usage;
        pDesc->stride = pixelsPerLine;
        buf.buffer.nativeHandle = memHandle;
        buf.bufferId = kBufferIdOffset + i;  // Unique number to identify this buffer
        buffers[i] = buf;
    }

    // Retrieve a list of available cameras.
    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> cameras;
    mEnumerator->getCameraList_1_1([&cameras](const auto& list) { cameras = list; });
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(cameras[0].v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);

    int delta = 0;
    hidlevs::V1_0::EvsResult result = hidlevs::V1_0::EvsResult::OK;
    c->importExternalBuffers(buffers, [&](auto _result, auto _delta) {
        result = _result;
        delta = _delta;
    });
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, result);
    EXPECT_EQ(delta, kNumExternalBuffers);

    // Create AidlCamera object and call importExternalBuffers().
    std::shared_ptr<AidlCamera> aidlCamera =
            ndk::SharedRefBase::make<AidlCamera>(c, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlCamera);
    EXPECT_FALSE(aidlCamera->importExternalBuffers({}, nullptr).isOk());

    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseDisplayWithAidlWrapper) {
    std::shared_ptr<AidlEnumerator> aidlWrapper =
            ndk::SharedRefBase::make<AidlEnumerator>(mEnumerator);
    EXPECT_NE(nullptr, aidlWrapper);

    std::vector<uint8_t> displays;
    EXPECT_TRUE(aidlWrapper->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    const uint8_t displayIdToUse = displays[0];
    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsDisplay> d;
    EXPECT_TRUE(aidlWrapper->openDisplay(displayIdToUse, &d).isOk());

    ::aidl::android::hardware::automotive::evs::DisplayDesc desc;
    EXPECT_TRUE(d->getDisplayInfo(&desc).isOk());

    ::aidl::android::hardware::automotive::evs::DisplayState state;
    EXPECT_TRUE(aidlWrapper->getDisplayState(&state).isOk());
    EXPECT_EQ(::aidl::android::hardware::automotive::evs::DisplayState::NOT_VISIBLE, state);

    aidlWrapper = ndk::SharedRefBase::make<AidlEnumerator>(mEnumerator, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlWrapper);

    // hidlevs::V1_0::IEvsEnumerator returns an erroneous status.
    displays.clear();
    EXPECT_FALSE(aidlWrapper->getDisplayIdList(&displays).isOk());
    EXPECT_TRUE(displays.empty());

    d = nullptr;
    EXPECT_TRUE(aidlWrapper->openDisplay(displayIdToUse, &d).isOk());
    EXPECT_TRUE(d->getDisplayInfo(&desc).isOk());

    EXPECT_TRUE(aidlWrapper->getDisplayState(&state).isOk());
    EXPECT_EQ(::aidl::android::hardware::automotive::evs::DisplayState::NOT_VISIBLE, state);
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyAidlEnumeratorWrapper) {
    std::shared_ptr<AidlEnumerator> aidlWrapper =
            ndk::SharedRefBase::make<AidlEnumerator>(mEnumerator);
    EXPECT_NE(nullptr, aidlWrapper);

    bool isHardware = false;
    EXPECT_TRUE(aidlWrapper->isHardware(&isHardware).isOk());
    // AidlEnumerator class will always be used to wrap around HIDL EVS HAL
    // implementation.
    EXPECT_TRUE(isHardware);

    // Below methods are not implemented yet.
    std::vector<::aidl::android::hardware::automotive::evs::UltrasonicsArrayDesc> descs;
    EXPECT_FALSE(aidlWrapper->getUltrasonicsArrayList(&descs).isOk());

    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsUltrasonicsArray> ptr;
    EXPECT_FALSE(aidlWrapper->openUltrasonicsArray(/* id= */ "invalid", &ptr).isOk());
    EXPECT_FALSE(aidlWrapper->closeUltrasonicsArray(ptr).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyOpenAndCloseCameraWithAidlWrapper) {
    std::shared_ptr<AidlEnumerator> aidlWrapper =
            ndk::SharedRefBase::make<AidlEnumerator>(mEnumerator);
    EXPECT_NE(nullptr, aidlWrapper);

    std::vector<CameraDesc> cameras;
    EXPECT_TRUE(aidlWrapper->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<Stream> configs;
    EXPECT_TRUE(aidlWrapper->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    std::shared_ptr<::aidl::android::hardware::automotive::evs::IEvsCamera> c;
    EXPECT_TRUE(aidlWrapper->openCamera(cameras[0].id, configs[0], &c).isOk());
    EXPECT_TRUE(aidlWrapper->closeCamera(c).isOk());

    aidlWrapper = ndk::SharedRefBase::make<AidlEnumerator>(mEnumerator, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlWrapper);

    EXPECT_TRUE(aidlWrapper->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    EXPECT_TRUE(aidlWrapper->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    EXPECT_TRUE(aidlWrapper->openCamera(cameras[0].id, configs[0], &c).isOk());
    EXPECT_TRUE(aidlWrapper->closeCamera(c).isOk());
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyEvsResultConversion) {
    for (const auto& v : ::android::hardware::hidl_enum_range<hidlevs::V1_0::EvsResult>()) {
        ::android::hardware::Return<hidlevs::V1_0::EvsResult> wrapped = v;
        if (v == hidlevs::V1_0::EvsResult::OK) {
            EXPECT_TRUE(Utils::buildScopedAStatusFromEvsResult(v).isOk());
            EXPECT_TRUE(Utils::buildScopedAStatusFromEvsResult(wrapped).isOk());
        } else {
            EXPECT_FALSE(Utils::buildScopedAStatusFromEvsResult(v).isOk());
            EXPECT_FALSE(Utils::buildScopedAStatusFromEvsResult(wrapped).isOk());
        }
    }
}

TEST_F(EvsEnumeratorHidlUnitTest, VerifyUltrasonicsArray) {
    ::android::hardware::hidl_vec<hidlevs::V1_1::UltrasonicsArrayDesc> list;
    EXPECT_TRUE(
            mEnumerator->getUltrasonicsArrayList([&list](const auto& received) { list = received; })
                    .isOk());
    EXPECT_EQ(list.size(), 0);

    ::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray> v =
            mEnumerator->openUltrasonicsArray(/* id= */ "invalidId");
    EXPECT_EQ(v, nullptr);
    EXPECT_TRUE(mEnumerator->closeUltrasonicsArray(v).isOk());
}

bool EvsEnumeratorHidlUnitTest::VerifyCameraStream(const hidlevs::V1_1::CameraDesc& desc,
                                                   size_t framesToReceive,
                                                   std::chrono::duration<long double> maxInterval,
                                                   std::chrono::duration<long double> eventTimeout,
                                                   const std::string& name,
                                                   StreamStartedCallbackFunc callback) {
    std::mutex m;
    std::condition_variable cv;
    ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc> receivedFrames;
    hidlevs::V1_1::EvsEventDesc receivedEvent;
    size_t counter = 0;
    bool gotEventCallback = false, gotFrameCallback = false, gotFirstFrame = false;
    auto frameCb = FrameCallbackFunc(
            [&](const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>& forwarded) {
                std::lock_guard lk(m);
                receivedFrames = forwarded;

                LOG(DEBUG) << name << " received frames from " << forwarded[0].deviceId << ", "
                           << ++counter;
                if (!gotFirstFrame) {
                    callback();
                    gotFirstFrame = true;
                }
                gotFrameCallback = true;
                cv.notify_all();
                return ::android::hardware::Void();
            });
    auto eventCb = EventCallbackFunc([&](const hidlevs::V1_1::EvsEventDesc& event) {
        std::lock_guard lk(m);
        receivedEvent = event;

        LOG(INFO) << name << " received an event from " << event.deviceId;
        gotEventCallback = true;
        cv.notify_all();
        return ::android::hardware::Void();
    });

    ::android::sp<hidlevs::V1_1::IEvsCamera> c =
            mEnumerator->openCamera_1_1(desc.v1.cameraId, /* streamCfg= */ {});
    EXPECT_NE(nullptr, c);
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, c->setMaxFramesInFlight(/* bufferCount= */ 3));

    // Request to start a video stream and wait for a given number of frames.
    ::android::sp<StreamCallback> cb = new (std::nothrow) StreamCallback(frameCb, eventCb);
    EXPECT_NE(nullptr, cb);
    EXPECT_TRUE(c->startVideoStream(cb).isOk());

    std::unique_lock lk(m);
    for (auto i = 0; i < framesToReceive; ++i) {
        EXPECT_TRUE(cv.wait_for(lk, maxInterval, [&gotFrameCallback] { return gotFrameCallback; }));
        EXPECT_TRUE(gotFrameCallback);
        if (!gotFrameCallback) {
            continue;
        }

        EXPECT_TRUE(c->doneWithFrame_1_1(receivedFrames).isOk());
        gotFrameCallback = false;
    }
    lk.unlock();

    // Call two methods that are not implemented yet in a mock EVS HAL
    // implementation.
    EXPECT_TRUE(c->pauseVideoStream().isOk());
    EXPECT_TRUE(c->resumeVideoStream().isOk());

    // Create AidlCamera object and call pauseVideoStream() and
    // resumeVideoStream().
    std::shared_ptr<AidlCamera> aidlCamera = ndk::SharedRefBase::make<AidlCamera>(c);
    EXPECT_NE(nullptr, aidlCamera);

    // Mock HIDL EVS HAL implementation does not support pause/resume; hence
    // below calls should fail.
    EXPECT_FALSE(aidlCamera->pauseVideoStream().isOk());
    EXPECT_FALSE(aidlCamera->resumeVideoStream().isOk());

    // Request to stop a video stream and wait.
    EXPECT_TRUE(c->stopVideoStream().isOk());

    lk.lock();
    cv.wait_for(lk, eventTimeout, [&gotEventCallback] { return gotEventCallback; });
    EXPECT_EQ(hidlevs::V1_1::EvsEventType::STREAM_STOPPED, receivedEvent.aType);

    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());

    return true;
}

bool EvsEnumeratorHidlUnitTest::VerifyCameraStream_1_0(
        const hidlevs::V1_0::CameraDesc& desc, size_t framesToReceive,
        std::chrono::duration<long double> maxInterval,
        std::chrono::duration<long double> stopTimeout, const std::string& name,
        StreamStartedCallbackFunc callback) {
    std::mutex m;
    std::condition_variable cv;
    hidlevs::V1_0::BufferDesc receivedFrame;
    size_t counter = 0;
    bool gotFrameCallback = false, gotFirstFrame = false, gotNullFrame = false;
    auto frameCb = FrameCallbackFunc_1_0([&](const hidlevs::V1_0::BufferDesc& forwarded) {
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
    EXPECT_EQ(hidlevs::V1_0::EvsResult::OK, c->setMaxFramesInFlight(/* bufferCount= */ 3));

    // Request to start a video stream and wait for a given number of frames.
    ::android::sp<StreamCallback_1_0> cb = new (std::nothrow) StreamCallback_1_0(frameCb);
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

    // Create AidlCamera object and call pauseVideoStream() and
    // resumeVideoStream().
    std::shared_ptr<AidlCamera> aidlCamera =
            ndk::SharedRefBase::make<AidlCamera>(c, /* forceV1_0= */ true);
    EXPECT_NE(nullptr, aidlCamera);

    // ::android::hardware::automotive::evs::V1_0::IEvsCamera does not support
    // pause/resume; hence, below calls should fail.
    EXPECT_FALSE(aidlCamera->pauseVideoStream().isOk());
    EXPECT_FALSE(aidlCamera->resumeVideoStream().isOk());

    // Request to stop a video stream and wait.
    EXPECT_TRUE(c->stopVideoStream().isOk());

    lk.lock();
    EXPECT_TRUE(cv.wait_for(lk, stopTimeout, [&gotNullFrame] { return gotNullFrame; }));
    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());

    return true;
}

}  // namespace aidl::android::automotive::evs::implementation
