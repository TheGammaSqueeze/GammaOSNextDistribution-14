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
#include "MockEvsHal.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "hardware/gralloc.h"
#include "utils/include/Utils.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <cutils/android_filesystem_config.h>
#include <ui/GraphicBufferAllocator.h>

#include <unistd.h>

#include <future>

namespace {

using ::aidl::android::hardware::automotive::evs::BnEvsCameraStream;
using ::aidl::android::hardware::automotive::evs::BnEvsEnumeratorStatusCallback;
using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DeviceStatus;
using ::aidl::android::hardware::automotive::evs::DeviceStatusType;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::IEvsEnumeratorStatusCallback;
using ::aidl::android::hardware::automotive::evs::IEvsUltrasonicsArray;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::aidl::android::hardware::automotive::evs::Stream;

using FrameCallbackFunc = std::function<::ndk::ScopedAStatus(const std::vector<BufferDesc>&)>;
using EventCallbackFunc = std::function<::ndk::ScopedAStatus(const EvsEventDesc&)>;
using StatusCallbackFunc = std::function<::ndk::ScopedAStatus(std::vector<DeviceStatus>)>;
using StreamStartedCallbackFunc = std::function<void()>;

constexpr size_t kNumMockEvsCameras = 4;
constexpr size_t kNumMockEvsDisplays = 2;

const std::unordered_set<int32_t> gAllowedUid({AID_ROOT, AID_SYSTEM, AID_AUTOMOTIVE_EVS});

constexpr auto doNothingFunc = []() { /* do nothing */ };

}  // namespace

namespace aidl::android::automotive::evs::implementation {

class EvsEnumeratorUnitTest : public ::testing::Test {
public:
    EvsEnumeratorUnitTest() {
        // Instantiates IEvsEnumerator
        mEnumerator = ndk::SharedRefBase::make<Enumerator>();
        EXPECT_NE(nullptr, mEnumerator);

        // Disable a permission check
        mEnumerator->enablePermissionCheck(/* enable= */ false);
    }

    ~EvsEnumeratorUnitTest() override {
        // Clean-up work should be here.  No exception should be thrown.
    }

    void SetUp() override {
        // Additional place to set up the test environment.  This will be called
        // right after the constructor.
        mMockEvsHal = std::make_shared<MockEvsHal>(kNumMockEvsCameras, kNumMockEvsDisplays);
        EXPECT_NE(nullptr, mMockEvsHal);
        mMockEvsHal->initialize();

        std::shared_ptr<IEvsEnumerator> hwEnumerator = mMockEvsHal->getEnumerator();
        EXPECT_NE(nullptr, hwEnumerator);
        mEnumerator->init(hwEnumerator, /* enableMonitor= */ true);
    }

    void TearDown() override {
        // This will be called immediately after each test; right before the
        // destructor.
    }

    bool VerifyCameraStream(const CameraDesc& desc, size_t framesToReceive,
                            std::chrono::duration<long double> maxInterval,
                            std::chrono::duration<long double> eventTimeout,
                            const std::string& name, StreamStartedCallbackFunc cb);

protected:
    // Class members declared here can be used by all tests in the test suite
    // for EvsEnumerator
    std::shared_ptr<Enumerator> mEnumerator;
    std::shared_ptr<MockEvsHal> mMockEvsHal;

    class StreamCallback : public BnEvsCameraStream {
    public:
        StreamCallback(FrameCallbackFunc frameCallbackFunc, EventCallbackFunc eventCallbackFunc) :
              mFrameCallback(frameCallbackFunc), mEventCallback(eventCallbackFunc) {}
        ::ndk::ScopedAStatus deliverFrame(const std::vector<BufferDesc>& frames) override {
            return mFrameCallback(frames);
        }

        ::ndk::ScopedAStatus notify(const EvsEventDesc& event) override {
            return mEventCallback(event);
        }

    private:
        FrameCallbackFunc mFrameCallback;
        EventCallbackFunc mEventCallback;
    };

    class DeviceStatusCallback : public BnEvsEnumeratorStatusCallback {
    public:
        DeviceStatusCallback(StatusCallbackFunc callback) : mCallback(callback) {}
        ::ndk::ScopedAStatus deviceStatusChanged(const std::vector<DeviceStatus>& status) override {
            return mCallback(status);
        }

