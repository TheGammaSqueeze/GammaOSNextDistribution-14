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

#include <assert.h>
#include <lib/spi/client/spi.h>
#include <lib/tipc/tipc.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <trusty/memref.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#define TLOG_TAG "spi-client"
#include <trusty_log.h>

#define PAGE_SIZE getauxval(AT_PAGESZ)

/**
 * Size of the largest SPI request argument structure. Needs to be updated if we
 * add larger SPI arguments.
 */
#define SPI_CMD_SHM_ARGS_MAX_SIZE sizeof(struct spi_xfer_args)

static int send_shm(struct spi_dev* dev,
                    struct spi_msg_req* req,
                    struct spi_shm_map_req* shm_req,
                    handle_t memref) {
    int rc;
    struct iovec iovs[2] = {
            {
                    .iov_base = req,
                    .iov_len = sizeof(*req),
            },
            {
                    .iov_base = shm_req,
                    .iov_len = sizeof(*shm_req),
            },
    };
    struct ipc_msg msg = {
            .iov = iovs,
            .num_iov = countof(iovs),
            .handles = &memref,
            .num_handles = 1,
    };
    rc = send_msg(dev->h, &msg);
    if (rc < 0) {
        TLOGE("failed (%d) to send memref\n", rc);
        return rc;
    }
    return NO_ERROR;
}

static int handle_shm_resp(handle_t chan) {
    int rc;
    struct uevent evt;
    struct spi_msg_resp resp;

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to wait for reply\n", rc);
        return rc;
    }

    rc = tipc_recv1(chan, sizeof(resp), &resp, sizeof(resp));
    if (rc < 0 || (size_t)rc != sizeof(resp)) {
        TLOGE("failed (%d) to read reply\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return translate_srv_err(resp.status);
}

static int shm_map(struct spi_dev* dev, void* shm_base, size_t shm_size) {
    int rc;
    struct spi_msg_req req;
    struct spi_shm_map_req shm_req;

    /* create memref to send to SPI server */
    rc = memref_create(shm_base, shm_size,
                       MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE);
    if (rc < 0) {
        TLOGE("failed (%d) to create memref\n", rc);
        goto err_memref_create;
    }
    handle_t memref = (handle_t)rc;

    /* send memref to SPI server */
    req.cmd = SPI_CMD_MSG_OP_SHM_MAP;
    shm_req.len = shm_size;
    rc = send_shm(dev, &req, &shm_req, memref);
    if (rc < 0) {
        TLOGE("failed (%d) to send memref\n", rc);
        goto err_send_msg;
    }

    /* handle SPI server's response */
    rc = handle_shm_resp(dev->h);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to handle shared memory map response\n", rc);
        goto err_resp;
    }

    close(memref);
    return NO_ERROR;

err_resp:
err_send_msg:
    close(memref);
err_memref_create:
    return rc;
}

static inline size_t get_shm_size(size_t max_num_cmds,
                                  size_t max_total_payload) {
    /* account for space taken up by alignment requirements */
    size_t max_total_align = max_num_cmds * (SPI_CMD_SHM_ALIGN - 1);
    size_t cmd_size = round_up(sizeof(struct spi_shm_hdr), SPI_CMD_SHM_ALIGN) +
                      round_up(SPI_CMD_SHM_ARGS_MAX_SIZE, SPI_CMD_SHM_ALIGN);
    size_t shm_size =
            max_num_cmds * cmd_size + max_total_payload + max_total_align;

    return round_up(shm_size, PAGE_SIZE);
}

int spi_dev_open(struct spi_dev* dev,
                 const char* name,
                 size_t max_num_cmds,
                 size_t max_total_payload) {
    int rc;
    void* shm_base;
    size_t shm_size;

    if (!dev || !name || max_num_cmds == 0) {
        return ERR_INVALID_ARGS;
    }

    /* connect to SPI service */
    rc = tipc_connect(&dev->h, name);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to connect to service \"%s\"\n", rc, name);
        goto err_connect;
    }

    /* allocate shared memory */
    shm_size = get_shm_size(max_num_cmds, max_total_payload);
    shm_base = memalign(PAGE_SIZE, shm_size);
    if (!shm_base) {
        TLOGE("failed to allocate shared memory, base: %p, size: %zu\n",
              shm_base, shm_size);
        rc = ERR_NO_MEMORY;
        goto err_shm_alloc;
    }

    /* establish shared memory with SPI server*/
    rc = shm_map(dev, shm_base, shm_size);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to send shared memory\n", rc);
        goto err_shm_send;
    }

    mb_init(&dev->shm, shm_base, shm_size, SPI_CMD_SHM_ALIGN);
    mb_resize(&dev->shm, shm_size);
    dev->max_num_cmds = max_num_cmds;
    dev->max_total_payload = max_total_payload;
    spi_clear_cmds(dev);
    return NO_ERROR;

