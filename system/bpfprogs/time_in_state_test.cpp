/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <bpf_timeinstate.h>
#include <gtest/gtest.h>
#include <test/mock_bpf_helpers.h>

extern "C" {

uint64_t* bpf_cpu_last_update_map_lookup_elem(uint32_t* zero);
uint64_t* bpf_uid_last_update_map_lookup_elem(uint32_t* uid);
int bpf_cpu_last_update_map_update_elem(uint32_t* zero, uint64_t* time, uint64_t flags);
int bpf_nr_active_map_update_elem(uint32_t* zero, uint32_t* time, uint64_t flags);
int bpf_cpu_policy_map_update_elem(uint32_t* zero, uint32_t* time, uint64_t flags);
int bpf_policy_freq_idx_map_update_elem(uint32_t* policy, uint8_t* index, uint64_t flags);
int bpf_policy_nr_active_map_update_elem(uint32_t* policy, uint32_t* active, uint64_t flags);
uint8_t* bpf_policy_freq_idx_map_lookup_elem(uint32_t* policy);
int bpf_policy_freq_idx_map_update_elem(uint32_t* policy, uint8_t* index, uint64_t flags);
int bpf_freq_to_idx_map_update_elem(freq_idx_key_t* freq_idx_key, uint8_t* index, uint64_t flags);
tis_val_t* bpf_uid_time_in_state_map_lookup_elem(time_key_t* key);
concurrent_val_t* bpf_uid_concurrent_times_map_lookup_elem(time_key_t* key);
int bpf_cpu_last_pid_map_update_elem(uint32_t* zero, pid_t* pid, uint64_t flags);

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

int tp_sched_switch(struct switch_args* args);

struct cpufreq_args {
    unsigned long long ignore;
    unsigned int state;
    unsigned int cpu_id;
};

int tp_cpufreq(struct cpufreq_args* args);

}  // extern "C"

static void enableTracking() {
    uint32_t zero = 0;
    bpf_nr_active_map_update_elem(&zero, &zero, BPF_ANY);
}

// Defines a CPU cluster <policy> containing CPUs <cpu_ids> with available frequencies
// <frequencies> and marks it as <active>
static void initCpuPolicy(uint32_t policy, std::vector<uint32_t> cpuIds,
                          std::vector<uint32_t> frequencies, bool active) {
    for (uint32_t cpuId : cpuIds) {
        bpf_cpu_policy_map_update_elem(&cpuId, &policy, BPF_ANY);

        mock_bpf_set_smp_processor_id(cpuId);

        // Initialize time - this must be done per-CPU
        uint32_t zero = 0;
        uint64_t time = 0;
        bpf_cpu_last_update_map_update_elem(&zero, &time, BPF_ANY);

        pid_t pid = 0;
        bpf_cpu_last_pid_map_update_elem(&zero, &pid, BPF_ANY);
    }
    for (uint8_t i = 0; i < frequencies.size(); i++) {
        uint8_t index = i + 1;  // Frequency indexes start with 1
        freq_idx_key_t freqIdxKey{.policy = policy, .freq = frequencies[i]};
        bpf_freq_to_idx_map_update_elem(&freqIdxKey, &index, BPF_ANY);
    }
    if (active) {
        uint32_t zero = 0;
        bpf_policy_nr_active_map_update_elem(&policy, &zero, BPF_ANY);
    }
}

static void noteCpuFrequencyChange(uint32_t cpuId, uint32_t frequency) {
    cpufreq_args args{.cpu_id = cpuId, .state = frequency};
    int ret = tp_cpufreq(&args);  // Tracepoint event power/cpu_frequency
    ASSERT_EQ(1, ret);
}

static void noteSchedSwitch(pid_t prevPid, pid_t nextPid) {
    switch_args args{.prev_pid = prevPid, .next_pid = nextPid};
    int ret = tp_sched_switch(&args);  // Tracepoint event sched/sched_switch
    ASSERT_EQ(1, ret);
}

static void assertTimeInState(uint32_t uid, uint32_t bucket,
                              std::vector<uint64_t> expectedTimeInState) {
    time_key_t timeKey{.uid = uid, .bucket = bucket};
    tis_val_t* value = bpf_uid_time_in_state_map_lookup_elem(&timeKey);
    ASSERT_TRUE(value);

    for (int i = 0; i < FREQS_PER_ENTRY; i++) {
        if (i < expectedTimeInState.size()) {
            ASSERT_EQ(expectedTimeInState[i], value->ar[i]);
        } else {
            ASSERT_EQ(0, value->ar[i]);
        }
    }
}

