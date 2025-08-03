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
#include <virtio.h>

/**
 * virtio_probe_console() - Probe Virtio console device
 *
 * Return: Base address of the Virtio device specific configuration structure
 */
struct virtio_config* virtio_probe_console(void);

/**
 * virtio_or_status() - Set status bit of Virtio device
 * @vio:    The Virtio device to set feature bit on
 * @flags:  The feature bit to be set
 */
void virtio_or_status(struct virtio_config* vio, uint32_t flags);

/**
 * virtio_get_status() - Get status of Virtio device
 * @vio:    The Virtio device to get status from
 *
 * Return: Status of the Virtio device
 */
uint32_t virtio_get_status(struct virtio_config* vio);

/**
 * virtio_reset_device() - Reset Virtio device
 * @vio:    The Virtio device to be reset
 */
void virtio_reset_device(struct virtio_config* vio);

/**
 * virtio_set_guest_page_size() - Set guest page size of Virtio device
 * @vio:    The Virtio device to set guest page size on
 * @size:   Size of guest page
 */
void virtio_set_guest_page_size(struct virtio_config* vio, uint32_t size);
