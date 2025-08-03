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

#include <algorithm>
#include <sys/syscall.h>   // SYS_gettid
#include <unistd.h>        // bionic gettid
#include <utils/Errors.h>  // status_t

namespace android::audio_utils {

/*
 * Some basic priority definitions from linux,
 * see MACROs in common/include/linux/sched/prio.h.
 *
 * On device limits may be found with the following command $ adb shell chrt -m
 */
inline constexpr int kMaxNice = 19;  // inclusive
inline constexpr int kMinNice = -20; // inclusive
inline constexpr int kNiceWidth = (kMaxNice - kMinNice + 1);
inline constexpr int kMinRtPrio = 1; // inclusive
inline constexpr int kMaxRtPrio = 100;                    // [sic] exclusive
inline constexpr int kMaxPrio = kMaxRtPrio + kNiceWidth;  // [sic] exclusive
inline constexpr int kDefaultPrio = kMaxRtPrio + kNiceWidth / 2;

/*
 * The following conversion routines follow the linux equivalent.
 * Here we use the term "unified priority" as the linux normal_prio.
 *
 * See common/kernel/sched/core.c __normal_prio().
 */

/**
 * Convert CFS (SCHED_OTHER) nice to unified priority.
 */
inline int nice_to_unified_priority(int nice) {
    return kDefaultPrio + nice;
}

/**
 * Convert unified priority to CFS (SCHED_OTHER) nice.
 *
 * Some unified priorities are not CFS, they will be clamped in range.
 * Use is_cfs_priority() to check if a CFS priority.
 */
inline int unified_priority_to_nice(int priority) {
    return std::clamp(priority - kDefaultPrio, kMinNice, kMaxNice);
}

/**
 * Convert SCHED_FIFO/SCHED_RR rtprio 1 - 99 to unified priority 98 to 0.
 */
inline int rtprio_to_unified_priority(int rtprio) {
    return kMaxRtPrio - 1 - rtprio;
}

/**
 * Convert unified priority 0 to 98 to SCHED_FIFO/SCHED_RR rtprio 99 to 1.
 *
 * Some unified priorities are not real time, they will be clamped in range.
 * Use is_realtime_priority() to check if real time priority.
 */
inline int unified_priority_to_rtprio(int priority) {
    return std::clamp(kMaxRtPrio - 1 - priority, kMinRtPrio, kMaxRtPrio - 1);
}

/**
 * Returns whether the unified priority is realtime.
 */
inline bool is_realtime_priority(int priority) {
    return priority >= 0 && priority < kMaxRtPrio;  // note this allows the unified value 99.
}

/**
 * Returns whether the unified priority is CFS.
 */
inline bool is_cfs_priority(int priority) {
    return priority >= kMaxRtPrio && priority < kMaxPrio;
}

/**
 * Returns the linux thread id.
 *
 * We use gettid() which is available on bionic libc and modern glibc;
 * for other libc, we syscall directly.
 *
 * This is not the same as std::thread::get_id() which returns pthread_self().
 */
pid_t inline gettid_wrapper() {
#if defined(__BIONIC__)
    return ::gettid();
#else
    return syscall(SYS_gettid);
#endif
}

/*
 * set_thread_priority() and get_thread_priority()
 * both use the unified scheduler priority, where a lower value represents
 * increasing priority.
 *
 * The linux kernel unified scheduler priority values are as follows:
 * 0 - 98    (A real time priority rtprio between 99 and 1)
 * 100 - 139 (A Completely Fair Scheduler niceness between -20 and 19)
 *
 * Real time schedulers (SCHED_FIFO and SCHED_RR) have rtprio between 1 and 99,
 * where 1 is the lowest and 99 is the highest.
 *
 * The Completely Fair Scheduler (also known as SCHED_OTHER) has a
 * nice value between 19 and -20, where 19 is the lowest and -20 the highest.
 *
 * Note: the unified priority is reported on /proc/<tid>/stat file as "prio".
 *
 * See common/kernel/sched/debug.c proc_sched_show_task().
 */

/**
 * Sets the priority of tid to a unified priority.
 *
 * The range of priority is 0 through 139, inclusive.
 * A priority value of 99 is changed to 98.
 */
status_t set_thread_priority(pid_t tid, int priority);

/**
 * Returns the unified priority of the tid.
 *
 * A negative number represents error.
 */
int get_thread_priority(int tid);

} // namespace android::audio_utils
