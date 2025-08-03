/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define TLOG_TAG "sancov-rt"

#include <assert.h>
#include <interface/coverage/aggregator.h>
#include <lib/coverage/common/ipc.h>
#include <lib/coverage/common/record.h>
#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc.h>
#include <lk/macros.h>
#include <stdbool.h>
#include <stdlib.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty_log.h>
#include <uapi/err.h>

#define PAGE_SIZE getauxval(AT_PAGESZ)

typedef uint8_t counter_t;

struct sancov_ctx {
    handle_t coverage_srv;
    size_t idx;
    struct cov_shm mailbox;
    struct cov_shm data;
    volatile struct coverage_record_header* headers;
    volatile counter_t* counters;
    volatile uintptr_t* pcs;
    size_t record_len;
    size_t num_counters;
};

static bool in_sancov = false;

#define SANCOV_START \
    if (in_sancov) { \
        return;      \
    }                \
    in_sancov = true;

#define SANCOV_FINISH in_sancov = false;

static size_t header_len(void) {
    return sizeof(struct coverage_record_header) + /* COV_START */
           sizeof(struct coverage_record_header) + /* COV_8BIT_COUNTERS */
           sizeof(struct coverage_record_header) + /* COV_INSTR_PCS */
           sizeof(struct coverage_record_header);  /* COV_TOTAL_LENGTH */
}

static size_t counters_data_len(size_t num_counters) {
    return sizeof(counter_t) * num_counters;
}

static size_t pcs_data_len(size_t num_counters) {
    return sizeof(uintptr_t) * num_counters;
}

static size_t record_len(size_t num_counters) {
    return header_len() + counters_data_len(num_counters) +
           pcs_data_len(num_counters);
}

static void initialize_header(volatile struct coverage_record_header* headers,
                              size_t num_counters) {
    uint32_t offset = header_len();
    headers[1].type = COV_8BIT_COUNTERS;
    headers[1].offset = offset;
    offset += sizeof(counter_t) * num_counters;
    headers[2].type = COV_INSTR_PCS;
    headers[2].offset = offset;
    offset += sizeof(uintptr_t) * num_counters;
    headers[3].type = COV_TOTAL_LENGTH;
    headers[3].offset = offset;

    /* Mark the header as finished */
    headers[0].offset = 0;
    headers[0].type = COV_START;
}

static int init(struct sancov_ctx* ctx, size_t num_counters) {
    int rc;
    handle_t chan;
    handle_t memref;
    struct coverage_aggregator_req req;
    struct coverage_aggregator_resp resp;

    rc = tipc_connect(&chan, COVERAGE_AGGREGATOR_PORT);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to connect to coverage aggregator service\n", rc);
        return rc;
    }

    req.hdr.cmd = COVERAGE_AGGREGATOR_CMD_REGISTER;
    req.register_args.record_len = record_len(num_counters);

    rc = coverage_aggregator_rpc(chan, &req, NULL, &resp, &memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) coverage aggregator RPC\n", rc);
        goto err_rpc;
    }

    rc = cov_shm_mmap(&ctx->mailbox, memref, resp.register_args.mailbox_len);
    if (rc != NO_ERROR) {
        TLOGE("failed to mmap() mailbox shared memory\n");
        goto err_mmap;
    }

    ctx->num_counters = num_counters;
    ctx->record_len = record_len(num_counters);
    ctx->coverage_srv = chan;
    ctx->idx = resp.register_args.idx;

    close(memref);
    return NO_ERROR;

err_mmap:
    close(memref);
err_rpc:
    close(chan);
    return rc;
}

