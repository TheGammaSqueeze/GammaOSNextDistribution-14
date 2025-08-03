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

/* Address at which QEMU loads the VirtIO MMIO config by default. */
#define VIRTIO_MMIO_BASE 0xa000000

/*
 * Address at which QEMU loads the Virtio PCIe ECAM by default.
 *
 * If qemu is built from scratch by source code from the Trusty external/qemu
 * repository, PCIE ECAM base address is specified at 0x4010000000ULL.
 */
#define VIRT_PCIE_ECAM_HIGH_BASE 0x4010000000ULL
#define VIRT_PCIE_ECAM_HIGH_SIZE 0x10000000

/* Default address for second PCIe Window, start from 512GB */
#define VIRT_PCIE_MMIO_HIGH_BASE 0x8000000000ULL

/* Keep 1GB size in case Android kernel maps devices */
#define RESERVED_REGION_FOR_ANDROID_KERNEL 0x40000000

#define VIRT_CONSOLE_BAR_ADDR \
    VIRT_PCIE_MMIO_HIGH_BASE + RESERVED_REGION_FOR_ANDROID_KERNEL
