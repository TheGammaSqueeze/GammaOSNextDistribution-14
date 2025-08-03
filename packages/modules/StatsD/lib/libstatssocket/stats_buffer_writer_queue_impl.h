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

#pragma once

#include <stddef.h>
#include <stdint.h>

#include <queue>
#include <thread>

class BufferWriterQueue {
public:
    constexpr static int kDelayOnFailedWriteMs = 5;
    constexpr static int kQueueMaxSizeLimit = 4800;  // 2X max_dgram_qlen

    BufferWriterQueue();
    virtual ~BufferWriterQueue();

    bool write(const uint8_t* buffer, size_t size, uint32_t atomId);

    size_t getQueueSize() const;

    void drainQueue();

    struct Cmd {
        uint8_t* buffer = NULL;
        int atomId = 0;
        int size = 0;
    };

    virtual bool handleCommand(const Cmd& cmd) const;

private:
    std::condition_variable mCondition;
    mutable std::mutex mMutex;
    std::queue<Cmd> mCmdQueue;
    std::atomic_bool mDoTerminate = false;
    std::thread mWorkThread;

    static Cmd createWriteBufferCmd(const uint8_t* buffer, size_t size, uint32_t atomId);

    bool pushToQueue(const Cmd& cmd);

    void terminate();

    void processCommands();
};
