/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "VtsHalCarDisplayTest"

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/frameworks/automotive/display/DisplayDesc.h>
#include <aidl/android/frameworks/automotive/display/ICarDisplayProxy.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_status.h>
#include <bufferqueueconverter/BufferQueueConverter.h>

namespace {

using ::aidl::android::frameworks::automotive::display::DisplayDesc;
using ::aidl::android::frameworks::automotive::display::ICarDisplayProxy;
using ::aidl::android::hardware::common::NativeHandle;

android::sp<HGraphicBufferProducer> convertNativeHandleToHGBP(const NativeHandle& aidlHandle) {
    native_handle_t* handle = ::android::dupFromAidl(aidlHandle);
    if (handle == nullptr || handle->numFds != 0 ||
        handle->numInts < std::ceil(sizeof(size_t) / sizeof(int))) {
        LOG(ERROR) << "Invalid native handle";
        return nullptr;
    }
    android::hardware::hidl_vec<uint8_t> halToken;
    halToken.setToExternal(reinterpret_cast<uint8_t*>(const_cast<int*>(&(handle->data[1]))),
                           handle->data[0]);
    android::sp<HGraphicBufferProducer> hgbp =
        HGraphicBufferProducer::castFrom(::android::retrieveHalInterface(halToken));
    return std::move(hgbp);
}

}  // namespace

// The main test class for Automotive Display Service
class CarDisplayAidlTest : public ::testing::TestWithParam<std::string> {
   public:
    void SetUp() override {
        // Make sure we can connect to the service
        std::string serviceName = GetParam();
        AIBinder* binder = AServiceManager_waitForService(serviceName.data());
        ASSERT_NE(binder, nullptr);
        mDisplayProxy = ICarDisplayProxy::fromBinder(ndk::SpAIBinder(binder));
        ASSERT_NE(mDisplayProxy, nullptr);
        LOG(INFO) << "Test target service: " << serviceName;

        loadDisplayList();
    }

    void TearDown() override {
        mDisplayProxy.reset();
        mDisplayIds.clear();
    }

   protected:
    void loadDisplayList() {
        ASSERT_TRUE(mDisplayProxy->getDisplayIdList(&mDisplayIds).isOk());
        LOG(INFO) << "We have " << mDisplayIds.size() << " displays.";
    }

    std::shared_ptr<ICarDisplayProxy> mDisplayProxy;
    std::vector<int64_t> mDisplayIds;
};

TEST_P(CarDisplayAidlTest, getIGBPObject) {
    LOG(INFO) << "Test getHGraphicBufferProducer method";

    for (const auto& id : mDisplayIds) {
        // Get a display info.
        DisplayDesc desc;
        ASSERT_TRUE(mDisplayProxy->getDisplayInfo(id, &desc).isOk());

        // Get a HGBP object as a native handle object.
        NativeHandle handle;
        ASSERT_TRUE(mDisplayProxy->getHGraphicBufferProducer(id, &handle).isOk());

        // Convert a native handle object into a HGBP object.
        android::sp<android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>
            gfxBufferProducer = convertNativeHandleToHGBP(handle);
        ASSERT_NE(gfxBufferProducer, nullptr);

        // Create a Surface object.
        android::SurfaceHolderUniquePtr surfaceHolder = getSurfaceFromHGBP(gfxBufferProducer);
        ASSERT_NE(surfaceHolder, nullptr);

        // Verify the size.
        ANativeWindow* nativeWindow = getNativeWindow(surfaceHolder.get());
        ASSERT_EQ(desc.width, ANativeWindow_getWidth(nativeWindow));
        ASSERT_EQ(desc.height, ANativeWindow_getHeight(nativeWindow));
    }
}

TEST_P(CarDisplayAidlTest, showWindow) {
    LOG(INFO) << "Test showWindow method";
    for (const auto& id : mDisplayIds) {
        ASSERT_TRUE(mDisplayProxy->showWindow(id).isOk());
    }
}

TEST_P(CarDisplayAidlTest, hideWindow) {
    LOG(INFO) << "Test hideWindow method";

    for (const auto& id : mDisplayIds) {
        ASSERT_TRUE(mDisplayProxy->hideWindow(id).isOk());
    }
}

TEST_P(CarDisplayAidlTest, getSurface) {
    LOG(INFO) << "Test getSurface method";

    for (const auto& id : mDisplayIds) {
        // Get a display info.
        DisplayDesc desc;
        ASSERT_TRUE(mDisplayProxy->getDisplayInfo(id, &desc).isOk());

        // Get a Surface object.
        aidl::android::view::Surface shimSurface;
        ASSERT_TRUE(mDisplayProxy->getSurface(id, &shimSurface).isOk());

        // Verify the size.
        ANativeWindow* nativeWindow = shimSurface.get();
        ASSERT_EQ(desc.width, ANativeWindow_getWidth(nativeWindow));
        ASSERT_EQ(desc.height, ANativeWindow_getHeight(nativeWindow));
    }
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(CarDisplayAidlTest);
INSTANTIATE_TEST_SUITE_P(
    PerInstance, CarDisplayAidlTest,
    testing::ValuesIn(android::getAidlHalInstanceNames(ICarDisplayProxy::descriptor)),
    android::PrintInstanceNameToString);

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ABinderProcess_setThreadPoolMaxThreadCount(/* numThreads= */ 1);
    ABinderProcess_startThreadPool();
    return RUN_ALL_TESTS();
}
