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

#ifndef MEM_EVENTS_TEST_H_
#define MEM_EVENTS_TEST_H_

#include <inttypes.h>

#include <memevents/bpf_types.h>

/* BPF-Prog Paths */
#define MEM_EVENTS_TEST_OOM_KILL_TP "/sys/fs/bpf/prog_bpfMemEventsTest_skfilter_oom_kill"
#define MEM_EVENTS_TEST_DIRECT_RECLAIM_START_TP \
    "/sys/fs/bpf/prog_bpfMemEventsTest_skfilter_direct_reclaim_begin"
#define MEM_EVENTS_TEST_DIRECT_RECLAIM_END_TP \
    "/sys/fs/bpf/prog_bpfMemEventsTest_skfilter_direct_reclaim_end"

// clang-format off
const struct mem_event_t mocked_oom_event = {
     .type = MEM_EVENT_OOM_KILL,
     .event_data.oom_kill = {
        .pid = 1234,
        .uid = 4321,
        .process_name = "fake_process",
        .timestamp_ms = 1,
        .oom_score_adj = 999,
}};
// clang-format on

#endif /* MEM_EVENTS_TEST_H_ */