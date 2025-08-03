/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre_api/chre/re.h"

#include <cstdint>

#include "chre/core/event_loop_manager.h"
#include "chre/platform/log.h"
#include "chre/platform/memory_manager.h"
#include "chre_api/chre/event.h"

#include "gtest/gtest.h"
#include "inc/test_util.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {
namespace {

TEST_F(TestBase, MemoryAllocateAndFree) {
  CREATE_CHRE_TEST_EVENT(ALLOCATE, 0);
  CREATE_CHRE_TEST_EVENT(FREE, 1);

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case ALLOCATE: {
              auto bytes = static_cast<const uint32_t *>(event->data);
              void *ptr = chreHeapAlloc(*bytes);
              TestEventQueueSingleton::get()->pushEvent(ALLOCATE, ptr);
              break;
            }
            case FREE: {
              auto ptr = static_cast<void **>(event->data);
              chreHeapFree(*ptr);
              TestEventQueueSingleton::get()->pushEvent(FREE);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  MemoryManager &memManager =
      EventLoopManagerSingleton::get()->getMemoryManager();
  Nanoapp *nanoapp = getNanoappByAppId(appId);
  ASSERT_NE(nanoapp, nullptr);

  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);

  void *ptr1;
  sendEventToNanoapp(appId, ALLOCATE, 100);
  waitForEvent(ALLOCATE, &ptr1);
  EXPECT_NE(ptr1, nullptr);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 100);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100);
  EXPECT_EQ(memManager.getAllocationCount(), 1);

  void *ptr2;
  sendEventToNanoapp(appId, ALLOCATE, 200);
  waitForEvent(ALLOCATE, &ptr2);
  EXPECT_NE(ptr2, nullptr);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 100 + 200);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100 + 200);
  EXPECT_EQ(memManager.getAllocationCount(), 2);

  sendEventToNanoapp(appId, FREE, ptr1);
  waitForEvent(FREE);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 200);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 200);
  EXPECT_EQ(memManager.getAllocationCount(), 1);

  sendEventToNanoapp(appId, FREE, ptr2);
  waitForEvent(FREE);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);
}

TEST_F(TestBase, MemoryFreeOnNanoappUnload) {
  CREATE_CHRE_TEST_EVENT(ALLOCATE, 0);

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case ALLOCATE: {
              auto bytes = static_cast<const uint32_t *>(event->data);
              void *ptr = chreHeapAlloc(*bytes);
              TestEventQueueSingleton::get()->pushEvent(ALLOCATE, ptr);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  MemoryManager &memManager =
      EventLoopManagerSingleton::get()->getMemoryManager();
  Nanoapp *nanoapp = getNanoappByAppId(appId);
  ASSERT_NE(nanoapp, nullptr);

  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);

  void *ptr1;
  sendEventToNanoapp(appId, ALLOCATE, 100);
  waitForEvent(ALLOCATE, &ptr1);
  EXPECT_NE(ptr1, nullptr);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 100);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100);
  EXPECT_EQ(memManager.getAllocationCount(), 1);

  void *ptr2;
  sendEventToNanoapp(appId, ALLOCATE, 200);
  waitForEvent(ALLOCATE, &ptr2);
  EXPECT_NE(ptr2, nullptr);
  EXPECT_EQ(nanoapp->getTotalAllocatedBytes(), 100 + 200);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100 + 200);
  EXPECT_EQ(memManager.getAllocationCount(), 2);

  unloadNanoapp(appId);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);
}

TEST_F(TestBase, MemoryStressTestShouldNotTriggerErrors) {
  CREATE_CHRE_TEST_EVENT(ALLOCATE, 0);
  CREATE_CHRE_TEST_EVENT(FREE, 1);

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case ALLOCATE: {
              auto bytes = static_cast<const uint32_t *>(event->data);
              void *ptr = chreHeapAlloc(*bytes);
              TestEventQueueSingleton::get()->pushEvent(ALLOCATE, ptr);
              break;
            }
            case FREE: {
              auto ptr = static_cast<void **>(event->data);
              chreHeapFree(*ptr);
              TestEventQueueSingleton::get()->pushEvent(FREE);
              break;
            }
          }
        }
      }
    }
  };

  MemoryManager &memManager =
      EventLoopManagerSingleton::get()->getMemoryManager();

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);

  void *ptr1;
  void *ptr2;
  void *ptr3;

  sendEventToNanoapp(appId, ALLOCATE, 100);
  waitForEvent(ALLOCATE, &ptr1);
  sendEventToNanoapp(appId, ALLOCATE, 200);
  waitForEvent(ALLOCATE, &ptr2);
  sendEventToNanoapp(appId, ALLOCATE, 300);
  waitForEvent(ALLOCATE, &ptr3);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100 + 200 + 300);
  EXPECT_EQ(memManager.getAllocationCount(), 3);

  // Free middle, last, and first blocks.
  sendEventToNanoapp(appId, FREE, ptr2);
  waitForEvent(FREE);
  sendEventToNanoapp(appId, FREE, ptr3);
  waitForEvent(FREE);
  sendEventToNanoapp(appId, FREE, ptr1);
  waitForEvent(FREE);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);

  sendEventToNanoapp(appId, ALLOCATE, 100);
  waitForEvent(ALLOCATE, &ptr1);
  sendEventToNanoapp(appId, ALLOCATE, 200);
  waitForEvent(ALLOCATE, &ptr2);
  sendEventToNanoapp(appId, ALLOCATE, 300);
  waitForEvent(ALLOCATE, &ptr3);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100 + 200 + 300);
  EXPECT_EQ(memManager.getAllocationCount(), 3);

  // Free last, last and last blocks.
  sendEventToNanoapp(appId, FREE, ptr3);
  waitForEvent(FREE);
  sendEventToNanoapp(appId, FREE, ptr2);
  waitForEvent(FREE);
  sendEventToNanoapp(appId, FREE, ptr1);
  waitForEvent(FREE);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);

  sendEventToNanoapp(appId, ALLOCATE, 100);
  waitForEvent(ALLOCATE, &ptr1);
  sendEventToNanoapp(appId, ALLOCATE, 200);
  waitForEvent(ALLOCATE, &ptr2);
  sendEventToNanoapp(appId, ALLOCATE, 300);
  waitForEvent(ALLOCATE, &ptr3);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 100 + 200 + 300);
  EXPECT_EQ(memManager.getAllocationCount(), 3);

  // Automatic cleanup.
  unloadNanoapp(appId);
  EXPECT_EQ(memManager.getTotalAllocatedBytes(), 0);
  EXPECT_EQ(memManager.getAllocationCount(), 0);
}

}  // namespace
}  // namespace chre