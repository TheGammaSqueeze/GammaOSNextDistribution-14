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
 *
 */

#pragma once

#include <string>
#include <vector>

#include <netdutils/DumpWriter.h>

#include "LockedQueue.h"

namespace android::net {

// This class stores query records in a locked ring buffer. It's thread-safe for concurrent access.
class DnsQueryLog {
  public:
    static constexpr std::string_view DUMP_KEYWORD = "querylog";

    struct Record {
        Record(uint32_t netId, uid_t uid, pid_t pid, const std::string& hostname,
               const std::vector<std::string>& addrs, int timeTaken)
            : netId(netId),
              uid(uid),
              pid(pid),
              hostname(hostname),
              addrs(addrs),
              timeTaken(timeTaken) {}
        const uint32_t netId;
        const uid_t uid;
        const pid_t pid;
        const std::chrono::system_clock::time_point timestamp = std::chrono::system_clock::now();
        const std::string hostname;
        const std::vector<std::string> addrs;
        const int timeTaken;
    };

    DnsQueryLog() : DnsQueryLog(getLogSizeFromSysProp()) {}

    // Allow the tests to set the capacity.
    DnsQueryLog(size_t size) : mQueue(size) {}

    void push(Record&& record);
    void dump(netdutils::DumpWriter& dw) const;

  private:
    LockedRingBuffer<Record> mQueue;

    // The capacity of the circular buffer.
    static constexpr size_t kDefaultLogSize = 200;
    // The upper bound of the circular buffer.
    static constexpr size_t kMaxLogSize = 10000;

    uint64_t getLogSizeFromSysProp();
};

}  // namespace android::net