static void assertConcurrentTimes(uint32_t uid, uint32_t bucket,
                                  std::vector<uint64_t> expectedPolicy,
                                  std::vector<uint64_t> expectedActive) {
    time_key_t timeKey{.uid = uid, .bucket = bucket};
    concurrent_val_t* value = bpf_uid_concurrent_times_map_lookup_elem(&timeKey);
    ASSERT_TRUE(value);

    for (int i = 0; i < CPUS_PER_ENTRY; i++) {
        if (i < expectedPolicy.size()) {
            ASSERT_EQ(expectedPolicy[i], value->policy[i]);
        } else {
            ASSERT_EQ(0, value->policy[i]);
        }
    }

    for (int i = 0; i < CPUS_PER_ENTRY; i++) {
        if (i < expectedActive.size()) {
            ASSERT_EQ(expectedActive[i], value->active[i]);
        } else {
            ASSERT_EQ(0, value->active[i]);
        }
    }
}

static void assertUidLastUpdateTime(uint32_t uid, uint64_t expectedTime) {
    uint64_t* value = bpf_uid_last_update_map_lookup_elem(&uid);
    ASSERT_TRUE(value);
    ASSERT_EQ(expectedTime, *value);
}

TEST(time_in_state, tp_cpufreq) {
    initCpuPolicy(0, {0, 1, 2}, {1000, 2000}, true);
    initCpuPolicy(1, {3, 4}, {3000, 4000, 5000}, true);

    noteCpuFrequencyChange(1, 2000);
    {
        uint32_t policy = 0;  // CPU 1 belongs to Cluster 0
        uint8_t* freqIndex = bpf_policy_freq_idx_map_lookup_elem(&policy);
        ASSERT_TRUE(freqIndex);
        // Freq idx starts with 1. Cluster 0 is now running at the _second_ frequency
        ASSERT_EQ(2, *freqIndex);
    }

    noteCpuFrequencyChange(4, 5000);
    {
        uint32_t policy = 1;  // CPU 4 belongs to Cluster 1
        uint8_t* freqIndex = bpf_policy_freq_idx_map_lookup_elem(&policy);
        ASSERT_TRUE(freqIndex);
        // Freq idx starts with 1. Cluster 1 is now running at the _third_ frequency
        ASSERT_EQ(3, *freqIndex);
    }
}

TEST(time_in_state, tp_sched_switch) {
    mock_bpf_set_ktime_ns(1000);
    mock_bpf_set_current_uid_gid(42);

    initCpuPolicy(0, {0, 1, 2}, {1000, 2000}, true);
    initCpuPolicy(1, {3, 4}, {3000, 4000, 5000}, true);

    enableTracking();

    mock_bpf_set_smp_processor_id(2);

    // First call is ignored, because there is no "delta" to be computed
    noteSchedSwitch(0, 100);

    noteCpuFrequencyChange(2, 1000);

    mock_bpf_set_ktime_ns(1314);

    noteSchedSwitch(100, 200);

    // 1314 - 1000 = 314
    assertTimeInState(42, 0, {314, 0});
    assertConcurrentTimes(42, 0, {314, 0, 0, 0, 0}, {314, 0, 0, 0, 0});

    mock_bpf_set_current_uid_gid(51);
    mock_bpf_set_smp_processor_id(3);

    // First call on this CPU is also ignored
    noteSchedSwitch(200, 300);

    mock_bpf_set_ktime_ns(2718);

    noteCpuFrequencyChange(3, 5000);
    noteSchedSwitch(300, 400);

    mock_bpf_set_ktime_ns(5859);

    noteCpuFrequencyChange(3, 4000);
    noteSchedSwitch(400, 500);

    assertTimeInState(51, 0, {0, 5859 - 2718, 2718 - 1314});

    // (2718-1314)+(5859-2718) = 4545
    assertConcurrentTimes(51, 0, {4545, 0, 0, 0, 0}, {0, 4545, 0, 0, 0});

    assertUidLastUpdateTime(42, 1314);
    assertUidLastUpdateTime(51, 5859);
}

TEST(time_in_state, tp_sched_switch_active_cpus) {
    mock_bpf_set_ktime_ns(1000);
    mock_bpf_set_current_uid_gid(42);

    initCpuPolicy(0, {0}, {1000, 2000}, true);

    enableTracking();

    mock_bpf_set_smp_processor_id(0);

    noteSchedSwitch(0, 1);

    mock_bpf_set_ktime_ns(1100);

    noteSchedSwitch(0, 1);

    mock_bpf_set_ktime_ns(1200);

    noteSchedSwitch(1, 2);

    assertConcurrentTimes(42, 0, {100}, {100});
}

TEST(time_in_state, tp_sched_switch_sdk_sandbox) {
    mock_bpf_set_ktime_ns(1000);
    mock_bpf_set_current_uid_gid(AID_SDK_SANDBOX_PROCESS_START);

    initCpuPolicy(0, {0}, {1000, 2000}, true);

    enableTracking();

    mock_bpf_set_smp_processor_id(0);

    noteSchedSwitch(0, 1);

    mock_bpf_set_ktime_ns(1100);

    noteSchedSwitch(1, 2);

    assertTimeInState(AID_APP_START, 0, {100, 0});
    assertTimeInState(AID_SDK_SANDBOX, 0, {100, 0});

    assertConcurrentTimes(AID_APP_START, 0, {100}, {100});
    assertConcurrentTimes(AID_SDK_SANDBOX, 0, {100}, {100});
}
