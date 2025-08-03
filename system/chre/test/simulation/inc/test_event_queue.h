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

#ifndef CHRE_SIMULATION_TEST_EVENT_QUEUE_H_
#define CHRE_SIMULATION_TEST_EVENT_QUEUE_H_

#include <gtest/gtest.h>

#include <cinttypes>

#include "chre/platform/memory.h"
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/memory.h"
#include "chre/util/non_copyable.h"
#include "chre/util/singleton.h"
#include "test_event.h"

namespace chre {

//! A test event type to indicate the test nanoapp has loaded.
#define CHRE_EVENT_SIMULATION_TEST_NANOAPP_LOADED \
  CHRE_SIMULATION_TEST_EVENT_ID(0)

//! A test event type to indicate the test has timed out, and should abort.
#define CHRE_EVENT_SIMULATION_TEST_TIMEOUT CHRE_SIMULATION_TEST_EVENT_ID(1)

//! A test event type to indicate the test nanoapp has unloaded.
#define CHRE_EVENT_SIMULATION_TEST_NANOAPP_UNLOADED \
  CHRE_SIMULATION_TEST_EVENT_ID(2)

/**
 * A class that monitors events for the test to consume.
 *
 * This class can be used as an execution barrier for the test, i.e. waiting
 * for a specific event to occur. The barrier is done through the semantics of
 * CHRE events, and can be used e.g. for nanoapps to redirect incoming events
 * using pushEvent().
 *
 * The main test thread can then wait for this event using waitForEvent().
 *
 * Note 1) pushEvent() can also be invoked outside the nanoapp, for instance
 * using deferred system callbacks.
 * Note 2) The CHRE_EVENT_SIMULATION_TEST_TIMEOUT event type can be used to
 * abort the test due to a timeout (this usage is recommended in order to avoid
 * the test framework from stalling).
 */
class TestEventQueue : public NonCopyable {
 public:
  //! Push an event to the queue.
  void pushEvent(uint16_t eventType) {
    mQueue.push({eventType});
  }

  /**
   * Push an event with data to the queue.
   *
   * Note: The data passed to this method must be trivially copyable. It is
   * recommended to pass a scalar or a struct composed of scalars only. If this
   * method is used in the test nanoapp handleEvent be careful not to forward
   * pointers to memory that could be freed by the CHRE framework before the
   * data is received using @ref waitForEvent.
   *
   * @param eventType The type of event.
   * @param eventData The data to send together with the event, which must not
   *        contain references to dynamically allocated memory.
   */
  template <class T>
  void pushEvent(uint16_t eventType, T eventData) {
    static_assert(std::is_trivial<T>::value);
    auto ptr = memoryAlloc<T>();
    ASSERT_NE(ptr, nullptr);
    *ptr = eventData;
    mQueue.push({eventType, static_cast<void *>(ptr)});
  }

  //! Block until the event happens.
  void waitForEvent(uint16_t eventType) {
    LOGD("Waiting for event type 0x%" PRIx16, eventType);
    while (true) {
      auto event = mQueue.pop();
      LOGD("Got event type 0x%" PRIx16, event.type);
      ASSERT_NE(event.type, CHRE_EVENT_SIMULATION_TEST_TIMEOUT)
          << "Timeout waiting for event " << eventType;
      memoryFree(event.data);
      if (event.type == eventType) {
        break;
      }
    }
  }

  //! Block until the event happens and populate the event data.
  template <class T>
  void waitForEvent(uint16_t eventType, T *data) {
    static_assert(std::is_trivial<T>::value);
    LOGD("Waiting for event type 0x%" PRIx16, eventType);
    while (true) {
      auto event = mQueue.pop();
      LOGD("Got event type 0x%" PRIx16, event.type);
      ASSERT_NE(event.type, CHRE_EVENT_SIMULATION_TEST_TIMEOUT)
          << "Timeout waiting for event " << eventType;
      if (event.type == eventType) {
        *data = *(static_cast<T *>(event.data));
        memoryFree(event.data);
        break;
      }
      memoryFree(event.data);
    }
  }

  //! Flush the queue.
  void flush() {
    while (!mQueue.empty()) {
      auto event = mQueue.pop();
      memoryFree(event.data);
    }
  }

 private:
  static const size_t kQueueCapacity = 64;
  FixedSizeBlockingQueue<TestEvent, kQueueCapacity> mQueue;
};

//! Provide an alias to the TestEventQueue singleton.
typedef Singleton<TestEventQueue> TestEventQueueSingleton;

//! Extern the explicit TestEventQueueSingleton to force non-inline method
//! calls.
extern template class Singleton<TestEventQueue>;

}  // namespace chre

#endif  // CHRE_SIMULATION_TEST_EVENT_QUEUE_H_
