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

#include "swspi.h"

#include <assert.h>
#include <lib/spi/srv/dev.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <uapi/err.h>

#define TLOG_TAG "swspi-drv"
#include <trusty_log.h>

bool spi_is_bus_shared(struct spi_dev_ctx* dev) {
    return dev->bus->num_devs > 1;
}

static inline bool spi_dev_seq_active(struct spi_dev_ctx* dev) {
    return dev->cmds;
}

static void spi_req_exec_set_clk(struct spi_dev_ctx* dev, void* priv) {
    /* Not a real device. No clock to configure */
}

int spi_req_set_clk(struct spi_dev_ctx* dev, uint64_t* clk_hz) {
    assert(dev);
    assert(clk_hz);

    dev->cmds[dev->curr_cmd].exec = spi_req_exec_set_clk;
    dev->curr_cmd++;
    return NO_ERROR;
}

static void spi_req_exec_cs_assert(struct spi_dev_ctx* dev, void* priv) {
    assert(dev->bus->owner == NULL);
    /* become bus owner */
    dev->bus->owner = dev;
}

int spi_req_cs_assert(struct spi_dev_ctx* dev) {
    assert(spi_dev_seq_active(dev));
    assert(dev->curr_cmd < dev->num_cmds);

    dev->cmds[dev->curr_cmd].exec = spi_req_exec_cs_assert;
    dev->curr_cmd++;
    return NO_ERROR;
}

static void spi_req_exec_cs_deassert(struct spi_dev_ctx* dev, void* priv) {
    assert(dev->bus->owner == dev);
    /* release the bus */
    dev->bus->owner = NULL;
}

int spi_req_cs_deassert(struct spi_dev_ctx* dev) {
    assert(spi_dev_seq_active(dev));
    assert(dev->curr_cmd < dev->num_cmds);

    dev->cmds[dev->curr_cmd].exec = spi_req_exec_cs_deassert;
    dev->curr_cmd++;
    return NO_ERROR;
}

/* calculate an 8-bit digest of a buffer */
static uint8_t digest(uint8_t* buf, size_t sz) {
    uint8_t digest = 0;

    for (size_t i = 0; i < sz; i++) {
        /* rotate right one bit */
        digest = digest >> 1 | (digest & 0x1) << 7;
        digest ^= buf[i];
    }
    return digest;
}

/* fill buffer with predefined pattern */
static void rand_buf(uint8_t* buf, size_t sz, uint8_t seed) {
    /* seed RNG */
    srand(seed);

    for (size_t i = 0; i < sz; i++) {
        buf[i] = rand() % 0xff;
    }
}

struct spi_req_xfer_args {
    void* tx;
    void* rx;
    size_t len;
};

/*
 * This device calculates an 8-bit digest of TX buffer, seeds rand() with that
 * digest, fills RX with random bytes, and sends it back to us. If it's a
 * receive-only transfer, i.e. no TX buffer, use seed 0.
 */
static void spi_req_exec_xfer(struct spi_dev_ctx* dev, void* priv) {
    struct spi_req_xfer_args* args = (struct spi_req_xfer_args*)priv;
    void* tx = args->tx;
    void* rx = args->rx;
    size_t len = args->len;
    uint8_t seed = 0;

    if (dev->loopback) {
        if (rx) {
            if (tx) {
                memcpy(rx, tx, len);
            } else {
                memset(rx, 0, len);
            }
        }
        return;
    }

    if (tx) {
        seed = digest(tx, len);
    }

    if (rx) {
        rand_buf(rx, len, seed);
    }
}

int spi_req_xfer(struct spi_dev_ctx* dev, void* tx, void* rx, size_t len) {
    assert(spi_dev_seq_active(dev));
    assert(dev->curr_cmd < dev->num_cmds);

    struct spi_req_xfer_args* args = malloc(sizeof(*args));
    if (!args) {
        TLOGE("failed to allocate memory for arguments for xfer requests\n");
        return ERR_NO_MEMORY;
    }
    args->tx = tx;
    args->rx = rx;
    args->len = len;

    dev->cmds[dev->curr_cmd].exec = spi_req_exec_xfer;
    dev->cmds[dev->curr_cmd].priv = args;
    dev->curr_cmd++;
    return NO_ERROR;
}

static void spi_req_exec_delay(struct spi_dev_ctx* dev, void* priv) {
    uint64_t delay_ns = *((uint64_t*)priv);
    trusty_nanosleep(0, 0, delay_ns);
}

int spi_req_delay(struct spi_dev_ctx* dev, uint64_t delay_ns) {
    assert(dev);

    uint64_t* arg = malloc(sizeof(*arg));
    if (!arg) {
        TLOGE("failed to allocate memory for delay argument\n");
        return ERR_NO_MEMORY;
    }
    *arg = delay_ns;

    dev->cmds[dev->curr_cmd].exec = spi_req_exec_delay;
    dev->cmds[dev->curr_cmd].priv = arg;
    dev->curr_cmd++;
    return NO_ERROR;
}

int spi_seq_begin(struct spi_dev_ctx* dev, size_t num_cmds) {
    assert(!spi_dev_seq_active(dev));

    /* allocate SPI sequence represented by an array of &struct spi_seq_entry */
    dev->cmds = calloc(num_cmds, sizeof(struct spi_seq_entry));
    if (!dev->cmds) {
        TLOGE("failed to allocate memory for SPI sequence\n");
        return ERR_NO_MEMORY;
    }

    dev->num_cmds = num_cmds;
    return NO_ERROR;
}

static void spi_seq_free(struct spi_dev_ctx* dev) {
    for (size_t i = 0; i < dev->num_cmds; i++) {
        void* priv = dev->cmds[i].priv;
        if (priv) {
            free(priv);
        }
    }
    free(dev->cmds);
    dev->cmds = NULL;
    dev->num_cmds = 0;
    dev->curr_cmd = 0;
}

int spi_seq_commit(struct spi_dev_ctx* dev) {
    void* priv;
    size_t i;

    assert(spi_dev_seq_active(dev));
    assert(dev->curr_cmd == dev->num_cmds);

    /* iterate through SPI sequence and execute each SPI request */
    for (i = 0; i < dev->num_cmds; i++) {
        priv = dev->cmds[i].priv;
        dev->cmds[i].exec(dev, priv);
    }

    spi_seq_free(dev);
    return NO_ERROR;
}

void spi_seq_abort(struct spi_dev_ctx* dev) {
    assert(spi_dev_seq_active(dev));
    spi_seq_free(dev);
}
