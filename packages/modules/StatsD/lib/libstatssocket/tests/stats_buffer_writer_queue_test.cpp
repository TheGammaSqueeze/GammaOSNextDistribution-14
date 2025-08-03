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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>

#include "stats_buffer_writer_queue_impl.h"
#include "stats_event.h"
#include "utils.h"

using testing::_;
using testing::Return;
using testing::StrictMock;

constexpr static int WAIT_MS = 200;

static AStatsEvent* generateTestEvent() {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    AStatsEvent_writeInt32(event, 5);
    AStatsEvent_write(event);
    return event;
}

class BasicBufferWriterQueueMock : public BufferWriterQueue {
public:
    BasicBufferWriterQueueMock() = default;
    MOCK_METHOD(bool, handleCommand, (const BasicBufferWriterQueueMock::Cmd& cmd),
                (const override));
};

typedef StrictMock<BasicBufferWriterQueueMock> BufferWriterQueueMock;

TEST(StatsBufferWriterQueueTest, TestWriteSuccess) {
    AStatsEvent* event = generateTestEvent();

    size_t eventBufferSize = 0;
    const uint8_t* buffer = AStatsEvent_getBuffer(event, &eventBufferSize);
    EXPECT_GE(eventBufferSize, 0);
    EXPECT_TRUE(buffer != nullptr);

    const uint32_t atomId = AStatsEvent_getAtomId(event);

    BufferWriterQueueMock queue;
    EXPECT_CALL(queue, handleCommand(_)).WillOnce(Return(true));
    // simulate failed write to stats socket
    const bool addedToQueue = queue.write(buffer, eventBufferSize, atomId);
    AStatsEvent_release(event);
    EXPECT_TRUE(addedToQueue);
    // to yeld to the queue worker thread
    std::this_thread::sleep_for(std::chrono::milliseconds(WAIT_MS));
}

TEST(StatsBufferWriterQueueTest, TestWriteOverflow) {
    AStatsEvent* event = generateTestEvent();

    size_t eventBufferSize = 0;
    const uint8_t* buffer = AStatsEvent_getBuffer(event, &eventBufferSize);
    EXPECT_GE(eventBufferSize, 0);
    EXPECT_TRUE(buffer != nullptr);

    const uint32_t atomId = AStatsEvent_getAtomId(event);

    BufferWriterQueueMock queue;
    EXPECT_CALL(queue, handleCommand(_)).WillRepeatedly(Return(false));
    // simulate failed write to stats socket
    for (int i = 0; i < BufferWriterQueueMock::kQueueMaxSizeLimit; i++) {
        const bool addedToQueue = queue.write(buffer, eventBufferSize, atomId);
        EXPECT_TRUE(addedToQueue);
    }

    const bool addedToQueue = queue.write(buffer, eventBufferSize, atomId);
    AStatsEvent_release(event);
    EXPECT_FALSE(addedToQueue);

    EXPECT_EQ(queue.getQueueSize(), BufferWriterQueueMock::kQueueMaxSizeLimit);
}

TEST(StatsBufferWriterQueueTest, TestSleepOnOverflow) {
    AStatsEvent* event = generateTestEvent();

    size_t eventBufferSize = 0;
    const uint8_t* buffer = AStatsEvent_getBuffer(event, &eventBufferSize);
    EXPECT_GE(eventBufferSize, 0);
    EXPECT_TRUE(buffer != nullptr);

    const uint32_t atomId = AStatsEvent_getAtomId(event);

    std::vector<int64_t> attemptsTs;

    BufferWriterQueueMock queue;
    EXPECT_CALL(queue, handleCommand(_))
            .WillRepeatedly([&attemptsTs](const BufferWriterQueueMock::Cmd&) {
                // store timestamp for command handler invocations
                attemptsTs.push_back(get_elapsed_realtime_ns());
                return false;
            });

    // simulate failed write to stats socket to fill the queue
    for (int i = 0; i < BufferWriterQueueMock::kQueueMaxSizeLimit; i++) {
        const bool addedToQueue = queue.write(buffer, eventBufferSize, atomId);
        EXPECT_TRUE(addedToQueue);
    }
    AStatsEvent_release(event);

    // to yeld to the queue worker thread
    std::this_thread::sleep_for(std::chrono::milliseconds(WAIT_MS));

    EXPECT_GE(attemptsTs.size(), 2);
    for (int i = 0; i < attemptsTs.size() - 1; i++) {
        EXPECT_GE(attemptsTs[i + 1] - attemptsTs[i],
                  BufferWriterQueueMock::kDelayOnFailedWriteMs * 1000000);
    }
}

TEST(StatsBufferWriterQueueTest, TestTerminateNonEmptyQueue) {
    AStatsEvent* event = generateTestEvent();

    size_t eventBufferSize = 0;
    const uint8_t* buffer = AStatsEvent_getBuffer(event, &eventBufferSize);
    EXPECT_GE(eventBufferSize, 0);
    EXPECT_TRUE(buffer != nullptr);

    const uint32_t atomId = AStatsEvent_getAtomId(event);

    BufferWriterQueueMock queue;
    EXPECT_CALL(queue, handleCommand(_)).WillRepeatedly(Return(false));
    // simulate failed write to stats socket
    for (int i = 0; i < BufferWriterQueueMock::kQueueMaxSizeLimit; i++) {
        const bool addedToQueue = queue.write(buffer, eventBufferSize, atomId);
        EXPECT_TRUE(addedToQueue);
    }
    AStatsEvent_release(event);
    EXPECT_EQ(queue.getQueueSize(), BufferWriterQueueMock::kQueueMaxSizeLimit);
    queue.drainQueue();
    EXPECT_EQ(queue.getQueueSize(), 0);
}
