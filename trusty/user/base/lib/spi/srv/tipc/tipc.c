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

#include <interface/spi/spi.h>
#include <lib/spi/common/utils.h>
#include <lib/spi/srv/common/common.h>
#include <lib/spi/srv/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#define TLOG_TAG "spi-srv-tipc"
#include <trusty_log.h>

/**
 * chan_ctx - per-connection SPI data
 * @shm:        state of memory region shared with SPI server
 * @shm_handle: handle to shared memory region
 * @cs:         tracks CS state of the underlying SPI device
 *              true - asserted, false - deasserted
 */
struct chan_ctx {
    struct mem_buf shm;
    handle_t shm_handle;
    bool cs;
};

static inline bool shm_is_mapped(struct chan_ctx* ctx) {
    return ctx->shm.buf && ctx->shm_handle != INVALID_IPC_HANDLE;
}

static inline void shm_unmap(struct chan_ctx* ctx) {
    if (shm_is_mapped(ctx)) {
        munmap(ctx->shm.buf, ctx->shm.capacity);
        mb_destroy(&ctx->shm);
        close(ctx->shm_handle);
        ctx->shm_handle = INVALID_IPC_HANDLE;
    }
}

union spi_msg_req_args {
    struct spi_shm_map_req shm;
    struct spi_batch_req batch;
};

static size_t get_spi_msg_size(struct spi_msg_req* req) {
    size_t msg_size = sizeof(struct spi_msg_req);
    switch (req->cmd & SPI_CMD_OP_MASK) {
    case SPI_CMD_MSG_OP_SHM_MAP:
        msg_size += sizeof(struct spi_shm_map_req);
        break;

    case SPI_CMD_MSG_OP_BATCH_EXEC:
        msg_size += sizeof(struct spi_batch_req);
        break;
    }
    return msg_size;
}

static int recv_msg(handle_t chan,
                    struct spi_msg_req* req,
                    union spi_msg_req_args* args,
                    handle_t* h) {
    int rc;
    struct ipc_msg_info msg_inf;
    size_t num_handles = h ? 1 : 0;

    rc = get_msg(chan, &msg_inf);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to get_msg()\n", rc);
        return rc;
    }

    struct iovec iovs[2] = {
            {
                    .iov_base = req,
                    .iov_len = sizeof(*req),
            },
            {
                    .iov_base = args,
                    .iov_len = sizeof(*args),
            },
    };
    struct ipc_msg msg = {
            .iov = iovs,
            .num_iov = countof(iovs),
            .handles = h,
            .num_handles = num_handles,
    };
    rc = read_msg(chan, msg_inf.id, 0, &msg);
    if (rc != (int)get_spi_msg_size(req)) {
        TLOGE("failed (%d) to read_msg()\n", rc);
        put_msg(chan, msg_inf.id);
        return rc;
    }

    put_msg(chan, msg_inf.id);
    return NO_ERROR;
}

