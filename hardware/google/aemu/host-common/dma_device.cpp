// Copyright 2023 The Android Open Source Project
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

#include "GoldfishDma.h"
#include "dma_device.h"

static void* defaultDmaGetHostAddr(uint64_t guest_paddr) { return nullptr; }
static void defaultDmaUnlock(uint64_t addr) { }

namespace emugl {

emugl_dma_get_host_addr_t g_emugl_dma_get_host_addr = defaultDmaGetHostAddr;
emugl_dma_unlock_t g_emugl_dma_unlock = defaultDmaUnlock;

void set_emugl_dma_get_host_addr(emugl_dma_get_host_addr_t f) {
    g_emugl_dma_get_host_addr = f;
}

void set_emugl_dma_unlock(emugl_dma_unlock_t f) {
    g_emugl_dma_unlock = f;
}

}  // namespace emugl