    private:
        StatusCallbackFunc mCallback;
    };
};

TEST_F(EvsEnumeratorUnitTest, VerifyPermissionCheck) {
    bool isAllowedUid = gAllowedUid.find(getuid()) != gAllowedUid.end();
    mEnumerator->enablePermissionCheck(true);

    std::vector<CameraDesc> cameras;
    Stream emptyConfig;
    if (!isAllowedUid) {
        EXPECT_FALSE(mEnumerator->getCameraList(&cameras).isOk());

        std::shared_ptr<IEvsCamera> invalidCamera;
        EXPECT_FALSE(
                mEnumerator->openCamera(/* cameraId= */ "invalidId", emptyConfig, &invalidCamera)
                        .isOk());
        EXPECT_EQ(nullptr, invalidCamera);
        EXPECT_FALSE(mEnumerator->closeCamera(invalidCamera).isOk());

        std::shared_ptr<IEvsDisplay> invalidDisplay;
        EXPECT_FALSE(mEnumerator->openDisplay(/* displayId= */ 0xFF, &invalidDisplay).isOk());

        DisplayState emptyState;
        EXPECT_FALSE(mEnumerator->getDisplayState(&emptyState).isOk());
    } else {
        // TODO(b/240619903): Adds more lines to verify the behavior when
        //                    current user is allowed to use the EVS service.
        EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    }

    mEnumerator->enablePermissionCheck(false);
}

TEST_F(EvsEnumeratorUnitTest, VerifyIsHardwareMethod) {
    bool isHardware = true;

    EXPECT_TRUE(mEnumerator->isHardware(&isHardware).isOk());

    EXPECT_FALSE(isHardware);
}

TEST_F(EvsEnumeratorUnitTest, VerifyOpenAndCloseDisplay) {
    std::vector<uint8_t> displays;

    EXPECT_TRUE(mEnumerator->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    for (auto& id : displays) {
        std::shared_ptr<IEvsDisplay> h0, h1;
        EXPECT_TRUE(mEnumerator->openDisplay(id, &h0).isOk());
        EXPECT_NE(nullptr, h0);

        EXPECT_TRUE(mEnumerator->openDisplay(id, &h1).isOk());
        EXPECT_NE(nullptr, h1);

        DisplayDesc desc;
        EXPECT_TRUE(h1->getDisplayInfo(&desc).isOk());

        DisplayState state;
        EXPECT_TRUE(mEnumerator->getDisplayState(&state).isOk());
        EXPECT_EQ(DisplayState::NOT_VISIBLE, state);

        EXPECT_TRUE(mEnumerator->closeDisplay(h1).isOk());

        // closeDisplay() with an invalidated display handle should be okay.
        EXPECT_TRUE(mEnumerator->closeDisplay(h0).isOk());
    }
}

TEST_F(EvsEnumeratorUnitTest, VerifyOpenAndCloseCamera) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());
    for (auto& desc : cameras) {
        std::vector<Stream> configs;

        EXPECT_TRUE(mEnumerator->getStreamList(desc, &configs).isOk());
        EXPECT_FALSE(configs.empty());

        std::shared_ptr<IEvsCamera> h0, h1;
        EXPECT_TRUE(mEnumerator->openCamera(desc.id, configs[0], &h0).isOk());
        EXPECT_NE(nullptr, h0);
        EXPECT_TRUE(mEnumerator->openCamera(desc.id, configs[0], &h1).isOk());
        EXPECT_NE(nullptr, h1);

        EXPECT_TRUE(mEnumerator->closeCamera(h1).isOk());
        EXPECT_TRUE(mEnumerator->closeCamera(h0).isOk());
    }
}