err_shm_send:
    /*
     * There is no way to free() shared memory safely once SPI server receives
     * the memref. At this point in the program, we don't know if shm_map() has
     * successfully sent the shared memory or not. So we leak the memory in case
     * it was already shared.
     * TODO: It may be possible to avoid memory leaks using other ways of
     * allocating shared memory.
     */
err_shm_alloc:
    close(dev->h);
    dev->h = INVALID_IPC_HANDLE;
err_connect:
    return rc;
}

static inline bool is_initialized(struct spi_dev* dev) {
    return dev && dev->h != INVALID_IPC_HANDLE;
}

void spi_clear_cmds(struct spi_dev* dev) {
    assert(is_initialized(dev));
    mb_rewind_pos(&dev->shm);
    dev->num_cmds = 0;
    dev->total_payload = 0;
    dev->config_err = false;
}

static int send_batch_req(struct spi_dev* dev) {
    struct spi_msg_req req = {
            .cmd = SPI_CMD_MSG_OP_BATCH_EXEC,
    };
    struct spi_batch_req batch_req = {
            .len = mb_curr_pos(&dev->shm),
            .num_cmds = dev->num_cmds,
    };
    int rc = tipc_send2(dev->h, &req, sizeof(req), &batch_req,
                        sizeof(batch_req));
    if (rc < 0 || (size_t)rc != sizeof(req) + sizeof(batch_req)) {
        TLOGE("failed (%d) to send SPI batch request\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }
    return NO_ERROR;
}

static int validate_batch_resp(struct spi_batch_resp* batch_resp,
                               struct mem_buf* shm,
                               size_t* failed) {
    int rc = NO_ERROR;
    struct spi_shm_hdr* shm_hdr;
    uint32_t shm_hdr_cmd;
    uint32_t shm_hdr_status;
    struct spi_xfer_args* xfer_resp;
    uint32_t xfer_resp_len;

    /*
     * length of the response in shared memory must be equal to that of the
     * request
     */
    if (batch_resp->len != mb_curr_pos(shm)) {
        return ERR_BAD_STATE;
    }

    mb_rewind_pos(shm);

    while (mb_curr_pos(shm) < batch_resp->len) {
        shm_hdr = mb_advance_pos(shm, sizeof(*shm_hdr));
        shm_hdr_cmd = READ_ONCE(shm_hdr->cmd);
        shm_hdr_status = READ_ONCE(shm_hdr->status);

        if (!(shm_hdr_cmd & SPI_CMD_RESP_BIT)) {
            TLOGE("invalid response 0x%08x\n", shm_hdr_cmd);
            return ERR_BAD_STATE;
        }
        rc = translate_srv_err(shm_hdr_status);
        if (rc != NO_ERROR) {
            return rc;
        }

        switch (shm_hdr_cmd & SPI_CMD_OP_MASK) {
        case SPI_CMD_SHM_OP_XFER:
            /* skip xfer_resp and payload */
            xfer_resp = mb_advance_pos(shm, sizeof(*xfer_resp));
            xfer_resp_len = READ_ONCE(xfer_resp->len);
            mb_advance_pos(shm, xfer_resp_len);
            break;
        case SPI_CMD_SHM_OP_CS_ASSERT:
        case SPI_CMD_SHM_OP_CS_DEASSERT:
            break;
        case SPI_CMD_SHM_OP_SET_CLK:
            /* skip spi_clk_args */
            mb_advance_pos(shm, sizeof(struct spi_clk_args));
            break;
        case SPI_CMD_SHM_OP_DELAY:
            /* skip spi_delay_args */
            mb_advance_pos(shm, sizeof(struct spi_delay_args));
            break;
        default:
            TLOGE("cmd 0x%x: unknown command\n", shm_hdr_cmd);
            return ERR_CMD_UNKNOWN;
        }
        (*failed)++;
    }

    return NO_ERROR;
}

static int handle_batch_resp(struct spi_dev* dev, size_t* failed) {
    int rc;
    struct uevent evt;
    struct spi_msg_resp resp;
    struct spi_batch_resp batch_resp;

    rc = wait(dev->h, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to wait for batch response\n", rc);
        return rc;
    }

    rc = tipc_recv2(dev->h, sizeof(resp) + sizeof(batch_resp), &resp,
                    sizeof(resp), &batch_resp, sizeof(batch_resp));
    if (rc < 0 || (size_t)rc != sizeof(resp) + sizeof(batch_resp)) {
        TLOGE("failed (%d) to receive batch response\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    rc = translate_srv_err(resp.status);
    if (rc != NO_ERROR) {
        TLOGE("batch request encountered an error\n");
        *failed = batch_resp.failed;
        return rc;
    }

    return validate_batch_resp(&batch_resp, &dev->shm, failed);
}

int spi_exec_cmds(struct spi_dev* dev, size_t* failed) {
    int rc;
    size_t fake_failed;

    if (!is_initialized(dev)) {
        return ERR_INVALID_ARGS;
    }

    if (!failed) {
        failed = &fake_failed;
    }
    *failed = 0;

    if (dev->config_err) {
        rc = ERR_BAD_STATE;
        *failed = dev->num_cmds;
        goto out;
    }

    rc = send_batch_req(dev);
    if (rc != NO_ERROR) {
        goto out;
    }

    rc = handle_batch_resp(dev, failed);

out:
    /* reset SPI requests */
    spi_clear_cmds(dev);
    return rc;
}

static int spi_add_cmd(struct spi_dev* dev,
                       uint32_t cmd,
                       void** args,
                       size_t args_len,
                       void** payload,
                       size_t payload_len) {
    int rc;
    struct spi_shm_hdr* shm_hdr;

    assert(args || !args_len);
    assert(payload || !payload_len);
    assert(args_len <= SPI_CMD_SHM_ARGS_MAX_SIZE);

    if (!is_initialized(dev)) {
        rc = ERR_BAD_HANDLE;
        goto err_init;
    }
    if (dev->config_err) {
        rc = ERR_BAD_STATE;
        goto err_config;
    }
    if (dev->num_cmds >= dev->max_num_cmds) {
        rc = ERR_OUT_OF_RANGE;
        goto err_range;
    }

    shm_hdr = mb_advance_pos(&dev->shm, sizeof(*shm_hdr));
    if (!shm_hdr) {
        rc = ERR_TOO_BIG;
        goto err_shm_hdr;
    }
    WRITE_ONCE(shm_hdr->cmd, cmd);
    WRITE_ONCE(shm_hdr->status, 0);

    if (args) {
        *args = mb_advance_pos(&dev->shm, args_len);
        if (!*args) {
            rc = ERR_TOO_BIG;
            goto err_args;
        }
    }
    if (payload) {
        assert(dev->total_payload <= dev->max_total_payload);
        if (payload_len > dev->max_total_payload - dev->total_payload) {
            rc = ERR_TOO_BIG;
            goto err_payload;
        }
        dev->total_payload += payload_len;

        *payload = mb_advance_pos(&dev->shm, payload_len);
        assert(*payload);
    }

    dev->num_cmds++;
    return NO_ERROR;

err_payload:
    *args = NULL;
err_args:
err_shm_hdr:
err_range:
    dev->config_err = true;
err_config:
err_init:
    return rc;
}

int spi_add_data_xfer_cmd(struct spi_dev* dev,
                          void** tx,
                          void** rx,
                          size_t len) {
    int rc;
    struct spi_xfer_args* args;
    uint32_t flags;
    void* payload;

    rc = spi_add_cmd(dev, SPI_CMD_SHM_OP_XFER, (void**)&args, sizeof(*args),
                     &payload, len);
    if (rc != NO_ERROR) {
        return rc;
    }

    flags = (tx ? SPI_XFER_FLAGS_TX : 0) | (rx ? SPI_XFER_FLAGS_RX : 0);
    WRITE_ONCE(args->len, len);
    WRITE_ONCE(args->flags, flags);

    if (tx) {
        *tx = payload;
    }
    if (rx) {
        *rx = payload;
    }

    return NO_ERROR;
}

int spi_add_cs_assert_cmd(struct spi_dev* dev) {
    return spi_add_cmd(dev, SPI_CMD_SHM_OP_CS_ASSERT, NULL, 0, NULL, 0);
}

int spi_add_cs_deassert_cmd(struct spi_dev* dev) {
    return spi_add_cmd(dev, SPI_CMD_SHM_OP_CS_DEASSERT, NULL, 0, NULL, 0);
}

int spi_add_set_clk_cmd(struct spi_dev* dev,
                        uint64_t clk_hz_in,
                        uint64_t** clk_hz_out) {
    int rc;
    struct spi_clk_args* args;

    rc = spi_add_cmd(dev, SPI_CMD_SHM_OP_SET_CLK, (void**)&args, sizeof(*args),
                     NULL, 0);
    if (rc != NO_ERROR) {
        return rc;
    }

    WRITE_ONCE(args->clk_hz, clk_hz_in);

    if (clk_hz_out) {
        *clk_hz_out = &args->clk_hz;
    }

    return NO_ERROR;
}

int spi_add_delay_cmd(struct spi_dev* dev, uint64_t delay_ns) {
    int rc;
    struct spi_delay_args* args;

    rc = spi_add_cmd(dev, SPI_CMD_SHM_OP_DELAY, (void**)&args, sizeof(*args),
                     NULL, 0);
    if (rc != NO_ERROR) {
        return rc;
    }

    WRITE_ONCE(args->delay_ns, delay_ns);

    return NO_ERROR;
}
