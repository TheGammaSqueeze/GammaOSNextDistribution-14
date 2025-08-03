/*
 * Copyright (C) 2019 The Android Open Source Project
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

#pragma once

#include <gtest/gtest_prod.h>

#include <condition_variable>
#include <mutex>
#include <queue>

#include "LogEvent.h"

namespace android {
namespace os {
namespace statsd {

/**
 * A zero copy thread safe queue buffer for producing and consuming LogEvent.
 */
class LogEventQueue {
public:
    explicit LogEventQueue(size_t maxSize) : mQueueLimit(maxSize){};

    /**
     * Blocking read one event from the queue.
     */
    std::unique_ptr<LogEvent> waitPop();

    struct Result {
        bool success = false;
        int64_t oldestTimestampNs = 0;
        int32_t size = 0;
    };

    /**
     * Puts a LogEvent ptr to the end of the queue.
     * Returns false on failure when the queue is full, and output the oldest event timestamp
     * in the queue. Returns true on success and new queue size.
     */
    Result push(std::unique_ptr<LogEvent> event);

private:
    const size_t mQueueLimit;
    std::condition_variable mCondition;
    std::mutex mMutex;
    std::queue<std::unique_ptr<LogEvent>> mQueue;

    friend class SocketParseMessageTest;

    FRIEND_TEST(SocketParseMessageTest, TestProcessMessage);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageEmptySetExplicitSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterCompleteSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterPartialSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterToggle);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
