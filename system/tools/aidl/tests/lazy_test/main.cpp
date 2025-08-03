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

#include <sys/eventfd.h>
#include <cstdlib>
#include <ctime>
#include <iostream>
#include <numeric>
#include <string>
#include <thread>

#include <sys/epoll.h>
#include <unistd.h>

#include <ILazyTestService.h>
#include <ILazyTestServiceCb.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <gtest/gtest.h>
#include <utils/String8.h>

using ::ILazyTestService;
using ::android::DEAD_OBJECT;
using ::android::IBinder;
using ::android::IPCThreadState;
using ::android::IServiceManager;
using ::android::OK;
using ::android::sp;
using ::android::String16;
using ::android::base::unique_fd;
using ::android::os::ParcelFileDescriptor;

std::vector<String16> gServiceNames;
static constexpr size_t SHUTDOWN_WAIT_MS = 10000;
static constexpr size_t CALLBACK_SHUTDOWN_WAIT_MS = 5000;

sp<IBinder> waitForService(const String16& name) {
  sp<IServiceManager> manager;
  manager = android::defaultServiceManager();
  EXPECT_NE(manager, nullptr);

  return manager->waitForService(name);
}

bool isServiceRunning(const String16& name) {
  sp<IServiceManager> manager;
  manager = android::defaultServiceManager();
  EXPECT_NE(manager, nullptr);

  return manager->checkService(name) != nullptr;
}

TEST(AidlLazyQuitTest, Quits) {
  const String16 quitter = String16("aidl_lazy_test_quit");
  usleep(SHUTDOWN_WAIT_MS * 1000);
  ASSERT_FALSE(isServiceRunning(quitter));

  auto binder = waitForService(quitter);

  // binder dies. Could also linkToDeath to switch to cond var.
  for (size_t i = 0; i < 1000; i++) {
    if (binder->pingBinder() != OK) break;
    usleep(10000);
  }
  EXPECT_EQ(DEAD_OBJECT, binder->pingBinder());

  // service should quit immediately, but wait a bit before checking
  // so that servicemanager has time to process the death notification
  usleep(SHUTDOWN_WAIT_MS * 1000 / 2);
  ASSERT_FALSE(isServiceRunning(quitter));
}

class AidlLazyTest : public ::testing::Test {
 protected:
  sp<IServiceManager> manager;

  void SetUp() override {
    manager = android::defaultServiceManager();
    ASSERT_NE(manager, nullptr);

    for (size_t i = 0; i < gServiceNames.size(); i++) {
      ASSERT_FALSE(isServiceRunning(gServiceNames.at(i)))
          << "Service '" << android::String8(gServiceNames.at(i)) << "' is already running. "
          << "Please ensure this is implemented as a lazy service, then kill all "
          << "clients of this service and try again.";
    }
  }

  void TearDown() override {
    std::cout << "Waiting " << SHUTDOWN_WAIT_MS << " milliseconds before checking that the "
              << "service has shut down." << std::endl;
    IPCThreadState::self()->flushCommands();
    usleep(SHUTDOWN_WAIT_MS * 1000);
    for (size_t i = 0; i < gServiceNames.size(); i++) {
      ASSERT_FALSE(isServiceRunning(gServiceNames.at(i))) << "Service failed to shut down.";
    }
  }
};

static constexpr size_t NUM_IMMEDIATE_GETS = 100;
TEST_F(AidlLazyTest, GetRelease) {
  size_t nServices = gServiceNames.size();

  for (size_t i = 0; i < nServices * NUM_IMMEDIATE_GETS; i++) {
    IPCThreadState::self()->flushCommands();
    sp<IBinder> service = waitForService(gServiceNames.at(i % nServices));
    ASSERT_NE(service.get(), nullptr);
    ASSERT_EQ(service->pingBinder(), android::NO_ERROR);
  }
}

static std::vector<size_t> waitMs(size_t numTimes, size_t maxWait) {
  std::vector<size_t> times(numTimes);
  for (size_t i = 0; i < numTimes; i++) {
    times.at(i) = (size_t)(rand() % (maxWait + 1));
  }
  return times;
}

static void testWithTimes(const std::vector<size_t>& waitMs, bool beforeGet) {
  size_t nServices = gServiceNames.size();
  for (size_t i = 0; i < waitMs.size(); i++) {
    IPCThreadState::self()->flushCommands();
    if (beforeGet) {
      std::cout << "Thread waiting " << waitMs.at(i) << " while not holding service." << std::endl;
      usleep(waitMs.at(i) * 1000);
    }

    sp<IBinder> service = waitForService(gServiceNames.at(i % nServices));

    if (!beforeGet) {
      std::cout << "Thread waiting " << waitMs.at(i) << " while holding service." << std::endl;
      usleep(waitMs.at(i) * 1000);
    }

    ASSERT_NE(service.get(), nullptr);
    ASSERT_EQ(service->pingBinder(), android::NO_ERROR);
  }
}

