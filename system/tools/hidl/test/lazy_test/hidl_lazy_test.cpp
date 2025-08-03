/*
 * Copyright (C) 2019, The Android Open Source Project
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

#include <cstdlib>
#include <ctime>
#include <iostream>
#include <numeric>
#include <string>
#include <thread>

#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <unistd.h>

#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <android/hardware/tests/lazy/1.1/ILazy.h>
#include <android/hardware/tests/lazy_cb/1.0/ILazyCb.h>
#include <android/hidl/manager/1.2/IServiceManager.h>
#include <cutils/native_handle.h>
#include <gtest/gtest.h>
#include <hidl-util/FqInstance.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <hidl/HidlTransportUtils.h>
#include <hwbinder/IPCThreadState.h>

using ::android::FqInstance;
using ::android::sp;
using ::android::base::unique_fd;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::IPCThreadState;
using ::android::hardware::Return;
using ::android::hardware::tests::lazy::V1_1::ILazy;
using ::android::hardware::tests::lazy_cb::V1_0::ILazyCb;
using ::android::hidl::base::V1_0::IBase;
using ::android::hidl::manager::V1_2::IServiceManager;

static std::vector<FqInstance> gInstances;

sp<IBase> getHal(const FqInstance& instance) {
    return ::android::hardware::details::getRawServiceInternal(instance.getFqName().string(),
                                                               instance.getInstance(),
                                                               true /*retry*/, false /*getStub*/);
}

class HidlLazyTestBase : public ::testing::Test {
  protected:
    static constexpr size_t SHUTDOWN_WAIT_TIME = 10;
    sp<IServiceManager> manager;

    void SetUp() override {
        manager = IServiceManager::getService();
        ASSERT_NE(manager, nullptr);
        // if the services aren't installed/declared on the device, skip.
        // if one is installed, the other(s) are also installed so skip on the
        // first instance that isn't declared.
        for (const auto& instance : gInstances) {
            if (!isServiceDeclared(instance))
                GTEST_SKIP() << "No HIDL lazy test services on device";
        }
    }

    bool isServiceRunning(const FqInstance& instance) {
        bool isRunning = false;
        EXPECT_TRUE(manager->listByInterface(instance.getFqName().string(),
                                             [&](const hidl_vec<hidl_string>& instanceNames) {
                                                 for (const hidl_string& name : instanceNames) {
                                                     if (name == instance.getInstance()) {
                                                         isRunning = true;
                                                         break;
                                                     }
                                                 }
                                             })
                            .isOk());
        return isRunning;
    }
    bool isServiceDeclared(const FqInstance& instance) {
        const auto transport =
                manager->getTransport(instance.getFqName().string(), instance.getInstance());
        EXPECT_TRUE(transport.isOk());
        if (transport == IServiceManager::Transport::HWBINDER) return true;
        return false;
    }
};

class HidlLazyTest : public HidlLazyTestBase {
  protected:
    void SetUp() override {
        HidlLazyTestBase::SetUp();
        for (const auto& instance : gInstances) {
            ASSERT_FALSE(isServiceRunning(instance))
                    << "Service '" << instance.string()
                    << "' is already running. Please ensure this "
                    << "service is implemented as a lazy HAL, then kill all "
                    << "clients of this service and try again.";
        }
    }

    void TearDown() override {
        std::cout << "Waiting " << SHUTDOWN_WAIT_TIME << " seconds before checking that the "
                  << "service has shut down." << std::endl;
        IPCThreadState::self()->flushCommands();
        int timeout_multiplier = android::base::GetIntProperty("ro.hw_timeout_multiplier", 1);
        sleep(SHUTDOWN_WAIT_TIME * timeout_multiplier);
        for (const auto& instance : gInstances) {
            ASSERT_FALSE(isServiceRunning(instance))
                    << "Service failed to shutdown " << instance.string();
        }
    }
};

class HidlLazyCbTest : public HidlLazyTestBase {
  protected:
    static constexpr size_t CALLBACK_SHUTDOWN_WAIT_TIME = 5;
};

static constexpr size_t NUM_IMMEDIATE_GET_UNGETS = 100;
TEST_F(HidlLazyTest, GetUnget) {
    for (size_t i = 0; i < NUM_IMMEDIATE_GET_UNGETS; i++) {
        IPCThreadState::self()->flushCommands();
        for (const auto& instance : gInstances) {
            sp<IBase> hal = getHal(instance);
            ASSERT_NE(hal.get(), nullptr);
            EXPECT_TRUE(hal->ping().isOk());
        }
    }
}

static std::vector<size_t> waitTimes(size_t numTimes, size_t maxWait) {
    std::vector<size_t> times(numTimes);
    for (size_t i = 0; i < numTimes; i++) {
        times[i] = (size_t)(rand() % (maxWait + 1));
    }
    return times;
}

static void testWithTimes(const std::vector<size_t>& waitTimes, const FqInstance& instance) {
    std::cout << "Note runtime expected from sleeps: "
              << std::accumulate(waitTimes.begin(), waitTimes.end(), 0) << " second(s)."
              << std::endl;

    for (size_t sleepTime : waitTimes) {
        IPCThreadState::self()->flushCommands();
        std::cout << "Thread for " << instance.string() << " waiting " << sleepTime
                  << " while not holding HAL." << std::endl;
        int timeout_multiplier = android::base::GetIntProperty("ro.hw_timeout_multiplier", 1);
        sleep(sleepTime * timeout_multiplier);
        sp<IBase> hal = getHal(instance);
        ASSERT_NE(hal.get(), nullptr);
        ASSERT_TRUE(hal->ping().isOk());
    }
}

