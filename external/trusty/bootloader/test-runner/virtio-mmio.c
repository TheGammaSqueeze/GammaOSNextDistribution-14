/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <virtio-device.h>
#include <virtio-mmio.h>
#include <virtio.h>

void virtio_set_features(struct virtio_config* vio, uint64_t features) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;

    io_write_32(&vio_mmio->guest_features_sel, 0);
    io_write_32(&vio_mmio->guest_features, features & 0xFFFF);
    io_write_32(&vio_mmio->guest_features_sel, 1);
    io_write_32(&vio_mmio->guest_features, features >> 32);
}

uint64_t virtio_get_features(struct virtio_config* vio) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;
    uint64_t features;

    io_write_32(&vio_mmio->host_features_sel, 1);
    features = io_read_32(&vio_mmio->host_features);
    features <<= 32;
    io_write_32(&vio_mmio->host_features_sel, 0);
    features |= io_read_32(&vio_mmio->host_features);
    return features;
}

void vq_attach(struct virtq* vq, uint16_t idx) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vq->vio;

    vq->queue_id = idx;
    io_write_32(&vio_mmio->queue_sel, idx);
    io_write_32(&vio_mmio->queue_num, vq->num_bufs);
    io_write_32(&vio_mmio->queue_align, PAGE_SIZE);
    io_write_32(&vio_mmio->queue_pfn, (uintptr_t)vq->raw >> PAGE_SHIFT);
}

void vq_kick(struct virtq* vq) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vq->vio;

    io_write_32(&vio_mmio->queue_notify, vq->queue_id);
}

void virtio_or_status(struct virtio_config* vio, uint32_t flags) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;
    uint32_t old_status = io_read_32(&vio_mmio->status);

    io_write_32(&vio_mmio->status, old_status | flags);
}

uint32_t virtio_get_status(struct virtio_config* vio) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;

    return io_read_32(&vio_mmio->status);
}

void virtio_reset_device(struct virtio_config* vio) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;

    io_write_32(&vio_mmio->status, 0);
}

void virtio_set_guest_page_size(struct virtio_config* vio, uint32_t size) {
    struct virtio_mmio_config* vio_mmio = (struct virtio_mmio_config*)vio;

    io_write_32(&vio_mmio->guest_page_size, size);
}

struct virtio_config* virtio_probe_console(void) {
    struct virtio_mmio_config* cfg;

    for (cfg = (struct virtio_mmio_config*)VIRTIO_MMIO_BASE;
         cfg->magic == VIRTIO_MMIO_MAGIC; cfg++) {
        if (cfg->device_id == VIRTIO_DEVICE_ID_CONSOLE) {
            return (struct virtio_config*)cfg;
        }
    }
    return NULL;
}
