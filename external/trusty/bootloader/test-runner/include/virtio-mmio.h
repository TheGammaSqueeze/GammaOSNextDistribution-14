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
#include <sys/types.h>

/**
 * struct virtio_mmio_config - VirtIO MMIO Register Layout
 * @magic:              Contains VIRTIO_MMIO_MAGIC, or this is invalid
 * @version:            MMIO Spec Version
 * @device_id:          Type of device. See VIRTIO_DEVICE_ID_*
 * @vendor_id:          Identifies the implementor of the device
 * @host_features:      32-bits of which features the host supports.
 * @host_features_sel:  Selects which half of the feature vector @host_features
 *                      exposes - 0 for low, 1 for high.
 * @guest_features:     32-bits of which features the guest wants.
 * @guest_features_sel: Selects which half of the feature vector
 *                      @guest_features is writing to.
 * @queue_sel:          Which device queue the guest is configuring
 * @queue_num_max:      The maximum VirtIO queue size supported by the for the
 *                      selected queue.
 * @queue_num:          The VirtIO queue size used by the guest for the
 *                      selected queue.
 * @queue_align:        Specify the alignment of the queues. Using a value
 *                      other than PAGE_SIZE may not be supported.
 *                      Must be a power of 2.
 * @queue_pfn:          Page frame number of the virtq_raw struct to be used.
 * @queue_ready:        (Non-Legacy) Writing 1 to this enables the current
 *                      queue. Should be poked after using @queue_desc_low and
 *                      friends to define the queue.
 * @queue_notify:       Write a queue index here to tell the device to check it
 * @interrupt_status:   Why the most recent interrupt was fired.
 *                      * Bit 0 - Used ring updated (device processed a buffer)
 *                      * Bit 1 - Config updated
 * @interrupt_ack:      Tell the device an interrupt was handled. Format is the
 *                      the same as @interrupt_status
 * @status:             Used to negotiate startup. See VIRTIO_STATUS_*.
 *                      Write 0 to reset.
 * @queue_desc_low:     (Non-Legacy) Low 32-bits of descriptor table physaddr.
 * @queue_desc_high:    (Non-Legacy) High 32-bits of descriptor table physaddr.
 * @queue_avail_low:    (Non-Legacy) Low 32-bits of available ring physaddr.
 * @queue_avail_high:   (Non-Legacy) High 32-bits of available ring physaddr.
 * @queue_used_low:     (Non-Legacy) Low 32-bits of used ring physaddr.
 * @queue_used_high:    (Non-Legacy) High 32-bits of used ring physaddr.
 * @config:             Device specific configuration information.
 */
struct virtio_mmio_config {
    /* 0x00 */
    uint32_t magic;
    uint32_t version;
    uint32_t device_id;
    uint32_t vendor_id;
    /* 0x10 */
    uint32_t host_features;
    uint32_t host_features_sel;
    uint32_t __reserved0[2];
    /* 0x20 */
    uint32_t guest_features;
    uint32_t guest_features_sel;
    uint32_t guest_page_size;
    uint32_t __reserved1[1];
    /* 0x30 */
    uint32_t queue_sel;
    uint32_t queue_num_max;
    uint32_t queue_num;
    uint32_t queue_align;
    /* 0x40 */
    uint32_t queue_pfn;
    uint32_t queue_ready;
    uint32_t __reserved2[2];
    /* 0x50 */
    uint32_t queue_notify;
    uint32_t __reserved3[3];
    /* 0x60 */
    uint32_t interrupt_status;
    uint32_t interrupt_ack;
    uint32_t __reserved4[2];
    /* 0x70 */
    uint32_t status;
    uint32_t __reserved5[3];
    /* 0x80 */
    uint32_t queue_desc_low;
    uint32_t queue_desc_high;
    uint32_t __reserved6[2];
    /* 0x90 */
    uint32_t queue_avail_low;
    uint32_t queue_avail_high;
    uint32_t __reserved7[2];
    /* 0xa0 */
    uint32_t queue_used_low;
    uint32_t queue_used_high;
    uint8_t __reserved8[0x58];
    /* 0x100 */
    uint8_t config[0x100];
};

/* Magic value to identify a real VirtIO MMIO region */
#define VIRTIO_MMIO_MAGIC (0x74726976)
