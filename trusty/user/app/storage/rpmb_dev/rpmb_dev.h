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

#pragma once

#include "../rpmb.h"
#include "../rpmb_protocol.h"

#define MAX_WRITE_COUNTER (0xffffffff)

struct rpmb_data_header {
    uint32_t write_counter;
    uint16_t max_block;
    uint8_t pad1;
    uint8_t key_programmed;
    struct rpmb_key key;
    uint8_t pad[512 - 4 - 2 - 1 - 1 - sizeof(struct rpmb_key)];
};

#define MAX_PACKET_COUNT (8)

struct rpmb_dev_state {
    struct rpmb_data_header header;
    struct rpmb_packet cmd[MAX_PACKET_COUNT];
    struct rpmb_packet res[MAX_PACKET_COUNT];
    uint16_t cmd_count;
    uint16_t res_count;
    int data_fd;
    int fail_next_writes;
    bool commit_failed_writes;
    int fail_next_reads;
    int fail_next_get_counters;
};

void rpmb_dev_process_cmd(struct rpmb_dev_state* s);

/* verbose is an int for getopt */
extern int verbose;
