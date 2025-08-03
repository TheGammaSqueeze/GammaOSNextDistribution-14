/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <assert.h>
#include <test-runner-arch.h>
#include <trusty/rpmb.h>
#include <trusty/trusty_dev.h>
#include <trusty/util.h>
#include <virtio-rpmb.h>
#include <virtio.h>

static const char* rpmb_devname = "rpmb0";

static struct virtq input;
static struct virtq_raw input_raw;
static struct virtq output;
static struct virtq_raw output_raw;

static int rpmb_scan(struct virtio_console* console) {
    /* Scan for RPMB */
    for (size_t i = 0; i < MAX_PORTS; i++) {
        if (!trusty_strcmp(rpmb_devname, console->ports[i].name)) {
            assert(console->ports[i].host_connected);
            return i;
        }
    }

    /* RPMB not found */
    return -1;
}

int init_virtio_rpmb(struct virtio_console* console) {
    int rpmb_id = rpmb_scan(console);
    if (rpmb_id < 0) {
        return rpmb_id;
    }

    vq_init(&input, &input_raw, console->vio, true);
    vq_init(&output, &output_raw, console->vio, false);

    virtio_console_connect_port(console, rpmb_id, &input, &output);
    return 0;
}

static ssize_t send_virtio_rpmb(const void* data, size_t len) {
    return send_vq(&output, data, len);
}

static ssize_t recv_virtio_rpmb(void* data, size_t len) {
    return recv_vq(&input, data, len);
}

int rpmb_storage_send(void* rpmb_dev,
                      const void* rel_write_data,
                      size_t rel_write_size,
                      const void* write_data,
                      size_t write_size,
                      void* read_buf,
                      size_t read_size) {
    int ret;
    uint16_t read_count = read_size / MMC_BLOCK_SIZE;
    uint16_t cmd_count = (rel_write_size + write_size) / MMC_BLOCK_SIZE;

    assert(rel_write_size % MMC_BLOCK_SIZE == 0);
    assert(write_size % MMC_BLOCK_SIZE == 0);

    ret = send_virtio_rpmb(&read_count, sizeof(read_count));
    if (ret < 0) {
        goto err;
    }
    assert((size_t)ret == sizeof(read_count));

    ret = send_virtio_rpmb(&cmd_count, sizeof(cmd_count));
    if (ret < 0) {
        goto err;
    }
    assert((size_t)ret == sizeof(cmd_count));

    ret = send_virtio_rpmb(rel_write_data, rel_write_size);
    if (ret < 0) {
        goto err;
    }
    assert((size_t)ret == rel_write_size);

    ret = send_virtio_rpmb(write_data, write_size);
    if (ret < 0) {
        goto err;
    }
    assert((size_t)ret == write_size);

    ret = recv_virtio_rpmb(read_buf, read_size);
    if (ret < 0) {
        goto err;
    }
    assert((size_t)ret == read_size);

    return 0;

err:
    return TRUSTY_ERR_GENERIC;
}
