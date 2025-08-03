/*
 * Copyright 2023 The Android Open Source Project
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

#include <Common.h>
#include <Enumerator.h>

#include <random>

namespace {

using aidl::android::hardware::automotive::evs::CameraDesc;
using aidl::android::hardware::automotive::evs::IEvsCamera;
using aidl::android::hardware::automotive::evs::IEvsEnumerator;
using aidl::android::hardware::automotive::evs::Stream;

constexpr int MIN_NUM_DEVICES = 1;
constexpr int MAX_NUM_DEVICES = 4;

}  // namespace

namespace aidl::android::automotive::evs::implementation {

std::shared_ptr<MockEvsHal> initializeMockEvsHal() {
    // Initialize a mock EVS HAL with random number of cameras and displays.
    std::random_device r;
    std::default_random_engine engine(r());
    std::uniform_int_distribution<int> uniform_dist(MIN_NUM_DEVICES, MAX_NUM_DEVICES);
    std::shared_ptr<MockEvsHal> mockEvsHal =
            std::make_shared<MockEvsHal>(/* numCameras= */ uniform_dist(engine),
                                         /* numDisplays= */ uniform_dist(engine));
    EXPECT_NE(mockEvsHal, nullptr);
    mockEvsHal->initialize();

    return mockEvsHal;
}

std::shared_ptr<IEvsCamera> openFirstCamera(const std::shared_ptr<MockEvsHal>& handle) {
    EXPECT_NE(handle, nullptr);

    // Get a mock EVS camera.
    std::shared_ptr<IEvsEnumerator> hwEnumerator = handle->getEnumerator();
    EXPECT_NE(hwEnumerator, nullptr);

    std::vector<CameraDesc> cameras;
    EXPECT_TRUE(hwEnumerator->getCameraList(&cameras).isOk());
    EXPECT_GT(cameras.size(), 0);

    std::vector<Stream> configs;
    EXPECT_TRUE(hwEnumerator->getStreamList(cameras[0], &configs).isOk());
    EXPECT_GT(configs.size(), 0);

    std::shared_ptr<IEvsCamera> mockHwCamera;
    EXPECT_TRUE(hwEnumerator->openCamera(cameras[0].id, configs[0], &mockHwCamera).isOk());
    EXPECT_NE(mockHwCamera, nullptr);

    return mockHwCamera;
}

}  // namespace aidl::android::automotive::evs::implementation
