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

#define LOG_TAG "audio_utils::threads"

#include <audio_utils/threads.h>

#include <algorithm>  // std::clamp
#include <errno.h>
#include <sched.h>    // scheduler
#include <sys/resource.h>
#include <utils/Errors.h>  // status_t
#include <utils/Log.h>

namespace android::audio_utils {

/**
 * Sets the unified priority of the tid.
 */
status_t set_thread_priority(pid_t tid, int priority) {
    if (is_realtime_priority(priority)) {
        // audio processes are designed to work with FIFO, not RR.
        constexpr int new_policy = SCHED_FIFO;
        const int rtprio = unified_priority_to_rtprio(priority);
        struct sched_param param {
            .sched_priority = rtprio,
        };
        if (sched_setscheduler(tid, new_policy, &param) != 0) {
            ALOGW("%s: Cannot set FIFO priority for tid %d to policy %d rtprio %d  %s",
                    __func__, tid, new_policy, rtprio, strerror(errno));
            return -errno;
        }
        return NO_ERROR;
    } else if (is_cfs_priority(priority)) {
        const int policy = sched_getscheduler(tid);
        const int nice = unified_priority_to_nice(priority);
        if (policy != SCHED_OTHER) {
            struct sched_param param{};
            constexpr int new_policy = SCHED_OTHER;
            if (sched_setscheduler(tid, new_policy, &param) != 0) {
                ALOGW("%s: Cannot set CFS priority for tid %d to policy %d nice %d  %s",
                        __func__, tid, new_policy, nice, strerror(errno));
                return -errno;
            }
        }
        if (setpriority(PRIO_PROCESS, tid, nice) != 0) return -errno;
        return NO_ERROR;
    } else {
        return BAD_VALUE;
    }
}

/**
 * Returns the unified priority of the tid.
 *
 * A negative number represents error.
 */
int get_thread_priority(int tid) {
    const int policy = sched_getscheduler(tid);
    if (policy < 0) return -errno;

    if (policy == SCHED_OTHER) {
        errno = 0;  // negative return value valid, so check errno change.
        const int nice = getpriority(PRIO_PROCESS, tid);
        if (errno != 0) return -errno;
        return nice_to_unified_priority(nice);
    } else if (policy == SCHED_FIFO || policy == SCHED_RR) {
        struct sched_param param{};
        if (sched_getparam(tid, &param) < 0) return -errno;
        return rtprio_to_unified_priority(param.sched_priority);
    } else {
        return INVALID_OPERATION;
    }
}

} // namespace android::audio_utils
