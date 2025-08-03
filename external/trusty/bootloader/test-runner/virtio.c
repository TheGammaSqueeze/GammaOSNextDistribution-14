/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <arch/io-mem.h>
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <test-runner-arch.h>
#include <trusty/sysdeps.h>
#include <virtio-device.h>
#include <virtio.h>

void vq_init(struct virtq* vq,
             struct virtq_raw* raw,
             struct virtio_config* vio,
             bool is_input) {
    uint16_t flags = 0;

    if (is_input) {
        flags = VIRTQ_DESC_F_WRITE;
    }

    vq->raw = raw;
    vq->num_bufs = VQ_SIZE;
    for (size_t i = 0; i < vq->num_bufs; i++) {
        vq->raw->desc[i].flags = flags;
    }

    vq->vio = vio;
}

void vq_make_avail(struct virtq* vq, uint16_t desc_id) {
    io_write_16(&vq->raw->avail.ring[vq->raw->avail.idx % vq->num_bufs],
                desc_id);
    io_write_16(&vq->raw->avail.idx, vq->raw->avail.idx + 1);
}

void vq_wait(struct virtq* vq) {
    while (!vq_ready(vq)) {
    }
}

uint32_t vq_adv(struct virtq* vq) {
    return vq->raw->used.ring[vq->old_used_idx++ % vq->num_bufs].len;
}

void vq_set_buf_w(struct virtq* vq, uint16_t desc_id, void* data, size_t len) {
    vq->raw->desc[desc_id].addr = (uint64_t)data;
    vq->raw->desc[desc_id].len = len;
    assert(vq->raw->desc[desc_id].flags == VIRTQ_DESC_F_WRITE);
}

void vq_set_buf_r(struct virtq* vq,
                  uint16_t desc_id,
                  const void* data,
                  size_t len) {
    vq->raw->desc[desc_id].addr = (uint64_t)data;
    vq->raw->desc[desc_id].len = len;
    assert(vq->raw->desc[desc_id].flags == 0);
}

ssize_t send_vq(struct virtq* vq, const char* data, size_t len) {
    size_t idx = vq->old_used_idx;

    if (len == 0) {
        return 0;
    }

    vq_set_buf_r(vq, idx % vq->num_bufs, data, len);
    vq_make_avail(vq, idx % vq->num_bufs);
    vq_kick(vq);
    vq_wait(vq);
    vq_set_buf_r(vq, idx % vq->num_bufs, NULL, 0);
    /*
     * QEMU's device does not set len correctly, as per the legacy-mode
     * notes. This means the value returned by vq_adv is unreliable, so we
     * assume no partial write and return len.
     */
    vq_adv(vq);
    return len;
}

ssize_t recv_vq(struct virtq* vq, char* data, size_t len) {
    size_t idx = vq->old_used_idx;

    if (len == 0) {
        return 0;
    }

    vq_set_buf_w(vq, idx % vq->num_bufs, data, len);
    vq_make_avail(vq, idx % vq->num_bufs);
    vq_kick(vq);
    vq_wait(vq);
    vq_set_buf_w(vq, idx % vq->num_bufs, NULL, 0);
    return vq_adv(vq);
}
