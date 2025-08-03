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

#include <arch/virtio-base.h>
#include <assert.h>
#include <pci.h>
#include <utils.h>
#include <virtio-device.h>
#include <virtio-pci.h>
#include <virtio.h>

/* Queue notification base address */
static uint64_t notify_base;

/*
 * Queue notification multiplier, notify_multiplier is combined with the
 * queue notify offset to derive the Queue Notify address within a BAR for
 * a virtqueue.
 */
static uint32_t notify_multiplier;

void virtio_set_features(struct virtio_config* vio, uint64_t features) {
    struct virtio_pci_common_cfg* vio_pci = (struct virtio_pci_common_cfg*)vio;

    io_write_32(&vio_pci->guest_features_sel, 0);
    io_write_32(&vio_pci->guest_features, features & 0xFFFF);
    io_write_32(&vio_pci->guest_features_sel, 1);
    io_write_32(&vio_pci->guest_features, features >> 32);
}

uint64_t virtio_get_features(struct virtio_config* vio) {
    struct virtio_pci_common_cfg* vio_pci = (struct virtio_pci_common_cfg*)vio;
    uint64_t features;

    io_write_32(&vio_pci->host_features_sel, 1);
    features = io_read_32(&vio_pci->host_features);
    features <<= 32;
    io_write_32(&vio_pci->host_features_sel, 0);
    features |= io_read_32(&vio_pci->host_features);
    return features;
}

void vq_attach(struct virtq* vq, uint16_t idx) {
    struct virtio_pci_common_cfg* vio_pci =
            (struct virtio_pci_common_cfg*)vq->vio;

    vq->queue_id = idx;
    io_write_16(&vio_pci->queue_sel, idx);
    io_write_16(&vio_pci->queue_size, vq->num_bufs);

    io_write_64(&vio_pci->queue_desc, (uint64_t)&vq->raw->desc);
    io_write_64(&vio_pci->queue_avail, (uint64_t)&vq->raw->avail);
    io_write_64(&vio_pci->queue_used, (uint64_t)&vq->raw->used);

    io_write_16(&vio_pci->queue_enable, 1);
}

void vq_kick(struct virtq* vq) {
    struct virtio_pci_common_cfg* vio_pci =
            (struct virtio_pci_common_cfg*)vq->vio;
    uint16_t notify_off;
    uint64_t notify_addr;

    io_write_16(&vio_pci->queue_sel, vq->queue_id);

    notify_off = io_read_16(&vio_pci->queue_notify_off);

    notify_addr = notify_base + notify_off * notify_multiplier;
    io_write_32((void*)notify_addr, vq->queue_id);
}

void virtio_or_status(struct virtio_config* vio, uint32_t flags) {
    struct virtio_pci_common_cfg* vio_pci = (struct virtio_pci_common_cfg*)vio;
    uint8_t old_status = io_read_8(&vio_pci->device_status);

    io_write_8(&vio_pci->device_status, old_status | flags);
}

uint32_t virtio_get_status(struct virtio_config* vio) {
    struct virtio_pci_common_cfg* vio_pci = (struct virtio_pci_common_cfg*)vio;

    return io_read_8(&vio_pci->device_status);
}

void virtio_reset_device(struct virtio_config* vio) {
    struct virtio_pci_common_cfg* vio_pci = (struct virtio_pci_common_cfg*)vio;

    io_write_8(&vio_pci->device_status, 0);
}

void virtio_set_guest_page_size(struct virtio_config* vio, uint32_t size) {}

static void* virtio_console_probe_pcie_mmio(void) {
    uint8_t cap_offset;
    uint8_t common_bar;
    uint8_t notify_bar;
    uint16_t cmd_val;
    uint64_t virtio_cfg;
    uint64_t base_addr;
    struct pci_type0_config* cfg;

    /*
     * PCI Express extends the Configuration Space to 4096 bytes per Function as
     * compared to 256 bytes allowed by PCI Local Bus Specification
     */
    for (base_addr = VIRT_PCIE_ECAM_HIGH_BASE;
         base_addr < VIRT_PCIE_ECAM_HIGH_BASE + VIRT_PCIE_ECAM_HIGH_SIZE;
         base_addr += PAGE_SIZE) {
        cfg = (struct pci_type0_config*)base_addr;
        if ((VIRTIO_DEVICE_VENDOR_ID == cfg->vendor_id) &&
            (VIRTIO_PCI_DEVICE_CONSOLE_ID == cfg->device_id)) {
            break;
        }
    }

    if (base_addr == VIRT_PCIE_ECAM_HIGH_BASE + VIRT_PCIE_ECAM_HIGH_SIZE) {
        log_msg("Error: No virtio console device found!\n");
        return NULL;
    }

    /* Make sure Capabilities List bit is set */
    if (!(cfg->status & (1 << STATUS_CAP_LIST_BIT_POSITION))) {
        log_msg("Error: Virtio console capabilities list unsupport!\n");
        return NULL;
    }

    /* Enable Memory Space access */
    cmd_val = io_read_16(&cfg->command);
    cmd_val |= 1 << CMD_MEM_SPACE_BIT_POSITION;
    io_write_16(&cfg->command, cmd_val);

    /* Get capabilities start offset */
    cap_offset = cfg->capabilities_pointer;

    /*
     * Traverse capabilites linked list to find common configuration
     * and notification settings. Common configuration and notification
     * should share same BAR with different offset.
     */
    do {
        struct virtio_pci_cap* virtio_cap =
                (struct virtio_pci_cap*)(base_addr + cap_offset);
        uint8_t type = io_read_8(&virtio_cap->cfg_type);

        if (VIRTIO_PCI_CAP_COMMON_CFG == type) {
            common_bar = io_read_8(&virtio_cap->bar);
            virtio_cfg = io_read_32(&virtio_cap->offset);
            virtio_cfg += VIRT_CONSOLE_BAR_ADDR;
        }

        if (VIRTIO_PCI_CAP_NOTIFY_CFG == type) {
            struct virtio_pci_notify_cap* notify_cap =
                    (struct virtio_pci_notify_cap*)virtio_cap;

            notify_bar = io_read_8(&notify_cap->cap.bar);
            notify_multiplier = io_read_32(&notify_cap->notify_off_multiplier);
            notify_base = io_read_32(&notify_cap->cap.offset);
            notify_base += VIRT_CONSOLE_BAR_ADDR;
        }

        cap_offset = io_read_8(&virtio_cap->cap_next);
    } while (0 != cap_offset);

    assert(common_bar == notify_bar);

    /* Set 64-bit BAR address */
    io_write_64((uint64_t*)((uint64_t)&cfg->base_addr_reg0 + common_bar * 4),
                VIRT_CONSOLE_BAR_ADDR | 0x4);

    return (void*)virtio_cfg;
}

struct virtio_config* virtio_probe_console(void) {
    return virtio_console_probe_pcie_mmio();
}
