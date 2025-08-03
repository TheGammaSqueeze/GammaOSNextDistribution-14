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

#include "stats_buffer_writer_queue.h"

#include <private/android_filesystem_config.h>
#include <unistd.h>

#include <chrono>
#include <queue>
#include <thread>

#include "stats_buffer_writer_impl.h"
#include "stats_buffer_writer_queue_impl.h"
#include "utils.h"

BufferWriterQueue::BufferWriterQueue() : mWorkThread(&BufferWriterQueue::processCommands, this) {
}

BufferWriterQueue::~BufferWriterQueue() {
    terminate();
    // at this stage there can be N elements in the queue for which memory needs to be freed
    // explicitly
    drainQueue();
}

bool BufferWriterQueue::write(const uint8_t* buffer, size_t size, uint32_t atomId) {
    Cmd cmd = createWriteBufferCmd(buffer, size, atomId);
    if (cmd.buffer == NULL) {
        return false;
    }
    return pushToQueue(cmd);
}

size_t BufferWriterQueue::getQueueSize() const {
    std::unique_lock<std::mutex> lock(mMutex);
    return mCmdQueue.size();
}

bool BufferWriterQueue::pushToQueue(const Cmd& cmd) {
    {
        std::unique_lock<std::mutex> lock(mMutex);
        if (mCmdQueue.size() >= kQueueMaxSizeLimit) {
            // TODO (b/258003151): add logging info about internal queue overflow with appropriate
            // error code
            return false;
        }
        mCmdQueue.push(cmd);
    }
    mCondition.notify_one();
    return true;
}

BufferWriterQueue::Cmd BufferWriterQueue::createWriteBufferCmd(const uint8_t* buffer, size_t size,
                                                               uint32_t atomId) {
    BufferWriterQueue::Cmd writeCmd;
    writeCmd.atomId = atomId;
    writeCmd.buffer = (uint8_t*)malloc(size);
    if (writeCmd.buffer == NULL) {
        return writeCmd;
    }
    memcpy(writeCmd.buffer, buffer, size);
    writeCmd.size = size;
    return writeCmd;
}

void BufferWriterQueue::terminate() {
    if (mWorkThread.joinable()) {
        mDoTerminate = true;
        Cmd terminateCmd;
        terminateCmd.buffer = NULL;
        pushToQueue(terminateCmd);
        mWorkThread.join();
    }
}

void BufferWriterQueue::drainQueue() {
    std::unique_lock<std::mutex> lock(mMutex);
    while (!mCmdQueue.empty()) {
        free(mCmdQueue.front().buffer);
        mCmdQueue.pop();
    }
}

void BufferWriterQueue::processCommands() {
    while (true) {
        // temporary local thread copy
        Cmd cmd;
        {
            std::unique_lock<std::mutex> lock(mMutex);
            if (mCmdQueue.empty()) {
                mCondition.wait(lock, [this] { return !this->mCmdQueue.empty(); });
            }
            cmd = mCmdQueue.front();
        }

        if (cmd.buffer == NULL) {
            // null buffer ptr used as a marker of the termination request
            return;
        }

        const bool writeSuccess = handleCommand(cmd);
        if (writeSuccess) {
            // no event drop is observed otherwise command remains in the queue
            // and worker thread will try to log later on

            // call free() explicitly here to free memory before the mutex lock
            free(cmd.buffer);
            {
                std::unique_lock<std::mutex> lock(mMutex);
                // this will lead to Cmd destructor call which will be no-op since now the
                // buffer is NULL
                mCmdQueue.pop();
            }
        }
        // TODO (b/258003151): add logging info about retry count

        if (mDoTerminate) {
            return;
        }

        // attempt to enforce the logging frequency constraints
        // in case of failed write due to socket overflow the sleep can be longer
        // to not overload socket continuously
        if (!writeSuccess) {
            std::this_thread::sleep_for(std::chrono::milliseconds(kDelayOnFailedWriteMs));
        }
    }
}

bool BufferWriterQueue::handleCommand(const Cmd& cmd) const {
    // skip log drop if occurs, since the atom remains in the queue and write will be retried
    return write_buffer_to_statsd_impl(cmd.buffer, cmd.size, cmd.atomId, /*doNoteDrop*/ false) > 0;
}

bool write_buffer_to_statsd_queue(const uint8_t* buffer, size_t size, uint32_t atomId) {
    static BufferWriterQueue queue;
    return queue.write(buffer, size, atomId);
}

#ifdef ENABLE_BENCHMARK_SUPPORT
bool should_write_via_queue(uint32_t atomId) {
#else
bool should_write_via_queue(uint32_t /*atomId*/) {
#endif
    const uint32_t appUid = getuid();

    // hard-coded push all system server atoms to queue
    if (appUid == AID_SYSTEM) {
        return true;
    }

#ifdef ENABLE_BENCHMARK_SUPPORT
    // some hand-picked atoms to be pushed into the queue
    switch (atomId) {
        case 47:  // APP_BREADCRUMB_REPORTED for statsd_benchmark purpose
            return true;
        default:
            return false;
    }
#endif  // ENABLE_BENCHMARK_SUPPORT
    return false;
}
