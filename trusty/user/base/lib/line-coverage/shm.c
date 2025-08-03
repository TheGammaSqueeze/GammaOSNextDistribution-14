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

#define TLOG_TAG "cov-shm"

#include <lib/coverage/common/cov_shm.h>
#include <interface/line-coverage/aggregator.h>
#include <interface/line-coverage/client.h>
#include <lib/coverage/common/ipc.h>
#include <lib/line-coverage/shm.h>
#include <lib/tipc/tipc.h>
#include <lk/macros.h>
#include <lk/compiler.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_log.h>
#include <uapi/err.h>

#ifdef UNITTEST_COVERAGE
static struct cov_ctx ctx;
/*
This symbol is exported to each instrumented shared library and
exectuable to instruct the linker to skip loading the object which
contains the profiling runtime's static initialiser
*/

int __llvm_profile_runtime;

/*
These functions have to be forward declared and then called
when we want profiling runtime without static initialisers
*/
uint64_t __llvm_profile_get_size_for_buffer(void);
int __llvm_profile_write_buffer(char *Buffer);
void __llvm_profile_reset_counters(void);

void dump_shm(void) {
    if (!cov_shm_is_mapped(&ctx.data)) {
        return;
    }
    struct control *control = (struct control *)ctx.data.base;
    uint64_t flags = READ_ONCE(control->cntrl_flags);
    if ( (flags & FLAG_RUN) != 0) {
        uint64_t count =  control->write_buffer_start_count + 1;
        WRITE_ONCE(control->write_buffer_start_count, count);
        __llvm_profile_write_buffer((char *)control + sizeof(struct control));
        WRITE_ONCE(control->write_buffer_complete_count, count);
    }
}

int setup_shm(void) {
    int rc;
    handle_t memref;
    struct coverage_aggregator_req cov_req;
    struct coverage_aggregator_resp cov_resp;
    size_t shm_len;

    if(ctx.mailbox.base == NULL) {
        TLOGE("Mailbox not setup\n");
        return -1;
    }

    int* app_mailbox = (int*)(ctx.mailbox.base) + ctx.idx;
    int event = READ_ONCE(*app_mailbox);

    if (event != COVERAGE_MAILBOX_RECORD_READY) {
        TLOGE("NS memory not shared yet\n");
        return -1;
    }
    if (cov_shm_is_mapped(&ctx.data)) {
        TLOGD("SHM already setup\n");
        return NO_ERROR;
    }

    memset(&cov_req, 0, sizeof(struct coverage_aggregator_req));
    cov_req.hdr.cmd = COVERAGE_AGGREGATOR_CMD_GET_RECORD;

    rc = coverage_aggregator_rpc(ctx.coverage_srv, &cov_req, NULL, &cov_resp, &memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) coverage aggregator RPC\n", rc);
        return rc;
    }
    shm_len = cov_resp.get_record_args.shm_len;

    if (shm_len < ctx.record_len) {
        TLOGE("not enough shared memory, received: %zu, need at least: %zu\n",
            shm_len, ctx.record_len);
        rc = ERR_BAD_LEN;
        return rc;
    }

    rc = cov_shm_mmap(&ctx.data, memref, cov_resp.get_record_args.shm_len);
    if (rc != NO_ERROR) {
        TLOGE("failed to mmap() coverage record shared memory\n");
        return rc;
    }
    return NO_ERROR;
}

int setup_mailbox(const struct tipc_port* ports, uint32_t num_ports) {
    uint64_t buf_len = __llvm_profile_get_size_for_buffer();
    int rc;
    uint32_t i;
    handle_t chan;
    handle_t memref;
    struct coverage_aggregator_req req;
    struct coverage_aggregator_resp resp;

    for (i = 0; i < num_ports; i++) {
        // Skip for coverage aggregator and client
        if (strcmp(ports[i].name, LINE_COVERAGE_AGGREGATOR_PORT) == 0 ||
                strcmp(ports[i].name, LINE_COVERAGE_CLIENT_PORT) == 0) {
            ctx.mailbox.base = NULL;
            return -1;
        }
    }

    rc = tipc_connect(&chan, LINE_COVERAGE_AGGREGATOR_PORT);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to connect to coverage aggregator service\n", rc);
        return rc;
    }

    memset(&req, 0, sizeof(struct coverage_aggregator_req));
    req.hdr.cmd = COVERAGE_AGGREGATOR_CMD_REGISTER;
    req.register_args.record_len = buf_len + sizeof(struct control);

    rc = coverage_aggregator_rpc(chan, &req, NULL, &resp, &memref);
    if (rc != NO_ERROR) {
        TLOGE("sys_state: failed (%d) coverage aggregator RPC\n", rc);
        close(chan);
        return rc;
    }

    rc = cov_shm_mmap(&(ctx.mailbox), memref, resp.register_args.mailbox_len);
    if (rc != NO_ERROR) {
        TLOGE("failed to mmap() mailbox shared memory\n");
        close(memref);
        close(chan);
        return rc;
    }

    ctx.record_len = buf_len + sizeof(struct control);
    ctx.coverage_srv = chan;
    ctx.idx = resp.register_args.idx;

    close(memref);
    return NO_ERROR;
}

#else
void dump_shm(void) {
    return;
}

int setup_shm(void) {
    return NO_ERROR;
}

int setup_mailbox(const struct tipc_port* ports, uint32_t num_ports) {
    return NO_ERROR;
}
#endif