TEST_F(EvsEnumeratorUnitTest, CloseInvalidEvsCamera) {
    std::shared_ptr<IEvsCamera> invalidCamera;
    EXPECT_FALSE(mEnumerator->closeCamera(invalidCamera).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyExclusiveDisplayOwner) {
    std::shared_ptr<IEvsDisplay> display;
    EXPECT_TRUE(mEnumerator->openDisplay(kExclusiveDisplayId, &display).isOk());
    EXPECT_NE(nullptr, display);

    std::shared_ptr<IEvsDisplay> failed;
    EXPECT_FALSE(mEnumerator->openDisplay(/* displayId= */ 0, &failed).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyRegisterStatusCallback) {
    mEnumerator->enablePermissionCheck(/* enable= */ false);
    std::mutex lock;
    std::condition_variable cv;
    std::vector<DeviceStatus> received;
    bool gotCallback = false;

    auto func = std::function<::ndk::ScopedAStatus(const std::vector<DeviceStatus>&)>(
            [&cv, &received, &gotCallback](const std::vector<DeviceStatus>& deviceStatus) {
                received = deviceStatus;
                gotCallback = true;
                cv.notify_all();
                return ::ndk::ScopedAStatus::ok();
            });
    std::shared_ptr<DeviceStatusCallback> callback =
            ::ndk::SharedRefBase::make<DeviceStatusCallback>(func);
    EXPECT_TRUE(mEnumerator->registerStatusCallback(callback).isOk());

    const std::string deviceId("/dev/hotplug_camera");
    mMockEvsHal->addMockCameraDevice(deviceId);

    std::unique_lock l(lock);
    cv.wait_for(l, std::chrono::seconds(1), [&gotCallback] { return gotCallback; });
    EXPECT_TRUE(gotCallback);
    EXPECT_FALSE(received.empty());

    auto it = std::find_if(received.begin(), received.end(), [deviceId](DeviceStatus v) {
        return v.id == deviceId && v.status == DeviceStatusType::CAMERA_AVAILABLE;
    });
    EXPECT_TRUE(it != received.end());

    gotCallback = false;
    received.clear();
    mMockEvsHal->removeMockCameraDevice(deviceId);
    cv.wait_for(l, std::chrono::seconds(1), [&gotCallback] { return gotCallback; });
    EXPECT_TRUE(gotCallback);
    EXPECT_FALSE(received.empty());

    it = std::find_if(received.begin(), received.end(), [deviceId](DeviceStatus v) {
        return v.id == deviceId && v.status == DeviceStatusType::CAMERA_NOT_AVAILABLE;
    });
    EXPECT_TRUE(it != received.end());
}

TEST_F(EvsEnumeratorUnitTest, VerifyStartAndStopVideoStream) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        std::vector<Stream> configs;
        EXPECT_TRUE(mEnumerator->getStreamList(desc, &configs).isOk());
        EXPECT_FALSE(configs.empty());

        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                                  desc, kFramesToReceive, kMaxFrameInterval,
                                                  kEventTimeout, desc.id, doNothingFunc));
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

TEST_F(EvsEnumeratorUnitTest, VerifyMultipleClientsStreaming) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto& desc : cameras) {
        std::vector<Stream> configs;
        EXPECT_TRUE(mEnumerator->getStreamList(desc, &configs).isOk());
        EXPECT_FALSE(configs.empty());

        // Start sending a frame early.
        mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ 100);

        std::packaged_task<bool()> task0(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                                   desc, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, "client0", doNothingFunc));
        std::packaged_task<bool()> task1(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                                   desc, kFramesToReceive, kMaxFrameInterval,
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

TEST_F(EvsEnumeratorUnitTest, VerifyMultipleCamerasStreaming) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    constexpr auto kFramesToReceive = 5;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 5s;
    for (auto i = 0; i < cameras.size() - 1; ++i) {
        const auto& desc0 = cameras[i];
        const auto& desc1 = cameras[i + 1];

        std::packaged_task<bool()> task0(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                                   desc0, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, desc0.id, doNothingFunc));
        std::packaged_task<bool()> task1(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                                   desc1, kFramesToReceive, kMaxFrameInterval,
                                                   kEventTimeout, desc1.id, doNothingFunc));

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

TEST_F(EvsEnumeratorUnitTest, VerifyPrimaryCameraClient) {
    std::vector<CameraDesc> cameras;
    std::vector<uint8_t> displays;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());
    EXPECT_TRUE(mEnumerator->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    std::shared_ptr<IEvsDisplay> validDisplay, invalidDisplay;
    EXPECT_TRUE(mEnumerator->openDisplay(displays[0], &validDisplay).isOk());

    std::vector<Stream> configs;
    EXPECT_TRUE(mEnumerator->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    std::shared_ptr<IEvsCamera> c0, c1;
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, configs[0], &c0).isOk());
    EXPECT_NE(nullptr, c0);
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, configs[0], &c1).isOk());
    EXPECT_NE(nullptr, c1);

    EXPECT_TRUE(c0->forcePrimaryClient(validDisplay).isOk());
    EXPECT_FALSE(c1->forcePrimaryClient(invalidDisplay).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyGetCameraInfo) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<Stream> configs;
    EXPECT_TRUE(mEnumerator->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    std::shared_ptr<IEvsCamera> c0;
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, configs[0], &c0).isOk());
    EXPECT_NE(nullptr, c0);

    CameraDesc desc;
    EXPECT_TRUE(c0->getCameraInfo(&desc).isOk());
    EXPECT_EQ(desc, cameras[0]);
}