static constexpr size_t NUM_TIMES_GET_RELEASE = 5;
static constexpr size_t MAX_WAITING_DURATION_MS = 10000;
static constexpr size_t NUM_CONCURRENT_THREADS = 3;
static void testConcurrentThreadsWithDelays(bool delayBeforeGet) {
  size_t nServices = gServiceNames.size();
  std::vector<std::vector<size_t>> threadWaitTimes(NUM_CONCURRENT_THREADS);
  int maxWait = 0;
  for (size_t i = 0; i < threadWaitTimes.size(); i++) {
    threadWaitTimes.at(i) = waitMs(NUM_TIMES_GET_RELEASE * nServices, MAX_WAITING_DURATION_MS);
    int totalWait = std::accumulate(threadWaitTimes.at(i).begin(), threadWaitTimes.at(i).end(), 0);
    maxWait = std::max(maxWait, totalWait);
  }
  std::cout << "Additional runtime expected from sleeps: " << maxWait << " millisecond(s)."
            << std::endl;

  std::vector<std::thread> threads(NUM_CONCURRENT_THREADS);
  for (size_t i = 0; i < threads.size(); i++) {
    threads.at(i) = std::thread(testWithTimes, threadWaitTimes.at(i), delayBeforeGet);
  }

  for (auto& thread : threads) {
    thread.join();
  }
}

TEST_F(AidlLazyTest, GetConcurrentWithWaitBefore) {
  testConcurrentThreadsWithDelays(true);
}

TEST_F(AidlLazyTest, GetConcurrentWithWaitAfter) {
  testConcurrentThreadsWithDelays(false);
}

class AidlLazyRegistrarTest : public ::testing::Test {
 protected:
  const String16 serviceName = String16("aidl_lazy_test_1");
  void SetUp() override {
    if (std::find(gServiceNames.begin(), gServiceNames.end(), serviceName) == gServiceNames.end()) {
      GTEST_SKIP() << "Persistence test requires special instance: " << serviceName;
    }
  }
};

template <typename T>
sp<T> waitForLazyTestService(String16 name) {
  sp<T> service = android::waitForService<T>(name);
  EXPECT_NE(service, nullptr);
  return service;
}

TEST_F(AidlLazyRegistrarTest, ForcedPersistenceTest) {
  sp<ILazyTestService> service;
  for (int i = 0; i < 2; i++) {
    service = waitForLazyTestService<ILazyTestService>(serviceName);
    EXPECT_TRUE(service->forcePersist(i == 0).isOk());
    service = nullptr;

    std::cout << "Waiting " << SHUTDOWN_WAIT_MS << " milliseconds before checking whether the "
              << "service is still running." << std::endl;
    IPCThreadState::self()->flushCommands();
    usleep(SHUTDOWN_WAIT_MS * 1000);

    if (i == 0) {
      ASSERT_TRUE(isServiceRunning(serviceName)) << "Service shut down when it shouldn't have.";
    } else {
      ASSERT_FALSE(isServiceRunning(serviceName)) << "Service failed to shut down.";
    }
  }
}

TEST_F(AidlLazyRegistrarTest, ActiveServicesCountCallbackTest) {
  const String16 cbServiceName = String16("aidl_lazy_cb_test");

  int efd = eventfd(0, 0);
  ASSERT_GE(efd, 0) << "Failed to create eventfd";

  unique_fd uniqueEventFd(efd);
  ParcelFileDescriptor parcelEventFd(std::move(uniqueEventFd));

  sp<ILazyTestServiceCb> service;
  service = waitForLazyTestService<ILazyTestServiceCb>(cbServiceName);
  ASSERT_TRUE(service->setEventFd(parcelEventFd).isOk());
  service = nullptr;

  IPCThreadState::self()->flushCommands();

  std::cout << "Waiting " << SHUTDOWN_WAIT_MS << " milliseconds for callback completion "
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

  rc = TEMP_FAILURE_RETRY(epoll_wait(epollFd, events, EPOLL_MAX_EVENTS, SHUTDOWN_WAIT_MS));
  ASSERT_NE(rc, 0) << "Service shutdown timeout";
  ASSERT_GT(rc, 0) << "Error waiting for service shutdown notification";

  eventfd_t counter;
  rc = TEMP_FAILURE_RETRY(eventfd_read(parcelEventFd.get(), &counter));
  ASSERT_GE(rc, 0) << "Failed to get callback completion notification from service";
  ASSERT_EQ(counter, 1);

  std::cout << "Waiting " << CALLBACK_SHUTDOWN_WAIT_MS
            << " milliseconds before checking whether the "
            << "service is still running." << std::endl;

  usleep(CALLBACK_SHUTDOWN_WAIT_MS * 1000);

  ASSERT_FALSE(isServiceRunning(serviceName)) << "Service failed to shut down.";
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);

  srand(time(nullptr));

  if (argc < 2) {
    // If the user does not specify any service to test, default to these test interfaces
    gServiceNames.push_back(String16("aidl_lazy_test_1"));
    gServiceNames.push_back(String16("aidl_lazy_test_2"));
  } else {
    for (int i = 1; i < argc; i++) {
      gServiceNames.push_back(String16(argv[i]));
    }
  }

  android::ProcessState::self()->startThreadPool();

  return RUN_ALL_TESTS();
}
