/*
 * MM Events - eBPF programs
 *
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <string.h>

#include <linux/bpf_perf_event.h>

#include <memevents/bpf_helpers.h>
#include <memevents/bpf_types.h>

DEFINE_BPF_RINGBUF_EXT(ams_rb, struct mem_event_t, MEM_EVENTS_RINGBUF_SIZE, DEFAULT_BPF_MAP_UID,
                       AID_SYSTEM, 0660, DEFAULT_BPF_MAP_SELINUX_CONTEXT,
                       DEFAULT_BPF_MAP_PIN_SUBDIR, PRIVATE, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER,
                       LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

DEFINE_BPF_RINGBUF_EXT(lmkd_rb, struct mem_event_t, MEM_EVENTS_RINGBUF_SIZE, DEFAULT_BPF_MAP_UID,
                       AID_SYSTEM, 0660, DEFAULT_BPF_MAP_SELINUX_CONTEXT,
                       DEFAULT_BPF_MAP_PIN_SUBDIR, PRIVATE, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER,
                       LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG)

DEFINE_BPF_PROG("tracepoint/oom/mark_victim/ams", AID_ROOT, AID_SYSTEM, tp_ams)
(struct mark_victim_args* args) {
    unsigned long long timestamp_ns = bpf_ktime_get_ns();
    struct mem_event_t* data = bpf_ams_rb_reserve();
    if (data == NULL) return 1;

    data->type = MEM_EVENT_OOM_KILL;
    data->event_data.oom_kill.pid = args->pid;
    data->event_data.oom_kill.oom_score_adj = args->oom_score_adj;
    data->event_data.oom_kill.uid = args->uid;
    data->event_data.oom_kill.timestamp_ms = timestamp_ns / 1000000;  // Convert to milliseconds

    read_str((char*)args, args->__data_loc_comm, data->event_data.oom_kill.process_name,
             MEM_EVENT_PROC_NAME_LEN);

    bpf_ams_rb_submit(data);

    return 0;
}

DEFINE_BPF_PROG("tracepoint/vmscan/mm_vmscan_direct_reclaim_begin/lmkd", AID_ROOT, AID_SYSTEM,
                tp_lmkd_dr_start)
(struct direct_reclaim_begin_args* args) {
    struct mem_event_t* data = bpf_lmkd_rb_reserve();
    if (data == NULL) return 1;

    data->type = MEM_EVENT_DIRECT_RECLAIM_BEGIN;

    bpf_lmkd_rb_submit(data);

    return 0;
}

DEFINE_BPF_PROG("tracepoint/vmscan/mm_vmscan_direct_reclaim_end/lmkd", AID_ROOT, AID_SYSTEM,
                tp_lmkd_dr_end)
(struct direct_reclaim_end_args* args) {
    struct mem_event_t* data = bpf_lmkd_rb_reserve();
    if (data == NULL) return 1;

    data->type = MEM_EVENT_DIRECT_RECLAIM_END;

    bpf_lmkd_rb_submit(data);

    return 0;
}

// bpf_probe_read_str is GPL only symbol
LICENSE("GPL");