TEST_F(EvsEnumeratorUnitTest, VerifyExtendedInfo) {
    std::vector<CameraDesc> cameras;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());

    std::vector<Stream> configs;
    EXPECT_TRUE(mEnumerator->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    std::shared_ptr<IEvsCamera> c0;
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, configs[0], &c0).isOk());
    EXPECT_NE(nullptr, c0);

    constexpr int id = 0x12;
    std::vector<uint8_t> value({1, 2, 3, 4});
    EXPECT_TRUE(c0->setExtendedInfo(id, value).isOk());

    std::vector<uint8_t> read;
    EXPECT_TRUE(c0->getExtendedInfo(id, &read).isOk());
    EXPECT_TRUE(std::equal(value.begin(), value.end(), read.begin()));

    constexpr int invalidId = 0x10;
    EXPECT_FALSE(c0->getExtendedInfo(invalidId, &read).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyIntParameters) {
    std::vector<CameraDesc> cameras;
    std::vector<uint8_t> displays;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_TRUE(mEnumerator->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    std::vector<Stream> configs;
    EXPECT_TRUE(mEnumerator->getStreamList(cameras[0], &configs).isOk());
    EXPECT_FALSE(configs.empty());

    std::shared_ptr<IEvsCamera> c;
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, configs[0], &c).isOk());
    EXPECT_NE(nullptr, c);

    std::shared_ptr<IEvsDisplay> validDisplay;
    EXPECT_TRUE(mEnumerator->openDisplay(displays[0], &validDisplay).isOk());

    EXPECT_TRUE(c->forcePrimaryClient(validDisplay).isOk());

    std::vector<CameraParam> parameters;
    EXPECT_TRUE(c->getParameterList(&parameters).isOk());
    std::vector<int32_t> read;
    constexpr int value = 12;
    for (const auto& param : parameters) {
        read.clear();
        EXPECT_TRUE(c->setIntParameter(param, value, &read).isOk());
        EXPECT_GT(read.size(), 0);

        read.clear();
        EXPECT_TRUE(c->getIntParameter(param, &read).isOk());
        EXPECT_GT(read.size(), 0);
        EXPECT_EQ(read[0], value);

        ParameterRange range;
        EXPECT_TRUE(c->getIntParameterRange(param, &range).isOk());
    }

    for (auto param : ::ndk::internal::enum_values<CameraParam>) {
        auto it = std::find(parameters.begin(), parameters.end(), param);
        if (it != parameters.end()) {
            continue;
        }

        EXPECT_FALSE(c->setIntParameter(param, value, &read).isOk());
        EXPECT_FALSE(c->getIntParameter(param, &read).isOk());
    }
}
TEST_F(EvsEnumeratorUnitTest, VerifyDisplayBuffer) {
    std::vector<uint8_t> displays;

    EXPECT_TRUE(mEnumerator->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    for (auto& id : displays) {
        std::shared_ptr<IEvsDisplay> display;
        EXPECT_TRUE(mEnumerator->openDisplay(id, &display).isOk());
        EXPECT_NE(nullptr, display);

        BufferDesc displayBuffer;
        EXPECT_TRUE(display->getTargetBuffer(&displayBuffer).isOk());
        EXPECT_TRUE(display->returnTargetBufferForDisplay(displayBuffer).isOk());
        EXPECT_TRUE(mEnumerator->closeDisplay(display).isOk());
    }
}

TEST_F(EvsEnumeratorUnitTest, VerifyImportExternalBuffer) {
    constexpr size_t kNumExternalBuffers = 5;
    constexpr size_t kExternalBufferWidth = 64;
    constexpr size_t kExternalBufferHeight = 32;
    constexpr int32_t kBufferIdOffset = 0x100;
    constexpr auto usage =
            GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_RARELY | GRALLOC_USAGE_SW_WRITE_OFTEN;

    buffer_handle_t memHandle = nullptr;
    ::android::GraphicBufferAllocator& alloc(::android::GraphicBufferAllocator::get());
    std::vector<BufferDesc> buffers;
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

        BufferDesc buf;
        AHardwareBuffer_Desc* pDesc =
                reinterpret_cast<AHardwareBuffer_Desc*>(&buf.buffer.description);
        pDesc->width = kExternalBufferWidth;
        pDesc->height = kExternalBufferHeight;
        pDesc->layers = 1;
        pDesc->format = HAL_PIXEL_FORMAT_RGBA_8888;
        pDesc->usage = usage;
        pDesc->stride = pixelsPerLine;
        buf.buffer.handle = ::android::dupToAidl(memHandle);
        buf.bufferId = kBufferIdOffset + i;  // Unique number to identify this buffer
        buffers.push_back(std::move(buf));
    }

    // Retrieve a list of available cameras.
    std::vector<CameraDesc> cameras;
    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());

    // Open a camera with the first configuration.
    std::shared_ptr<IEvsCamera> c;
    EXPECT_TRUE(mEnumerator->openCamera(cameras[0].id, {}, &c).isOk());
    EXPECT_NE(nullptr, c);

    int delta = 0;
    EXPECT_TRUE(c->importExternalBuffers(buffers, &delta).isOk());
    EXPECT_EQ(delta, kNumExternalBuffers);

    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyEvsResultConversion) {
    for (const auto& v : ndk::enum_range<EvsResult>()) {
        if (v == EvsResult::OK) {
            EXPECT_TRUE(Utils::buildScopedAStatusFromEvsResult(v).isOk());
        } else {
            EXPECT_FALSE(Utils::buildScopedAStatusFromEvsResult(v).isOk());
        }
    }
}