static int handle_msg_shm_map_req(handle_t chan,
                                  struct chan_ctx* ctx,
                                  struct spi_shm_map_req* shm_req,
                                  handle_t shm_handle) {
    int rc = NO_ERROR;
    void* shm_base;

    shm_unmap(ctx);

    shm_base = mmap(0, shm_req->len, MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE,
                    0, shm_handle, 0);
    if (shm_base == MAP_FAILED) {
        TLOGE("failed to map shared memory\n");
        rc = ERR_GENERIC;
        goto err_mmap;
    }

    struct spi_msg_resp resp = {
        .status = translate_lk_err(rc)
    };

    rc = tipc_send1(chan, &resp, sizeof(resp));
    if (rc < 0 || (size_t)rc != sizeof(resp)) {
        TLOGE("failed (%d) to send SPI response\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        goto err_resp;
    }

    mb_init(&ctx->shm, shm_base, shm_req->len, SPI_CMD_SHM_ALIGN);
    ctx->shm_handle = shm_handle;
    return NO_ERROR;

err_resp:
    munmap(shm_base, shm_req->len);
err_mmap:
    return rc;
}

static int handle_msg_batch_req(handle_t chan,
                                struct spi_dev_ctx* spi,
                                struct chan_ctx* ctx,
                                struct spi_batch_req* batch_req) {
    int rc;
    struct spi_batch_state state;

    if (!shm_is_mapped(ctx)) {
        return ERR_BAD_STATE;
    }

    state.cs = ctx->cs;
    state.num_cmds = 0;
    rc = spi_srv_handle_batch(spi, &ctx->shm, batch_req, &state);
    if (rc == NO_ERROR) {
        ctx->cs = state.cs;
    }

    struct spi_msg_resp resp = {
        .cmd = SPI_CMD_MSG_OP_BATCH_EXEC | SPI_CMD_RESP_BIT,
        .status = translate_lk_err(rc)
    };

    struct spi_batch_resp batch_resp = {
        .len = mb_curr_pos(&ctx->shm),
        .failed = (rc != NO_ERROR) ? (uint32_t)state.num_cmds : 0
    };

    rc = tipc_send2(chan, &resp, sizeof(resp), &batch_resp, sizeof(batch_resp));
    if (rc < 0 || (size_t)rc != sizeof(resp) + sizeof(batch_resp)) {
        TLOGE("failed (%d) to send batch response\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

static int on_connect(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p) {
    struct chan_ctx* ctx = calloc(1, sizeof(struct chan_ctx));
    if (!ctx) {
        TLOGE("failed to allocate channel context\n");
        return ERR_NO_MEMORY;
    }

    ctx->shm_handle = INVALID_IPC_HANDLE;

    *ctx_p = ctx;
    return NO_ERROR;
}

static int on_message(const struct tipc_port* port,
                      handle_t chan,
                      void* chan_ctx) {
    int rc;
    struct spi_msg_req req;
    union spi_msg_req_args args;
    struct spi_dev_ctx* spi = (struct spi_dev_ctx*)port->priv;
    struct chan_ctx* ctx = (struct chan_ctx*)chan_ctx;
    handle_t h = INVALID_IPC_HANDLE;

    rc = recv_msg(chan, &req, &args, &h);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to receive SPI message, closing connection\n", rc);
        return rc;
    }

    switch (req.cmd & SPI_CMD_OP_MASK) {
    case SPI_CMD_MSG_OP_SHM_MAP:
        rc = handle_msg_shm_map_req(chan, ctx, &args.shm, h);
        break;

    case SPI_CMD_MSG_OP_BATCH_EXEC:
        rc = handle_msg_batch_req(chan, spi, ctx, &args.batch);
        break;

    default:
        TLOGE("cmd 0x%x: unknown command\n", req.cmd);
        rc = ERR_CMD_UNKNOWN;
    }

    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to handle SPI message, closing connection\n", rc);
        return rc;
    }

    return NO_ERROR;
}

static void on_disconnect(const struct tipc_port* port,
                          handle_t chan,
                          void* _ctx) {
    int rc;
    struct spi_dev_ctx* spi = (struct spi_dev_ctx*)port->priv;
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;
    struct spi_shm_hdr hdr;
    struct mem_buf mb;
    struct spi_batch_req req;
    struct spi_batch_state state;

    /* make sure CS is deasserted */
    if (!ctx->cs) {
        return;
    }

    /* Construct a batch with a single deassert command to recover CS state */
    hdr.cmd = SPI_CMD_SHM_OP_CS_DEASSERT;

    /* Deassert commands are header only */
    mb_init(&mb, &hdr, sizeof(hdr), SPI_CMD_SHM_ALIGN);
    mb_resize(&mb, sizeof(hdr));

    req.len = sizeof(hdr);
    req.num_cmds = 1;

    state.cs = true;
    state.num_cmds = 0;

    rc = spi_srv_handle_batch(spi, &mb, &req, &state);
    /* CS state will be out of sync. This is an unrecoverable error. */
    assert(rc == NO_ERROR);

    ctx->cs = false;
}

static void on_channel_cleanup(void* _ctx) {
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;
    assert(!ctx->cs);
    shm_unmap(ctx);
    free(ctx);
}

static const struct tipc_srv_ops spi_dev_ops = {
        .on_connect = on_connect,
        .on_message = on_message,
        .on_disconnect = on_disconnect,
        .on_channel_cleanup = on_channel_cleanup,
};

int add_spi_service(struct tipc_hset* hset,
                    const struct tipc_port* ports,
                    size_t num_ports) {
    int rc;

    for (size_t i = 0; i < num_ports; i++) {
        if (!ports[i].priv) {
            return ERR_INVALID_ARGS;
        }

        rc = tipc_add_service(hset, &ports[i], 1, 1, &spi_dev_ops);
        if (rc != NO_ERROR) {
            return rc;
        }
    }

    return NO_ERROR;
}
