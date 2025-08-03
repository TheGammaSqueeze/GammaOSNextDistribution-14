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

#pragma once
#include <arch/io-mem.h>
#include <arch/virtio-base.h>
#include <stdbool.h>
#include <sys/types.h>
#include <trusty/sysdeps.h>

struct virtio_config;

/*
 * Size of virtual queues. This can be dynamic, but since we do not have
 * memory allocation, supporting dynamic allocation seems like overkill.
 *
 * Maximum number of ports we support in multiport of virtual serial console
 * device is 16. To support 16 ports, vqueue size should be double size of
 * maximum ports supported, since host would sent port add control event for
 * all ports in one sequence.
 */
#define VQ_SIZE 32

/**
 * struct virtq_desc - VirtIO buffer descriptor
 * @addr:  Physical address of the buffer
 * @len:   Size of the buffer
 * @flags: Buffer flags, see VIRTQ_DESC_* constants
 * @next:  Used to indicate a chained buffer (not used in our code)
 *
 * Buffer descriptor, stored in a descriptor table and referenced in rings
 */
struct virtq_desc {
    uint64_t addr;
    uint32_t len;
    uint16_t flags;
    uint16_t next;
};

/* Used to indicate a chained descriptor. We aren't doing this */
#define VIRTQ_DESC_F_NEXT (1 << 0)

/* This buffer may be written by the host */
#define VIRTQ_DESC_F_WRITE (1 << 1)

/* This descriptor contains other descriptor ids. We aren't doing this */
#define VIRTQ_DESC_F_INDIRECT (1 << 2)

/**
 * struct virtq_avail - VirtIO ring of buffers for use by the device
 * @flags:      Features for the ring. We zero this.
 * @idx:        Location we would insert the next buffer, mod VQ_SIZE
 * @ring:       Ring of indexes into the descriptor table.
 * @used_event: Delay interrupts until idx > used_event if
 *              VIRTIO_F_EVENT_IDX was negotiated.
 */
struct virtq_avail {
    uint16_t flags;
    uint16_t idx;
    uint16_t ring[VQ_SIZE];
    uint16_t used_event;
};

/**
 * struct virtq_used_elem - What the device did with a buffer
 * @id:  Descriptor index of the buffer used.
 * @len: How much of the buffer was used.
 *       NOTE: On Legacy MMIO devices (e.g. our serial ports), this field
 *       may be inaccurate for send requests (and is for our QEMU version).
 */
struct virtq_used_elem {
    uint32_t id;
    uint32_t len;
};

/**
 * struct virtq_used - Ring of buffers used by the device
 * @flags:       Features for the ring. We zero this.
 * @idx:         Location the device would insert the next buffer, mod VQ_SIZE
 * @ring:        Ring of virtq_used_elem, saying what the device has done.
 * @avail_event: Delay interrupt until idx > avail_event if
 *               VIRTIO_F_EVENT_IDX was negotiated.
 *
 * While virtq_used has weaker alignment requirements (4) than PAGE_SIZE in
 * the current spec, the Legacy spec requires that it be aligned to PAGE_SIZE.
 */
struct virtq_used {
    uint16_t flags;
    uint16_t idx;
    struct virtq_used_elem ring[VQ_SIZE];
    uint16_t avail_event;
} __attribute__((aligned(PAGE_SIZE)));

/**
 * struct virtq_raw - Legacy VirtIO layout container
 * @desc:  Table of bufferdescriptors. Indexes/IDs mentioned elsewhere are
 *         indexes into this table.
 * @avail: Ring of buffers made available to the device
 * @used:  Ring of buffers the device is done processing
 *
 * The virtq_raw struct itself must be page aligned because a page frame is
 * passed to the VirtIO driver to identify the region rather than an address.
 *
 * Further, the Legacy spec requires that @avail immediately follow the
 * descriptor table, and that @used must be at the first page boundary
 * afterwards.
 */
struct virtq_raw {
    struct virtq_desc desc[VQ_SIZE];
    struct virtq_avail avail;
    struct virtq_used used;
} __attribute__((aligned(PAGE_SIZE)));
;

/* VirtIO Device IDs */
#define VIRTIO_DEVICE_ID_RESERVED (0)
#define VIRTIO_DEVICE_ID_BLOCK_DEVICE (2)
#define VIRTIO_DEVICE_ID_CONSOLE (3)

/* Flags for the status field of a VirtIO device */

/* Guest->Host: I have seen this device */
#define VIRTIO_STATUS_ACKNOWLEDGE (1)

/* Guest->Host: I have a driver for this device */
#define VIRTIO_STATUS_DRIVER (2)

/* Guest->Host: Driver is ready to drive the device */
#define VIRTIO_STATUS_DRIVER_OK (4)