static constexpr size_t NUM_TIMES_GET_UNGET = 5;
static constexpr size_t MAX_WAITING_DURATION = 10;
static constexpr size_t NUM_CONCURRENT_THREADS = 5;
TEST_F(HidlLazyTest, GetWithWaitConcurrent) {
    std::vector<std::vector<size_t>> threadWaitTimes(NUM_CONCURRENT_THREADS);

    for (size_t i = 0; i < threadWaitTimes.size(); i++) {
        threadWaitTimes[i] = waitTimes(NUM_TIMES_GET_UNGET, MAX_WAITING_DURATION);
    }

    std::vector<std::thread> threads(NUM_CONCURRENT_THREADS);
    for (size_t i = 0; i < threads.size(); i++) {
        const FqInstance& instance = gInstances[i % gInstances.size()];
        threads[i] = std::thread(testWithTimes, threadWaitTimes[i], instance);
    }

    for (auto& thread : threads) {
        thread.join();
    }
}

TEST_F(HidlLazyCbTest, ActiveServicesCallbackTest) {
    const std::string fqInstanceName = "android.hardware.tests.lazy_cb@1.0::ILazyCb/default";
    FqInstance fqInstance;
    ASSERT_TRUE(fqInstance.setTo(fqInstanceName));

    // b/251244025 - this service logic is coupled with this test, but other
    // things may have started it
    (void)android::base::SetProperty("ctl.stop", "hidl_lazy_cb_test_server");

    ASSERT_FALSE(isServiceRunning(fqInstance)) << "Lazy service already running.";

    sp<IBase> hal = getHal(fqInstance);
    ASSERT_NE(hal, nullptr);

    sp<ILazyCb> lazyCb = ILazyCb::castFrom(hal);
    ASSERT_NE(lazyCb, nullptr);
    hal = nullptr;

    int efd = eventfd(0, 0);
    ASSERT_GE(efd, 0) << "Failed to create eventfd";
    unique_fd uniqueEventFd(efd);

    native_handle_t* h = native_handle_create(/* numFds */ 1, /* numInts */ 0);
    h->data[0] = efd;
    hidl_handle handle(h);
    Return<bool> setEventFdRet = lazyCb->setEventFd(handle);
    native_handle_delete(h);
    ASSERT_TRUE(setEventFdRet.isOk());
    ASSERT_TRUE(setEventFdRet);

    lazyCb = nullptr;

    IPCThreadState::self()->flushCommands();

    std::cout << "Waiting " << SHUTDOWN_WAIT_TIME << " seconds for callback completion "
              << "notification." << std::endl;

    int epollFd = epoll_create1(EPOLL_CLOEXEC);
    ASSERT_GE(epollFd, 0) << "Failed to create epoll";
    unique_fd epollUniqueFd(epollFd);

    const int EPOLL_MAX_EVENTS = 1;
    struct epoll_event event, events[EPOLL_MAX_EVENTS];

    event.events = EPOLLIN;
    event.data.fd = efd;
    int rc = epoll_ctl(epollFd, EPOLL_CTL_ADD, efd, &event);
    ASSERT_GE(rc, 0) << "Failed to add fd to epoll";

    rc = TEMP_FAILURE_RETRY(
            epoll_wait(epollFd, events, EPOLL_MAX_EVENTS, SHUTDOWN_WAIT_TIME * 1000));
    ASSERT_NE(rc, 0) << "Service shutdown timeout";
    ASSERT_GT(rc, 0) << "Error waiting for service shutdown notification";

    eventfd_t counter;
    rc = TEMP_FAILURE_RETRY(eventfd_read(uniqueEventFd.get(), &counter));
    ASSERT_GE(rc, 0) << "Failed to get callback completion notification from service";
    ASSERT_EQ(counter, 1);

    std::cout << "Waiting " << CALLBACK_SHUTDOWN_WAIT_TIME
              << " seconds before checking whether the "
              << "service is still running." << std::endl;

    int timeout_multiplier = android::base::GetIntProperty("ro.hw_timeout_multiplier", 1);
    sleep(CALLBACK_SHUTDOWN_WAIT_TIME * timeout_multiplier);

    ASSERT_FALSE(isServiceRunning(fqInstance)) << "Service failed to shut down.";
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);

    srand(time(nullptr));

    std::vector<std::string> fqInstances;

    if (argc == 1) {
        fqInstances.push_back("android.hardware.tests.lazy@1.0::ILazy/default1");
        fqInstances.push_back("android.hardware.tests.lazy@1.0::ILazy/default2");
    } else {
        for (size_t arg = 1; arg < argc; arg++) {
            fqInstances.push_back(argv[arg]);
        }
    }

    for (const std::string& instance : fqInstances) {
        FqInstance fqInstance;
        if (!fqInstance.setTo(instance)) {
            std::cerr << "Invalid fqinstance: " << instance << std::endl;
            return 1;
        }
        gInstances.push_back(fqInstance);
    }

    return RUN_ALL_TESTS();
}
