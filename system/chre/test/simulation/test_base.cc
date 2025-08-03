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

#include "test_base.h"

#include <gtest/gtest.h>

#include "chre/core/event_loop_manager.h"
#include "chre/core/init.h"
#include "chre/platform/linux/platform_log.h"
#include "chre/platform/linux/task_util/task_manager.h"
#include "chre/util/time.h"
#include "chre_api/chre/version.h"
#include "inc/test_util.h"
#include "test_util.h"

namespace chre {

/**
 * This base class initializes and runs the event loop.
 *
 * This test framework makes use of the TestEventQueue as a primary method
 * of a test execution barrier (see its documentation for details). To simplify
 * the test execution flow, it is encouraged that any communication between
 * threads (e.g. a nanoapp and the main test thread) through this
 * TestEventQueue. In this way, we can design simulation tests in a way that
 * validates an expected sequence of events in a well-defined manner.
 *
 * To avoid the test from potentially stalling, we also push a timeout event
 * to the TestEventQueue once a fixed timeout has elapsed since the start of
 * this test.
 */
void TestBase::SetUp() {
  TaskManagerSingleton::init();
  TestEventQueueSingleton::init();
  chre::PlatformLogSingleton::init();
  chre::init();
  EventLoopManagerSingleton::get()->lateInit();

  mChreThread = std::thread(
      []() { EventLoopManagerSingleton::get()->getEventLoop().run(); });

  auto callback = [](void *) {
    LOGE("Test timed out ...");
    TestEventQueueSingleton::get()->pushEvent(
        CHRE_EVENT_SIMULATION_TEST_TIMEOUT);
  };

  ASSERT_TRUE(mSystemTimer.init());
  ASSERT_TRUE(mSystemTimer.set(callback, nullptr /*data*/,
                               Nanoseconds(getTimeoutNs())));
}

void TestBase::TearDown() {
  mSystemTimer.cancel();
  // Free memory allocated for event on the test queue.
  TestEventQueueSingleton::get()->flush();
  EventLoopManagerSingleton::get()->getEventLoop().stop();
  mChreThread.join();

  chre::deinit();
  chre::PlatformLogSingleton::deinit();
  TestEventQueueSingleton::deinit();
  TaskManagerSingleton::deinit();
  deleteNanoappInfos();
  unregisterAllTestNanoapps();
}

TEST_F(TestBase, CanLoadAndStartSingleNanoapp) {
  constexpr uint64_t kAppId = 0x0123456789abcdef;
  constexpr uint32_t kAppVersion = 0;
  constexpr uint32_t kAppPerms = 0;

  UniquePtr<Nanoapp> nanoapp = createStaticNanoapp(
      "Test nanoapp", kAppId, kAppVersion, kAppPerms, defaultNanoappStart,
      defaultNanoappHandleEvent, defaultNanoappEnd);

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::FinishLoadingNanoapp, std::move(nanoapp),
      testFinishLoadingNanoappCallback);
  waitForEvent(CHRE_EVENT_SIMULATION_TEST_NANOAPP_LOADED);
}

TEST_F(TestBase, CanLoadAndStartMultipleNanoapps) {
  constexpr uint64_t kAppId1 = 0x123;
  constexpr uint64_t kAppId2 = 0x456;
  constexpr uint32_t kAppVersion = 0;
  constexpr uint32_t kAppPerms = 0;
  loadNanoapp("Test nanoapp", kAppId1, kAppVersion, kAppPerms,
              defaultNanoappStart, defaultNanoappHandleEvent,
              defaultNanoappEnd);

  loadNanoapp("Test nanoapp", kAppId2, kAppVersion, kAppPerms,
              defaultNanoappStart, defaultNanoappHandleEvent,
              defaultNanoappEnd);

  uint16_t id1;
  EXPECT_TRUE(EventLoopManagerSingleton::get()
                  ->getEventLoop()
                  .findNanoappInstanceIdByAppId(kAppId1, &id1));
  uint16_t id2;
  EXPECT_TRUE(EventLoopManagerSingleton::get()
                  ->getEventLoop()
                  .findNanoappInstanceIdByAppId(kAppId2, &id2));

  EXPECT_NE(id1, id2);
}

TEST_F(TestBase, methods) {
  CREATE_CHRE_TEST_EVENT(SOME_EVENT, 0);

  class App : public TestNanoapp {
   public:
    explicit App(TestNanoappInfo info) : TestNanoapp(info) {}
    bool start() override {
      LOGE("start");
      mTest = 0xc0ffee;
      return true;
    }

    void handleEvent(uint32_t /*senderInstanceId*/, uint16_t /*eventType*/,
                     const void * /**eventData*/) override {
      LOGE("handleEvent %" PRIx16, mTest);
    }

    void end() override {
      LOGE("end");
    }

   protected:
    uint32_t mTest = 0;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>(TestNanoappInfo{.id = 0x456}));

  sendEventToNanoapp(appId, SOME_EVENT);
}

// Explicitly instantiate the TestEventQueueSingleton to reduce codesize.
template class Singleton<TestEventQueue>;

}  // namespace chre