TEST_F(EvsEnumeratorUnitTest, VerifyUltrasonicsArray) {
    EXPECT_FALSE(mEnumerator->getUltrasonicsArrayList(nullptr).isOk());
    EXPECT_FALSE(mEnumerator->openUltrasonicsArray(/* id= */ "invalidId", nullptr).isOk());

    std::shared_ptr<IEvsUltrasonicsArray> empty;
    EXPECT_FALSE(mEnumerator->closeUltrasonicsArray(empty).isOk());
}

TEST_F(EvsEnumeratorUnitTest, VerifyDumpInvalidCommand) {
    const char* args[1] = {"--invalid"};
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)args, 1));
}

TEST_F(EvsEnumeratorUnitTest, VerifyDumpHelpCommand) {
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), nullptr, 0));

    const char* args[1] = {"--help"};
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)args, 1));
}

TEST_F(EvsEnumeratorUnitTest, VerifyDumpListCommand) {
    std::vector<const char*> args({"--list", "camera"});
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    args.pop_back();
    args.push_back("display");
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    args.pop_back();
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));
}

TEST_F(EvsEnumeratorUnitTest, VerifyDumpDeviceCommand) {
    std::vector<CameraDesc> cameras;
    std::vector<uint8_t> displays;

    EXPECT_TRUE(mEnumerator->getCameraList(&cameras).isOk());
    EXPECT_TRUE(mEnumerator->getDisplayIdList(&displays).isOk());
    EXPECT_EQ(kNumMockEvsCameras, cameras.size());
    EXPECT_EQ(kNumMockEvsDisplays, displays.size());

    std::shared_ptr<IEvsDisplay> d;
    EXPECT_TRUE(mEnumerator->openDisplay(displays[0], &d).isOk());

    std::vector<const char*> args({"--dump", "display"});
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    std::mutex m;
    std::condition_variable cv;
    bool gotFirstFrame = false;
    constexpr auto kFramesToReceive = 100;
    constexpr auto kMaxFrameInterval = 100ms;
    constexpr auto kEventTimeout = 1s;
    constexpr auto kResultTimeout = 10s;
    auto streamCb = StreamStartedCallbackFunc([&]() {
        std::lock_guard lk(m);
        gotFirstFrame = true;
        LOG(INFO) << "Received the first frame";
        cv.notify_all();
    });

    // Start sending a frame early.
    mMockEvsHal->setNumberOfFramesToSend(/* numFramesToSend = */ kFramesToReceive * 10);

    std::packaged_task<bool()> task(std::bind(&EvsEnumeratorUnitTest::VerifyCameraStream, this,
                                              cameras[0], kFramesToReceive, kMaxFrameInterval,
                                              kEventTimeout, cameras[0].id, streamCb));
    std::future<bool> result = task.get_future();
    std::thread t(std::move(task));
    t.detach();

    std::unique_lock lk(m);
    EXPECT_TRUE(cv.wait_for(lk, /* rel_time= */ 1s, [&gotFirstFrame] { return gotFirstFrame; }));

    args.pop_back();
    args.push_back("camera");
    args.push_back("all");
    args.push_back("--current");
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    args.pop_back();
    args.pop_back();
    args.push_back(cameras[0].id.c_str());
    args.push_back("--custom");
    args.push_back("start");
    args.push_back("1000");
    args.push_back("50000");
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    std::this_thread::sleep_for(3s);

    args.pop_back();
    args.pop_back();
    args.pop_back();
    args.push_back("stop");
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    EXPECT_EQ(std::future_status::ready, result.wait_for(kResultTimeout));
    EXPECT_TRUE(result.get());

    args.pop_back();
    args.pop_back();
    args.push_back("--collected");
    EXPECT_EQ(STATUS_OK, mEnumerator->dump(fileno(stdout), (const char**)&args[0], args.size()));

    // Close a display
    EXPECT_TRUE(mEnumerator->closeDisplay(d).isOk());
}

