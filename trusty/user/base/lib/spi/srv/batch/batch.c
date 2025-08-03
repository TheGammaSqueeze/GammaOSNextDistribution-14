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
#include <lib/spi/srv/batch/dev.h>
#include <lib/spi/srv/common/common.h>
#include <lk/compiler.h>
#include <uapi/err.h>

#ifdef TRUSTY_USERSPACE
#define TLOG_TAG "spi-srv-batch"
#include <trusty_log.h>
#else
#include <stdio.h>
#define TLOGE(fmt, ...) \
    fprintf(stderr, "%s:%d: " fmt, __func__, __LINE__, ##__VA_ARGS__)
#endif

static int handle_xfer_args(struct spi_dev_ctx* spi, struct mem_buf* shm) {
    int rc;
    struct spi_xfer_args* xfer_args;
    uint32_t xfer_args_len;
    uint32_t xfer_args_flags;
    void* payload;
    void* tx;
    void* rx;

    xfer_args = mb_advance_pos(shm, sizeof(*xfer_args));
    if (!xfer_args) {
        TLOGE("failed to read SPI xfer request arguments from shared memory\n");
        return ERR_NO_MEMORY;
    }
    xfer_args_len = READ_ONCE(xfer_args->len);
    xfer_args_flags = READ_ONCE(xfer_args->flags);

    payload = mb_advance_pos(shm, xfer_args_len);
    if (!payload) {
        TLOGE("failed to get payload from shared memory\n");
        return ERR_NO_MEMORY;
    }

    tx = (xfer_args_flags & SPI_XFER_FLAGS_TX) ? payload : NULL;
    rx = (xfer_args_flags & SPI_XFER_FLAGS_RX) ? payload : NULL;

    rc = spi_req_xfer(spi, tx, rx, xfer_args_len);
    if (rc != NO_ERROR) {
        TLOGE("spi xfer failed (%d)\n", rc);
    }

    /* don't modify @xfer_args as a response */
    return rc;
}

static int handle_clk_args(struct spi_dev_ctx* spi, struct mem_buf* shm) {
    int rc;
    struct spi_clk_args* clk_args;

    clk_args = mb_advance_pos(shm, sizeof(*clk_args));
    if (!clk_args) {
        TLOGE("failed to read SPI clk request arguments from shared memory\n");
        return ERR_NO_MEMORY;
    }

    rc = spi_req_set_clk(spi, &clk_args->clk_hz);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to set SPI clock speed\n", rc);
    }

    /* @clk_args response is handled by driver implementation */
    return rc;
}

static int handle_delay_args(struct spi_dev_ctx* spi, struct mem_buf* shm) {
    int rc = 0;
    struct spi_delay_args* delay_args;

    delay_args = mb_advance_pos(shm, sizeof(*delay_args));
    if (!delay_args) {
        TLOGE("failed to read delay request arguments from shared memory\n");
        return ERR_NO_MEMORY;
    }

    rc = spi_req_delay(spi, delay_args->delay_ns);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to request delay\n", rc);
    }

    delay_args->delay_ns = 0;
    return rc;
}

static int unpack_shm(struct spi_dev_ctx* spi,
                      struct mem_buf* shm,
                      size_t len,
                      struct spi_batch_state* state) {
    int rc;
    struct spi_shm_hdr* shm_hdr;
    uint32_t shm_hdr_cmd;

    /*
     * Resize @shm, so that we don't process more than batch length. And rewind
     * @shm position back to the beginning.
     */
    mb_resize(shm, len);

    while (mb_curr_pos(shm) < len) {
        shm_hdr = mb_advance_pos(shm, sizeof(*shm_hdr));
        if (!shm_hdr) {
            TLOGE("failed to read spi_shm_hdr in shared memory\n");
            return ERR_NO_MEMORY;
        }
        shm_hdr_cmd = READ_ONCE(shm_hdr->cmd);

        switch (shm_hdr_cmd) {
        case SPI_CMD_SHM_OP_XFER:
            if (state->cs) {
                rc = handle_xfer_args(spi, shm);
            } else {
                rc = ERR_NOT_READY;
            }
            break;

        case SPI_CMD_SHM_OP_CS_ASSERT:
            if (state->cs) {
                rc = ERR_BUSY;
            } else {
                rc = spi_req_cs_assert(spi);
                state->cs = true;
            }
            break;

        case SPI_CMD_SHM_OP_CS_DEASSERT:
            if (state->cs) {
                rc = spi_req_cs_deassert(spi);
                state->cs = false;
            } else {
                rc = ERR_NOT_READY;
            }
            break;

        case SPI_CMD_SHM_OP_SET_CLK:
            rc = handle_clk_args(spi, shm);
            break;

        case SPI_CMD_SHM_OP_DELAY:
            rc = handle_delay_args(spi, shm);
            break;

        default:
            TLOGE("cmd 0x%x: unknown command\n", shm_hdr_cmd);
            rc = ERR_CMD_UNKNOWN;
        }

        WRITE_ONCE(shm_hdr->cmd, shm_hdr_cmd | SPI_CMD_RESP_BIT);
        WRITE_ONCE(shm_hdr->status, translate_lk_err(rc));

        if (rc != NO_ERROR) {
            TLOGE("failed (%d) to unpack SPI request at index: %zu\n", rc,
                  state->num_cmds);
            return rc;
        }
        state->num_cmds++;
    }

    return NO_ERROR;
}

int spi_srv_handle_batch(struct spi_dev_ctx* spi,
                         struct mem_buf* shm,
                         struct spi_batch_req* batch_req,
                         struct spi_batch_state* state) {
    int rc = NO_ERROR;

    if (batch_req->len > shm->capacity) {
        TLOGE("requests batch size(%d) is larger than shared memory(%zu)\n",
              batch_req->len, shm->capacity);
        return ERR_TOO_BIG;
    }

    /* SPI devices with shared bus must be deasserted before command sequence */
    assert(!state->cs || !spi_is_bus_shared(spi));

    rc = spi_seq_begin(spi, batch_req->num_cmds);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to begin SPI requests\n", rc);
        return rc;
    }

    rc = unpack_shm(spi, shm, batch_req->len, state);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to unpack SPI requests, aborting sequence\n", rc);
        goto err;
    }

    if (state->num_cmds != batch_req->num_cmds) {
        TLOGE("number of commands in shared memory(%zu) and in request(%d) "
              "are different\n",
              state->num_cmds, batch_req->num_cmds);
        rc = ERR_INVALID_ARGS;
        goto err;
    }

    if (mb_curr_pos(shm) != batch_req->len) {
        TLOGE("response size (%zu) and request size (%d) are different\n",
              mb_curr_pos(shm), batch_req->len);
        rc = ERR_BAD_LEN;
        goto err;
    }

    /* SPI devices with shared bus must be deasserted after command sequence */
    if (state->cs && spi_is_bus_shared(spi)) {
        rc = ERR_BAD_STATE;
        goto err;
    }

    rc = spi_seq_commit(spi);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to commit SPI requests\n", rc);
        goto err;
    }

    return NO_ERROR;

err:
    spi_seq_abort(spi);
    return rc;
}
