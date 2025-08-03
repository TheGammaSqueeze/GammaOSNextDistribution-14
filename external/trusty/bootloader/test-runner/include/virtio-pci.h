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

#pragma once

#include <stdint.h>

/* Virtio devices must have the PIC Vendor ID 0x1AF4 */
#define VIRTIO_DEVICE_VENDOR_ID 0x1AF4
/* Transitional PCI console device has Device ID 0x1003 */
#define VIRTIO_PCI_DEVICE_CONSOLE_ID 0x1003

/* Virtio structure PCI capabilities: Common configuration */
#define VIRTIO_PCI_CAP_COMMON_CFG 0x1
/* Virtio structure PCI capabilities: Notifications */
#define VIRTIO_PCI_CAP_NOTIFY_CFG 0x2

/**
 * struct virtio_pci_cap - Virtio PCI capability structure
 * @cap_vndr:   Generic PCI field: PCI_CAP_ID_VNDR
 * @cap_next:   Generic PCI field: next ptr.
 * @cap_len:    Generic PCI field: capability length.
 * @cfg_type:   Identifies the structure.
 * @bar:        Where to find it.
 * @padding:    Pad to full dword.
 * @offset:     Offset within bar.
 * @length:     Length of the structure, in bytes.
 */
struct virtio_pci_cap {
    uint8_t cap_vndr;
    uint8_t cap_next;
    uint8_t cap_len;
    uint8_t cfg_type;
    uint8_t bar;
    uint8_t padding[3];
    uint32_t offset;
    uint32_t length;
};

/**
 * struct virtio_pci_common_cfg - Virtio PCI common configuration structure
 * @host_features_sel:  Selects which half of the feature vector @host_features
 *                      exposes - 0 for low, 1 for high.
 * @host_features:      32-bits of which features the host supports.
 * @guest_features_sel: Selects which half of the feature vector
 *                      @guest_features is writing to.
 * @guest_features:     32-bits of which features the guest wants.
 * @msix_config:        Guest sets the Configuration Vector for MSI-X.
 * @num_queues:         Host specifies the maximum number of virtqueues
 *                      supported.
 * @device_status:      Used to negotiate startup. Write 0 to reset.
 * @config_generation:  Guest changes this every time the configuration
 *                      noticeably changes.
 * @queue_sel:          Which device queue the guest is configuring
 * @queue_size:         Queue size used by the guest for the selected queue.
 * @queue_msix_vector:  Guest uses this to specify the queue vector for MSI-X.
 * @queue_enable:       Guest uses this to selectively prevent the host from
 *                      executing requests from this virtqueue.
 *                      1- enabled; 0 - disabled.
 * @queue_notify_off:   Guest reads this to calculate the offset from start of
 *                      Notification structure at which this queue is located.
 * @queue_desc:         Physical address of the Descriptor Table.
 * @queue_avail:        Physical address of the Available Ring.
 * @queue_used:         Physical address of the Used Ring.
 */
struct virtio_pci_common_cfg {
    /* About the whole device. */
    uint32_t host_features_sel;
    uint32_t host_features;
    uint32_t guest_features_sel;
    uint32_t guest_features;
    uint16_t msix_config;
    uint16_t num_queues;
    uint8_t device_status;
    uint8_t config_generation;

    /* About a specific virtqueue. */
    uint16_t queue_sel;
    uint16_t queue_size;
    uint16_t queue_msix_vector;
    uint16_t queue_enable;
    uint16_t queue_notify_off;
    uint64_t queue_desc;
    uint64_t queue_avail;
    uint64_t queue_used;
};

/**
 *struct virtio_pci_notify_cap - Virtio PCI notification capability structure
 * @cap:                   Virtio PCI capability.
 * @notify_off_multiplier: Multiplier for queue_notify_off.
 *                         notify_off_multiplier is combined with the
 *                         queue_notify_off to derive the Queue Notify address
 *                         within a BAR for a virtqueue:
 *                         cap.offset + queue_notify_off * notify_off_multiplier
 */
struct virtio_pci_notify_cap {
    struct virtio_pci_cap cap;
    uint32_t notify_off_multiplier;
};
