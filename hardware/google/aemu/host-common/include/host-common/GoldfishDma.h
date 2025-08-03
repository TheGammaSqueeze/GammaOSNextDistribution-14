// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include "aemu/base/files/Stream.h"

#include <inttypes.h>

static const uint32_t kDmaBufSizeMB = 32;
// GOLDFISH DMA
//
// Goldfish DMA is an extension to the pipe device
// and is designed to facilitate high-speed RAM->RAM
// transfers from guest to host.
//
// See docs/GOLDFISH-VIRTUAL-HARDWARE.txt and
//     docs/ANDROID-QEMU-PIPE.txt
//
// for more details.
// Host / virtual device interface:
typedef struct {
// add_buffer():
// Tell us that there is a physically-contiguous buffer in the guest
// starting at |guest_paddr|. This should be used upon allocation
// of the DMA buffer in the ugest.
void (*add_buffer)(void* pipe, uint64_t guest_paddr, uint64_t sz);
// remove_buffer():
// Tell us that we don't care about tracking this buffer anymore.
// This should usually be used when the DMA buffer has been freed
// in the ugest.
void (*remove_buffer)(uint64_t guest_paddr);
// get_host_addr():
// Obtain a pointer to guest physical memory that is usable as
// as host void*.
void* (*get_host_addr)(uint64_t guest_paddr);
// invalidate_host_mappings():
// Sometimes (say, on snapshot save/load) we may need to re-map
// the guest DMA buffers so to regain access to them.
// This function tells our map of DMA buffers to remap the buffers
// next time they are used.
void (*invalidate_host_mappings)(void);
// unlock():
// Unlocks the buffer at |guest_paddr| to signal the guest
// that we are done writing it.
void (*unlock)(uint64_t guest_paddr);
// reset_host_mappings();
// Not only invalidates the mappings, but also removes them from the record.
void (*reset_host_mappings)(void);
// For snapshots.
void (*save_mappings)(android::base::Stream* stream);
void (*load_mappings)(android::base::Stream* stream);
} GoldfishDmaOps;

extern const GoldfishDmaOps android_goldfish_dma_ops;
