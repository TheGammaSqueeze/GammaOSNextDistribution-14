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

#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <stddef.h>

__BEGIN_CDECLS

#define FLAG_NONE               0x0
#define FLAG_RUN                0x1
#define FLAG_TOGGLE_CLEAR       0x2

typedef uint8_t counter_t;

struct control {
    /* Written by controller, read by instrumented TA */
    uint64_t        cntrl_flags;

    /* Written by instrumented TA, read by controller */
    uint64_t        oper_flags;
    uint64_t        write_buffer_start_count;
    uint64_t        write_buffer_complete_count;
};

struct cov_ctx {
    handle_t coverage_srv;
    size_t idx;
    struct cov_shm mailbox;
    struct cov_shm data;
    size_t record_len;
};

/**
 * setup_mailbox() - Mailbox setup with the help of coverage aggregator
 * @ports: List of ports for which mailbox has to be setup
 * @num_ports: Number of ports
 */
int setup_mailbox(const struct tipc_port* ports, uint32_t num_ports);

/**
 * setup_shm() - SHM from NS is passed onto the TA via coverage aggregator
 */
int setup_shm(void);

/*
 * dump_shm() - Coverage information is dumped into the shared memory after
                looking at the control flags
*/
void dump_shm(void);
__END_CDECLS