static int get_record(struct sancov_ctx* ctx) {
    int rc;
    handle_t memref;
    struct coverage_aggregator_req req;
    struct coverage_aggregator_resp resp;
    size_t shm_len;

    if (cov_shm_is_mapped(&ctx->data)) {
        cov_shm_munmap(&ctx->data);
    }
    ctx->counters = NULL;
    ctx->pcs = NULL;

    req.hdr.cmd = COVERAGE_AGGREGATOR_CMD_GET_RECORD;

    rc = coverage_aggregator_rpc(ctx->coverage_srv, &req, NULL, &resp, &memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) coverage aggregator RPC\n", rc);
        return rc;
    }
    shm_len = resp.get_record_args.shm_len;

    if (shm_len < ctx->record_len) {
        TLOGE("not enough shared memory, received: %zu, need at least: %zu\n",
              shm_len, ctx->record_len);
        rc = ERR_BAD_LEN;
        goto out;
    }

    rc = cov_shm_mmap(&ctx->data, memref, resp.get_record_args.shm_len);
    if (rc != NO_ERROR) {
        TLOGE("failed to mmap() coverage record shared memory\n");
        goto out;
    }

    ctx->headers = ctx->data.base;
    initialize_header(ctx->headers, ctx->num_counters);

    ctx->counters = ctx->data.base + header_len();
    ctx->pcs = ctx->data.base + header_len() +
               counters_data_len(ctx->num_counters);
    rc = NO_ERROR;

out:
    close(memref);
    return rc;
}

static void update_record(struct sancov_ctx* ctx, size_t idx, uintptr_t pc) {
    assert(idx < ctx->num_counters);
    /*
     * Since counters are fixed-sized, there is always a chance of overflowing.
     * Cap maximum counter value instead of overflowing.
     */
    if (ctx->counters[idx] < (counter_t)(-1)) {
        ctx->counters[idx]++;
    }
    if (!ctx->pcs[idx]) {
        ctx->pcs[idx] = pc - getauxval(AT_BASE);
    }
}

static int get_event(struct sancov_ctx* ctx) {
    int* app_mailbox = (int*)(ctx->mailbox.base) + ctx->idx;
    int event = READ_ONCE(*app_mailbox);
    WRITE_ONCE(*app_mailbox, COVERAGE_MAILBOX_EMPTY);
    return event;
};

static struct sancov_ctx ctx;

__attribute__((__weak__)) void __sanitizer_cov_trace_pc_guard_init(
        uint32_t* start,
        uint32_t* stop) {
    SANCOV_START;

    static size_t num_counters = 0;
    int rc;

    /* Initialize only once */
    if (start == stop || *start) {
        goto out;
    }

    for (uint32_t* x = start; x < stop; x++) {
        *x = ++num_counters;
    }

    TLOGI("sancov initialized with %lu counters\n", num_counters);

    rc = init(&ctx, num_counters * sizeof(counter_t));
    assert(rc == NO_ERROR);

out:
    SANCOV_FINISH;
}

__attribute__((__weak__)) void __sanitizer_cov_trace_pc_guard(uint32_t* guard) {
    SANCOV_START;

    int rc;
    int event = get_event(&ctx);

    /* Guards start at 1, and indices start at 0 */
    assert(*guard > 0);
    size_t idx = *guard - 1;

    switch (event) {
    case COVERAGE_MAILBOX_EMPTY:
        break;

    case COVERAGE_MAILBOX_RECORD_READY:
        rc = get_record(&ctx);
        assert(rc == NO_ERROR);
        break;

    default:
        TLOGE("unknown event: %d\n", event);
        abort();
    }

    if (cov_shm_is_mapped(&ctx.data)) {
        uintptr_t ret_address = (uintptr_t)__builtin_return_address(0);
        /* The sancov tool expects the address of the instruction before the
         * call to this function on ARM and AArch64. */
#if defined(__aarch64__)
        ret_address -= 4;
#elif defined(__arm__)
        ret_address = (ret_address - 3) & (~1);
#else
#error Only ARM and AArch64 are supported by the Trusty sancov runtime
#endif
        update_record(&ctx, idx, ret_address);
    }

    SANCOV_FINISH;
}
