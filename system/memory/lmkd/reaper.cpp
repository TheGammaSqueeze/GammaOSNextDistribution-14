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

#define LOG_TAG "lowmemorykiller"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <signal.h>
#include <string.h>
#include <stdlib.h>
#include <sys/epoll.h>
#include <sys/pidfd.h>
#include <sys/resource.h>
#include <sys/sysinfo.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <processgroup/processgroup.h>
#include <system/thread_defs.h>

#include "reaper.h"

#define NS_PER_MS (NS_PER_SEC / MS_PER_SEC)
#define THREAD_POOL_SIZE 2

#ifndef __NR_process_mrelease
#define __NR_process_mrelease 448
#endif

static int process_mrelease(int pidfd, unsigned int flags) {
    return syscall(__NR_process_mrelease, pidfd, flags);
}

static inline long get_time_diff_ms(struct timespec *from,
                                    struct timespec *to) {
    return (to->tv_sec - from->tv_sec) * (long)MS_PER_SEC +
           (to->tv_nsec - from->tv_nsec) / (long)NS_PER_MS;
}

static void set_process_group_and_prio(uid_t uid, int pid, const std::vector<std::string>& profiles,
                                       int prio) {
    DIR* d;
    char proc_path[PATH_MAX];
    struct dirent* de;

    if (!SetProcessProfilesCached(uid, pid, profiles)) {
        ALOGW("Failed to set task profiles for the process (%d) being killed", pid);
    }

    snprintf(proc_path, sizeof(proc_path), "/proc/%d/task", pid);
    if (!(d = opendir(proc_path))) {
        ALOGW("Failed to open %s; errno=%d: process pid(%d) might have died", proc_path, errno,
              pid);
        return;
    }

    while ((de = readdir(d))) {
        int t_pid;

        if (de->d_name[0] == '.') continue;
        t_pid = atoi(de->d_name);

        if (!t_pid) {
            ALOGW("Failed to get t_pid for '%s' of pid(%d)", de->d_name, pid);
            continue;
        }

        if (setpriority(PRIO_PROCESS, t_pid, prio) && errno != ESRCH) {
            ALOGW("Unable to raise priority of killing t_pid (%d): errno=%d", t_pid, errno);
        }
    }
    closedir(d);
}

static void* reaper_main(void* param) {
    Reaper *reaper = static_cast<Reaper*>(param);
    struct timespec start_tm, end_tm;
    struct Reaper::target_proc target;
    pid_t tid = gettid();

    // Ensure the thread does not use little cores
    if (!SetTaskProfiles(tid, {"CPUSET_SP_FOREGROUND"}, true)) {
        ALOGE("Failed to assign cpuset to the reaper thread");
    }

    if (setpriority(PRIO_PROCESS, tid, ANDROID_PRIORITY_HIGHEST)) {
        ALOGW("Unable to raise priority of the reaper thread (%d): errno=%d", tid, errno);
    }

    for (;;) {
        target = reaper->dequeue_request();

        if (reaper->debug_enabled()) {
            clock_gettime(CLOCK_MONOTONIC_COARSE, &start_tm);
        }

        if (pidfd_send_signal(target.pidfd, SIGKILL, NULL, 0)) {
            // Inform the main thread about failure to kill
            reaper->notify_kill_failure(target.pid);
            goto done;
        }

        set_process_group_and_prio(target.uid, target.pid,
                                   {"CPUSET_SP_FOREGROUND", "SCHED_SP_FOREGROUND"},
                                   ANDROID_PRIORITY_NORMAL);

        if (process_mrelease(target.pidfd, 0)) {
            ALOGE("process_mrelease %d failed: %s", target.pid, strerror(errno));
            goto done;
        }
        if (reaper->debug_enabled()) {
            clock_gettime(CLOCK_MONOTONIC_COARSE, &end_tm);
            ALOGI("Process %d was reaped in %ldms", target.pid,
                  get_time_diff_ms(&start_tm, &end_tm));
        }

done:
        close(target.pidfd);
        reaper->request_complete();
    }

    return NULL;
}

bool Reaper::is_reaping_supported() {
    static enum {
        UNKNOWN,
        SUPPORTED,
        UNSUPPORTED
    } reap_support = UNKNOWN;

    if (reap_support == UNKNOWN) {
        if (process_mrelease(-1, 0) && errno == ENOSYS) {
            reap_support = UNSUPPORTED;
        } else {
            reap_support = SUPPORTED;
        }
    }
    return reap_support == SUPPORTED;
}

bool Reaper::init(int comm_fd) {
    char name[16];
    struct sched_param param = {
        .sched_priority = 0,
    };

    if (thread_cnt_ > 0) {
        // init should not be called multiple times
        return false;
    }

    thread_pool_ = new pthread_t[THREAD_POOL_SIZE];
    for (int i = 0; i < THREAD_POOL_SIZE; i++) {
        if (pthread_create(&thread_pool_[thread_cnt_], NULL, reaper_main, this)) {
            ALOGE("pthread_create failed: %s", strerror(errno));
            continue;
        }
        // set normal scheduling policy for the reaper thread
        if (pthread_setschedparam(thread_pool_[thread_cnt_], SCHED_OTHER, &param)) {
            ALOGW("set SCHED_FIFO failed %s", strerror(errno));
        }
        snprintf(name, sizeof(name), "lmkd_reaper%d", thread_cnt_);
        if (pthread_setname_np(thread_pool_[thread_cnt_], name)) {
            ALOGW("pthread_setname_np failed: %s", strerror(errno));
        }
        thread_cnt_++;
    }

    if (!thread_cnt_) {
        delete[] thread_pool_;
        return false;
    }

    queue_.reserve(thread_cnt_);
    comm_fd_ = comm_fd;
    return true;
}

bool Reaper::async_kill(const struct target_proc& target) {
    if (target.pidfd == -1) {
        return false;
    }

    if (!thread_cnt_) {
        return false;
    }

    mutex_.lock();
    if (active_requests_ >= thread_cnt_) {
        mutex_.unlock();
        return false;
    }
    active_requests_++;

    // Duplicate pidfd instead of reusing the original one to avoid synchronization and refcounting
    // when both reaper and main threads are using or closing the pidfd
    queue_.push_back({ dup(target.pidfd), target.pid, target.uid });
    // Wake up a reaper thread
    cond_.notify_one();
    mutex_.unlock();

    return true;
}

int Reaper::kill(const struct target_proc& target, bool synchronous) {
    /* CAP_KILL required */
    if (target.pidfd < 0) {
        return ::kill(target.pid, SIGKILL);
    }

    if (!synchronous && async_kill(target)) {
        // we assume the kill will be successful and if it fails we will be notified
        return 0;
    }

    int result = pidfd_send_signal(target.pidfd, SIGKILL, NULL, 0);
    if (result) {
        return result;
    }

    return 0;
}

Reaper::target_proc Reaper::dequeue_request() {
    struct target_proc target;
    std::unique_lock<std::mutex> lock(mutex_);

    while (queue_.empty()) {
        cond_.wait(lock);
    }
    target = queue_.back();
    queue_.pop_back();

    return target;
}

void Reaper::request_complete() {
    std::scoped_lock<std::mutex> lock(mutex_);
    active_requests_--;
}

void Reaper::notify_kill_failure(int pid) {
    std::scoped_lock<std::mutex> lock(mutex_);

    ALOGE("Failed to kill process %d", pid);
    if (TEMP_FAILURE_RETRY(write(comm_fd_, &pid, sizeof(pid))) != sizeof(pid)) {
        ALOGE("thread communication write failed: %s", strerror(errno));
    }
}