bool EvsEnumeratorUnitTest::VerifyCameraStream(const CameraDesc& desc, size_t framesToReceive,
                                               std::chrono::duration<long double> maxInterval,
                                               std::chrono::duration<long double> eventTimeout,
                                               const std::string& name,
                                               StreamStartedCallbackFunc callback) {
    std::mutex m;
    std::condition_variable cv;
    std::vector<BufferDesc> receivedFrames;
    EvsEventDesc receivedEvent;
    size_t counter = 0;
    bool gotEventCallback = false, gotFrameCallback = false, gotFirstFrame = false;
    auto frameCb = FrameCallbackFunc([&](const std::vector<BufferDesc>& forwarded) {
        std::lock_guard lk(m);
        for (const auto& frame : forwarded) {
            BufferDesc dup = Utils::dupBufferDesc(frame, /* doDup= */ true);
            EXPECT_TRUE(Utils::validateNativeHandle(dup.buffer.handle));
            receivedFrames.push_back(std::move(dup));
        }

        LOG(DEBUG) << name << " received frames from " << forwarded[0].deviceId << ", "
                   << ++counter;
        if (!gotFirstFrame) {
            callback();
            gotFirstFrame = true;
        }
        gotFrameCallback = true;
        cv.notify_all();
        return ::ndk::ScopedAStatus::ok();
    });
    auto eventCb = EventCallbackFunc([&](const EvsEventDesc& event) {
        std::lock_guard lk(m);
        receivedEvent = event;

        LOG(INFO) << name << " received an event from " << event.deviceId;
        gotEventCallback = true;
        cv.notify_all();
        return ::ndk::ScopedAStatus::ok();
    });

    // Retrieve available stream configurations.
    std::vector<Stream> config;
    EXPECT_TRUE(mEnumerator->getStreamList(desc, &config).isOk());
    EXPECT_FALSE(config.empty());

    // Open a camera with the first configuration.
    std::shared_ptr<IEvsCamera> c;
    EXPECT_TRUE(mEnumerator->openCamera(desc.id, config[0], &c).isOk());
    EXPECT_NE(nullptr, c);

    // Request to start a video stream and wait for a given number of frames.
    std::shared_ptr<StreamCallback> cb =
            ::ndk::SharedRefBase::make<StreamCallback>(frameCb, eventCb);
    EXPECT_TRUE(c->startVideoStream(cb).isOk());

    std::unique_lock lk(m);
    for (auto i = 0; i < framesToReceive; ++i) {
        EXPECT_TRUE(cv.wait_for(lk, maxInterval, [&gotFrameCallback] { return gotFrameCallback; }));
        EXPECT_TRUE(gotFrameCallback);
        if (!gotFrameCallback) {
            continue;
        }

        EXPECT_TRUE(c->doneWithFrame(receivedFrames).isOk());
        receivedFrames.clear();
        gotFrameCallback = false;
    }
    lk.unlock();

    // Call two methods that are not implemented yet in a mock EVS HAL
    // implementation.
    EXPECT_TRUE(c->pauseVideoStream().isOk());
    EXPECT_TRUE(c->resumeVideoStream().isOk());

    // Request to stop a video stream and wait.
    EXPECT_TRUE(c->stopVideoStream().isOk());

    lk.lock();
    cv.wait_for(lk, eventTimeout, [&gotEventCallback] { return gotEventCallback; });
    EXPECT_EQ(EvsEventType::STREAM_STOPPED, receivedEvent.aType);

    EXPECT_TRUE(mEnumerator->closeCamera(c).isOk());

    return true;
}

}  // namespace aidl::android::automotive::evs::implementation
