/*
 * timeInState eBPF program
 *
 * Copyright (C) 2018 Google
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#ifdef MOCK_BPF
#include <test/mock_bpf_helpers.h>
#else
#include <bpf_helpers.h>
#endif

#include <bpf_timeinstate.h>

DEFINE_BPF_MAP_GRW(total_time_in_state_map, PERCPU_ARRAY, uint32_t, uint64_t, MAX_FREQS_FOR_TOTAL,
                   AID_SYSTEM)

DEFINE_BPF_MAP_GRW(uid_time_in_state_map, PERCPU_HASH, time_key_t, tis_val_t, 1024, AID_SYSTEM)

DEFINE_BPF_MAP_GRW(uid_concurrent_times_map, PERCPU_HASH, time_key_t, concurrent_val_t, 1024,
                   AID_SYSTEM)
DEFINE_BPF_MAP_GRW(uid_last_update_map, HASH, uint32_t, uint64_t, 1024, AID_SYSTEM)

DEFINE_BPF_MAP_GWO(cpu_last_update_map, PERCPU_ARRAY, uint32_t, uint64_t, 1, AID_SYSTEM)
DEFINE_BPF_MAP_GWO(cpu_last_pid_map, PERCPU_ARRAY, uint32_t, pid_t, 1, AID_SYSTEM)

DEFINE_BPF_MAP_GWO(cpu_policy_map, ARRAY, uint32_t, uint32_t, 1024, AID_SYSTEM)
DEFINE_BPF_MAP_GWO(policy_freq_idx_map, ARRAY, uint32_t, uint8_t, 1024, AID_SYSTEM)

DEFINE_BPF_MAP_GWO(freq_to_idx_map, HASH, freq_idx_key_t, uint8_t, 2048, AID_SYSTEM)

DEFINE_BPF_MAP_GWO(nr_active_map, ARRAY, uint32_t, uint32_t, 1, AID_SYSTEM)
DEFINE_BPF_MAP_GWO(policy_nr_active_map, ARRAY, uint32_t, uint32_t, 1024, AID_SYSTEM)

DEFINE_BPF_MAP_GWO(pid_tracked_hash_map, HASH, uint32_t, pid_t, MAX_TRACKED_PIDS, AID_SYSTEM)
DEFINE_BPF_MAP_GWO(pid_tracked_map, ARRAY, uint32_t, tracked_pid_t, MAX_TRACKED_PIDS, AID_SYSTEM)
DEFINE_BPF_MAP_GWO(pid_task_aggregation_map, HASH, pid_t, uint16_t, 1024, AID_SYSTEM)
DEFINE_BPF_MAP_GRO(pid_time_in_state_map, PERCPU_HASH, aggregated_task_tis_key_t, tis_val_t, 1024,
                   AID_SYSTEM)

struct switch_args {
    unsigned long long ignore;
    char prev_comm[16];
    int prev_pid;
    int prev_prio;
    long long prev_state;
    char next_comm[16];
    int next_pid;
    int next_prio;
};

static inline __always_inline void update_uid(uint32_t uid, uint64_t delta, uint64_t time,
                                              uint8_t freq_idx, uint32_t active,
                                              uint32_t policy_active) {
    time_key_t key = {.uid = uid, .bucket = freq_idx / FREQS_PER_ENTRY};
    tis_val_t* val = bpf_uid_time_in_state_map_lookup_elem(&key);
    if (!val) {
        tis_val_t zero_val = {.ar = {0}};
        bpf_uid_time_in_state_map_update_elem(&key, &zero_val, BPF_NOEXIST);
        val = bpf_uid_time_in_state_map_lookup_elem(&key);
    }
    if (val) val->ar[freq_idx % FREQS_PER_ENTRY] += delta;

    key.bucket = active / CPUS_PER_ENTRY;
    concurrent_val_t* ct = bpf_uid_concurrent_times_map_lookup_elem(&key);
    if (!ct) {
        concurrent_val_t zero_val = {.active = {0}, .policy = {0}};
        bpf_uid_concurrent_times_map_update_elem(&key, &zero_val, BPF_NOEXIST);
        ct = bpf_uid_concurrent_times_map_lookup_elem(&key);
    }
    if (ct) ct->active[active % CPUS_PER_ENTRY] += delta;

    if (policy_active / CPUS_PER_ENTRY != key.bucket) {
        key.bucket = policy_active / CPUS_PER_ENTRY;
        ct = bpf_uid_concurrent_times_map_lookup_elem(&key);
        if (!ct) {
            concurrent_val_t zero_val = {.active = {0}, .policy = {0}};
            bpf_uid_concurrent_times_map_update_elem(&key, &zero_val, BPF_NOEXIST);
            ct = bpf_uid_concurrent_times_map_lookup_elem(&key);
        }
    }
    if (ct) ct->policy[policy_active % CPUS_PER_ENTRY] += delta;
    uint64_t* uid_last_update = bpf_uid_last_update_map_lookup_elem(&uid);
    if (uid_last_update) {
        *uid_last_update = time;
    } else {
        bpf_uid_last_update_map_update_elem(&uid, &time, BPF_NOEXIST);
    }
    return;
}

DEFINE_BPF_PROG("tracepoint/sched/sched_switch", AID_ROOT, AID_SYSTEM, tp_sched_switch)
(struct switch_args* args) {
    const int ALLOW = 1;  // return 1 to avoid blocking simpleperf from receiving events.
    uint32_t zero = 0;
    uint64_t* last = bpf_cpu_last_update_map_lookup_elem(&zero);
    if (!last) return ALLOW;
    uint64_t old_last = *last;
    uint64_t time = bpf_ktime_get_ns();
    *last = time;

    // With suspend-to-ram, it's possible to see prev_pid==0 twice in a row on the same CPU. Add a
    // check to ensure prev_pid matches the previous next_pid to avoid incorrectly incrementing our
    // active CPU counts a second time in this scenario.
    pid_t *cpu_pidp = bpf_cpu_last_pid_map_lookup_elem(&zero);
    if (!cpu_pidp) return ALLOW;
    pid_t cpu_pid = *cpu_pidp;
    *cpu_pidp = args->next_pid;
    if (old_last && args->prev_pid != cpu_pid) return ALLOW;

    uint32_t* active = bpf_nr_active_map_lookup_elem(&zero);
    if (!active) return ALLOW;

    uint32_t cpu = bpf_get_smp_processor_id();
    uint32_t* policyp = bpf_cpu_policy_map_lookup_elem(&cpu);
    if (!policyp) return ALLOW;
    uint32_t policy = *policyp;

    uint32_t* policy_active = bpf_policy_nr_active_map_lookup_elem(&policy);
    if (!policy_active) return ALLOW;

    uint32_t nactive = *active - 1;
    uint32_t policy_nactive = *policy_active - 1;

    if (!args->prev_pid || (!old_last && args->next_pid)) {
        __sync_fetch_and_add(active, 1);
        __sync_fetch_and_add(policy_active, 1);
    }

    // Return here in 2 scenarios:
    // 1) prev_pid == 0, so we're exiting idle. No UID stats need updating, and active CPUs can't be
    //    decreasing.
    // 2) old_last == 0, so this is the first time we've seen this CPU. Any delta will be invalid,
    //    and our active CPU counts don't include this CPU yet so we shouldn't decrement them even
    //    if we're going idle.
    if (!args->prev_pid || !old_last) return ALLOW;

    if (!args->next_pid) {
        __sync_fetch_and_add(active, -1);
        __sync_fetch_and_add(policy_active, -1);
    }

    uint8_t* freq_idxp = bpf_policy_freq_idx_map_lookup_elem(&policy);
    if (!freq_idxp || !*freq_idxp) return ALLOW;
    // freq_to_idx_map uses 1 as its minimum index so that *freq_idxp == 0 only when uninitialized
    uint8_t freq_idx = *freq_idxp - 1;

    uint32_t uid = bpf_get_current_uid_gid();
    uint64_t delta = time - old_last;

    // For UIDs in the SDK sandbox range, we account per-UID times twice, both to the corresponding
    // app uid and to the "virtual" UID AID_SDK_SANDBOX which is reserved for collecting total times
    // across all SDK sandbox UIDs. Special handling for this reserved UID in framework code
    // prevents double counting in systemwide totals.
    if (((uid % AID_USER_OFFSET) >= AID_SDK_SANDBOX_PROCESS_START) &&
        ((uid % AID_USER_OFFSET) <= AID_SDK_SANDBOX_PROCESS_END)) {
        uid -= AID_SDK_SANDBOX_PROCESS_START - AID_APP_START;
        update_uid(uid, delta, time, freq_idx, nactive, policy_nactive);
        update_uid(AID_SDK_SANDBOX, delta, time, freq_idx, nactive, policy_nactive);
    } else {
        update_uid(uid, delta, time, freq_idx, nactive, policy_nactive);
    }

    // Add delta to total.
    const uint32_t total_freq_idx = freq_idx < MAX_FREQS_FOR_TOTAL ? freq_idx :
                                    MAX_FREQS_FOR_TOTAL - 1;
    uint64_t* total = bpf_total_time_in_state_map_lookup_elem(&total_freq_idx);
    if (total) *total += delta;

    const int pid = args->prev_pid;
    const pid_t tgid = bpf_get_current_pid_tgid() >> 32;
    bool is_tgid_tracked = false;

    // eBPF verifier does not currently allow loops.
    // Instruct the C compiler to unroll the loop into a series of steps.
    #pragma unroll
    for (uint32_t index = 0; index < MAX_TRACKED_PIDS; index++) {
        const uint32_t key = index;
        tracked_pid_t* tracked_pid = bpf_pid_tracked_map_lookup_elem(&key);
        if (!tracked_pid) continue;
        if (tracked_pid->state == TRACKED_PID_STATE_UNUSED) {
            // Reached the end of the list
            break;
        }

        if (tracked_pid->state == TRACKED_PID_STATE_ACTIVE && tracked_pid->pid == tgid) {
            is_tgid_tracked = true;
            break;
        }
    }

    if (is_tgid_tracked) {
        // If this process is marked for time-in-state tracking, aggregate the CPU time-in-state
        // with other threads sharing the same TGID and aggregation key.
        uint16_t* aggregation_key = bpf_pid_task_aggregation_map_lookup_elem(&pid);
        aggregated_task_tis_key_t task_key = {
                .tgid = tgid,
                .aggregation_key = aggregation_key ? *aggregation_key : 0,
                .bucket = freq_idx / FREQS_PER_ENTRY};
        tis_val_t* task_val = bpf_pid_time_in_state_map_lookup_elem(&task_key);
        if (!task_val) {
            tis_val_t zero_val = {.ar = {0}};
            bpf_pid_time_in_state_map_update_elem(&task_key, &zero_val, BPF_NOEXIST);
            task_val = bpf_pid_time_in_state_map_lookup_elem(&task_key);
        }
        if (task_val) task_val->ar[freq_idx % FREQS_PER_ENTRY] += delta;
    }
    return ALLOW;
}

struct cpufreq_args {
    unsigned long long ignore;
    unsigned int state;
    unsigned int cpu_id;
};

DEFINE_BPF_PROG("tracepoint/power/cpu_frequency", AID_ROOT, AID_SYSTEM, tp_cpufreq)
(struct cpufreq_args* args) {
    const int ALLOW = 1;  // return 1 to avoid blocking simpleperf from receiving events.
    uint32_t cpu = args->cpu_id;
    unsigned int new = args->state;
    uint32_t* policyp = bpf_cpu_policy_map_lookup_elem(&cpu);
    if (!policyp) return ALLOW;
    uint32_t policy = *policyp;
    freq_idx_key_t key = {.policy = policy, .freq = new};
    uint8_t* idxp = bpf_freq_to_idx_map_lookup_elem(&key);
    if (!idxp) return ALLOW;
    uint8_t idx = *idxp;
    bpf_policy_freq_idx_map_update_elem(&policy, &idx, BPF_ANY);
    return ALLOW;
}

// The format of the sched/sched_process_free event is described in
// adb shell cat /d/tracing/events/sched/sched_process_free/format
struct sched_process_free_args {
    unsigned long long ignore;
    char comm[16];
    pid_t pid;
    int prio;
};

DEFINE_BPF_PROG("tracepoint/sched/sched_process_free", AID_ROOT, AID_SYSTEM, tp_sched_process_free)
(struct sched_process_free_args* args) {
    const int ALLOW = 1;

    int pid = args->pid;
    bool is_last = true;

    // eBPF verifier does not currently allow loops.
    // Instruct the C compiler to unroll the loop into a series of steps.
    #pragma unroll
    for (uint32_t index = 0; index < MAX_TRACKED_PIDS; index++) {
        const uint32_t key = MAX_TRACKED_PIDS - index - 1;
        tracked_pid_t* tracked_pid = bpf_pid_tracked_map_lookup_elem(&key);
        if (!tracked_pid) continue;
        if (tracked_pid->pid == pid) {
            tracked_pid->pid = 0;
            tracked_pid->state = is_last ? TRACKED_PID_STATE_UNUSED : TRACKED_PID_STATE_EXITED;
            bpf_pid_tracked_hash_map_delete_elem(&key);
            break;
        }
        if (tracked_pid->state == TRACKED_PID_STATE_ACTIVE) {
            is_last = false;
        }
    }

    bpf_pid_task_aggregation_map_delete_elem(&pid);
    return ALLOW;
}

LICENSE("GPL");