/* Guest->Host: Feature negotiation is complete */
#define VIRTIO_STATUS_FEATURES_OK (8)

/* Host->Guest: The device has encountered an error; a reset may recover */
#define VIRTIO_STATUS_DEVICE_NEEDS_RESET (64)

/* Guest->Host: The driver can no longer drive the device */
#define VIRTIO_STATUS_FAILED (128)

/**
 * struct virtq - VirtIO Queue + bookkeeping information
 * @num_bufs:     How many buffers are in the queue. For now, this is always
 *                VQ_NUM_BUFS.
 * @queue_id:     Which ID this queue has on the device.
 * @raw:          The actual legacy MMIO ring.
 * @old_used_idx: Last seen value of the used index.
 *                This value is used to track whether there are new buffers to
 *                process.
 * @vio:          Pointer to the MMIO space this virtq is used by.
 */
struct virtq {
    size_t num_bufs;
    size_t queue_id;
    struct virtq_raw* raw;
    uint16_t old_used_idx;
    struct virtio_config* vio;
};

/**
 * virtio_set_features() - Sets the provided features on a VirtIO device
 * @vio:      The device to set the features on
 * @features: The features to set
 */
void virtio_set_features(struct virtio_config* vio, uint64_t features);

/**
 * virtio_get_features() - Gets the possible features on a VirtIO device
 * @vio:   The device to query
 *
 * Return: The feature vector supported by the device
 */
uint64_t virtio_get_features(struct virtio_config* vio);

/**
 * vq_init() - Initialize a virtq for the provided VirtIO device and sense.
 * @vq:       The uninitialized virtq
 * @vq_raw:   The uninitialized legacy-compatible VirtIO queue
 * @vio:      The VirtIO device the queue is for
 * @is_input: Whether buffers moving through the queue should be
 *            host->guest (input) or guest->host (output).
 *
 * It is reccomended that virtq_raw be statically allocated to avoid alignment
 * considerations.
 */
void vq_init(struct virtq* vq,
             struct virtq_raw* raw,
             struct virtio_config* vio,
             bool is_input);

/**
 * vq_attach() - Attaches an initialized virtq to the specified queue ID
 * @vq:  The virtq to attach
 * @idx: Which id to attach it on
 */
void vq_attach(struct virtq* vq, uint16_t idx);

/**
 * vq_make_avail - Adds the specified descriptor to the available ring
 * @vq:      The virtq we are operating on
 * @desc_id: Which descriptor to make available to the device
 */
void vq_make_avail(struct virtq* vq, uint16_t desc_id);

/**
 * vq_kick - Alerts the device that this virtq has been updated
 * @vq: The virtq to tell the device about.
 */
void vq_kick(struct virtq* vq);

/**
 * vq_ready() - Checks whether the device has processed another buffer.
 * @vq: The queue to check
 */
static inline bool vq_ready(struct virtq* vq) {
    return io_read_16(&vq->raw->used.idx) != vq->old_used_idx;
}

/**
 * vq_wait() - Performs a blocking wait for the device to process a buffer.
 * @vq: The queue to wait for processing on.
 */
void vq_wait(struct virtq* vq);

/**
 * vq_adv() - Acknowledge that the host processed a buffer
 * @vq:    The queue we are acknowledging
 * Return: The processed buffer's length.
 *         This may be inaccurate for output buffers when in Legacy mode.
 */
uint32_t vq_adv(struct virtq* vq);

/**
 * send_vq() - Send a buffer via a virtq.
 * @vq:    The VirtIO queue to send on
 * @data:  The buffer to send
 * @len:   The size of the buffer
 * Return: Negative on error, size sent on success.
 */
ssize_t send_vq(struct virtq* vq, const char* data, size_t len);

/**
 * recv_vq() - Receive data from a VirtIO queue.
 * @vq:    The queue to receive on.
 * @data:  The buffer to write to.
 * @len:   The size of the buffer.
 * Return: Negative value on error, size received on success.
 *
 * Will receive *exactly* one packet (since this is not truly a stream
 * protocol).
 */
ssize_t recv_vq(struct virtq* vq, char* data, size_t len);

/**
 * vq_set_buf_w() - Set a descriptor's buffer, host writable
 * @vq:      The queue to operate on
 * @desc_id: Which descriptor to set
 * @data:    The buffer to set it to
 * @len:     How big the buffer is
 */
void vq_set_buf_w(struct virtq* vq, uint16_t desc_id, void* data, size_t len);

/**
 * vq_set_buf_r() - Set a descriptor's buffer, host readable
 * @vq:      The queue to operate on
 * @desc_id: Which descriptor to set
 * @data:    The buffer to set it to
 * @len:     How big the buffer is
 */
void vq_set_buf_r(struct virtq* vq,
                  uint16_t desc_id,
                  const void* data,
                  size_t len);
