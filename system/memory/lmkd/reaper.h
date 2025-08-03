/*
 *  Copyright 2021 Google, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#pragma once

#include <condition_variable>
#include <mutex>
#include <vector>

class Reaper {
public:
    struct target_proc {
        int pidfd;
        int pid;
        uid_t uid;
    };
private:
    // mutex_ and cond_ are used to wakeup the reaper thread.
    std::mutex mutex_;
    std::condition_variable cond_;
    // mutex_ protects queue_ and active_requests_ access.
    std::vector<struct target_proc> queue_;
    int active_requests_;
    // write side of the pipe to communicate kill failures with the main thread
    int comm_fd_;
    int thread_cnt_;
    pthread_t* thread_pool_;
    bool debug_enabled_;

    bool async_kill(const struct target_proc& target);
public:
    Reaper() : active_requests_(0), thread_cnt_(0), debug_enabled_(false) {}

    static bool is_reaping_supported();

    bool init(int comm_fd);
    int thread_cnt() const { return thread_cnt_; }
    void enable_debug(bool enable) { debug_enabled_ = enable; }
    bool debug_enabled() const { return debug_enabled_; }

    // return 0 on success or error code returned by the syscall
    int kill(const struct target_proc& target, bool synchronous);
    // below members are used only by reaper_main
    target_proc dequeue_request();
    void request_complete();
    void notify_kill_failure(int pid);
};
