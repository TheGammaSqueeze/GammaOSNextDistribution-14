/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "SysfsMonitor.h"

#include <gtest/gtest.h>
#include <utils/StrongPointer.h>

#include <sys/socket.h>

#include <vector>

namespace android {
namespace automotive {

class SysfsMonitorTest : public ::testing::Test {
public:
    void SetUp() override { mSysfsMonitor = sp<SysfsMonitor>::make(); }
    void TearDown() override { mSysfsMonitor = nullptr; }

protected:
    sp<SysfsMonitor> mSysfsMonitor;
};

TEST_F(SysfsMonitorTest, TestDuplicateInitialize) {
    auto ret = mSysfsMonitor->init([](const std::vector<int32_t>&) {});
    ASSERT_TRUE(ret.ok()) << "First initialization should be successful: " << ret.error().message();
    ASSERT_FALSE(mSysfsMonitor->init([](const std::vector<int32_t>&) {}).ok())
            << "Initialization cannot be done twice";
}

TEST_F(SysfsMonitorTest, TestDuplicateRelease) {
    auto ret = mSysfsMonitor->init([](const std::vector<int32_t>&) {});
    ASSERT_TRUE(ret.ok()) << "First initialization should be successful: " << ret.error().message();
    ret = mSysfsMonitor->release();
    ASSERT_TRUE(ret.ok()) << "Releasing the initialized instance should be successful: "
                          << ret.error().message();
    ASSERT_FALSE(mSysfsMonitor->release().ok()) << "Released instance cannot be released";
}

TEST_F(SysfsMonitorTest, TestNoInitRelease) {
    ASSERT_FALSE(mSysfsMonitor->release().ok()) << "Uninitailized instance cannot be released";
}

TEST_F(SysfsMonitorTest, TestUnregisterNotRegisteredFd) {
    // A regular file cannot be registered to epoll instance. So, the test is using a socket file.
    int32_t fd = socket(AF_UNIX, SOCK_DGRAM, /*protocol=*/0);
    mSysfsMonitor->init([](const std::vector<int32_t>&) {});

    ASSERT_FALSE(mSysfsMonitor->unregisterFd(fd).ok())
            << "Unregistered file description cannot be unregistered";
    close(fd);
}

TEST_F(SysfsMonitorTest, TestDuplicateRegister) {
    int32_t fd = socket(AF_UNIX, SOCK_DGRAM, /*protocol=*/0);
    mSysfsMonitor->init([](const std::vector<int32_t>&) {});

    auto ret = mSysfsMonitor->registerFd(fd);
    ASSERT_TRUE(ret.ok()) << "Registering a file descriptor first time should be successful: "
                          << ret.error().message();
    ASSERT_FALSE(mSysfsMonitor->registerFd(fd).ok())
            << "Registering a file descriptor twice cannot be done";
    close(fd);
}

TEST_F(SysfsMonitorTest, TestDuplicateUnregister) {
    int32_t fd = socket(AF_UNIX, SOCK_DGRAM, /*protocol=*/0);
    mSysfsMonitor->init([](const std::vector<int32_t>&) {});

    auto ret = mSysfsMonitor->registerFd(fd);
    ASSERT_TRUE(ret.ok()) << "Registering a file descriptor first time should be successful: "
                          << ret.error().message();
    ret = mSysfsMonitor->unregisterFd(fd);
    ASSERT_TRUE(ret.ok()) << "The registered file descriptor should be unregistered: "
                          << ret.error().message();
    ASSERT_FALSE(mSysfsMonitor->unregisterFd(fd).ok())
            << "Unregistering the unregistered file descriptor cannot be done";
    close(fd);
}

TEST_F(SysfsMonitorTest, TestRegisterInvalidFd) {
    const int32_t invalidFd = -1;
    ASSERT_FALSE(mSysfsMonitor->registerFd(invalidFd).ok())
            << "fd(-1) cannot be registered to SysfsMonitor";
}

TEST_F(SysfsMonitorTest, TestUnregisterInvalidFd) {
    const int32_t invalidFd = -1;
    ASSERT_FALSE(mSysfsMonitor->unregisterFd(invalidFd).ok())
            << "fd(-1) cannot be given to unregisterFd";
}

TEST_F(SysfsMonitorTest, TestRegisterMultipleFds) {
    const int32_t maxFdCount = 10;
    int32_t fdsToMonitor[maxFdCount + 1];

    mSysfsMonitor->init([](const std::vector<int32_t>&) {});
    for (int i = 0; i < maxFdCount; i++) {
        fdsToMonitor[i] = socket(AF_UNIX, SOCK_DGRAM, /*protocol=*/0);
        auto ret = mSysfsMonitor->registerFd(fdsToMonitor[i]);
        ASSERT_TRUE(ret.ok()) << "Registering a file descriptor first time should be successful: "
                              << ret.error().message();
    }
    fdsToMonitor[maxFdCount] = socket(AF_UNIX, SOCK_DGRAM, /*protocol=*/0);
    ASSERT_FALSE(mSysfsMonitor->registerFd(fdsToMonitor[maxFdCount]).ok())
            << "Registering more than " << maxFdCount << " files cannot be done";

    mSysfsMonitor->release();
    for (int i = 0; i <= maxFdCount; i++) {
        close(fdsToMonitor[i]);
    }
}

TEST_F(SysfsMonitorTest, TestObserveWithoutInitialization) {
    ASSERT_FALSE(mSysfsMonitor->observe().ok()) << "Uninitialized instance cannot observe";
}

}  // namespace automotive
}  // namespace android
