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
 */

#include <inttypes.h>
#include <sys/types.h>

#define BPF_FS_PATH "/sys/fs/bpf/"

// Number of frequencies tracked in the array with total time. If some CPUs have
// more than 64 freqs
// // available, the overflow is stored in the last entry.
#define MAX_FREQS_FOR_TOTAL 64
// Number of frequencies for which a UID's times can be tracked in a single map entry. If some CPUs
// have more than 32 freqs available, a single UID is tracked using 2 or more entries.
#define FREQS_PER_ENTRY 32
// Number of distinct CPU counts for which a UID's concurrent time stats can be tracked in a single
// map entry. On systems with more than 8 CPUs, a single UID is tracked using 2 or more entries.
#define CPUS_PER_ENTRY 8

typedef struct {
    uint32_t uid;
    uint32_t bucket;
} time_key_t;

typedef struct {
    uint64_t ar[FREQS_PER_ENTRY];
} tis_val_t;

typedef struct {
    uint64_t active[CPUS_PER_ENTRY];
    uint64_t policy[CPUS_PER_ENTRY];
} concurrent_val_t;

typedef struct {
    uint32_t policy;
    uint32_t freq;
} freq_idx_key_t;

// Maximum number of processes whose thread CPU time-in-state can be tracked simultaneously.
#define MAX_TRACKED_PIDS 8

// Indicates that the pid_tracked_map item is unused and further items in the array are also
// unused
#define TRACKED_PID_STATE_UNUSED 0
// Indicates that the pid_tracked_map item contains a PID that is currently tracked
#define TRACKED_PID_STATE_ACTIVE 1
// Indicates that the pid_tracked_map item is vacant, but further items in the array may
// contain tracked PIDs
#define TRACKED_PID_STATE_EXITED 2

typedef struct {
    pid_t pid;
    // TRACKED_PID_STATE_UNUSED, TRACKED_PID_STATE_ACTIVE or TRACKED_PID_STATE_EXITED
    uint8_t state;
} tracked_pid_t;

typedef struct {
    pid_t tgid;
    uint16_t aggregation_key;
    uint16_t bucket;
} aggregated_task_tis_key_t;
