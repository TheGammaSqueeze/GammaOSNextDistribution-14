/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef MEM_EVENTS_BPF_TYES_H_
#define MEM_EVENTS_BPF_TYES_H_

#include <inttypes.h>

#define MEM_EVENT_PROC_NAME_LEN 16  // linux/sched.h
#define MEM_EVENTS_RINGBUF_SIZE 4096

typedef unsigned int mem_event_type_t;
/* Supported mem_event_type_t */
#define MEM_EVENT_OOM_KILL 0
#define MEM_EVENT_BASE MEM_EVENT_OOM_KILL
#define MEM_EVENT_DIRECT_RECLAIM_BEGIN 1
#define MEM_EVENT_DIRECT_RECLAIM_END 2

// This always comes after the last valid event type
#define NR_MEM_EVENTS 3

/* BPF-Rb Paths */
#define MEM_EVENTS_AMS_RB "/sys/fs/bpf/map_bpfMemEvents_ams_rb"
#define MEM_EVENTS_LMKD_RB "/sys/fs/bpf/map_bpfMemEvents_lmkd_rb"
#define MEM_EVENTS_TEST_RB "/sys/fs/bpf/map_bpfMemEventsTest_rb"

/* BPF-Prog Paths */
#define MEM_EVENTS_AMS_OOM_MARK_VICTIM_TP \
    "/sys/fs/bpf/prog_bpfMemEvents_tracepoint_oom_mark_victim_ams"
#define MEM_EVENTS_LMKD_VMSCAN_DR_BEGIN_TP \
    "/sys/fs/bpf/prog_bpfMemEvents_tracepoint_vmscan_mm_vmscan_direct_reclaim_begin_lmkd"
#define MEM_EVENTS_LMKD_VMSCAN_DR_END_TP \
    "/sys/fs/bpf/prog_bpfMemEvents_tracepoint_vmscan_mm_vmscan_direct_reclaim_end_lmkd"
#define MEM_EVENTS_TEST_OOM_MARK_VICTIM_TP \
    "/sys/fs/bpf/prog_bpfMemEventsTest_tracepoint_oom_mark_victim"

/* Struct to collect data from tracepoints */
struct mem_event_t {
    uint64_t type;

    union EventData {
        struct OomKill {
            uint32_t pid;
            uint64_t timestamp_ms;
            uint64_t oom_score_adj;
            uint32_t uid;
            char process_name[MEM_EVENT_PROC_NAME_LEN];
        } oom_kill;
    } event_data;
};

/* Expected args for tracepoints */

struct mark_victim_args {
    uint64_t __ignore;
    /* Actual fields start at offset 8 */
    uint32_t pid;
    uint32_t uid;
    uint32_t __data_loc_comm;
    short oom_score_adj;
};

struct direct_reclaim_begin_args {
    char __ignore[24];
};

struct direct_reclaim_end_args {
    char __ignore[16];
};

#endif /* MEM_EVENTS_BPF_TYES_H_ */
