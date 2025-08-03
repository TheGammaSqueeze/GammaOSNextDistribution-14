/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <sysutils/SocketListener.h>
#include <utils/RefBase.h>

#include "LogEventFilter.h"
#include "logd/LogEventQueue.h"

// DEFAULT_OVERFLOWUID is defined in linux/highuid.h, which is not part of
// the uapi headers for userspace to use.  This value is filled in on the
// out-of-band socket credentials if the OS fails to find one available.
// One of the causes of this is if SO_PASSCRED is set, all the packets before
// that point will have this value.  We also use it in a fake credential if
// no socket credentials are supplied.
#ifndef DEFAULT_OVERFLOWUID
#define DEFAULT_OVERFLOWUID 65534
#endif

namespace android {
namespace os {
namespace statsd {

class StatsSocketListener : public SocketListener, public virtual RefBase {
public:
    explicit StatsSocketListener(const std::shared_ptr<LogEventQueue>& queue,
                                 const std::shared_ptr<LogEventFilter>& logEventFilter);

    virtual ~StatsSocketListener() = default;

protected:
    bool onDataAvailable(SocketClient* cli) override;

private:
    static int getLogSocket();

    /**
     * @brief Helper API to parse buffer, make the LogEvent & submit it into the queue
     * Created as a separate API to be easily tested without StatsSocketListener instance
     *
     * @param msg buffer to parse
     * @param len size of buffer in bytes
     * @param uid arguments for LogEvent constructor
     * @param pid arguments for LogEvent constructor
     * @param queue queue to submit the event
     * @param filter to be used for event evaluation
     */
    static void processMessage(const uint8_t* msg, uint32_t len, uint32_t uid, uint32_t pid,
                               const std::shared_ptr<LogEventQueue>& queue,
                               const std::shared_ptr<LogEventFilter>& filter);

    /**
     * Who is going to get the events when they're read.
     */
    std::shared_ptr<LogEventQueue> mQueue;

    std::shared_ptr<LogEventFilter> mLogEventFilter;

    friend class SocketParseMessageTest;
    friend void generateAtomLogging(const std::shared_ptr<LogEventQueue>& queue,
                                    const std::shared_ptr<LogEventFilter>& filter, int eventCount,
                                    int startAtomId);

    FRIEND_TEST(SocketParseMessageTest, TestProcessMessage);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageEmptySetExplicitSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterCompleteSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterPartialSet);
    FRIEND_TEST(SocketParseMessageTest, TestProcessMessageFilterToggle);
    FRIEND_TEST(LogEventQueue_test, TestQueueMaxSize);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
